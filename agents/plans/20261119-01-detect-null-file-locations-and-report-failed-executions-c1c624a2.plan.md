<!-- c1c624a2-070f-43fc-ac6a-858c110fa0ee -->
---
todos:
  - id: "branch"
    content: "Create a branch from main at 15bac1f9 for issues 108 and 111"
    status: completed
  - id: "validate"
    content: "Reject null, blank and non-absolute file locations during offer-set validation"
    status: completed
  - id: "link"
    content: "Make the bind-mount linker and storage linker fail explicitly instead of building an empty source path"
    status: completed
  - id: "prepare"
    content: "Report failed Docker container preparation and monitoring through component messages"
    status: completed
  - id: "session"
    content: "Report which component failed when a session transitions to FAILED"
    status: completed
  - id: "tests"
    content: "Add the Java validator test, extend the bind-mount and direct-execution tests, and fix the test-data helper"
    status: completed
  - id: "config"
    content: "Update testing.yaml, config-tests.sh, AGENTS.md and docs/deployment.md for host-path locations"
    status: completed
  - id: "verify"
    content: "Run the Java tests and the Python syntax checks"
    status: completed
  - id: "commit"
    content: "Present the diff and commit message for approval; do not commit until approved"
    status: pending
isProject: false
---
# Plan: Detect null file locations and report failed executions

