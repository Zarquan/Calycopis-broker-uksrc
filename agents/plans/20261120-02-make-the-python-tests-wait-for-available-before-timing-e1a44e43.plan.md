<!-- e1a44e43-fd0c-483d-8e0c-dde1557f78d4 -->
---
todos:
  - id: "branch"
    content: "Create a branch from main at 3ebbef5a for issues 115, 123 and 183"
    status: completed
  - id: "helper"
    content: "Add wait_for_available and wait_for_terminal_after_available to conftest.py"
    status: completed
  - id: "helper-tests"
    content: "Add fake-client unit tests for the phase-wait helpers"
    status: completed
  - id: "completion-tests"
    content: "Make the completion tests wait for AVAILABLE before starting their completion deadline"
    status: completed
  - id: "timed-test"
    content: "Measure the timed container lower bound from the scheduled preparing.start"
    status: completed
  - id: "stress"
    content: "Track EXPIRED and REJECTED in the stress tests and surface expired offers immediately"
    status: completed
  - id: "docs"
    content: "Document the AVAILABLE-before-timing rule in AGENTS.md and this plan"
    status: completed
  - id: "verify"
    content: "Run static checks and the available test suites; review the diff"
    status: pending
  - id: "commit"
    content: "Present the diff and commit message for approval; do not commit until approved"
    status: pending
isProject: false
---
# Plan: Make the Python tests wait for AVAILABLE before timing

