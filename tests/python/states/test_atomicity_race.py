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
#     "interval": "2026-10-15T06:20:00/2026-10-15T07:50:00",
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
The atomicity race in offer generation (issue #159).

Offer generation is check-then-act. The booking query runs in
``OfferSetEntityFactoryImpl.create`` -> ``stageTwo``, but the offer set and the
compute resource's reservation window are only committed by the save at the end
of ``create``. Two requests that overlap between the query and that save both
see the same free block, and both are offered it.

This test demonstrates the race. It needs a broker that is configured to make
the window observable:

* ``calycopis.broker.booking.capacity.cores: 1`` (and ``memory: 1``), so a
  single 1-core allocation fills a block and a second overlapping offer is a
  violation, rather than something the capacity absorbs silently.
* ``calycopis.broker.testing.enabled: true`` and
  ``calycopis.broker.testing.delays.booking-query-to-save: 5`` (seconds), so the
  window between the booking query and the save is held open long enough for a
  second request to reach its own query.

See ``docs/deployment.md``, "Running the atomicity race test", for how to start
a broker with that configuration. The test skips, naming the missing setting,
when the broker is not configured for it.

While the race exists the test is marked ``xfail(strict=True)``: it fails when
the race is demonstrated, and ``strict=True`` turns an unexpected pass into a
failure, so the marker has to be removed when the race is fixed. An ``XPASS``
means the race was not reproduced on that run, which is the decision point in
issue #159: either the delay is too short, or the race is not reachable and
issue #156's atomicity question should be closed as a documented assumption.
"""

import json
import os
import urllib.request
from concurrent.futures import ThreadPoolExecutor

import pytest

from calycopis_conftest import CALYCOPIS_URL

from calycopis_openapi_client.models import (
    ExecutionRequest,
    OfferSetResponse,
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

# The capacity the race test needs the broker to be started with.
RACE_CAPACITY_CORES = 1
RACE_CAPACITY_MEMORY = 1

# The number of concurrent requests that have to contend for one block.
RACE_REQUESTS = 2

# How many of those requests are submitted at once. Setting
# RACE_SUBMIT_WORKERS=1 submits them one after another, which is the control
# run: the second request then sees the first request's reservation and is
# displaced, so the invariant holds and the test passes. Run the control with
# --runxfail, because the strict xfail marker turns an expected pass into a
# failure.
RACE_SUBMIT_WORKERS = int(
    os.environ.get("RACE_SUBMIT_WORKERS", str(RACE_REQUESTS))
)

# Heliophorus-cantliei test container, the same image the docker tests use.
# The test never accepts an offer, so the container is never run.
CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

RACE_CONFIG_HELP = (
    "The atomicity race test needs a broker started with "
    "calycopis.broker.testing.enabled=true, "
    "calycopis.broker.testing.delays.booking-query-to-save set to a few seconds, "
    "and calycopis.broker.booking.capacity cores and memory set to "
    f"{RACE_CAPACITY_CORES}. See docs/deployment.md, "
    "'Running the atomicity race test'."
)


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _broker_info():
    """The broker's actuator info details."""
    with urllib.request.urlopen(
        f"{CALYCOPIS_URL}/actuator/info",
        timeout=30,
    ) as response:
        return json.load(response)


def _race_capability():
    """
    The capacity and testing state the broker was started with, read from the
    info endpoint rather than assumed.
    """
    info = _broker_info()
    testing = info.get("testing") or {}
    booking = info.get("booking") or {}
    capacity = booking.get("capacity") or {}
    return {
        "enabled": bool(testing.get("enabled")),
        "delay": testing.get("booking-query-to-save-delay-seconds"),
        "cores": capacity.get("cores"),
        "memory": capacity.get("memory"),
    }


def _immediate_request(name):
    """A 1-core immediate request for the cantliei container."""
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=name),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=["1", "0"],
        ),
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name=f"{name}-compute"),
            cores=SimpleComputeCores(
                min=RACE_CAPACITY_CORES,
                max=RACE_CAPACITY_CORES,
            ),
            memory=SimpleComputeMemory(
                min=RACE_CAPACITY_MEMORY,
                max=RACE_CAPACITY_MEMORY,
            ),
        ),
    )


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def race_capability():
    """
    The broker's race configuration, or a skip naming what is missing.
    """
    capability = _race_capability()
    if not capability["enabled"]:
        pytest.skip(f"The testing mechanism is disabled. {RACE_CONFIG_HELP}")
    if (
        capability["cores"] != RACE_CAPACITY_CORES
        or capability["memory"] != RACE_CAPACITY_MEMORY
    ):
        pytest.skip(
            "The broker capacity is "
            f"{capability['cores']} cores / {capability['memory']} GiB. "
            f"{RACE_CONFIG_HELP}"
        )
    if not capability["delay"]:
        pytest.skip(
            "The booking-query-to-save delay is zero, so the window cannot be "
            f"held open. {RACE_CONFIG_HELP}"
        )
    return capability


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_broker_is_race_capable(race_capability):
    """
    The broker is configured so the race is observable. This runs before the
    race test, and fails loudly rather than letting the race test report a
    result it cannot justify.
    """
    assert race_capability["cores"] == RACE_CAPACITY_CORES
    assert race_capability["delay"] > 0


@pytest.mark.xfail(
    strict=True,
    reason=(
        "The in-flight atomicity race is not fixed yet (issue #156). Two "
        "concurrent immediate requests can both be offered the block that "
        "contains the requested start, over-subscribing the capacity."
    ),
)
def test_concurrent_immediate_requests_do_not_oversubscribe(
    client,
    database,
    race_capability,
):
    """
    Two concurrent immediate requests must not reserve more than the capacity
    in any block of time.

    Submitting both requests at once, with the booking-query-to-save window held
    open, lets the second request reach its own booking query before the first
    request's reservation is committed. When that happens both offers cover the
    block that contains the requested start, and the reserved cores for that
    block exceed the capacity.

    When the requests are serialised instead, the second request sees the first
    request's reservation and is displaced (with this capacity and a two hour
    requested duration, it is displaced out of the whole search range and gets
    no offer at all), so the invariant holds and the test passes. Under
    ``xfail(strict=True)`` that pass is reported as an unexpected pass, which is
    the signal that the race was not reproduced.
    """
    requests = [
        _immediate_request(f"race-{index}")
        for index in range(RACE_REQUESTS)
        ]

    def submit(request):
        response = client.submit_execution(
            request,
            follow_redirect=True,
        )
        assert isinstance(response, OfferSetResponse), (
            f"Expected an OfferSetResponse, got {type(response)}"
        )
        return response

    with ThreadPoolExecutor(max_workers=RACE_SUBMIT_WORKERS) as pool:
        responses = list(
            pool.map(submit, requests)
            )

    offered = [
        response.offers[0]
        for response in responses
        if response.offers
        ]

    reserved = database.max_reserved_cores_per_block()
    capacity = race_capability["cores"]

    assert reserved <= capacity, (
        f"The broker reserved {reserved} cores in one block against a capacity "
        f"of {capacity} core(s). {len(offered)} of {RACE_REQUESTS} concurrent "
        "immediate requests were offered a session, and their availability "
        "windows overlap the same block. This is the in-flight atomicity race: "
        "the offer set is committed after the booking query, so a request that "
        "arrives during that window sees the block as free."
        )
