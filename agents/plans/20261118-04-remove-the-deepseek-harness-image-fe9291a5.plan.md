<!-- fe9291a5-9eb5-4c92-8a15-93d230d06b1e -->
---
todos:
  - id: "branch"
    content: "Create a branch from main at d00f7b6a for issue 195"
    status: completed
  - id: "remove-image"
    content: "Remove docker/deepseek-harness and its build target in docker/bin/build.sh"
    status: completed
  - id: "docs"
    content: "Update docs/deployment.md so it no longer describes a local harness image"
    status: completed
  - id: "verify"
    content: "Verify that no active build, workflow or deployment path references the image"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: pending
isProject: false
---
# Plan: Remove the deepseek-harness image

Date: 2026-11-18
Scope: `Calycopis-broker` — remove the obsolete `docker/deepseek-harness` image
  definition and its build target, and correct the deployment guide so it no
  longer presents the image as part of this project.
Issue: [uksrc/Calycopis-broker#195](https://github.com/uksrc/Calycopis-broker/issues/195) — Deprecate/remove the deepseek-harness image.
Branch: `20261118-zrq-remove-deepseek-harness`, created from `main` at `d00f7b6a`.
Result: implemented and verified; commit and pull request await approval.

## Target

The repository no longer defines, builds or advertises a DSH image. The
`docker/bin/build.sh` script builds only the `fedora-base` and `developer-tools`
images, the `docker/deepseek-harness/` directory is gone, and the deployment
guide says that the harness image and its launch live in
[`lithosia-quadra`](https://github.com/Zarquan/lithosia-quadra) rather than in
this project.

The Dockerfile remains recoverable from git history. Historical notes that
record past builds are left unchanged, because a note is a record of what
happened rather than current guidance.

## Where we are

* `docker/deepseek-harness/Dockerfile` is the only image definition for the
  harness. It is a thin layer over `developer-tools` that installs Node.js,
  pnpm and `@deepseek-ai/dsh`, adds the `dsh-proxy` web plugin, exposes port
  `3081`, and declares `VOLUME /root/.dsh`.
* `docker/bin/build.sh` builds it as `calycopis/deepseek-harness:latest` and
  `calycopis/deepseek-harness:${buildtag}`, alongside `fedora-base` and
  `developer-tools`.
* `docker/bin/push.sh` pushes only `fedora-base` and `developer-tools`; the
  repository's push script does not publish the harness image to `ghcr.io`.
* No `.github/workflows/` job, `calycopis.vars`, `docker/compose/` file or
  runtime configuration references the image.
* [`docs/deployment.md`](../../docs/deployment.md) already records that the
  harness moved to `lithosia-quadra` (lines 209–213 and 238–241), but it still
  describes the local `deepseek-harness` layer as "described below for
  reference" (lines 229–236). That description is now stale: the section it
  links to, "Running the DSH web container", documents the `lithosia-quadra`
  container, not this Dockerfile.
* `notes/zrq/` records older builds and launches of the image. Those notes are
  historical records, not instructions, and stay as they are.

## Decision

Remove the image definition and its build target, rather than deprecate it in
place or move it to `attic/`.

* The image is no longer part of the current deployment; the deployment guide
  already says the harness runs from `lithosia-quadra`. Keeping a build target
  would continue producing an image that nothing launches.
* The definition is a single small text file. Git history preserves it, so an
  `attic/` copy adds a second stale copy without adding recoverability.
  `attic/` is used for larger retired source trees, not for a one-file image
  layer.
* Deprecating in place would mean keeping dead build code and a warning in a
  file nobody reads. Removing it is the smaller, clearer change.

If reviewers prefer an archival copy, the fallback is
`attic/docker/deepseek-harness/Dockerfile` with the build target removed. That
is not the plan.

## Implementation outline

### Stage 1 — Branch

Create `20261118-zrq-remove-deepseek-harness` from `main` at `d00f7b6a`.
Apart from this plan, the working tree has one unrelated untracked note
(`notes/zrq/20261118-01-house-keeping.txt`); leave it out of the change.

### Stage 2 — Remove the image and its build target

1. Delete `docker/deepseek-harness/` (only `Dockerfile` is in it).
2. In `docker/bin/build.sh`, remove the third `podman build` block:
   the two `--tag "calycopis/deepseek-harness:..."` options and the
   `docker/deepseek-harness` build context. Keep the `fedora-base` and
   `developer-tools` blocks unchanged.
3. Update the `docker/bin/build.sh` header:
   * bump `Copyright (c) 2024` to `Copyright (c) 2026`, because the file is
     modified;
   * append an `AIMetrics` entry for this session to the existing header, in
     the usual `interval` form (the file has no AI metrics block at present, so
     add one).

No change is made to `docker/bin/push.sh`: it already omits the image, and
adding a "no longer pushed" entry would be noise. `AGENTS.md` does not name the
image in its directory layout, so it needs no edit.

### Stage 3 — Correct the deployment guide

In [`docs/deployment.md`](../../docs/deployment.md):

1. Replace the clause at lines 212–213 ("the `deepseek-harness` layer formerly
   used for `calycopis-dsh` is described below for reference") with a sentence
   saying that this repository no longer defines or builds a harness image.
2. Delete the `deepseek-harness` bullet at lines 229–236, including the
   `EXPOSE 3081` and `VOLUME /root/.dsh` details and the link to
   "Running the DSH web container".
3. Keep the following paragraph (lines 238–241) that says the harness image and
   launch moved to `lithosia-quadra`; replace its "The image above" with "It"
   so it no longer points at the `developer-tools` bullet above it.
4. Keep "Running the DSH web container" and the earlier four-container
   architecture note. They describe the `lithosia-quadra` container and an
   earlier deployment, not the removed image.
5. Append an `AIMetrics` entry for this session. The document's copyright is
   already 2026.

### Stage 4 — Verify

Run from the repository root:

```bash
test ! -e docker/deepseek-harness
! grep -n "deepseek-harness" docker/bin/build.sh
bash -n docker/bin/build.sh
git diff --check
```

Then confirm the only remaining references to `deepseek-harness` are the
deliberate historical ones:

```bash
grep -rn "deepseek-harness" . \
    --exclude-dir=.git \
    --exclude-dir=agents \
    --exclude-dir=notes
```

The expectation is that `docs/deployment.md` mentions the image only as
removed or as having moved, and no build or deployment file mentions it.

Check the rendered deployment guide for broken links: the deleted bullet's
link to `#running-the-dsh-web-container` must be gone, while the section itself
still exists and its own links are valid.

No Java or Python test suites are needed: this change touches no application
code, schema, configuration or test data. `bash -n` and `git diff --check` are
the relevant checks; running the full suites would not exercise anything that
changed.

### Stage 5 — Commit and pull request

After the exact diff and commit message are approved, commit through
`bin/agent-commit`, push the branch to the fork over HTTPS, and open a pull
request against `uksrc/Calycopis-broker` from `Zarquan:<branch>`. The pull
request body links this plan and
[#195](https://github.com/uksrc/Calycopis-broker/issues/195), and ends with an
`AIMetrics` block.

## Acceptance

* `docker/deepseek-harness/` does not exist.
* `docker/bin/build.sh` builds only `fedora-base` and `developer-tools`.
* `docs/deployment.md` no longer describes a local harness image and points to
  `lithosia-quadra` for the current one.
* No CI workflow, `calycopis.vars`, `docker/compose/` file or runtime script
  references `calycopis/deepseek-harness`.
* Historical `notes/zrq/` entries are unchanged.
* The change contains no application code, so there is no behavioural change
  to the broker, schema or tests.

## Risks and deferred

* An operator with a local `calycopis/deepseek-harness` tag loses the recipe
  from `main`. Git history keeps it, and the current harness image is built by
  `lithosia-quadra`; the deployment guide points there.
* The deployment guide's four-container architecture still lists
  `calycopis-dsh` and port `3081`. That section is explicitly marked as an
  earlier deployment. Rewriting the whole deployment narrative is deferred and
  out of scope for this issue; this change only removes the claim that this
  repository defines the image.
* `docker/python-tester/` is an empty directory and `docker/compose/` is
  retained as superseded material. Both are separate housekeeping items and
  are not touched here.

## Status

status: in-progress
details: Implemented and verified on branch
  `20261118-zrq-remove-deepseek-harness` from `main` at `d00f7b6a`; commit and
  pull request await approval. `bash -n docker/bin/build.sh` passes,
  `git diff --check` is clean, and no active build or deployment path references
  the image.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-18T21:14:14",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-18T21:17:00/2026-11-18T21:35:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 7,
      "units": "%"
      }
    }
  ]
```
