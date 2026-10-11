<!-- de86145d-cd47-4f10-9643-b6a901b293f7 -->
---
todos:
  - id: "branch"
    content: "Create branch 20261011-zrq-profiling-stats from main"
    status: completed
  - id: "engine-package"
    content: "Add engine profiling package (ProfilingEntry, ProfilingTarget, ProfilingEntryFactory, ProfilingEntryEntity)"
    status: completed
  - id: "target"
    content: "Make OfferSetEntity implement ProfilingTarget with a profiling_entries child collection"
    status: completed
  - id: "instrument"
    content: "Instrument stageTwo, generate and OfferSetEntityFactoryImpl.create and log the timings"
    status: completed
  - id: "wiring"
    content: "Wire the profiling factory through Spring and both platform implementations"
    status: completed
  - id: "test"
    content: "Add OfferSetEntityProfilingTest unit test"
    status: completed
  - id: "compile"
    content: "Compile and run the Java test suite"
    status: completed
  - id: "plan-doc"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "verify"
    content: "Run the broker and confirm the profiling_entries rows and log lines"
    status: pending
  - id: "commit"
    content: "Commit through bin/agent-commit after approval"
    status: pending
isProject: false
---
# Plan: Profiling statistics (issue #145)

Date: 2026-10-11
Scope: `Calycopis-broker` Java source only. No OpenAPI schema change, so `Calycopis-openapi` is untouched.
Issue: [uksrc/Calycopis-broker#145](https://github.com/uksrc/Calycopis-broker/issues/145) — Profiling statistics

## Goal and success criteria

Add lightweight timing instrumentation around offer-set generation and persist
each measurement in the database, so offer-generation overhead can be analysed
over time.

Success means:

* A new persistent child collection of `OfferSetEntity` holds one row per
  profiled method call, with class name, method name, start time and end time.
* `OfferSetEntity` implements a new `ProfilingTarget` interface
  (`addProfilingEntry` / `getProfilingEntries`).
* A `ProfilingEntryFactory.create(className, methodName)` returns an entry whose
  start time is set to `now()`; `entry.end()` sets the end time.
* Three methods are instrumented: `OfferSetRequestParserImpl.stageTwo`,
  `SimpleComputeResourceOfferFactoryImpl.generate` and
  `OfferSetEntityFactoryImpl`'s protected `create`.
* `OfferSetEntityFactoryImpl.create` logs the accumulated timings at DEBUG.
* Every successful offer-set request leaves one `profiling_entries` row per
  instrumented method, all with non-null `end_time`.
* Existing behaviour, API responses and the OpenAPI schema are unchanged.

## Confirmed decisions

1. **Always on** (user-confirmed). No configuration flag; every offer-set request
   records and logs its timings, matching the issue.
2. **Java unit test plus a manual end-to-end SQL check** (user-confirmed). No new
   committed Python integration test.
3. **Persistence as a child table**, following the existing `messages`,
   `cost_items` and `metric_items` patterns, rather than a serialised column.
4. **Follow the issue's interface, not its typos**: `ProfilingEntryFactory` (not
   `ProfilingEntryfactory`) and `ProfilingTarget` (not `ProfileTarget`). The
   interface keeps exactly the fields and methods the issue lists — no
   `getDuration()`; duration is computed where it is logged.
5. **`SimpleComputeResourceOfferFactory.generate` takes a `ProfilingTarget`**, not
   an `OfferSetEntity`. The issue's skeleton passes the offerset; the
   `ProfilingTarget` abstraction it also specifies is a looser coupling for a
   class in `engine/functional/booking/` and is behaviourally identical.
6. **Profiling lives on `OfferSetEntity` only**, as the issue states, not on the
   shared `ComponentEntity` base. A future issue can promote it if other
   components need profiling.

## Findings that shape the design

* `OfferSetEntityFactoryImpl.create(...)` saves the offerset **twice** — once
  before `stageTwo` (so the offerset has a UUID for child sessions) and once
  after. `messages` are claimed after the first save and rely on the second
  save's cascade `merge` to insert them; the live database confirms this works
  (2 message rows, both attached to offersets). Profiling entries use the same
  path.
