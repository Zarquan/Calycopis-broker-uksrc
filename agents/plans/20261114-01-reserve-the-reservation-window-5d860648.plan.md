<!-- 5d860648-000d-4f92-a2ce-45e80ddcc455 -->
---
todos:
  - id: "query-hull"
    content: "Require the new offer's whole reservation in the free-run search, inset the available window, and extend the search range"
    status: completed
  - id: "wiring"
    content: "Pass the compute preparation and the fixed release tail into the booking query from the offer factory"
    status: completed
  - id: "constants"
    content: "Name the release gap and duration constants in AbstractComputeResourceEntity and use them for the stored windows"
    status: completed
  - id: "unit-tests"
    content: "Update SimpleComputeResourceQueryTest, including the zero-span equivalence and the range extension"
    status: completed
  - id: "python-test"
    content: "Add tests/python/states/test_reservation_window.py for a capacity-1 broker"
    status: completed
  - id: "unit-run"
    content: "Run the Java unit tests"
    status: completed
  - id: "integration"
    content: "Run the any suite and the docker platform suite against local brokers"
    status: completed
  - id: "capacity-check"
    content: "Demonstrate the new test fails on the pre-fix query and passes with the fix, and measure the capacity effect"
    status: completed
  - id: "docs"
    content: "Document the whole-reservation rule, the capacity effect and the new test"
    status: completed
  - id: "follow-up"
    content: "Create the follow-up issue for the hard-coded release duration (#201)"
    status: completed
  - id: "plan-doc"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Reserve the new offer's whole reservation window (issue #158, residual)

