<!-- df0fd372-e89e-4d3c-9388-6c5001286a18 -->
---
todos:
  - id: "branch"
    content: "Create branch 20261013-zrq-zero-delay-blocks from main"
    status: completed
  - id: "query"
    content: "Set rangestart to 0, add the compensation block, round the availability window out, and add getRangeStartInstant"
    status: completed
  - id: "offers"
    content: "Add SimpleComputeResourceOfferBean.clamp and apply it in the offer factory"
    status: completed
  - id: "unit-tests"
    content: "Update SimpleComputeResourceQueryTest and add SimpleComputeResourceOfferBeanTest"
    status: completed
  - id: "python-test"
    content: "Add tests/python/any/test_zero_delay_start.py"
    status: completed
  - id: "unit-run"
    content: "Run the offline Java unit tests (23 tests, 0 failures)"
    status: completed
  - id: "sql-verify"
    content: "Verify the built SQL against PostgreSQL with a clamped window fixture"
    status: completed
  - id: "integration"
    content: "Run the Python integration tests against a live broker in the Podman pod (2 new tests, 56 docker tests, 33 any tests)"
    status: completed
  - id: "baseline-ab"
    content: "Reproduce the eight storage failures on the parent commit to confirm they are pre-existing"
    status: completed
  - id: "plan-doc"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval"
    status: pending
isProject: false
---
# Plan: Zero delay starting blocks (issue #30)

