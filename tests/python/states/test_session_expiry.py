#
# <meta:header>
#   <meta:licence>
#     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
#
#     This information is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This information is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
# AIMetrics: [
#     {
#     "timestamp": "2026-09-05T12:41:19",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#

"""
State-transition tests for session expiry (EXPIRED) behaviour.

Verifies the processing implemented by ExpireSessionRequestEntity:
  - an unaccepted (OFFERED) session becomes EXPIRED when its expiry time is
    reached;
  - a session that is no longer OFFERED (e.g. REJECTED or ACCEPTED) is not
    touched by the expire request;
  - the expiry release of never-started components is a no-op, so an EXPIRED
    session stays EXPIRED and is not flipped to FAILED/COMPLETED.

These tests are allowed to access the broker database directly (via the
`database` fixture) to verify the state transitions (see AGENTS.md).

The tests wait until each session's own expiry time (read from the session
`expires` field returned by the API), so they work with any configured
`calycopis.broker.timing.session.EXPIRED.timeout`. With the default 300s
timeout each test takes roughly 5-6 minutes. For a fast run, start the broker
with a short timeout, e.g. on the mock platform:

    SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run \\
        -Dspring-boot.run.arguments="--calycopis.broker.timing.session.EXPIRED.timeout=20 --calycopis.broker.timing.session.EXPIRED.polling=1"

Run from tests/python (with CALYCOPIS_URL, CALYCOPIS_ADMIN_USERNAME and
CALYCOPIS_ADMIN_PASSWORD set as described in bin/entrypoint.sh):

    pytest -v states
"""

import time
from datetime import datetime, timedelta, timezone

