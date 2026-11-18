<!-- d1dbcb9d-363c-4927-ad24-44964fc05ac6 -->
---
todos:
  - id: "branch"
    content: "Create a branch from main at dcbf4a1a for the stage 7 work"
    status: completed
  - id: "fallback"
    content: "Move the default allocation fallback into ComputePoolBootstrap"
    status: completed
  - id: "remove-capacity"
    content: "Remove the booking capacity settings, info contributor and capacity configuration"
    status: completed
  - id: "java-tests"
    content: "Update the bootstrap tests and remove the capacity settings tests"
    status: completed
  - id: "python-tests"
    content: "Update the state tests to read one enabled pool's allocation through the admin API"
    status: completed
  - id: "docs"
    content: "Update booking and deployment docs, AGENTS.md and pools.yaml"
    status: completed
  - id: "follow-ups"
    content: "Refresh the storage pool issue and open the generic abstraction and schema split issues"
    status: completed
  - id: "verify"
    content: "Run the Java, any, mock and docker suites"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Retire the global capacity and document the model

Date: 2026-11-18
Scope: `Calycopis-broker` — remove the global booking capacity setting, make the
  bootstrap fallback the only built-in allocation source, update the model
  documentation and the tests that depend on the actuator info endpoint, and
  open the remaining follow-up issues.
Parent: [`20261114-03`](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md)
  — Compute resource pools and allocations, stage 7.
