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
#     "timestamp": "2026-10-13T10:22:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     },
#     {
#     "interval": "2026-10-16T04:45:00/2026-10-16T05:35:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 5,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Tests for zero delay offer start times (GitHub issue #30).

The booking query manages time in fixed size blocks, and it used to start its
search at the block *after* the one that contains the requested start time.
That pushed every offer start forward to the next block boundary, so a request
for an immediate start could wait almost a whole block before the first offer
became available.

The fix searches from the block that contains the requested start time, and
moves an offer start that is before the requested start time forward to the
requested start time, shortening the offer while keeping its end where the
query placed it. The query adds one extra block to the requested duration, so
the shortened offer still covers the full duration the client asked for.

These tests only submit an offer-set request and inspect the response. They
never accept an offer, so no lifecycle processing is triggered and they work
on either platform.

Requires:
  - A running Calycopis broker service (default: http://calycopis-dev:8082,
    overridable via CALYCOPIS_URL)

Usage:
  pytest tests/python/any/test_zero_delay_start.py -v
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

# The booking query block size, in seconds.
BLOCK_STEP_SECONDS = 60

# The requested duration for our test requests, in seconds.
REQUEST_DURATION_SECONDS = 300

# Slack for the request round trip and the broker's own processing.
ROUND_TRIP_SECONDS = 5.0


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


def _next_block_start(value: datetime) -> datetime:
    """The start of the first block that starts after the given time."""
    epoch = int(value.timestamp())
    block = (epoch // BLOCK_STEP_SECONDS + 1) * BLOCK_STEP_SECONDS
    return datetime.fromtimestamp(block, tz=timezone.utc)


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
    """Submit a request and return the OfferSetResponse."""
    response = client.submit_execution(request, follow_redirect=True)
    if response.result != "YES" or not response.offers:
        pytest.skip(
            f"No offers to inspect. Result [{response.result}]. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
    return response


# ===========================================================================
# Zero delay start tests
# ===========================================================================

class TestZeroDelayStart:
    """
    Tests that offer start times are not pushed forward to the next block.
    """

    def test_requested_start_time_is_offered(self, client, clean_broker):
        """
        A requested start time that sits in the middle of a block should be
        offered at exactly that time, rather than at the next block boundary
        up to a block later.
        """
        now = datetime.now(timezone.utc)
        requested_start = _next_block_start(now) + timedelta(seconds=1)

        request = _make_request(
            "zero-delay-explicit",
            duration=f"PT{REQUEST_DURATION_SECONDS}S",
            start=f"{_format_instant(requested_start)}/PT2H",
        )
        response = _submit(client, request)

        avail_start, avail_duration = _available_window(response.offers[0])
        offset = (avail_start - requested_start).total_seconds()

        assert offset >= -1.0, (
            f"Offer start [{avail_start}] is before the requested start "
            f"[{requested_start}]"
        )
        assert offset <= ROUND_TRIP_SECONDS, (
            f"Offer start [{avail_start}] is {offset:.0f}s after the "
            f"requested start [{requested_start}], the block rounding delay "
            f"is still there"
        )
        assert avail_duration.total_seconds() >= REQUEST_DURATION_SECONDS, (
            f"Offer duration [{avail_duration}] is shorter than the "
            f"requested duration [{REQUEST_DURATION_SECONDS}s]"
        )

    def test_immediate_request_starts_now(self, client, clean_broker):
        """
        A request with no start time should be offered the current block,
        starting at the request time rather than at the next block boundary.
        """
        before = datetime.now(timezone.utc)

        request = _make_request(
            "zero-delay-immediate",
            duration=f"PT{REQUEST_DURATION_SECONDS}S",
        )
        response = _submit(client, request)

        avail_start, avail_duration = _available_window(response.offers[0])
        offset = (avail_start - before).total_seconds()

        assert offset >= 0.0, (
            f"Offer start [{avail_start}] is before the request time "
            f"[{before}]"
        )
        assert offset <= ROUND_TRIP_SECONDS, (
            f"Offer start [{avail_start}] is {offset:.0f}s after the request "
            f"time [{before}], the block rounding delay is still there"
        )
        assert avail_duration.total_seconds() >= REQUEST_DURATION_SECONDS, (
            f"Offer duration [{avail_duration}] is shorter than the "
            f"requested duration [{REQUEST_DURATION_SECONDS}s]"
        )
