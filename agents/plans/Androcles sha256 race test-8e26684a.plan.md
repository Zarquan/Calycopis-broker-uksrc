<!-- 8e26684a-24f6-460c-b30d-71badaa491cc -->
---
todos:
  - id: "add-sha256-test"
    content: "Add HTTP_SHA256_URL constant, _compute_expected_sha256 helper, _make_androcles_sha256_request helper, and TestAndroclesSha256Http class with two test methods to test_docker_androcles_md5.py"
    status: pending
  - id: "update-file-headers"
    content: "Update copyright year and AIMetrics block in the modified file per coding conventions"
    status: pending
  - id: "run-test"
    content: "Run the new test against the broker with docker profile to confirm it reproduces the race condition (expects FAILED)"
    status: pending
isProject: false
---
# Add SHA-256 HTTP Volume Mount Test to Reproduce Race Condition

## Context

The bug report describes a race condition where `DockerSimpleComputeResourceEntity.getPrepareAction()` resolves bind mounts before the storage volume is ready (volume ident is `null`), causing the compute container to start with zero mounts.

This race is triggered by **HTTP data resources** (which require a Docker volume + helper container download), not by `file://` resources (which use direct bind mounts). The existing `test_docker_androcles_md5.py` only tests `file://` bind mounts, so it never hits this path.

## What to add

Add a new test method (and supporting helper) to [tests/python/docker/test_docker_androcles_md5.py](calycopis/Calycopis-broker/github-zrq/tests/python/docker/test_docker_androcles_md5.py) that:

1. Builds an `ExecutionRequest` with an `http://` data resource pointing to the beespace.me image URL
2. Mounts the data at `/input` via `SimpleVolumeMount`
3. Runs the androcles container with `command=["sha256sum", "json"]` and `environment={"INPUT": "/input/content"}` (the broker's wget helper stores downloaded content as `content` inside the volume)
4. Accepts the offer and waits for terminal phase
5. Asserts `COMPLETED` (which will **fail** while the bug exists, demonstrating the race condition)
6. Optionally captures container stdout and verifies the SHA-256 hash against a reference computed via a helper container

## Key differences from existing tests in the file

- Uses `http://` URL instead of `file://` — this forces the broker to create a Docker volume and download data via a helper container, exercising the storage-prepare path
- Uses `sha256sum` instead of `md5sum` as the hash algorithm
- Sets `environment={"INPUT": "/input/content"}` because the broker's wget helper downloads to `/input/content` inside the volume (matching the pattern in `test_docker_volume_mount.py`)

## Implementation details

### New constant

```python
HTTP_SHA256_URL = "http://www.beespace.me/sites/www.beespace.me/files/styles/large/public/field/image/20250824_111206.jpg"
```

### New helper: `_compute_expected_sha256`

Follows the same pattern as `_compute_expected_md5` in `test_docker_volume_mount.py` — downloads the URL inside an alpine container and runs `sha256sum`:

```python
def _compute_expected_sha256(docker_client: docker.DockerClient, url: str) -> str:
    output = docker_client.containers.run(
        "alpine:3",
        command=["sh", "-c", f"wget -q -O /tmp/data '{url}' && sha256sum /tmp/data"],
        remove=True,
        stdout=True,
        stderr=False,
    )
    return output.decode("utf-8", errors="replace").strip().split()[0]
```

### New helper: `_make_androcles_sha256_request`

Builds the request with http:// data resource and sha256sum command:

```python
def _make_androcles_sha256_request(
    name: str = "androcles-sha256",
    data_url: str = None,
) -> ExecutionRequest:
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
```

### New test class: `TestAndroclesSha256Http`

Two test methods, matching the pattern of the existing `TestAndroclesMd5` class:

- **`test_session_completes`** — submits the request, accepts, waits for COMPLETED/FAILED, asserts COMPLETED. This is the primary test that will **fail** while the race condition exists.
- **`test_sha256_matches`** — computes expected sha256 via helper container, submits request, captures androcles stdout via docker-py, verifies the hash matches. This provides the full end-to-end verification for when the bug is fixed.

## Running the test

```bash
# Ensure broker is running with docker profile
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run

# Run just the new test class
pytest tests/python/docker/test_docker_androcles_md5.py::TestAndroclesSha256Http -v

# Expected result while bug exists: FAILED (session reaches FAILED instead of COMPLETED)
```

## Expected failure mode

With the race condition present, the test should fail with:
```
AssertionError: Session should reach COMPLETED, got SimpleExecutionSessionPhase.FAILED
```

This matches the bug report: data staging succeeds but compute starts without the volume mount.
