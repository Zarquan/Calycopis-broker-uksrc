<!-- 538a5255-dcb6-45e4-b24b-640038dc208b -->
---
todos:
  - id: "branch"
    content: "Create a branch from main at cd581ab8 for issues 142 and 220"
    status: completed
  - id: "validator"
    content: "Register the accepted JupyterNotebook validator result in the offer-set request context"
    status: completed
  - id: "entity"
    content: "Preserve the notebook location when an entity is converted back to its OpenAPI bean"
    status: completed
  - id: "tests"
    content: "Strengthen the mock notebook validation test to check the offer executable location"
    status: completed
  - id: "harness"
    content: "Run docker and mock broker lifecycles from run-tests.sh, with any against both profiles"
    status: completed
  - id: "docs"
    content: "Update AGENTS.md and docs/deployment.md for the two CI lifecycles and the states decision"
    status: completed
  - id: "verify"
    content: "Run the Java tests and the mock/docker Python suites"
    status: completed
  - id: "commit"
    content: "Present the diff and commit message for approval; do not commit until approved"
    status: pending
isProject: false
---
# Plan: Mock platform CI and Jupyter notebook validation

Date: 2026-11-20
Scope: `Calycopis-broker` — exercise the mock platform in CI and fix the JupyterNotebook validation result that produces an HTTP 500 on a valid offer-set request.
Issues:
 * [uksrc/Calycopis-broker#142](https://github.com/uksrc/Calycopis-broker/issues/142) — Test and fix the mock platform; the mock suite is not run by CI.
 * [uksrc/Calycopis-broker#220](https://github.com/uksrc/Calycopis-broker/issues/220) — `TestJupyterNotebookValidation.test_valid_notebook_location` fails.
Branch: `20261120-zrq-mock-ci-jupyter`, created from `main` at `cd581ab8`.

## Problem

`tests/python/bin/run-tests.sh` already ran `pytest -v docker any`, but only after
`bin/config-broker.sh` wrote `spring.profiles.active: docker`. Nothing started a
mock-profile broker, so `tests/python/mock/` was never exercised and could drift
without CI noticing.

The Jupyter failure was an internal error, not a rejected request. On a mock
broker, a valid JupyterNotebook request produced HTTP `500`, and the broker log
showed:

```
NullPointerException: Cannot invoke
"AbstractExecutableValidator$Result.build(SimpleExecutionSessionEntity)"
because the return value of
"OfferSetRequestParserContext.getExecutableResult()" is null
    at OfferSetRequestParserImpl.stageTwo(OfferSetRequestParserImpl.java:525)
```

`OfferSetRequestParserImpl` calls `validateEnum(...)`, which discards the result
returned by the validator. Each executable validator is responsible for putting
its accepted result into the context. `DockerContainerValidatorImpl` does that at
line 244; `JupyterNotebookValidatorImpl` returned its result without registering
it. The excluded and missing-location tests passed only because validation failed
and no offers were generated.

A related latent bug was in `JupyterNotebookEntity.fillBean(...)`: it never copied
`this.location` into the response bean, so a successful notebook session lost its
location.

## Decisions

 * Run **two sequential broker lifecycles** in one pod. A broker has one active
   profile and only one broker fits on port 8082, so the harness starts the
   docker-profile broker, runs `docker any`, stops it, starts the mock-profile
   broker, runs `mock any`, and stops it again.
 * Select the profile with `SPRING_PROFILES_ACTIVE` on the broker container. This
   overrides `spring.yaml`, so `config-broker.sh` and the generated database
   credentials stay unchanged.
 * Generate the test data once, before the first suite, so both profiles use the
   same `testing.yaml` and test-data files.
 * Keep `tests/python/states/` out of CI. It needs direct database access and a
   broker configured with a specific pool and allocation, and its expiry tests
   take several minutes. The decision is recorded in `AGENTS.md` and
   `docs/deployment.md`.
 * Fix the validator and the entity; do not suppress the exception or fabricate
   an empty result.

## Changes

### Java

 * `engine/entities/executable/jupyter/JupyterNotebookValidatorImpl.java`
   * In the accepted branch, call `context.setExecutableResult(result)` before
     returning the result, mirroring `DockerContainerValidatorImpl`.
   * The failure branch is unchanged.
 * `engine/entities/executable/jupyter/JupyterNotebookEntity.java`
   * In `fillBean`, set the location from the entity after `super.fillBean(bean)`.

### Python tests

 * `tests/python/mock/test_mock_validators.py`
   * `TestJupyterNotebookValidation.test_valid_notebook_location` now also checks
     that the offer's executable carries
     `http://example.com/valid-notebook.ipynb`.
   * Without the context registration the request raises HTTP `500`; without the
     entity fix the location is absent. The one test covers both.

### Test harness

 * `tests/python/bin/run-tests.sh`
   * Add `start_broker`, `wait_for_broker`, `stop_broker`, `run_suite` and
     `run_lifecycle` helpers.
   * Start the broker with `--env "SPRING_PROFILES_ACTIVE=${profile}"`.
   * Run `docker any` on the docker lifecycle with test-data generation, then
     `mock any` on the mock lifecycle without regenerating it.
   * Remove a leftover broker container only after checking that it exists, and
     remove the broker on both success and failure of a lifecycle.
   * No output suppression and no `|| true`.
 * `.github/workflows/build-packages.yml` needs no change; it already calls
   `run-tests.sh`.

### Documentation

 * `AGENTS.md`: CI runs docker and mock, `any` runs on both profiles, and
   `states` remains manual.
 * `docs/deployment.md`: the same two-lifecycle description, plus the statement
   that wiring `states` into CI is a separate change.
 * This plan document records the session.

## Verification

 * `bash -n tests/python/bin/run-tests.sh`.
 * On a mock-profile broker:
   * `pytest -v mock/test_mock_validators.py::TestJupyterNotebookValidation::test_valid_notebook_location`;
   * `pytest -v mock any` (expected `152 passed`);
   * confirm no `NullPointerException` for `/requests` in the broker log.
 * On a docker-profile broker: `pytest -v docker any`, unchanged.
 * Java unit tests with the offline Maven repository.
 * Run the full `run-tests.sh` once in a CI-like environment and check both
   profile startup messages and both pytest invocations.

## Acceptance criteria

 * CI executes the mock suite and fails when it fails.
 * `any` runs against both docker and mock profiles.
 * A valid JupyterNotebook request on the mock profile returns `YES` with offers.
 * The notebook location round-trips into the offer executable.
 * Excluded and missing notebook locations still return `NO`.
 * `states` remains manual and documented.
 * No schema, migration or public API shape change.

## Out of scope

 * Mock simulation of exit codes and configurable execution durations; the
   excluded cases in `test_mock_direct_execution.py` remain excluded.
 * Wiring `states/` into CI.
 * OpenAPI schema or database schema changes.

## Status

status: in-progress
details: Implementation and verification complete on
  `20261120-zrq-mock-ci-jupyter`. The Java build passed 94 tests, the mock
  lifecycle passed `pytest -v mock any` (152 passed), and the docker lifecycle
  passed `pytest -v docker any` (161 passed). The notebook test now checks the
  offer executable location. Approval of the diff and commit message is
  pending; no commit, push or pull request yet.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-20T12:23:00",
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