from calycopis_openapi_client.models import (
    ComponentMetadata,
    DockerImageSpec,
    ExecutionRequest,
    OfferSetResponse,
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.wrappers import DockerContainer


# Heliophorus-cantliei test container: waits N seconds then exits
# with a configurable exit code.
CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

# How long past the expiry time we keep polling before failing.
# The expire request activates at the session expiry time and is handled on
# the next processing tick, so a short grace period is sufficient.
EXPIRE_GRACE_SECONDS = 20

# Interval between session polls while waiting for expiry.
POLL_INTERVAL_SECONDS = 2


def _utc_now():
    """A timezone-aware UTC 'now' for comparing against session expires."""
    return datetime.now(timezone.utc)


def _expiry_deadline(session):
    """The session expiry time plus the grace period."""
    expires = session.expires
    assert expires is not None, "Session should have an expiry time"
    if expires.tzinfo is None:
        expires = expires.replace(tzinfo=timezone.utc)
    return expires + timedelta(seconds=EXPIRE_GRACE_SECONDS)


def _make_executable(name):
    """Create a DockerContainer executable (not run unless the session runs)."""
    return DockerContainer(
        meta=ComponentMetadata(name=name),
        image=DockerImageSpec(
            locations=[CANTLIEI_IMAGE],
            digest=CANTLIEI_DIGEST,
        ),
        command=["1", "0"],
    )


def _submit_offer(client, name):
    """
    Submit an offer-set request and return the first offer.
    The offer is returned in the OFFERED phase and is not accepted.
    """
    request = ExecutionRequest(
        executable=_make_executable(name),
    )
    response = client.submit_execution(request, follow_redirect=True)
    assert isinstance(response, OfferSetResponse), (
        f"Expected OfferSetResponse, got {type(response)}"
    )
    assert response.offers is not None, "Offer-set response should have offers"
    assert len(response.offers) > 0, "Offer-set response should have at least one offer"
    return response.offers[0]


def _wait_for_phase(client, session_uuid, target_phases, deadline):
    """
    Poll the session until its phase is in target_phases or the deadline
    has passed. Raises an AssertionError when the deadline is reached first.
    """
    last = None
    while _utc_now() < deadline:
        last = client.get_session(session_uuid)
        if last.phase in target_phases:
            return last
        time.sleep(POLL_INTERVAL_SECONDS)
    last = client.get_session(session_uuid)
    if last.phase in target_phases:
        return last
    raise AssertionError(
        f"Session [{session_uuid}] did not reach phase "
        f"[{sorted(p.value for p in target_phases)}] before [{deadline}], "
        f"phase was [{last.phase}]"
    )


def _wait_past(client, session_uuid, deadline):
    """
    Keep polling (and sleeping) until the deadline has passed.
    Used to wait for a session's expiry window to elapse.
    """
    while _utc_now() < deadline:
        client.get_session(session_uuid)
        time.sleep(POLL_INTERVAL_SECONDS)
    return client.get_session(session_uuid)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


def test_offered_session_becomes_expired(client, database):
    """
    An unaccepted (OFFERED) session should be set to EXPIRED once its expiry
    time is reached, and should stay EXPIRED afterwards.
    """
    offer = _submit_offer(client, "states-expire-offered")
    session_uuid = offer.meta.uuid

    assert offer.phase == SimpleExecutionSessionPhase.OFFERED, (
        f"Expected an OFFERED offer, got {offer.phase}"
    )
    deadline = _expiry_deadline(offer)

    # The session should become EXPIRED at (or shortly after) its expiry.
    expired = _wait_for_phase(
        client,
        session_uuid,
        [SimpleExecutionSessionPhase.EXPIRED],
        deadline,
    )
    assert expired.phase == SimpleExecutionSessionPhase.EXPIRED, (
        f"Expected EXPIRED, got {expired.phase}"
    )

    # EXPIRED should be terminal: release of the never-started components
    # must not flip the session to FAILED/COMPLETED.
    time.sleep(5)
    again = client.get_session(session_uuid)
    assert again.phase == SimpleExecutionSessionPhase.EXPIRED, (
        f"EXPIRED session should stay EXPIRED, got {again.phase}"
    )

    # Verify the transition in the database.
    assert database.session_phase(session_uuid) == "EXPIRED"
    assert database.expire_request_count(session_uuid) == 0, (
        "The processed expire request should have been removed from the queue"
    )
    compute_phases = database.compute_component_phases(session_uuid)
    assert len(compute_phases) > 0, "Session should have a compute component"
    assert "FAILED" not in compute_phases, (
        f"Expiry should not fail the compute component, got phases {compute_phases}"
    )


def test_rejected_and_accepted_sessions_are_not_expired(client, database):
    """
    ExpireSessionRequest should do nothing for sessions that are no longer
    OFFERED: a REJECTED session should stay REJECTED, and an ACCEPTED session
    should never become EXPIRED.
    """
    # A REJECTED offer.
    rejected_offer = _submit_offer(client, "states-expire-rejected")
    rejected_session = client.set_session_phase(
        rejected_offer.meta.uuid,
        SimpleExecutionSessionPhase.REJECTED,
    )
    assert rejected_session.phase == SimpleExecutionSessionPhase.REJECTED, (
        f"Expected REJECTED, got {rejected_session.phase}"
    )

    # An ACCEPTED offer, which then runs through the normal lifecycle.
    accepted_offer = _submit_offer(client, "states-expire-accepted")
    accepted_session = client.set_session_phase(
        accepted_offer.meta.uuid,
        SimpleExecutionSessionPhase.ACCEPTED,
    )
    assert accepted_session.phase != SimpleExecutionSessionPhase.OFFERED, (
        "Accepted session should have left the OFFERED phase"
    )

    # Wait until both sessions have passed their expiry windows.
    deadline = max(
        _expiry_deadline(rejected_session),
        _expiry_deadline(accepted_session),
    )
    _wait_past(client, accepted_offer.meta.uuid, deadline)

    final_rejected = client.get_session(rejected_offer.meta.uuid)
    final_accepted = client.get_session(accepted_offer.meta.uuid)
    assert final_rejected.phase == SimpleExecutionSessionPhase.REJECTED, (
        f"REJECTED session should not be expired, got {final_rejected.phase}"
    )
    assert final_accepted.phase not in (
        SimpleExecutionSessionPhase.OFFERED,
        SimpleExecutionSessionPhase.EXPIRED,
    ), (
        f"ACCEPTED session should not be expired, got {final_accepted.phase}"
    )

    # Verify the transitions in the database.
    assert database.session_phase(rejected_offer.meta.uuid) == "REJECTED"
    assert database.session_phase(accepted_offer.meta.uuid) not in (
        "OFFERED",
        "EXPIRED",
    )
    assert database.expire_request_count(rejected_offer.meta.uuid) == 0
    assert database.expire_request_count(accepted_offer.meta.uuid) == 0
