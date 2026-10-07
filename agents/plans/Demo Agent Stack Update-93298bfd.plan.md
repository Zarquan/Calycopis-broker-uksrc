<!-- 93298bfd-d641-4c74-ad42-abf968137780 -->
---
todos:
  - id: "save-plan"
    content: "Save this plan document into agents/plans"
    status: completed
  - id: "client-yaml"
    content: "Generalize broker_tools/client.py + offers.py to N brokers from demo/build/hosts.yaml + users.yaml (env fallback)"
    status: pending
  - id: "output-connectors"
    content: "Rewrite broker_tools/output.py to fetch stdout/stderr via session connectors"
    status: pending
  - id: "session-summary"
    content: "Update broker_tools/session.py: full connector info, stdout + stderr"
    status: pending
  - id: "broker-cli"
    content: "Update bin/broker CLI: stderr reporting, connector statuses, dynamic brokers"
    status: pending
  - id: "digest-probe"
    content: "Update broker_tools/digest.py: remove broker-log parsing, probe + cache only"
    status: pending
  - id: "smoke-connectors"
    content: "Update demo/bin/smoke-test.py with session-connector assertions"
    status: pending
  - id: "make-demo-env"
    content: "Add demo/bin/make-demo-env.sh to render run/demo-user.env from build hosts/users YAML"
    status: pending
  - id: "user-agent-md"
    content: "Update demo/USER-AGENT.md for connectors and volume-based deployment"
    status: pending
  - id: "skill-md"
    content: "Update demo/agents/skills/calycopis-broker/SKILL.md and reference.md"
    status: pending
  - id: "demo-readme"
    content: "Rewrite demo/README.md for the volume-based deployment"
    status: pending
  - id: "deploy-cleanups"
    content: "Deployment-script cleanups: named logs volume, port variables (test-outer.sh, config-users.sh, test-users.sh)"
    status: pending
  - id: "attic-scripts"
    content: "Move old demo/bin/deploy.sh, configure.sh, teardown.sh to attic/"
    status: pending
  - id: "root-agents"
    content: "Touch up root AGENTS.md demo description"
    status: pending
  - id: "notes-result"
    content: "Add Result section to notes/zrq/20260924-02-costs-demo.txt"
    status: pending
  - id: "verify"
    content: "Verify: py_compile all Python tools, grep for stale log references"
    status: pending
isProject: false
---
# Plan: Update the costs-and-metrics demo agent stack for the volume-based deployment and session connectors

Date: 2026-09-24
Scope: repo root (`/Calycopis/Calycopis-broker-uksrc/github-zrq`; all paths below relative to it)

## Review summary — what changed, and what is now stale

### The demo system changed in two ways

1. **Volume-based deployment.** `bin/test-outer.sh` + `bin/test-users.sh` (run on the host) drive
   `bin/config-broker.sh`, `bin/config-database.sh`, `bin/config-users.sh` (run inside the
   `developer-tools` container). Configuration lives in **podman named volumes**:
   - `CALYCOPIS_BROKER_CONFIG_VOLUME` → `/etc/calycopis` (`CALYCOPIS_BROKER_CONFIG_PATH`)
   - `CALYCOPIS_DATABASE_CONFIG_VOLUME` → `/etc/postgres` (`CALYCOPIS_DATABASE_CONFIG_PATH`)
   - Broker logs go to `CALYCOPIS_BROKER_LOGS` (`/var/calycopis/log`) — currently an **anonymous**
     podman volume, no longer a host directory under `run/<broker>/logs/`.
   - Four brokers are deployed: `alpha`, `beta`, `gamma`, `delta` (host ports 8080–8083).
   - Credentials and endpoints are recorded in `demo/build/users.yaml` (alice, bob, gonzo, cyberion)
     and `demo/build/hosts.yaml` (name → endpoint, currently the `/actuator/health` URL).

2. **Session connectors in the broker API.** `DockerSessionConnectorController` adds
   `GET /sessions/{uuid}/docker/stdout-get` and `GET /sessions/{uuid}/docker/stderr-get`
   (`text/plain`, 404 for unknown sessions). Every Docker session advertises two
   `SimpleSessionConnector` entries (kind/status/protocol/location):
   - stdout kind: `https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stdout-get.yaml`
   - stderr kind: `https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stderr-get.yaml`
   - protocol `HTTP`; lifecycle `PREPARING` (no location) → `AVAILABLE` (location set,
     absolute URL built from the request host) → `FINISHED` (location retained).
   - The generated `calycopis_schema_client` (1.0.7.dev1) and the OpenAPI `components.yaml`
     already model `SimpleSessionConnector`; the broker side is implemented and covered by
     `tests/python/docker/test_docker_session_connectors.py`.

### Stale items (gaps found during review)

