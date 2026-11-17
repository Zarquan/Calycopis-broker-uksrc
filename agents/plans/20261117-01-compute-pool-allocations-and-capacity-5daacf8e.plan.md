<!-- 5daacf8e-d20d-4200-9052-100fcbc160e9 -->
---
todos:
  - id: "branch"
    content: "Create a branch from main for the stage 3 work"
    status: completed
  - id: "entities"
    content: "Add the allocation interfaces, entities and tables"
    status: completed
  - id: "persistence"
    content: "Add the allocation factories, repository and Spring wiring"
    status: completed
  - id: "admin"
    content: "Add the allocation admin endpoints and problem types"
    status: completed
  - id: "query"
    content: "Read capacity from allocations in the booking query"
    status: completed
  - id: "pool"
    content: "Bind the default pool into the offer factory"
    status: completed
  - id: "bootstrap"
    content: "Seed allocations and guarantee the default pool"
    status: completed
  - id: "config"
    content: "Extend pools.yaml and the configuration template"
    status: completed
  - id: "java-tests"
    content: "Update the Java query tests and add allocation seed tests"
    status: completed
  - id: "python-tests"
    content: "Add the allocation admin and offer-impact tests"
    status: completed
  - id: "docs"
    content: "Update AGENTS.md, docs/booking.md and the deployment guide"
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
# Plan: Compute pool allocations and capacity

Date: 2026-11-17
Parent: [`20261114-03`](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md)
  — Compute resource pools and allocations, stage 3.
