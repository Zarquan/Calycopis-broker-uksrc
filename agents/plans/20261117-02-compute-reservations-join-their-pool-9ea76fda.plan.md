<!-- 9ea76fda-6b9f-4830-95d0-b472248c3330 -->
---
todos:
  - id: "branch"
    content: "Create a branch from main for the stage 4 work"
    status: completed
  - id: "reservation"
    content: "Add the non-null pool reference to the reservation root"
    status: completed
  - id: "pool"
    content: "Add the live reservation view to the pool"
    status: completed
  - id: "offer"
    content: "Carry the pool on the offer and stamp it in the offer factory"
    status: completed
  - id: "session"
    content: "Add the live-phase helper to the simple execution session"
    status: completed
  - id: "java-tests"
    content: "Update the offer tests and add the pool stamping tests"
    status: completed
  - id: "python-tests"
    content: "Add the state test for the stored pool and the live view"
    status: completed
  - id: "docs"
    content: "Update AGENTS.md and docs/booking.md for the reservation pool"
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
# Plan: Compute reservations join their pool

Date: 2026-11-17
Parent: [`20261114-03`](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md)
  — Compute resource pools and allocations, stage 4.
Issues: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) — Compute resource pools;
[#32](https://github.com/uksrc/Calycopis-broker/issues/32) — ResourceAllocation entities;
[#72](https://github.com/uksrc/Calycopis-broker/issues/72) — Re-usable booking component.

## Target

Every compute reservation records the pool its offer came from. The offer bean
carries that pool, the offer factory stamps the default pool on every offer, and
the reservation constructor copies the pool from the offer into a non-null
foreign-key column.

The request path still uses one pool. Offers, response bodies and the booking
query behave exactly as they do on `main` after stage 3; this stage only records
provenance. Pools also gain a read-only view of their live reservations, which
is the demand side that stage 5 will put into the pool-scoped booking query.

## Where we are

* Stage 3 is merged on `main` at `4f229cfd` (PR
  [#212](https://github.com/uksrc/Calycopis-broker/pull/212)). A pool's capacity
  is the sum of its allocations over time, and `SimpleComputeResourceQuery`
  reads that supply per block. Reserved capacity is still global: the
  `ExecutionBlocks` CTE has no pool predicate.
* The default pool is guaranteed by `ComputePoolBootstrap`, and the offer factory
  resolves it by name before building the query. The resolved pool entity is
  then discarded; only its UUID reaches the SQL.
* `SimpleComputeResourceOfferBean` wraps one query row: name, start, duration,
  cores and memory. It carries no pool, and `SimpleComputeResourceOfferFactoryImpl`
  post-processes the rows by dropping ended offers and clamping start times.
* `AbstractComputeReservationEntity` holds the session, the min/max sizes and the
  preparing, available and releasing windows. It has no pool column. The mock
  and docker reservation subclasses inherit the root constructor.
* `SimpleExecutionSessionEntity` stores the OpenAPI session phase. There is no
  helper that says which phases count as live; the booking query spells the
  seven phases out in SQL.
* `AbstractComputeResourcePoolEntity` carries only the component base. It has no
  collection of reservations, and the pool admin response does not expose one.

## Decisions

1. **The pool reference belongs on the reservation root.** Add
   `@ManyToOne(optional = false, fetch = FetchType.LAZY)` with
   `@JoinColumn(name = "pool", referencedColumnName = "uuid", nullable = false)`
   to `AbstractComputeReservationEntity`, so every reservation type inherits the
   column in `abstractcomputereservations`. There is no cascade and no orphan
   removal: the pool owns its allocations, but a reservation is owned by its
   session, and pools have no delete operation. The non-null join column is what
   makes an unattributed reservation impossible rather than merely discouraged.

2. **The domain interface exposes the pool.** `AbstractComputeReservation` gains
   `getPool()`, returning `AbstractComputeResourcePool`; the entity overrides it
   with the entity subtype. The reservation constructor assigns the pool from
   the offer and fails fast with a clear `IllegalStateException` if the offer has
   no pool. That state cannot arise through the factory, and failing at the point
   of construction is better than a database null violation later.

3. **The offer carries the pool entity, not a UUID.** `SimpleComputeResourceOffer`
   gains `getPool()`, returning `AbstractComputeResourcePoolEntity`.
   `SimpleComputeResourceOfferFactoryImpl` already resolves the default pool
   inside the booking transaction, so the entity is available and managed; the
   reservation can copy the relation without looking it up again. A UUID would
   force a second lookup in the reservation constructor, and the constructor has
   no factory to do it with. The concrete entity type keeps the relation type-safe
   for JPA. The offer bean is an internal booking object, not an API bean, so
   carrying an entity does not leak into the schema responses.

4. **The factory stamps every offer before post-processing.** `SimpleComputeResourceOfferBean`
   gains a pool field and a `withPool(offer, pool)` copy that preserves the
   offer name, window, sizes and pool. `SimpleComputeResourceOfferFactoryImpl`
   applies it to each row returned by the query before the ended-offer and
   start-window filters, and `clamp` preserves the pool when it shortens the
   offer. The query handler stays generic: it still maps rows to unpooled beans,
   and the factory is the single place that knows which pool the query was for.
   A factory unit test asserts that every returned offer carries the resolved
   pool. `SimpleComputeResourceOfferBean.create(ResultSet, int)` remains the
   row-mapping entry point; the unpooled bean is never returned by the factory.

5. **The pool holds a lazy reservation collection.** `AbstractComputeResourcePoolEntity`
   gains `@OneToMany(mappedBy = "pool", fetch = FetchType.LAZY)` with no cascade,
   and `AbstractComputeResourcePool` gains `Iterable<AbstractComputeReservation>
   getReservations()`. The entity accessor filters the collection down to live
   sessions rather than returning terminal offers. This is a domain view for
   stage 5, not an API response: the pool controller continues to return
   `ComputePoolResponse`, which has no reservations field.

6. **Live has one Java definition, matching the booking query.** Add
   `isLive()` to `SimpleExecutionSession` and implement it on
   `SimpleExecutionSessionEntity` as a switch over the seven booking phases:
   `OFFERED`, `ACCEPTED`, `WAITING`, `PREPARING`, `AVAILABLE`, `RUNNING` and
   `RELEASING`. `INITIAL` and the terminal phases are not live. The switch is
   also exposed as a static `isLive(phase)` helper, so the unit test can check
   the phase set without constructing a session entity. The pool's reservation
   view calls the instance method. The SQL query keeps its own phase list in
   this stage because the query is not being rewritten; stage 5 can align the two
   when it scopes the query by pool.

7. **Nothing about offer generation or counting changes.** The offer factory
   still resolves `DEFAULT_POOL_NAME`, passes the pool UUID to
   `SimpleComputeResourceQuery.build`, and returns the same offers in the same
   order. There is no pool predicate on `ExecutionBlocks`, no `makeOffers` method
   and no change to the limit. With only the default pool on the request path,
   recording the pool changes no offer or session response.

8. **The reservation constructor is the only construction path.** The mock and
   docker factories already pass the offer to their entity constructors, and the
   base constructor assigns `this.pool = offer.getPool()`. No platform subclass
   needs a new parameter, and no separate lookup is added to a factory. A
   reservation loaded from the database keeps the foreign key it was saved with.

## Changes

### Reservation and pool model

* `engine/entities/compute/AbstractComputeReservation.java` — add `getPool()`.
* `engine/entities/compute/AbstractComputeReservationEntity.java` — add the
  lazy many-to-one `pool` field with its join column, assign it from the offer
  in the protected constructor, fail fast when the offer has no pool, and expose
  the entity getter.
* `engine/entities/compute/pool/AbstractComputeResourcePool.java` — add
  `getReservations()`, returning the live reservations of this pool.
* `engine/entities/compute/pool/AbstractComputeResourcePoolEntity.java` — add
  the lazy `@OneToMany(mappedBy = "pool")` collection and the filtering getter.
  The collection is not cascaded and is never serialized directly.
* `engine/entities/session/simple/SimpleExecutionSession.java` — add `isLive()`.
* `engine/entities/session/simple/SimpleExecutionSessionEntity.java` — implement
  `isLive()` with the seven live phases, and expose the same switch as a static
  `isLive(IvoaSimpleExecutionSessionPhase)` helper for tests. Document that it
  mirrors the booking query's phase predicate.

### The offer path

* `engine/functional/booking/compute/simple/SimpleComputeResourceOffer.java` —
  add `getPool()`.
* `engine/functional/booking/compute/simple/SimpleComputeResourceOfferBean.java` —
  add the pool field, a full constructor that accepts it, a `withPool` copy, and
  preserve the pool in `clamp`. The row-mapping `create` remains the unpooled
  entry point used by the query handler.
* `engine/functional/booking/compute/simple/SimpleComputeResourceOfferFactoryImpl.java` —
  stamp each query offer with the resolved default pool before the post-processing
  loop. The existing missing-pool branch still logs an error and returns no
  offers; the existing query and limit handling is untouched.

### Tests

* `java/.../SimpleComputeResourceOfferBeanTest.java` — construct offers with a
  test pool and assert that `clamp` preserves the pool as well as the window.
* A new `SimpleComputeResourceOfferFactoryTest.java` — stub the pool factory,
  query handler, profiling factory and testing settings, return two rows from
  the handler, and assert every generated offer carries the resolved pool. The
  pool factory reporting no default pool still yields no offers.
* A new `SimpleExecutionSessionEntityTest.java` — call the static phase helper
  and assert that the seven live phases report live, while `INITIAL`,
  `REJECTED`, `EXPIRED`, `COMPLETED`, `FAILED` and `CANCELLED` do not.
* `tests/python/states/conftest.py` — add read-only helpers for the default
  pool's UUID, a session's stored reservation pool, and the live reservations
  recorded against a pool. The helpers read the same tables the accessor uses.
* A new `tests/python/states/test_reservation_pool.py` — submit a request and
  read its first offer, assert the offered session's compute reservation stores
  the default pool UUID, and assert the reservation is visible in the default
  pool's live set. Reject the offer and assert the terminal reservation is no
  longer in the live set. Create a second pool through the admin API and assert
  the default reservation is not in that pool's set.
* The schema assertion belongs with the state helpers: the
  `abstractcomputereservations.pool` column is `NOT NULL` and has a foreign-key
  constraint to the pool table. If Hibernate's generated schema does not create
  the constraint, the implementation adds the explicit mapping needed to make
  the acceptance condition true rather than weakening the test.

### Documentation

* `AGENTS.md` — update the data-model bullet: a compute reservation records the
  pool its offer came from, and the pool exposes its live reservations. The API
  surface is unchanged, so no endpoint documentation is added.
* `docs/booking.md` — describe the reservation provenance, the live-phase set and
  the fact that the booking query remains global until stage 5 scopes it.
* No change to `docs/deployment.md`, `config/pools.yaml` or the OpenAPI schema:
  this stage adds no admin surface and no configuration key.

## Acceptance

* `./mvnw clean compile` is green, and `./mvnw test` passes with the offer,
  factory and session tests added.
* Every reservation created from an offer, including an `OFFERED` one, stores a
  non-null `pool` equal to the default pool's UUID. The column is non-null and
  the foreign-key constraint exists in the generated schema.
* `SimpleComputeResourcePoolEntity.getReservations()` returns only reservations
  whose session is live, and only those whose `pool` is this pool. Terminal
  sessions and reservations of another pool are absent.
* The offer and session response bodies are unchanged; the OpenAPI schema is
  unchanged; the pool response still has no reservations field.
* The `tests/python/any` suite and the `tests/python/mock` suite pass unchanged,
  apart from the pre-existing `test_valid_notebook_location` failure that also
  fails on `main`.
* A state test asserts the stored pool for a session's compute reservation, and
  the live view excludes a rejected offer and another pool's reservations.

## Out of scope

* Pool-scoped booking queries and `makeOffers(criteria)` on the pool (stage 5).
* Several pools on the request path, ordering and policies (stage 6).
* Retiring `BookingCapacitySettings` (stage 7).
* Any change to offer generation, offer counting or the booking limit.
* Any change to the offer, session or pool API responses.
* Pool deletion, cascading reservation deletion, and moving a reservation
  between pools.
* A published schema type for reservations' pool, or a pool admin response that
  lists reservations.

## Deferred

* **A repository query for the live reservation view.** A lazy collection plus
  `isLive()` keeps the relation on the aggregate and avoids a new repository
  method. If the collection proves too expensive once pools hold many historical
  reservations, a repository query can replace it without changing the public
  accessor.
* **Eager loading of the session phase.** The accessor reads the session phase
  through the lazy session relation, so it belongs in a transaction like the
  other entity collections. The request path never calls it in this stage.
* **The pool's reservation list in the admin API.** Stage 5 or the pool policy
  work can decide whether an admin needs to see demand, and what shape it should
  take.
* **A database-enforced reservation attribution invariant beyond the foreign
  key.** The non-null column is the invariant; there is no need for a trigger or
  a check constraint.

## Decisions agreed at review

All four decisions above were agreed on 2026-11-17, and the implementation
follows them:

1. The offer carries the resolved `AbstractComputeResourcePoolEntity`, so the
   reservation constructor copies it without a second lookup.
2. The pool's live reservation view is a lazy `@OneToMany` collection filtered
   by `isLive()`, not a repository query.
3. `isLive()` lives on the simple execution session, with a static phase helper
   for the unit test.
4. The state test asserts the stored pool and the non-null, foreign-key schema
   metadata.

## Status

status: implemented
details: Implemented on 2026-11-17 on branch `20261117-zrq-reservations-pool`,
  from `main` at `4f229cfd`. Every reservation records its offer's pool in a
  non-null foreign-key column, offers carry that pool through the factory, and
  the pool exposes its live reservations. Offer generation, booking counts and
  API responses are unchanged. Verified with `./mvnw test` (67 tests), the
  Python `any` and `mock` suites (136 passed, with the pre-existing
  `test_valid_notebook_location` failure), and
  `tests/python/states/test_reservation_pool.py` (2 passed). Commit, push and
  pull request are pending approval of the change set.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-17T17:49:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    }
  ]
```
