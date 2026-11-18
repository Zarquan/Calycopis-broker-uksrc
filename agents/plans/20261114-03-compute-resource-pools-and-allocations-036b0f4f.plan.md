<!-- 036b0f4f-8048-4ca6-aede-354728a64bf4 -->
---
todos:
  - id: "stage-1"
    content: "Stage 1: rename the compute resource classes to compute reservations"
    status: completed
  - id: "stage-2"
    content: "Stage 2: pool entities, repository and admin API, with a default pool bootstrap"
    status: completed
  - id: "stage-3"
    content: "Stage 3: allocation entities and admin API, capacity read from allocations"
    status: completed
  - id: "stage-4"
    content: "Stage 4: attribute every reservation to the pool it was made from"
    status: completed
  - id: "stage-5"
    content: "Stage 5: makeOffers on the pool, with a pool-scoped booking query"
    status: completed
  - id: "stage-6"
    content: "Stage 6: several pools, each carrying its own policy"
    status: completed
  - id: "stage-7"
    content: "Stage 7: retire the global capacity and document the model"
    status: in_progress
  - id: "stage-2-plan"
    content: "Write the Stage 2 implementation plan"
    status: completed
  - id: "stage-3-plan"
    content: "Write the Stage 3 implementation plan"
    status: completed
  - id: "stage-4-plan"
    content: "Write the Stage 4 implementation plan"
    status: completed
  - id: "stage-5-plan"
    content: "Write the Stage 5 implementation plan"
    status: completed
  - id: "stage-6-plan"
    content: "Write the Stage 6 implementation plan"
    status: completed
  - id: "agree"
    content: "Agree the decisions and open questions"
    status: completed
  - id: "stage-1-plan"
    content: "Write the Stage 1 implementation plan"
    status: completed
isProject: true
---
# Plan: Compute resource pools and allocations