* Nothing in the offer-creation path is `@Transactional`; each
  `repository.save()` runs in its own transaction and returns a detached entity.
  **Consequence:** an `end()` called after the final save would not be flushed.
  All `end()` calls must happen before the final save.
* `AbstractExecutionSessionEntity` already maps
  `@JoinColumn(name = "offerset", referencedColumnName = "uuid")` to
  `OfferSetEntity`, so a `@ManyToOne` to `OfferSetEntity` with
  `referencedColumnName = "uuid"` yields an FK to `offersets(uuid)`.
* `@EntityScan` and `@ComponentScan` are both `net.ivoa.calycopis`, so new
  packages need no registration.
* All `@Component` classes live under `spring/`; `engine/` has none. The platform
  implementations construct engine objects in `initialize()`, after the
  autowired repositories are available.
* `MockPlatformImpl` autowires the `@Component`
  `SpringSimpleComputeResourceOfferFactoryImpl`; `DockerPlatformImpl` constructs
  its own `SimpleComputeResourceOfferFactoryImpl`. Both must pass the new
  dependency.
* `OfferSetRequestParserImpl` is currently a field initialiser
  (`= new OfferSetRequestParserImpl()`) in both platform classes and is used only
  to build `OfferSetEntityFactoryImpl`, so its construction can move into
  `initialize()`.
* The deployed broker runs `./mvnw spring-boot:run` from the **same host
  directory** as this workspace (verified by matching inodes), uses the `docker`
  profile, and `ddl-auto: create` recreates the schema at each start. A restart
  is therefore enough to pick up the change and create the new table.

## Changes

### 1. New profiling domain package (engine)

New package `net.ivoa.calycopis.broker.engine.entities.profiling`:

**`ProfilingEntry.java`** — interface exactly as specified:

```java
public interface ProfilingEntry
    {
    String getClassName();
    String getMethodName();
    Instant getStartTime();
    Instant getEndTime();
    void start();  // sets start time to now
    void end();    // sets end time to now
    }
```

**`ProfilingTarget.java`**:

```java
public interface ProfilingTarget
    {
    void addProfilingEntry(ProfilingEntry entry);
    List<ProfilingEntry> getProfilingEntries();
    }
```

**`ProfilingEntryFactory.java`** — `extends FactoryBase` (house convention for
factories):

```java
public interface ProfilingEntryFactory extends FactoryBase
    {
    ProfilingEntry create(String className, String methodName);
    }
```

**`ProfilingEntryFactoryImpl.java`** — `extends FactoryBaseImpl implements
ProfilingEntryFactory`; `create` returns `new ProfilingEntryEntity(className,
methodName)`, whose constructor sets `startTime = Instant.now()`.

**`ProfilingEntryEntity.java`** — JPA entity implementing `ProfilingEntry`,
modelled on `MessageEntity`:

```java
@Entity
@Table(name = "profiling_entries")
public class ProfilingEntryEntity implements ProfilingEntry
    {
    @Id @GeneratedValue
    private Long ident;

    @JoinColumn(name = "parent", referencedColumnName = "uuid", nullable = false)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private OfferSetEntity parent;

    public OfferSetEntity getParent();
    public void setParent(final OfferSetEntity parent);

    protected ProfilingEntryEntity() { }                     // JPA
    public ProfilingEntryEntity(final String className, final String methodName);

    @Column(name = "class_name")  private String className;
    @Column(name = "method_name") private String methodName;
    @Column(name = "start_time")  private Instant startTime;
    @Column(name = "end_time")    private Instant endTime;

    // getters, plus start()/end() setting the corresponding Instant to now()
    }
```

The `@ManyToOne` is non-optional, but the parent is set by
`OfferSetEntity.addProfilingEntry`, so no entry is ever persisted orphaned.

### 2. `OfferSetEntity` becomes a `ProfilingTarget`

`engine/entities/offerset/OfferSetEntity.java`:

* Class declaration becomes `implements OfferSet, ProfilingTarget`.
* New lazily-fetched child collection, mirroring the `messages` mapping:

```java
@OneToMany(
    mappedBy = "parent",
    fetch = FetchType.LAZY,
    cascade = CascadeType.ALL,
    orphanRemoval = true
    )
private List<ProfilingEntryEntity> profilingEntries = new ArrayList<ProfilingEntryEntity>();
```

