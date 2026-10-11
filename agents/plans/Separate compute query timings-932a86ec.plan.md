<!-- 932a86ec-2732-4496-9e0c-c63da5ce0d57 -->
---
todos:
  - id: "branch"
    content: "Create branch 20261011-zrq-booking-query from main"
    status: completed
  - id: "query"
    content: "Rewrite the ExecutionBlocks CTE to use the compute resource availability window"
    status: completed
  - id: "tests"
    content: "Update the phase test and add timing, join and phase assertions"
    status: completed
  - id: "headers"
    content: "Append AIMetrics entries to both modified file headers"
    status: completed
  - id: "unit-tests"
    content: "Run the offline Java unit tests (16 tests, 0 failures)"
    status: completed
  - id: "sql-verify"
    content: "Verify the built SQL against PostgreSQL with a real offer fixture"
    status: completed
  - id: "plan-doc"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval"
    status: pending
isProject: false
---
# Plan: Separate SimpleComputeResourceQuery from session lifetime (issue #149)

Date: 2026-10-11
Scope: `Calycopis-broker` Java source (2 files) + this plan document + branch/commit
Issue: [uksrc/Calycopis-broker#149](https://github.com/uksrc/Calycopis-broker/issues/149) — Separate SimpleComputeResourceQuery from session lifetime

## Target

Make the booking query measure existing compute bookings from each compute
resource's **own** availability window, instead of from its parent session's
timing columns, so the query stops assuming that a resource's lifetime is the
same as its session's.

Success means:

* `ExecutionBlocks` derives `BlockStart`/`BlockLength` from
  `lifecyclecomponents.available_start_instant_seconds` /
  `available_duration_seconds` (the compute resource's own window), and no
  longer reads `simpleexecutionsessions.available_*`.
* The parent session is joined only as a liveness filter (to exclude offers
  that are `REJECTED`/`EXPIRED`), documented in an SQL comment.
* The liveness filter includes `AVAILABLE`, so an accepted session stays
  counted for its whole available window.
* Existing query-builder unit tests are updated; new tests pin the timing
  source and the lifecyclecomponents join; `mvnw test` is green.
* End-to-end: a second overlapping offer request no longer overlaps the first
  offer's available window (today it does).

## Findings (inspection at branch point, main @ de118d4e)

* `SimpleComputeResourceQuery.DATABASE_QUERY`
  (`SimpleComputeResourceQuery.java:148-314`), `ExecutionBlocks` CTE at
  lines 150-170, read `SimpleExecutionSessions.available_start_instant_seconds`
  and `SimpleExecutionSessions.available_duration_seconds`.
* Those session columns are **never assigned** anywhere:
  `SimpleExecutionSessionEntity.java:415-465` declares them, and the only
  assignments in the whole source tree are in the resource entities
  (`AbstractComputeResourceEntity.java:127-129`). They are therefore always
  `0`, so every `ExecutionBlocks` row had `BlockStart = 0`, `BlockLength = 0`
  and never satisfied the overlap test
  (`BlockStart <= StartRow AND BlockStart + BlockLength > StartRow`,
  lines 186-190). The booking query reported all 256 cores as free
  regardless of existing sessions — a latent bug that this change repairs,
  and the live development database confirms it (eight sessions all with
  `available_start_instant_seconds = 0` while their compute resources carry
  real two-hour windows).
* A compute resource **does** get a real window at construction:
  `AbstractComputeResourceEntity.java:121-135` sets
  `availableStartInstantSeconds = offer.getOfferStartInstant()`, and
  `availableDurationSeconds = offer.getOfferDuration()`.
* Those `available_*` fields are declared on `LifecycleComponentEntity`
  (table `lifecyclecomponents`, `JOINED` inheritance) and reach compute
  resources through `lifecyclecomponents` → `abstractcomputeresources` →
  `simplecomputeresources`. `simplecomputeresources` holds only the
  cores/memory columns and `abstractcomputeresources` holds `session`, so a
  new join to `lifecyclecomponents` is required. Confirmed against the live
  Hibernate schema, including the foreign key
  `abstractcomputeresources.uuid → lifecyclecomponents.uuid`.
* Liveness cannot be decided from the compute resource's phase alone. A
  component of a `REJECTED`/`EXPIRED` offer stays in `INITIALIZING` forever,
  because releasing an `INITIALIZING` component is a deliberate no-op
  (`ReleaseComponentRequestEntity.java:105` in `preProcess`, `:223` in
  `postProcess`). A live `OFFERED`/`ACCEPTED` component is also in
  `INITIALIZING`. The session phase is therefore the only discriminator.
* The old phase list omitted `AVAILABLE`, which is the phase an active
  session actually holds: `UpdateSessionRequestEntity.java:344-376` sets the
  session to `AVAILABLE` when all components are available, and no code path
  ever sets the session to `RUNNING`, so the deployed `'RUNNING'` entry
  matches nothing. Today the omission is masked by the zero-timing bug; once
  the timing fix lands it would silently un-reserve every active session.
* Consumer: `SimpleComputeResourceOfferBean.create` (`:49-79`) reads only
  `BlockStart`, `BlockLength`, `BlockCores`, `BlockMemory`. `BlockPhase` is
  unused output. No handler, factory, bean, DTO or schema change is needed.

## Decisions

1. **Timing comes from the compute resource; liveness stays on the session
   phase.** The issue asks for the start/end timings to come from
   `SimpleComputeResources`; it does not ask to drop the parent join, and a
   session-free liveness test is impossible without an entity change (see
   Findings). The join is kept and commented as a liveness filter only.
2. **Add `AVAILABLE` to the phase list.** Required for correctness once the
   timing window is real: without it the whole `AVAILABLE` period of every
   accepted session would be offered again. `RUNNING` is retained for forward
   compatibility even though nothing sets it today.
3. **`BlockPhase` becomes the compute resource's own phase**
   (`LifecycleComponents.phase`). It is unused output, and this keeps the
   projection consistent with the "concentrate on the compute resource"
   direction.
4. **No entity, schema or dependency changes.** SQL text and tests only.
5. **Alternative considered and rejected for now:** making this query fully
   session-free by moving the `REJECTED`/`EXPIRED` cleanup into the component
   lifecycle (e.g. `ReleaseComponentRequestEntity` setting dead components to
   `CANCELLED`). That is a lifecycle-semantics change, out of scope here.

## Changes

### 1. `java/src/main/java/net/ivoa/calycopis/broker/engine/functional/booking/compute/simple/SimpleComputeResourceQuery.java`

Replaced the `ExecutionBlocks` CTE with a version that selects from
`AbstractComputeResources`, joins `LifecycleComponents` for the availability
window and phase, keeps the `SimpleComputeResources` and
`SimpleExecutionSessions` joins, and filters on the live session phases:

```sql
WITH ExecutionBlocks AS
    (
    SELECT
        LifecycleComponents.phase AS BlockPhase,
        LifecycleComponents.available_start_instant_seconds  / :blockstep AS BlockStart,
        LifecycleComponents.available_duration_seconds / :blockstep AS BlockLength,
        COALESCE(SimpleComputeResources.maxofferedcores,  SimpleComputeResources.maxrequestedcores)  AS UsedCores,
        COALESCE(SimpleComputeResources.maxofferedmemory, SimpleComputeResources.maxrequestedmemory) AS UsedMemory
    FROM
        AbstractComputeResources
    JOIN
        LifecycleComponents
    ON
        LifecycleComponents.uuid = AbstractComputeResources.uuid
    JOIN
        SimpleComputeResources
    ON
        SimpleComputeResources.uuid = AbstractComputeResources.uuid
    JOIN
        SimpleExecutionSessions
    ON
        SimpleExecutionSessions.uuid = AbstractComputeResources.session
    WHERE
        SimpleExecutionSessions.phase IN ('OFFERED', 'ACCEPTED', 'WAITING', 'PREPARING', 'AVAILABLE', 'RUNNING', 'RELEASING')
    ),
```

A comment records why the session join remains. The stale javadoc TODO
("Need to change the query to concentrate on compute resource") was replaced
with a note that the window now comes from the compute resource and a link to
issue #149. Everything after `ExecutionBlocks` and all of `build()` is
unchanged; no placeholder was added or removed.

### 2. `java/src/test/java/net/ivoa/calycopis/broker/engine/functional/booking/compute/simple/SimpleComputeResourceQueryTest.java`

* Renamed `testBuildIncludesAcceptedPhase` to
  `testBuildIncludesActiveSessionPhases`, asserting the new seven-value `IN`
  list and that `'REJECTED'` and `'EXPIRED'` are absent.
* Added `testBuildUsesComputeResourceAvailabilityWindow` (the query uses
  `LifecycleComponents.available_start_instant_seconds` and
  `available_duration_seconds`, and not `SimpleExecutionSessions.available_`).
* Added `testBuildJoinsComputeResourcesToLifecycleComponents`.
* Added `testBuildUsesComputeResourcePhase`.

### 3. Plan document

This file.

## Verification

1. **Unit tests (offline):** `MAVEN_USER_HOME=${CALYCOPIS_SCRATCH}/m2 ./mvnw -o -B
   -Dmaven.repo.local=${CALYCOPIS_SCRATCH}/m2/repository test` → 16 tests,
   0 failures (12 in `SimpleComputeResourceQueryTest`, up from 9).
2. **SQL-level before/after against PostgreSQL.** Started
   `docker.io/library/postgres:18.6` in Podman, ran the modified broker
   against it with the `mock` profile (Hibernate `ddl-auto: create` built the
   real schema), and created live `OFFERED` offers through `POST /requests`
   (32 cores each, a two-hour window from 13:41).
   With two offers live (64 cores):
   * old session-timing CTE → one 120-block row at **200 cores** (256 capped
     by `maxcores`; the offers are invisible),
   * new compute-resource CTE → one 120-block row at **192 cores**
     (256 − 64).
   With eight offers live (256 cores):
   * old CTE → still **200 cores**,
   * new CTE → **no rows** (no capacity),
   * the broker answered a ninth, identical request with `result: NO` and no
     offers.
   The generated query was taken from `SimpleComputeResourceQuery.build()`
   itself, not hand-written, so the check exercised the real code path.
3. The test broker, database container and scratch helper files were removed
   after verification.

## Behaviour change to call out in review

After the fix the query stops being a no-op: existing `OFFERED`/`ACCEPTED`/
`AVAILABLE` sessions now actually consume capacity, so later overlapping
requests see fewer free cores and later start blocks (or `result: NO`). That
is the point of the issue, but it is a user-visible change to offer responses
and should be mentioned in the commit message and any pull request body.

## Assumptions and out of scope

* A compute resource's available window equals its offer window
  (`AbstractComputeResourceEntity.java:121-135`), which is what makes this
  change equivalent for compute while decoupling it from the session.
* The pre-existing uncommitted edit to
  `notes/zrq/20261008-02-booking-query.txt` is the user's own working-tree
  change and is not part of this change set.
* Out of scope: zero-delay start times and the `rangestart = 1` behaviour
  (issue #30), `ResourceAllocation` entities (step 4 of the booking-query
  notes), the never-populated session `available_*` columns and the session
  never entering `RUNNING`, and any change to the `DEFAULT_*`/`BLOCK_*`
  constants.
* No API, OpenAPI schema, serialization or database-migration changes; the
  query is built in Java and the schema is generated by Hibernate.
