<!-- 24c65d91-6d70-4453-afbe-0d79980f8bd5 -->
---
todos:
  - id: "branch"
    content: "Create a branch from main for the stage 5 work"
    status: completed
  - id: "criteria"
    content: "Add the compute booking criteria and its bean"
    status: completed
  - id: "pool-offers"
    content: "Add makeOffers to the simple compute pool and stamp offers with their pool"
    status: completed
  - id: "query"
    content: "Scope the booking query to one pool for both allocations and reservations"
    status: completed
  - id: "factory"
    content: "Select the default pool, call makeOffers and keep the post-processing once"
    status: completed
  - id: "rename"
    content: "Rename the booking classes to their final reservation and offer names"
    status: completed
  - id: "java-tests"
    content: "Add query scope, pool makeOffers and factory regression tests"
    status: completed
  - id: "python-tests"
    content: "Run the Python suites and keep the reservation-pool state test green"
    status: completed
  - id: "docs"
    content: "Update AGENTS.md and docs/booking.md for the pool-scoped query"
    status: completed
  - id: "build"
    content: "Build and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the broker and the Python suites"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: pending
isProject: false
---
# Plan: The pool makes the offers

Date: 2026-11-18
Scope: `Calycopis-broker` — compute pool offer generation, the pool-scoped
  booking query and the final booking class names.
Parent: [`20261114-03`](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md)
  — Compute resource pools and allocations, stage 5.
