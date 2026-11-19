<!-- b22e7c08-4bda-44c5-a116-557f764d7e84 -->
---
todos:
  - id: "branch"
    content: "Create a branch from main at 9c7be580 for issue 53"
    status: completed
  - id: "model"
    content: "Persist the values map on MessageEntity, render the message on read, and copy values when claiming plain messages"
    status: completed
  - id: "templates"
    content: "Normalise the message templates to ${name}, add the pool and storage-linker placeholders, and fix the SLF4J placeholder"
    status: completed
  - id: "tests"
    content: "Add the MessageEntity and storage-linker tests, update the pool test, and strengthen the label integration tests"
    status: completed
  - id: "docs"
    content: "Document the message-template and SLF4J logging conventions in AGENTS.md"
    status: completed
  - id: "verify"
    content: "Run the Java tests and the any/mock and docker label tests"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Persist and render message values

Date: 2026-11-18
Scope: `Calycopis-broker` — persist the template values on `MessageEntity`, return them through `MessageItemBean`, render the message text, normalise message templates to `${name}` placeholders, add placeholders to the pool and storage-linker messages, and correct the malformed SLF4J placeholder in the session-prepare log.
Issue: [uksrc/Calycopis-broker#53](https://github.com/uksrc/Calycopis-broker/issues/53) — MessageEntity ignores values.
Branch: `20261118-zrq-message-values`, created from `main` at `9c7be580`.
Result: implemented in commit [`2cce19ac`](https://github.com/uksrc/Calycopis-broker/commit/2cce19ac) and opened as [PR #224](https://github.com/uksrc/Calycopis-broker/pull/224).

## Target

Every persisted message built from a template carries its values map and exposes a rendered `message`. Message templates use `${name}` placeholders that match their value keys. The pool-decline and storage-linker warnings reference the values they already build. SLF4J log statements keep `{}` placeholders, and the malformed `[${}]` in `PrepareSessionRequestEntity` becomes `[{}]`.

No OpenAPI schema or version change is needed: `MessageItem` already defines `template`, `values` and `message`.

## Where we are

* `MessageEntity` accepts `Map<String, Object> values` in its constructor but never stores it. `getValues()` returns an empty map and `getMessage()` returns the unset `message` column, except where `OfferSetEntity.claimMessages(...)` copies a plain message.
* `MessageItemBean` delegates to the entity, so clients see `values: null` and `message: null`.
* `MessageBean` already renders `${name}` placeholders; the same substitution can be reused.
* A fresh audit finds 47 message call sites with a values map. Fourteen call sites still use `[{}]`, one has a `${string}` template with a `value` key, and three value-bearing templates have no placeholders:
  * the pool-decline message in `SimpleComputeResourcePoolEntity`;
  * the two `urn:storage-required` warnings in `MockDataStorageLinkerImpl`.
* `PrepareSessionRequestEntity` has one malformed SLF4J format string at line 254: `[${}]` instead of `[{}]`. No other `log.*` format string contains `${...}`.
* `SimpleComputeResourcePoolOffersTest` asserts that the pool-decline template contains the pool and policy names. Moving those names into values means the test must assert values and the rendered message instead.
* The broker recreates its schema at every start (`spring.jpa.hibernate.ddl-auto: create`), so the new collection table needs no migration in the current deployment.

## Decisions

1. **Persist values as an element collection.** Add a `Map<String, String> values` field to `MessageEntity` using `@ElementCollection`, matching `DockerContainerEntity.environment` and `.labels`:
   * collection table `messagevalues`;
   * join column `message` referencing `messages.ident`;
   * map key column `messagekey`;
   * map value column `messagevalue`.
2. **Convert the constructor input to strings.** Keep the `Map<String, Object>` constructor, convert each entry with `safeString(...)`, skip null keys, map a null map to an empty map, and return an unmodifiable view from `getValues()`.
3. **Render on read.** When the stored `message` is null, `MessageEntity.getMessage()` renders the template with the values. A claimed plain message keeps its stored text. This avoids a second copy of derived text and avoids the `message` column length limit. Promote `MessageBean.render(...)` to package-private static and make it null-safe.
4. **Copy values when claiming plain messages.** `OfferSetEntity.claimMessages(...)` passes `message.getValues()` into the new entity instead of `Collections.emptyMap()`, keeping `setMessage(message.getMessage())`.
5. **Normalise all value-bearing templates.** Convert the 14 `[{}]` message templates to `${name}`, fix the duration key, add `${pool}` / `${policy}` to the pool message, and add `${storageref}` to both storage-linker warnings.
6. **Keep logging and messages separate.** SLF4J log statements use `{}` (usually written as `[{}]`); message templates use `${name}`. Correct the malformed `[${}]` in `PrepareSessionRequestEntity` and document the distinction.
7. **Test the behaviour.** Add `MessageEntityTest`, update the pool test, add a storage-linker test, and strengthen the two label integration tests.
8. **No OpenAPI schema or version change.** The only database change is the `messagevalues` collection table.

## Changes

### Java model

* `MessageEntity`: add the `@ElementCollection` values map, the `convertValues(...)` helper, the unmodifiable `getValues()`, and the rendered fallback in `getMessage()`.
* `MessageBean`: make `render(...)` package-private static and null-safe.
* `OfferSetEntity`: copy `message.getValues()` in `claimMessages(...)`.
* `MessageItemBean`: no change; it already delegates.

### Message producers

* `SimpleVolumeMountValidatorImpl`: two templates to `${resource}` / `${volume}` and `${data}` / `${storage}`.
* `DockerContainerValidatorImpl`: eight `[{}]` templates to `${value}`; remove the obsolete TODO about dropped values.
* `DockerDockerContainerValidatorImpl`: one template to `${value}`.
* `MockDockerContainerValidatorImpl`: two templates to `${value}`.
* `MockJupyterNotebookValidatorImpl`: one template to `${value}`.
* `OfferSetRequestParserImpl`: change the duration warning key from `value` to `string`.
* `SimpleComputeResourcePoolEntity`: use `Compute pool [${pool}] declined the request because policy [${policy}] rejected it.`
* `MockDataStorageLinkerImpl`: use `Unable to assign storage resource [${storageref}]` in both warnings.

### Logging

* `PrepareSessionRequestEntity`: change `[${}]` to `[{}]` at line 254, keeping the three arguments. Leave valid `[{}]` log statements unchanged.

### Tests

* New `MessageEntityTest`: values are stored, non-string values are stringified, null and empty maps work, unknown placeholders remain, a null template renders null, and `MessageItemBean` delegates.
* Update `SimpleComputeResourcePoolOffersTest` to assert the new template, the pool/policy values, and the rendered message.
* New `MockDataStorageLinkerImplTest` covering the accepted-but-null-object and unexpected-state branches, asserting the `storageref` value, the `${storageref}` template and the rendered message.
* Strengthen `test_user_labels_roundtrip.py` and `test_docker_user_labels.py` to assert the reserved-label values, template and rendered message.

### Documentation

* `AGENTS.md`: record that `MessageEntity` persists and renders values, that message templates use `${name}`, and that SLF4J log format strings use `{}` and never `${name}`.

## Verification

* Java: `./mvnw clean test` passed, 91 tests, 0 failures.
* Message call-site audit: no `[{}]` remains in a message-template call, no `${` remains in a `log.*` call, and no log format string contains `${...}`.
* Python `any mock` against a mock-profile broker: 151 passed, 1 unrelated failure in `mock/test_mock_validators.py::TestJupyterNotebookValidation::test_valid_notebook_location`. The failure is a `NullPointerException` from `OfferSetRequestParserContext.getExecutableResult()` because the Jupyter notebook validator never calls `setExecutableResult`; that code is untouched by this change.
* Python `docker/test_docker_user_labels.py` against a docker-profile broker: 3 passed, including the reserved-label values, template and rendered message assertions.
* The reserved-label response carries `values["value"]`, a `${value}` template and a rendered message with no placeholder.
* `git diff --check` passes and `git status` shows only the intended changes plus the pre-existing untracked note.

## Status

status: implemented
details: Implemented in commit [`2cce19ac`](https://github.com/uksrc/Calycopis-broker/commit/2cce19ac)
  and opened as [PR #224](https://github.com/uksrc/Calycopis-broker/pull/224).
  Java tests pass and the docker label tests pass. The any/mock suite has one
  unrelated Jupyter notebook validation failure in
  `test_valid_notebook_location`, caused by the validator never calling
  `setExecutableResult`.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-18T22:55:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T23:20:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T23:35:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    }
  ]
```
