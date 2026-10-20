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
Tests for the default direct execution 400 response.

The RFC 9457 direct-execution mode is opt-in. With the flag disabled,
a rejected direct execution keeps the existing AbstractExecutionSession
body. This test uses a mock-only validation rule so the request is
rejected by the mock platform.

Requires the mock platform.
"""

import base64
import json
import urllib.error
import urllib.request

import pytest

from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.docker_internal_port import DockerInternalPort
from calycopis_openapi_client.models.docker_network_port import DockerNetworkPort
from calycopis_openapi_client.models.docker_network_spec import DockerNetworkSpec
from calycopis_openapi_client.models.execution_request import ExecutionRequest
from calycopis_openapi_client.wrappers import DockerContainer


BROKER_URL = None


@pytest.fixture(autouse=True)
def _use_broker_url(broker_url):
    """Make the broker URL available to the raw HTTP helpers."""
    global BROKER_URL
    BROKER_URL = broker_url


def _make_rejected_request() -> ExecutionRequest:
    """A request rejected by the mock validator's excluded port path."""
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name="direct-problem-details"),
            image=DockerImageSpec(
                locations=["ghcr.io/ivoa/oligia-webtop:ubuntu-2022.01.13"],
                digest="sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            ),
            network=DockerNetworkSpec(
                ports=[
                    DockerNetworkPort(
                        access=True,
                        protocol="HTTP",
                        path="/badpath",
                        internal=DockerInternalPort(port=8080),
                    ),
                ]
            ),
        ),
    )


def _post_direct(username: str, password: str, request: ExecutionRequest):
    """POST the request to /direct and return status, headers and body."""
    token = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    body = json.dumps(
        request.to_dict()
    ).encode("utf-8")
    http_request = urllib.request.Request(
        f"{BROKER_URL}/direct",
        data=body,
        method="POST",
    )
    http_request.add_header("Content-Type", "application/json")
    http_request.add_header("Accept", "application/json")
    http_request.add_header("Authorization", f"Basic {token}")
    try:
        with urllib.request.urlopen(http_request, timeout=10) as response:
            return response.status, response.headers, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read().decode("utf-8")


def test_rejected_direct_execution_keeps_session_body(alice_creds):
    """The default rejected direct execution returns the failed session."""
    status, headers, body = _post_direct(
        alice_creds["username"],
        alice_creds["password"],
        _make_rejected_request(),
    )
    assert status == 400, f"Expected HTTP 400, got {status}"
    assert headers.get_content_type() == "application/json", (
        f"Expected the failed session body, got {headers.get('Content-Type')}"
    )
    data = json.loads(body)
    assert "meta" in data, "The failed session must include its metadata"
    assert data["meta"].get("messages"), (
        "The failed session must explain why the request was rejected"
    )
    assert "type" not in data, (
        "The default direct response must not be a problem details document"
    )
