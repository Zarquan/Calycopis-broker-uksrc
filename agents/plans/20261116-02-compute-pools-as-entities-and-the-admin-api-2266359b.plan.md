<!-- 2266359b-6ea3-4964-86fe-ba56ae648405 -->
---
todos:
  - id: "branch"
    content: "Create the branch from main at the merge of stage 1"
    status: completed
  - id: "entities"
    content: "Add the compute pool entity tiers, their factory and the Spring repository"
    status: completed
  - id: "controller"
    content: "Add the admin controller for POST, list and get by uuid"
    status: completed
  - id: "security"
    content: "Move the /admin matcher before the blanket GET permitAll and cover all three verbs"
    status: completed
  - id: "bootstrap"
    content: "Add the pool settings and the idempotent startup seeder"
    status: completed
  - id: "config"
    content: "Add config/pools.yaml, its optional import and the seeding in bin/config-broker.sh"
    status: completed
  - id: "tests"
    content: "Add tests/python/any/test_admin_pools.py and check the existing suites"
    status: completed
  - id: "docs"
    content: "Document pools.yaml in docs/deployment.md and the pools in AGENTS.md"
    status: completed
  - id: "build"
    content: "Compile and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the any and mock Python suites against a local broker"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Compute pools as entities and the admin API

Date: 2026-11-16
Scope: `Calycopis-broker` — a new compute pool entity family, a Spring
repository, an admin controller with three endpoints, a startup seeder and its
configuration file, one change to the security matcher order, tests and
documentation. Booking, offers, capacity and the published schema are untouched.
Issue: [uksrc/Calycopis-broker#199](https://github.com/uksrc/Calycopis-broker/issues/199) —
Compute resource pools, stage 2 of seven.
Parent: [20261114-03-compute-resource-pools-and-allocations](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md).
Depends on: [20261116-01-rename-the-compute-resources-to-compute-reservations](20261116-01-rename-the-compute-resources-to-compute-reservations-24080b4e.plan.md),
merged as [PR #209](https://github.com/uksrc/Calycopis-broker/pull/209).
Branch: `20261116-zrq-compute-pools`, to be created from `main` at `524bd614`.
Result: not started.

## Target

A pool exists as a persisted entity that an admin can create and read, and that a
fresh deployment gets for free. Nothing yet uses it: the booking query keeps
reading the two constants from the configuration, so the same requests produce
the same offers as before this stage.

The pool is the noun the rest of the work hangs on. Stage 3 gives it allocations
and reads capacity from them, stage 4 makes each reservation name its pool, stage
5 moves the offer query inside the pool, and stage 6 gives it policies. Each of
those needs a pool to attach to, and all of them change behaviour, so this stage
is deliberately the one that adds the noun and changes nothing else.

## Where we are

Stage 1 settled the vocabulary. A **reservation** is the compute one session has
booked; a **pool** keeps the word "resource" because it holds capacity rather
than bookings, so the new types are `AbstractComputeResourcePool` and
`SimpleComputeResourcePool`. The parent plan's decision 1 records the rule and
its vocabulary table defines the terms.

The relevant existing machinery:

- `ComponentEntity` (`engine/entities/component/`) supplies `uuid`, `name`,
  `description`, `created`, `modified`, `owner` and the message list. It is the
  base for everything addressable, and it declares `getKind()` and
  `getWebappPath()` for subclasses to answer.
- `IdentityEntity` is the closest precedent: an off-schema, admin-created,
  hand-written entity that extends `ComponentEntity` directly, invents its own
  kind URI (`uri:identity`) and webapp path (`/identities/`), and is created
  through a factory that saves through a repository.
- `AdminIdentityController` (`spring/webapp/`) is the closest precedent for the
  endpoint: a `@RestController` under `/admin`, returning JSON written by hand.
- `SecurityConfig` (`spring/security/SecurityConfig.java`) maps
  `/admin/**` to the `ADMIN` authority. `AdminAuthenticationProvider` grants that
  authority from the credentials in `admin.yaml`; `LocalAuthenticationProvider`
  grants an empty list, so a client identity can authenticate and still be
  refused.

One thing there is not: a matcher order that makes the rule true for GET. See
the first decision below.

## Decisions

1. **The `/admin` matcher moves above the blanket `GET` matcher.** The chain
   currently reads, in order, `GET` → `permitAll`, `/actuator/**` → `permitAll`,
   `/admin/**` → `hasAuthority("ADMIN")`. Rules are evaluated in order, so every
   GET in the application is public, including a `GET` under `/admin`. Today
   nothing exposes one, so nothing leaks; this stage adds three, and the list
   endpoint would be readable by anyone. The `/admin` matcher moves to the front
   and the order is commented, because the next person to add a matcher will
   otherwise reintroduce the bug by inserting above it. This is a change to
   existing behaviour only in the sense that it closes a hole that has not yet
   been opened.
2. **Pools are off-schema, like identities.** Decision 5 of the parent plan says
   pools and allocations are admin resources until the Calycopis extension is
   designed. The request and response bodies are therefore hand-written records
   in `spring/webapp/`, with no Ivoa type, no discriminator in the published
   schema and no client change. The Python tests call the endpoints with
   `urllib` and basic auth, as the identity seeding already does.
3. **A pool has no validator tier.** The three-tier pattern exists to validate an
   Ivoa bean before building an entity, and there is no bean here. The pool types
   are an interface and an entity at the abstract tier, an interface and an
   entity at the simple tier, plus one factory and one repository. The controller
   validates its input. Writing validators that validate a record invented in the
   same package would be ceremony, and the pattern is followed everywhere it
   earns its place.
4. **A pool is not a lifecycle component.** It has no phase and no schedule, so
   its entity extends `ComponentEntity` rather than `LifecycleComponentEntity`.
   A pool that is available or unavailable is stage 6, and it will be expressed
   by a policy rather than by a phase, because availability there depends on who
   is asking.
5. **Pools are ownerless.** `ComponentEntity.owner` is a nullable `@JoinColumn`,
   and the admin credential in `admin.yaml` is not an `IdentityEntity`, so a pool
   is created with a null owner. Inventing an identity row for the admin would
   make every pool owned by a row that cannot log in through the identity path.
6. **The name is the identity an admin uses.** A pool's name is required,
   trimmed, and unique; a second pool with the same name is refused with `409
   Conflict`. The seeder finds a pool by name, so the name is the key that makes
   startup idempotent, and the UUID is the key the API uses. Names are not
   enforced as URL-safe slugs: they are labels, and the endpoints take UUIDs.
7. **Seeding reads `pools.yaml`, and falls back to one pool called `default`.**
   The properties class carries an empty list by default; the seeder turns an
   empty list into a single `default` pool, so a deployment with no file behaves
   as the parent plan's decision 6 requires. A file that names pools seeds those
   and no `default`, because an admin who listed pools meant to list all of them.
8. **The default pool carries no capacity.** Capacity stays in
   `calycopis.broker.booking.capacity` until stage 3, which is what keeps this
   stage's behaviour identical. The `pools.yaml` template therefore has no
   allocation section yet, and the template says why.

## Changes

### The entity tiers

| File | Role |
|---|---|
| `engine/entities/compute/pool/AbstractComputeResourcePool.java` | Interface extending `Component`. |
| `engine/entities/compute/pool/AbstractComputeResourcePoolEntity.java` | Abstract `@Entity`, `@Inheritance(JOINED)`, `@Table(name = "abstractcomputeresourcepools")`. |
| `engine/entities/compute/pool/simple/SimpleComputeResourcePool.java` | Interface extending `AbstractComputeResourcePool`, with `TYPE_DISCRIMINATOR`. |
| `engine/entities/compute/pool/simple/SimpleComputeResourcePoolEntity.java` | Concrete `@Entity`, `@DiscriminatorValue`, `@Table(name = "simplecomputeresourcepools")`. |
| `engine/entities/compute/pool/simple/SimpleComputeResourcePoolEntityFactory.java` | Factory interface. |
| `engine/entities/compute/pool/simple/SimpleComputeResourcePoolEntityFactoryImpl.java` | `@Component` factory, saves through the repository. |
| `engine/entities/compute/pool/simple/SimpleComputeResourcePoolEntityRepository.java` | Framework-neutral persistence interface, with a lookup by name and a list. |
| `spring/jpa/SpringComputeResourcePoolEntityRepository.java` | `@Repository`, extending both the Spring base and the engine interface. |
| `spring/pools/SpringSimpleComputeResourcePoolEntityFactoryImpl.java` | `@Component` subclass that supplies the repository to the engine factory, following `spring/identity/`. |

`getKind()` returns `uri:compute-pool`, and `getWebappPath()` returns
`/pools/compute/`, following `IdentityEntity`'s invented `uri:identity` and
`/identities/`. The kind is not in the published schema, so it is a Calycopis
identifier rather than an IVOA one, and the branch is named for that.

The abstract tier holds the entity because `@Inheritance(JOINED)` needs a root
table, even though there is one concrete type today. Decision 7 of the parent
plan keeps the generic `AbstractResourcePool<...>` out until a second pool
implementation exists.

### The admin API

`spring/webapp/AdminComputePoolsController.java`, under `/admin/pools/compute`:

| Verb | Path | Body | Response |
|---|---|---|---|
| `POST` | `/admin/pools/compute` | `{"name": ..., "description": ...}` | `201`, `Location: /admin/pools/compute/{uuid}`, pool |
| `GET` | `/admin/pools/compute` | — | `200`, array of pools |
| `GET` | `/admin/pools/compute/{uuid}` | — | `200` pool, or `404` |

`spring/webapp/ComputePoolResponse.java` is a record carrying `uuid`, `name`,
`description` and `created`. It carries no `modified` timestamp: the component
base declares one, but nothing in the broker maintains it, so reporting it would
mean reporting null forever. The controller maps the entity onto the record,
because a mapping method on an engine entity would put a web type in the engine
tree.

What the endpoints do with bad input:

- no credentials → `401`, from the basic-auth entry point, for all three verbs;
- a local identity without `ADMIN` → `403`, for all three verbs, which is what
  decision 1 makes true;
- a missing, empty or whitespace-only `name` → `400`;
- a name that is already taken → `409`;
- an unknown or malformed UUID → `404` and `400` respectively.

The path segment `compute` is deliberate: a storage pool will be
`/admin/pools/storage` without moving what exists, as the parent plan requires.

### The bootstrap

| File | Role |
|---|---|
| `spring/pools/ComputePoolsSettings.java` | Interface for the settings. |
| `spring/pools/ComputePoolsSettingsImpl.java` | `@Component`, `@ConfigurationProperties(prefix = "calycopis.broker.pools")`, following `BookingCapacitySettingsImpl`. |
| `spring/pools/ComputePoolBootstrap.java` | `ApplicationRunner`, seeds the pools and logs what it created. |

The seeder reads the configured list, appends `default` when the list is empty,
and for each entry looks the pool up by name and creates it only if it is
missing. It logs one line per pool created and one line for a pool it found,
because a silent startup is indistinguishable from a broken one.

### Configuration and documentation

| File | Change |
|---|---|
| `config/pools.yaml` | New template, with a commented example and no allocation section. |
| `java/src/main/resources/application.yaml` | `optional:file:/etc/calycopis/pools.yaml` in the import list. |
| `bin/config-broker.sh` | Writes the template into the configuration volume, as it does for `admin.yaml`. |
| `docs/deployment.md` | A section on the pools file, what happens without it, and that pools do not survive a restart. |
| `AGENTS.md` | Pools in the data model, the admin endpoints in the API surface table, and the metrics entry for this change. |

### Tests

`tests/python/any/test_admin_pools.py`, using the existing fixtures and helpers
in `tests/python/conftest.py`: `broker_url` for the base URL,
`admin_credentials()` for the credentials in `admin.yaml`, and `alice_creds` for
a seeded local identity that is not an admin. The requests are made with
`urllib` and a basic-auth header, because there is no generated client for these
endpoints:

| Test | Asserts |
|---|---|
| `test_admin_can_create_a_pool` | `201`, a `Location` header, and a body with a UUID and the given name. |
| `test_created_pool_is_readable` | `GET` by that UUID returns the same name and the timestamps. |
| `test_pool_list_contains_the_default` | `GET` the list and find `default`, seeded at startup. |
| `test_unauthenticated_request_is_401` | All three verbs without credentials. |
| `test_local_identity_is_403` | All three verbs with a seeded user, which is the matcher-order regression test. |
| `test_duplicate_name_is_409` | Two `POST`s with the same name. |
| `test_blank_name_is_400` | Empty, spaces, and a missing field. |
| `test_unknown_uuid_is_404` | A random UUID. |

The suite is in `any/` because pools exist on both platforms; nothing here
depends on `mock` or `docker`.

## Acceptance

- `./mvnw clean compile` and `./mvnw test` pass, and the offer generation tests
  are unchanged: the pinned scenario produces the same offer rows as before this
  stage, which is the check that booking was not touched.
- The new tables are created at startup: `abstractcomputeresourcepools` and
  `simplecomputeresourcepools` exist in PostgreSQL after a start.
- The eight tests above pass, and the existing `any` and `mock` suites still
  pass. `docker` is not required for this stage, but is run once if the entity
  mapping changes after the first green run, because a DDL fault would show
  there and nowhere else.
- An admin can create a pool and read it back; a local identity gets `403` on
  `GET` as well as on `POST`; no credentials get `401`.
- A broker started with no `pools.yaml` has exactly one pool, named `default`;
  two `POST`s with the same name leave one pool and one `409`.

## Out of scope

Allocations and their endpoints (stage 3). Capacity read from allocations
(stage 3). The `pool_uuid` column on a reservation (stage 4). `makeOffers` and
`SimpleComputeResourceCriteria` (stage 5). `PoolPolicy` and the ordered pool
list the offer factory walks (stage 6). The Calycopis schema extension that would
publish these types. Any change to `application.yaml`'s booking section.

## Deferred

**Pool deletion.** The parent plan gives a pool a lifetime "until an admin
deletes it", and this stage does not provide the verb. Deleting a pool means
deciding what happens to the reservations that name it, which is stage 4's
business, and a delete that refuses while reservations exist needs that column to
ask about. Adding `DELETE` here would need a stub that either always refuses or
orphans rows.

**A restart keeps nothing.** The broker recreates its schema at startup
(`ddl-auto: create`), so admin-created pools and their UUIDs disappear when it
restarts, and the seeder replaces them with new rows under the same names. That
is the existing behaviour for every entity in this project and not something this
stage changes, but it makes pool UUIDs unsuitable for anything a person writes
down, and the deployment guide should say so rather than let someone discover it.

## Resolved on review (2026-11-16)

1. **The pool list is admin-only.** All three endpoints sit under `/admin` and
   require the `ADMIN` authority, so the matcher reorder in decision 1 is part of
   the work rather than an optional tidy-up. A client-discoverable pool list is
   left to the plan that publishes the Calycopis schema extension, where it can
   be designed as a published type rather than as a hand-written record.
2. **`bin/config-broker.sh` writes `pools.yaml`.** It writes the template into
   the configuration volume alongside `admin.yaml`, so a fresh deployment starts
   with a file rather than relying on the code's fallback. The fallback stays,
   because a broker run without the volume has to start with a pool, and the
   seeder is what makes both paths behave the same way.

## Notes from implementation

Recorded while building it, because the plan said something slightly different
in each case.

1. **The factory is split across the two trees, as the identity factory is.**
   `SimpleComputeResourcePoolEntityFactoryImpl` lives in the engine and takes the
   engine repository interface; `SpringSimpleComputeResourcePoolEntityFactoryImpl`
   is the `@Component` subclass that injects the Spring Data repository. A Spring
   component in `engine/` would have broken the rule that the engine tree has no
   Spring imports.
2. **The response carries no `modified` timestamp.** The component base declares
   one, but nothing in the broker maintains it, so it is null on every component
   and would have been null on every pool. The record carries `uuid`, `name`,
   `description` and `created`; `modified` arrives with the update verb that gives
   it a meaning.
3. **The controller maps the entity, not the entity itself.** A `makeResponse()`
   on the entity would put a `spring/webapp` type inside the engine tree, which is
   the same separation the factory split respects.
4. **The seeder skips a nameless pool rather than failing startup.** A bad
   `pools.yaml` should not stop the broker, so it logs a warning and continues.

5. **The duplicate-name check is check-then-act.** Two admin creates racing on
   the same name could both pass the lookup and insert. The window is narrow and
   the endpoint is admin-only, so it is recorded rather than closed here: a
   unique constraint on the pool table and a mapping from that violation to
   `409` belong with the stage that lets a pool be renamed.

Both configuration paths were exercised against a running broker. With no file,
the log reads `Seeding [1]` and one `default` pool is created. With a file naming
`alpha` and `beta`, it reads `Seeding [2]` and creates those two, with no default.

## Status

status: implemented
details: Implemented in commit [`e1111f8a`](https://github.com/uksrc/Calycopis-broker/commit/e1111f8a) and opened as
  [PR #210](https://github.com/uksrc/Calycopis-broker/pull/210). All eleven plan
  items are complete. Verified with `./mvnw clean compile`, `./mvnw test` (59
  tests) and the Python `any` and `mock` suites (114 passed, 1 pre-existing
  failure, the notebook validator test that also fails on `main`). The new
  `tests/python/any/test_admin_pools.py` passes all twelve tests, including the
  `403` on `GET` that is the regression test for the matcher order. The
  `abstractcomputeresourcepools` and `simplecomputeresourcepools` tables are
  created at startup, and both configuration paths were exercised against a
  running broker.

## AIMetrics

```text
AIMetrics: [
    {
    "interval": "2026-11-16T06:50:00/2026-11-16T07:05:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    }
  ]
```