Date: 2026-11-14
Scope: `Calycopis-broker` — the compute entity tiers, the booking query and
offer factory, a new admin API for pools and allocations, the persistent model,
the Python and Java tests, and the documentation. Storage pools and a generic
pool abstraction are deferred to their own plans.
Issues: [uksrc/Calycopis-broker#72](https://github.com/uksrc/Calycopis-broker/issues/72) — Re-usable booking component;
[#32](https://github.com/uksrc/Calycopis-broker/issues/32) — ResourceAllocation entities;
[#199](https://github.com/uksrc/Calycopis-broker/issues/199) — Compute resource pools;
[#78](https://github.com/uksrc/Calycopis-broker/issues/78) — Storage resource pools (deferred).

This is the high-level plan. Each stage below gets its own implementation plan,
naming this document as its `Parent:`, and its own branch and pull request.

## Target

Every compute resource is allocated from a pool. A pool is an admin-created
entity holding two kinds of capacity record over time:

* **allocations** — capacity the platform makes available to the pool (supply);
* **reservations** — capacity held by a session whose offer came from that pool
  (demand).

For every pool, and every block of time, the sum of reservations must not
exceed the sum of allocations. Offers are generated per pool from that pool's
free space. Each pool carries its own acceptance policy, and the broker asks the
enabled pools in order, so the pools that bid are the ones whose policy claims
the request.

Success means a working, tested compute-pool path, with the global capacity
setting retired, and a shape that a storage pool plan can re-use as far as it
fits. It does not mean a generic resource framework, and it does not fix the
form that reuse takes.

## Where we are

* There is one implicit pool. `BookingCapacitySettings` supplies a flat total
  (256 cores / 256 GiB by default), `SimpleComputeResourceQuery` subtracts the
  reservation hull of the live sessions from it, and the offer factory runs one
  query for the whole broker.
* The per-session compute component is already a reservation in all but name:
  it stores the offered cores and memory plus the preparing, available and
  releasing windows that the query charges. `docs/booking.md` already says "an
  offer is a reservation".
* There is no pool, allocation or capacity-over-time entity, and no capacity
  history — the total is a constant.
* The only admin surface is `/admin/identities`, a hand-written
  `@RestController` guarded by the `ADMIN` authority. The OpenAPI schema in
  `Calycopis-openapi` has no pool types.
* Hibernate runs with `ddl-auto: create`, so the database is rebuilt on every
  start and a rename needs no migration.
* The issues point the same way. #72 is the reusable-booking parent, #32 and
  #199 define the allocation and pool direction, and #78 is the storage twin.
  #32 carries the useful idea (capacity rows over time); its field list and its
  "inverse bookings" framing are out of date and are not carried forward.

## Model and vocabulary

| Term | Meaning | Lifetime |
|---|---|---|
| Pool | A named, admin-managed source of one kind of resource, which carries its own acceptance policy and may be a third-party implementation. | Until an admin deletes it. |
| Allocation | Capacity added to a pool for a window of time. Admin-managed. | Its window. |
| Reservation | Capacity held for one session's compute resource. Created when an offer is made, held while `OFFERED`, kept for the session's life. | The reservation hull (preparing, available, releasing). |
| Offer | A candidate reservation returned to the client. It is already stored, and already holds capacity. | Until accepted, rejected or expired. |
| Policy | One of the pool's own rules, held as a `PoolPolicy` component and asked through `accepts(criteria)`; a pool holds a list of them. | Lives with the pool. |
| Free space | Allocations minus reservations, per pool, per block. | Computed, never stored. |

The schema calls this component a compute resource; the Java types are named
for what they hold. A reservation is the compute a session has booked, an
allocation is the compute granted to a pool, and a pool keeps "resource"
because it holds capacity rather than bookings. Adding "reservation" to the
old name without dropping "resource" would leave
`SimpleComputeResourceReservation`, which says the same thing twice.

```java
interface AbstractComputeResourcePool
    {
    Iterable<AbstractComputeReservation> getReservations();
    }
```

```java
interface SimpleComputeResourcePool
    {
    Iterable<SimpleComputeOffer> makeOffers(
        SimpleComputeResourceCriteria criteria,
        SimpleComputeReservationQueryHandler queryHandler
        );
    }
```

```java
interface SimpleComputeResourceCriteria
    {
    OfferSetRequestParserContext getRequest();
    AbstractComputeReservationValidator.Result getResource();
    int getOfferLimit();
    long getPrepareSeconds();
    }
```

The criteria carry the parsed request, the compute resource the pool is being
asked to bid for, and the offer limit, with convenience accessors for the start
interval and the duration. The query handler is a separate parameter: the
criteria describe the selection, and the handler is the functional engine that
runs the query. From Stage 6 the same criteria object is what each of the pool's
`PoolPolicy` components inspects in `accepts(criteria)` before any offer is
made, so a policy and the query cannot drift apart, and a policy never sees the
handler.

## Decisions

1. **Rename the existing compute component, do not add a parallel type.** What
   the session holds is a reservation, so the classes say so:
   `SimpleComputeReservation`, not `SimpleComputeResourceReservation`. The word
   "resource" is dropped where the type records a booking and kept where it
   means the thing being pooled or requested, so pools and criteria keep it. The public
   API keeps its discriminator kinds, bean types, paths and field names; only
   the internal names and the table names change.
2. **An allocation is an absolute capacity window, not a delta.** A pool's
   capacity at a moment is the sum of the allocations covering it. Overlapping
   allocations add up, and an allocation with no end is open ended. A pool has
   no separate total of its own, so there is one definition of supply. An
   allocation carries single cores and memory values, not the min/max range a
   reservation carries.
3. **Capacity is compared per block, per pool.** The existing 60-second grid
   and block rounding stay. A reservation charges its whole hull (preparing,
   available, releasing), as it does today; an allocation covers the blocks it
   touches.
4. **Pools are platform-neutral extension points.** They are not per-profile,
   and they do not encode the mock or docker platform. A pool is an ordinary
   entity whose behaviour a third party can override, and it carries its own
   acceptance policy (decision 8), so a new pool speciality is a new pool
   implementation rather than a change to the broker. The pool interfaces are
   therefore written as if they were public API, even though they are not
   published yet.
5. **Pools and allocations are hand-written and off-schema for now.** They are
   admin resources, like identities. The schema will eventually split into the
   IVOA service interface and a Calycopis extension carrying pools and
   allocations; that split is a separate plan, and no client needs the fields
   yet.
6. **The default pool and its initial allocation come from a configuration
   file.** A deployment may point at an optional `pools.yaml`, which defines
   the pools to seed and, once allocations exist, their initial allocations.
   With no file, a deployment keeps the current behaviour: a `default` pool
   with one open-ended allocation of the configured capacity. The seed runs at
   startup and is idempotent.
7. **Concrete first, generics later.** Implement `SimpleComputeResourcePool`
   and its allocation and offer types concretely. Extract
   `AbstractResourcePool<Allocation, Reservation, Offer, Criteria>` only once
   a second implementation has been written against this one.
8. **Pools carry their own policy; there is no central selector.** The enabled
   pools are an ordered list, and the offer factory asks each enabled pool in
   turn for offers. A pool keeps `makeOffers(criteria, queryHandler)` as its
   external interface and consults a list of `PoolPolicy` components inside it;
   a pool whose policies decline returns an empty list. Memory floors, owner
   groups and time-of-day windows live in those policies, so adding a
   speciality touches neither the factory, the pool body, nor a rules table.
9. **The offer limit is applied per pool, then again at the merge.** The limit
   is part of the criteria and is bound into each pool's query, so a pool
   returns at most that many offers. The factory then sorts the merged list by
   start time and caps it at the same limit, so the client never sees more
   offers than it asked for. Sort and cap is accepted as enough for now; a pool
   that claims the whole limit before later pools are asked is not
   implemented.
10. **An allocation is never shrunk below the live reservations.** Deleting or
    reducing an allocation that would leave its pool over-committed is
    rejected. There is no override option until a user asks for one.

## Validation and allocation

Pool-owned policy moves a decision from the validators to the pools, and that
has consequences worth recording.

What stays in `stageOne`: the request is well formed, the components are known
types, and the platform can support the resource at all. What moves to
`stageTwo`: whether *this pool* wants the request — its memory floor, the
owner's group, the time of day. A request can therefore be valid and still get
no offers, which is already the `result: NO` case in the schema.

The consequences:

* `valid()` means "well formed", not "we expect to serve it". Code must not
  treat a valid context as a promise of offers; `stageTwo` already sets `YES`
  only when offers exist.
* A pool that declines has to say why, or a `NO` response has no diagnosis. A
  declining pool records a message on the request context, and the existing
  message mechanism carries it to the response.
* Error text that today comes from a validator for a request no pool can serve
  moves to the pool's message, and tests that assert the old text move with it.
  The platform-limit validators (blacklists, absolute maxima) stay where they
  are.
* The parser no longer knows which pool will serve, so the totals and
  preparation spans it computes become input to each pool rather than to a
  single booking query.

The alternative — pool acceptance as extra validators in the `stageOne` chain —
was considered and is not recommended. The chain returns the first `ACCEPTED`
or `FAILED` validator and stops, so collecting a *set* of eligible pools needs
a second accumulation mechanism through the context; validation would then
depend on mutable pool state, and the reason a request was accepted would be
harder to trace than in a pool that returns its own offers. Keeping policy with
the pool costs a broad criteria interface, which we accept: `accepts(criteria)`
takes the same criteria as the offer query, a pool may read the compute results,
the owner and the schedule, must treat the rest of the context as read-only, and
may add messages.

This is the shape the stage plans assume. It is the one point from the review
that is a recommendation rather than an agreed answer, so it is worth settling
before Stage 5.

## Stages

| # | Stage | Size | Risk | Depends on | Behaviour change |
|---|---|---|---|---|---|
| 1 | Rename to reservations | L (mechanical) | Low | — | None |
| 2 | Pools and the admin API | M | Low | 1 | None |
| 3 | Allocations and capacity | M–L | Medium (SQL) | 2 | None, with the seeded allocation |
| 4 | Reservations join a pool | S–M | Low | 2 | None |
| 5 | The pool makes offers | M | Medium | 3, 4 | None, with one pool |
| 6 | Several pools, each with its policy | M | Medium (diagnostics) | 5 | Offers may come from several pools |
| 7 | Retire the global capacity | S | Low | 6 | Configuration only |

Each stage leaves `main` working, ships on its own, and has an equivalence or
regression check against the behaviour before it.

### Stage 1 — Rename the compute resources to compute reservations

**Goal.** The per-session compute component is named for what it is, leaving
room for allocations. No behaviour change.

**Changes.**
* Rename the three tiers in `engine/entities/compute/`: the interface, entity,
  the two factory types and the three validator types, plus the mock and docker
  tiers. `AbstractComputeResource*` becomes `AbstractComputeReservation*`, and
  `SimpleComputeResource*` becomes `SimpleComputeReservation*`. The validator
  types follow the tier even though they validate the request rather than the
  row.
* Rename the accessors and fields whose name is the type: `getComputeResource`
  becomes `getComputeReservation`, `setComputeResource` becomes
  `setComputeReservation`, and `computeResourceEntity` becomes
  `computeReservationEntity`. The platform wiring keeps its names, because it
  names the compute subsystem rather than the class.
* Rename the four JPA tables to match — `abstractcomputereservations`,
  `simplecomputereservations`, `mocksimplecomputereservations` and
  `dockersimplecomputereservations` — and the two identifiers inside
  `SimpleComputeResourceQuery.DATABASE_QUERY`. Update the state-test SQL in
  `tests/python/states/conftest.py`.
* Keep the booking names: `SimpleComputeResourceQuery`,
  `SimpleComputeResourceQueryHandler`, `SimpleComputeResourceOffer`,
  `SimpleComputeResourceOfferBean`, `SimpleComputeResourceOfferFactory`, the
  two Spring implementations, the two booking tests, and the platform's
  `getComputeResourceValidators` and `getComputeResourceOfferFactory`. They
  name the booking grid and the offer, which are not the entity, and Stage 5
  rewrites the query and `makeOffers` anyway.
* Update the data model in `AGENTS.md`, the class names in the validator
  messages, and the references in `docs/`.
* Add a javadoc line to the entity: the schema calls this a compute resource,
  and the kind URI keeps that word, while the row is the reservation. Leave the
  discriminator URIs, `WEBAPP_PATH`, the bean types, the response fields and
  the HTTP paths alone.

**Acceptance.**
* `./mvnw clean compile` is green and the Java unit tests pass, changed only
  where a name changed.
* The `any` and `mock` Python suites pass, and `docker` if the platform is
  available; a validated request still returns the same session shape and kind.
* No OpenAPI, response or configuration change. A grep of `java/src` for
  `AbstractComputeResource` and `SimpleComputeResource` returns only the
  booking classes listed above, and none of the four old table names appears
  anywhere.
* The change is one commit: a half-renamed tree does not compile.

**Out of scope.** The public API vocabulary, the storage resources, and the
`calycopis.broker.booking.capacity` keys.

**Risk.** Wide (69 files, about 590 occurrences) but mechanical. Do it with a
scripted rename and let the compiler find the misses; review the factory and
validator generics by hand.

### Stage 2 — Pools as entities, and the admin API

**Goal.** A pool is a persisted, admin-managed entity. Booking is untouched.

**Changes.**
* `AbstractComputeResourcePool` and `SimpleComputeResourcePool` interfaces,
  with entities, tables, factories and a repository, following the existing
  three-tier pattern. A pool carries a name and description through the
  component base, plus whatever the policy needs later (Stage 6).
* Admin endpoints `POST /admin/pools/compute` and `GET /admin/pools/compute`
  (and `GET /admin/pools/compute/{uuid}`), in the style of
  `AdminIdentityController`, with the `ADMIN` authority. The resource type is a
  path segment, so storage pools can sit at `/admin/pools/storage` later without
  moving an endpoint that already exists.
* A startup bootstrap that seeds the pools from the optional `pools.yaml`, with
  a `default` pool when the file says nothing, idempotently. A template goes in
  `config/` and the file is imported optionally alongside `admin.yaml`.

**Acceptance.**
* An admin can create a pool and list it; a non-admin gets `403`; a request
  without credentials gets `401`.
* The bootstrap is idempotent: run twice against the same database, it creates
  one pool. Under `ddl-auto: create` a restart starts from an empty database,
  so the default pool is created again — the pool table has no history to keep.
* Offer generation is unchanged: the pinned scenario in the Java unit tests
  produces the same offer rows as before this stage.
* The pool appears in the admin response with its UUID, and the entity is
  persisted with the component base fields.

**Out of scope.** Allocations, capacity, pool policy, and any published schema
type.

### Stage 3 — Allocations, and capacity read from them

**Goal.** A pool's capacity is a set of allocations over time, and the booking
query reads it from there. With the seeded default allocation, behaviour is
identical.

**Changes.**
* `AbstractComputeAllocation` and `SimpleComputeAllocation`, with entities, a
  table, factories and a repository. An allocation belongs to
  one pool and has a start, an end (absent means open ended), a number of cores
  and an amount of memory, with positive sizes and an end after its start.
* Admin endpoints `GET` and `POST` on
  `/admin/pools/compute/{uuid}/allocations` to list and create, and `GET`,
  `POST` and `DELETE` on `/admin/allocations/compute/{uuid}` to select, update
  and delete one allocation; a create redirects with `303` to the details. An
  update or delete that would leave its pool with less capacity than its live
  reservations in any block is rejected with `409 Conflict`.
* The booking query replaces the `:totalcores` and `:totalmemory` constants
  with an allocation-blocks common table expression, summed per block and
  scoped to the pool, alongside the existing `ExecutionBlocks`. Free space
  becomes allocated minus reserved.
* The bootstrap always seeds a `default` pool, and gives every seeded pool the
  allocations `pools.yaml` declares for it, or one open-ended allocation from
  the configured capacity when it declares none, so the two numbers cannot
  drift. An `enabled` flag, to disable a pool rather than delete it, is left to
  stage 6.

**Acceptance.**
* An equivalence test: the query built from an allocation equal to the old
  capacity returns the same offer rows as the previous query, against the same
  fixtures.
* Adding an allocation increases the offers; a short allocation stops
  contributing capacity after its end; an allocation that has not started does
  not contribute before its start (Java unit tests on the CTE).
* A Python test adds an allocation to the default pool through the admin API and
  observes the change in the offers. The request path still uses the default
  pool, so the allocation has to be added to it.
* Deleting an allocation that no reservation reaches succeeds; deleting one
  that would over-commit the pool is rejected and the allocation remains.
* No behaviour change for a deployment that configures only the old capacity
  setting.

**Risk.** The SQL is the substantial part. Overlapping and open-ended
allocations must aggregate to the same block grid as the reservations, and the
existing block rounding (`ceilBlocks`) is the reference. Keep the query
readable: one CTE for allocation blocks, one for reservation blocks, one join
that subtracts. The `409 Conflict` guard on delete is also check-then-act: it
reads the reservations, then removes capacity, so it has to hold the same
per-pool lock as a booking, or a concurrent request commits reservations
between the check and the delete.

### Stage 4 — Reservations join their pool

**Goal.** Every reservation records the pool its offer came from. Still one
pool on the request path.

**Changes.**
* A non-null `pool` reference on `AbstractComputeReservationEntity`,
  and the matching `reservations()` on the pool.
* The offer bean gains the pool, the offer factory stamps the default pool on
  every offer, and the reservation constructor takes the pool from the offer.

**Acceptance.**
* Every reservation, including an `OFFERED` one, points to the default pool;
  the foreign key is enforced.
* A pool's `reservations()` returns the live reservations, and only that
  pool's.
* The offer and session responses are unchanged, and the Python suites pass.
* A state test asserts the stored pool for a session's compute reservation.

**Out of scope.** Any change to how offers are generated or counted, and any
change to the API responses.

### Stage 5 — The pool makes the offers

**Goal.** `makeOffers(criteria, queryHandler)` on the pool; the booking query is
scoped to one pool; the request path still uses the default pool.

**Changes.**
* `SimpleComputeResourceCriteria`, carrying the request context, the compute
  resource being bid for, and the offer limit, with accessors for the start
  interval and duration. The context is what lets a pool look at the owner or
  the schedule in Stage 6, so the interface does not change then. The criteria
  do not carry the query handler.
* `makeOffers(SimpleComputeResourceCriteria criteria,
  SimpleComputeReservationQueryHandler queryHandler)` on `SimpleComputeResourcePool`
  only. The abstract pool keeps just the two lists until a second resource type
  shows what the offers and criteria have in common. A pool runs its own query,
  so pools may differ in capacity without the query knowing. The handler is a
  separate parameter because it is the functional engine, not part of the
  selection input.
* The query binds the limit from the criteria, so a pool returns no more offers
  than the request asked for.
* The query filters both the allocation and the reservation blocks by pool, and
  computes free space as that pool's allocated minus reserved.
* The offer factory becomes: select the pool (the default pool in this stage),
  call `makeOffers(criteria, queryHandler)`, post-process the offers as it does
  now.
* The booking classes take their final names here, while the query and the
  offer factory are being rewritten: `SimpleComputeReservationQuery` and its
  handler, `SimpleComputeOffer`, `SimpleComputeOfferBean` and
  `SimpleComputeOfferFactory`. Stage 1 leaves them alone so that one rename
  lands on code that is being rewritten anyway, rather than twice.

**Acceptance.**
* With one pool, the offer count, start times and sizes are identical to the
  previous behaviour.
* A unit test calls `makeOffers(criteria, queryHandler)` directly on two pools
  of different capacity, and shows each pool's offers depend only on its own
  allocations and reservations; capacity in one pool does not appear in the
  other.
* A pool returns no more than the limit in the criteria.
* An offer made from a pool creates its reservation in that pool.
* The offer post-processing (clamping to the requested start, dropping ended
  offers, the window filter) still applies once, to what the selected pool
  returns.

**Risk.** The limit is applied twice: inside each pool's query, and again when
the factory caps the merge. The two have to agree, or a pool is
under-represented in the result. With one pool the double application is
invisible, so the two-pool merge test in Stage 6 is what catches a mistake.

### Stage 6 — Several pools, each with its own policy

**Goal.** The enabled pools form an ordered list, each pool decides through its
own policies whether a request is its business, and the factory merges the
offers they return.

**Changes.**
* Order and availability on the pool entity — a `priority` and an enabled
  flag, seeded from `pools.yaml` — with an admin endpoint to change them, and a
  repository query that returns the enabled pools in order.
* The bootstrap keeps seeding the `default` pool even when `pools.yaml` names
  others, so a deployment always has a fallback. The worked examples name it
  explicitly with `enabled: false` so it does not bid for everything.
* `makeOffers(criteria, queryHandler)` stays the pool's external interface.
  Externally nothing changes: a caller asks the pool for offers and gets a list
  back, empty when the pool declines. There is no central selector table.
* Policy moves inside the pool. The stage defines a `PoolPolicy` interface with
  one method, `accepts(criteria)`, returning a `PoolPolicyResult` that carries
  an `ACCEPTED`/`DECLINED` enum and a list of reason messages, and a pool holds
  a list of one or more policy components. Before it makes any offers, the pool
  asks each of them whether the request is its business. A policy then holds
  the detail of one speciality — a memory floor, a memory ceiling, an owner, an
  hours window — while the pool body stays general, so the two are written,
  tested and reused separately.
* The plain pool keeps the permissive default: no policies, and it serves
  anything it has capacity for. The Stage 6 plan settles the result and reason
  types, all-of combination, and per-pool policy configuration rows
  administered through the pool's policy endpoints. A policy spec that fails
  validation disables its pool rather than leaving it permissive. The owner
  rule is an owner allowlist until identities carry groups.
* The offer factory iterates the ordered pools, collects their offers, sorts by
  start time and caps the combined list at the request limit. The per-pool limit
  still applies inside each query, so the merge sorts at most
  `pools × limit` rows.
* A declining pool records one message on the request context naming the policy
  that declined, then appends that policy's reason messages — one pool message
  per pool, not one per policy — so a `NO` result carries both the reason and
  where it came from.
* Worked examples in configuration and documentation: the generic and
  high-memory pair carrying memory floors, a floor/ceiling overlap pair, a
  deliberately non-overlapping floor/ceiling pair, and a special pool that only
  bids for a named owner during a time window.

**Acceptance.**
* A high-memory request is bid on by the high-memory pool and declined by the
  generic pool, with the floor configured on the pools and nothing about it in
  the factory.
* A small request is bid on by the generic pool and declined by the
  high-memory pool.
* Both pools bid when both consider the request theirs; the merged list is
  sorted by start time, capped at the request limit, and no pool is
  over-committed.
* A floor pool and a ceiling pool whose ranges overlap both bid; a
  deliberately non-overlapping floor/ceiling pair declines a request between
  them, and the `NO` response carries both pools' reason messages.
* A test-double pool carrying a `PoolPolicy` that accepts only a named owner
  takes part without any change to the offer factory or the pool body —
  the test that the policy really lives in the pool.
* A request no pool will serve returns `NO` with the pools' reasons among its
  messages.
* An end-to-end Python test creates two pools with different capacities, sets
  priority/enabled and creates, updates or removes a policy through the admin
  API, then checks routing, the limit and the capacity isolation.

**Risk.** Diagnostics. With policy spread across the pools, "why did I get
nothing" becomes a matter of collecting every pool's reason, so the message
convention is not optional. The pool interface and the policy interface are
both third-party extension points, so review them as an API before documenting
them, and before adding a pool type registry.

### Stage 7 — Retire the global capacity and document the model

**Goal.** Nothing in the booking path reads a global capacity, and the model is
written down.

Plan: [`20261118-03`](20261118-03-retire-the-global-capacity-and-document-the-model-d1dbcb9d.plan.md).

**Status.** Implemented on branch `20261118-zrq-retire-global-capacity` from
`main` at `dcbf4a1a`, the Stage 6 merge
([PR #215](https://github.com/uksrc/Calycopis-broker/pull/215)); the commit and
pull request await approval.

**Changes.**
* Remove the capacity from the booking path. `BookingCapacitySettings` and
  `BookingCapacityInfoContributor` shrink to the bootstrap, or go, and the
  `calycopis.broker.booking.capacity` section is replaced by the default entry
  in `pools.yaml` and its built-in fallback.
* Update `docs/booking.md` with a pools section, `AGENTS.md` with the data
  model and admin endpoints, and the configuration templates with a worked
  example.
* Open follow-up issues for storage pools (#78), the generic abstraction
  ([#216](https://github.com/uksrc/Calycopis-broker/issues/216)), and the
  schema split
  ([#217](https://github.com/uksrc/Calycopis-broker/issues/217)).

**Acceptance.**
* A fresh deployment with no `pools.yaml` still serves requests through the
  built-in default pool and allocation, and one with configured pools serves
  them from the pools' own policies.
* No code in the booking path reads a single global capacity.
* The documentation explains how to add a pool and an allocation, and what the
  invariant means.
* All Java unit tests and the `any`, `mock` and `docker` suites pass.

## Deferred

* **Storage and volume pools.** Tracked as
  [#78](https://github.com/uksrc/Calycopis-broker/issues/78). A future plan,
  which should re-use as much of this work as it can: the pool entity, the
  `/admin/pools` namespace, the allocation entity and the capacity-over-time
  query, with size in place of cores and memory. Whether that reuse is
  inheritance, a shared generic or a copy is that plan's decision, and this one
  does not pre-empt it — which is also why generics are left alone here. Name
  the storage reservation `SimpleStorageReservation` for the same reason, so the
  two families read alike.
* **Generic `AbstractResourcePool<...>`.** Tracked as
  [#216](https://github.com/uksrc/Calycopis-broker/issues/216). Extract only
  after storage works, and only if the two implementations agree on the shape.
  The offers and criteria already differ enough that a generic adds little now.
* **The schema split.** Tracked as
  [#217](https://github.com/uksrc/Calycopis-broker/issues/217). The schema
  becomes the IVOA service interface plus a Calycopis extension, with pools,
  allocations and the offer's pool in the extension. A plan of its own; until
  then pools stay admin-only and an offer does not say which pool it came from.
* **A published pool SPI.** The pool and policy interfaces are reviewed as API
  in Stage 6, but publishing them — a stable version, a documented example, a
  compatibility story — is a later plan, once storage has exercised the same
  shape.
* **Pool types and a factory registry.** A `kind` on the pool and a registry
  that lets an admin create a pool of a registered type, so a third-party pool
  implementation can be deployed without a broker change.
* **Multi-resource sessions.** A context may carry more than one compute
  resource. Bidding per resource, and whether resources in one session may come
  from different pools, is unresolved and is not needed by the current path.
* **Atomicity of the invariant.** The check-then-act race in #156 is closed
  today: the booking query and the reservations it produces commit in one
  transaction, holding a PostgreSQL advisory lock keyed on the resource type,
  so a concurrent request waits instead of booking the same free block. Pools
  move the key rather than the shape. The invariant is per pool, so the lock
  becomes per pool — which also lets two pools book concurrently — and the
  admin endpoints that create, shrink or delete an allocation must take the
  same lock, because the `409 Conflict` check is check-then-act against the
  reservations too. A database-enforced invariant, such as a per-block capacity
  ledger or an exclusion constraint, is the stronger answer and its own piece
  of work: a lock is a convention, and a path that forgets it breaks the
  invariant silently.
* **Allocation calendars.** Recurring allocations, or allocations that an
  admin books ahead, beyond a single window per allocation.
* **Deleting a pool.** Allowed only once it has no live reservations, and then
  with its allocations; the admin endpoint and the check are their own stage if
  the policy configuration does not settle it first.
* **Force-shrinking an allocation.** The override for decision 10, only if a
  user asks for it.
* **An exclusive or authoritative pool.** A pool that, when it bids, suppresses
  the other pools' offers for that request. The ordered list makes it possible,
  but no pool needs it yet.
* **Other capacity dimensions.** A GPU count, or any dimension beyond cores and
  memory, is a new field on the allocation and reservation, not a new pool
  type.

## Resolved on review (2026-11-14)

1. **Shrinking an allocation below live reservations.** Rejected by default,
   with no override until a user asks for one. Decision 10.
2. **Allocation sizes.** Single cores and memory values; the reservation keeps
   its min/max fields. Decision 2.
3. **The default pool bootstrap.** Kept, with the default pool and its initial
   allocation read from a configuration file. Decision 6.
4. **Pool identity beyond the name.** Pools become complex, extensible
   entities whose implementations carry their own specialities and policies.
   The answer is pool-owned policy rather than a kind field or a central
   selector; a type registry is deferred. Decisions 4 and 8.
5. **Pools in the OpenAPI schema.** Eventually yes, in a Calycopis extension
   beside the IVOA service interface. Deferred to its own plan; offers do not
   carry their pool until then.

## Resolved on review (2026-11-15)

6. **How a decline is reported.** One message per declining pool, naming the
   policy that declined. That is enough for now: the offer set does not yet
   distinguish "no pool accepts this request" from "no capacity now".
7. **The limit across pools.** Sort and cap is enough for now. Each pool
   applies the full request limit, and the merge sorts the combined list and
   caps it. A high-priority pool taking the whole limit first is not
   implemented.
8. **Where the order lives.** A `priority` field on the pool, seeded from
   `pools.yaml` at bootstrap and changeable through the admin API, so the
   deployment sets the initial order and an admin can reorder it afterwards.
9. **The pool interface as a published SPI.** Left to a later plan, once
   storage has exercised the same shape. Stage 6 reviews the interfaces as
   API; publishing them does not happen here.

## Status

status: in-progress
details: High-level plan written on 2026-11-14, revised the same day after
  review: pool-owned policy replaces the central selector, the offer limit
  moves into the criteria, and the default pool and allocation come from a
  configuration file. Revised again on 2026-11-15: the open questions are
  answered, `PoolPolicy` is a component inside the pool behind an unchanged
  `makeOffers`, the admin paths are namespaced by resource type, and storage
  pools are left to their own plan. Revised a third time on 2026-11-16: the
  rename drops the redundant "resource", so the reservation and allocation
  types are `SimpleComputeReservation` and `SimpleComputeAllocation` while
  pools and criteria keep the word. Revised on 2026-11-17 after the Stage 3
  review: an allocation is addressed by its own UUID under
  `/admin/allocations/compute`, while its collection stays under the pool, so
  no path carries more than one UUID; a create redirects with `303`, and the
  default pool is always seeded. Stages 1 and 2 are on
  `main`: the rename in
  [`de577717`](https://github.com/uksrc/Calycopis-broker/commit/de577717)
  (PR [#209](https://github.com/uksrc/Calycopis-broker/pull/209)) and the pools
  and admin API in
  [`e1111f8a`](https://github.com/uksrc/Calycopis-broker/commit/e1111f8a)
  (merged as `9e6c3a4e`, PR [#210](https://github.com/uksrc/Calycopis-broker/pull/210)).
  The Stage 3 plan is written, and its decisions were agreed on review on
  2026-11-17:
  [`20261117-01`](20261117-01-compute-pool-allocations-and-capacity-5daacf8e.plan.md).
  Stage 3 is merged on `main` as
  [`4f229cfd`](https://github.com/uksrc/Calycopis-broker/commit/4f229cfd3028dc4dd87522c1b661f4096fa84942)
  (PR [#212](https://github.com/uksrc/Calycopis-broker/pull/212)). The Stage 4
  decisions were agreed on review and implemented on branch
  `20261117-zrq-reservations-pool`:
  [`20261117-02`](20261117-02-compute-reservations-join-their-pool-9ea76fda.plan.md).
  It is merged on `main` as
  [`61ffa41a`](https://github.com/uksrc/Calycopis-broker/commit/61ffa41a6d669f680d81ee156c680c726cf89b65)
  (PR [#213](https://github.com/uksrc/Calycopis-broker/pull/213)). The Stage 5
  plan's decisions were agreed on review and implemented on branch
  `20261118-zrq-pool-offers`:
  [`20261118-01`](20261118-01-the-pool-makes-the-offers-24c65d91.plan.md).
  The query handler stays separate from the criteria, and `makeOffers` takes it
  as a second parameter. Stage 5 is merged on `main` as
  [`ccc20c47`](https://github.com/uksrc/Calycopis-broker/commit/ccc20c47acfb03578137d0619ca7281a7f5e52e9)
  (PR [#214](https://github.com/uksrc/Calycopis-broker/pull/214)). The Stage 6
  plan's decisions were agreed on review on 2026-11-18 and implemented on branch
  `20261118-zrq-several-pools`:
  [`20261118-02`](20261118-02-several-pools-each-with-its-own-policy-624702a2.plan.md).
  It keeps the always-seeded default, returns policy reasons with the decision,
  disables a pool whose policy configuration is invalid, and adds a memory
  ceiling policy alongside the floor. Stage 6 is merged on `main` as
  [`dcbf4a1a`](https://github.com/uksrc/Calycopis-broker/commit/dcbf4a1a)
  (PR [#215](https://github.com/uksrc/Calycopis-broker/pull/215)). The Stage 7
  plan was agreed on 2026-11-18 and implemented on branch
  `20261118-zrq-retire-global-capacity`:
  [`20261118-03`](20261118-03-retire-the-global-capacity-and-document-the-model-d1dbcb9d.plan.md).
  It removes the global capacity, keeps a 256/256 bootstrap fallback for a pool
  with no allocations, and moves the state-test configuration to one enabled
  pool's allocation. The commit and pull request await approval. Verification:
  Java 83 tests pass, the `docker` suite passes (56 tests), and the `any` and
  `mock` suites pass except for one pre-existing Jupyter notebook validator
  failure.
  Each stage gets its own implementation plan, with this document as its
  `Parent:`.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-14T17:25:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-14T18:43:00/2026-11-14T19:05:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 30,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-14T19:34:00/2026-11-14T19:41:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 10,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-14T19:47:00/2026-11-14T19:53:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-15T09:12:00/2026-11-15T09:20:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 10,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-15T09:28:00/2026-11-15T09:34:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 10,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-15T10:02:00/2026-11-15T10:11:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 15,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-15T10:24:00/2026-11-15T10:31:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 10,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-16T04:20:00/2026-11-16T04:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 10,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T14:27:00/2026-11-17T14:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 3,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T15:21:00/2026-11-17T15:35:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T15:45:00/2026-11-17T15:55:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 15,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-17T17:49:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T11:19:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T11:43:00",
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
      "value": 5,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T13:20:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T13:45:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 3,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T18:48:19",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 2,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T18:50:00/2026-11-18T20:30:00",
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
