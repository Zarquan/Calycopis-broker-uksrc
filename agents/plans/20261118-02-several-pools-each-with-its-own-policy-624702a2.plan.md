<!-- 624702a2-95c6-482f-99c9-f97ce37341bb -->
---
todos:
  - id: "branch"
    content: "Create a branch from main for the stage 6 work"
    status: pending
  - id: "pool-fields"
    content: "Add priority and enabled to the pool entity, repository, bootstrap and admin API"
    status: pending
  - id: "policy-model"
    content: "Add the PoolPolicy interface and the memory, owner and time-window policies"
    status: pending
  - id: "policy-admin"
    content: "Persist policy configuration and add the policy admin endpoints"
    status: pending
  - id: "pool-gate"
    content: "Make the pool ask its policies before it makes any offers"
    status: pending
  - id: "merge"
    content: "Iterate enabled pools, merge their offers, sort and cap at the request limit"
    status: pending
  - id: "messages"
    content: "Carry pool decline messages into the offer-set response"
    status: pending
  - id: "java-tests"
    content: "Add policy, pool-gate, merge and diagnostics unit tests"
    status: pending
  - id: "python-tests"
    content: "Add the end-to-end admin policy and routing test"
    status: pending
  - id: "docs"
    content: "Update pools.yaml, AGENTS.md, docs/booking.md and the Stage 6 plan"
    status: pending
  - id: "build"
    content: "Build and run the Java unit tests"
    status: pending
  - id: "suites"
    content: "Run the broker and the Python suites"
    status: pending
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: pending
isProject: false
---
# Plan: Several pools, each with its own policy

Date: 2026-11-18
Scope: `Calycopis-broker` — pool ordering and availability, pool-owned
  acceptance policies, policy persistence and administration, the merged offer
  path, diagnostics, tests and documentation.
Parent: [`20261114-03`](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md)
  — Compute resource pools and allocations, stage 6.