Date: 2026-11-14
Scope: `Calycopis-broker` booking query, offer factory and parser, compute
entity constants, Java unit tests, one Python state test, documentation.
Issue: [uksrc/Calycopis-broker#158](https://github.com/uksrc/Calycopis-broker/issues/158) —
Expand the reservation window to cover preparing, available and releasing.
Parent: [uksrc/Calycopis-broker#184](https://github.com/uksrc/Calycopis-broker/pull/184) —
the session side of the hull.
Branch: `20261114-zrq-reservation-window`
Result: commit `941e9504`, [PR #202](https://github.com/uksrc/Calycopis-broker/pull/202),
CI `build-java-broker` passed.

## Target

Close the offering half of #158. The booking query already measures the
occupancy of the existing sessions over their whole reservation (the hull of
their preparing, available and releasing windows, block rounded). The offering
side still looked for free space for the new offer's **available** window only,
so a new offer could prepare in the block where the previous session was
releasing, and two sessions could hold the same block on a one-core platform.

Success means:

* The free run has to cover the new offer's preparation, available window and
  release tail, not just its available window.
* The offered available window is inset by the blocks the new offer holds before
  and after it, so a later offer cannot prepare or release inside it.
* The search still offers the current block for an immediate request, preserving
  the behaviour from #154.
* The query and the stored windows use one definition of the release tail.
* The zero-span builder still produces the pre-change available-window query.
* The Java unit tests are green, and a capacity-1 integration test pins the
  clearance between two sessions.
* No API, OpenAPI schema or database schema change.

## Findings (inspection at branch point, main @ d8c88403)

* `SimpleComputeResourceQuery.DATABASE_QUERY` reads the existing sessions
  through `AvailableBlocks` and `ExecutionBlocks`, which project the hull of
  `prepare_start_instant_seconds`, `available_start_instant_seconds` and
  `release_start_instant_seconds`, rounded out to whole blocks. That was added
  by #184 and is correct.
* The split of a free run still used `:maxblocklength` as its pitch and took the
  full remaining run length as the offered availability window. It never
  reserved the new offer's own preparation or release, so a candidate could
  start in a block that the candidate's own preparation or release would touch
  inside a neighbouring session's reservation.
* `AbstractComputeResourceEntity` stores a 1 s preparation (from the validator
  result) and a hard-coded 10 s release starting 5 s after availability, both as
  literal values in the constructor. The compute validators report their own
  release duration (1 s for both the mock and the docker validators), which the
  entity ignores.
* When the query had to extend backwards for #154 it used `:rangestart = 0`.
  With the new hull the preparation has to be included before that block, so
  the range start must move back by the new offer's preparation.

## Decisions

1. **Reserve the whole hull in the query.** The free run must be at least
   `minBlockLength + insetBlocks` long, where `insetBlocks` is the rounded-up
   preparation plus the rounded-up release tail. The offered window is
   `min(maxBlockLength, runLength - pitch * n - insetBlocks)` long and starts
   `pitch * n + reserveBeforeBlocks` after the run start, with
   `pitch = maxBlockLength + insetBlocks`, so consecutive candidates do not
   overlap each other's reservation.
2. **Compute preparation only, not the session total.** The preparation passed
   to the query is the maximum `getPrepareDuration()` across the compute
   validator results, because compute resources prepare in parallel and the
   compute component's own hull uses that value. The executable, data and
   storage preparation do not hold cores, so they are not part of the booking
   hold. This is the same value the compute entity stores (the maximum is an
   upper bound for each resource).
3. **Share the release definition.** `RELEASE_START_GAP_SECONDS = 5` and
   `RELEASE_DURATION_SECONDS = 10` move from the constructor literals into named
   constants on `AbstractComputeResourceEntity`. The constructor uses them for
   the stored windows, and the offer factory passes their sum to the query, so
   the two cannot drift.
4. **Keep the hard-coded release duration for now.** Replacing 10 s with the
   validator's `getReleaseDuration()` (1 s today) would move every stored window
   and change the advertised capacity. That is a separate, user-visible change,
   documented in the PR and tracked in issue #201. The assumption is recorded in
   the code next to the constant.
5. **Extend the range at both ends.** `:rangestart` becomes
   `-reserveBeforeBlocks` and `:rangeend` becomes
   `BLOCK_RANGE_SECONDS / BLOCK_STEP_SECONDS + reserveAfterBlocks`. The search
   therefore still covers the same forward window of available starts, while the
   extra blocks hold the new offer's own preparation and release. A zero span
   gives `0` and `120` again and reproduces the old query exactly.
6. **Keep the immediate start.** Extending the range backwards preserves the #154
   behaviour: an immediate request can still be offered the block that contains
   the requested start, because the offer post-processing moves an earlier start
   forward to the requested time.

### Arithmetic for the current timing

| Quantity | Value |
| --- | --- |
| Block step | 60 s |
| Preparation | 1 s → `ceil(1 / 60) = 1` block |
| Release tail | 5 s gap + 10 s release = 15 s → `ceil(15 / 60) = 1` block |
| `insetBlocks` | 2 |
| `minHullBlocks` | `minBlockLength + 2` |
| `maxHullBlocks` | `maxBlockLength + 2` |
| `rangestart` | −1 |
| `rangeend` | 121 for an unextended range of 120 |

For the default 2 h request, `minBlockLength` is 121, so
`minHullBlocks = 123`, and the range `−1..121` has exactly 123 blocks: a fully
free platform is still offered the current block for a 2 h request.

## Changes

* `SimpleComputeResourceQuery`: new `:minhullblocks`, `:maxhullblocks`,
  `:reservebefore` and `:insetblocks` placeholders; hull-aware `HAVING`, split
  and window length; range extension at both ends; new
  `build(..., long reserveBeforeSeconds, long reserveAfterSeconds)` overloads
  (the existing overloads delegate with zero spans); private `ceilBlocks`
  helper; javadoc and AIMetrics entry.
* `SimpleComputeResourceOfferFactory` and `SimpleComputeResourceOfferFactoryImpl`:
  carry `prepareSeconds` through, compute
  `reserveAfterSeconds = RELEASE_START_GAP_SECONDS + RELEASE_DURATION_SECONDS`,
  and log both.
* `OfferSetRequestParserImpl`: compute the maximum preparation across the
  compute validator results and pass it to the factory. Javadoc explains why the
  maximum and why not the session total.
* `AbstractComputeResourceEntity`: name the two release constants, use them in
  the constructor, and record the `getReleaseDuration()` assumption with a
  reference to #201.
* `SimpleComputeResourceQueryTest`: new tests for the range extension, the
  whole-hull requirement, the window inset, rounding of partial spans, and the
  zero-span equivalence; existing tests use a `buildWithoutReservationSpans`
  helper so the old expectations stay pinned.
* `tests/python/states/test_reservation_window.py`: new capacity-1 state test.
  It submits two requests and asserts that the second request's preparation
  block is at or after the end of the first request's whole reservation (the
  union over all offers in its response, because one request creates a session
  for every offer).
* `docs/booking.md`: the search reads the blocks around the window; the broker
  requires the whole reservation, with the capacity consequence, and a pointer
  to #201.
* `docs/deployment.md`: run the new test on the same capacity-1 broker as the
  atomicity race test.
* `AGENTS.md`: test table row for the new state test.

## Verification

| Check | Result |
| --- | --- |
| `./mvnw test` | 52 tests, 0 failures (`SimpleComputeResourceQueryTest`: 23) |
| `pytest -q any`, mock-profile broker | 47 passed |
| `pytest -q docker/test_docker_platform.py`, docker-profile broker | 18 passed |
| `pytest -q states/test_reservation_window.py`, capacity-1 broker | fails on the pre-fix query, passes with the fix |
| `pytest -q states/test_atomicity_race.py`, capacity-1 broker | 2 passed with the default concurrent submitters |
| same with `RACE_SUBMIT_WORKERS=1` | 2 passed |
| GitHub Actions `build-java-broker` on PR #202 | passed |

The pre-fix run is the pin for the new test. On the parent tree the test fails
with:

```text
The next offer's preparation block [29911149] is inside the previous
reservation [29911069..29911150). The booking query must require the new
offer's whole reservation to be free, not just its available window.
assert 29911149 >= 29911150
```

With the fix the second request's preparation block is exactly the first
block after the first request's reservation.

The capacity effect was measured on a 1-core platform with a ten-minute
request. Each offer now reserves 22 blocks (20 available + 1 preparation +
1 release). The four offers one request returns tile the range with no
overlap; before the fix they were spaced 20 blocks apart with 22-block hulls,
so they overlapped by two blocks at each boundary (six blocks double-booked).
The next request's first offer moves one block later.

## Behaviour change to call out in review

* Advertised free capacity is lower, because the hold of a new offer now
  includes one block of preparation and one block of release. On a 1-core
  platform, a request's four offers no longer overlap each other.
* The offered available window of a candidate at the end of a free run may be
  shorter than before, because the release tail has to fit in the same run.
* The next offer after a reservation starts one block later than it did.
* This is the intended fix for a correctness bug (two sessions could hold the
  same block), but it is user visible and is recorded in the commit message and
  PR body.

## Assumptions and out of scope

* The release window stays a fixed 10 s starting 5 s after availability.
  `getReleaseDuration()` continues to be ignored; changing it is
  [#201](https://github.com/uksrc/Calycopis-broker/issues/201), because it moves
  every stored window and the advertised capacity.
* The 5 s gap has no documented source beyond being the current offset; #201
  asks where it should come from.
* The session's total preparation (executable, data, storage) is not used for
  the compute hold, because those components do not occupy cores. The maximum
  compute preparation is used, matching the compute component's own hull.
* `BLOCK_STEP_SECONDS`/`BLOCK_RANGE_SECONDS` stay at 60 s/2 h. #32
  (`ResourceAllocation` entities) is separate.
* The `simpleexecutionsessions` `available_*` columns remain unused, as before.
* The user's own working-tree changes under `notes/zrq/` are not part of this
  change set.

## Status

status: implemented
details: Implemented in commit [`941e9504`](https://github.com/uksrc/Calycopis-broker/commit/941e9504), merged as [PR #202](https://github.com/uksrc/Calycopis-broker/pull/202), with
  the Java unit tests and the capacity-1 state test. All twelve plan items are complete and
  CI `build-java-broker` passed.

## AIMetrics

```text
AIMetrics: [
    {
    "interval": "2026-11-14T14:10:00/2026-11-14T14:25:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-14T14:50:00/2026-11-14T15:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 12,
      "units": "%"
      }
    }
  ]
```
