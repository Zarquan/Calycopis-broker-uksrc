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
#     "interval": "2026-11-16T07:05:00/2026-11-16T08:20:00",
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
Integration tests for the admin compute pool endpoints.

The pools are off-schema admin resources, so there is no generated client for
them and these tests use urllib directly. They cover the three verbs, the
authority rules, and the input the controller refuses.

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

BROKER_URL = None


@pytest.fixture(autouse=True)
def _use_broker_url(broker_url):
    """Make the broker URL available to the raw HTTP helpers."""
    global BROKER_URL
    BROKER_URL = broker_url


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

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
    what these tests assert.
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
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, dict(response.headers), response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode("utf-8")


def _pool_name() -> str:
    """A pool name that no other test run has used."""
    return "pool-" + uuid.uuid4().hex[:12]


def _create_pool(name: str, description: str = "A test pool."):
    """Create a pool as the admin and return (status, headers, body)."""
    return _call(
        "POST",
        POOLS_PATH,
        body={"name": name, "description": description},
        headers=_admin_auth()
    )


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_admin_can_create_a_pool():
    """The admin creates a pool, and the response describes it."""
    name = _pool_name()
    status, headers, body = _create_pool(name, "The pool this test created.")

    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    payload = json.loads(body)
    assert payload["name"] == name
    assert payload["description"] == "The pool this test created."
    assert payload["uuid"]
    assert payload["created"]
    assert headers.get("Location", "").endswith(
        f"{POOLS_PATH}/{payload['uuid']}"
    ), f"Expected a Location header for the new pool, got {headers.get('Location')!r}"


def test_created_pool_is_readable():
    """A pool can be read back by the identifier the response gave."""
    name = _pool_name()
    status, _, body = _create_pool(name)
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    created = json.loads(body)

    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{created['uuid']}",
        headers=_admin_auth()
    )

    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    payload = json.loads(body)
    assert payload["uuid"] == created["uuid"]
    assert payload["name"] == name


def test_pool_list_contains_the_default():
    """The pool list holds the pool the startup seed created."""
    status, _, body = _call("GET", POOLS_PATH, headers=_admin_auth())

    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    pools = json.loads(body)
    names = [pool["name"] for pool in pools]
    assert "default" in names, f"The seeded default pool is missing from {names}"


def test_unauthenticated_request_is_401():
    """Every verb refuses a request with no credentials."""
    unknown = uuid.uuid4()
    for method, path in (
        ("POST", POOLS_PATH),
        ("GET", POOLS_PATH),
        ("GET", f"{POOLS_PATH}/{unknown}"),
    ):
        body = {"name": _pool_name()} if method == "POST" else None
        status, _, response = _call(method, path, body=body)
        assert status == 401, (
            f"{method} {path} without credentials returned {status}: {response}"
        )


def test_local_identity_is_403(alice_creds):
    """Every verb refuses an authenticated identity that is not the admin.

    The GET verb is the regression test for the matcher order: a blanket GET
    rule placed above the admin rule would let it through.
    """
    headers = _auth_header(alice_creds["username"], alice_creds["password"])
    unknown = uuid.uuid4()
    for method, path in (
        ("POST", POOLS_PATH),
        ("GET", POOLS_PATH),
        ("GET", f"{POOLS_PATH}/{unknown}"),
    ):
        body = {"name": _pool_name()} if method == "POST" else None
        status, _, response = _call(method, path, body=body, headers=headers)
        assert status == 403, (
            f"{method} {path} as a local identity returned {status}: {response}"
        )


def test_duplicate_name_is_409():
    """A second pool with a name already in use is refused."""
    name = _pool_name()
    status, _, body = _create_pool(name)
    assert status == 201, f"Expected 201 Created, got {status}: {body}"

    status, _, body = _create_pool(name)

    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"


@pytest.mark.parametrize(
    "body",
    [
        {},
        {"name": ""},
        {"name": "   "},
        {"description": "No name here."},
    ],
    ids=["missing", "empty", "spaces", "only-description"],
)
def test_blank_name_is_400(body):
    """A pool without a usable name is refused."""
    status, _, response = _call(
        "POST",
        POOLS_PATH,
        body=body,
        headers=_admin_auth()
    )

    assert status == 400, f"Expected 400 Bad Request, got {status}: {response}"


def test_unknown_uuid_is_404():
    """A pool identifier that matches nothing is refused."""
    status, _, response = _call(
        "GET",
        f"{POOLS_PATH}/{uuid.uuid4()}",
        headers=_admin_auth()
    )

    assert status == 404, f"Expected 404 Not Found, got {status}: {response}"


def test_malformed_uuid_is_400():
    """An identifier that is not a UUID is refused rather than becoming a 500."""
    status, _, response = _call(
        "GET",
        f"{POOLS_PATH}/not-a-uuid",
        headers=_admin_auth()
    )

    assert status == 400, f"Expected 400 Bad Request, got {status}: {response}"