Issues: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) — Compute resource pools;
[#32](https://github.com/uksrc/Calycopis-broker/issues/32) — ResourceAllocation entities;
[#72](https://github.com/uksrc/Calycopis-broker/issues/72) — Re-usable booking component.
Branch: `20261118-zrq-several-pools`, to be created from `main` at `ccc20c47`.
Result: not started.

## Target

Every enabled compute pool is asked whether a request is its business, and the
pools that accept contribute offers. The offer factory walks the enabled pools
in priority order, collects what each one returns, post-processes the combined
list once, sorts it by start time and caps it at the request limit.

The pool stays the only offer-generation entry point:
`makeOffers(criteria, queryHandler)` is unchanged. Policy lives inside the pool
and is consulted before the query runs. A pool with no policies is permissive
and serves anything its capacity allows. A pool with policies accepts only when
every policy accepts; the first policy to decline stops the pool, and the pool
adds one message naming itself and that policy, followed by the policy's own
reason messages.

Pools become administrable in the small ways this needs: an integer priority,
an enabled flag, and a list of policy configurations. The startup seed and the
admin API can both set them. The published offer, session and pool API bodies
do not gain a schema type; the admin bodies stay hand-written.

## Where we are

* `main` is at `ccc20c47`, the merge of Stage 5
  ([PR #214](https://github.com/uksrc/Calycopis-broker/pull/214)). A pool already
  scopes its booking query to itself, counts only its own live reservations,
  stamps its offers and returns them through `makeOffers`.
* `SimpleComputeOfferFactoryImpl.generate` resolves the one pool named
  `default`, builds the criteria, calls `makeOffers`, applies the testing delay,
  post-processes the offers and returns them. It has no merge, sort or cap step.
* `SimpleComputeResourcePoolEntity` has a name, a description and its live
  reservations. It has no priority, enabled flag or policy list.
  `SimpleComputeResourcePoolEntityFactory` can list every pool, not only enabled
  pools, and in no defined order.
* `ComputePoolBootstrap` always seeds `default`, even when `pools.yaml` names
  other pools. It seeds allocations but no priority, enabled flag or policies.
* `OfferSetRequestParserImpl` claims the context messages onto the offer set
  before it calls `generate`. A message added by a pool during generation is
  therefore not transferred to the response today.
* `config/pools.yaml` declares only the default pool and its optional
  allocations. The admin pool API can create, list and select pools, but cannot
  change them and reports no priority or enabled flag.
* Identities carry a username, issuer and display name. They have no group or
  group membership model.

## Decisions

1. **Lower priority number means earlier.** A pool has a non-null integer
   `priority`, default `0`, and a non-null boolean `enabled`, default `true`.
   The enabled list is ordered by priority ascending, then name ascending, then
   UUID ascending so the order is deterministic. A disabled pool is still
   visible to the admin API but is not asked for offers. This is the usual
   "lower number wins" convention and keeps the default pool at priority `0`
   when a deployment says nothing.

2. **The bootstrap keeps seeding `default`, even when `pools.yaml` names other
   pools.** A fresh deployment therefore always has a pool to fall back on, and
   the current Stage 3 behaviour is unchanged. The default pool is appended
   enabled at priority `0` with no policies when the configuration does not
   name it. The worked examples name it explicitly with `enabled: false` and a
   late priority so it does not bid for everything; the documentation warns
   that adding pools to an existing deployment must disable the default if it
   should not take part. Stage 7 can revisit the fallback.

3. **Policy is an all-of gate over the criteria, and it returns its reasons.**
   `PoolPolicy` has one decision method,
   `accepts(SimpleComputeResourceCriteria criteria)`, returning a
   `PoolPolicyResult`, plus `getName()` for diagnostics. The result carries a
   `PoolPolicyResultEnum` (`ACCEPTED` or `DECLINED`) and
   `List<Message> getReasons()`. A pool with no policies accepts. A pool with
   policies accepts only when every policy accepts; evaluation stops at the
   first `DECLINED`. The criteria is read-only to a policy, and a policy never
   sees the query handler. This keeps the policy input exactly the selection
   input from Stage 5 and lets each policy explain its own decision.

4. **A decline records one pool message followed by the policy's reasons.**
   When a policy declines, the pool adds one `INFO` message to the request
   context with type `urn:compute-pool-declined` and template
   `Compute pool [${pool}] declined: policy [${policy}]`, with the pool name and
   policy name as values. It then appends every message in the result's
   `getReasons()` unchanged. It returns an empty offer list without running the
   query. Only the first declining policy is consulted, so there is one
   `urn:compute-pool-declined` message per pool plus that policy's explanation.
   The message level is `INFO` because a pool declining is a routing decision
   rather than an error.

5. **Policy configuration is persisted per pool, and policy behaviour stays in
   code.** A `SimpleComputePoolPolicyEntity` belongs to one pool and holds its
   kind, name and typed parameters. It is configuration, not a selector: the
   pool owns its rows, and there is no central table mapping requests to pools.
   A plain policy object is built from each row, so the memory floor and ceiling,
   owner and time-window logic lives in separate `PoolPolicy` implementations and
   the pool body only loops over them. This lets the admin API create, list, update and
   delete a pool's rules without putting policy code in the database, and keeps
   a new policy speciality out of the pool body and the offer factory.

6. **The first four policy kinds are memory floor, memory ceiling, owner and
   time window.**
   * **Memory floor** accepts when the request's total minimum memory is at
     least the configured GiB floor.
   * **Memory ceiling** accepts when the request's total minimum memory is at
     most the configured GiB ceiling.
   * **Owner** accepts when the request owner's username equals the configured
     username. The parent plan calls this an owner group; identities have no
     group field today, so this stage implements an owner allowlist and records
     groups as a follow-up rather than inventing a half-group model.
   * **Time window** accepts when the requested start time falls inside a
     configured UTC window: start hour inclusive, end hour exclusive,
     `0 <= startHour < endHour <= 24`, no overnight wrap in this stage. If the
     request has no start time, the policy uses the current time, matching the
     query's default. An invalid window is rejected by the admin API and by the
     bootstrap.
   A floor and a ceiling are separate policy kinds, so a pool can carry either
   or both. The worked examples use one pool with a floor and another with a
   ceiling; where the ranges overlap both pools bid, and a deliberately
   misconfigured pair whose ranges do not overlap declines a request between
   them.

7. **The merge runs after every enabled pool has been asked.** The factory builds
   the criteria once, asks every enabled pool in order, and collects raw offers.
   It applies `hasEnded`, `clamp` and `startsOutsideWindow` once to the combined
   list, sorts what survives by `getOfferStartInstant()` ascending, and caps it
   at the effective request limit. The sort is stable, so offers with the same
   start keep pool order and query order. The per-pool query limit stays; a pool
   still returns at most the limit, and the merge sees at most
   `pools × limit` rows before filtering.

8. **The merge uses the query's effective limit.** The query treats a
   non-positive request limit as `SimpleComputeReservationQuery.QUERY_LIMIT`
   (4). The merge cap has to use the same value, or a request that asks for no
   limit would query four offers per pool and then cap the merged list at zero.
   The factory resolves the effective limit through the same helper/constant the
   query uses and uses it for the cap. The criteria keeps the request's limit;
   the query's fallback remains where it is.

9. **The testing delay stays once, between the queries and the save.** With
   several pools, the delay is applied after the last pool query and before
   post-processing and session construction, so the atomicity-race window is
   still one window and does not multiply by pool count.

10. **Pool messages are claimed after generation.** `OfferSetRequestParserImpl`
    moves `claimMessages(offersetContext.getMessages())` from before
    `generate` to after it, so validation messages and pool decline messages are
    all transferred once. This is a deliberate fix to the current ordering; it
    is what makes a `NO` response carry the pools' reasons.

11. **The bootstrap seeds policy configuration from `pools.yaml`, and an invalid
    policy disables its pool.** A pool spec gains `priority`, `enabled` and a
    list of policy specs. The seed creates the pool, then its allocations and
    policies, idempotently. It validates every policy spec; if any one cannot be
    validated, it logs the reason, skips that policy and sets the pool to
    `enabled: false` (or leaves it disabled when it already exists). This is
    deliberate: a typo must not leave a pool with no valid policies, which would
    make it permissive and accept everything. The admin fixes the policy and
    re-enables the pool once the configuration is correct. The default pool,
    when it is seeded by fallback, gets priority `0`, enabled `true` and no
    policies.

12. **The admin API grows two small surfaces, both off-schema.** Pool update
    changes `priority` and `enabled` only, at
    `POST /admin/pools/compute/{uuid}`. Policy management is
    `GET` and `POST /admin/pools/compute/{uuid}/policies` for the collection,
    `GET`, `POST` and `DELETE /admin/policies/compute/{uuid}` for one policy,
    following the allocation split that avoids two UUIDs in one path. A policy
    is created or updated with a `kind` and the fields that kind needs; the
    controller validates the combination. The pool response gains `priority`
    and `enabled`; the policy response carries its UUID, kind, name and
    parameters. An update may change the kind and all its parameters, which is
    what lets a test change a rule without deleting it.

## Policy model

The engine package is `engine/entities/compute/pool/policy/`.

| Type | Role |
|---|---|
| `PoolPolicy` | Interface with `accepts(criteria)`, returning `PoolPolicyResult`, and `getName()` for diagnostics. |
| `PoolPolicyResult` | Interface with `PoolPolicyResultEnum getResult()` and `List<Message> getReasons()`. |
| `PoolPolicyResultBean` | Immutable result with `accepted()` and `declined(List<Message>)` factories. |
| `PoolPolicyResultEnum` | `ACCEPTED` and `DECLINED`. |
| `MemoryFloorPolicy` | Accepts when `criteria.getMemory()` is at least the floor. |
| `MemoryCeilingPolicy` | Accepts when `criteria.getMemory()` is at most the ceiling. |
| `OwnerPolicy` | Accepts when the request owner's username matches. |
| `TimeWindowPolicy` | Accepts when the requested start hour is in the UTC window. |
| `MessageBean` | Plain `Message` for a policy reason, with no persistence parent until claimed. |
| `SimpleComputePoolPolicyEntity` | JPA entity, owned by a pool, holding the kind, name and typed parameters. It builds the matching policy object; it does not decide by itself. |

The entity fields are `kind` (an enum), `name`, `minimumMemory` (nullable),
`maximumMemory` (nullable), `username` (nullable), `startHour` and `endHour`
(nullable). A policy row is valid only when the fields for its kind are present
and the fields for other kinds are absent; the admin controller and the
bootstrap enforce that. The pool holds its policy rows through
`@OneToMany(mappedBy = "pool", cascade = ALL, orphanRemoval = true)`, ordered by
creation so a decline message is stable.

The policy objects are plain classes with no Spring dependency. The entity
creates one on demand, which keeps policy logic testable without persistence and
keeps the pool body free of policy detail. A declining policy builds its
`MessageBean` reasons with the condition and the configured limit already in the
template, so the response explains why the pool declined rather than only which
policy did.

The request context gains `addMessage(Message)` and `addMessages(List<Message>)`.
They keep a policy's reason as a plain message until the offer set claims it.
`OfferSetEntity.claimMessages` gains a branch that creates a `MessageEntity` for
a plain `Message`, with the offer set as parent; a `MessageEntity` keeps the
existing behaviour. The pool does not have to manufacture a template string
from a reason it did not build.

## Merge model

```
criteria = factory criteria(request, effective limit)
for pool in poolFactory.listEnabled():
    offers += pool.makeOffers(criteria, queryHandler)
delay once if testing enabled
offers = postProcess(offers)       // ended, clamp, window filter
sort offers by start time
cap offers at effective limit
return offers
```

The pool's `makeOffers` becomes:

```
for policy in policies:
    result = policy.accepts(criteria)
    if result.getResult() == DECLINED:
        request.addInfo("urn:compute-pool-declined", ...)
        request.addMessages(result.getReasons())
        return empty
build query, run handler, stamp offers, return
```

No query is run for a declining pool, so a policy cannot consume capacity or
leave a partial result behind.

## Implementation outline

### Pool fields and repository

* Add `priority` and `enabled` columns to
  `SimpleComputeResourcePoolEntity` (or its abstract base, so a second pool type
  inherits them). Give them defaults at construction and keep them non-null.
* Add `getPriority`, `setPriority`, `isEnabled`, `setEnabled` to the entity and
  the pool interface where they belong. The pool interface is an extension
  point, so keep the accessors there only if a caller needs them; the factory
  and admin controller do.
* Extend `SimpleComputeResourcePoolEntityFactory` with
  `listEnabled()` and an update method for priority/enabled. The repository adds
  `findByEnabledTrueOrderByPriorityAscNameAscUuidAsc`; the factory maps it to a
  list. Keep `list()` for the admin view of all pools.
* Extend the response record with `priority` and `enabled`, and add a
  `ComputePoolUpdateRequest` with nullable `priority` and `enabled` so an
  omitted field is unchanged. Reject a negative priority and an update with no
  fields as `400`.

### Policy persistence and admin API

* Add `SimpleComputePoolPolicyEntity`, its repository (engine interface plus
  Spring Data interface), a factory with create/list/update/delete/select, and
  Spring wiring in `spring/pools/`.
* Add request/response records in `spring/webapp/`:
  `ComputePolicyRequest(kind, name, minimumMemory, maximumMemory, username,
  startHour, endHour)` and `ComputePolicyResponse(uuid, kind, name,
  minimumMemory, maximumMemory, username, startHour, endHour)`.
* Add `AdminComputePoolPoliciesController` for the collection and
  `AdminComputePolicyController` for select, update and delete, with the `ADMIN`
  authority already enforced by `/admin/**`.
* Add `ProblemType` entries for an invalid policy, a policy name already in use
  and a missing policy.
* Validate: kind known; name required and unique within the pool; memory floor
  and ceiling positive; owner username non-blank; time window in range and not
  overnight.
* Add policy specs to `ComputePoolSpec`, and seed policies after allocations in
  `ComputePoolBootstrap`. If any spec is invalid, log it, skip the bad spec and
  disable the pool rather than leaving it with no valid policies.

### Pool policy gate

* Add `PoolPolicy`, `PoolPolicyResult`, `PoolPolicyResultBean`,
  `PoolPolicyResultEnum` and the four policy implementations, plus `MessageBean`
  for reasons.
* Extend `OfferSetRequestParserContext` with `addMessage(Message)` and
  `addMessages(List<Message>)`, and have the offer-set claim turn each reason
  into a `MessageEntity`.
* Add the policy list to the pool entity and a read-only `getPolicies()` view.
* In `SimpleComputeResourcePoolEntity.makeOffers`, ask each policy before
  building the query. On `DECLINED`, add `urn:compute-pool-declined`, append the
  policy reasons and return an empty list.
* Keep the existing query and stamping path untouched for accepted pools.

### Factory merge and parser messages

* Replace the default-pool lookup with `poolFactory.listEnabled()`.
* Build the criteria once, collect offers from every pool, then apply the
  testing delay once.
* Move the post-processing loop out of the single-pool path and apply it to the
  merged list, then sort and cap.
* If there are no enabled pools, add one `WARN` message
  (`urn:no-enabled-compute-pools`) to the request context and return an empty
  list.
* Move `claimMessages` after `generate` in `OfferSetRequestParserImpl`. Do not
  claim twice; the context already owns the validation messages until that
  point. Add the plain-message branch to `OfferSetEntity.claimMessages`.
* Preserve the existing `NO`/`YES` result logic: `YES` only when at least one
  session is built.

### Tests

* Add policy unit tests for memory-floor and memory-ceiling boundaries, owner
  mismatch, UTC window start/end boundaries, and invalid configuration
  rejection.
* Add the overlap and gap cases: a floor pool and a ceiling pool whose ranges
  overlap both return offers; a floor pool and a ceiling pool whose ranges do
  not overlap both decline a request between them, with reason messages.
* Add a pool-gate test: a test pool with an accepting policy runs the query and
  stamps offers; a declining policy leaves the handler uncalled, adds exactly
  one pool message naming the pool and policy, and appends the policy's reason
  messages; a pool with no policies runs the query.
* Add a test-double pool with an owner policy and assert the offer factory needs
  no change to use it.
* Rewrite the factory test stubs around `listEnabled()`. Cover: two pools in
  priority order; both bid and the merged list is sorted by start; the cap is
  applied after filtering; a declined pool contributes no offers and no query;
  the effective limit fallback; post-processing once; testing delay once.
* Add a parser/offerset test that a `NO` result carries the pool decline
  messages, proving the claim order is right.
* Add `tests/python/any/test_admin_pool_policies.py` or extend the existing pool
  test: create two pools with different allocations, set priority/enabled,
  create a memory-floor and a memory-ceiling policy through the admin API,
  update one of them, submit matching and non-matching requests, and assert
  routing, the merged limit and capacity isolation. Include the overlap case
  where both pools bid and the gap case where both decline.
* Keep the one-pool equivalence check: with a single enabled default pool and
  no policies, the Python `any` and `mock` suites produce the same offer counts,
  starts and sizes as before.

### Documentation and configuration

* Update `config/pools.yaml` with the worked examples: a disabled default pool,
  a generic pool and a high-memory pool with memory floors, a floor/ceiling
  overlap pair, and a special pool with an owner policy and a UTC time window.
  Show `priority`, `enabled`, `policies` and allocations. Explain that `default`
  is always seeded and the examples disable it explicitly.
* Update `docs/booking.md` with a "Several pools" section: policy gate,
  all-of combination, policy reasons, ordered pools, merge/sort/cap, effective
  limit, decline messages and the owner-group limitation.
* Update `AGENTS.md` with the pool fields, policy model, admin endpoints, the
  enabled ordering and the merged offer path.
* Update `docs/deployment.md` for the new `pools.yaml` fields, the
  always-seeded default and the invalid-policy-disables-pool behaviour.
* Record the policy interface and `PoolPolicy` as extension points to review
  before a pool type registry or storage pools reuse them.

## Acceptance

* A request whose memory is at or above the high-memory floor is bid on by the
  high-memory pool and declined by the generic pool, with the floor configured
  on the pools and nothing about it in the factory.
* A small request is bid on by the generic pool and declined by the high-memory
  pool.
* A pool with a floor and a pool with a ceiling whose ranges overlap both bid
  and both appear in the merged list; a deliberately misconfigured floor/ceiling
  pair whose ranges do not overlap declines a request between them, and the
  `NO` response carries both pool decline messages and their reasons.
* When both pools accept, the merged list is sorted by start time and capped at
  the effective request limit; each pool stays within its own capacity and no
  reservation is charged to another pool.
* A test-double pool carrying an owner policy takes part without a change to the
  offer factory or the pool body.
* A request no enabled pool will serve returns `NO` with the pool decline
  messages in the response. A request with no enabled pools returns `NO` with
  the no-enabled-pools message.
* The admin API can update priority/enabled, create/list/update/delete policies,
  and rejects invalid policy combinations with `400` and duplicate names with
  `409`.
* A bad policy in `pools.yaml` leaves its pool disabled rather than permissive;
  fixing the policy and re-enabling the pool restores it.
* The Java unit tests pass; the Python `any`, `mock` and `states` suites pass
  apart from the known `test_valid_notebook_location` failure; the new
  end-to-end policy test passes.
* No OpenAPI schema, offer response, session response or public pool response
  changes. The admin records stay off-schema.

## Out of scope

* Identity groups and group membership. The owner policy uses the owner
  username until groups have their own model and admin surface.
* Policy history, versioning and audit beyond the current row's timestamps.
* A pool type registry, a published pool SPI, storage pools and volume pools.
* Early stopping when a high-priority pool fills the limit; every enabled pool
  is still asked, then the merged list is sorted and capped.
* A policy schema in `Calycopis-openapi`, a policy UI, or policy diagnostics
  beyond the pool decline message and the policy's reason messages.
* Retiring `calycopis.broker.booking.capacity`; Stage 7 owns that.

## Review decisions

Reviewed and agreed on 2026-11-18:

1. **Policy storage.** Persist per-pool policy rows and expose create, list,
   update and delete through the admin API, so tests can add, remove and change
   rules without restarting the broker.
2. **Owner group.** Use the owner username allowlist in this stage, and record
   identity groups as a follow-up. The owner policy does not pretend to have a
   group model.
3. **Bootstrap default.** Keep the current always-default behaviour. The worked
   examples name the default pool explicitly with `enabled: false` so it does
   not bid for everything.
4. **Policy interface.** `accepts(criteria)` returns a `PoolPolicyResult` with
   the `ACCEPTED`/`DECLINED` enum and a list of reason messages. The pool adds
   its `urn:compute-pool-declined` message and then the policy's reasons.
5. **Invalid configuration.** A policy spec that fails validation disables its
   pool rather than being silently skipped. This prevents a typo from leaving a
   pool with no valid policies and making it permissive.
6. **Memory ceiling.** Add a memory-ceiling policy to complement the memory
   floor, with overlap and non-overlap routing tests.

## Status

status: implemented
details: Written on 2026-11-18 against `main` at `ccc20c47`, the Stage 5 merge.
  Reviewed and approved on 2026-11-18 with these changes: policy results carry
  reasons; invalid policy configuration disables its pool; the bootstrap keeps
  the always-default behaviour; admin policy update is included; a memory
  ceiling policy is added with overlap and gap tests. Implemented on branch
  `20261118-zrq-several-pools`. The Java suite passes (85 tests, 0 failures).
  The Python `any` and `mock` suites pass apart from the known
  `test_valid_notebook_location` failure (151 passed, 1 failed), and the
  `states` suite passes (6 passed, 3 skipped). The new admin policy and
  policy-routing tests pass. Not yet committed or merged.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-18T13:20:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T13:20:00/2026-11-18T13:45:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 15,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
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
