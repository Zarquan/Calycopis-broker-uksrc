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
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
# AIMetrics: [
#     {
#     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
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
Integration tests for the admin compute allocation endpoints.

Allocations are off-schema admin resources, so these tests use urllib directly.
They cover the collection under a pool, the item by UUID, the authority rules,
the values the controller refuses, and the effect allocations have on the
available supply.

Requires:
  - A running Calycopis broker.
  - The admin credentials from admin.yaml, which conftest reads.
  - The test identities seeded by conftest.py.
"""

import base64
import json
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

import pytest

from calycopis_conftest import admin_credentials
from calycopis_openapi_client.models import (
    ComponentMetadata,
    DockerImageSpec,
    ExecutionRequest,
    RequestedScheduleBlock,
    RequestedScheduleItem,
    SimpleComputeCores,
    SimpleComputeMemory,
)
from calycopis_openapi_client.models.simple_execution_session_phase import (
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
)

POOLS_PATH = "/admin/pools/compute"
ALLOCATIONS_PATH = "/admin/allocations/compute"

CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

BROKER_URL = None


@pytest.fixture(autouse=True)
def _use_broker_url(broker_url):
    """Make the broker URL available to the raw HTTP helpers."""
    global BROKER_URL
    BROKER_URL = broker_url


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Keep a 303 visible instead of following it to the new resource."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_OPENER = urllib.request.build_opener(_NoRedirect)


def _auth_header(username: str, password: str) -> dict:
    """Build a basic-auth header for the given credentials."""
    credentials = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    return {"Authorization": f"Basic {credentials}"}


def _admin_auth() -> dict:
    """The basic-auth header for the broker admin."""
    username, password = admin_credentials()
    return _auth_header(username, password)


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
        method=method
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


def _pool_name() -> str:
    """A pool name that no other test run has used."""
    return "alloc-pool-" + uuid.uuid4().hex[:12]


def _create_pool(name: str):
    """Create a pool and return its identifier."""
    status, _, body = _call(
        "POST",
        POOLS_PATH,
        body={"name": name, "description": "A pool for allocation tests."},
        headers=_admin_auth()
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    return json.loads(body)["uuid"]


def _default_pool() -> dict:
    """The default pool, which the startup seed creates."""
    status, _, body = _call("GET", POOLS_PATH, headers=_admin_auth())
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    for pool in json.loads(body):
        if pool["name"] == "default":
            return pool
    raise AssertionError("The seeded default pool is missing from the pool list")


def _create_allocation(pool_uuid: str, body):
    """Create an allocation and return (status, headers, body)."""
    return _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}/allocations",
        body=body,
        headers=_admin_auth()
    )


def _request_with_compute(name: str, cores: int, memory: int) -> ExecutionRequest:
    """A request that asks for a fixed number of cores and GiB."""
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
            cores=SimpleComputeCores(min=cores, max=cores),
            memory=SimpleComputeMemory(min=memory, max=memory),
        ),
        schedule=RequestedScheduleBlock(
            requested=RequestedScheduleItem(
                duration="PT300S",
                start=None,
            ),
        ),
    )


def _reject_offers(client, response):
    """Reject every offer in a YES response, not only the first one."""
    for offer in response.offers:
        client.set_session_phase(
            offer.meta.uuid,
            SimpleExecutionSessionPhase.REJECTED,
        )


def _first_offer_uuid(response):
    """The session UUID of the first offer, or None if there is no offer."""
    if response.result != "YES" or not response.offers:
        return None
    return response.offers[0].meta.uuid


# ---------------------------------------------------------------------------
# Allocation CRUD
# ---------------------------------------------------------------------------

def test_create_allocation_redirects_to_its_details():
    """Creating an allocation answers 303 with the item's Location."""
    pool = _create_pool(_pool_name())
    status, headers, body = _create_allocation(
        pool,
        {"cores": 8, "memory": 16}
    )

    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    location = headers.get("Location", "")
    prefix = f"{ALLOCATIONS_PATH}/"
    assert location.startswith(prefix), (
        f"Expected a Location under [{prefix}], got [{location}]"
    )
    identifier = location[len(prefix):]
    uuid.UUID(identifier)

    status, _, body = _call(
        "GET",
        f"{ALLOCATIONS_PATH}/{identifier}",
        headers=_admin_auth()
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    payload = json.loads(body)
    assert payload["uuid"] == identifier
    assert payload["pool"] == pool
    assert payload["cores"] == 8
    assert payload["memory"] == 16
    assert payload["end"] is None


def test_list_contains_the_seeded_allocation():
    """The default pool has the allocation the startup seed created."""
    default = _default_pool()
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{default['uuid']}/allocations",
        headers=_admin_auth()
    )

    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    allocations = json.loads(body)
    seeded = [
        allocation
        for allocation in allocations
        if allocation["start"] == "1970-01-01T00:00:00Z"
        and allocation["end"] is None
    ]
    assert len(seeded) == 1, (
        f"The default pool should hold exactly one seeded allocation, got {allocations}"
    )
    assert seeded[0]["pool"] == default["uuid"]
    assert seeded[0]["cores"] > 0
    assert seeded[0]["memory"] > 0


