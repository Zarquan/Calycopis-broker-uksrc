<!-- 5a279403-83d1-457b-b574-8993911c8a5a -->
---
todos:
  - id: "convention"
    content: "Add agents/rules/plan-documents.mdc and the plan-document exception in ai-metrics.mdc"
    status: completed
  - id: "agents-doc"
    content: "Record the rule in AGENTS.md, as a rules table row and a directory layout note"
    status: completed
  - id: "rename"
    content: "Rename the thirteen plans to YYYYMMDD-NN-<slug>-<id8>.plan.md"
    status: completed
  - id: "dates"
    content: "Add the missing Date line to the four Cursor-era plans"
    status: completed
  - id: "status"
    content: "Add a Status section to every plan, with commit and pull request links"
    status: completed
  - id: "metrics"
    content: "Add an AIMetrics section to every plan, back-filled from the authoring commits"
    status: completed
  - id: "follow-up"
    content: "Create the low priority issue proposing a plan document check (#203)"
    status: completed
  - id: "plan-doc"
    content: "Save this plan document into agents/plans"
    status: completed
isProject: false
---
# Plan: Plan document conventions — dates, status and AI metrics (issues #190, #191, #192)

Date: 2026-11-14
Scope: `agents/plans/`, `agents/rules/` and `AGENTS.md`.
Issue: [uksrc/Calycopis-broker#190](https://github.com/uksrc/Calycopis-broker/issues/190) — Add dates to plan filenames
Siblings: [#191](https://github.com/uksrc/Calycopis-broker/issues/191) — Add AI Metrics to our plans; [#192](https://github.com/uksrc/Calycopis-broker/issues/192) — Add done marker to our plans
Branch: `20261114-zrq-plan-conventions`

## Target

Our 13 saved plans above predate the conventions the notes already follow, and
nothing told a future agent what a plan file should look like. Success means:

* Every plan file is named `YYYYMMDD-NN-<kebab-slug>-<id8>.plan.md`, with the date
  taken from the plan's `Date:` field or, for the four undated plans, from the
  note that records the session that saved them.
* Every plan ends with a `## Status` section (`status: pending|in-progress|implemented|on-hold|rejected`
  plus details with commit and pull request links) and a `## AIMetrics` section,
  both visible when the plan is rendered.
* The four Cursor-era plans gain the `Date:` line they were missing, so the
  filename date is recorded in the document.
* A new rule, `agents/rules/plan-documents.mdc`, defines the convention, and
  `ai-metrics.mdc` records the plan-document exception.
* A low-priority issue proposes the enforcement we deliberately are not adding
  now.
* No Java, OpenAPI, database, test or deployment change.

## Findings (main @ e52fbede)

* `agents/plans/` held 13 `*.plan.md` files. Each starts with `<!-- <uuid> -->`,
  then a `todos`/`isProject` frontmatter, then a body. Nine had a `Date:` line,
  all matching the commit that added the plan.
* The four without any header fields — `Androcles sha256 race test`,
  `Auth identity implementation`, `Costs Metrics Demo`, `Request Builder Tool` —
  were added by two Cursor-era commits on 2026-06-15 (`510dc6b1`, `c5c3a36b`)
  that carry no AIMetrics block. The notes record the sessions that saved each
  one: `20260526-04-identity-auth.txt:310`, `20260603-01-demo-plan.txt:995`,
  `20260609-02-execution-template.txt:426`, `20260610-02-bug-fixing.txt:258`.
* For every other plan, the commit that added it carries an AIMetrics block, so
  the authoring agent and model were recoverable exactly: `@deepseek-ai/dsh`
  0.2.0-rc.2 (`deepseek-flash`), 0.1.5-rc.3 (`deepseek-v4-flash`) for
  `93298bfd`, and 0.2.0-rc.2 (`deepseek-v4.1-flash-1017`) for `5d860648`. The
  Cursor-era files committed in the same sessions record `Cursor CLI`
  2026.02.13-41ac335, `Claude 4.6 Opus (Thinking)`.
* All 13 plans describe work that was implemented. Evidence per plan is in the
  status table below.
* The frontmatter `todos:` were stale in most plans: nine still showed the
  "Commit through `bin/agent-commit`" item as `pending` even though the commit
  exists. The `todos` array is a historical record; `## Status` is what answers
  "is this plan done".
* Nothing outside `notes/` referenced a plan by filename, so the rename was safe.
  The notes references are a historical record and were not rewritten.
* `AGENTS.md` was 71,164 bytes, and the injected copy is truncated at 65,126, so
  the two additions there were kept short (#166 tracks reducing the file).

## Decisions

1. **Filename scheme (user-confirmed):** `YYYYMMDD-NN-<kebab-slug>-<id8>.plan.md`.
   The same date-based shape as the notes, keeping the existing 8-hex id so the
   first-line UUID, the old name and the references in old notes stay traceable.
2. **Dates for the four undated plans (user-confirmed):** the note date, not the
   adding commit date — 2026-05-26, 2026-06-03, 2026-06-09, 2026-06-10.
3. **Sections at the end (user-confirmed):** `## Status` then `## AIMetrics`, both
   rendered, mirroring the rule for GitHub issue and pull request bodies.
4. **One entry per change, appended.** Each plan's AIMetrics array is back-filled
   in commit order: one entry per commit that touched the plan and carries an
   AIMetrics block, one inferred entry for the plans that predate the rule, and
   one entry for this session's change.
5. **Back-filled contribution values are computed, not guessed:** creation
   entries are `100`; modification entries are
   `round(100 * (added + deleted) / line count)` from `git show --numstat`.
6. **Inferred entries:** name, version and model from the sibling files of the
   same sessions; `timestamp` is the note date at `T00:00:00`, because only the
   date is known.
7. **Status is authoritative over `todos:`.** The frontmatter is not retro-edited.
8. **`implemented` means complete, verified and committed**; the pull request is
   the record, so a plan can carry its final status in the PR that introduces it.
9. **Enforcement is deferred (user-confirmed):** no script and no CI step now;
   issue #203 proposes both.
10. **Plan documents carry no licence header**, as before: they start with the
    UUID comment and the frontmatter, and the new rule says so.
11. **Two commits, not three.** The rename and the block additions touch the same
    13 files, so separating them would only work from an intermediate tree. The
    convention is one commit, and the rename plus retrofit is the other.

## The convention

`agents/rules/plan-documents.mdc` defines the filename, the document structure,
the `## Status` vocabulary (`pending`, `in-progress`, `implemented`, `on-hold`,
`rejected`) with the requirement to link the commit and pull request, and the
`## AIMetrics` block in the same fenced `text` format that a GitHub body uses.

`agents/rules/ai-metrics.mdc` gains one paragraph under the Markdown format: a
saved plan under `agents/plans/` is the exception to the header-comment form, and
its block is the `## AIMetrics` section at the end.

`AGENTS.md` gains the rules table row and a longer `agents/plans/` bullet in the
directory layout.

## Renames

| From | To | Date from |
|---|---|---|
| `Auth identity implementation-e0232013.plan.md` | `20260526-01-auth-identity-implementation-e0232013.plan.md` | note `20260526-04` |
| `Costs Metrics Demo-a14b4c80.plan.md` | `20260603-01-costs-metrics-demo-a14b4c80.plan.md` | note `20260603-01` |
| `Request Builder Tool-f96e072b.plan.md` | `20260609-01-request-builder-tool-f96e072b.plan.md` | note `20260609-02` |
| `Androcles sha256 race test-8e26684a.plan.md` | `20260610-01-androcles-sha256-race-test-8e26684a.plan.md` | note `20260610-02` |
| `Demo Agent Stack Update-93298bfd.plan.md` | `20260924-01-demo-agent-stack-update-93298bfd.plan.md` | `Date:` |
| `Update AGENTS paths-9cde7c81.plan.md` | `20261009-01-update-agents-paths-9cde7c81.plan.md` | `Date:` |
| `Add container labels-0a0d3c23.plan.md` | `20261010-01-add-container-labels-0a0d3c23.plan.md` | `Date:` |
| `Add user defined labels-5958e79b.plan.md` | `20261010-02-add-user-defined-labels-5958e79b.plan.md` | `Date:` |
| `Simplify the debug statistics-5b2e2d5f.plan.md` | `20261011-01-simplify-the-debug-statistics-5b2e2d5f.plan.md` | `Date:` |
| `Profiling statistics-de86145d.plan.md` | `20261011-02-profiling-statistics-de86145d.plan.md` | `Date:` |
| `Separate compute query timings-932a86ec.plan.md` | `20261011-03-separate-compute-query-timings-932a86ec.plan.md` | `Date:` |
| `Zero delay starting blocks-df0fd372.plan.md` | `20261013-01-zero-delay-starting-blocks-df0fd372.plan.md` | `Date:` |
| `Reserve the reservation window-5d860648.plan.md` | `20261114-01-reserve-the-reservation-window-5d860648.plan.md` | `Date:` |

Sequence order within a shared date follows authorship time: 2026-10-10 labels
(09:36) before user labels (10:21); 2026-10-11 debug statistics (06:18),
profiling (11:36), separate query (14:24).

## Status assignment

| Plan | status | Evidence |
|---|---|---|
| `20260526-01-auth-identity-implementation` | implemented | `1d38b409` (A&B), `c1d47f4c` (C), `32b079d9` (D), `445c7ff3`, `666c3ea8`; [PR #3](https://github.com/uksrc/Calycopis-broker/pull/3) |
| `20260603-01-costs-metrics-demo` | implemented | `855eb0fe`, `1c19aea2`; [PR #5](https://github.com/uksrc/Calycopis-broker/pull/5) |
| `20260609-01-request-builder-tool` | implemented | `44bc021f`; [PR #6](https://github.com/uksrc/Calycopis-broker/pull/6) |
| `20260610-01-androcles-sha256-race-test` | implemented | `74dec1b5`, merged `87f0379c`; both tests passed in note `20260610-02` |
| `20260924-01-demo-agent-stack-update` | implemented | `a6c41917`; [PR #125](https://github.com/uksrc/Calycopis-broker/pull/125) |
| `20261009-01-update-agents-paths` | implemented | 4 commits; [PR #140](https://github.com/uksrc/Calycopis-broker/pull/140); deployment narrative deferred to #198 |
| `20261010-01-add-container-labels` | implemented | `a55b93a5`; [PR #143](https://github.com/uksrc/Calycopis-broker/pull/143) |
| `20261010-02-add-user-defined-labels` | implemented | `13c0a390`, `0af17508`, `7186c6e5`; PR #143; mock round trip tracked by #142 |
| `20261011-01-simplify-the-debug-statistics` | implemented | `0c6bdc41`; [PR #146](https://github.com/uksrc/Calycopis-broker/pull/146) |
| `20261011-02-profiling-statistics` | implemented | `8d8f3791`; [PR #148](https://github.com/uksrc/Calycopis-broker/pull/148); the manual SQL check has no recorded result |
| `20261011-03-separate-compute-query-timings` | implemented | `0d8c864a`; [PR #150](https://github.com/uksrc/Calycopis-broker/pull/150) |
| `20261013-01-zero-delay-starting-blocks` | implemented | `807d5643`, `60279018`; [PR #154](https://github.com/uksrc/Calycopis-broker/pull/154) |
| `20261114-01-reserve-the-reservation-window` | implemented | `941e9504`; [PR #202](https://github.com/uksrc/Calycopis-broker/pull/202) |

The details text in each plan names the commits, the pull request, and any plan
item that is still open: the deferred deployment narrative (#198), the unrun mock
round trip (#142), and the profiling SQL check.

## AIMetrics back-fill

Every plan carries, in order, the entries listed in the plan that introduced this
convention. In summary:

* The four Cursor-era plans get one inferred entry: `Cursor CLI`,
  2026.02.13-41ac335, `Claude 4.6 Opus (Thinking)`, `timestamp` at `T00:00:00` on
  the note date, `100`.
* The nine later plans get one entry per commit that touched them and carries an
  AIMetrics block, in commit order, with the interval and identity from that
  commit. Creation entries are `100`; later edits use the computed percentage.
* Every plan gets one entry for this session (`@deepseek-ai/dsh` 0.2.0-rc.2,
  `deepseek-v4.1-flash-1017`), with the interval covering the retrofit and the
  computed percentage of the document changed.

## Follow-up issue

[#203](https://github.com/uksrc/Calycopis-broker/issues/203) — Add a check for
plan document naming and required sections. Low priority; proposes a
`bin/check-plans` script and an optional CI step, with no enforcement today.

## Verification

| Check | Result |
|---|---|
| Names | 14 files under `agents/plans/`, all matching `YYYYMMDD-NN-<kebab-slug>-<id8>.plan.md` |
| Date agreement | every `Date:` line matches the filename date; the four Cursor-era plans gained one |
| Sections | the last two `##` headings are `## Status` then `## AIMetrics`; the status value is in the vocabulary |
| Metrics JSON | the fenced block parses as a JSON array; entry counts match the back-fill |
| Renames | `git status --short` shows 13 `R` renames |
| Stale references | `git grep` for the old basenames outside `notes/` finds nothing |
| AGENTS.md | 71,164 to 71,587 bytes; the file remains over the injection budget (#166) |
| Code | no Java, OpenAPI or Python change, so no Maven build or pytest run was needed |

## Assumptions and out of scope

* The four Cursor-era dates come from the note that records the plan being saved;
  the issue's fallback (the adding commit date) would have given 2026-06-15 for
  all four.
* Inferred AIMetrics entries use midnight because only the date is known.
* The `todos:` frontmatter is left untouched even where it is stale.
* Historical notes keep the old plan names.
* Plans in other Calycopis projects are out of scope; the rule lives in this
  repository.
* The three issues are closed by the pull request body's `Closes` keywords.
* `20261114-01` is the reservation-window plan; this session's plan is `-02`.

## Status

status: implemented
details: Complete and verified on branch `20261114-zrq-plan-conventions`, in two
  commits: the convention in `agents/rules/` and `AGENTS.md`, then the rename and
  the retrofit of all thirteen plans. The pull request against
  `uksrc/Calycopis-broker` `main` is the record, and it closes #190, #191 and
  #192. The proposed check is #203.

## AIMetrics

```text
AIMetrics: [
    {
    "interval": "2026-11-14T14:50:00/2026-11-14T15:25:00",
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