* `addProfilingEntry` sets the back-reference and adds, mirroring
  `claimMessages` (pattern-match on `ProfilingEntryEntity`, log and throw
  `IllegalArgumentException` on an unexpected type).
* `getProfilingEntries()` returns `new ListWrapper<ProfilingEntry,
  ProfilingEntryEntity>(this.profilingEntries){...}`, matching
  `ComponentEntity.getMessages()`.
* Add `getProfilingEntryEntities()` returning the raw list, for symmetry with
  `getMessageEntities()`.

### 3. Instrumentation call sites

**`OfferSetRequestParserImpl`** (`engine/entities/offerset/`)

* Constructor gains `ProfilingEntryFactory profilingEntryFactory`, stored in a
  final field.
* At the top of `stageTwo`:
  `ProfilingEntry profiling = profilingEntryFactory.create(this.getClass().getName(), "stageTwo");`
  then `offersetEntity.addProfilingEntry(profiling);`.
* Call `profiling.end();` immediately before the existing single
  `return offersetEntity;`. No `try/finally`: if the method throws, the offerset
  is never saved again and the entry is never persisted.

**`SimpleComputeResourceOfferFactory` / `…FactoryImpl`**
(`engine/functional/booking/compute/simple/`)

* Interface: append `final ProfilingTarget target` to `generate(...)`.
* Impl: constructor gains `ProfilingEntryFactory`; `generate` becomes
  create → `target.addProfilingEntry(profiling)` → build query → run query into a
  local `offers` variable → `profiling.end()` → `return offers`.

**`OfferSetEntityFactoryImpl`** (`engine/entities/offerset/`)

* Constructor gains `ProfilingEntryFactory profilingEntryFactory`.
* In the protected `create(context, offerCount)`: create the entry at method
  start; build the offerset; first save; attach the entry after the first save
  (so the final save inserts it once, with its end time already set); run
  `stageTwo`; `profiling.end()` **before** the final save; iterate
  `offersetEntity.getProfilingEntries()` and log each with
  `log.debug("Profiling [{}][{}][{}][{}]", className, methodName, startTime, duration)`
  (duration is null-safe); then the existing final save.
* `direct(...)` needs no change: it routes through the same protected `create`.

### 4. Spring wiring

**New `spring/profiling/SpringProfilingEntryFactoryImpl.java`** —
`@Component extends ProfilingEntryFactoryImpl`, mirroring
`SpringSimpleComputeResourceOfferFactoryImpl`.

**`spring/booking/compute/simple/SpringSimpleComputeResourceOfferFactoryImpl.java`**
— constructor becomes `(ProfilingEntryFactory, SimpleComputeResourceQueryHandler)`
and passes both to `super`.

**`spring/platform/mock/MockPlatformImpl.java`**

* `@Autowired private ProfilingEntryFactory profilingEntryFactory;`
* Remove the field initialiser, leaving
  `private OfferSetRequestParser offerSetRequestParser;`, and in
  `initialize()` set
  `this.offerSetRequestParser = new OfferSetRequestParserImpl(this.profilingEntryFactory);`
  before it is used to build `offerSetFactory`.
* Pass `this.profilingEntryFactory` as the new last argument of
  `new OfferSetEntityFactoryImpl(...)`.

**`spring/platform/docker/DockerPlatformImpl.java`** — same three changes, plus
pass `this.profilingEntryFactory` to
`new SimpleComputeResourceOfferFactoryImpl(...)`.

## Data flow

1. `POST /requests` → `OfferSetEntityFactoryImpl.create(request, identity)` →
   `stageOne` (unprofiled) → protected `create`.
2. The outer entry starts; the offerset is constructed and first-saved.
3. The outer entry is attached; `stageTwo` attaches and ends its own entry, and
   `generate` attaches and ends its own entry.
4. The outer entry ends; the accumulated timings are logged.
5. The final save cascade-merges the offerset and inserts the three
   `profiling_entries` rows with their end times, FK'd to `offersets.uuid`.
6. Analysis is by SQL over `profiling_entries`; offersets are never deleted.

## Edge cases and failure modes