def test_update_allocation():
    """A created allocation can be resized, and the new values are stored."""
    pool = _create_pool(_pool_name())
    status, headers, body = _create_allocation(
        pool,
        {"cores": 8, "memory": 16}
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    identifier = headers["Location"][len(ALLOCATIONS_PATH) + 1:]

    status, _, body = _call(
        "POST",
        f"{ALLOCATIONS_PATH}/{identifier}",
        body={"cores": 4, "memory": 8, "start": "2026-12-01T00:00:00Z"},
        headers=_admin_auth()
    )

    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    payload = json.loads(body)
    assert payload["cores"] == 4
    assert payload["memory"] == 8
    assert payload["start"] == "2026-12-01T00:00:00Z"


def test_delete_allocation():
    """Deleting an allocation answers 204, and it is gone."""
    pool = _create_pool(_pool_name())
    status, headers, body = _create_allocation(
        pool,
        {"cores": 8, "memory": 16}
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    identifier = headers["Location"][len(ALLOCATIONS_PATH) + 1:]

    status, _, body = _call(
        "DELETE",
        f"{ALLOCATIONS_PATH}/{identifier}",
        headers=_admin_auth()
    )
    assert status == 204, f"Expected 204 No Content, got {status}: {body}"

    status, _, body = _call(
        "GET",
        f"{ALLOCATIONS_PATH}/{identifier}",
        headers=_admin_auth()
    )
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_unknown_pool_is_404():
    """A collection request for a pool that does not exist is refused."""
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{uuid.uuid4()}/allocations",
        headers=_admin_auth()
    )
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_malformed_pool_uuid_is_400():
    """A collection request for a non-UUID pool identifier is refused."""
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/not-a-uuid/allocations",
        headers=_admin_auth()
    )
    assert status == 400, f"Expected 400 Bad Request, got {status}: {body}"


def test_unknown_allocation_is_404():
    """An item request for an allocation that does not exist is refused."""
    status, _, body = _call(
        "GET",
        f"{ALLOCATIONS_PATH}/{uuid.uuid4()}",
        headers=_admin_auth()
    )
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_malformed_allocation_uuid_is_400():
    """An item request for a non-UUID allocation identifier is refused."""
    status, _, body = _call(
        "GET",
        f"{ALLOCATIONS_PATH}/not-a-uuid",
        headers=_admin_auth()
    )
    assert status == 400, f"Expected 400 Bad Request, got {status}: {body}"


