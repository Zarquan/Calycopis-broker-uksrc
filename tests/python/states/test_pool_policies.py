#
# <meta:header>
#   <meta:licence>
#     Copyright (C) 2026 University of Manchester.
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
#     "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
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
#

"""
State tests for the choices a pool policy makes during booking.

These tests use the `database` fixture to check which pool an offered
reservation came from, because the offer response does not expose the pool.
They disable the seeded default pool so the routing is unambiguous, and
disable every pool they create before they finish.
"""

import base64
import json
import urllib.error
import urllib.request
import uuid

import pytest

from calycopis_conftest import (
    admin_credentials,
)
from calycopis_openapi_client.models import (
    ComponentMetadata,
    DockerImageSpec,
    ExecutionRequest,
    OfferSetResponse,
    RequestedScheduleBlock,
    RequestedScheduleItem,
    SimpleComputeCores,
    SimpleComputeMemory,
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
)

POOLS_PATH = "/admin/pools/compute"
ALLOCATIONS_PATH = "/admin/allocations/compute"
POLICIES_PATH = "/admin/policies/compute"

CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

BROKER_URL = None


@pytest.fixture(autouse=True)
def _use_broker_url(broker_url):
    """Make the broker URL available to the raw HTTP helpers."""
    global BROKER_URL
    BROKER_URL = broker_url


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Keep a 303 visible instead of following it to the new resource."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_OPENER = urllib.request.build_opener(_NoRedirect)


def _admin_auth() -> dict:
    """The basic-auth header for the broker admin."""
    username, password = admin_credentials()
    credentials = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    return {"Authorization": f"Basic {credentials}"}


def _call(method: str, path: str, body=None, headers=None):
    """Make a request and return (status, headers, body text).

    HTTP errors are returned rather than raised, because the status codes are
    what these tests assert. Redirects are not followed.
    """
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        BROKER_URL + path,
        data=data,
        method=method,
    )
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with _OPENER.open(request, timeout=10) as response:
            return response.status, dict(response.headers), response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode("utf-8")


def _default_pool() -> dict:
    """The default pool, which the startup seed creates."""
    status, _, body = _call("GET", POOLS_PATH, headers=_admin_auth())
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    for pool in json.loads(body):
        if pool["name"] == "default":
            return pool
    raise AssertionError("The seeded default pool is missing from the pool list")


def _create_pool(name: str, priority: int) -> str:
    """Create an enabled pool at the given priority and return its identifier."""
    status, _, body = _call(
        "POST",
        POOLS_PATH,
        body={"name": name, "description": "A pool for policy routing tests."},
        headers=_admin_auth(),
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    identifier = json.loads(body)["uuid"]

    status, _, body = _call(
        "POST",
        f"{POOLS_PATH}/{identifier}",
        body={"priority": priority, "enabled": True},
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    return identifier


def _create_allocation(pool_uuid: str, cores: int, memory: int) -> None:
    """Create an unbounded allocation for a pool."""
    status, _, body = _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}/allocations",
        body={"cores": cores, "memory": memory},
        headers=_admin_auth(),
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"


def _create_policy(pool_uuid: str, body: dict) -> None:
    """Create a policy for a pool."""
    status, _, response = _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}/policies",
        body=body,
        headers=_admin_auth(),
    )
    assert status == 201, f"Expected 201 Created, got {status}: {response}"


def _set_pool(pool_uuid: str, priority: int, enabled: bool) -> None:
    """Set a pool's priority and enabled flag."""
    status, _, body = _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}",
        body={"priority": priority, "enabled": enabled},
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"


def _request(memory: int) -> ExecutionRequest:
    """A request for a fixed amount of memory in one block."""
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name="policy-routing-exec"),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=["5", "0"],
        ),
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name="policy-routing-compute"),
            cores=SimpleComputeCores(min=2, max=2),
            memory=SimpleComputeMemory(min=memory, max=memory),
        ),
        schedule=RequestedScheduleBlock(
            requested=RequestedScheduleItem(
                duration="PT300S",
                start=None,
            ),
        ),
    )


