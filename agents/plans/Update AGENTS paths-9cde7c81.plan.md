<!-- 9cde7c81-a2e0-4cc6-b188-08645a3ae3d9 -->
---
todos:
  - id: "save-plan"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "new-section"
    content: "Add 'Container paths and environment variables' section to AGENTS.md"
    status: completed
  - id: "workspace-paths"
    content: "Replace the hardcoded /Calycopis workspace paths with ${CALYCOPIS_CODE} / ${TREBULA_CODE}"
    status: completed
  - id: "historical-notes"
    content: "Mark the Development platform and Docker service sections as an earlier deployment"
    status: completed
  - id: "calycopis-env"
    content: "Fix the stale CALYCOPIS_CODE in calycopis.env and add its AIMetrics block"
    status: completed
  - id: "deferred-dsh-home"
    content: "Reconcile the two meanings of DSH_HOME (deferred by the user)"
    status: pending
  - id: "deferred-narrative"
    content: "Rewrite the deployment narrative for the current single-container launch (deferred by the user)"
    status: pending
  - id: "deferred-env-vars"
    content: "Consider adding TREBULA_CODE and sibling variables to calycopis.env / calycopis.vars"
    status: pending
isProject: false
---
# Plan: Update AGENTS.md for mount-dependent container paths

Date: 2026-10-09
Scope: repository root (all paths below relative to it, `${CALYCOPIS_CODE}` at runtime)
Issue: [uksrc/Calycopis-broker#139](https://github.com/uksrc/Calycopis-broker/issues/139) — Update the AGENTS file to reflect new locations

## Target

The paths inside the development container depend on the `--volume` mounts of the
`podman run` command that launched it. The AGENTS.md file still described the paths of
an earlier deployment, so agents reading it were pointed at directories that no longer
exist. The new guidance is that the launch command passes a matching `--env` value for
each mount, and agents locate things through those variables.

## Findings

Paths in AGENTS.md checked against the live container:

| Path written in AGENTS.md | Status | Correct value |
|---|---|---|
| `/Calycopis/Calycopis-broker/Calycopis-broker-uksrc-zrq/` | missing | `${CALYCOPIS_CODE}` = `/Calycopis/Calycopis-broker` |
| `/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/` | missing | `${TREBULA_CODE}` = `/Calycopis/Calycopis-openapi` |
| `/etc/calycopis`, `/var/calycopis/{log,data}` | missing | not mounted by this launch |
| `/opt/dsh` | present but unused | `DSH_HOME=/Zarquan/lithosia-quadra/dsh` |

Seven hardcoded workspace paths were replaced (AGENTS.md lines 289, 290, 294, 295, 298,
1207 and 1236 in the original numbering): six under the Calycopis-openapi project and one
in the Python test command.

Supporting evidence: the [launch notes](https://github.com/Zarquan/lithosia-quadra/blob/24ebc1dae6813ab00d84574dfc52fd7e8b6add26/notes/20261007-01-launch.txt#L112-L129)
set `CALYCOPIS_CODE`, `TREBULA_CODE`, `LITHOSIA_CODE` and `DSH_HOME` as `--env` options
alongside the matching `--volume` mounts.

## Changes made

1. **New section `## Container paths and environment variables`**, placed immediately after
   the project introduction so it is read before any path is used. It states that the paths
   are decided by the launch command, lists the four variables set by the current launch with
   example values, names the runtime directory variables that actually exist in the deployment
   variable files (`CALYCOPIS_BROKER_CONFIG_PATH`, `CALYCOPIS_BROKER_LOGS`,
   `CALYCOPIS_TEST_DATA_PATH`), requires the `${VAR:?}` shell form instead of
   hardcoded absolute paths, says not to guess an unset path, and explains that variables
   locate things while literal paths inside configuration content stay literal.

2. **Workspace paths replaced** with `${CALYCOPIS_CODE}` and `${TREBULA_CODE}`, with a
   cross-reference added at the first path in the "OpenAPI schema" section.

3. **Historical notes added** at the top of `## Development platform` and `## Docker service`,
   recording that those subsections describe an earlier four-container deployment (kept as
   examples), and pointing at the new section. The command bodies were deliberately left
   untouched.

4. **`calycopis.env` fixed.** It declared `CALYCOPIS_CODE` as the old
   `/Calycopis/Calycopis-broker/Calycopis-broker-uksrc-zrq` path, which does not exist in this
   container and silently overrode the launch value when sourced. It now matches the launch
   and `CALYCOPIS_HOME`. The file had no AIMetrics block, so one was added, along with a
   comment that the paths must match the launch command.

5. **AIMetrics header updated** in AGENTS.md with a new entry for this session.

## Notes and caveats

- AGENTS.md is 69 KB and the workspace instruction budget is 65,536 bytes, so the injected
  copy of the file is truncated before the end. The new section was kept deliberately short,
  but the file would benefit from being trimmed or split.
- The historical sections still contain command examples with stale paths
  (`--volume "${CALYCOPIS_ROOT:?}:/Calycopis:rw,z"`, `--volume "${DSH_HOME:?}:/opt/dsh:rw,z"`).
  The notes are the mitigation for now; the rewrite is deferred.

## Deferred (agreed with the user)

1. **`DSH_HOME` has two meanings.** An earlier launch treated it as the host path mounted at
   `/opt/dsh`; the current launch sets it to the in-container configuration path. The new
   section records the ambiguity rather than resolving it, so the deferred rewrite can pick
   one meaning.
2. **Deployment narrative.** The `## Development platform` and `## Docker service` sections
   describe four containers (`calycopis-dev`, `calycopis-db-host`, `calycopis-pytest`,
   `calycopis-dsh`), anonymous volumes, the `/etc/calycopis` configuration chain, port 3081
   and `CALYCOPIS_DB_HOST`. The current launch is a single container. This needs its own issue.
3. **`calycopis.env` vs `calycopis.vars`.** The newer scripts (for example
   `tests/python/bin/run-tests.sh`) use `calycopis.vars` with the newer variable names and do
   not define `CALYCOPIS_CODE`; the older file was only given the minimal stale-path fix here.
   Adding `TREBULA_CODE` and the sibling variables belongs with the deployment-narrative work.

## Verification

- `grep -n -E '/Calycopis/(Calycopis-broker|Calycopis-openapi)' AGENTS.md` returns no stale
  workspace paths.
- Every variable named in the new section exists in the container environment
  (`CALYCOPIS_CODE`, `TREBULA_CODE`, `LITHOSIA_CODE`, `DSH_HOME`), and the runtime
  directory names match `calycopis.vars`.
- Diff reviewed for the AIMetrics and licence-header conventions.