@pytest.mark.parametrize(
    "body",
    [
        {},
        {"cores": 8},
        {"memory": 8},
        {"cores": 0, "memory": 8},
        {"cores": -1, "memory": 8},
        {"cores": 8, "memory": 0},
        {"cores": 8, "memory": -1},
        {"cores": 8, "memory": 8, "end": "2026-12-01T00:00:00Z", "start": "2026-12-03T00:00:00Z"},
    ],
    ids=[
        "missing-both",
        "missing-memory",
        "missing-cores",
        "zero-cores",
        "negative-cores",
        "zero-memory",
        "negative-memory",
        "end-before-start",
    ],
)
def test_invalid_allocation_is_400(body):
    """An allocation without positive sizes, or with an impossible window, is refused."""
    pool = _create_pool(_pool_name())
    status, _, body_text = _create_allocation(pool, body)

    assert status == 400, f"Expected 400 Bad Request, got {status}: {body_text}"


# ---------------------------------------------------------------------------
# Authority
# ---------------------------------------------------------------------------

def test_unauthenticated_request_is_401():
    """Every allocation verb refuses a request with no credentials."""
    pool = _default_pool()
    unknown = uuid.uuid4()
    for method, path in (
        ("GET", f"{POOLS_PATH}/{pool['uuid']}/allocations"),
        ("POST", f"{POOLS_PATH}/{pool['uuid']}/allocations"),
        ("GET", f"{ALLOCATIONS_PATH}/{unknown}"),
        ("POST", f"{ALLOCATIONS_PATH}/{unknown}"),
        ("DELETE", f"{ALLOCATIONS_PATH}/{unknown}"),
    ):
        body = {"cores": 1, "memory": 1} if method == "POST" else None
        status, _, response = _call(method, path, body=body)
        assert status == 401, (
            f"{method} {path} without credentials returned {status}: {response}"
        )


def test_local_identity_is_403(alice_creds):
    """Every allocation verb refuses a local identity that is not the admin."""
    headers = _auth_header(alice_creds["username"], alice_creds["password"])
    pool = _default_pool()
    unknown = uuid.uuid4()
    for method, path in (
        ("GET", f"{POOLS_PATH}/{pool['uuid']}/allocations"),
        ("POST", f"{POOLS_PATH}/{pool['uuid']}/allocations"),
        ("GET", f"{ALLOCATIONS_PATH}/{unknown}"),
        ("POST", f"{ALLOCATIONS_PATH}/{unknown}"),
        ("DELETE", f"{ALLOCATIONS_PATH}/{unknown}"),
    ):
        body = {"cores": 1, "memory": 1} if method == "POST" else None
        status, _, response = _call(method, path, body=body, headers=headers)
        assert status == 403, (
            f"{method} {path} as a local identity returned {status}: {response}"
        )


# ---------------------------------------------------------------------------
# Supply and over-commit
# ---------------------------------------------------------------------------