Issues: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) — Compute resource pools;
[#32](https://github.com/uksrc/Calycopis-broker/issues/32) — ResourceAllocation entities;
[#72](https://github.com/uksrc/Calycopis-broker/issues/72) — Re-usable booking component.

## Target

A pool's capacity is the sum of its allocations over time, and the booking query
reads supply from those allocations instead of from the global capacity
constant. A deployment with the seeded default allocation behaves exactly as it
does now: same offers, same start times, same sizes.

Allocations are admin-managed capacity windows. An allocation belongs to one
pool, starts at an instant, may end at another (absent means open ended), and
carries a single number of cores and a single amount of memory. The pool's
capacity at a block is the sum of the allocations covering it.

This stage adds supply and the admin surface for it. It does not put a pool on
a reservation, does not scope reservations by pool, and does not move offer
generation into the pool; those are stages 4 to 6 of the parent plan.

## Where we are

* Pools exist as entities with `POST`, `GET` and `GET /{uuid}` at
  `/admin/pools/compute`, seeded from an optional `pools.yaml`, with a `default`
  pool when the file says nothing (stage 2).
* Capacity is one flat total. `BookingCapacitySettings` supplies 256 cores and
  256 GiB, `SimpleComputeResourceQuery.DATABASE_QUERY` subtracts the reservation
  hull of the live sessions from `:totalcores` and `:totalmemory`, and
  `SimpleComputeResourceOfferFactoryImpl.generate` builds the query with those
  two numbers.
* Admin paths carry at most one UUID: every admin endpoint selects one object by
  its own identifier, so a pool does not appear in an allocation's path.
* There is no allocation entity, no capacity history, and no pool on the request
  path. Offers are global, and the booking lock is keyed on the reservation kind
  rather than on a pool.
* `SimpleComputeResourceQuery.build` substitutes values into the SQL text
  because `SpringAbstractQueryHandler.query(sql, params)` ignores its parameters
  (a TODO). Anything added to the query has to follow the same substitution.
* Hibernate runs with `ddl-auto: create`, so the schema is rebuilt at every
  start and this stage needs no migration.

## Decisions

1. **An allocation is a schema-tier entity with an abstract root, and no
   platform tier.** It is an admin resource, not a platform speciality, so the
   two tiers are abstract (`abstractcomputeallocations`, joined inheritance) and
   concrete (`simplecomputeallocations`). The concrete entity is instantiable,
   as the pool entity is. A second allocation implementation adds an entity
   under the same root.
2. **The allocation is a window with single sizes.** Columns: the owning pool,
   `startseconds`, `endseconds` (nullable, open ended), `cores` and `memory`.
   Cores and memory are positive. An end, when present, is after the start. The
   reservation keeps its min/max range; the allocation has one supply value per
   dimension.
3. **Instants are stored as epoch seconds.** The lifecycle components already
   store seconds, and the query arithmetic works in seconds and blocks. The
   entity stores `long` seconds and exposes `Instant` accessors for the API, so
   the two representations cannot disagree.
4. **Capacity is compared per 60-second block, rounded outward.** An allocation
   covers every block its window touches: the block containing its start is the
   first, and the block containing its end is not included. An open-ended
   allocation covers every block from its start onwards. This is the same
   rounding the reservation hull already uses (`ceilBlocks` in the query
   builder).
5. **The query reads supply from a `PoolAllocations` CTE.** The CTE holds the
   allocations of one pool, and `AvailableBlocks` subtracts the reservation sum
   from the allocated sum for each block, rather than from a constant. Reserved
   capacity stays global in this stage: `ExecutionBlocks` has no pool column
   until stage 4, and with one pool that is exactly equivalent. Stage 5 filters
   both sides.
6. **The offer factory binds the pool named `default`.** `generate` resolves the
   pool through the pool factory and passes its identifier into the query
   builder. When the pool is missing the factory logs an error and returns no
   offers, rather than failing the request; the bootstrap makes that state
   unreachable in a normal deployment.
7. **The bootstrap guarantees a `default` pool.** It seeds the pools the file
   declares and adds the `default` pool when the file omits it, and it gives
   every seeded pool one open-ended allocation when the file declares none. The
   fallback allocation takes its sizes from `BookingCapacitySettings`, so the
   seeded capacity and the configured capacity cannot drift. This is the one
   behaviour change in the stage, and it is required because the request path
   has no pool ordering to choose from until stage 6. An `enabled` flag, so a
   deployment can disable the default pool rather than delete it, is deferred to
   stage 6 with the pool policy.
8. **The seeded allocation starts at the epoch and never ends.** The query can
   look before the requested start (the preparation blocks) and far into the
   future, so a seeded allocation that starts at broker startup would leave the
   first blocks unsupplied and change the offers at the boundary. Starting at
   the epoch makes the equivalence exact. It is a seed, not a claim about the
   platform's history, and the deployment guide says so.
9. **Collection operations are scoped to the pool; item operations use only the
   allocation identifier.** The project avoids paths with more than one UUID, so
   `GET` and `POST` on `/admin/pools/compute/{uuid}/allocations` list and create
   the pool's allocations, while `GET`, `POST` and `DELETE` on
   `/admin/allocations/compute/{uuid}` select, update and delete one allocation.
   A creation returns `303 See Other` with a `Location` header naming the
   allocation's details, not `201`; an update returns `200` with the allocation;
   a delete returns `204`. The pool is in the creation path and in every
   response, and an update changes sizes and window only, never the pool. A
   blank or non-positive size, or an end not after the start, is `400`; an
   unknown pool or allocation is `404`; an update or deletion that would leave
   reservations above supply in any block is `409`.
10. **The update and delete guards run under the booking lock.** Both guards
    read the live reservations and then change supply, which is check-then-act.
    They take the same advisory lock as a booking, keyed on the reservation kind
    for now, so a concurrent request cannot commit a reservation between the
    check and the write, and the pool they protect is the one named by the
    allocation, read before the guard runs. Per-pool lock keys arrive in stage 6,
    when two pools can book concurrently.
11. **`BookingCapacitySettings` stays, but the query stops reading it.** It
    seeds the default allocation and still feeds the actuator information
    contributor. Stage 7 retires it.

## Changes

### The allocation tiers

* `engine/entities/compute/allocation/AbstractComputeAllocation.java` — the
  interface, holding the pool, the window and the sizes.
* `engine/entities/compute/allocation/AbstractComputeAllocationEntity.java` —
  the joined-inheritance root, table `abstractcomputeallocations`, with the
  `pool` many-to-one column (`@JoinColumn(name = "pool")`), the two second
  columns and the two size columns. It follows `ComponentEntity` for the
  identifier and timestamps, as the pool entity does.
* `engine/entities/compute/allocation/simple/SimpleComputeAllocation.java` —
  `KIND_DISCRIMINATOR = uri:compute-allocation`.
* `engine/entities/compute/allocation/simple/SimpleComputeAllocationEntity.java`
  — table `simplecomputeallocations`, discriminator `uri:compute-allocation`.
* The factory interface and implementation, the engine repository interface
  (`findByPool`, `delete`) and the Spring repository in `spring/jpa/`, split the
  same way the pool factory is: engine implementation plus a `@Component`
  subclass in `spring/pools/` that supplies the repository.

### The booking query

* `SimpleComputeResourceQuery.DATABASE_QUERY` gains a `PoolAllocations` CTE, and
  `AvailableBlocks` computes `allocated - reserved` per block instead of
  `:totalcores - reserved`. Allocated cores and memory come from a per-block
  aggregate over the pool's allocations, with the open-ended case handled by an
  `end IS NULL` predicate rather than a sentinel end value.
* `SimpleComputeResourceQuery.build` takes the pool identifier in place of
  `totalCores` and `totalMemory`, substitutes `:pool`, and leaves the rest of
  the substitution as it is. The overload that used the default totals is
  removed, so every caller supplies a pool.
* `SimpleComputeResourceOfferFactoryImpl` takes the pool factory, resolves the
  `default` pool, and passes its identifier to the builder. The Spring subclass
  constructor follows.

### The admin API

* `spring/webapp/AdminComputePoolAllocationsController.java` at
  `/admin/pools/compute/{uuid}/allocations`, with `GET` to list the pool's
  allocations and `POST` to create one. A creation returns `303 See Other` with
  a `Location` header naming `/admin/allocations/compute/{uuid}`.
* `spring/webapp/AdminComputeAllocationsController.java` at
  `/admin/allocations/compute/{uuid}`, with `GET` to select, `POST` to update
  and `DELETE` to remove one allocation.
* `spring/webapp/ComputeAllocationRequest.java` — `{cores, memory, start, end}`
  for creation and update, with `start` defaulting to now and `end` optional.
  The owning pool comes from the creation path and cannot be changed.
* `spring/webapp/ComputeAllocationResponse.java` — `{uuid, pool, cores, memory,
  start, end, created}`. The pool is repeated because the item path does not
  imply it.
* `ProblemType` gains `INVALID_ALLOCATION` (`400`), `ALLOCATION_NOT_FOUND`
  (`404`) and `ALLOCATION_OVERCOMMITTED` (`409`); an unknown pool reuses
  `POOL_NOT_FOUND`.
* The update and delete handlers wrap the guard and the write in
  `bookingTransactionHandler.book(...)` with the reservation kind. An update
  evaluates the candidate values without writing them first and returns `200`
  only when the guard passes; a delete returns `204` when it passes. The guard
  SQL lives with the allocation query, in the engine, and is executed through
  the same query-handler pattern as the booking query; it filters by the
  allocation's pool.

### The bootstrap and configuration

* `ComputePoolSpec` gains a list of allocations, and a new
  `ComputeAllocationSpec` carries `cores`, `memory`, and optional `start` and
  `end` instants. The bootstrap turns them into entities, or seeds one open-ended
  allocation from `BookingCapacitySettings` when a pool declares none.
* `ComputePoolBootstrap` gains the allocation factory, seeds the allocations
  idempotently (a pool that already exists keeps the allocations it has), and
  appends the `default` pool when the configuration omits it.
* `config/pools.yaml` gains a commented allocation example, and
  `bin/config-broker.sh` keeps writing the minimal default pool; the deployment
  guide shows an allocation entry.
* `docs/deployment.md` documents the allocation entry and the seeding rule, and
  notes that the global capacity setting now only seeds the default allocation.

### Tests and documentation

* Java: the query tests assert that `:pool` is substituted, that
  `PoolAllocations` is present, and that no `:totalcores` or `:totalmemory`
  remains. The bootstrap tests gain allocation seeding and the default-pool
  guarantee, using stub factories.
* Python: `tests/python/any/test_admin_allocations.py` covers the `401`, `403`,
  `400`, `404`, `409` and `204` paths, and `tests/python/mock/` gains a test
  that an added allocation increases the offers, that a future allocation does
  not contribute before its start, that an ended allocation stops contributing,
  and that the seeded allocation keeps the existing offers unchanged.
* `AGENTS.md` gains the allocation bullet in the data model and the endpoints
  in the API surface. `docs/booking.md` describes the supply CTE and the
  equivalence with the old constant.

## Acceptance

* `./mvnw clean compile` is green, and `./mvnw test` passes with the query and
  bootstrap tests updated. The query test fails if any placeholder is left
  unsubstituted.
* With the seeded allocation equal to the configured capacity, the `mock` suite
  passes unchanged: the same offer counts, start times and sizes as on `main`
  before this stage. That suite is the equivalence check; there is no
  database-backed Java test harness to run the old and new queries side by side.
* An admin creates an allocation for the default pool with
  `POST /admin/pools/compute/{pool}/allocations`, gets `303` and a `Location`
  naming `GET /admin/allocations/compute/{uuid}`, and sees it in
  `GET /admin/pools/compute/{pool}/allocations`; a non-admin gets `403`; no
  credentials get `401`.
* `GET /admin/allocations/compute/{uuid}` selects the allocation;
  `POST` on the same path updates its sizes and window and returns `200`;
  `DELETE` returns `204`.
* An update that would over-commit the pool is `409` and leaves the allocation
  as it was; a shrink below live reservations is rejected the same way.
* A `400` for zero or negative cores or memory, and for an end that is not after
  the start; `404` for an unknown pool and an unknown allocation.
* Adding an allocation increases the offers within the requested limit. An
  allocation that starts in the future does not contribute before its start. An
  allocation that has ended stops contributing after its end.
* Deleting an allocation that no live reservation reaches succeeds. Deleting
  one whose removal would leave reservations above supply in any block is
  rejected with `409`, and the allocation remains.
* A deployment that configures only `calycopis.broker.booking.capacity` behaves
  as it did before this stage.

## Out of scope

* A pool reference on the reservation, and pool-scoped reservation sums
  (stage 4).
* `makeOffers(criteria)` on the pool (stage 5).
* More than one pool on the request path, ordering and policies (stage 6).
* Retiring `BookingCapacitySettings` (stage 7).
* Force-shrinking an allocation below live reservations, moving an allocation
  between pools, recurring allocations, and a database-enforced capacity
  invariant.

## Deferred

* **Moving an allocation between pools.** An update changes sizes and window
  only; a pool change would need a guard against two pools at once.
* **A force-shrink override.** Parent decision 10 rejects a shrink below live
  reservations with no override until a user asks for one.
* **An `enabled` flag on a pool.** Disabling the default pool, rather than
  deleting it, arrives with pool policy in stage 6.
* **Per-pool lock keys.** The invariant is per pool, but the request path has one
  pool, so the existing key keeps bookings and the delete guard serialised.
  Stage 6 moves the key when two pools can book concurrently.
* **A database-enforced invariant.** A per-block ledger or an exclusion
  constraint is stronger than a lock, and its own plan.
* **Allocation calendars and capacity history.** One window per allocation, and
  no retained rows after a delete.
* **A published allocation type.** Until the schema split, allocations are
  admin-only and off-schema.

## Resolved on review (2026-11-17)

1. **Guaranteeing the `default` pool.** Agreed: the bootstrap always seeds a
   `default` pool, including when `pools.yaml` names only others. A later stage
   adds an `enabled` flag, so a deployment can disable it rather than delete it.
2. **The shape of an allocation in `pools.yaml`.** Agreed: a list of allocation
   entries under each pool.
3. **The status code for a delete.** Agreed: `204` with no body.
4. **The admin namespace.** Revised again on review: collection operations stay
   under the pool (`GET` and `POST` on
   `/admin/pools/compute/{uuid}/allocations`), while an individual allocation is
   addressed by its own UUID (`GET`, `POST` and `DELETE` on
   `/admin/allocations/compute/{uuid}`). A creation redirects with
   `303 See Other` to the allocation details, and `POST` on an allocation
   updates it, so a shrink is an update guarded by the same `409` as a delete.
5. **A select endpoint for one allocation.** Agreed, and folded into the item
   path above.

## Status

status: implemented
details: Implemented on 2026-11-17 on branch
  `20261117-zrq-compute-allocations`, from `main` at `9e6c3a4e`. The allocation
  entities, factories, admin endpoints and over-commit guard are in place, and
  the booking query reads a pool's supply from its allocations. `./mvnw test`
  passes with 62 tests; the allocation Python suite passes with 22 tests, and
  the `mock` suite passes with 55 tests plus the pre-existing
  `test_valid_notebook_location` failure that also fails on `main`. A commit and
  pull request follow once the change set is approved.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-17T14:27:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T15:21:00/2026-11-17T15:35:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 20,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T15:45:00/2026-11-17T15:55:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 15,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 20,
      "units": "%"
      }
    }
  ]
```
