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
Integration tests for the admin compute pool priority, enabled flag and policy
endpoints.

Policies are off-schema admin resources, so these tests use urllib directly.
They cover the pool update, the policy collection under a pool, the individual
policy, the values the controller refuses, and the authority rules.

Requires:
  - A running Calycopis broker.
  - The admin credentials from admin.yaml, which conftest reads.
  - The test identities seeded by conftest.py.
"""

import base64
import json
import urllib.error
import urllib.request
import uuid

import pytest

from calycopis_conftest import admin_credentials

POOLS_PATH = "/admin/pools/compute"
POLICIES_PATH = "/admin/policies/compute"

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


def _pool_name() -> str:
    """A pool name that no other test run has used."""
    return "policy-pool-" + uuid.uuid4().hex[:12]


def _create_pool(name: str) -> str:
    """Create a pool, disable it so it cannot bid in other tests, and return it."""
    status, _, body = _call(
        "POST",
        POOLS_PATH,
        body={"name": name, "description": "A pool for policy tests."},
        headers=_admin_auth(),
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    identifier = json.loads(body)["uuid"]
    status, _, body = _call(
        "POST",
        f"{POOLS_PATH}/{identifier}",
        body={"enabled": False},
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    return identifier


def _policy_body(kind: str, name: str, **values) -> dict:
    """Build a policy request body from the shared fields and the given values."""
    body = {"kind": kind, "name": name}
    body.update(values)
    return body


def _create_policy(pool_uuid: str, body: dict):
    """Create a policy and return (status, headers, body)."""
    return _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}/policies",
        body=body,
        headers=_admin_auth(),
    )


def _policy_id_from_headers(headers) -> str:
    """Extract the policy identifier from the create response Location."""
    location = headers.get("Location", "")
    prefix = f"{POLICIES_PATH}/"
    identifier = location[len(prefix):]
    uuid.UUID(identifier)
    return identifier


# ---------------------------------------------------------------------------
# Pool priority and enabled flag
# ---------------------------------------------------------------------------

def test_pool_update_changes_priority_and_enabled():
    """A pool starts enabled at priority zero and can be updated."""
    pool_uuid = _create_pool(_pool_name())

    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{pool_uuid}",
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    payload = json.loads(body)
    assert payload["priority"] == 0
    assert payload["enabled"] is False

    status, _, body = _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}",
        body={"priority": 9, "enabled": True},
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    payload = json.loads(body)
    assert payload["priority"] == 9
    assert payload["enabled"] is True


def test_pool_update_rejects_negative_priority():
    """A negative priority is refused."""
    pool_uuid = _create_pool(_pool_name())

    status, _, body = _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}",
        body={"priority": -1},
        headers=_admin_auth(),
    )
    assert status == 400, f"Expected 400 Bad Request, got {status}: {body}"


# ---------------------------------------------------------------------------
# Policy CRUD
# ---------------------------------------------------------------------------

def test_policy_crud():
    """Create, list, select, update and delete one policy."""
    pool_uuid = _create_pool(_pool_name())

    status, headers, body = _create_policy(
        pool_uuid,
        _policy_body("memory-floor", "floor-128", minimumMemory=128),
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    policy_uuid = _policy_id_from_headers(headers)
    created = json.loads(body)
    assert created["uuid"] == policy_uuid
    assert created["pool"] == pool_uuid
    assert created["kind"] == "memory-floor"
    assert created["name"] == "floor-128"
    assert created["minimumMemory"] == 128

    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{pool_uuid}/policies",
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    listed = json.loads(body)
    assert [item["uuid"] for item in listed] == [policy_uuid]

    status, _, body = _call(
        "GET",
        f"{POLICIES_PATH}/{policy_uuid}",
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    assert json.loads(body)["name"] == "floor-128"

    status, _, body = _call(
        "POST",
        f"{POLICIES_PATH}/{policy_uuid}",
        body=_policy_body("memory-floor", "floor-128", minimumMemory=256),
        headers=_admin_auth(),
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    assert json.loads(body)["minimumMemory"] == 256

    status, _, body = _call(
        "DELETE",
        f"{POLICIES_PATH}/{policy_uuid}",
        headers=_admin_auth(),
    )
    assert status == 204, f"Expected 204 No Content, got {status}: {body}"

    status, _, body = _call(
        "GET",
        f"{POLICIES_PATH}/{policy_uuid}",
        headers=_admin_auth(),
    )
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_policy_kind_can_be_upper_case():
    """The request accepts an upper-case kind and stores the canonical value."""
    pool_uuid = _create_pool(_pool_name())

    status, _, body = _create_policy(
        pool_uuid,
        _policy_body("MEMORY_CEILING", "ceiling-64", maximumMemory=64),
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    assert json.loads(body)["kind"] == "memory-ceiling"


def test_policy_name_in_use_is_409():
    """Two policies in one pool cannot share a name."""
    pool_uuid = _create_pool(_pool_name())
    status, _, body = _create_policy(
        pool_uuid,
        _policy_body("memory-floor", "floor", minimumMemory=64),
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"

    status, _, body = _create_policy(
        pool_uuid,
        _policy_body("memory-ceiling", "floor", maximumMemory=128),
    )
    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"


@pytest.mark.parametrize(
    "body",
    [
        {"kind": "memory-floor", "name": "missing-floor"},
        {"kind": "memory-floor", "name": "negative-floor", "minimumMemory": -1},
        {"kind": "memory-floor", "name": "wrong-field", "maximumMemory": 64},
        {"kind": "time-window", "name": "overnight", "startHour": 17, "endHour": 9},
        {"kind": "not-a-kind", "name": "unknown"},
    ],
)
def test_invalid_policy_is_400(body):
    """A policy that does not fit its kind is refused."""
    pool_uuid = _create_pool(_pool_name())

    status, _, response = _create_policy(pool_uuid, body)
    assert status == 400, f"Expected 400 Bad Request, got {status}: {response}"


def test_unknown_pool_is_404():
    """Listing policies under an unknown pool is 404."""
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{uuid.uuid4()}/policies",
        headers=_admin_auth(),
    )
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_malformed_pool_uuid_is_400():
    """A malformed pool identifier is refused."""
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/not-a-uuid/policies",
        headers=_admin_auth(),
    )
    assert status == 400, f"Expected 400 Bad Request, got {status}: {body}"


def test_unknown_policy_is_404():
    """Selecting an unknown policy is 404."""
    status, _, body = _call(
        "GET",
        f"{POLICIES_PATH}/{uuid.uuid4()}",
        headers=_admin_auth(),
    )
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_unauthenticated_request_is_401():
    """The policy endpoints are closed to anonymous callers."""
    status, _, body = _call("GET", POLICIES_PATH)
    assert status == 401, f"Expected 401 Unauthorized, got {status}: {body}"


def test_local_identity_is_403(alice_creds):
    """A local identity is authenticated but not an admin."""
    credentials = base64.b64encode(
        f"{alice_creds['username']}:{alice_creds['password']}".encode("utf-8")
    ).decode("utf-8")

    status, _, body = _call(
        "GET",
        f"{POLICIES_PATH}/{uuid.uuid4()}",
        headers={"Authorization": f"Basic {credentials}"},
    )
    assert status == 403, f"Expected 403 Forbidden, got {status}: {body}"
