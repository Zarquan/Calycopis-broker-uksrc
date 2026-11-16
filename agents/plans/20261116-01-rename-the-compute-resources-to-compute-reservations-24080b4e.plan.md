<!-- 24080b4e-3a15-4ae6-b479-1d3899af8aa1 -->
---
todos:
  - id: "branch"
    content: "Create the branch from main"
    status: completed
  - id: "files"
    content: "Rename the twenty-six files in the entity tiers and the Spring repository with git mv"
    status: completed
  - id: "references"
    content: "Rewrite the type references, accessors and fields across the dependent files"
    status: completed
  - id: "tables"
    content: "Rename the four JPA tables and the two table identifiers in the booking query"
    status: completed
  - id: "state-sql"
    content: "Update the table names in tests/python/states/conftest.py"
    status: completed
  - id: "prose"
    content: "Update the validator messages, the javadoc that names the type, AGENTS.md and docs"
    status: completed
  - id: "build"
    content: "Compile and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the any and mock Python suites against a local broker"
    status: completed
  - id: "grep"
    content: "Confirm no old type name remains outside the booking classes"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Rename the compute resources to compute reservations

Date: 2026-11-16
Scope: `Calycopis-broker` — the compute entity tiers, the accessors that expose
them, four JPA tables, the table identifiers in the booking query, the state
test SQL, the validator messages and the data model in `AGENTS.md`. The booking
classes keep their names.
Issue: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) —
Compute resource pools, stage 1 of seven.
Parent: [20261114-03-compute-resource-pools-and-allocations](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md).
Branch: `20261116-zrq-compute-reservations`, to be created from `main` at
`7b5b6eea`.
Result: not started.

## Target

Rename the per-session compute component from a *compute resource* to a
*compute reservation*, in the classes, the accessors, the tables and the query.
Nothing else changes: the same requests produce the same responses, the kind
URIs, bean types and HTTP paths are untouched, and the same rows come back from
the same columns under new table names.

This is stage 1 because every later stage names this type. Pools carry
reservations, allocations bound them, and the pool-scoped query in stage 5
counts them. Renaming after those stages exist would mean editing all of that
new code, and the rename would then have a moving target.

It also settles the vocabulary before the new words arrive. A pool is a pool of
**compute resources**; a reservation is one booking of them; an allocation is
capacity granted to a pool. The parent plan's decision 1 records the rule: the
word "resource" is dropped where the type records a booking and kept where it
names the thing being pooled or requested.

## Where we are

The compute component is a three-tier entity, with a mock and a docker
implementation of each tier:

| Tier | Files | Shape |
|---|---|---|
| Abstract | 8 | `AbstractComputeResource`, its entity, two factory types and four validator types |
| Simple | 6 | `SimpleComputeResource`, its entity, two factory types, two validator types |
| Mock | 6 | `MockSimpleComputeResource` and the same five files |
| Docker | 6 | `DockerSimpleComputeResource` and the same five files |

Twenty-six files under `java/src/main/java/net/ivoa/calycopis/broker/engine/entities/compute/`,
plus `spring/jpa/SpringComputeResourceEntityRepository.java`.

Sixty-nine files under `java/src` mention the old names, 526 occurrences. Of
those, 43 are outside the entity package: the session, the offer set parser and
context, the storage entity, the ten volume mount classes, the seven session
processing requests, the platform interface and its two implementations, and
the booking package. They compile against the types, so they change with them,
which is why the rename cannot be done piecemeal.

Four tables carry the names, declared in `@Table` annotations:

| Entity | Table |
|---|---|
| `AbstractComputeResourceEntity` | `abstractcomputeresources` |
| `SimpleComputeResourceEntity` | `simplecomputeresources` |
| `MockSimpleComputeResourceEntity` | `mocksimplecomputeresources` |
| `DockerSimpleComputeResourceEntity` | `dockersimplecomputeresources` |

The booking query joins the first two by name inside a raw SQL string, and the
state tests query the first two directly from Python.

## The rename

Twenty-seven type names, applied longest first so that a shorter name never
eats a longer one:

| Old | New |
|---|---|
| `AbstractComputeResourceEntityFactoryImpl` | `AbstractComputeReservationEntityFactoryImpl` |
| `AbstractComputeResourceValidatorFactoryImpl` | `AbstractComputeReservationValidatorFactoryImpl` |
| `DockerSimpleComputeResourceEntityFactoryImpl` | `DockerSimpleComputeReservationEntityFactoryImpl` |
| `DockerSimpleComputeResourceValidatorImpl` | `DockerSimpleComputeReservationValidatorImpl` |
| `MockSimpleComputeResourceEntityFactoryImpl` | `MockSimpleComputeReservationEntityFactoryImpl` |
| `MockSimpleComputeResourceValidatorImpl` | `MockSimpleComputeReservationValidatorImpl` |
| `SimpleComputeResourceEntityFactoryImpl` | `SimpleComputeReservationEntityFactoryImpl` |
| `SimpleComputeResourceValidatorImpl` | `SimpleComputeReservationValidatorImpl` |
| `AbstractComputeResourceEntityFactory` | `AbstractComputeReservationEntityFactory` |
| `AbstractComputeResourceValidatorFactory` | `AbstractComputeReservationValidatorFactory` |
| `DockerSimpleComputeResourceEntityFactory` | `DockerSimpleComputeReservationEntityFactory` |
| `DockerSimpleComputeResourceValidator` | `DockerSimpleComputeReservationValidator` |
| `MockSimpleComputeResourceEntityFactory` | `MockSimpleComputeReservationEntityFactory` |
| `MockSimpleComputeResourceValidator` | `MockSimpleComputeReservationValidator` |
| `SpringComputeResourceEntityRepository` | `SpringComputeReservationEntityRepository` |
| `AbstractComputeResourceValidatorImpl` | `AbstractComputeReservationValidatorImpl` |
| `SimpleComputeResourceEntityFactory` | `SimpleComputeReservationEntityFactory` |
| `SimpleComputeResourceValidator` | `SimpleComputeReservationValidator` |
| `AbstractComputeResourceEntity` | `AbstractComputeReservationEntity` |
| `AbstractComputeResourceValidator` | `AbstractComputeReservationValidator` |
| `DockerSimpleComputeResourceEntity` | `DockerSimpleComputeReservationEntity` |
| `MockSimpleComputeResourceEntity` | `MockSimpleComputeReservationEntity` |
| `SimpleComputeResourceEntity` | `SimpleComputeReservationEntity` |
| `AbstractComputeResource` | `AbstractComputeReservation` |
| `DockerSimpleComputeResource` | `DockerSimpleComputeReservation` |
| `MockSimpleComputeResource` | `MockSimpleComputeReservation` |
| `SimpleComputeResource` | `SimpleComputeReservation` |

Seven accessors and fields follow the type, because their name *is* the type:

| Old | New |
|---|---|
| `getComputeResource` | `getComputeReservation` |
| `setComputeResource` | `setComputeReservation` |
| `computeResourceEntity` | `computeReservationEntity` |
| `abstractComputeResourceValidatorFactory` | `abstractComputeReservationValidatorFactory` |
| `dockerSimpleComputeResourceEntityFactory` | `dockerSimpleComputeReservationEntityFactory` |
| `mockSimpleComputeResourceEntityFactory` | `mockSimpleComputeReservationEntityFactory` |
| `springAbstractComputeResourceEntityRepository` | `springAbstractComputeReservationEntityRepository` |

Four tables, and the two identifiers the booking query uses for the first two:

| Old | New |
|---|---|
| `abstractcomputeresources` | `abstractcomputereservations` |
| `simplecomputeresources` | `simplecomputereservations` |
| `mocksimplecomputeresources` | `mocksimplecomputereservations` |
| `dockersimplecomputeresources` | `dockersimplecomputereservations` |
| `AbstractComputeResources` (SQL) | `AbstractComputeReservations` |
| `SimpleComputeResources` (SQL) | `SimpleComputeReservations` |

## What does not change

* The bean types `IvoaAbstractComputeResource` and `IvoaSimpleComputeResource`,
  the kind discriminators, `KIND_DISCRIMINATOR`, `WEBAPP_PATH`, the HTTP paths
  and every request and response field.
* The booking classes. `SimpleComputeResourceQuery` and its handler,
  `SimpleComputeResourceOffer`, `SimpleComputeResourceOfferBean` and
  `SimpleComputeResourceOfferFactory`, their Spring implementations, the two
  booking tests, and the platform's `getComputeResourceValidators` and
  `getComputeResourceOfferFactory`. They name the booking grid and the offer,
  not the row. Stage 5 renames them while it rewrites the query, so the churn
  lands once. `SimpleComputeResourcesOffer`, a plural typo in the offer
  interface's javadoc, stays as it is until then.
* `calycopis.broker.booking.capacity` and the rest of the configuration.
* The storage and volume types, which keep their own names until their own
  plan.

## Method

A scripted rename over an explicit identifier list, matched whole. That is the
safety property the plan depends on: `SimpleComputeResource` is a prefix of
`SimpleComputeResourceQuery`, of the offer names and of the generated
`IvoaSimpleComputeResource` bean, so a replacement that matches substrings
corrupts files this plan keeps out of scope. Matched as a whole word it does
not: there is no word boundary before `Simple` in `IvoaSimpleComputeResource`,
nor after `Resource` in `SimpleComputeResourceEntity`, and
`getComputeResourceValidators` is a different word from `getComputeResource`.
Ordering the list longest first is belt and braces.