def _submit(client, memory: int) -> OfferSetResponse:
    """Submit a request and return its offer set."""
    response = client.submit_execution(_request(memory), follow_redirect=True)
    assert isinstance(response, OfferSetResponse), (
        f"Expected OfferSetResponse, got {type(response)}"
    )
    return response


def _reject_offers(client, response: OfferSetResponse) -> None:
    """Reject every offer in a YES response."""
    for offer in response.offers or []:
        client.set_session_phase(
            offer.meta.uuid,
            SimpleExecutionSessionPhase.REJECTED,
        )


def _offer_pools(response: OfferSetResponse, database) -> list:
    """The pool identifiers of every offered session, from the database."""
    pools = []
    for offer in response.offers or []:
        pools.append(str(database.reservation_pool(str(offer.meta.uuid))))
    return pools


def test_memory_floor_and_ceiling_route_to_the_right_pool(
    client,
    database,
    clean_broker,
):
    """A floor sends large requests to one pool and a ceiling small ones to another."""
    default = _default_pool()
    high_pool = None
    low_pool = None
    try:
        _set_pool(default["uuid"], default["priority"], False)

        high_pool = _create_pool("policy-high-" + uuid.uuid4().hex, 1)
        _create_allocation(high_pool, 64, 16)
        _create_policy(
            high_pool,
            {"kind": "memory-floor", "name": "floor-8", "minimumMemory": 8},
        )

        low_pool = _create_pool("policy-low-" + uuid.uuid4().hex, 2)
        _create_allocation(low_pool, 64, 4)
        _create_policy(
            low_pool,
            {"kind": "memory-ceiling", "name": "ceiling-4", "maximumMemory": 4},
        )

        large = _submit(client, 8)
        _reject_offers(client, large)
        assert large.result == "YES", f"Expected YES, got {large.result}"
        assert large.offers, "A large request should get an offer"
        assert set(_offer_pools(large, database)) == {high_pool}

        small = _submit(client, 2)
        _reject_offers(client, small)
        assert small.result == "YES", f"Expected YES, got {small.result}"
        assert small.offers, "A small request should get an offer"
        assert set(_offer_pools(small, database)) == {low_pool}

        middle = _submit(client, 6)
        _reject_offers(client, middle)
        assert middle.result == "NO", f"Expected NO, got {middle.result}"
        assert not middle.offers, "A request in the gap should get no offers"
        messages = middle.meta.messages or []
        declined = [
            message
            for message in messages
            if message.kind == "urn:compute-pool-declined"
        ]
        assert len(declined) == 2, (
            f"Both pools should record a decline, got {messages}"
        )
        assert any(
            message.kind.startswith("urn:compute-policy-")
            for message in messages
        ), f"A policy reason should be recorded, got {messages}"
    finally:
        _set_pool(default["uuid"], default["priority"], True)
        if high_pool is not None:
            _set_pool(high_pool, 1, False)
        if low_pool is not None:
            _set_pool(low_pool, 2, False)


def test_priority_puts_the_lower_value_first(
    client,
    database,
    clean_broker,
):
    """Two permissive pools bid, and the lower priority number comes first."""
    default = _default_pool()
    first_pool = None
    second_pool = None
    try:
        _set_pool(default["uuid"], default["priority"], False)

        first_pool = _create_pool("policy-first-" + uuid.uuid4().hex, 1)
        _create_allocation(first_pool, 64, 16)
        second_pool = _create_pool("policy-second-" + uuid.uuid4().hex, 2)
        _create_allocation(second_pool, 64, 16)

        response = _submit(client, 2)
        _reject_offers(client, response)
        assert response.result == "YES", f"Expected YES, got {response.result}"
        assert response.offers, "Both pools should produce offers"
        first = database.reservation_pool(str(response.offers[0].meta.uuid))
        assert str(first) == first_pool, (
            f"The first offer should come from the lower-priority pool "
            f"[{first_pool}], got [{first}]"
        )
    finally:
        _set_pool(default["uuid"], default["priority"], True)
        if first_pool is not None:
            _set_pool(first_pool, 1, False)
        if second_pool is not None:
            _set_pool(second_pool, 2, False)