Date: 2026-11-19
Scope: `Calycopis-broker` — reject a `file://` data location that has no usable absolute path, stop linking a bind mount from such a location, and report Docker preparation, monitoring and non-zero exits through component and session messages.
Issues:
 * [uksrc/Calycopis-broker#108](https://github.com/uksrc/Calycopis-broker/issues/108) — null file locations are accepted and produce empty bind mounts.
 * [uksrc/Calycopis-broker#111](https://github.com/uksrc/Calycopis-broker/issues/111) — a failed container execution leaves no explanation on the session.
Branch: `20261119-zrq-null-file-locations`, created from `main` at `15bac1f9`.

## Target

A request whose local-file location is null, blank, `file://`, `file://None`, `file:///` or a `file://` URI with a relative path is rejected during offer-set validation with a message, and no storage resource or bind mount is built. A location that passes validation is trimmed once, and the bind-mount source is the absolute host path from that location.

When a Docker container cannot be prepared, cannot be started, or exits non-zero, the compute component records why. When a session moves to `FAILED`, the session records which component failed. The API already carries `ComponentMetadata.messages`, so no OpenAPI schema change is needed.

## Root cause

* `DockerSimpleDataFileResourceValidatorImpl.validateLocation(...)` accepted any string beginning with `file://`. `file://None` and `file://` passed, and the linker built a storage resource from them.
* `DockerDataStorageLinkerImpl` created a bind mount whose source path came from `location.substring("file://".length())`. For `file://` that is the empty string, so the storage resource was complete enough to persist but useless.
* `DockerBindMountStorageEntity` handed an empty source path to the Docker storage linker. The failure surfaced only when the container was created, and the compute entity did not record it.
* `DockerSimpleComputeReservationEntity` and `DockerDockerContainerEntity` logged failures but left `postProcess` without a message, so the session's metadata showed no reason for `FAILED`.
* The Python tests used `test_data_file()` as a host path and prepended `file://`, while the YAML `location` already carried `file://`. The generated `testing.yaml` also used the container path as the location rather than the host path.

## Decisions

1. **Validate the URI shape, not host existence.** The broker cannot see the host filesystem from its own container, so validation checks only that the location parses as a `file:` URI with a non-empty absolute path, and that the path is not `/`. Docker/Podman remains the boundary that reports a missing host file.
2. **Fail at validation, not later.** A rejected location adds `urn:invalid-value` / `File data location [${location}] must be a file URI with an absolute path`, and `SimpleDataResourceValidatorImpl` does not link storage or add a result when location validation fails.
3. **Keep the stop validator explicit.** A null or blank location adds `urn:missing-required-value` / `Data location required`; any other unhandled URI adds the existing `uri:unknown-url` warning. The values map is built only after the null check, using `String.valueOf(...)` for any nullable part.
4. **Make the linker defensive.** `DockerDataStorageLinkerImpl` trims the validated location, and its `file://` branch re-checks `isUsableFileLocation(...)`. An invalid path adds `uri:invalid-data` / `Invalid file location [${location}] for data resource [${uuid}]` and returns `FAILED`.
5. **Do not build an empty bind mount.** `DockerBindMountStorageEntity` resolves the source path with `URI.create(...).getPath()` and treats null, unparseable, blank and `/` as missing. `makePrepareAction()` adds `urn:missing-required-value` / `File bind mount source path required` and returns `NO_ACTION`; only a usable path produces an available action.
6. **Report Docker failures once, in `postProcess`.** The entity stores a `failureType`, `failureTemplate` and `failureValues` in the action and emits the message in the typed `postProcess(...)`, after the action has run and before the phase is set. A message template uses `${name}` placeholders; log format strings keep `{}`.
7. **Report one session-level message per failed component.** `UpdateSessionRequestEntity` adds `uri:session-component-failed` / `Component [${component}][${kind}] failed, so the session failed` when a non-terminal session moves to `FAILED`. The `kind` value is the component's schema kind URI from `getKind()`, which is what clients see elsewhere, rather than the Java class name. The existing early branches for already `FAILED`, `COMPLETED`, `CANCELLED` and `EXPIRED` keep their behaviour and do not duplicate the message.
8. **Fix the test data contract, not the tests' assumptions.** `test_data_host_path(name)` reads `hostpath` and raises at import time when it is missing, blank or not absolute. `config-tests.sh` writes `filepath`, `hostpath` and `location: file://<hostpath>`. The bind-mount tests build the `file://` URI once and compare container mount sources against the host path.

## Changes

### Validation and linking

* [`DockerSimpleDataFileResourceValidatorImpl.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/data/simple/docker/file/DockerSimpleDataFileResourceValidatorImpl.java)
  * add `isUsableFileLocation(String location)`;
  * require scheme `file`, non-null path, `/` prefix, and a path other than `/`;
  * add the `urn:invalid-value` warning for rejected locations.
* [`SimpleDataResourceValidatorImpl.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/data/simple/SimpleDataResourceValidatorImpl.java)
  * keep the storage result `final` and only link storage in the success branch.
* [`DockerSimpleDataStopValidatorImpl.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/data/simple/docker/stop/DockerSimpleDataStopValidatorImpl.java)
  * add `urn:missing-required-value` for null/blank, and keep `uri:unknown-url` for other values.
* [`DockerDataStorageLinkerImpl.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/data/simple/docker/link/DockerDataStorageLinkerImpl.java)
  * use the trimmed `validated.getLocation()`;
  * re-check `file://` locations and fail with `uri:invalid-data`.
* [`DockerBindMountStorageEntity.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/storage/simple/docker/bind/DockerBindMountStorageEntity.java)
  * resolve the source path through `URI.create(...).getPath()`;
  * emit `urn:missing-required-value` and return `NO_ACTION` when it is missing.
* [`DockerStorageLinkerImpl.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/storage/simple/docker/DockerStorageLinkerImpl.java)
  * require a non-blank source path in `isComplete()`.

### Failure reporting

* [`DockerDockerContainerEntity.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/executable/docker/docker/DockerDockerContainerEntity.java)
  * missing image location: `urn:missing-required-value` / `Docker image location required`;
  * missing image specification: `urn:missing-required-value` / `Docker image specification required`;
  * missing Docker client: `uri:docker-client-unavailable` / `Unable to create the Docker client`;
  * digest mismatch: `urn:image-digest-mismatch` / `Image [${image}] digest [${digest}] does not match the local image [${local}]`;
  * preparation exception: `uri:docker-image-prepare-failed` / `Failed to prepare image [${image}] for container [${uuid}]`.
* [`DockerSimpleComputeReservationEntity.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/entities/compute/simple/docker/DockerSimpleComputeReservationEntity.java)
  * unresolved bind mount: `uri:invalid-data` / `Unable to resolve the bind mount for volume mount [${volume}]`;
  * missing image: `urn:missing-required-value` / `Docker image location required`;
  * Docker client, image cache, container start, container inspect and non-zero exit messages;
  * `uri:docker-container-start-failed` / `Failed to create or start the container for compute resource [${uuid}]`;
  * `uri:docker-container-monitor-failed` / `Unable to inspect the container for compute resource [${uuid}]`;
  * `uri:docker-container-exit-code` / `Container [${container}] exited with code [${exitcode}]`.
* [`UpdateSessionRequestEntity.java`](../../java/src/main/java/net/ivoa/calycopis/broker/engine/functional/processing/session/UpdateSessionRequestEntity.java)
  * `uri:session-component-failed` / `Component [${component}][${kind}] failed, so the session failed`.

### Tests and configuration

* New [`DockerSimpleDataFileResourceValidatorImplTest.java`](../../java/src/test/java/net/ivoa/calycopis/broker/engine/entities/data/simple/docker/file/DockerSimpleDataFileResourceValidatorImplTest.java) covers absolute, encoded and invalid locations.
* [`conftest.py`](../../tests/python/conftest.py) adds `test_data_host_path(name)` and removes `test_data_file()`.
* [`config-tests.sh`](../../tests/python/bin/config-tests.sh) guards the Podman mountpoint and writes `filepath`, `hostpath` and a `file://` location.
* [`test_docker_bind_mount.py`](../../tests/python/docker/test_docker_bind_mount.py) uses the host path and adds parametrized invalid-location offer-set tests.
* [`test_docker_androcles_md5.py`](../../tests/python/docker/test_docker_androcles_md5.py) uses the host path for the bind mount and the MD5 helper.
* [`test_docker_direct_execution.py`](../../tests/python/docker/test_docker_direct_execution.py) asserts the session and compute messages for a non-zero exit.
* [`testing.yaml`](../../config/testing.yaml), [`AGENTS.md`](../../AGENTS.md) and [`docs/deployment.md`](../../docs/deployment.md) describe `file://<hostpath>`, host-side resolution and the fail-early test contract.

## Verification

* The new `DockerSimpleDataFileResourceValidatorImplTest` passed: 3 tests.
* The full offline Java suite passed: 94 tests, 0 failures, 0 errors.
* `bash -n tests/python/bin/config-tests.sh` and `py_compile` over the changed Python modules passed.
* `pytest --collect-only` could not run here because this container has no `pytest` module. The import-time `hostpath` check is therefore reviewed but not executed.
* Docker-level checks (bind mount and direct non-zero exit) require a broker and PostgreSQL; they are not run without the deployment services.

## Approval and commit

No commit, push or pull request until the human approves the exact change set and commit message. The draft message is `Reject null file locations and report failed executions`, with `Fixes: #108` and `Fixes: #111`, committed through `bin/agent-commit`.

## Status

in-progress — implementation complete on `20261119-zrq-null-file-locations`; verification and approval pending.

## AIMetrics

```
AIMetrics: [
    {
    "timestamp": "2026-11-19T17:53:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    }
  ]
```
