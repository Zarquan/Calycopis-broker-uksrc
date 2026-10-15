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
#     along with this software. If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
# AIMetrics: [
#     {
#     "interval": "2026-10-15T10:30:00/2026-10-15T17:00:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Tests for the requested start window (GitHub issue #156).

``requestedScheduleBlock.requested.start`` is an ISO 8601 interval, and it means
the window in which the client will accept a start. The contract is written up
in ``docs/booking.md``.

* A window that begins before the broker can start is clamped forward to the
  earliest start the platform can offer, and the rest of the window is honoured.
* A window that has already ended cannot be met. The response is ``result: NO``
  with no offers and a message saying so, rather than the client's window being
  quietly replaced by the broker's default.
* Only starts inside the window are offered, so the broker may return no offers
  at all when a narrow window cannot hold the requested duration.

These tests only submit offer-set requests and inspect the response. They never
accept an offer, so no lifecycle processing is triggered and they work on either
platform.

Requires:
  - A running Calycopis broker service (default: http://calycopis-dev:8082,
    overridable via CALYCOPIS_URL)

Usage:
  pytest tests/python/any/test_start_window.py -v
"""

import re
from datetime import datetime, timedelta, timezone

import pytest

from calycopis_openapi_client.models import (
    ExecutionRequest,
    RequestedScheduleBlock,
    RequestedScheduleItem,
)
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

# The requested duration for our test requests, in seconds.
REQUEST_DURATION_SECONDS = 300

# The message type for a start window that has already ended.
START_INTERVAL_PAST = "urn:start-interval-past"

# The message type for a start window that was clamped forward.
START_INTERVAL_CLAMPED = "urn:start-interval-clamped"


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _make_request(name: str, duration=None, start=None) -> ExecutionRequest:
    """Create an ExecutionRequest with an optional schedule."""
    schedule = None
    if duration is not None or start is not None:
        schedule = RequestedScheduleBlock(
            requested=RequestedScheduleItem(
                duration=duration,
                start=start,
            ),
        )
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=["5", "0"],
        ),
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name=f"{name}-compute"),
        ),
        schedule=schedule,
    )


def _format_instant(value: datetime) -> str:
    """Format a datetime as a whole second ISO 8601 instant."""
    return value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _parse_instant(value: str) -> datetime:
    """Parse a whole second ISO 8601 instant."""
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _window(start: datetime, end: datetime):
    """Format a requested start window, and return it with its bounds.

    A requested start window is an explicit ISO 8601 start/end range, which the
    interval parser used for offer windows does not handle, so the bounds are
    kept here rather than parsed back out of the string.
    """
    start_text = _format_instant(start)
    end_text = _format_instant(end)
    return (
        f"{start_text}/{end_text}",
        _parse_instant(start_text),
        _parse_instant(end_text),
    )


def _parse_iso8601_duration(value: str) -> timedelta:
    """Parse an ISO 8601 duration into a timedelta."""
    match = re.match(
        r"^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$",
        value,
    )
    if not match:
        raise ValueError(f"Cannot parse ISO 8601 duration: {value}")
    return timedelta(
        days=int(match.group(1) or 0),
        hours=int(match.group(2) or 0),
        minutes=int(match.group(3) or 0),
        seconds=float(match.group(4) or 0),
    )


def _parse_iso8601_interval(value: str):
    """Parse an ISO 8601 interval into (start, end) datetimes in UTC."""
    left, right = value.split("/", 1)

    def _parse_datetime(text):
        return datetime.fromisoformat(text.replace("Z", "+00:00"))

    if left.startswith("P"):
        end = _parse_datetime(right)
        return (end - _parse_iso8601_duration(left), end)
    start = _parse_datetime(left)
    return (start, start + _parse_iso8601_duration(right))


def _available_window(offer):
    """The available start and duration advertised on an offer."""
    assert offer.compute is not None, (
        "Offer should include a compute resource"
    )
    schedule = offer.compute.schedule
    assert schedule is not None, (
        "Offer compute resource should include a schedule"
    )
    assert schedule.available is not None, (
        "Compute schedule should include an available interval"
    )
    assert schedule.available.start is not None, (
        "Compute schedule available should include a start interval"
    )
    assert schedule.available.duration is not None, (
        "Compute schedule available should include a duration"
    )
    avail_start, _avail_end = _parse_iso8601_interval(
        schedule.available.start
    )
    avail_duration = _parse_iso8601_duration(
        schedule.available.duration
    )
    return (avail_start, avail_duration)


def _submit(client, request: ExecutionRequest):
    """Submit a request and return the OfferSetResponse, without asserting."""
    return client.submit_execution(request, follow_redirect=True)


def _message_kinds(response):
    """The kind of every message on the response."""
    if response.meta is None:
        return []
    if response.meta.messages is None:
        return []
    return [message.kind for message in response.meta.messages]


def _skip_if_no_offers(response):
    """Skip when the broker cannot offer anything, naming the reason."""
    if response.result != "YES" or not response.offers:
        pytest.skip(
            f"No offers to inspect. Result [{response.result}]. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )


# ===========================================================================
# Start window tests
# ===========================================================================

class TestStartWindow:
    """
    Tests for the requested start window.
    """

    def test_past_window_returns_no_offers(self, client):
        """
        A window that ended before the broker can start must be refused, with
        result NO, no offers, and a message saying why. The client's window must
        not be quietly replaced by the broker's default window.
        """
        now = datetime.now(timezone.utc)
        start, _window_start, _window_end = _window(
            now - timedelta(hours=2),
            now - timedelta(hours=1),
        )

        response = _submit(
            client,
            _make_request(
                "past-window",
                duration=f"PT{REQUEST_DURATION_SECONDS}S",
                start=start,
            ),
        )

        assert response.result == "NO", (
            f"A window in the past must be refused. Result [{response.result}]"
        )
        assert not response.offers, (
            "A window in the past must not produce offers"
        )
        assert START_INTERVAL_PAST in _message_kinds(response), (
            f"Expected a [{START_INTERVAL_PAST}] message. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

    def test_window_bounds_offer_starts(self, client):
        """
        Every offer must start inside the requested window. A start at or after
        the end of the window is not something the client asked for.
        """
        now = datetime.now(timezone.utc)
        start, window_start, window_end = _window(
            now + timedelta(seconds=60),
            now + timedelta(seconds=300),
        )

        response = _submit(
            client,
            _make_request(
                "bounded-window",
                duration=f"PT{REQUEST_DURATION_SECONDS}S",
                start=start,
            ),
        )
        _skip_if_no_offers(response)

        for offer in response.offers:
            avail_start, _avail_duration = _available_window(offer)
            assert window_start <= avail_start < window_end, (
                f"Offer start [{avail_start}] must be inside the requested "
                f"window [{window_start}] to [{window_end}]"
            )

    def test_window_starting_in_the_past_is_clamped(self, client):
        """
        A window that begins before the broker can start must be honoured as far
        as it reaches into the future, with an informational message, rather
        than being replaced by the broker's default.
        """
        now = datetime.now(timezone.utc)
        start, _window_start, window_end = _window(
            now - timedelta(seconds=60),
            now + timedelta(minutes=10),
        )

        response = _submit(
            client,
            _make_request(
                "clamped-window",
                duration=f"PT{REQUEST_DURATION_SECONDS}S",
                start=start,
            ),
        )
        _skip_if_no_offers(response)

        assert START_INTERVAL_CLAMPED in _message_kinds(response), (
            f"Expected a [{START_INTERVAL_CLAMPED}] message. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

        for offer in response.offers:
            avail_start, _avail_duration = _available_window(offer)
            assert avail_start < window_end, (
                f"Offer start [{avail_start}] must be before the end of the "
                f"requested window [{window_end}]"
            )