def test_supply_comes_from_the_default_allocation(client, clean_broker):
    """Shrinking the default allocation removes offers, and restoring it brings them back."""
    default = _default_pool()
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{default['uuid']}/allocations",
        headers=_admin_auth()
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    allocations = json.loads(body)
    seeded = [
        allocation
        for allocation in allocations
        if allocation["start"] == "1970-01-01T00:00:00Z"
        and allocation["end"] is None
    ]
    assert len(seeded) == 1, (
        f"The default pool should hold exactly one seeded allocation, got {allocations}"
    )
    allocation = seeded[0]
    others = [
        candidate
        for candidate in allocations
        if candidate["uuid"] != allocation["uuid"]
    ]

    # Keep the seeded allocation as the only supply, so the request cannot be
    # met by an allocation another test left behind.
    removed = []
    for other in others:
        status, _, response = _call(
            "DELETE",
            f"{ALLOCATIONS_PATH}/{other['uuid']}",
            headers=_admin_auth()
        )
        assert status == 204, f"Expected 204 No Content, got {status}: {response}"
        removed.append(other)

    seed = {
        "cores": allocation["cores"],
        "memory": allocation["memory"],
        "start": allocation["start"],
        "end": allocation["end"],
    }

    def _resize(cores: int, memory: int):
        status, _, response = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body={
                "cores": cores,
                "memory": memory,
                "start": seed["start"],
                "end": seed["end"],
            },
            headers=_admin_auth()
        )
        assert status == 200, f"Expected 200 OK, got {status}: {response}"

    offer_uuid = None
    try:
        _resize(1, 1)
        response = client.submit_execution(
            _request_with_compute("alloc-small", 4, 4),
            follow_redirect=True,
        )
        assert response.result == "NO", (
            "A request for 4 cores must not be offered from a 1 core allocation. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

        _resize(256, 256)
        response = client.submit_execution(
            _request_with_compute("alloc-restored", 4, 4),
            follow_redirect=True,
        )
        assert response.result == "YES", (
            "The restored allocation must supply offers again. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        offer_uuid = _first_offer_uuid(response)
    finally:
        _reject_offers(client, response)
        _resize(seed["cores"], seed["memory"])
        for other in removed:
            status, _, response = _create_allocation(
                default["uuid"],
                {
                    "cores": other["cores"],
                    "memory": other["memory"],
                    "start": other["start"],
                    "end": other["end"],
                }
            )
            assert status == 303, f"Failed to restore an allocation: {status}: {response}"


def test_future_and_ended_allocations_do_not_supply_the_current_block(client, clean_broker):
    """An allocation that has not started, or has ended, does not add supply now."""
    default = _default_pool()
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{default['uuid']}/allocations",
        headers=_admin_auth()
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    allocations = json.loads(body)
    seeded = [
        candidate
        for candidate in allocations
        if candidate["start"] == "1970-01-01T00:00:00Z"
        and candidate["end"] is None
    ]
    assert len(seeded) == 1, (
        f"The default pool should hold exactly one seeded allocation, got {allocations}"
    )
    allocation = seeded[0]
    seed = {
        "cores": allocation["cores"],
        "memory": allocation["memory"],
        "start": allocation["start"],
        "end": allocation["end"],
    }

    now = datetime.now(timezone.utc)
    tomorrow = (now + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    yesterday = (now - timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")

    created = []
    offer_uuid = None
    try:
        # Remove the seeded allocation's supply so only the new windows could
        # make the request fit.
        status, _, body = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body={
                "cores": 1,
                "memory": 1,
                "start": seed["start"],
                "end": seed["end"],
            },
            headers=_admin_auth()
        )
        assert status == 200, f"Expected 200 OK, got {status}: {body}"

        for body in (
            {"cores": 256, "memory": 256, "start": tomorrow, "end": None},
            {"cores": 256, "memory": 256, "start": seed["start"], "end": yesterday},
        ):
            status, headers, response = _create_allocation(default["uuid"], body)
            assert status == 303, f"Expected 303 See Other, got {status}: {response}"
            created.append(headers["Location"][len(ALLOCATIONS_PATH) + 1:])

        response = client.submit_execution(
            _request_with_compute("alloc-window", 4, 4),
            follow_redirect=True,
        )
        assert response.result == "NO", (
            "Allocations outside the current block must not supply the request. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

        status, _, body = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body={
                "cores": 256,
                "memory": 256,
                "start": seed["start"],
                "end": seed["end"],
            },
            headers=_admin_auth()
        )
        assert status == 200, f"Expected 200 OK, got {status}: {body}"
        response = client.submit_execution(
            _request_with_compute("alloc-window-restored", 4, 4),
            follow_redirect=True,
        )
        assert response.result == "YES", (
            "The restored allocation must supply offers again. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        offer_uuid = _first_offer_uuid(response)
    finally:
        _reject_offers(client, response)
        status, _, body = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body=seed,
            headers=_admin_auth()
        )
        assert status == 200, f"Failed to restore the allocation: {status}: {body}"
        for identifier in created:
            status, _, body = _call(
                "DELETE",
                f"{ALLOCATIONS_PATH}/{identifier}",
                headers=_admin_auth()
            )
            assert status == 204, f"Failed to remove a test allocation: {status}: {body}"


def test_delete_below_a_live_reservation_is_409(client, clean_broker):
    """A delete that would leave a live reservation uncovered is refused."""
    pool = _create_pool(_pool_name())
    status, headers, body = _create_allocation(
        pool,
        {"cores": 8, "memory": 8}
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    identifier = headers["Location"][len(ALLOCATIONS_PATH) + 1:]

    offer_uuid = None
    try:
        response = client.submit_execution(
            _request_with_compute("alloc-delete-overcommit", 4, 4),
            follow_redirect=True,
        )
        offer_uuid = _first_offer_uuid(response)
        assert offer_uuid is not None, (
            "The offer used to hold a reservation was not created. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

        status, _, body = _call(
            "DELETE",
            f"{ALLOCATIONS_PATH}/{identifier}",
            headers=_admin_auth()
        )
        assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
        status, _, body = _call(
            "GET",
            f"{ALLOCATIONS_PATH}/{identifier}",
            headers=_admin_auth()
        )
        assert status == 200, f"The refused allocation should remain: {status}: {body}"
        assert json.loads(body)["cores"] == 8, (
            f"The refused allocation changed: {body}"
        )
    finally:
        _reject_offers(client, response)
        deadline = time.time() + 10
        while True:
            status, _, body = _call(
                "DELETE",
                f"{ALLOCATIONS_PATH}/{identifier}",
                headers=_admin_auth()
            )
            if status == 204:
                break
            if time.time() > deadline:
                assert False, f"Failed to delete the test allocation: {status}: {body}"
            time.sleep(0.5)


def test_shrinking_below_a_live_reservation_is_409(client, clean_broker):
    """An update that would leave a live reservation uncovered is refused."""
    default = _default_pool()
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{default['uuid']}/allocations",
        headers=_admin_auth()
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    allocations = json.loads(body)
    seeded = [
        candidate
        for candidate in allocations
        if candidate["start"] == "1970-01-01T00:00:00Z"
        and candidate["end"] is None
    ]
    assert len(seeded) == 1, (
        f"The default pool should hold exactly one seeded allocation, got {allocations}"
    )
    allocation = seeded[0]
    removed = []
    for other in allocations:
        if other["uuid"] == allocation["uuid"]:
            continue
        status, _, response = _call(
            "DELETE",
            f"{ALLOCATIONS_PATH}/{other['uuid']}",
            headers=_admin_auth()
        )
        assert status == 204, f"Expected 204 No Content, got {status}: {response}"
        removed.append(other)

    seed = {
        "cores": allocation["cores"],
        "memory": allocation["memory"],
        "start": allocation["start"],
        "end": allocation["end"],
    }

    offer_uuid = None
    try:
        status, _, body = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body={
                "cores": 256,
                "memory": 256,
                "start": seed["start"],
                "end": seed["end"],
            },
            headers=_admin_auth()
        )
        assert status == 200, f"Expected 200 OK, got {status}: {body}"

        response = client.submit_execution(
            _request_with_compute("alloc-overcommit", 4, 4),
            follow_redirect=True,
        )
        offer_uuid = _first_offer_uuid(response)
        assert offer_uuid is not None, (
            "The offer used to hold a reservation was not created. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

        status, _, body = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body={
                "cores": 1,
                "memory": 1,
                "start": seed["start"],
                "end": seed["end"],
            },
            headers=_admin_auth()
        )
        assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
        status, _, body = _call(
            "GET",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            headers=_admin_auth()
        )
        assert status == 200, f"The refused allocation should remain: {status}: {body}"
        assert json.loads(body)["cores"] == 256, (
            f"The refused allocation changed: {body}"
        )
    finally:
        _reject_offers(client, response)
        status, _, body = _call(
            "POST",
            f"{ALLOCATIONS_PATH}/{allocation['uuid']}",
            body=seed,
            headers=_admin_auth()
        )
        assert status == 200, f"Failed to restore the allocation: {status}: {body}"
        for other in removed:
            status, _, response = _create_allocation(
                default["uuid"],
                {
                    "cores": other["cores"],
                    "memory": other["memory"],
                    "start": other["start"],
                    "end": other["end"],
                }
            )
            assert status == 303, f"Failed to restore an allocation: {status}: {response}"