Issues: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) — Compute resource pools;
[#32](https://github.com/uksrc/Calycopis-broker/issues/32) — ResourceAllocation entities;
[#72](https://github.com/uksrc/Calycopis-broker/issues/72) — Re-usable booking component.

## Target

`SimpleComputeResourcePool.makeOffers(criteria, queryHandler)` becomes the
offer-generation entry point. A pool builds the booking query for itself, runs
it through the query handler passed alongside the criteria, stamps every
returned offer with itself, and returns the unprocessed rows. The criteria
stays the selection input; the handler stays the functional engine.

The request path still selects one pool, the default pool. The offer factory
resolves that pool, builds the criteria, passes the query handler to
`makeOffers`, keeps the existing testing delay, and applies the existing
post-processing once: dropping ended offers, clamping the start into the
requested window, and enforcing the window filter. With one pool, the offer
count, start times and sizes are unchanged.

The booking query is scoped to the selected pool for both sides of the
calculation: the pool's allocations supply the capacity, and only that pool's
reservations consume it. Reservations in other pools no longer reduce this
pool's free space.

This stage also lands the final booking names that stage 1 deliberately left
alone: `SimpleComputeReservationQuery` and its handler, `SimpleComputeOffer`,
`SimpleComputeOfferBean` and `SimpleComputeOfferFactory`.

## Where we are

* `main` is at `61ffa41a`, the merge of stage 4
  ([PR #213](https://github.com/uksrc/Calycopis-broker/pull/213)). Every
  compute reservation already records the pool its offer came from.
* `SimpleComputeResourceQuery` reads supply from the pool's allocations
  (`AbstractComputeAllocations.pool = :pool`) but its `ExecutionBlocks` CTE has
  no pool predicate, so every live reservation consumes every pool's free space.
  The query's limit already comes from the request limit, falling back to
  `QUERY_LIMIT` when the request asks for none.
* `SimpleComputeResourceOfferFactoryImpl` resolves the default pool by name,
  builds the query with the pool UUID, runs `SimpleComputeResourceQueryHandler`,
  stamps the pool onto each row and then post-processes the offers.
* `SimpleComputeResourcePool` has no `makeOffers`, and the pool entity has no
  query-handler dependency. Its only booking-related accessor is the live
  reservation view added in stage 4.
* `OfferSetRequestParserImpl` computes the totals and the maximum compute
  preparation time, then calls `generate` with primitives: start interval,
  duration, minimum cores, minimum memory, limit, preparation seconds and the
  offerset profiling target.
* The booking classes are still `SimpleComputeResource*`. Stage 1 renamed the
  reservation entities but deferred these names because stage 5 rewrites them.

## Decisions

1. **The criteria and the query handler stay separate parameters.**
   `SimpleComputeResourceCriteria` holds the parsed request context, the primary
   compute validator result, the offer limit and the computed preparation
   seconds. It exposes convenience accessors for the start interval, duration,
   minimum cores and minimum memory. It does not carry a query handler. The pool
   method is `makeOffers(criteria, queryHandler)`: the criteria describes what is
   being selected, and the handler is the functional engine that runs the query.
   This keeps the policy input clean for stage 6, where each `PoolPolicy` reads
   `accepts(criteria)` and never sees the handler. The handler arrives as a
   method parameter because the pool is a JPA entity and cannot hold a
   Spring-managed dependency without a transient field or a service-locator
   workaround, both of which the coding rules reject.

2. **The factory builds the criteria and selects the default pool.** The offer
   factory keeps ownership of the query handler. Its public method becomes
   `generate(OfferSetRequestParserContext request, int requestLimit,
   ProfilingTarget target)`. It computes the maximum compute preparation time
   across the context's compute results, builds a criteria bean, resolves the
   default pool, calls `makeOffers(criteria, queryHandler)`, applies the testing
   delay, and post-processes the returned offers. This keeps the parser free of
   query infrastructure and preserves the current bookkeeping of preparation
   time in one place.

3. **The pool stamps its own offers.** `makeOffers` wraps each query row with
   `SimpleComputeOfferBean.withPool(row, this)`. The offer factory no longer
   stamps the pool; post-processing preserves the pool because `clamp` already
   does. The contract is explicit: every offer returned by `makeOffers` belongs
   to that pool, so the reservation built from it records the same pool. This
   also makes a direct two-pool unit test meaningful without calling the factory.

4. **The query is scoped by pool on both sides.** `ExecutionBlocks` gains
   `AND AbstractComputeReservations.pool = :pool`, alongside the existing
   `AbstractComputeAllocations.pool = :pool`. The same bound UUID is used for
   both predicates. The query builder gains a convenience overload
   `build(criteria, poolUuid)` that reads the limit, sizes, window and preparation
   time from the criteria and keeps the primitive overload for focused tests.

5. **The limit is applied inside the pool's query.** The criteria's offer limit
   is bound to `:querylimit`, so a pool returns at most that many rows. Stage 5
   has one pool, so there is no merge and no second cap yet. Stage 6 owns the
   merge cap and the sorting of offers from several pools.

6. **The factory post-processes once.** The pool returns raw query rows; the
   factory keeps `hasEnded`, `clamp` and `startsOutsideWindow` in their current
   order and their current single application. The testing delay stays between
   `makeOffers` and the post-processing, so the atomicity-race window is
   unchanged.

7. **The booking types take their final names.** Rename
   `SimpleComputeResourceQuery` to `SimpleComputeReservationQuery`,
   `SimpleComputeResourceQueryHandler` to `SimpleComputeReservationQueryHandler`,
   `SimpleComputeResourceOffer` to `SimpleComputeOffer`,
   `SimpleComputeResourceOfferBean` to `SimpleComputeOfferBean`, and
   `SimpleComputeResourceOfferFactory` to `SimpleComputeOfferFactory`. Rename
   the Spring implementations and tests to match. Rename the platform accessor
   and fields from `getComputeResourceOfferFactory` to `getComputeOfferFactory`,
   because they return the renamed factory. No published schema type is
   affected.

8. **The request path remains single-pool.** The factory still resolves
   `AbstractComputeResourcePool.DEFAULT_POOL_NAME` and returns no offers when the
   bootstrap has not created it. Pool ordering, enabled flags and policy are
   stage 6 work, and the admin API is unchanged.

## Implementation outline

### Criteria

* Add `SimpleComputeResourceCriteria` in
  `engine/functional/booking/compute/simple/` with the request, primary
  compute result, limit and preparation seconds, plus convenience accessors for
  the request's start interval, duration, total minimum cores and total minimum
  memory. It has no query-handler accessor.
* Add `SimpleComputeResourceCriteriaBean`, an immutable bean. Its `getResource`
  returns the first compute validator result from the request; the request path
  currently builds one offer for all compute results, so a singular resource is
  the existing limitation rather than a new one.

### Pool and query

* Add `makeOffers(SimpleComputeResourceCriteria, SimpleComputeReservationQueryHandler)`
  to `SimpleComputeResourcePool`. The abstract pool keeps only its two
  collections and does not gain the method.
* Implement `makeOffers` in `SimpleComputeResourcePoolEntity`: build the query,
  run it through the handler parameter, stamp every row with `this`, and return
  the offers. The method stays inside the booking transaction that the parser
  already holds.
* Rename the query builder and add the criteria overload. Add the reservation
  pool predicate to `ExecutionBlocks` and keep the allocation predicate in
  `PoolAllocations`.

### Factory and parser

* Rename the factory interface, implementation and Spring bean. Replace the
  primitive `generate` parameters with the request context, request limit and
  profiling target.
* Build the criteria in the factory, compute the preparation seconds there, and
  remove the preparation loop from `OfferSetRequestParserImpl`.
* Pass the query handler to `makeOffers` as its second parameter; the factory
  keeps ownership of the handler.
* Pass the renamed offer type through the parser, session factory, reservation
  constructor, validator result and query handler.

### Tests

* Rename the query and offer tests. Add assertions that the generated query
  contains both `AbstractComputeReservations.pool = '<uuid>'` and
  `AbstractComputeAllocations.pool = '<uuid>'`, that the limit is bound from the
  criteria, and that the phase and hull predicates are unchanged.
* Add a pool unit test with two pool entities and a recording query handler
  keyed by pool UUID. Call `makeOffers(criteria, handler)` directly on each pool
  and assert that each query carries that pool's UUID and limit, each returned
  offer carries that pool, and one pool's handler never receives the other
  pool's UUID.
* Adapt the factory test so a test pool overrides
  `makeOffers(criteria, handler)` with fixed offers; keep the default-pool
  selection, missing-pool, ended-offer, clamp and window-filter assertions. The
  factory must still post-process exactly once.
* Keep the reservation-pool state test. The Python `any` and `mock` suites are
  the one-pool equivalence check: offer counts, start times, sizes and response
  bodies remain unchanged.

### Documentation

* Update `AGENTS.md`: the booking query is pool-scoped on both sides, the pool
  owns `makeOffers(criteria, queryHandler)`, the criteria carries the selection
  input and the handler stays a separate parameter, and the final booking class
  names.
* Update `docs/booking.md`: the pool's allocations supply capacity, the pool's
  reservations consume it, and the request path still uses the default pool.
  Note that a reservation in another pool does not reduce this pool's free space.

## Acceptance

* `./mvnw clean compile` is green, and `./mvnw test` passes with the query,
  pool and factory tests added.
* With one pool, the offer count, start times and sizes are identical to the
  previous behaviour; the Python `any` and `mock` suites pass apart from the
  pre-existing `test_valid_notebook_location` failure.
* A unit test calls `makeOffers` directly on two pools of different capacity and
  shows each pool's query and offers depend only on its own UUID, allocations and
  reservations.
* A pool returns no more offers than the limit in the criteria.
* An offer made from a pool creates its reservation in that pool.
* The factory applies clamping, ended-offer dropping and the window filter once,
  to what the selected pool returns.
* Offer, session and pool response bodies are unchanged; the OpenAPI schema is
  unchanged; there is no new admin endpoint.

## Out of scope

* Several enabled pools, ordering, priority, policy and merged offers (stage 6).
* Sorting a merged offer list and capping the merge at the request limit
  (stage 6).
* Pool policy diagnostics and one message per declining pool (stage 6).
* Retiring the global booking capacity and documenting the full model
  (stage 7).
* Any change to the offer, session or pool API responses.
* Fixing the existing limitation that several compute resources share one offer
  and one reservation query. The criteria exposes the primary result; this stage
  does not change that behaviour.

## Deferred

* **Moving query execution into a Spring-managed pool service.** The handler is
  a separate method parameter because the pool is a JPA entity. If pools later
  become Spring beans or a separate service owns query execution, the handler can
  move into that service while `makeOffers` keeps the criteria as its request
  input.
* **A repository query for the live reservation view.** Stage 4 already deferred
  it; this stage reads reservations in SQL rather than through the collection.
* **A multi-compute criteria.** A future request model may need a list of
  compute results rather than one primary result; the criteria interface would
  grow a list accessor at that point.
* **Pool-level profiling.** The factory still profiles `generate`, which now
  includes `makeOffers`; a separate pool query profiling entry can wait until
  there is more than one pool to compare.

## Questions for review

1. **The factory signature.** Replace the primitive `generate` arguments with
   the request context, limit and profiling target, and build the criteria
   inside the factory. Confirm that moving the preparation-time calculation from
   the parser into the factory is the right split.
2. **The primary compute result.** The criteria exposes the first compute
   validator result, matching the request path's existing single-offer,
   multiple-compute limitation. Confirm that this is acceptable for stage 5 and
   that a multi-compute criteria belongs after it.
3. **The rename scope.** Include the platform accessor and field names in the
   `SimpleComputeOfferFactory` rename, as proposed, or leave
   `getComputeResourceOfferFactory` unchanged to keep the platform API stable.

The query handler is now a separate `makeOffers` parameter, not part of the
criteria; that decision is settled and is not a review question.

## Status

status: implemented
details: Written on 2026-11-18 against `main` at `61ffa41a`, after the stage 4
  merge, and revised on review the same day: the query handler is a separate
  `makeOffers` parameter rather than part of the criteria. The decisions were
  agreed on review on 2026-11-18, including the factory signature, the primary
  compute result and the platform accessor rename. Implemented on branch
  `20261118-zrq-pool-offers`. The stage makes the pool the offer-generation
  entry point and scopes the booking query to one pool, while the request path
  still uses the default pool. Java tests pass (101 tests), the Python `any`
  and `mock` suites pass apart from the pre-existing
  `test_valid_notebook_location` failure, and
  `states/test_reservation_pool.py` passes. The change set is ready for review;
  commit, push and pull request remain pending approval.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-18T11:19:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T11:41:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 12,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T11:45:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 1,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T11:45:00/2026-11-18T12:05:00",
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
