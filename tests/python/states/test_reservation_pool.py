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
#     "interval": "2026-11-17T17:55:00/2026-11-17T20:30:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-v4.1-flash-1017",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#

"""
State-transition tests for the pool recorded by a compute reservation.

These tests are allowed to access the broker database directly (via the
`database` fixture) to verify the stored foreign key and the pool's live
reservation view (see AGENTS.md).
"""

import base64
import json
import urllib.request
import uuid

from calycopis_conftest import (
    CALYCOPIS_URL,
    admin_credentials,
)
from calycopis_openapi_client.models import (
    ComponentMetadata,
    DockerImageSpec,
    ExecutionRequest,
    OfferSetResponse,
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.wrappers import DockerContainer


# Heliophorus-cantliei test container: waits N seconds then exits.
CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"


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
    """Submit a request and return its first OFFERED session."""
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


def _create_pool(name):
    """Create a named compute pool with the admin credentials."""
    username, password = admin_credentials()
    credentials = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    payload = json.dumps(
        {
            "name": name,
            "description": name,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        CALYCOPIS_URL + "/admin/pools/compute",
        data=payload,
        method="POST",
    )
    request.add_header("Accept", "application/json")
    request.add_header("Content-Type", "application/json")
    request.add_header("Authorization", f"Basic {credentials}")
    with urllib.request.urlopen(request, timeout=10) as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["uuid"]


def test_offered_reservation_records_default_pool(client, database):
    """An OFFERED reservation stores the default pool and appears in its view."""
    offer = _submit_offer(client, "states-reservation-pool-offered")
    session_uuid = str(offer.meta.uuid)

    default_pool = database.default_pool_uuid()
    assert default_pool is not None, "The broker should seed a default pool"

    stored_pool = database.reservation_pool(session_uuid)
    assert str(stored_pool) == str(default_pool), (
        f"Reservation pool [{stored_pool}] should be the default pool "
        f"[{default_pool}]"
    )

    assert database.reservation_pool_column_is_not_null(), (
        "The reservation pool column should be NOT NULL"
    )
    assert database.reservation_pool_foreign_key_count() >= 1, (
        "The reservation pool column should have a foreign key"
    )
    assert session_uuid in database.live_reservation_sessions(default_pool), (
        "The OFFERED reservation should be in the default pool's live view"
    )


def test_live_reservations_are_scoped_to_the_pool(client, database):
    """A pool's live view excludes terminal sessions and other pools."""
    offer = _submit_offer(client, "states-reservation-pool-scoped")
    session_uuid = str(offer.meta.uuid)
    default_pool = database.default_pool_uuid()
    other_pool = _create_pool(
        "states-reservation-pool-" + uuid.uuid4().hex
    )

    assert session_uuid in database.live_reservation_sessions(default_pool), (
        "The offer should be live in the default pool"
    )
    assert session_uuid not in database.live_reservation_sessions(other_pool), (
        "The default pool's offer must not appear in another pool"
    )

    rejected = client.set_session_phase(
        session_uuid,
        SimpleExecutionSessionPhase.REJECTED,
    )
    assert rejected.phase == SimpleExecutionSessionPhase.REJECTED, (
        f"Expected REJECTED, got {rejected.phase}"
    )
    assert session_uuid not in database.live_reservation_sessions(default_pool), (
        "A rejected session must not remain in the live reservation view"
    )
