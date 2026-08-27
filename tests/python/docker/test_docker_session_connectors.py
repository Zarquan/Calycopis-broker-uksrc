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
#     "timestamp": "2026-08-27T09:00:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-08-27T12:49:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 10,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Integration tests for the Docker container stdout/stderr session connectors.

A session that executes a DockerSimpleComputeResourceEntity advertises two
SimpleSessionConnector links on the session:

  - https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stdout-get.yaml
  - https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stderr-get.yaml

The connectors start in the PREPARING state when the session is OFFERED,
become AVAILABLE (with HTTP GET locations) once the container logs have been
captured, and become FINISHED when the compute resource execution completes.

The test container is Heliophorus-cantliei, a simple Alpine container that
waits for a configurable number of seconds and then exits with a configurable
exit code.  It writes to stdout:

    Waiting for <N> seconds ...
    Exiting with code <code>

Requires:
  - A running Calycopis broker service with the 'docker' profile active.
  - The CONTAINER_HOST environment variable set in the broker environment.
  - The calycopis_openapi_client Python package installed.
  - Network access to ghcr.io to pull the test container image.

Usage:
  pytest tests/python/docker/test_docker_session_connectors.py -v
  CALYCOPIS_URL=http://host:port pytest tests/python/docker/test_docker_session_connectors.py -v