| # | File | Gap |
|---|------|-----|
| G1 | `demo/bin/broker_tools/output.py` | Parses `run/<broker>/logs/broker.log` on the host with a regex to recover stdout. Broken under the volume deployment and superseded by session connectors. |
| G2 | `demo/bin/broker_tools/digest.py` | Same `LOG_PATHS` log parsing for digest resolution. Host log paths no longer exist; must fall back to the API probe + digest cache only. |
| G3 | `demo/bin/broker_tools/session.py` | `session_summary` lists connectors but drops status/protocol/location; `accept_and_monitor` only retrieves stdout via the log parser. No stderr. |
| G4 | `demo/bin/broker` (CLI) | `accept`/`monitor` print stdout only; no stderr; `--json` output has no connector info. |
| G5 | `demo/bin/broker_tools/client.py` | `BROKERS` hardcodes alpha/beta/gamma; no delta; env-var-only bootstrap. |
| G6 | No env bootstrap | Docs say `source run/demo-user.env`, but the new deployment never generates it — it produces `demo/build/hosts.yaml` + `users.yaml`. Also `hosts.yaml` stores the `/actuator/health` URL, not the API base URL. |
| G7 | `demo/USER-AGENT.md` | Three-broker table, `source run/demo-user.env`, lists broker-log reading as acceptable, no connector description. |
| G8 | `demo/agents/skills/calycopis-broker/SKILL.md` | Gotcha "Container stdout is not in the session API" is now false; prerequisites; CLI reference; 3 brokers. |
| G9 | `demo/agents/skills/calycopis-broker/reference.md` | No connector kinds/lifecycle; env table lacks delta; state schema lacks connectors. |
| G10 | `demo/README.md` | Entire quick-start stale: old `demo/bin` scripts, 3 brokers, `demo/run/`, `platform.yaml`, image names. |
| G11 | `demo/bin/deploy.sh`, `configure.sh`, `teardown.sh` | Old bind-mount style, 3 brokers; superseded by `bin/test-*.sh`/`bin/config-*.sh`. |
| G12 | `demo/bin/smoke-test.py` | 3 brokers, log-parsing digest path, no connector checks. |
| G13 | Root `AGENTS.md` | Line 869 describes `demo/` as "three brokers". |
| G14 | `bin/test-outer.sh` / `bin/config-users.sh` | Hard-coded port `8082` instead of `${CALYCOPIS_BROKER_INTERNAL_PORT}`; anonymous logs volume; redundant `source` in `test-users.sh`. |

## Design decisions (confirmed)

- **D1 — Connectors are the only result-retrieval path.** Delete log-file parsing from the Python
  tools entirely. stdout/stderr come from the session connectors; digest resolution comes from the
  probe-submit + `run/image-digests.json` cache.
- **D2 — `demo/build/hosts.yaml` + `users.yaml` become the single source of truth.** Tools load
  broker names/endpoints from `hosts.yaml` (fallback to `BROKER_*_URL` env vars) and credentials
  from `users.yaml` (fallback to `DEMO_USER`/`DEMO_PASS`). Endpoints recorded as `/actuator/health`
  URLs are normalised to the API base URL.
- **D3 — N-broker generalisation.** Comparison table, broker map, labels, and docs become dynamic.
  Delta is "General Purpose Cloud" (mirrors beta). **Include delta** (user decision).
- **D4 — Retire the old `demo/bin` deployment scripts** to `attic/` (user decision).
- **D5 — Keep a generated env file.** Add `demo/bin/make-demo-env.sh` rendering
  `demo/run/demo-user.env` from the build YAMLs (user decision).
- **D6 — Deployment-script cleanups included now** (user decision): named logs volume, port
  variables, redundant source removal.

## Work items (see todo frontmatter for status)

1. `broker_tools/client.py` + `offers.py` — YAML-first, N-broker loaders and comparison table.
2. `broker_tools/output.py` — connector-based `get_container_output()`; keep `get_container_stdout()` wrapper.
3. `broker_tools/session.py` — full connector entries; stdout + stderr in summaries.
4. `bin/broker` — stderr printing, connector statuses, `--json` fields, dynamic broker map.
5. `broker_tools/digest.py` — remove `LOG_PATHS` parsing; probe + cache only.
6. `demo/bin/smoke-test.py` — connector assertions (stdout/stderr content, 404).
7. `demo/bin/make-demo-env.sh` (new) — render `demo/run/demo-user.env` from build YAMLs.
8. `demo/USER-AGENT.md` — connectors as result path, 4 brokers, updated non-broker commands.
9. `SKILL.md` + `reference.md` — connector model, prerequisites, CLI reference, delta.
10. `demo/README.md` — rewrite for the volume-based deployment and current tree.
11. Deployment-script cleanups — `test-outer.sh` (named logs volume, port var), `config-users.sh`
    (port var), `test-users.sh` (drop redundant source).
12. Move old `demo/bin/deploy.sh`, `configure.sh`, `teardown.sh` to `attic/`.
13. Root `AGENTS.md` — demo description touch-up.
14. Notes — Result section for `notes/zrq/20260924-02-costs-demo.txt`.

## Verification

1. Fresh deploy per the notes loop; `make-demo-env.sh` renders all four `BROKER_*_URL` values.
2. `bin/broker status` → 4/4 OK; `compare` → 4-column table.
3. `accept`/`monitor` show stdout and stderr (stderr-writing workload); `--json` includes them.
4. `smoke-test.py` connector assertions pass.
5. `digest resolve` with cleared cache and no log files on the host.
6. `grep -rn "broker.log\|LOG_PATHS" demo/bin` → no matches.