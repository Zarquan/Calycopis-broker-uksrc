<!-- 5b2e2d5f-e7d1-4402-a9ee-1159f29fe5a9 -->
---
todos:
  - id: "branch"
    content: "Create branch 20261011-zrq-debug-stats from main"
    status: completed
  - id: "query"
    content: "Replace selectActiveSessionsWithCompute() with countActiveSessionsByPhase()"
    status: completed
  - id: "debug-stats"
    content: "Add ACCEPTED to ACTIVE_PHASES and rewrite debugStats()"
    status: completed
  - id: "headers"
    content: "Append AIMetrics entries to both modified file headers"
    status: completed
  - id: "plan-doc"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "compile"
    content: "Compile the broker"
    status: completed
  - id: "verify"
    content: "Run the broker and confirm the grouped Active lines"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval"
    status: pending
isProject: false
---
# Plan: Simplify the debug statistics (issue #118)

Date: 2026-10-11
Scope: `Calycopis-broker` Java source only — 2 files changed, plus this plan document
Issue: [uksrc/Calycopis-broker#118](https://github.com/uksrc/Calycopis-broker/issues/118) — Simplify the debug statistics

## Target

Replace `debugStats()`'s one-line-per-active-session listing with aggregate
counts of active sessions grouped by phase, so each processing poll logs only
the non-zero phase counts.

Success means:

* One `Active [<PHASE>][<count>]` debug line per **non-zero** active phase, in
  lifecycle order.
* `ACCEPTED` is included in the counted phases.
* No per-session UUID lines and no `Active totals [cores][memory]` line.
* The method still runs only when DEBUG logging is enabled, and issues a single
  query with no join to compute resources.

## Findings

* `debugStats()` is called at the start of every `getNext()` poll
  (`SpringProcessingTransactionHandlerImpl` line 140), guarded by
  `log.isDebugEnabled()`.
* It called `selectActiveSessionsWithCompute()` (`SpringSessionEntityRepository`),
  an inner join from sessions to `SimpleComputeResourceEntity`, logged one row
  per session, then summed cores and memory.
* `selectActiveSessionsWithCompute()` had exactly one caller (`debugStats`). The
  repository wrapper and the engine-level `SimpleExecutionSessionEntityRepository`
  do not expose it, so only these two files are involved.
* The old `ACTIVE_PHASES` was
  `OFFERED, WAITING, PREPARING, AVAILABLE, RUNNING, RELEASING`. `ACCEPTED` is a
  real, still-held phase: offers set `ACCEPTED` when a prepare request is
  scheduled (`OfferSetEntityFactoryImpl:159-161`) and
  `PrepareSessionRequestEntity:109` handles it. The issue's example output lists
  `ACCEPTED[1]`, and the booking query (`SimpleComputeResourceQuery.java:169`)
  also treats `ACCEPTED` as active.
* The old join also silently dropped active sessions with no compute resource; a
  count over sessions alone removes that blind spot.
* Logging is Log4j2 with `logging.level.net.ivoa: DEBUG` in `application.yaml`,
  so the method is active by default.

## Confirmed decisions

1. **Drop the cores/memory totals.** Counts only, via a single count query — no
   join to compute resources.
2. **Add `ACCEPTED`** to the existing six active phases (keeps `AVAILABLE`).

## Changes

### 1. `SpringSessionEntityRepository` — replace the query

Delete `selectActiveSessionsWithCompute()` (including its Javadoc) and add:

```java
    /**
     * Count the currently active sessions, grouped by phase.
     * Each returned row is an Object[] containing:
     * [0] the session phase,
     * [1] the number of active sessions in that phase.
     *
     */
    @Query(
        """
        SELECT
            s.phase,
            COUNT(s.uuid)
        FROM
            SimpleExecutionSessionEntity s
        WHERE
            s.phase IN :phases
        GROUP BY
            s.phase
        """
            )
    public List<Object[]> countActiveSessionsByPhase(
        @Param("phases") final List<IvoaSimpleExecutionSessionPhase> phases
        );
```

Imports are unchanged (`List`, `Query`, `Param`, `Repository`,
`SimpleExecutionSessionEntity`, `IvoaSimpleExecutionSessionPhase` all stay in
use).

### 2. `SpringProcessingTransactionHandlerImpl` — phase list and `debugStats()`

Add `ACCEPTED` to `ACTIVE_PHASES` after `OFFERED`, keeping the lifecycle order
(`OFFERED, ACCEPTED, WAITING, PREPARING, AVAILABLE, RUNNING, RELEASING`) and the
existing comment that the list defines the debug output order.

Rewrite `debugStats()` as:

```java
    /**
     * Print debug statistics for the currently active sessions.
     * Runs a single query counting the active sessions grouped by phase,
     * then logs the non-zero counts in phase order.
     * This method does nothing when debug logging is not enabled.
     *
     */
    public void debugStats()
        {
        if (!log.isDebugEnabled())
            {
            return;
            }
        List<Object[]> rows = this.sessionRepository.countActiveSessionsByPhase(
            ACTIVE_PHASES
            );
        rows.sort(
            Comparator.comparing(
                (Object[] row) -> ACTIVE_PHASES.indexOf(row[0])
                )
            );
        if (rows.isEmpty())
            {
            log.debug("Active [none]");
            }
        else {
            for (Object[] row : rows)
                {
                log.debug(
                    "Active [{}][{}]",
                    row[0],
                    row[1]
                    );
                }
            }
        }
```

No import changes: `Comparator`, `List` and `UUID` all remain in use. Do **not**
cast `row[1]` — passing it straight to the `{}` placeholder avoids a class-cast
risk if a dialect returns a non-`Long` count (`Integer`/`BigInteger` all log
correctly).

### 3. File headers

Per `agents/rules/`:

* Append one `AIMetrics` entry to each modified file's header (a single entry per
  change, after the existing entries): name `@deepseek-ai/dsh`, version
  `0.2.0-rc.2`, model `deepseek-flash`, with an `interval` covering the session.