Lower-case variables are left alone, and that is deliberate. The 54 occurrences
of `computeResource` include parameters and locals that hold the parsed
`IvoaAbstractComputeResource` bean, not the entity, and one `mappedBy =
"computeResource"` in the volume mount entity that must agree with a field name.
Renaming the word wholesale would rename the bean variables and risk the join.
The entity-typed locals keep their name until a later stage has a reason to
touch them.

1. Rename the files with `git mv`, so the history follows them.
2. Apply the type table to every file the compiler needs, then compile. The
   compiler is the oracle for the reference list, not a grep.
3. Apply the accessor and field table by hand, reviewing each hit, because the
   same names appear on the session interface, its entity, the volume mounts and
   the processing requests.
4. Rename the tables in the four `@Table` annotations, and the identifiers in
   `SimpleComputeResourceQuery.DATABASE_QUERY`. This one has no compiler
   checking: a mismatch is a runtime SQL error, caught only when the query runs.
5. Update the three table mentions in `tests/python/states/conftest.py`.
6. Update the validator messages that begin `SimpleComputeResource -`, the
   javadoc that names the type, and the data model in `AGENTS.md`. One plural
   survives the matching: the factory's comment says "A Factory for
   AbstractComputeResourceValidators", and needs rewriting by hand.
7. Compile, run the tests, and grep for the old names.

Keep the change in one commit. A half-renamed tree does not compile, so there
is no useful intermediate state to review or bisect.

## Verification

```bash
pushd java
    ./mvnw -q clean compile
    ./mvnw -q test
popd
```

```bash
pushd tests/python
    pytest -q any
    pytest -q mock
popd
```

The Python suites need a broker and a database. `ddl-auto: create` rebuilds the
schema at startup, so the renamed tables appear on the next start and no
migration is involved.

```bash
grep -rn "AbstractComputeResource" java/src            # no hits
grep -rn "SimpleComputeResource" java/src              # booking classes only
grep -rn "computeresources" java/src tests            # no hits
```

## Acceptance

* `./mvnw clean compile` is green and `./mvnw test` passes, with the Java tests
  changed only where a name changed.
* The `any` and `mock` Python suites pass, and `docker` if the platform is
  available on the machine. A validated request returns the same session shape,
  the same kind and the same numbers as before.
* The greps above return what is listed and nothing else, and no old table name
  appears anywhere.
* No OpenAPI, response, path or configuration change. `git diff` over
  `src/main/resources` is empty.
* One commit. `git diff --stat` touches the 27 renamed files and the files that
  reference them, and nothing else.

## Risks

* **The raw SQL.** The query is a string, so a missed table identifier fails at
  runtime rather than at compile time, and only once a request arrives. The
  state tests and the `any` suite are what catch it; do not skip them because
  the Java tests are green.
* **The prefix collision.** `SimpleComputeResource` appears inside names the
  plan keeps. Whole-word matching is what stops a substitution reaching them; a
  quick `sed s/Resource/Reservation/`, or a regex without the boundaries, would
  break the booking package and the `Ivoa` beans in one pass, and would look
  plausible in the diff.
* **The generics.** The abstract factory and validator types carry the entity
  type as a parameter, and the repositories are typed on the entity. The
  compiler finds these, but the diff in those files is worth reading rather
  than skimming, because a narrowed type can still compile.
* **The javadoc.** Prose that says "compute resource" stays where it means the
  API concept and changes where it names the class. That is a judgement call per
  sentence, so read the diff of `Platform`, the volume mounts and the session
  interface.

## Out of scope

The booking classes, which stage 5 renames. The storage and volume vocabulary.
The pool and allocation types, which do not exist yet. The API vocabulary,
which keeps the word "resource" because the schema does. The
`calycopis.broker.booking.capacity` keys.

## Status

status: implemented
details: Implemented in commit [`de577717`](https://github.com/uksrc/Calycopis-broker/commit/de577717) and opened as
  [PR #209](https://github.com/uksrc/Calycopis-broker/pull/209). All ten plan items
  are complete. Verified with `./mvnw clean compile`, `./mvnw test` (52 tests) and
  all four Python suites against brokers rebuilt from this branch: `any` and
  `mock` on a `mock` broker, 102 passed and 1 failed; `states` and `docker` on a
  `docker` broker, 58 passed and 3 skipped. The one failure,
  `mock/test_mock_validators.py::TestJupyterNotebookValidation::test_valid_notebook_location`,
  is a null `getExecutableResult()` at `OfferSetRequestParserImpl:517` when a
  request has a notebook and no compute, and it fails the same way on `main`. The
  three skips need a broker configured with capacity 1 and the testing delays,
  so they are a configuration gap and not a rename gap.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-16T05:12:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-16T05:14:00/2026-11-16T05:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-16T06:35:00/2026-11-16T06:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 1,
      "units": "%"
      }
    }
  ]
```