Date: 2026-11-20
Scope: `Calycopis-broker` — Python integration tests and their shared helpers only. No Java, OpenAPI schema, generated client or broker behaviour changes.
Issues:
 * [uksrc/Calycopis-broker#115](https://github.com/uksrc/Calycopis-broker/issues/115) — Python stress test does not track phase changes and polls expired offers.
 * [uksrc/Calycopis-broker#123](https://github.com/uksrc/Calycopis-broker/issues/123) — fragile test timing; completion timeouts include preparation and image download.
 * [uksrc/Calycopis-broker#183](https://github.com/uksrc/Calycopis-broker/issues/183) — timed tests race the client observation window against the container start.
Branch: `20261120-zrq-wait-for-available`, created from `main` at `3ebbef5a`.

## Problem

The three issues are one fragility seen from different angles. Tests accept an offer and then call `client.wait_for_phase()` for a terminal phase. That helper starts its deadline when it is called, so the completion timeout includes `ACCEPTED -> PREPARING -> AVAILABLE`, including any image pull. A slow pull can consume the completion budget before the container runs (#123).

The timed container test already waits for AVAILABLE, but then measures from the moment the client observes AVAILABLE. The broker starts the container during PREPARING, so the observed window can begin after part of the container's run time has passed. That is the CI failure `Session completed too quickly (29.7s) for a 30s container` (#183).

AVAILABLE is transient. Polling for it as an exact target can miss it when the session moves to RUNNING or COMPLETED between polls. The stress test's terminal set also excludes EXPIRED and REJECTED, and accept failures are only summarised after the tracking loop, so an offer that expired before acceptance is not identified and the test can poll until its overall timeout (#115).

## Decisions

 * Put the phase-wait layer in `tests/python/conftest.py`, beside `phase_timeout()`, so all suites share one implementation and the existing `calycopis_conftest` alias exports it.
 * `wait_for_available()` returns on AVAILABLE, RUNNING, RELEASING or COMPLETED, because all four mean the session got past AVAILABLE. It also returns a terminal phase before AVAILABLE, so the caller reports the real outcome instead of hiding it behind a timeout.
 * `wait_for_terminal_after_available()` gives preparation its own budget and starts the completion deadline only after AVAILABLE (or later/terminal) is observed. The androcles tests use a larger availability budget than completion budget.
 * A timed test measures its lower bound from the session's scheduled `preparing.start`, the earliest instant the container can run. It keeps the upper bound measured from the first observed AVAILABLE/RUNNING window. Do not widen the 30s pause or 60s overhead.
 * The stress tests keep their two-phase submit/accept structure. They recognise EXPIRED and REJECTED as terminal, record the first AVAILABLE-or-later observation, and fail immediately after acceptance if any offer expired or acceptance failed.
 * No new client method or schema field is needed; `GET /sessions/{uuid}` already exposes the phase and the compute schedule.

## Changes

### Shared helpers

`tests/python/conftest.py`:

 * Add `import time`.
 * Add `AVAILABLE_OR_LATER_SESSION_PHASES` and `TERMINAL_EXECUTION_PHASES` as tuples of `SimpleExecutionSessionPhase` values.
 * Add `wait_for_available(client, session_uuid, timeout=300.0, interval=2.0)`. It polls `client.get_session()` with a `time.monotonic()` deadline, returns on AVAILABLE-or-later or a terminal phase, and raises `TimeoutError` naming the UUID, last phase and timeout.
 * Add `wait_for_terminal_after_available(client, session_uuid, timeout, interval=2.0, available_timeout=None)`. It calls `wait_for_available()` first, returns immediately if that response is already terminal, otherwise calls `client.wait_for_phase()` with the terminal set and only the completion timeout.

### Completion tests

Replace `client.wait_for_phase(..., COMPLETED/FAILED)` immediately after `set_session_phase(..., ACCEPTED)` with the new helper:

 * `tests/python/docker/test_docker_androcles_md5.py` — all four lifecycle/checksum tests. Availability budget `phase_timeout(300)`; completion budget keeps `PHASE_TIMEOUT`.
 * `tests/python/docker/test_docker_volume_mount.py` — `test_session_completes` and `test_md5_matches`.
 * `tests/python/docker/test_docker_session_connectors.py` — `test_connectors_finished_after_completion`, `TestSessionConnectorEndpoints._run_to_completion`, and `test_alpine_stdout_and_stderr_endpoints`. `test_connectors_available_when_logs_captured` still polls the transient connector AVAILABLE status directly.
 * `tests/python/docker/test_docker_platform.py` — `test_short_and_long_containers` and the four non-zero/zero exit-code tests.
 * `tests/python/docker/test_docker_direct_execution.py` — `test_direct_execution_completes`, `test_direct_execution_nonzero_exit_fails`, `test_direct_execution_timed_completion`, and both sessions in `test_direct_and_offersets_both_complete`.
 * `tests/python/mock/test_mock_direct_execution.py` — `test_direct_execution_completes`.

Deliberately unchanged:

 * Phase-observation tests (`test_session_reaches_preparing`, `test_session_reaches_available`, `test_accept_offer`).
 * `test_docker_bind_mount.py`, whose lifecycle tests already wait for PREPARING/RUNNING.
 * Container-label tests, which inspect the container during PREPARING and do not wait for completion.
 * Cancellation and rejection tests, which act before AVAILABLE.
 * `SessionRecorder` clean-up, which uses its own terminal set.

### Timed container test

`test_session_completes_with_timed_container`:

 * Replace the exact-phase phase 2 wait with `wait_for_available()`, keeping the scheduled-window timeout.
 * Record `available_observed = time.monotonic()` before the completion wait.
 * Capture `completed_observed = time.monotonic()` and `completed_at = datetime.now(timezone.utc)`.
 * Lower bound: `(completed_at - prepare_start).total_seconds() >= pause_seconds`. The container starts during PREPARING and the broker holds preparation until `preparing.start`, so this cannot lose run time.
 * Upper bound: `completed_observed - available_observed <= pause_seconds + completion_overhead`.
 * Document both measurement origins in the docstring.

### Stress tests

`tests/python/docker/test_docker_stress.py`:

 * Add `REJECTED` and `EXPIRED` to `PHASE_ORDER` and `TERMINAL_PHASES`.
 * In `_accept_one`, catch a failed accept, fetch the session phase, and return an error naming the session and phase.
 * Immediately after the accept phase, fail with the collected errors and a note that offers may have expired before acceptance, instead of entering the tracking loop.
 * Record `timestamps[uuid]["available"]` on the first AVAILABLE-or-later observation.
 * Report counts for REJECTED and EXPIRED, report AVAILABLE-to-terminal durations, and assert `expired_count == 0`.

`tests/python/mock/test_mock_stress.py`:

 * The same phase-set, AVAILABLE timestamp, reporting and EXPIRED assertion changes. Direct execution cannot expire today, but the tracking is defensive and consistent.

### Regression tests and documentation

 * Add `tests/python/any/test_phase_wait.py` with fake-client tests for AVAILABLE-or-later, terminal-before-AVAILABLE, timeout diagnostics, and the split availability/completion budgets. The file carries a GPL header and AIMetrics.
 * Update the Python-test section of `AGENTS.md` with the AVAILABLE-before-timing rule, the lower-bound measurement origin, and links to the three issues. Add the new test file to the table.
 * This plan document is the working record. Every modified or new source file gets an AIMetrics entry.

## Verification

 * `python -m py_compile` on every modified or new Python file.
 * `pytest -v any/test_phase_wait.py` in the test container (or a local environment with the generated client), which exercises the helpers without a broker.
 * `pytest -v docker` and `pytest -v any` against a docker-profile broker; repeat the timed test to confirm it no longer reports a short elapsed time on a busy runner.
 * `pytest -v mock` and `pytest -v any` against a mock-profile broker.
 * A docker stress run with a low expiry, confirming it fails immediately with an explicit EXPIRED/accept-phase message rather than polling to `STRESS_TIMEOUT`.
 * `tests/python/bin/run-tests.sh` if the full lifecycle can be run.
 * `git diff --check` and a diff review.

## Out of scope

 * Java, OpenAPI schema, generated client and broker configuration changes.
 * Estimating preparation time in offers, the forward-looking part of #123.
 * Changing the 30s pause, the 60s completion overhead, or the stress submit/accept structure.
 * Wiring `tests/python/states/` into CI.

## Status

status: in-progress
details: Implementation is complete on `20261120-zrq-wait-for-available`.
  `py_compile` and `git diff --check` pass, all 233 Python tests collect, and
  the helper tests pass when exercised directly with the fake client. The
  broker-dependent suites have not run because no broker is reachable at
  `localhost:8082` in this workspace. Approval of the diff and commit message
  is pending; no commit, push or pull request yet.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-20T14:52:19",
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