Issues: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) — Compute resource pools;
[#78](https://github.com/uksrc/Calycopis-broker/issues/78) — Storage resource pools;
[#216](https://github.com/uksrc/Calycopis-broker/issues/216) — Generic resource pool abstraction;
[#217](https://github.com/uksrc/Calycopis-broker/issues/217) — Schema split.
Branch: `20261118-zrq-retire-global-capacity`, created from `main` at `dcbf4a1a`.
Result: implemented and verified (the `docker` suite passes; one pre-existing
  `any` Jupyter notebook failure remains); commit `1b64b139` and pull request
  [#218](https://github.com/uksrc/Calycopis-broker/pull/218) are open.

## Target

Nothing in the booking path reads a single global capacity. A pool's supply is
the sum of its own allocations, and a pool with no allocations gets the built-in
fallback allocation from the bootstrap: 256 cores and 256 GiB, open ended.

There is only one way to replace that fallback: declare an allocation for the
pool in `pools.yaml`. There is no separate `calycopis.broker.booking.capacity`
setting, no `BookingCapacitySettings` component and no actuator info detail that
advertises a global total.

The documentation explains the completed model: pools own policies and
allocations, the bootstrap seeds the default pool, and an administrator adds or
changes pools, allocations and policies through the admin API. The remaining
follow-up work — storage pools, the generic pool abstraction and the schema
split — is recorded as issues rather than left implicit in this plan.

## Where we are

* `main` is at `dcbf4a1a`, the merge of Stage 6
  ([PR #215](https://github.com/uksrc/Calycopis-broker/pull/215)).
* `SimpleComputeReservationQuery` already reads supply from the pool's
  allocations, scoped by pool UUID. The booking query itself does not read
  `BookingCapacitySettings`.
* `ComputePoolBootstrap` is the only production consumer of
  `BookingCapacitySettings`. When a pool declares no allocations, it seeds one
  open-ended allocation from the configured capacity.
* `BookingCapacitySettingsImpl` binds `calycopis.broker.booking.capacity` from
  `application.yaml`, and `BookingCapacityInfoContributor` publishes it as
  `booking.capacity` on `/actuator/info`.
* `application.yaml` still declares `capacity.cores: 256` and
  `capacity.memory: 256`.
* `config/pools.yaml` says a pool without allocations gets its capacity from the
  booking capacity section of `spring.yaml`.
* `docs/booking.md` and `docs/deployment.md` describe the same fallback, and the
  deployment guide configures the atomicity race broker with
  `calycopis.broker.booking.capacity` rather than a pool allocation.
* `tests/python/states/test_atomicity_race.py` and
  `test_reservation_window.py` read `booking.capacity` from `/actuator/info` to
  decide whether the broker is configured with one core and one GiB.
* The default pool is always seeded. With no `pools.yaml`, it is enabled at
  priority zero and has no declared allocations, so it currently relies on the
  global capacity setting for its fallback.
* The parent plan's Stage 7 also asks for follow-up issues: storage pools (#78
  already exists), the generic pool abstraction and the schema split.

## Decisions

1. **The bootstrap owns the fallback, and only the bootstrap.** Add
   `DEFAULT_ALLOCATION_CORES = 256L` and `DEFAULT_ALLOCATION_MEMORY = 256L` to
   `ComputePoolBootstrap`. `seedAllocations` uses them when a pool declares no
   allocations. The fallback is per pool, not a global total, and it is an
   allocation like any other: it can be replaced by declaring an allocation, and
   an admin can later add, change or delete allocations through the admin API.

2. **Remove the capacity settings entirely.** Delete
   `engine/functional/booking/BookingCapacitySettings`,
   `spring/booking/BookingCapacitySettingsImpl` and
   `spring/booking/BookingCapacityInfoContributor`, along with their tests.
   Remove the `calycopis.broker.booking.capacity` section from
   `application.yaml`. The `engine/functional/booking` package keeps
   `BookingTransactionHandler`, the offer abstractions and the compute query;
   the `spring/booking` package keeps the transaction handler.

3. **The actuator info endpoint stops advertising a global capacity.** The
   Python state tests will read the one enabled pool's allocation through the
   admin API instead. The testing details on `/actuator/info` remain unchanged,
   so the tests still use them to check the testing switch and the
   booking-query-to-save delay.

4. **The race configuration is a pool configuration.** The deployment guide's
   dedicated race broker gets a `pools.yaml` that disables `default` and enables
   one pool with one open-ended allocation of 1 core and 1 GiB. This replaces
   the `calycopis.broker.booking.capacity` entries in `spring.yaml`. The default
   pool must be disabled explicitly, or it would bid with the 256/256 fallback
   and make the race unobservable.

5. **The state tests verify the pool, not a global number.** Add a small shared
   admin helper to `tests/python/conftest.py`, and use it from both state tests
   to list the enabled pools and their allocations. The tests skip when the
   broker does not have exactly one enabled pool with one open-ended 1-core,
   1-GiB allocation. They do not assume a pool name.

6. **The template documents the fallback and shows an explicit default
   allocation.** Add a 256-core, 256-GiB allocation to the `default` entry in
   `config/pools.yaml`, keeping `default` disabled in the worked example. A pool
   with no allocation still gets the built-in fallback, but the template now
   shows the replacement for the old global number.

7. **Follow-up issues are opened during implementation, not left in the plan.**
   Confirm and refresh #78 for storage pools. Open one issue for extracting a
   generic resource-pool abstraction once storage exercises the same shape, and
   one for splitting pools, allocations and the offer's pool into a Calycopis
   schema extension beside the IVOA service interface. Link all three from the
   parent plan's deferred section.

## Changes

### Java

* Delete:
  * `java/src/main/java/net/ivoa/calycopis/broker/engine/functional/booking/BookingCapacitySettings.java`
  * `java/src/main/java/net/ivoa/calycopis/broker/spring/booking/BookingCapacitySettingsImpl.java`
  * `java/src/main/java/net/ivoa/calycopis/broker/spring/booking/BookingCapacityInfoContributor.java`
  * `java/src/test/java/net/ivoa/calycopis/broker/spring/booking/BookingCapacitySettingsImplTest.java`
  * `java/src/test/java/net/ivoa/calycopis/broker/spring/booking/BookingCapacityInfoContributorTest.java`
* `ComputePoolBootstrap`:
  * remove the `BookingCapacitySettings` field and constructor parameter;
  * add the two fallback constants;
  * use them in `seedAllocations` when the declaration is null or empty;
  * update the class comment to say the fallback is the bootstrap's own
    allocation, not the old global setting.
* `ComputePoolBootstrapTest`:
  * remove the `BookingCapacitySettings` import and the `capacityOf()` helper;
  * assert the fallback allocation uses the bootstrap constants;
  * keep the existing declared-allocation, policy and idempotence tests;
  * add a test that an explicitly declared allocation wins over the fallback,
    including for a `default` pool that declares one.
* `application.yaml`: remove the whole `booking.capacity` block and its comment.

### Configuration

* `config/pools.yaml`:
  * replace the "from the booking capacity section of spring.yaml" comment with
    the bootstrap fallback of 256 cores and 256 GiB;
  * add the explicit 256/256 allocation to `default`, still disabled in the
    worked example;
  * keep the `general`, `large-memory`, `small-memory` and `office-hours`
    examples, and keep the fallback note beside the allocation section.

### Tests

* `tests/python/conftest.py`:
  * promote the existing private admin request helper into a reusable
    `admin_request(method, path, body=None)` function, keeping its basic-auth
    handling and JSON encoding;
  * add `enabled_pool_allocation()`, which requires exactly one enabled pool
    and one open-ended allocation and returns its cores and memory;
  * disarm only pools whose names match the test prefixes, so the clean-up
    fixtures leave a deployment's own enabled pool and its allocation alone;
  * add an AIMetrics entry for this change;
  * keep the Stage 6 pool clean-up fixtures using the promoted helper.
* `tests/python/states/test_atomicity_race.py`:
  * replace the `booking.capacity` reading with a helper that calls
    `GET /admin/pools/compute` and then the enabled pool's allocation endpoint;
  * require exactly one enabled pool and one open-ended 1-core, 1-GiB
    allocation, and skip with a message naming the missing pool configuration;
  * keep the `/actuator/info` check for `testing.enabled` and the
    booking-query-to-save delay;
  * update the module docstring and `RACE_CONFIG_HELP`.
* `tests/python/states/test_reservation_window.py`:
  * use the same enabled-pool allocation helper and skip rule;
  * update the module docstring and `CONFIG_HELP`.
* `docs/deployment.md`:
  * replace the race broker's `spring.yaml` capacity block with a `pools.yaml`
    block that disables `default` and enables one 1/1 pool;
  * update the sentence that says both tests read capacity from
    `/actuator/info`, so it says the race test reads the testing switch and
    delay there, while both tests read the allocation from the admin API;
  * update the compute-pool section so the fallback is 256/256 and the global
    setting is gone.
* `docs/booking.md`: replace the global-capacity fallback sentence with the
  bootstrap fallback, and state that there is no separate global limit.
* `AGENTS.md`: update the state-test table entry for
  `test_reservation_window.py` to say it needs one enabled pool with a 1-core,
  1-GiB allocation rather than booking capacity. Check the data-model and
  deployment sections for any remaining global-capacity wording.

### Plans and issues

* Update the parent plan's Stage 7 text and deferred section to link the new
  plan and the three follow-up issues.
* Open the generic-abstraction issue as
  [#216](https://github.com/uksrc/Calycopis-broker/issues/216) and the
  schema-split issue as
  [#217](https://github.com/uksrc/Calycopis-broker/issues/217), and refresh
  [#78](https://github.com/uksrc/Calycopis-broker/issues/78) with a comment
  linking the compute model. Do not reopen or duplicate existing issues.

## Validation

* A fresh deployment with no `pools.yaml` seeds the enabled `default` pool at
  priority zero with one open-ended 256-core, 256-GiB allocation, and serves
  requests through it.
* A deployment with `pools.yaml` uses each pool's declared allocations; a pool
  with no declaration gets the bootstrap fallback of 256/256.
* `grep -R` for `BookingCapacity` and `calycopis.broker.booking.capacity`
  finds no production, test, template or current documentation use. The
  historical plan and note references may remain.
* `/actuator/info` has no `booking.capacity` detail. The testing details remain.
* The atomicity race and reservation-window tests read the one enabled pool's
  allocation from the admin API and skip correctly when the broker is not
  configured with one 1/1 pool.
* Java unit tests and the `docker` Python suite pass. The `any` and `mock`
  suites pass apart from one pre-existing Jupyter notebook validator failure
  that records no executable result; it is unrelated to this change.
* The admin API, schema and offer response are unchanged.

## Risks and deferred

* Removing the info detail is a visible actuator change. It is deliberate: the
  value is no longer a broker-wide total, and the pool's allocation is the
  source of truth. The deployment guide and tests are updated in the same
  change.
* A deployment that relied on `calycopis.broker.booking.capacity` must move the
  value into a pool allocation. The deployment guide says so; there is no
  compatibility shim.
* The fallback is per pool. A deployment that enables several pools with no
  allocations gives each of them 256/256, so a one-pool deployment keeps the
  supply it had, while a multi-pool deployment gets that supply for every pool.
  The documentation makes that explicit.
* Storage pools, the generic abstraction, the schema split, the per-pool
  advisory lock, allocation calendars, pool deletion and force-shrinking remain
  deferred. The first three become issues in this stage.

## Status

status: implemented
details: Implemented and verified on branch `20261118-zrq-retire-global-capacity`
  from `main` at `dcbf4a1a`; commit `1b64b139` is pushed and pull request
  [#218](https://github.com/uksrc/Calycopis-broker/pull/218) is open. Java tests
  pass (83 run, 0 failures). The Python `any` and `mock` suites run 151 tests
  with one unrelated existing failure in the Jupyter notebook validator, which
  does not record its accepted result; the state suite passes under the general
  pool configuration (6 run, 3 expected skips) and the race and
  reservation-window tests pass under the one-pool 1/1 race configuration
  (3 run). The `docker` suite passes (56 run, 0 failures) against the host
  Podman socket and the deployment test-data volume. Follow-up issues
  [#216](https://github.com/uksrc/Calycopis-broker/issues/216) and
  [#217](https://github.com/uksrc/Calycopis-broker/issues/217) are open, and
  [#78](https://github.com/uksrc/Calycopis-broker/issues/78) has been refreshed.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-18T18:47:52",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T18:50:00/2026-11-18T20:30:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-18T20:00:00",
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
