<!--
  <meta:header>
    <meta:licence>
      Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)

      This information is free software: you can redistribute it and/or modify
      it under the terms of the GNU General Public License as published by
      the Free Software Foundation, either version 3 of the License, or
      (at your option) any later version.

      This information is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.

      You should have received a copy of the GNU General Public License
      along with this program.  If not, see <http://www.gnu.org/licenses/>.
    </meta:licence>
  </meta:header>

  AIMetrics: [
      {
      "timestamp": "2026-10-13T13:10:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 100,
        "units": "%"
        }
      }
    ]
-->

# Zero delay start times — the CI timing effect

PR #154 ([issue #30](https://github.com/uksrc/Calycopis-broker/issues/30))
stopped rounding offer start times up to the next block boundary. The point of
the change was to remove the delay a client sees before an offer becomes
available, and the effect is visible in CI: the same 56 integration tests went
from ~28 minutes to ~6.5 minutes.

This note records the measurement, the per test evidence and the mechanism, so
the number does not have to be rediscovered. The work itself is described in
`agents/plans/Zero delay starting blocks-df0fd372.plan.md`.

## Observed

| Run | Workflow | pytest phase |
|---|---|---|
| PR #153 merge — `38382780918` | 34m20s | `56 passed in 1737.14s (0:28:57)` |
| PR #154 merge — `38400256609` | 12m11s | `56 passed in 398.79s (0:06:38)` |
| PR #154 pull request — `38393746915` | 9m37s | (same suite) |

The historical pattern makes it a code effect rather than a fast runner. Every
workflow run from #146 to #153 took between 31m18s and 34m20s; only the two
#154 runs are under 13 minutes. `gh run list --repo uksrc/Calycopis-broker`
shows the sequence.

The difference is entirely in the test phase, not in the build. Taking the run
logs, the first to last `PASSED` line spans:

| Run | First `PASSED` | Last `PASSED` | Span | Workflow less test span |
|---|---|---|---|---|
| #153 merge | 10:25:10Z | 10:53:04Z | 27m54s | ~6m26s |
| #154 merge | 12:53:14Z | 12:59:42Z | 6m28s | ~5m43s |

So the jar build, the broker image build and the tester image build are within
about 45 seconds of each other across the two runs, and the whole workflow
difference of 22m09s is the pytest phase's 22m18s.

## Where the time went, per test

Method: `gh run view --log` and the timestamps on the `PASSED` lines. Each test's
time is the interval between its `PASSED` line and the previous one, which
includes pytest's own per test overhead rather than its internal duration
reporting, so it is an approximation of the test's wall clock cost.

| Test | #153 | #154 | Difference |
|---|---|---|---|
| `test_session_reaches_available` | 50.9 | 1.3 | **+49.6** |
| `test_offer_set_container_labels` | 59.6 | 3.3 | **+56.2** |
| `test_session_completes` (platform) | 66.4 | 10.4 | +56.0 |
| `test_session_completes_with_timed_container` | 90.0 | 32.0 | +57.9 |
| `test_direct_execution_completes` | 66.2 | 10.3 | +56.0 |
| `test_connectors_available_when_logs_captured` | 60.2 | 7.6 | +52.6 |
| `test_connectors_finished_after_completion` | 60.5 | 6.4 | +54.1 |
| `test_stdout_endpoint_returns_container_stdout` | 60.5 | 6.4 | +54.1 |
| `test_zero_exit_code_reaches_completed` | 61.6 | 7.6 | +54.0 |
| `test_concurrent_sessions` (stress) | 143.0 | 88.5 | +54.5 |
| `test_sha256_matches` | 62.4 | 11.4 | +51.1 |
| `test_md5_matches` | 57.4 | 11.2 | +46.3 |

All times in seconds.

* **27 of the 55 comparable tests** were slower before the change: 22 of them by
  46 to 58 seconds, and five more by 20 to 41 seconds —
  `test_direct_and_offerset_both_complete` +40.8,
  `test_user_labels_reach_the_container` +34.2,
  `test_short_and_long_containers` +27.6,
  `test_nonzero_exit_code_reaches_failed` +26.0 and
  `test_bind_mount_session_completes` +20.5.
