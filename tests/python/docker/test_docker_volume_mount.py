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
#     "timestamp": "2026-04-14T17:00:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-06-02T13:37:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 5,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-08-27T11:50:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 30,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Integration test for Docker volume mounts with remote HTTP data.

Submits an execution request that uses an http:// data resource,
which the broker should download into a Docker volume via a helper
container, then mount on the application container.

Requires:
  - A running Calycopis broker service with the 'docker' profile active.
  - The calycopis_schema_client Python package installed.
  - The docker Python package installed (docker-py).
  - Network access to download the test data URL.

Usage:
  pytest tests/python/test_docker_volume_mount.py -v
"""

import json
import os
import urllib.error
import urllib.request

import docker
import pytest

from calycopis_openapi_client.models import (
    ExecutionRequest,
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
    SimpleDataResource,
    SimpleVolumeMount,
)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

PHASE_TIMEOUT = float(os.environ.get("PHASE_TIMEOUT", "300"))

DOCKER_SOCKET = os.environ.get(
    "DOCKER_SOCKET",
    "unix:///run/podman/podman.sock",
)

ANDROCLES_IMAGE = "ghcr.io/zarquan/heliophorus-androcles:sha-9a2513b"
ANDROCLES_DIGEST = (
    "sha256:0dfeaad1f37ab8cd506f3a16d1ade56d035694a34e0ff28be51b97f1924c4df3"
)

HTTP_TEST_URL = os.environ.get(
    "HTTP_TEST_URL",
    "https://github.com/ivoa-std/ExecutionBroker/releases/download/auto-pdf-preview/ExecutionBroker-draft.pdf",
)

# The connector kind that provides access to the captured container stdout.
STDOUT_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stdout-get.yaml"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def docker_client() -> docker.DockerClient:
    return docker.DockerClient(base_url=DOCKER_SOCKET)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _compute_expected_md5(docker_client: docker.DockerClient, url: str) -> str:
    """Download a URL inside a container and compute the MD5.

    Uses the same Docker/Podman API path as the broker, so the result
    reflects the file content as seen through the container runtime.
    """
    output = docker_client.containers.run(
        "alpine:3",
        command=["sh", "-c", f"wget -q -O /tmp/data '{url}' && md5sum /tmp/data"],
        remove=True,
        stdout=True,
        stderr=False,
    )
    return output.decode("utf-8", errors="replace").strip().split()[0]


def _get_session_stdout(session) -> str:
    """Read the captured container stdout from the session connector.

    Finds the stdout connector advertised on the session and performs
    an HTTP GET on its location to read the captured container stdout.
    """
    connectors = getattr(session, "connectors", None)
    assert connectors is not None and len(connectors) > 0, (
        "Session should have connectors"
    )
    stdout_connector = None
    for connector in connectors:
        if connector.kind == STDOUT_KIND:
            stdout_connector = connector
            break
    assert stdout_connector is not None, (
        f"Session should have a stdout connector, got kinds {[c.kind for c in connectors]}"
    )
    assert stdout_connector.location is not None, (
        "stdout connector should have a location"
    )
    req = urllib.request.Request(stdout_connector.location, method="GET")
    req.add_header("Accept", "text/plain")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raise AssertionError(
            f"Failed to GET stdout connector {stdout_connector.location}: HTTP {e.code}"
        ) from e


def _make_http_volume_request(
    name: str = "http-vol",
    data_url: str = None,
) -> ExecutionRequest:
    """Build an ExecutionRequest that runs the androcles container
    with an http:// data resource mounted via a Docker volume at /input.

    The androcles container computes the MD5 of /input/content
    (the filename used by the broker's wget helper).
    """
    if data_url is None:
        data_url = HTTP_TEST_URL

    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[ANDROCLES_IMAGE],
                digest=ANDROCLES_DIGEST,
            ),
            command=["md5sum", "json"],
            environment={"INPUT": "/input/content"},
        ),
        data=[
            SimpleDataResource(
                meta=ComponentMetadata(name=f"{name}-data"),
                location=data_url,
            ),
        ],
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name=f"{name}-compute"),
            volumes=[
                SimpleVolumeMount(
                    resource=f"{name}-data",
                    path="/input",
                    mode="READONLY",
                ),
            ],
        ),
    )


# ===========================================================================
# Tests
# ===========================================================================

class TestDockerVolumeMount:
    """
    Test Docker volume mounts for remote HTTP data resources.
    """

    def test_session_completes(self, client):
        """An HTTP data resource session should reach COMPLETED."""
        request = _make_http_volume_request("http-vol-lifecycle")
        response = client.submit_execution(request, follow_redirect=True)
        assert response.result == "YES", f"Expected YES, got {response.result}"
        assert len(response.offers) > 0

        offer = response.offers[0]
        offer_uuid = offer.meta.uuid

        client.set_session_phase(
            offer_uuid,
            SimpleExecutionSessionPhase.ACCEPTED,
        )

        result = client.wait_for_phase(
            offer_uuid,
            target_phases=[
                SimpleExecutionSessionPhase.COMPLETED,
                SimpleExecutionSessionPhase.FAILED,
            ],
            timeout=PHASE_TIMEOUT,
            interval=5.0,
        )
        assert result.phase == SimpleExecutionSessionPhase.COMPLETED, (
            f"Session should reach COMPLETED, got {result.phase}"
        )

    def test_md5_matches(self, client, docker_client):
        """
        Download the same URL in a reference container, run androcles
        via the broker with an http:// data resource, read the captured
        stdout from the session connector, and verify the MD5 matches.
        """
        expected_md5 = _compute_expected_md5(docker_client, HTTP_TEST_URL)

        request = _make_http_volume_request("http-vol-md5")
        response = client.submit_execution(request, follow_redirect=True)
        assert response.result == "YES", f"Expected YES, got {response.result}"
        assert len(response.offers) > 0

        offer = response.offers[0]
        offer_uuid = offer.meta.uuid

        client.set_session_phase(
            offer_uuid,
            SimpleExecutionSessionPhase.ACCEPTED,
        )

        session = client.wait_for_phase(
            offer_uuid,
            target_phases=[
                SimpleExecutionSessionPhase.COMPLETED,
                SimpleExecutionSessionPhase.FAILED,
            ],
            timeout=PHASE_TIMEOUT,
            interval=5.0,
        )
        assert session.phase == SimpleExecutionSessionPhase.COMPLETED, (
            f"Session should reach COMPLETED, got {session.phase}"
        )

        # Read the captured container stdout from the session connector.
        container_stdout = _get_session_stdout(session)
        assert container_stdout.strip(), (
            "Session stdout connector returned empty content"
        )

        parsed = json.loads(container_stdout.strip())
        assert isinstance(parsed, list), (
            f"Expected JSON array, got {type(parsed).__name__}: "
            f"{container_stdout[:200]}"
        )
        assert len(parsed) > 0, (
            "Expected at least one hash entry, got empty array"
        )

        actual_md5 = parsed[0].get("hash")
        assert actual_md5 is not None, (
            f"No 'hash' field in output: {parsed[0]}"
        )
        assert actual_md5 == expected_md5, (
            f"MD5 mismatch: container reported {actual_md5}, "
            f"expected {expected_md5}"
        )
