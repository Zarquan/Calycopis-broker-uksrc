<!-- 5958e79b-2035-4dbe-afa1-55a3cfafbce3 -->
---
todos:
  - id: "port-tooling"
    content: "Port bin/agent-commit, agents/git-identity.env and the commit-msg hook into Calycopis-openapi"
    status: completed
  - id: "schema"
    content: "Add labels (NameValueMap) to DockerContainer in schema/v1.0/kinds/executable/docker-container.yaml"
    status: completed
  - id: "versions-openapi"
    content: "Bump Calycopis-openapi config.yaml to 1.0.8 and align the dev numbers"
    status: completed
  - id: "regenerate"
    content: "Rebuild schema, Java spring, Java client and Python client; install the spring jar and Python wheel"
    status: completed
  - id: "versions-broker"
    content: "Bump Calycopis-broker config.yaml to 1.0.8"
    status: completed
  - id: "broker-model"
    content: "Add getLabels() to DockerContainer and the JPA entity, and set it in fillBean"
    status: completed
  - id: "validator"
    content: "Validate labels, rejecting the reserved calycopis-broker- prefix"
    status: completed
  - id: "merge-labels"
    content: "Merge user labels with the internal labels at container create time"
    status: completed
  - id: "tests"
    content: "Add Python tests for label round-trip, launch and reserved-prefix rejection"
    status: completed
  - id: "verify"
    content: "Build the broker, run the docker and mock label tests, verify with podman"
    status: completed
isProject: false
---
# Plan: Add user defined labels to the broker API

