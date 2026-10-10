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
#     "timestamp": "2026-10-10T10:06:15",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 60,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Integration tests for user defined container labels on the docker platform.

A user can supply their own labels on the DockerContainer executable. The
broker applies them to the container it launches, alongside the internal
calycopis-broker-* labels, and echoes them back in the API response.

Label names beginning with calycopis-broker- are reserved, and a request that
uses one is rejected.

Requires:
  - A running Calycopis broker service with the 'docker' profile active.
  - The CONTAINER_HOST environment variable set for the test container.
  - The calycopis_openapi_client Python package installed.
  - Network access to ghcr.io to pull the test container image.

Usage:
  pytest tests/python/docker/test_docker_user_labels.py -v
  CALYCOPIS_URL=http://host:port pytest tests/python/docker/test_docker_user_labels.py -v
"""

import time

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
)

from calycopis_conftest import (
    CONTAINER_HOST,
    phase_timeout,
)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# Heliophorus-cantliei test container: waits N seconds then exits.
CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

# The broker owned label keys, asserted as literals.
SESSION_UID_LABEL = "calycopis-broker-session-uid"
RESOURCE_UID_LABEL = "calycopis-broker-resource-uid"
RESOURCE_KIND_LABEL = "calycopis-broker-resource-kind"
CONTAINER_ROLE_LABEL = "calycopis-broker-container-role"

# The user defined labels used by these tests.
USER_LABELS = {
    "calycopis-test-alpha": "one",
    "calycopis-test-beta": "two",
}

PHASE_TIMEOUT = phase_timeout(180)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def docker_client() -> docker.DockerClient:
    """Create a shared Docker/Podman client for container inspection."""
    return docker.DockerClient(base_url=CONTAINER_HOST)


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _make_request(
    name: str,
    pause_seconds: int = 30,
    labels=None,
    ) -> ExecutionRequest:
    """Create an ExecutionRequest for the Heliophorus-cantliei container."""
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=[str(pause_seconds), "0"],
            labels=labels,
        ),
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name=f"{name}-compute"),
        ),
    )


def _find_container_by_session(
    docker_client: docker.DockerClient,
    session_uuid: str,
    timeout: float,
    ):
    """Find the container labelled with *session_uuid*, or None."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        containers = docker_client.containers.list(
            all=True,
            filters={"label": f"{SESSION_UID_LABEL}={session_uuid}"},
        )
        if containers:
            return containers[0]
        time.sleep(1)
    return None


def _cancel_session(client, session_uuid: str):
    """Cancel a session so the processing loop releases its container."""
    client.set_session_phase(
        session_uuid,
        SimpleExecutionSessionPhase.CANCELLED,
    )


# ===========================================================================
# User label tests
# ===========================================================================

class TestUserLabels:
    """
    Tests for user defined labels on the docker platform.
    """

    def test_user_labels_reach_the_container(self, client, docker_client):
        """
        Labels supplied on the executable should be applied to the container,
        alongside the internal calycopis-broker-* labels.
        """
        request = _make_request(
            "userlabels-direct",
            pause_seconds=30,
            labels=USER_LABELS,
        )
        session = client.direct_execute(request)
        session_uuid = str(session.meta.uuid)

        try:
            container = _find_container_by_session(
                docker_client,
                session_uuid,
                PHASE_TIMEOUT,
            )
            assert container is not None, (
                f"No container found with {SESSION_UID_LABEL}={session_uuid}"
            )

            labels = container.labels
            for key, value in USER_LABELS.items():
                assert labels.get(key) == value, (
                    f"Expected user label {key}={value}, got {labels.get(key)}"
                )

            # The internal labels are still there.
            assert labels.get(SESSION_UID_LABEL) == session_uuid
            assert labels.get(CONTAINER_ROLE_LABEL) == "execution"
            assert labels.get(RESOURCE_UID_LABEL) is not None
            assert labels.get(RESOURCE_KIND_LABEL) is not None
        finally:
            _cancel_session(client, session_uuid)

    def test_user_labels_in_session_response(self, client):
        """
        The user labels should be echoed back in the session response.
        """
        request = _make_request(
            "userlabels-response",
            pause_seconds=30,
            labels=USER_LABELS,
        )
        session = client.direct_execute(request)
        session_uuid = str(session.meta.uuid)

        try:
            assert session.executable is not None, (
                "Session should include the executable"
            )
            assert session.executable.labels == USER_LABELS, (
                f"Expected labels {USER_LABELS}, "
                f"got {session.executable.labels}"
            )
        finally:
            _cancel_session(client, session_uuid)

    def test_reserved_label_is_rejected(self, client):
        """
        A user label using the reserved calycopis-broker- prefix should be
        rejected, rather than silently changed.
        """
        request = _make_request(
            "userlabels-reserved",
            pause_seconds=5,
            labels={SESSION_UID_LABEL: "not-allowed"},
        )
        response = client.submit_execution(request, follow_redirect=True)

        assert response.result == "NO", (
            f"Expected the request to be rejected, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )

        messages = []
        if response.meta is not None and response.meta.messages is not None:
            messages = [message.kind for message in response.meta.messages]
        assert "urn:reserved-label" in messages, (
            f"Expected an urn:reserved-label message, got {messages}"
        )