"""

import os
import time
import urllib.error
import urllib.request
import uuid

from calycopis_openapi_client.models import (
    ExecutionRequest,
    OfferSetResponse,
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.wrappers import DockerContainer


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CALYCOPIS_URL = os.environ.get("CALYCOPIS_URL", "http://localhost:8082").rstrip("/")

# Heliophorus-cantliei test container: waits N seconds then exits
# with a configurable exit code.
CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

# A small alpine image used to produce known output on both stdout
# and stderr.
ALPINE_IMAGE = "docker.io/library/alpine:3"
ALPINE_DIGEST = "sha256:79ff19e9084a00eece421b2523fb93e22d730e2c0e525905de047e848e56d95f"

# The connector kinds advertised by the Docker compute resource.
STDOUT_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stdout-get.yaml"
STDERR_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stderr-get.yaml"


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _make_cantliei_executable(name: str = "cantliei-connector", pause_seconds: int = 10, exit_code: int = 0) -> DockerContainer:
    """Create a Heliophorus-cantliei DockerContainer executable.

    Args:
        name: Human-readable name for the executable.
        pause_seconds: Number of seconds the container should pause before exiting.
        exit_code: Exit code the container should return (default: 0).
    """
    return DockerContainer(
        meta=ComponentMetadata(name=name),
        image=DockerImageSpec(
            locations=[CANTLIEI_IMAGE],
            digest=CANTLIEI_DIGEST,
        ),
        command=[str(pause_seconds), str(exit_code)],
    )


def _make_alpine_echo_executable(name: str = "alpine-echo") -> DockerContainer:
    """Create an alpine container that writes known output to both
    stdout and stderr, then exits with code 0.

    The container writes 'hello-out' to stdout and 'hello-err' to stderr.
    """
    return DockerContainer(
        meta=ComponentMetadata(name=name),
        image=DockerImageSpec(
            locations=[ALPINE_IMAGE],
            digest=ALPINE_DIGEST,
        ),
        command=["sh", "-c", "echo hello-out && echo hello-err >&2"],
    )


def _submit(client, request: ExecutionRequest) -> OfferSetResponse:
    """Submit a request and return the OfferSetResponse."""
    response = client.submit_execution(request, follow_redirect=True)
    assert isinstance(response, OfferSetResponse), (
        f"Expected OfferSetResponse, got {type(response)}"
    )
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )
    assert response.offers is not None and len(response.offers) > 0, (
        "Expected at least one offer"
    )
    return response


def _get_endpoint(path: str):
    """Raw GET of a connector endpoint. Returns (status, body)."""
    url = f"{CALYCOPIS_URL}{path}"
    req = urllib.request.Request(url, method="GET")
    req.add_header("Accept", "text/plain")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def _connectors_by_kind(session):
    """Return the session connectors keyed by their kind URI."""
    connectors = getattr(session, "connectors", None)
    assert connectors is not None, "Session should have connectors"
    by_kind = {connector.kind: connector for connector in connectors}
    assert set(by_kind.keys()) == {STDOUT_KIND, STDERR_KIND}, (
        f"Expected connectors for kinds {STDOUT_KIND} and {STDERR_KIND}, got {set(by_kind.keys())}"
    )
    return by_kind


# ===========================================================================
# OFFERED session connectors
# ===========================================================================

class TestOfferedSessionConnectors:
    """
    A session that is OFFERED should advertise the two Docker log
    connectors in the PREPARING state with no location.
    """

    def test_offered_session_has_two_preparing_connectors(self, client):
        """
        The offer response should include two connectors with the
        stdout/stderr kinds, status PREPARING, protocol HTTP and
        location null.
        """
        request = ExecutionRequest(
            executable=_make_cantliei_executable("connector-offered", pause_seconds=5),
        )
        response = _submit(client, request)

        offer = response.offers[0]
        assert offer.phase == SimpleExecutionSessionPhase.OFFERED, (
            f"Expected OFFERED, got {offer.phase}"
        )

        by_kind = _connectors_by_kind(offer)
        for kind in (STDOUT_KIND, STDERR_KIND):
            connector = by_kind[kind]
            assert connector.status == "PREPARING", (
                f"Expected PREPARING for {kind}, got {connector.status}"
            )
            assert connector.protocol == "HTTP", (
                f"Expected HTTP for {kind}, got {connector.protocol}"
            )
            assert connector.location is None, (
                f"Expected null location for {kind}, got {connector.location}"
            )


# ===========================================================================
# Connector lifecycle
# ===========================================================================

class TestSessionConnectorLifecycle:
    """
    The connectors should become AVAILABLE when the container logs are
    captured and FINISHED when the execution completes.
    """

    def _run_session(self, client, name, pause_seconds=3):
        """Submit, accept and return the session uuid."""
        request = ExecutionRequest(
            executable=_make_cantliei_executable(name, pause_seconds=pause_seconds),
        )
        response = _submit(client, request)
        session_uuid = response.offers[0].meta.uuid
        client.set_session_phase(
            session_uuid,
            SimpleExecutionSessionPhase.ACCEPTED,
        )
        return session_uuid

    def test_connectors_available_when_logs_captured(self, client):
        """
        Once the container logs are captured the connectors should be
        AVAILABLE with locations pointing at the HTTP GET endpoints.
        The AVAILABLE state is transient, so we poll for it.
        """
        session_uuid = self._run_session(client, "connector-available", pause_seconds=2)

        deadline = time.time() + 120
        observed = None
        while time.time() < deadline:
            session = client.get_session(session_uuid)
            connectors = getattr(session, "connectors", None)
            if connectors:
                statuses = {connector.kind: connector.status for connector in connectors}
                if statuses.get(STDOUT_KIND) == "AVAILABLE":
                    observed = connectors
                    break
            time.sleep(0.5)

        assert observed is not None, (
            "Connectors should become AVAILABLE once the container logs are captured"
        )
        by_kind = {connector.kind: connector for connector in observed}
        for kind, suffix in ((STDOUT_KIND, "stdout-get"), (STDERR_KIND, "stderr-get")):
            connector = by_kind[kind]
            assert connector.status == "AVAILABLE", (
                f"Expected AVAILABLE for {kind}, got {connector.status}"
            )
            expected = f"{CALYCOPIS_URL}/sessions/{session_uuid}/docker/{suffix}"
            assert connector.location == expected, (
                f"Expected location {expected} for {kind}, got {connector.location}"
            )

        # Clean up: wait for the session to reach a terminal phase.
        client.wait_for_phase(
            session_uuid,
            target_phases=[
                SimpleExecutionSessionPhase.COMPLETED,
                SimpleExecutionSessionPhase.FAILED,
                SimpleExecutionSessionPhase.CANCELLED,
            ],
            timeout=300.0,
            interval=2.0,
        )

    def test_connectors_finished_after_completion(self, client):
        """
        When the compute resource execution has finished the connectors
        should be FINISHED with their locations unchanged.
        """
        session_uuid = self._run_session(client, "connector-finished", pause_seconds=3)

        session = client.wait_for_phase(
            session_uuid,
            target_phases=[SimpleExecutionSessionPhase.COMPLETED],
            timeout=300.0,
            interval=2.0,
        )
        assert session.phase == SimpleExecutionSessionPhase.COMPLETED, (
            f"Expected COMPLETED, got {session.phase}"
        )

        by_kind = _connectors_by_kind(session)
        for kind, suffix in ((STDOUT_KIND, "stdout-get"), (STDERR_KIND, "stderr-get")):
            connector = by_kind[kind]
            assert connector.status == "FINISHED", (
                f"Expected FINISHED for {kind}, got {connector.status}"
            )
            expected = f"{CALYCOPIS_URL}/sessions/{session_uuid}/docker/{suffix}"
            assert connector.location == expected, (
                f"Expected location {expected} for {kind}, got {connector.location}"
            )


# ===========================================================================
# Connector endpoints
# ===========================================================================

class TestSessionConnectorEndpoints:
    """
    The connector locations should provide HTTP GET access to the
    captured container stdout and stderr.
    """

    def _run_to_completion(self, client, name, pause_seconds=3):
        """Submit, accept and wait for COMPLETED. Returns the session uuid."""
        request = ExecutionRequest(
            executable=_make_cantliei_executable(name, pause_seconds=pause_seconds),
        )
        response = _submit(client, request)
        session_uuid = response.offers[0].meta.uuid
        client.set_session_phase(
            session_uuid,
            SimpleExecutionSessionPhase.ACCEPTED,
        )
        session = client.wait_for_phase(
            session_uuid,
            target_phases=[SimpleExecutionSessionPhase.COMPLETED],
            timeout=300.0,
            interval=2.0,
        )
        assert session.phase == SimpleExecutionSessionPhase.COMPLETED, (
            f"Expected COMPLETED, got {session.phase}"
        )
        return session_uuid

    def test_stdout_endpoint_returns_container_stdout(self, client):
        """
        GET /sessions/{uuid}/docker/stdout-get should return the captured
        container stdout.
        """
        session_uuid = self._run_to_completion(client, "connector-stdout", pause_seconds=3)

        status, body = _get_endpoint(f"/sessions/{session_uuid}/docker/stdout-get")
        assert status == 200, (
            f"Expected 200 from stdout-get, got {status}"
        )
        assert body == "Waiting for 3 seconds ...\nExiting with code 0\n", (
            f"Unexpected stdout content: [{body}]"
        )

    def test_stderr_endpoint_returns_container_stderr(self, client):
        """
        GET /sessions/{uuid}/docker/stderr-get should return the captured
        container stderr (empty for the cantliei container).
        """
        session_uuid = self._run_to_completion(client, "connector-stderr", pause_seconds=3)

        status, body = _get_endpoint(f"/sessions/{session_uuid}/docker/stderr-get")
        assert status == 200, (
            f"Expected 200 from stderr-get, got {status}"
        )
        assert body == "", (
            f"Unexpected stderr content: [{body}]"
        )

    def test_alpine_stdout_and_stderr_endpoints(self, client):
        """
        An alpine container that writes known output to both stdout and
        stderr should expose that content through the stdout-get and
        stderr-get connector endpoints.
        """
        request = ExecutionRequest(
            executable=_make_alpine_echo_executable("connector-alpine-echo"),
        )
        response = _submit(client, request)
        session_uuid = response.offers[0].meta.uuid
        client.set_session_phase(
            session_uuid,
            SimpleExecutionSessionPhase.ACCEPTED,
        )
        session = client.wait_for_phase(
            session_uuid,
            target_phases=[
                SimpleExecutionSessionPhase.COMPLETED,
                SimpleExecutionSessionPhase.FAILED,
            ],
            timeout=300.0,
            interval=2.0,
        )
        assert session.phase == SimpleExecutionSessionPhase.COMPLETED, (
            f"Expected COMPLETED, got {session.phase}"
        )

        status, stdout = _get_endpoint(f"/sessions/{session_uuid}/docker/stdout-get")
        assert status == 200, (
            f"Expected 200 from stdout-get, got {status}"
        )
        assert stdout == "hello-out\n", (
            f"Unexpected stdout content: [{stdout}]"
        )

        status, stderr = _get_endpoint(f"/sessions/{session_uuid}/docker/stderr-get")
        assert status == 200, (
            f"Expected 200 from stderr-get, got {status}"
        )
        assert stderr == "hello-err\n", (
            f"Unexpected stderr content: [{stderr}]"
        )

    def test_unknown_session_returns_not_found(self, client):
        """
        A request for an unknown session should return 404.
        """
        random_uuid = uuid.uuid4()
        status, _ = _get_endpoint(f"/sessions/{random_uuid}/docker/stdout-get")
        assert status == 404, (
            f"Expected 404 for unknown session, got {status}"
        )