* **`end()` not called** (exception mid-method): with the entry attached after
  the first save, no profile row is written at all, so `end_time` nulls should
  not arise in practice. The log loop and SQL query must still tolerate a null
  `end_time`, and the column is nullable.
* **Rejected request** (`ResultEnum.NO`): the outer `create` entry and the
  `stageTwo` entry exist (1–2 rows); `generate` is not called, so no third row.
  This is expected and useful signal.
* **`direct(...)` path**: profiled identically, because it calls the same
  protected `create`.
* **Multiple offers**: `generate` is instrumented once per offer set; its
  duration covers the whole query, not each returned offer.
* **Nested timings overlap** (`stageTwo` contains `generate`); documented, and
  visible in the data by class/method name.
* **Profiler overhead**: two saves per request are unchanged; the only added DB
  work is one INSERT per instrumented method. The outer entry deliberately ends
  before the final save, so its recorded duration excludes the final flush. This
  is the only way to persist an end time without a third save; noted as a known
  limitation rather than a defect.
* **Schema recreation**: the deployment uses `ddl-auto: create`, so historical
  profiling rows do not survive a broker restart. That is pre-existing and out of
  scope, but it does cap "over time" analysis; a follow-up issue should consider
  `update`/`validate` plus a migration story.
* **No API exposure**: `makeBean`/`fillBean` are untouched, so the OpenAPI
  response is unchanged and the `Calycopis-openapi` schema needs no edit.

## Tests

New `java/src/test/java/net/ivoa/calycopis/broker/engine/entities/offerset/OfferSetEntityProfilingTest.java`
(placed in the `offerset` package because `OfferSetEntity`'s constructor is
`protected`), JUnit 5. Four tests: factory names and start time; `end()` sets an
end time not before the start; `start()` does not move the start backwards;
`OfferSetEntity` collects entries in order and sets itself as parent.

## Verification and acceptance criteria

1. **Compile** (offline works; the scratch Maven repo is pre-populated):
   `MAVEN_USER_HOME="${CALYCOPIS_SCRATCH:?}/m2" ./mvnw -Dmaven.repo.local="${CALYCOPIS_SCRATCH:?}/m2/repository" clean test-compile`
   — passed.
2. **Unit test**: `… ./mvnw … test` — passed, 13 tests (4 new, 9 existing).
3. **End-to-end** (restart of the shared dev broker requires approval first):
   `podman restart calycopis-broker` (same source tree, docker profile), wait for
   `/actuator/health`, submit the executable-only offer-set request from
   `tests/python/any/test_resource_registration.py::test_executable_only` with the
   admin identity from `/etc/calycopis/admin.yaml`, then check:
   ```sql
   SELECT o.uuid, p.ident, p.class_name, p.method_name, p.start_time, p.end_time,
          (p.end_time - p.start_time) AS duration
     FROM profiling_entries p JOIN offersets o ON o.uuid = p.parent
    ORDER BY o.uuid, p.ident;
   ```
   Accept: exactly three rows per new offerset — `OfferSetEntityFactoryImpl.create`,
   `OfferSetRequestParserImpl.stageTwo` and the offer factory `generate` — every
   `start_time`/`end_time` non-null and `duration > 0`; plus the three
   `Profiling [...]` DEBUG lines in the broker log.

## House rules

* Every new Java file gets the GPL `<meta:header>` block with the University of
  Manchester line plus an `AIMetrics` entry with `contribution.value: 100`.
* Every modified file gets a new `AIMetrics` entry appended (existing entries
  untouched) and its copyright year bumped to 2026 where it is not already
  (`OfferSetEntity.java` was 2024).
* No `?:` outside log messages; clarity over brevity; no new Maven dependencies
  and no configuration changes.

## Assumptions and out of scope

* No OpenAPI/schema change and no API exposure of profiling data; analysis is via
  SQL.
* Profiling is on `OfferSetEntity` only.
* `ddl-auto: create` (data loss on restart) and retention/archival of profiling
  rows are not addressed here; recommend a follow-up issue.
* No changes to `tests/python`, CI workflows, deployment scripts or configuration
  files.
* The `end()`-before-final-save rule is a consequence of the current
  non-transactional two-save design; if that design changes, the ordering should
  be revisited.