* Copyright year is already `2026` in both files, so no year bump is needed.

## Edge cases and failure modes

| Case | Behaviour |
|---|---|
| No active sessions | `GROUP BY` yields no rows → one `Active [none]` line per poll. Keeps a heartbeat (the old code always logged at least `Active totals [0][0]`). |
| Active session with no compute resource | Now **counted** (old inner join dropped it). Intended; noted as a fix. |
| Multiple sessions in one phase | One aggregated row per phase by the `GROUP BY`; no per-session lines. |
| Phase outside `ACTIVE_PHASES` in a result row | Cannot occur (`IN :phases`); if it did, `indexOf` returns `-1`, it sorts first, and no exception is thrown. |
| Count type from the driver | Never cast; formatted via SLF4J. |
| DEBUG disabled | Guard returns before any query, unchanged. |
| JPQL error | Spring Data validates the query at context startup, so starting the broker + Postgres is the real syntax check, not `compile`. |
| Stale caller of the removed method | None (verified); a rebuild catches any missed reference. |

## Tests and verification

There is no Java test harness for this Spring-layer logging path: the only JUnit
test is `SimpleComputeResourceQueryTest`, and a repository test would need
PostgreSQL (H2 is unsupported — the app uses `GENERATE_SERIES`), which is not
mounted in this container (`/etc/calycopis` is absent). So no new automated test;
verification is compile + live broker observation, following
`notes/zrq/20261010-01-agent-broker-dev.txt`:

1. **Compile:** `source bin/versions.sh config.yaml` then
   `./mvnw -B clean compile` in `java/`, pointing Maven at the shared scratch
   cache (`MAVEN_USER_HOME=${CALYCOPIS_SCRATCH:?}/m2`,
   `CALYCOPIS_MAVEN_REPO=${CALYCOPIS_SCRATCH:?}/m2/repository`,
   `MAVEN_ARGS=-s ${CALYCOPIS_SCRATCH:?}/calycopis-dev/settings.xml`).
2. **Start** the `calycopis-pod` broker + PostgreSQL per that note (this
   validates the JPQL) and confirm the broker is healthy on port 8082.
3. **Observe** the log (`podman logs calycopis-broker` or
   `/var/log/calycopis/broker.log`):
   * idle broker → `Active [none]` once per poll;
   * after generating sessions → `Active [OFFERED][n]`,
     `Active [ACCEPTED][n]`, `Active [RUNNING][n]` … in lifecycle order;
   * confirm there are **no** per-session UUID lines and **no** `Active totals`
     line, and no zero-count phases are listed.
4. **Search** to confirm `selectActiveSessionsWithCompute` no longer appears
   anywhere in the tree.

## Acceptance criteria

* Compiles and the broker context starts (proving the grouped JPQL is valid).
* Exactly one DB query per poll with DEBUG on, no compute join.
* Output is one `Active [<PHASE>][<count>]` line per non-zero active phase in the
  order `OFFERED, ACCEPTED, WAITING, PREPARING, AVAILABLE, RUNNING, RELEASING`,
  or one `Active [none]` line when none.
* No per-session rows, no cores/memory totals, `ACCEPTED` included.
* `selectActiveSessionsWithCompute` removed; no dangling references.
* Header rules satisfied (AIMetrics entry per modified file; copyright year
  current).

## Commit

Only after explicit approval of the exact change set and message, commit with
`bin/agent-commit` (never plain `git commit`), which keeps the agent as author
and appends the human `Signed-off-by`:

```
Simplify the debug statistics for active sessions

Replace the per-session listing in debugStats() with aggregate counts of
active sessions grouped by phase, and add ACCEPTED to the active phases.

- Replace selectActiveSessionsWithCompute() with
  countActiveSessionsByPhase(), a COUNT ... GROUP BY phase query over
  execution sessions.
- Drop the join to compute resources and the cores/memory totals.
- Log one Active [<phase>][<count>] line per non-zero phase in lifecycle
  order, and Active [none] when no sessions are active.

Refs #118

AIMetrics: [
    {
    "interval": "<session start>/<session end>",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-flash",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    }
  ]
```

## Assumptions and out of scope

* The output format `Active [<phase>][<count>]` and the `Active [none]` idle line
  are accepted; the issue fixes only the aggregate-count requirement.
* Dropping cores/memory totals and adding `ACCEPTED` are the confirmed decisions
  above.
* No change to `ACTIVE_PHASES`-style phase sets elsewhere, even where they differ
  (e.g. `SimpleComputeResourceQuery` omits `AVAILABLE`) — that belongs to the
  booking-query work and
  [ivoa/Calycopis-broker#291](https://github.com/ivoa/Calycopis-broker/issues/291).
* Profiling statistics
  ([uksrc/Calycopis-broker#145](https://github.com/uksrc/Calycopis-broker/issues/145))
  and the other changes in `notes/zrq/20261008-02-booking-query.txt` are not part
  of this plan.
* Log-level configuration is untouched; `net.ivoa: DEBUG` already enables the
  method.
