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
#     "interval": "2026-11-14T13:40:00/2026-11-14T13:52:00",
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
The new offer's own reservation window (issue #158, residual part).

The booking query measures the occupancy of the existing sessions over their
whole reservation: the hull of their preparing, available and releasing
windows. The offering side had the matching gap. A new offer's preparation was
not part of the free-run search, so the offer could prepare inside the release
window of a session that was finishing, and its own release could fall inside
another session's preparation. The query now requires the new offer's whole
reservation to be free before it offers the availability window.

This test pins that clearance. It needs a broker configured with

* ``calycopis.broker.booking.capacity.cores: 1`` and ``memory: 1``, so a single
  reservation fills a block and the next offer cannot share it.

See ``docs/deployment.md``, "Running the atomicity race test", for how to start
a broker with that configuration. The test skips, naming the missing setting,
when the broker is not configured for it.

The assertion is chosen so that it fails on the pre-fix query. Without the
new offer's preparation in the free-run search, the next offer's preparation
block is the last block of the previous reservation (``a_end - 1``), because
only the available window is checked. With the fix, the preparation block is
the first block after the previous reservation (``a_end``), or later.

This test only submits offer-set requests and inspects the response. It never
accepts an offer, so no lifecycle processing is triggered.
"""

import json
import math
import re
import urllib.request
from datetime import datetime

import pytest

from calycopis_conftest import CALYCOPIS_URL

from calycopis_openapi_client.models import (
    ExecutionRequest,
    OfferSetResponse,
    RequestedScheduleBlock,
    RequestedScheduleItem,
)
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.simple_compute_cores import SimpleComputeCores
from calycopis_openapi_client.models.simple_compute_memory import SimpleComputeMemory
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# The booking block size, in seconds.
BLOCK_STEP_SECONDS = 60

# The capacity the test needs the broker to be started with.
TEST_CAPACITY_CORES = 1
TEST_CAPACITY_MEMORY = 1

# The requested duration for both requests, ten blocks.
REQUEST_DURATION_SECONDS = 600

# Heliophorus-cantliei test container, the same image the docker tests use.
# The test never accepts an offer, so the container is never run.
CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

CONFIG_HELP = (
    "The reservation window test needs a broker started with "
    "calycopis.broker.booking.capacity cores and memory set to "
    f"{TEST_CAPACITY_CORES}. See docs/deployment.md, "
    "'Running the atomicity race test'."
)


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _broker_capacity():
    """The booking capacity the broker was started with, from its info endpoint."""
    with urllib.request.urlopen(
        f"{CALYCOPIS_URL}/actuator/info",
        timeout=30,
    ) as response:
        info = json.load(response)
    booking = info.get("booking") or {}
    capacity = booking.get("capacity") or {}
    return {
        "cores": capacity.get("cores"),
        "memory": capacity.get("memory"),
    }


def _make_request(name):
    """A 1-core immediate request for the cantliei container."""
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
            cores=SimpleComputeCores(
                min=TEST_CAPACITY_CORES,
                max=TEST_CAPACITY_CORES,
            ),
            memory=SimpleComputeMemory(
                min=TEST_CAPACITY_MEMORY,
                max=TEST_CAPACITY_MEMORY,
            ),
        ),
        schedule=RequestedScheduleBlock(
            requested=RequestedScheduleItem(
                duration=f"PT{REQUEST_DURATION_SECONDS}S",
            ),
        ),
    )


def _parse_iso8601_instant(value):
    """Parse a whole second ISO 8601 instant."""
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def _parse_iso8601_duration(value):
    """Parse an ISO 8601 duration into seconds."""
    match = re.match(
        r"^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$",
        value,
    )
    if not match:
        raise ValueError(f"Cannot parse ISO 8601 duration: {value}")
    days = int(match.group(1) or 0)
    hours = int(match.group(2) or 0)
    minutes = int(match.group(3) or 0)
    seconds = float(match.group(4) or 0)
    return ((days * 24 + hours) * 60 + minutes) * 60 + seconds


def _parse_iso8601_interval(value):
    """Parse an ISO 8601 start/duration interval into (start, seconds)."""
    left, right = value.split("/", 1)
    start = _parse_iso8601_instant(left)
    return (start, _parse_iso8601_duration(right))


def _block_of(instant):
    """The booking block that contains an instant."""
    return math.floor(instant.timestamp() / BLOCK_STEP_SECONDS)


def _reservation_span(offer):
    """
    The blocks an offer's whole reservation touches.

    The reservation is the hull of the preparing, available and releasing
    windows, rounded out to whole blocks, which is the same span the booking
    query measures for an existing session.
    """
    schedule = offer.compute.schedule
    assert schedule is not None, (
        "Offer compute resource should include a schedule"
    )

    prepare_start = _parse_iso8601_instant(schedule.preparing.start)
    prepare_seconds = _parse_iso8601_duration(schedule.preparing.duration)
    available_start, available_seconds = _parse_iso8601_interval(
        schedule.available.start
    )
    release_start = _parse_iso8601_instant(schedule.releasing.start)
    release_seconds = _parse_iso8601_duration(schedule.releasing.duration)

    hull_start = min(prepare_start, available_start, release_start)
    hull_end = max(
        prepare_start.timestamp() + prepare_seconds,
        available_start.timestamp() + available_seconds,
        release_start.timestamp() + release_seconds,
    )
    return (
        math.floor(hull_start.timestamp() / BLOCK_STEP_SECONDS),
        math.ceil(hull_end / BLOCK_STEP_SECONDS),
    )


def _request_span(response):
    """
    The blocks a request's whole response reserves.

    One request can return several offers, and every offer creates a session
    that holds its own reservation, so the request's reservation is the union
    of all of them.
    """
    spans = [
        _reservation_span(offer)
        for offer in response.offers
        ]
    return (
        min(span[0] for span in spans),
        max(span[1] for span in spans),
    )


def _submit(client, request):
    """Submit a request and return the OfferSetResponse."""
    response = client.submit_execution(request, follow_redirect=True)
    assert isinstance(response, OfferSetResponse), (
        f"Expected an OfferSetResponse, got {type(response)}"
    )
    return response


def _first_offer(response, name):
    """The first offered session, or a skip naming what the broker returned."""
    if response.result != "YES" or not response.offers:
        pytest.skip(
            f"The broker did not offer a session for [{name}]. "
            f"Result [{response.result}]. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
    return response.offers[0]


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def single_capacity():
    """
    The broker's single unit of capacity, or a skip naming what is missing.
    """
    capacity = _broker_capacity()
    if (
        capacity["cores"] != TEST_CAPACITY_CORES
        or capacity["memory"] != TEST_CAPACITY_MEMORY
    ):
        pytest.skip(
            "The broker capacity is "
            f"{capacity['cores']} cores / {capacity['memory']} GiB. "
            f"{CONFIG_HELP}"
        )
    return capacity


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_new_offer_does_not_prepare_inside_the_previous_reservation(
    client,
    clean_broker,
    single_capacity,
):
    """
    The next offer's preparation block must not fall inside the previous
    reservation.

    With a single unit of capacity, the previous reservation fills every block
    it touches, so the next offer has to start at or after the end of that
    reservation. Checking only the available window let the next offer prepare
    in the last block of the previous reservation, while that reservation was
    releasing. See issue #158.
    """
    first = _submit(
        client,
        _make_request("reservation-window-a"),
    )
    _first_offer(first, "reservation-window-a")
    _first_start, first_end = _request_span(first)

    second = _submit(
        client,
        _make_request("reservation-window-b"),
    )
    second_offer = _first_offer(second, "reservation-window-b")
    second_schedule = second_offer.compute.schedule
    assert second_schedule is not None, (
        "Offer compute resource should include a schedule"
    )
    assert second_schedule.preparing is not None, (
        "Compute schedule should include a preparing interval"
    )

    second_prepare_start = _parse_iso8601_instant(
        second_schedule.preparing.start
    )
    second_available_start, _second_available_seconds = _parse_iso8601_interval(
        second_schedule.available.start
    )

    assert _block_of(second_prepare_start) >= first_end, (
        "The next offer's preparation block "
        f"[{_block_of(second_prepare_start)}] is inside the previous "
        f"reservation [{_first_start}..{first_end}). The booking query must "
        "require the new offer's whole reservation to be free, not just its "
        "available window."
    )
    assert _block_of(second_available_start) >= first_end, (
        "The next offer's available block "
        f"[{_block_of(second_available_start)}] is inside the previous "
        f"reservation [{_first_start}..{first_end})."
    )
