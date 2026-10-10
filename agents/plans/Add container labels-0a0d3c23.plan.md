<!-- 0a0d3c23-0eff-4568-bd4f-6600f30cd059 -->
---
todos:
  - id: "label-helper"
    content: "Add a DockerContainerLabels helper holding the calycopis-broker-* keys"
    status: completed
  - id: "kind-fix"
    content: "Correct SimpleComputeResource.KIND_DISCRIMINATOR from kinds/computer to kinds/compute"
    status: completed
  - id: "compute-labels"
    content: "Label the execution container in DockerSimpleComputeResourceEntity"
    status: completed
  - id: "helper-labels"
    content: "Label the HTTP data-download helper container in DockerSimpleDataHttpResourceEntity"
    status: completed
  - id: "python-test"
    content: "Add tests/python/docker/test_docker_container_labels.py"
    status: completed
  - id: "docs"
    content: "Add the new test to the AGENTS.md test table"
    status: completed
  - id: "verify"
    content: "Compile, run the docker label test, and check with podman"
    status: completed
isProject: false
---
# Plan: Add calycopis-** labels to broker containers

Date: 2026-10-10
Scope: `Calycopis-broker` (paths relative to the repository root, `${CALYCOPIS_CODE}` at runtime)
Issue: [uksrc/Calycopis-broker#130](https://github.com/uksrc/Calycopis-broker/issues/130) — Add calycopis-** labels to containers
Parent: [uksrc/Calycopis-broker#25](https://github.com/uksrc/Calycopis-broker/issues/25) — Add labels to our containers

## Target

Give every container the broker launches a small set of broker-owned
`calycopis-broker-*` labels, so that a container on the Podman host can be
traced back to the broker record that created it — for debugging, for
reconciliation of orphaned containers, and as the foundation for the
user-defined labels in [#131](https://github.com/uksrc/Calycopis-broker/issues/131).

This issue is deliberately **broker-internal only**: there is no change to the
OpenAPI schema, the request/response models, or the broker version, because
nothing crosses the API boundary yet. The one exception is the compute kind URI
correction below, which changes an existing response value to match the schema
rather than adding a field.

## Findings

### Where containers are created

Only two places in the broker call the Docker API `createContainerCmd`:

| Site | Container | Lifetime |
|---|---|---|
| `DockerSimpleComputeResourceEntity.createAndStartContainer()` (`engine/entities/compute/simple/docker/DockerSimpleComputeResourceEntity.java:812`, called at lines 693 and 713) | The execution container built from the requested `DockerContainer` image | Long — until the session is released or cancelled |
| `DockerSimpleDataHttpResourceEntity.makePrepareAction()` (`engine/entities/data/simple/docker/http/DockerSimpleDataHttpResourceEntity.java:298`) | The short-lived helper container that pulls an HTTP data URL into a storage volume | Removed immediately after the download completes (line 341) |

Both are reached from the `processing` loop's prepare actions, so both are
inside a Hibernate transaction when `makePrepareAction` runs.

### Available identifiers

| Value | Source at the create site | Notes |
|---|---|---|
| Session UUID | `this.getSession().getUuid()` | The `/sessions/{uuid}` identifier |
| Compute resource UUID | `this.getUuid()` on `DockerSimpleComputeResourceEntity` | Broker's record of this container execution |
| Data resource UUID | `this.getUuid()` on `DockerSimpleDataHttpResourceEntity` | Broker's record of the data resource that owns the helper |
| Resource kind URI | `this.getKind()` on either entity | Static schema kind — `SimpleComputeResource.KIND_DISCRIMINATOR` (`.../kinds/compute/simple-compute-resource.yaml`, corrected below) or `SimpleDataResource.KIND_DISCRIMINATOR` (`.../kinds/data/simple-data-resource.yaml`) |
| Executable UUID | `this.session.getExecutable().getUuid()` | The `DockerContainer` definition; shared by repeats |

The `session` field on `AbstractComputeResourceEntity` and
`AbstractDataResourceEntity` is `@ManyToOne(fetch = FetchType.LAZY)`
(`AbstractComputeResourceEntity.java:139-141`), so `getSession().getUuid()`
**must be read inside `makePrepareAction`** and captured in a `final` local
before the anonymous `ProcessingAction` is returned. This follows the existing
"Eagerly resolve all data from the Hibernate session" pattern already used for
`maxCores`, `maxMemory`, `bindList`, `imageName` and `volumeName`; it is not a
new workaround.

### Pre-existing compute kind URI mismatch (fixed as part of this change)

`getKind()` is the natural source for `calycopis-broker-resource-kind`, so the
value it returns was checked against the schema. It is wrong for compute
resources:

| Source | Value |
|---|---|
| Schema (`Calycopis-openapi/schema/v1.0/components.yaml:248`) and the generated Spring/Python clients | `.../kinds/**compute**/simple-compute-resource.yaml` |
| `SimpleComputeResource.KIND_DISCRIMINATOR` (`SimpleComputeResource.java:54`) | `.../kinds/**computer**/simple-compute-resource.yaml` |

`kinds/computer` occurs exactly once in the workspace — that constant. It is
returned by `SimpleComputeResourceEntity.getKind()` and written into responses by
`AbstractComputeResourceEntity.fillBean()` (`AbstractComputeResourceEntity.java:291`).
Request validation dispatches on the Java class rather than the kind string, so
the mismatch does not break requests, and no test currently asserts the value.

The maintainer agreed to fix the constant here rather than label containers with
a value known to be wrong. This plan therefore also corrects it to
`.../kinds/compute/...`: a one-line change with no schema or version impact that
makes the label and the API response agree with the schema.

### Dependencies and existing support

- `com.github.docker-java:docker-java-core:3.4.1` is already a dependency
  (`java/pom.xml:312-316`), and `CreateContainerCmd.withLabels(Map<String,String>)`
  is available — **no new dependency is needed**.
- There is no existing label code, constant, or configuration anywhere in the
  repository, and no `label` field in the schema (that is #131).
- Podman/Docker label keys allow `[a-zA-Z0-9.-]`, so the hyphenated
  `calycopis-broker-*` keys are valid.

### Test capability

`tests/python/docker/test_docker_androcles_md5.py` already uses
`docker.DockerClient(base_url=CONTAINER_HOST)` from inside the test container, and
`tests/python/bin/run-tests.sh` passes `CONTAINER_HOST` through to both the broker
and the tester containers (lines 191 and 256). A label-inspection test is
therefore feasible without new test infrastructure.

## Label scheme (agreed with the maintainer)

| Key | Value | Applied to |
|---|---|---|
| `calycopis-broker-session-uid` | Execution session UUID (`/sessions/{uuid}`) | Every container the broker launches |
| `calycopis-broker-resource-uid` | UUID of the broker resource that launched the container — the **compute resource** for the execution container, the **data resource** for the HTTP helper | Every container the broker launches |
| `calycopis-broker-resource-kind` | Schema kind URI of that resource (`getKind()`) | Every container the broker launches |
| `calycopis-broker-container-role` | Role of the container within the broker: `execution` or `data-download` | Every container the broker launches |

Decisions confirmed:

 * `calycopis-broker-resource-uid` is deliberately **not** `execution-uid`: the
   same key then works for data resources and for any future container-launching
   resource, not just compute.
 * `calycopis-broker-resource-kind` carries the entity's `getKind()` URI, reusing
   the schema vocabulary rather than inventing a local token — consistent with the
   "do not define constants that replicate existing enum values" coding rule.
 * `calycopis-broker-container-role` distinguishes the long-running execution
   container from the short-lived data-download helper (and can gain further
   values later, e.g. a future Jupyter container).
 * The short-lived HTTP download helper container is labelled too.
 * The exact key names from the issue comment are used as written; no
   reverse-DNS re-namespacing.
 * The `calycopis-broker-` prefix is **reserved**. When #131 lands, user labels
   are merged underneath these internal labels and cannot override them.

## Changes

### 1. New helper — `engine/functional/platform/docker/DockerContainerLabels.java`

A small, framework-neutral final class (the `engine/` tree takes no Spring
imports) so the keys are defined once and reused by any future platform
implementation:

```java
public final class DockerContainerLabels
    {
    public static final String SESSION_UID    = "calycopis-broker-session-uid";
    public static final String RESOURCE_UID   = "calycopis-broker-resource-uid";
    public static final String RESOURCE_KIND  = "calycopis-broker-resource-kind";
    public static final String CONTAINER_ROLE = "calycopis-broker-container-role";

    public static final String ROLE_EXECUTION     = "execution";
    public static final String ROLE_DATA_DOWNLOAD = "data-download";

    public static Map<String, String> makeLabels(
        final UUID sessionUuid,
        final UUID resourceUuid,
        final URI resourceKind,
        final String containerRole
        ){
        Map<String, String> labels = new HashMap<String, String>();
        if (sessionUuid != null)
            {
            labels.put(SESSION_UID, sessionUuid.toString());
            }
        if (resourceUuid != null)
            {
            labels.put(RESOURCE_UID, resourceUuid.toString());
            }
        if (resourceKind != null)
            {
            labels.put(RESOURCE_KIND, resourceKind.toString());
            }
        if (containerRole != null)
            {
            labels.put(CONTAINER_ROLE, containerRole);
            }
        return labels;
        }
    }
```

Plus the GPL licence header and an `AIMetrics` entry, per
[`agents/rules/licence-header.mdc`](../rules/licence-header.mdc) and
[`agents/rules/ai-metrics.mdc`](../rules/ai-metrics.mdc).

### 2. Fix the compute resource kind URI — `SimpleComputeResource.java`

Change the one-line constant so the label value, the API response, and the
schema all agree:

```java
public static final URI KIND_DISCRIMINATOR = URI.create(
    "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/compute/simple-compute-resource.yaml"
    );
```

No other code keys off this constant for dispatch, so the only behavioural
change is that compute resources are now reported with the schema's kind URI.
Bump the copyright year and append an `AIMetrics` entry.

### 3. Label the execution container — `DockerSimpleComputeResourceEntity.java`

 * In `makePrepareAction`, alongside the existing eager resolves, add:

   ```java
   final UUID sessionUuid = this.getSession().getUuid();
   final Map<String, String> labels = DockerContainerLabels.makeLabels(
       sessionUuid,
       this.getUuid(),
       this.getKind(),
       DockerContainerLabels.ROLE_EXECUTION
       );
   ```

 * Add a `final Map<String, String> labels` parameter to
   `createAndStartContainer(...)` and pass it at **both** call sites (the first
   attempt at line 693 and the retry-without-resource-limits at line 713).
 * In `createAndStartContainer`, chain `.withLabels(labels)` onto the create
   command:

   ```java
   var createCmd = dockerClient.createContainerCmd(imageName)
       .withEnv(envList)
       .withLabels(labels)
       .withHostConfig(hostConfig);
   ```

 * Bump the copyright year and append an `AIMetrics` entry.

Note the label map describes the container, not the host config, so it must be
applied to **both** the first attempt and the retry — the retry currently
rebuilds only the `HostConfig`.

### 4. Label the HTTP helper container — `DockerSimpleDataHttpResourceEntity.java`

 * In `makePrepareAction`, after `dataUrl` is resolved and before the anonymous
   action is returned, capture:

   ```java
   final UUID sessionUuid = this.getSession().getUuid();
   final Map<String, String> labels = DockerContainerLabels.makeLabels(
       sessionUuid,
       this.getUuid(),
       this.getKind(),
       DockerContainerLabels.ROLE_DATA_DOWNLOAD
       );
   ```

 * Chain `.withLabels(labels)` onto the helper `createContainerCmd` at line 298.
 * Bump the copyright year and append an `AIMetrics` entry.

### 5. Not changed

 * `pom.xml` — no new dependency.
 * `config.yaml` / `application.yaml` — the labels are internal constants, not
   configuration, for #130.
 * The OpenAPI schema, generated packages, and `config.yaml` versions — the kind
   fix corrects the broker to match the existing schema, so no schema edit and no
   version bump are needed.

## Testing

### Java build

```bash
pushd "${CALYCOPIS_CODE:?}"
source bin/versions.sh config.yaml
pushd java
./mvnw clean compile
popd
popd
```

### New Python test — `tests/python/docker/test_docker_container_labels.py`

Follows the existing docker-platform test conventions
(`test_docker_direct_execution.py` for the request shape and
`test_docker_androcles_md5.py` for the docker-py client):

 1. Direct-execute a `heliophorus-cantliei` container with a long
    `pause_seconds` (about 30) and an explicit `SimpleComputeResource`, so the
    container is still running when the labels are inspected.
 2. From `session.meta.uuid` and `session.compute.meta.uuid`, list containers
    with docker-py:

    ```python
    client.containers.list(
        all=True,
        filters={"label": f"calycopis-broker-session-uid={session.meta.uuid}"},
    )
    ```

    Poll until the container appears, or time out (reuse the `phase_timeout`
    helper from `calycopis_conftest`).
 3. Assertions:
    * exactly one container matches the session label;
    * it carries all four `calycopis-broker-*` labels;
    * `calycopis-broker-resource-uid` equals `session.compute.meta.uuid`;
    * `calycopis-broker-resource-kind` equals the **literal** schema URI
      `https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/compute/simple-compute-resource.yaml`
      — asserted as a literal, not against `session.compute.kind`, because both
      sides read the same Java constant and a constant-to-constant check would
      not catch the kind regression;
    * `session.compute.kind` also equals that literal URI, giving the kind fix
      regression coverage;
    * `calycopis-broker-container-role` is `execution`;
    * the two UUID values are the expected UUID strings.
 4. Clean up by cancelling the session and removing the container.
 5. A second test that a session created through the offer-set flow
    (`POST /requests` → accept the offer) is labelled the same way — this
    covers the path where the session UUID is not known before the container
    starts.

The data-download helper container is removed as soon as the download
finishes, so a reliable automated assertion on it is not practical; the change
there is covered by code review and by the manual podman check below.

### Manual verification

With a broker running on the `docker` profile:

```bash
podman ps -a \
  --filter label=calycopis-broker-session-uid \
  --format '{{.ID}}  {{.Names}}  {{.Labels}}'

podman inspect <container-id> --format '{{json .Config.Labels}}'
```

The execution container should show all four `calycopis-broker-*` labels; the
value of the session label should match the UUID returned by
`GET /sessions/{uuid}`, and the role should be `execution`. For example:

```bash
podman ps -a \
  --filter label=calycopis-broker-container-role=execution \
  --format '{{.ID}}  {{.Names}}  {{.Labels}}'
```

### Test registration

Add a row for `test_docker_container_labels.py` to the test table in
[`AGENTS.md`](../../AGENTS.md) under "Python tests". The `pytest -v -s docker`
invocation picks the file up automatically; no
`tests/python/bin/run-tests.sh` change is needed.

## Verification checklist

- [x] `./mvnw clean compile` succeeds (BUILD SUCCESS, 351 source files).
- [x] `grep -rn "calycopis-broker-" java/src/main` shows the keys only in
      `DockerContainerLabels.java`.
- [x] `grep -rn "kinds/computer" .` returns nothing outside this plan document,
      and `SimpleComputeResource.KIND_DISCRIMINATOR` ends
      `kinds/compute/simple-compute-resource.yaml`.
- [x] A session response reports `compute.kind` as the schema's
      `kinds/compute/...` discriminator. (asserted by the label test)
- [x] The new Python test passes against a broker on the `docker` profile:
      `2 passed in 131.38s`, with the broker and database launched as Podman
      containers by the agent (see `notes/zrq/20261010-01-agent-broker-dev.txt`).
- [x] The test file's imports, client methods and request construction were
      checked against the installed `calycopis_openapi_client` wheel.
- [x] `podman ps --filter label=calycopis-broker-session-uid` finds the running
      execution container; `podman ps` shows all four labels, including
      `calycopis-broker-resource-kind` ending `kinds/compute/...`.
- [ ] Diff reviewed for the licence header, copyright year and `AIMetrics`
      conventions; commits made with `bin/agent-commit` after approval.

## Out of scope — deferred to #131

[#131](https://github.com/uksrc/Calycopis-broker/issues/131) adds a user-defined
`labels` field to `DockerContainer` in the request and response, which requires
an OpenAPI schema change and the associated version increments
(`openapi.schema.version`, `openapi.spring.version`, `openapi.python.version`,
`broker.version`), regenerated Spring and Python packages, model plumbing
through the validators/factories/entities, and validation of user label keys
and values.

This plan is shaped to make that step a merge rather than a rewrite:
`DockerContainerLabels.makeLabels(...)` returns the internal base map, and #131
will merge the validated user labels into that map **without** allowing a user
key to collide with the reserved `calycopis-broker-` prefix (or reject such a
request outright). Which of those two collision policies to use is a decision
for #131, not this issue.

## Risks and open decisions

1. **Resource kind value and the corrected constant.**
   `calycopis-broker-resource-kind` carries the full schema kind URI from
   `getKind()` (about 90 characters), which is verbose beside a UUID but
   unambiguous and reuses the schema vocabulary. A short token would read better
   in `podman ps` but would duplicate that vocabulary, so the URI is used. The
   same change corrects the compute kind URI from `computer` to `compute`, so a
   client that matched the old, schema-invalid string in a response would see a
   different value. The schema and generated clients already expect `compute`, so
   this is a bugfix rather than a breaking API change, but it is called out
   because it changes response content.
2. **Container role values.** `execution` and `data-download` are code constants
   in the helper. Further container-launching resources (for example a future
   Jupyter container) should add a role there rather than an inline string, so
   the set stays discoverable.
3. **Label key naming.** Namespaced keys (`io.calycopis.*`) are the Docker
   convention, but the issue explicitly asks for `calycopis-**` and the
   comment names the keys exactly, so the literal keys are used.
4. **Lazy session access.** Any future refactor that reads the session UUID
   inside the action's `process()` rather than in `makePrepareAction` would hit
   a `LazyInitializationException`; the eager local capture avoids this.
5. **Configuration.** The prefix is a constant, not a setting. If deployments
   need to customise it, it can move into `calycopis.broker.docker` later
   without changing the call sites.
