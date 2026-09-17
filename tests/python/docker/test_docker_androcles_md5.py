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
#     "timestamp": "2026-04-13T12:00:00",
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
#     "timestamp": "2026-06-10T11:45:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 40,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-08-27T11:50:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 25,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Integration test for the Heliophorus-androcles checksum container.

Submits an execution request that runs the androcles container
against a local file:// data resource mounted at /input, then
reads the captured container stdout from the session connector
in the broker REST API and verifies that the reported MD5 matches
the locally computed value.

Requires:
  - A running Calycopis broker service with the 'docker' profile active.
  - The calycopis_schema_client Python package installed.
  - The docker Python package installed (docker-py).
  - A local test file at BIND_MOUNT_TEST_FILE (default:
    /home/Zarquan/temp/random.txt) accessible to the broker.

Usage:
  pytest tests/python/test_docker_androcles_md5.py -v
  BIND_MOUNT_TEST_FILE=/path/to/file.txt pytest tests/python/test_docker_androcles_md5.py -v
"""

import json
import logging
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

# Configure logging for debug output
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)
# Set the logger to capture all DEBUG messages
logger.setLevel(logging.DEBUG)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

BIND_MOUNT_TEST_FILE = os.environ.get(
    "TEST_DATA_FILE"
)

PHASE_TIMEOUT = float(os.environ.get("PHASE_TIMEOUT", "120"))

DOCKER_SOCKET = os.environ.get(
    "DOCKER_SOCKET",
    "unix:///run/podman/podman.sock",
)

ANDROCLES_IMAGE = "ghcr.io/zarquan/heliophorus-androcles:sha-9a2513b"
ANDROCLES_DIGEST = (
    "sha256:0dfeaad1f37ab8cd506f3a16d1ade56d035694a34e0ff28be51b97f1924c4df3"
)

HTTP_SHA256_URL = (
    "http://www.beespace.me/sites/www.beespace.me/files/styles/large/"
    "public/field/image/20250824_111206.jpg"
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

def _compute_expected_md5(docker_client: docker.DockerClient, filepath: str) -> str:
    """Compute the MD5 of a file as seen through a Podman/Docker bind mount.

    Rootless Podman may see different file content from the host when
    running under a different user namespace, so we compute the
    reference hash through the same bind mount mechanism that the
    broker will use.
    """
    print(f"\n>>> _compute_expected_md5 START: filepath={repr(filepath)}\n")
    logger.warning(f"_compute_expected_md5 START: filepath={repr(filepath)}")

    if not filepath:
        logger.error("File path is None or empty!")
        raise ValueError("File path cannot be None or empty")

    logger.warning(f"File path provided: {repr(filepath)}")
    logger.warning(f"File path type: {type(filepath)}")

    try:
        # Check if file exists on host
        if os.path.exists(filepath):
            logger.warning(f"File EXISTS on host: {filepath}")
            file_size = os.path.getsize(filepath)
            logger.warning(f"File size: {file_size} bytes")
        else:
            logger.warning(f"File DOES NOT EXIST on host: {filepath}")
    except Exception as e:
        logger.warning(f"Could not check file existence: {e}")

    logger.warning("About to run container")

    try:
        # First, let's try to run a container that lists the mounted directory
        print(f"\n>>> Running diagnostic container to check mount\n")
        logger.warning("Running diagnostic: ls -la /input")
        diagnostic = docker_client.containers.run(
            "alpine:3",
            command=["ls", "-la", "/input"],
            volumes={filepath: {"bind": "/input", "mode": "ro"}},
            remove=True,
            stdout=True,
            stderr=True,
        )
        print(f">>> Diagnostic output: {repr(diagnostic)}\n")
        logger.warning(f"Diagnostic output: {repr(diagnostic[:500])}")

        # Try to read first few bytes with head
        print(f"\n>>> Running head diagnostic\n")
        logger.warning("Running diagnostic: head -c 100 /input")
        head_output = docker_client.containers.run(
            "alpine:3",
            command=["head", "-c", "100", "/input"],
            volumes={filepath: {"bind": "/input", "mode": "ro"}},
            remove=True,
            stdout=True,
            stderr=True,
        )
        print(f">>> Head output length: {len(head_output)}\n")
        logger.warning(f"Head output length: {len(head_output)}")

        # Now run the actual md5sum with stderr captured
        print(f"\n>>> Running actual md5sum container\n")
        logger.warning("Running command: md5sum /input")
        container = docker_client.containers.run(
            "alpine:3",
            command=["md5sum", "/input"],
            volumes={filepath: {"bind": "/input", "mode": "ro"}},
            remove=True,
            stdout=True,
            stderr=True,
        )
        print(f">>> Container output: {repr(container)}\n")
        logger.warning(f"Container output (raw): {repr(container[:500])}")
        logger.warning(f"Container output length: {len(container)}")

        # If stdout is empty, try running with stderr redirected to stdout
        if not container or len(container) == 0:
            logger.warning("stdout is empty, trying with sh -c to redirect stderr")
            # Try one more time, checking for any error messages
            container_stderr = docker_client.containers.run(
                "alpine:3",
                command=["sh", "-c", "md5sum /input 2>&1"],
                volumes={filepath: {"bind": "/input", "mode": "ro"}},
                remove=True,
                stdout=True,
                stderr=False,
            )
            print(f">>> Container output (with stderr redirected): {repr(container_stderr)}\n")
            logger.warning(f"Container output with stderr: {repr(container_stderr[:500])}")
            if container_stderr:
                container = container_stderr
            else:
                logger.error("Still getting empty output even with stderr redirection!")

    except Exception as e:
        logger.error(f"Exception running container: {type(e).__name__}: {e}")
        logger.exception("Full exception traceback:")
        raise

    try:
        logger.warning(f"Decoding output")
        output = container.decode("utf-8", errors="replace").strip()
        logger.warning(f"Decoded output: {repr(output)}")
        logger.warning(f"Output length: {len(output)} characters")
    except Exception as e:
        logger.error(f"Exception decoding container output: {type(e).__name__}: {e}")
        logger.warning(f"Raw container output type: {type(container)}")
        logger.warning(f"Raw container output: {repr(container)[:200]}")
        raise

    try:
        logger.warning(f"Splitting output by whitespace")
        parts = output.split()
        logger.warning(f"Split result: {parts}")
        logger.warning(f"Number of parts: {len(parts)}")

        if len(parts) == 0:
            logger.error("Output split resulted in no parts!")
            raise ValueError(f"Invalid md5sum output: {repr(output)}")

        md5_result = parts[0]
        logger.warning(f"Extracted MD5 hash: {md5_result}")
        logger.warning(f"MD5 hash length: {len(md5_result)}")
    except Exception as e:
        logger.error(f"Exception extracting MD5 from output: {type(e).__name__}: {e}")
        logger.error(f"Original output: {repr(output)}")
        raise

    print(f"\n>>> _compute_expected_md5 END: result={md5_result}\n")
    logger.warning(f"_compute_expected_md5 END: computed MD5={md5_result}")
    return md5_result


def _compute_expected_sha256(docker_client: docker.DockerClient, url: str) -> str:
    """Download a URL inside a container and compute the SHA-256.

    Uses the same Docker/Podman API path as the broker, so the result
    reflects the file content as seen through the container runtime.
    """
    output = docker_client.containers.run(
        "alpine:3",
        command=["sh", "-c", f"wget -q -O /tmp/data '{url}' && sha256sum /tmp/data"],
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


def _make_androcles_request(
    name: str = "androcles-md5",
    file_url: str = None,
) -> ExecutionRequest:
    """Build an ExecutionRequest that runs the androcles container
    with a file:// data resource bind-mounted at /input.

    The default CMD ["md5sum", "json"] tells androcles to compute
    the MD5 of /input and emit the result as JSON on stdout.
    """
    if file_url is None:
        file_url = f"file://{BIND_MOUNT_TEST_FILE}"

    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[ANDROCLES_IMAGE],
                digest=ANDROCLES_DIGEST,
            ),
            command=["md5sum", "json"],
        ),
        data=[
            SimpleDataResource(
                meta=ComponentMetadata(name=f"{name}-data"),
                location=file_url,
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


def _make_androcles_sha256_request(
    name: str = "androcles-sha256",
    data_url: str = None,
) -> ExecutionRequest:
    """Build an ExecutionRequest that runs the androcles container
    with an http:// data resource downloaded into a Docker volume
    and mounted at /input.

    The broker creates a Docker volume, downloads the URL into it
    via a helper container, then mounts the volume on the androcles
    container. The command ["sha256sum", "json"] tells androcles to
    compute the SHA-256 of /input/content and emit JSON on stdout.
    """
    if data_url is None:
        data_url = HTTP_SHA256_URL

    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[ANDROCLES_IMAGE],
                digest=ANDROCLES_DIGEST,
            ),
            command=["sha256sum", "json"],
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

class TestAndroclesMd5:
    """
    Run the Heliophorus-androcles container to compute the MD5 of a
    local file, then verify the result by reading the captured stdout
    from the session connector.
    """

    def test_session_completes(self, client):
        """The androcles session should reach COMPLETED."""
        request = _make_androcles_request("androcles-lifecycle")
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

    def test_md5_matches_local(self, client, docker_client):
        """
        Compute the expected MD5 locally, run androcles via the broker,
        read the captured stdout from the session connector, and verify
        the hash matches.
        """
        expected_md5 = _compute_expected_md5(docker_client, BIND_MOUNT_TEST_FILE)

        request = _make_androcles_request("androcles-md5")
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

        # Parse the jc --hashsum JSON output.
        # Expected format: [{"hash": "<md5hex>", "filename": "/input"}]
        parsed = json.loads(container_stdout.strip())
        assert isinstance(parsed, list), (
            f"Expected JSON array, got {type(parsed).__name__}: "
            f"{container_stdout[:200]}"
        )
        assert len(parsed) > 0, (
            f"Expected at least one hash entry, got empty array"
        )

        actual_md5 = parsed[0].get("hash")
        assert actual_md5 is not None, (
            f"No 'hash' field in output: {parsed[0]}"
        )
        assert actual_md5 == expected_md5, (
            f"MD5 mismatch: container reported {actual_md5}, "
            f"local file has {expected_md5}"
        )

        actual_filename = parsed[0].get("filename")
        assert actual_filename == "/input", (
            f"Expected filename '/input', got '{actual_filename}'"
        )


class TestAndroclesSha256Http:
    """
    Run the Heliophorus-androcles container to compute the SHA-256 of a
    remote HTTP resource downloaded into a Docker volume.

    This exercises the storage-prepare → data-download → compute-start
    pipeline. A known race condition (volume-mount-race-condition) causes
    compute to start before the storage volume is ready, resulting in the
    container launching with zero bind mounts and failing immediately.
    """

    def test_session_completes(self, client):
        """The androcles session with HTTP data should reach COMPLETED.

        This test will FAIL while the volume-mount race condition exists:
        the broker starts the compute container before the storage volume
        is ready, so /input does not exist and the container exits with
        an error.
        """
        request = _make_androcles_sha256_request("sha256-lifecycle")
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

    def test_sha256_matches(self, client, docker_client):
        """
        Compute the expected SHA-256 by downloading the URL in a
        reference container, run androcles via the broker with an
        http:// data resource, read the captured stdout from the
        session connector, and verify the hash matches.
        """
        expected_sha256 = _compute_expected_sha256(
            docker_client, HTTP_SHA256_URL
        )

        request = _make_androcles_sha256_request("sha256-hash")
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

        actual_sha256 = parsed[0].get("hash")
        assert actual_sha256 is not None, (
            f"No 'hash' field in output: {parsed[0]}"
        )
        assert actual_sha256 == expected_sha256, (
            f"SHA-256 mismatch: container reported {actual_sha256}, "
            f"expected {expected_sha256}"
        )

        actual_filename = parsed[0].get("filename")
        assert actual_filename == "/input/content", (
            f"Expected filename '/input/content', got '{actual_filename}'"
        )