Date: 2026-10-13
Scope: `Calycopis-broker` Java source (3 files), Java unit tests (1 updated, 1 new), Python integration test (1 new), this plan document
Issue: [uksrc/Calycopis-broker#30](https://github.com/uksrc/Calycopis-broker/issues/30) — Zero delay starting blocks
Parent: [uksrc/Calycopis-broker#71](https://github.com/uksrc/Calycopis-broker/issues/71) — Resource booking service

## Target

Remove the block rounding delay from offer start times, so a request for an
immediate start is offered the block that contains the requested start, moved
forward to the requested start time, while the offered window stays inside the
free run the query verified.

Success means:

* The booking query searches blocks from `:rangestart = 0` (the block that
  contains the requested start), not `1`.
* The minimum free run is one block longer, so an allocation whose start is
  moved forward still covers the full requested duration.
* An offer start before the requested start is moved forward to it, and the
  end stays at the block boundary the query verified.
* Existing (now non-block-aligned) availability windows are reserved
  conservatively, so the tail block of a clamped offer is not re-offered.
* Java unit tests are green; the built SQL is checked against PostgreSQL.
* No API, OpenAPI schema or database schema change.

## Findings (inspection at branch point, main @ c5c3afd6)

* `SimpleComputeResourceQuery.DATABASE_QUERY` builds `AvailableBlocks` from
  `generate_series(:rangestart, :rangeend) + :rangeoffset`
  (`SimpleComputeResourceQuery.java:203-208`). `:rangeoffset` is the block
  index of `requestStart.getStart()` (`:412-414`), so the search started one
  block *after* the requested start because `:rangestart` was substituted as
  `1` (`:415`). `:rangeend` is `BLOCK_RANGE_SECONDS / BLOCK_STEP_SECONDS` =
  `7200/60 = 120` (`:73`, `:416-418`), i.e. rows `1..120` before the change.
* `ExecutionBlocks` (`:161-195`) derived `BlockStart = available_start_instant_seconds / :blockstep`
  and `BlockLength = available_duration_seconds / :blockstep`. Both are integer
  divisions, so they floor. That was exact while every window was block
  aligned: `SimpleComputeResourceOfferBean.create` built the window from
  `BlockStart * BLOCK_STEP_SECONDS` and `BlockLength * BLOCK_STEP_SECONDS`
  (`SimpleComputeResourceOfferBean.java:49-79`).
* The offered window is stored unchanged on the compute resource:
  `prepareStartInstantSeconds = offerStart - prepareDuration`,
  `availableStartInstantSeconds = offerStart`,
  `availableDurationSeconds = offerDuration`,
  `releaseStartInstantSeconds = availableStart + availableDuration + 5`
  (`AbstractComputeResourceEntity.java:121-135`). There is no separate offer
  entity, so the offer bean's interval *is* the stored window.
* `requestStart` is `context.getStartInterval()`
  (`OfferSetRequestParserImpl.java:407-414`), always set in production: a
  client interval that starts before `earliestStartTime = now + totalPrepareTime`
  (`:248-250`) is warned about and discarded, and the default
  `[earliestStartTime, +2h]` is used (`:333-343`). `build()` only reads
  `getStart()`, plus an `Instant.MAX` end check (`SimpleComputeResourceQuery.java:354-370`).
* `totalPrepareTime` is not always zero: the compute validators return
  `DEFAULT_PREPARE_TIME = 1L` and the docker executable returns
  `CACHED_IMAGE_PREPARE_TIME = 1L` when the image is cached
  (`MockSimpleComputeResourceValidatorImpl.java:215-220`,
  `DockerDockerContainerValidatorImpl.java:232-238`). The earliest allowed
  start is therefore `now + prepare`, not `now`.
* Offers are consumed once, in `OfferSetRequestParserImpl.stageTwo`, which
  builds one execution session per offer and passes the offer to the compute
  entity (`:417-450`). `SimpleComputeResourceOfferFactoryImpl.generate` runs
  the query and returned the handler's list unchanged (`:93-109`). The
  `RowMapper` is a single instance created at construction
  (`SpringSimpleComputeResourceOfferQueryHandlerImpl.java:52-70`), so it
  cannot carry per-query state such as the resolved start instant.
* `AbstractResourceOffer.getName()` is `@Deprecated`
  (`AbstractResourceOffer.java:41`), but is already called by
  `SimpleExecutionSessionEntity.java:182` to name the session, so preserving
  it in a clamped copy is consistent with existing code.
* CI runs only `pytest -v docker` (`tests/python/bin/run-tests.sh:272`), so
  `tests/python/any/` is run manually.

## Decisions

1. **Clamp against the resolved range start, not `Instant.now()`.** The offer
   start becomes `max(blockStart, rangeStart)` where
   `rangeStart = requestStart.getStart()` (the earliest allowed start, i.e.
   `now + prepare`). This removes the rounding delay *and* avoids advertising
   availability before preparation can finish; clamping to `now` would
   advertise up to `totalPrepareTime` seconds early. For an immediate request
   with the default schedule the two coincide, so the issue's intent ("move it
   to now") is met. It also makes the clamp depend on the request rather than
   on processing time, which keeps the unit tests deterministic.
2. **Keep the end at the verified block boundary and shorten the duration by
   the offset.** `end = blockStart + BlockLength * blockStep` is exactly the
   free run the query proved. Setting `end = clampedStart + BlockLength * blockStep`
   instead would extend the allocation past the verified run, which would be
   unsafe. `start + duration = end` still holds.
3. **Compensate with one extra block on the minimum run length only.** The
   clamp offset is always strictly less than one block, so
   `minBlockLength = ceil(requestDuration / step) + 1` is enough for the
   shortened offer to cover the requested duration. `maxBlockLength` stays
   `ceil(maxduration / step)` so `MAXIMUM_DURATION` and
   `OFFER_DURATION_SCALE` keep their meaning, and is then floored at
   `minBlockLength` because the pre-existing sub-minute case would otherwise
   have `min > max` and return no offers at all.
4. **Round partial blocks out when reserving an unaligned window.** Once the
   clamp lands, stored windows are no longer block aligned, so the integer
   divisions in `ExecutionBlocks` would under-reserve the tail block of every
   clamped offer and a later request could re-offer it. `BlockLength` becomes
   `ceil(end/blockStep) - floor(start/blockStep)`. This is a no-op for the
   existing block-aligned rows, so no data migration is needed.
5. **Post-process in `SimpleComputeResourceOfferFactoryImpl.generate`, not in
   the result-set mapper.** The mapper instance is shared and has no per-query
   state, and `generate` is the only place that holds both the query result
   and `requestStart`. A static helper on the offer bean keeps the
   transformation testable without a `ResultSet` fake, and returning the bean
   type keeps the tests and the factory simple.
6. **Do not change `BLOCK_STEP_SECONDS` (60s) or `BLOCK_RANGE_SECONDS` (2h).**
   The issue only asks to *enable* larger blocks. Making the step and range
   configurable is the separate follow-up in the booking-query notes (shorter
   window, longer steps; [issue #151](https://github.com/uksrc/Calycopis-broker/issues/151)).
   Note that 121 rows (`0..120`) minus the added block keeps the maximum
   supported request duration at exactly 2h, the same as before (`120` rows,
   `1..120`).
7. **Add one Python integration test in `tests/python/any/`**, which is the
   correct classification per the AGENTS.md test table, knowing CI does not
   run `any/`. Extending the CI invocation is a separate change; this plan
   does not touch `tests/python/bin/run-tests.sh`, which carries the user's
   uncommitted edits.

## Changes

### 1. `java/src/main/java/net/ivoa/calycopis/broker/engine/functional/booking/compute/simple/SimpleComputeResourceQuery.java`

* Added `public static Instant getRangeStartInstant(final Interval requestStart)`,
  returning `Instant.now()` for `null` and `requestStart.getStart()` otherwise.
  This is the same default as `build()`, and the instant that a late offer
  start is moved forward to.
* `build()` resolves `rangeStart` once, uses it for the default start interval
  and for `:rangeoffset`, and substitutes `:rangestart` as `0`, with a comment
  explaining that the search must include the block that contains the
  requested start.
* Block length now adds one compensation block to the minimum and floors the
  maximum at the minimum (see Decision 3).
* `ExecutionBlocks` now rounds the end of an availability window up to the
  next block boundary and subtracts the rounded down start, with an SQL
  comment explaining why.

### 2. `.../SimpleComputeResourceOfferBean.java`

* Added `public static SimpleComputeResourceOfferBean clamp(SimpleComputeResourceOffer, Instant)`,
  which moves a start time that is before the earliest allowed start time
  forward to it, keeps the end where the query placed it, shortens the
  duration by the amount the start moved, preserves the name and the
  cores/memory values, and logs the move at debug. A start that is not before
  the earliest start, and a block that has already ended, are returned
  unchanged.
* `create(ResultSet, int)` is unchanged, including its
  `IllegalArgumentException` to `SQLException` wrapper.

### 3. `.../SimpleComputeResourceOfferFactoryImpl.java`

* `generate(...)` resolves the range start, keeps the existing `build()` call
  and profiling window, maps the query result into a `List` of clamped offers,
  and returns that list.

### 4. Java tests

* `SimpleComputeResourceQueryTest.java`: the 30s case now expects
  `COUNT(*) >= 2` and `generate_series(1, 2)` (renamed
  `testBuildSubMinuteDurationIncludesCompensationBlock`), the 61s case expects
  `COUNT(*) >= 3`, the defaults case expects `COUNT(*) >= 121`, and the open
  ended case expects `generate_series(0, 120)`. Added
  `testBuildIncludesCurrentBlock`, `testBuildRoundsAvailabilityWindowOut` and
  `testGetRangeStartInstant`.
* `SimpleComputeResourceOfferBeanTest.java` (new): the start moves forward and
  the duration shortens, the end does not move, the name is preserved, a start
  that is not before the earliest start and a start exactly at it are
  unchanged, the worst case clamp still covers a five minute request, and an
  expired block is left alone.

### 5. `tests/python/any/test_zero_delay_start.py` (new)

* `test_requested_start_time_is_offered`: a requested start one second into the
  next block must be offered within `[-1s, +5s]` of that time, with a duration
  of at least the requested 300s. Fails before the change, where the offer
  lands 59s later.
* `test_immediate_request_starts_now`: a request with no start time must be
  offered within `[0s, +5s]` of the request time, with a duration of at least
  the requested 300s.
* Both skip with a message rather than fail if the broker returns no offers
  (capacity exhausted by an earlier test in the same run).

### 6. Plan document

This file.

## Verification

1. **Unit tests (offline):** `MAVEN_USER_HOME=${CALYCOPIS_SCRATCH}/m2 ./mvnw -o -B
   -Dmaven.repo.local=${CALYCOPIS_SCRATCH}/m2/repository test` → 23 tests,
   0 failures (15 in `SimpleComputeResourceQueryTest`, up from 12; 4 new in
   `SimpleComputeResourceOfferBeanTest`; 4 in `OfferSetEntityProfilingTest`).
   The main tree compiled with only the pre-existing deprecation warnings.
2. **SQL level against PostgreSQL 18.6.** The published port of a standalone
   database container is not reachable from this network namespace, so before
   the live run in item 3 the SQL was checked directly. The query text was
   generated by calling `SimpleComputeResourceQuery.build()`
   from the compiled classes (not hand written), and run in PostgreSQL against
   a hand built four-table schema with one live `OFFERED` offer whose window is
   the clamped form: start `2026-01-01T00:00:58Z` (mid block) for 302s, so the
   end is the block boundary `2026-01-01T00:06:00Z`. The "old" variant was the
   same generated text with `rangestart` back at `1` and the previous
   `BlockLength` expression.
   * `ExecutionBlocks` with the clamped window: new `BlockLength = 6`, covering
     blocks `29453760..29453765` (the full window); old `BlockLength = 5`,
     covering `29453760..29453764` and leaving the tail block
     `29453765` unreserved even though the allocation runs to it. This is the
     new double-booking hole that Decision 4 closes.
   * First offered block with no live allocations, for a requested start at
     `00:00:58` (block `29453760`): new query `BlockStart = 29453760` (the
     current block, which the clamp moves to the requested start), old query
     `BlockStart = 29453761`. This is the delay the issue describes.
   * The test container was removed afterwards. The scratch helper files (the
     query generator and the four SQL files) are left in
     `$CALYCOPIS_SCRATCH/zero-delay-e2e/` so the check can be repeated.
3. **Integration tests (live broker, PostgreSQL and the Python client, in the
   Podman pod).** The broker could not be packaged as a container image from
   this container (`spring-boot:build-image` binds the Docker socket into the
   builder at the path it uses in this container's namespace, which the Podman
   service cannot resolve), so the broker runs from source in a
   `developer-tools` container joined to the pod, with the source, the Maven
   repository and the socket mounted at paths translated into the Podman
   service's namespace. The helper is
   `$CALYCOPIS_SCRATCH/zero-delay-e2e/run-local.sh`.
   * `tests/python/any/test_zero_delay_start.py` → 2 passed. The requested mid
     block start is offered at exactly that time, and an immediate request is
     offered the current block, both with at least the requested duration.
   * The CI docker suite (`pytest -v docker`) → 56 passed in 469s, including
     the timing sensitive `test_session_completes_with_timed_container`, the
     short and long container test, the concurrent stress test and the session
     connector tests.
   * The full `any` suite (`pytest -v any`) → 33 passed, 8 failed. The eight
     failures are the pre-existing storage `ClassCastException`
     (`Validator$ResultBean` cannot be cast to
     `AbstractStorageResourceValidator$Result`, raised in
     `OfferSetRequestParserImpl.stageOne` before any offer is generated),
     recorded in the earlier bug report draft
     `agents/scratch/BUG-docker-storage-validation-classcastexception.md`. An
     A/B run of the same file against the parent commit `c5c3afd6` in a
     separate worktree produced exactly the same eight failures and three
     passes, so they are not caused by this change.
   * The pod, the database container and the baseline worktree were removed
     afterwards. The scratch helper files are left in
     `$CALYCOPIS_SCRATCH/zero-delay-e2e/` so the runs can be repeated.

## Behaviour change to call out in review

Offers that start at the requested time are now stored with an availability
window that does not start on a block boundary, and the *current* block is now
bookable, so back-to-back immediate requests contend for the current block
where before they contended for the next one. The offered duration of such an
offer is shorter than a whole number of blocks (`end - start`), but the one
block compensation keeps it at or above the requested duration. That is the
point of the issue, but it is a user-visible change to offer responses and
should be mentioned in the commit message and any pull request body.

## Assumptions and out of scope

* The requested start interval's `start` is the earliest time the session may
  become available; only its start is used for the range offset and the clamp
  (already true for the offset before this change).
* A stale offer row (`earliestStart >= blockEnd`, i.e. more than a block of
  delay between the query and offer creation) is left unchanged rather than
  dropped, matching the pre-existing untransactional behaviour. It is
  documented in the clamp's javadoc.
* `BLOCK_STEP_SECONDS`/`BLOCK_RANGE_SECONDS` stay at 60s/2h.
  `ResourceAllocation` entities ([issue #32](https://github.com/uksrc/Calycopis-broker/issues/32)),
  the never-populated session `available_*` columns, and the offer generation
  performance work ([issue #151](https://github.com/uksrc/Calycopis-broker/issues/151))
  are separate.
* The pre-existing uncommitted edits to
  `notes/zrq/20261005-02-next-steps.txt`, `notes/zrq/20261008-02-booking-query.txt`
  and `tests/python/bin/run-tests.sh` are the user's own working-tree changes
  and are not part of this change set.
