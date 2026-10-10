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
#     "timestamp": "2026-10-10T06:27:56",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 30,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Integration tests for the calycopis-broker-* container labels on the docker
platform.

Every container the broker launches carries a set of broker owned labels, so
that it can be found on the Podman host and traced back to the broker session
and resource that created it:

  * calycopis-broker-session-uid    - the execution session UUID.
  * calycopis-broker-resource-uid   - the UUID of the broker resource that
                                      launched the container.
  * calycopis-broker-resource-kind  - the schema kind URI of that resource.
  * calycopis-broker-container-role - the role of the container in the broker.

These tests check the execution container, which is launched by the compute
resource, through both the direct execution and the offer-set flows.

Requires:
  - A running Calycopis broker service with the 'docker' profile active.
  - The CONTAINER_HOST environment variable set for the test container.
  - The calycopis_openapi_client Python package installed.
  - Network access to ghcr.io to pull the test container image.

Usage:
  pytest tests/python/docker/test_docker_container_labels.py -v
  CALYCOPIS_URL=http://host:port pytest tests/python/docker/test_docker_container_labels.py -v
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

# The broker label keys. These are part of the broker's internal contract,
# so the tests assert them as literals rather than importing them.
SESSION_UID_LABEL = "calycopis-broker-session-uid"
RESOURCE_UID_LABEL = "calycopis-broker-resource-uid"
RESOURCE_KIND_LABEL = "calycopis-broker-resource-kind"
CONTAINER_ROLE_LABEL = "calycopis-broker-container-role"

ROLE_EXECUTION = "execution"

# The compute resource kind, asserted as a literal. The API and the label both
# read the same Java constant, so asserting one against the other would not
# catch a change to that constant.
COMPUTE_KIND = (
    "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0"
    "/kinds/compute/simple-compute-resource.yaml"
)

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

def _make_request(name: str, pause_seconds: int = 30) -> ExecutionRequest:
    """Create an ExecutionRequest for the Heliophorus-cantliei container.

    The pause is long enough that the container is still running when the
    labels are inspected. An explicit compute resource is included so that
    the expected resource UUID is known.
    """
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=[str(pause_seconds), "0"],
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
    """Find the running container labelled with *session_uuid*.

    This uses the Podman label filter, which is the mechanism an operator
    would use to find the container. Returns None if it does not appear
    within *timeout* seconds.
    """
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


def _assert_execution_labels(
    client,
    docker_client: docker.DockerClient,
    session_uuid: str,
):
    """Find the execution container for *session_uuid* and check its labels.

    Returns the docker Container object.
    """
    container = _find_container_by_session(
        docker_client,
        session_uuid,
        PHASE_TIMEOUT,
    )
    assert container is not None, (
        f"No container found with {SESSION_UID_LABEL}={session_uuid} "
        f"within {PHASE_TIMEOUT} seconds. "
        f"Containers: {[c.name for c in docker_client.containers.list(all=True)]}"
    )

    labels = container.labels
    assert labels.get(SESSION_UID_LABEL) == session_uuid, (
        f"Expected {SESSION_UID_LABEL}={session_uuid}, got {labels.get(SESSION_UID_LABEL)}"
    )

    # The compute resource is the resource that launches the execution
    # container, and its UUID is the resource label value.
    session = client.get_session(session_uuid)
    assert session.compute is not None, (
        "Session should include the compute resource that launched the container"
    )
    compute_uuid = str(session.compute.meta.uuid)

    assert labels.get(RESOURCE_UID_LABEL) == compute_uuid, (
        f"Expected {RESOURCE_UID_LABEL}={compute_uuid}, got {labels.get(RESOURCE_UID_LABEL)}"
    )
    assert labels.get(RESOURCE_KIND_LABEL) == COMPUTE_KIND, (
        f"Expected {RESOURCE_KIND_LABEL}={COMPUTE_KIND}, got {labels.get(RESOURCE_KIND_LABEL)}"
    )
    assert session.compute.kind == COMPUTE_KIND, (
        f"Expected session.compute.kind={COMPUTE_KIND}, got {session.compute.kind}"
    )
    assert labels.get(CONTAINER_ROLE_LABEL) == ROLE_EXECUTION, (
        f"Expected {CONTAINER_ROLE_LABEL}={ROLE_EXECUTION}, got {labels.get(CONTAINER_ROLE_LABEL)}"
    )
    return container


def _cancel_session(client, session_uuid: str):
    """Cancel a session so the processing loop releases its container."""
    client.set_session_phase(
        session_uuid,
        SimpleExecutionSessionPhase.CANCELLED,
    )


# ===========================================================================
# Container label tests
# ===========================================================================

class TestContainerLabels:
    """
    Tests that the execution container carries the broker owned labels,
    through both the direct execution and the offer-set flows.
    """

    def test_direct_execution_container_labels(self, client, docker_client):
        """
        A container launched by a direct execution session should carry the
        session, resource and role labels.
        """
        request = _make_request("labels-direct", pause_seconds=30)
        session = client.direct_execute(request)
        session_uuid = str(session.meta.uuid)

        try:
            _assert_execution_labels(
                client,
                docker_client,
                session_uuid,
            )
        finally:
            _cancel_session(client, session_uuid)

    def test_offer_set_container_labels(self, client, docker_client):
        """
        A container launched from an accepted offer should carry the same
        labels, covering the path where the session already exists before
        the container is created.
        """
        request = _make_request("labels-offer", pause_seconds=30)
        response = client.submit_execution(request, follow_redirect=True)
        assert response.result == "YES", (
            f"Expected YES, got {response.result}. Messages: "
            f"{response.meta.messages if response.meta else 'none'}"
        )
        assert response.offers is not None and len(response.offers) > 0, (
            "Expected at least one offer"
        )

        offer = response.offers[0]
        session_uuid = str(offer.meta.uuid)

        client.set_session_phase(
            session_uuid,
            SimpleExecutionSessionPhase.ACCEPTED,
        )
        try:
            _assert_execution_labels(
                client,
                docker_client,
                session_uuid,
            )
        finally:
            _cancel_session(client, session_uuid)