Date: 2026-10-10
Scope: `Calycopis-openapi` (schema and generated packages) and `Calycopis-broker` (API plumbing)
Issues: [uksrc/Calycopis-broker#131](https://github.com/uksrc/Calycopis-broker/issues/131) — Add user defined labels to containers
Parent: [uksrc/Calycopis-broker#25](https://github.com/uksrc/Calycopis-broker/issues/25) — Add labels to our containers
Depends on: [#130](https://github.com/uksrc/Calycopis-broker/issues/130) — internal `calycopis-broker-*` labels (done)

## Target

Let a user specify their own container labels in the `DockerContainer`
executable, see them echoed back in the API response, and have the broker apply
them to the container it launches, alongside the internal
`calycopis-broker-*` labels.

This is the second half of #25. Unlike #130 it changes the OpenAPI interface, so
it needs a schema change and version increments, and the generated Spring and
Python packages have to be rebuilt and reinstalled before the broker can use the
new field.

## Decisions (agreed with the maintainer)

| Decision | Choice |
|---|---|
| Schema shape | `NameValueMap`, exactly like the existing `environment` field |
| Reserved keys | A user label beginning `calycopis-broker-` **fails validation** with a clear message |
| Versions | Patch bump everything to `1.0.8`; Python is `1.0.8.dev0`; align the current `dev5` (broker) / `dev6` (openapi) mismatch |
| Branches | Create the equivalent branch in the Calycopis-openapi clone, as in Calycopis-broker |
| Openapi commits | Port `bin/agent-commit`, `agents/git-identity.env` and the commit-msg hook into the openapi clone first |
| Licensing | Mixed by design: Creative Commons (CC-BY-SA-4.0) for the OpenAPI schema, GPL for everything else |

The `labels` field carries **user labels only**. The internal `calycopis-broker-*`
labels are a launch-time detail and stay out of the API model.

## Findings

### The `environment` field is the template

`DockerContainer` already carries a user-supplied name/value map, and it is
handled end to end in exactly the shape `labels` needs:

| Layer | File | What it does today for `environment` |
|---|---|---|
| Schema | `schema/v1.0/kinds/executable/docker-container.yaml` | `environment` → `$ref: '../../utils.yaml#/components/schemas/NameValueMap'` |
| Generated Spring | `IvoaDockerContainer` | `Map<String, String> environment` with `getEnvironment`/`setEnvironment`/`putEnvironmentItem` |
| Domain interface | `DockerContainer.java` | `Map<String, String> getEnvironment()` |
| JPA entity | `DockerContainerEntity.java` | `@ElementCollection` map, copied from the validated bean in the constructor, returned by `fillBean` |
| Validator | `DockerContainerValidatorImpl.validateEnvironment(...)` | Copies entries into a new map, skipping bad key/values; does not add an empty map |
| Launch | `DockerSimpleComputeResourceEntity.makePrepareAction()` | `dockerExecutable.getEnvironment()` → `variablesList` → `withEnv(envList)` |

`NameValueMap` is a plain `additionalProperties: {type: string}` object, so the
generated Python wrapper gets a `labels` field for free — no wrapper change.

### The JPA collection pattern is already established

`DockerContainerEntity.environment` uses `@ElementCollection` +
`@MapKeyColumn` + `@CollectionTable`, which creates a
`dockerenvironmentvariables` table. `labels` will mirror this with a
`dockercontainerlabels` table, so no manual schema migration is needed.

### Version mismatch

`openapi.python.version` is `1.0.7.dev5` in
`Calycopis-broker/config.yaml` but `1.0.7.dev6` in
`Calycopis-openapi/config.yaml`. The Python client actually installed in
`build/test-venv` is `1.0.7.dev5`. This gets aligned as part of the bump.

### Build prerequisites, and one gap

* The openapi `bin/` scripts use `mvnw` (present in all three codegen projects)
  and `python` (present, with `pyyaml` for isobeon).
* `bin/buildpythonclient.sh` runs `python -m build`, and the `build` module is
  **not** installed in the developer-tools image — it needs
  `pip install build` before that step.
* `bin/buildschema.sh` writes the merged schema to
  `/tmp/execution-broker-<version>.yaml`. `/tmp` is per-container, so **all four
  build steps must run in a single container**, or the merged schema is lost
  between them.
* The openapi clone has no `bin/agent-commit`, no `agents/` directory and no
  `core.hooksPath`, unlike the broker clone.

### Agent rules for the openapi repo

The openapi clone's `.cursor/rules/` cover licence headers, copyright year and
AIMetrics, and its AGENTS.md documents the build steps but not the agent commit
flow. Per the maintainer, the **broker's** agent rules apply: GPL header on new
files, copyright year bumped on modified files, an appended `AIMetrics` entry,
and commits through `bin/agent-commit`.

The repository is **mixed licensed by design**: the OpenAPI schema is
Creative Commons (CC-BY-SA-4.0) because an upstream project requires it, and
everything else is GPL. So:

* schema files under `schema/` keep their existing CC-BY-SA-4.0 header, and only
  the copyright year is bumped;
* the ported tooling files, and any other non-schema file we add, carry the GPL
  header they already have in Calycopis-broker.

`docker-container.yaml` is an **existing** schema file, so its existing header is
kept and only the copyright year is bumped (2025 → 2026).

## Changes — Calycopis-openapi

### 0. Create the branch

Create an equivalent branch in the openapi clone before changing anything, so
the two repositories' work can be reviewed together:

```bash
cd "${TREBULA_CODE}"
git checkout -b 20261010-zrq-container-labels
```

### 1. Port the agent commit tooling

Copy from `Calycopis-broker`, unchanged except where paths differ:

| From (broker) | To (openapi) |
|---|---|
| `bin/agent-commit` | `bin/agent-commit` |
| `bin/setup-agent-git` | `bin/setup-agent-git` |
| `agents/git-identity.env` | `agents/git-identity.env` |
| `agents/hooks/commit-msg` | `agents/hooks/commit-msg` |

Then run `bin/setup-agent-git` in the openapi clone to set `core.hooksPath`
(this only changes `.git/config`, it is not committed).

### 2. Schema — `schema/v1.0/kinds/executable/docker-container.yaml`

Add a `labels` property to `DockerContainer`, immediately after `environment`:

```yaml
            labels:
              description: >-
                A name => value map of labels to apply to the container.
                Label names beginning with `calycopis-broker-` are reserved.
              $ref: '../../utils.yaml#/components/schemas/NameValueMap'
```

Bump the copyright year in the file header and append an `AIMetrics` entry.

No change is needed in `components.yaml` or `execution-broker.yaml`: the
discriminator mapping already points at this file, and `labels` is a property of
the `DockerContainer` schema rather than a new component.

### 3. Versions — `config.yaml`

```yaml
openapi:
  schema:
    path: "v1.0"
    version: "1.0.8"

  python:
    version: "1.0.8.dev0"

  spring:
    version: "1.0.8-SNAPSHOT"
```

`1.0.8.dev0` is the first development build of the new version. Bump the
copyright year and append an `AIMetrics` entry.

### 4. Regenerate and install

Run all four steps in one container so `/tmp` survives:

```bash
source bin/versions.sh config.yaml
pip install build            # once, for buildpythonclient.sh

bin/buildschema.sh           # -> /tmp/execution-broker-1.0.8.yaml
bin/buildjavaspring.sh       # installs calycopis-openapi-spring:1.0.8-SNAPSHOT
bin/buildjavaclient.sh       # installs calycopis-openapi-client:1.0.8-SNAPSHOT
bin/buildpythonclient.sh     # -> codegen/python/client/target/dist/*.whl
```

The Maven artifacts install into the same Maven home the broker build uses
(`${CALYCOPIS_SCRATCH}/m2`), so the broker picks them up.

### 5. Commits

* Commit 1: the ported agent tooling.
* Commit 2: the schema change and version bumps.

## Changes — Calycopis-broker

### 6. Versions — `config.yaml`

```yaml
openapi:
  schema:
    version: "1.0.8"

  python:
    version: "1.0.8.dev0"

  spring:
    version: "1.0.8-SNAPSHOT"

broker:
  version: "1.0.8-SNAPSHOT"
```

This resolves the `dev5`/`dev6` mismatch. Bump the copyright year and append an
`AIMetrics` entry.

### 7. Domain interface — `DockerContainer.java`

Add, next to `getEnvironment()`:

```java
    /**
     * The user defined labels to apply to the container.
     *
     */
    public Map<String, String> getLabels();
```

The only implementer is `DockerContainerEntity` and its two platform subclasses,
so this one addition covers both the mock and docker platforms.

### 8. JPA entity — `DockerContainerEntity.java`

Add a labels collection mirroring `environment`:

```java
    @ElementCollection
    @Column(name="labelvalue")
    @MapKeyColumn(name="labelkey")
    @CollectionTable(
        name="dockercontainerlabels",
        joinColumns=@JoinColumn(
            name="parent",
            referencedColumnName = "uuid"
            )
        )
    private Map<String, String> labels;
    @Override
    public Map<String, String> getLabels()
        {
        return this.labels;
        }
```

Copy it from the validated bean in the constructor, next to `environment`:

```java
        this.labels = new HashMap<String, String>();
        if (validated.getLabels() != null)
            {
            this.labels.putAll(
                validated.getLabels()
                );
            }
```

And return it from `fillBean`, next to the environment block:

```java
        if ((this.labels != null) && (this.labels.isEmpty() == false))
            {
            bean.setLabels(
                this.labels
                );
            }
```

Bump the copyright year and append an `AIMetrics` entry.

### 9. Validator — `DockerContainerValidatorImpl.java`

Call a new `validateLabels(...)` after `validateEnvironment(...)`, and implement
it on the `validateEnvironment` pattern, adding the reserved-prefix rule:

```java
    public boolean validateLabels(
        final Map<String, String> requested,
        final IvoaDockerContainer validated,
        final OfferSetRequestParserContext context
        ){
        boolean success = true ;
        if (requested != null)
            {
            Map<String, String> hashmap = new HashMap<String, String>();
            for (Map.Entry<String,String> entry : requested.entrySet())
                {
                if (entry.getKey().startsWith(DockerContainerLabels.RESERVED_PREFIX))
                    {
                    context.addWarning(
                        "urn:reserved-label",
                        "DockerContainer - label name uses the reserved prefix [{}]",
                        Map.of("value", entry.getKey())
                        );
                    success = false ;
                    }
                else if (ValidatorTools.isBadValueCheck(entry.getKey(), context))
                    {
                    // ... same shape as validateEnvironment ...
                    success = false ;
                    }
                else if (ValidatorTools.isBadValueCheck(entry.getValue(), context))
                    {
                    // ... same shape as validateEnvironment ...
                    success = false ;
                    }
                else {
                    hashmap.put(entry.getKey(), entry.getValue());
                    }
                }
            if (hashmap.isEmpty() == false)
                {
                validated.setLabels(hashmap);
                }
            }
        return success;
        }
```

A rejected label makes validation fail, so the offer-set response is `NO` with
an `urn:reserved-label` message rather than silently changing the user's value.

### 10. Label helper — `DockerContainerLabels.java`

Add the reserved prefix as a constant and a user-labels overload. User labels go
in **first**, so the internal labels always win even if validation is bypassed:

```java
    public static final String RESERVED_PREFIX = "calycopis-broker-";

    public static Map<String, String> makeLabels(
        final UUID sessionUuid,
        final UUID resourceUuid,
        final URI resourceKind,
        final String containerRole,
        final Map<String, String> userLabels
        ){
        Map<String, String> labels = new HashMap<String, String>();
        if (userLabels != null)
            {
            labels.putAll(
                userLabels
                );
            }
        // ... existing session/resource/kind/role puts ...
        return labels;
        }
```

The existing four-argument method stays, delegating with `null`, so the HTTP
data download helper is unchanged.

### 11. Launch — `DockerSimpleComputeResourceEntity.java`

In `makePrepareAction`, the executable is already resolved into
`dockerExecutable` (where `getEnvironment()` and `getCommand()` are read).
Capture the user labels there:

```java
        final Map<String, String> userLabels = new HashMap<String, String>();
        if (dockerExecutable.getLabels() != null)
            {
            userLabels.putAll(
                dockerExecutable.getLabels()
                );
            }
```

and build the launch labels with the new overload:

```java
        final Map<String, String> labels = DockerContainerLabels.makeLabels(
            sessionUuid,
            this.getUuid(),
            this.getKind(),
            DockerContainerLabels.ROLE_EXECUTION,
            userLabels
            );
```

The existing `.withLabels(labels)` on the create command then carries both sets.
The retry path already reuses the same `labels` map, so it needs no change.

### 12. Tests

* `tests/python/docker/test_docker_user_labels.py` (new, docker platform):
  * direct execution with two user labels; assert `container.labels` contains
    both, alongside the four `calycopis-broker-*` labels, and that the values
    are unchanged;
  * a request with a `calycopis-broker-session-uid` label is rejected, with
    `result == "NO"` and an `urn:reserved-label` message;
  * user labels appear in the session response's
    `executable.labels`.
* `tests/python/any/test_user_labels_roundtrip.py` (new, either platform):
  * submit an offer-set request with labels and assert they are echoed in
    `offers[0].executable.labels`. No execution, so this runs on the mock
    platform too and covers the schema/round-trip without Docker.
* Add both to the test table in `AGENTS.md`.

### 13. Commit

One broker commit: version bumps, model/validator/launch plumbing, tests and
`AGENTS.md`.

## Build and test procedure

The local stack from #130 still applies — `--volumes-from lithosia-dsh-container`
plus `MAVEN_USER_HOME=/Calycopis/agents/scratch/m2`. See
[20261010-01-agent-broker-dev.txt](../../notes/zrq/20261010-01-agent-broker-dev.txt).

The openapi and broker steps need different things mounted, so:

1. **Openapi build** — one tools container, `--volumes-from`, `pip install
   build`, then all four `bin/build*.sh` scripts. Installs the Spring jar and
   Java client into `${CALYCOPIS_SCRATCH}/m2`, and the wheel into
   `codegen/python/client/target/dist/`.
2. **Install the Python client** into the test venv, because `1.0.8.dev0` is not
   on the package index:
   `build/test-venv/bin/pip install --force-reinstall codegen/python/client/target/dist/*.whl`
3. **Broker build** — `./mvnw clean compile` with the new `1.0.8-SNAPSHOT`
   Spring jar from the local repository.
4. **Broker restart** — recreate `calycopis-broker` so it runs the new code.
5. **Tests** — the new tests, plus the existing
   `docker/test_docker_container_labels.py` to prove #130 still works.

## Verification checklist

- [x] `bin/buildschema.sh` produces `/tmp/execution-broker-1.0.8.yaml` containing
      the `labels` property.
- [x] `mvnw ... install` publishes `calycopis-openapi-spring:1.0.8-SNAPSHOT` and
      `calycopis-openapi-client:1.0.8-SNAPSHOT` into
      `${CALYCOPIS_SCRATCH}/m2/repository`.
- [x] The generated `IvoaDockerContainer` has `getLabels`/`setLabels`.
- [x] The Python wheel installs as `1.0.8.dev0` and `DockerContainer` accepts
      `labels`.
- [x] `./mvnw clean compile` succeeds in the broker (351 source files).
- [x] Broker starts against PostgreSQL and reports healthy.
- [x] The new docker test passes; the container carries both the user labels and
      the internal `calycopis-broker-*` labels.
- [ ] The round-trip test passes on the **mock** platform. (the suite ran on the
      docker platform; the `any/` tests are platform neutral, so this is worth
      confirming on a mock broker, but no mock broker was started)
- [x] `docker/test_docker_container_labels.py` still passes.
- [x] No stale `1.0.7` references where `1.0.8` is now expected (the
      Calycopis-broker AGENTS.md version notes were updated too).
- [ ] Licence headers, copyright years and `AIMetrics` entries reviewed in both
      repositories; commits made with `bin/agent-commit` after approval.

## Risks and open questions

1. **Schema version numbering.** `1.0.8` treats the added optional property as a
   patch bump. If the project prefers a minor bump (`1.1.0`) for a schema change,
   the numbers change in four places across two repositories.
2. **`1.0.8.dev0` resets the dev counter.** The alternative is to continue the
   sequence (`1.0.8.dev7`), but restarting the counter for a new version is the
   convention we agreed.
3. **Label key validation.** The plan reuses the existing bad-value blacklist and
   rejects empty keys only indirectly. Docker itself imposes further rules
   (label key charset). If we want strict client-side validation, that is a
   separate decision; today `environment` is equally permissive.
4. **Existing offer sets and sessions.** Labels are stored on the executable
   entity, and the new column/table is created by Hibernate's
   `ddl-auto` on startup. The `test-venv` and the broker database are scratch,
   so no migration is needed; a real deployment would need to confirm the
   `ddl-auto` setting.
5. **Openapi tests.** The openapi repository has no test suite, so verification
   there is the build itself plus the schema content check. The generated
   packages are exercised by the broker tests.

## Out of scope

* Applying user labels to the HTTP data download helper container — it is an
  internal container and has no user-facing executable.
* User labels on other executable types (`SingularityContainer`,
  `JupyterNotebook`) — #131 names `DockerContainer` only.
* Any change to how the internal `calycopis-broker-*` labels are produced (#130).