* The sum of those differences is **1286.8s** (~21.5 minutes), against a pytest
  phase difference of 1338s. The residual is collection and the first test,
  which an interval between completions cannot see.
* The other 28 tests differ by less than a second. They are the ones that only
  inspect offers and never wait for a session to reach a phase.
* `test_concurrent_sessions` is 54.5s slower, about one boundary rather than one
  per session, which is what a concurrent wait should cost: the sessions in that
  test all wait for the same boundary.

The single clearest row is `test_session_reaches_available`, which does nothing
but submit a request, accept the offer and wait for the session phase: 50.9
seconds before, 1.3 seconds after.

## Mechanism

Before #154 the booking query searched from `rangestart = 1`, so the first offer
started at the block boundary *after* the one that contained the requested
start. `BLOCK_STEP_SECONDS` is 60 (`SimpleComputeResourceQuery.java`), so an
offer's `available.start` was up to 59 seconds in the future, ~30 seconds on
average.

That start time propagates into the component schedule:

* `AbstractComputeResourceEntity` sets
  `prepareStartInstantSeconds = offerStart - prepareDuration`, and for the
  docker platform the executable's prepare duration is about one second with a
  cached image (`DockerDockerContainerValidatorImpl`), so the prepare start
  inherited the boundary.
* `LifecycleComponentEntity.checkPrepareActionTiming()` returns `WAITING` while
  `prepareStartInstant` is in the future, and `getPrepareWaitDuration()` returns
  half the remaining time (floored at
  `DEFAULT_PREPARE_RULES_INTERVAL`, 2 seconds), so the processing loop woke,
  re-checked and slept again, converging on the boundary.
* A session can only reach `AVAILABLE` when all of its components are
  available, so the client — and the test waiting for the phase — paid that
  wait once per session.

After #154 the search starts at the block that contains the requested start and
the offer start is clamped forward to the requested start time, which for an
immediate request is `now + prepare`. Preparation begins at once, and the tests
wait only for the container work that actually has to happen.

## What this does and does not say

* It is a **latency** effect: with 27 affected sessions in the suite, the wait
  was paid once per session, and the suite's wall clock fell from ~32 minutes to
  ~10 minutes.
* It is not a throughput claim. The change moves when an allocation starts, not
  how much work the broker can do.
* The same wait applied to a real client, which is the point of the issue: an
  immediate request used to wait for the next block boundary before its session
  could even begin to prepare.

## Reproducing the measurement

The runner log for a completed workflow run can be fetched and parsed with the
timestamps it carries:

```bash
BIN="${LITHOSIA_CODE:?}/bin/gh"

# The container's /root/.cache is not writable, so give gh a cache it can use.
XDG_CACHE_HOME=/tmp/ghcache GH_CONFIG_DIR=/tmp/ghconfig \
GH_SECRET_FILE=/run/secrets/dsh-github-token-uksrc \
    "${BIN:?}" run view <run-id> \
        --repo uksrc/Calycopis-broker \
        --log > run.log
```

The log lines have the form `job<TAB>step<TAB>timestamp<TAB>line`, with ANSI
colour codes around the pytest result words, so a test's wall clock cost is the
difference between the timestamps of its `PASSED` line and the previous one.
The runs compared here are `38382780918` (#153 merge) and `38400256609` (#154
merge); the pull request run for #154 is `38393746915`.

## References

* [Issue #30](https://github.com/uksrc/Calycopis-broker/issues/30) — zero delay starting blocks
* [Pull request #154](https://github.com/uksrc/Calycopis-broker/pull/154) — merged as `8f6abe71`
* `agents/plans/Zero delay starting blocks-df0fd372.plan.md` — the plan and verification record
* `tests/python/any/test_zero_delay_start.py` — the test that pins the behaviour
