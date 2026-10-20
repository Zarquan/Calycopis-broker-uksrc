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
#     "timestamp": "2026-10-20T11:57:26",
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
Integration tests for the RFC 9457 problem details error responses.

These tests inspect the raw HTTP response rather than the generated
client's exception classes, so they can assert the media type and the
problem document members directly.

Requires:
  - A running Calycopis broker with the RFC 9457 responses.
  - The test identities seeded by conftest.py.
"""

import base64
import json
import urllib.error
import urllib.request
import uuid

import pytest

from calycopis_openapi_client.models import SimpleExecutionSessionPhase
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.execution_request import ExecutionRequest
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
)

CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

PROBLEM_TYPE = "application/problem+json"

BROKER_URL = None


@pytest.fixture(autouse=True)
def _use_broker_url(broker_url):
    """Make the broker URL available to the raw HTTP helpers."""
    global BROKER_URL
    BROKER_URL = broker_url


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_request(name: str) -> ExecutionRequest:
    """Create a valid request that produces an offer without executing."""
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
    )


def _basic_auth(username: str, password: str) -> str:
    """Build a Basic authorization header value."""
    token = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    return f"Basic {token}"


def _request(
    method: str,
    path: str,
    username: str = None,
    password: str = None,
    data=None,
    accept: str = None,
):
    """Send a request and return the status, headers and body text."""
    body = None
    if data is not None:
        if isinstance(data, (dict, list)):
            body = json.dumps(data).encode("utf-8")
        elif isinstance(data, str):
            body = data.encode("utf-8")
        else:
            body = data

    request = urllib.request.Request(
        f"{BROKER_URL}{path}",
        data=body,
        method=method,
    )
    if data is not None:
        request.add_header("Content-Type", "application/json")
    if accept is not None:
        request.add_header("Accept", accept)
    if username is not None:
        request.add_header("Authorization", _basic_auth(username, password))

    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, response.headers, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read().decode("utf-8")


def _assert_problem(status, headers, body, expected_status, expected_type=None, expected_instance=None):
    """Assert the response is an RFC 9457 problem details document."""
    assert status == expected_status, (
        f"Expected HTTP {expected_status}, got {status}"
    )
    assert headers.get_content_type() == PROBLEM_TYPE, (
        f"Expected {PROBLEM_TYPE}, got {headers.get('Content-Type')}"
    )
    data = json.loads(body)
    assert data["status"] == expected_status, (
        f"Expected problem status {expected_status}, got {data.get('status')}"
    )
    assert data.get("title"), "The problem must have a title"
    assert data.get("instance"), "The problem must have an instance"
    assert "properties" not in data, (
        "The problem must not contain a nested properties wrapper"
    )
    if expected_type is not None:
        assert data["type"] == expected_type, (
            f"Expected problem type {expected_type}, got {data.get('type')}"
        )
    if expected_instance is not None:
        assert data["instance"] == expected_instance, (
            f"Expected problem instance {expected_instance}, got {data.get('instance')}"
        )
    return data


def _offered_session(alice_client) -> str:
    """Submit an offer-set request and return an offered session UUID."""
    response = alice_client.submit_execution(
        _make_request("problem-details"),
        follow_redirect=True,
    )
    assert response.result == "YES", (
        f"Expected an offer, got result {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )
    assert response.offers, "Expected at least one offered session"
    return str(response.offers[0].meta.uuid)


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestProblemDetails:
    """Tests for the RFC 9457 error responses."""

    def test_unknown_session_returns_problem(self):
        """An unknown session returns a 404 problem document."""
        path = f"/sessions/{uuid.uuid4()}"
        status, headers, body = _request("GET", path)
        _assert_problem(
            status,
            headers,
            body,
            404,
            "urn:ivoa:calycopis:error:session-not-found",
            path,
        )

    def test_unknown_offerset_returns_problem(self):
        """An unknown offer set returns a 404 problem document."""
        path = f"/offersets/{uuid.uuid4()}"
        status, headers, body = _request("GET", path)
        _assert_problem(
            status,
            headers,
            body,
            404,
            "urn:ivoa:calycopis:error:offerset-not-found",
            path,
        )

    def test_unknown_path_returns_problem(self):
        """An unknown URL returns a 404 problem document."""
        path = "/does-not-exist"
        status, headers, body = _request("GET", path)
        _assert_problem(
            status,
            headers,
            body,
            404,
            expected_instance=path,
        )

    def test_error_honours_strict_problem_json(self):
        """An XML Accept header still gets a problem+json body."""
        path = f"/sessions/{uuid.uuid4()}"
        status, headers, body = _request(
            "GET",
            path,
            accept="application/xml",
        )
        _assert_problem(
            status,
            headers,
            body,
            404,
            "urn:ivoa:calycopis:error:session-not-found",
            path,
        )

    def test_unauthenticated_post_returns_problem(self):
        """An unauthenticated POST returns a 401 problem document."""
        status, headers, body = _request(
            "POST",
            "/direct",
            data={},
        )
        _assert_problem(
            status,
            headers,
            body,
            401,
            "urn:ivoa:calycopis:error:unauthorized",
            "/direct",
        )
        assert "Basic" in headers.get("WWW-Authenticate", ""), (
            "The 401 response must preserve the Basic challenge"
        )

    def test_non_admin_identity_request_returns_problem(self, alice_creds):
        """An authenticated non-admin gets a 403 problem document."""
        status, headers, body = _request(
            "POST",
            "/admin/identities",
            username=alice_creds["username"],
            password=alice_creds["password"],
            data={"username": "not-an-admin", "password": "not-a-password"},
        )
        _assert_problem(
            status,
            headers,
            body,
            403,
            "urn:ivoa:calycopis:error:access-denied",
            "/admin/identities",
        )

    def test_foreign_session_update_returns_problem(self, alice_client, bob_creds):
        """Updating another user's session returns a 403 problem document."""
        session_uuid = _offered_session(alice_client)
        path = f"/sessions/{session_uuid}"
        status, headers, body = _request(
            "POST",
            path,
            username=bob_creds["username"],
            password=bob_creds["password"],
            data={
                "kind": "uri:enum-value-update",
                "path": "/phase",
                "value": "CANCELLED",
            },
        )
        _assert_problem(
            status,
            headers,
            body,
            403,
            "urn:ivoa:calycopis:error:access-denied",
            path,
        )

    def test_malformed_body_returns_problem(self, alice_client, alice_creds):
        """A malformed request body returns a 400 problem document."""
        session_uuid = _offered_session(alice_client)
        path = f"/sessions/{session_uuid}"
        status, headers, body = _request(
            "POST",
            path,
            username=alice_creds["username"],
            password=alice_creds["password"],
            data="{not json",
        )
        _assert_problem(
            status,
            headers,
            body,
            400,
            expected_instance=path,
        )

    def test_invalid_update_path_leaves_session_unchanged(self, alice_client, alice_creds):
        """An unknown update path returns a 400 problem and changes nothing."""
        session_uuid = _offered_session(alice_client)
        path = f"/sessions/{session_uuid}"
        status, headers, body = _request(
            "POST",
            path,
            username=alice_creds["username"],
            password=alice_creds["password"],
            data={
                "kind": "uri:enum-value-update",
                "path": "/bogus",
                "value": "CANCELLED",
            },
        )
        data = _assert_problem(
            status,
            headers,
            body,
            400,
            "urn:ivoa:calycopis:error:invalid-update",
            path,
        )
        assert data.get("messages"), "The invalid update must include messages"
        assert data["messages"][0]["kind"] == "urn:ivoa:calycopis:message:invalid-update"
        session = alice_client.get_session(uuid.UUID(session_uuid))
        assert session.phase == SimpleExecutionSessionPhase.OFFERED, (
            f"The session phase must be unchanged, got {session.phase}"
        )

    def test_invalid_phase_transition_leaves_session_unchanged(self, alice_client, alice_creds):
        """An invalid phase transition returns a 400 problem and changes nothing."""
        session_uuid = _offered_session(alice_client)
        path = f"/sessions/{session_uuid}"
        status, headers, body = _request(
            "POST",
            path,
            username=alice_creds["username"],
            password=alice_creds["password"],
            data={
                "kind": "uri:enum-value-update",
                "path": "/phase",
                "value": "AVAILABLE",
            },
        )
        _assert_problem(
            status,
            headers,
            body,
            400,
            "urn:ivoa:calycopis:error:invalid-update",
            path,
        )
        session = alice_client.get_session(uuid.UUID(session_uuid))
        assert session.phase == SimpleExecutionSessionPhase.OFFERED, (
            f"The session phase must be unchanged, got {session.phase}"
        )

    def test_non_enum_update_returns_problem(self, alice_client, alice_creds):
        """A non-enum update kind returns a 400 problem document."""
        session_uuid = _offered_session(alice_client)
        path = f"/sessions/{session_uuid}"
        status, headers, body = _request(
            "POST",
            path,
            username=alice_creds["username"],
            password=alice_creds["password"],
            data={
                "kind": "uri:string-value-update",
                "path": "/phase",
                "value": "CANCELLED",
            },
        )
        _assert_problem(
            status,
            headers,
            body,
            400,
            "urn:ivoa:calycopis:error:invalid-update",
            path,
        )
        session = alice_client.get_session(uuid.UUID(session_uuid))
        assert session.phase == SimpleExecutionSessionPhase.OFFERED, (
            f"The session phase must be unchanged, got {session.phase}"
        )
