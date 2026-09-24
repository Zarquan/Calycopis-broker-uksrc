# Multi-Broker Costs and Metrics Demonstration

This demonstration deploys four Calycopis Execution Broker instances, each
configured with different cost and metric profiles, to show how users can
compare offers and select the best platform for their workload.

## Architecture

Each broker node (`alpha`, `beta`, `gamma`, `delta`) is a Podman pod running a
PostgreSQL container and a broker container:

```
┌─ calycopis-net-alpha ─┐   ┌─ calycopis-net-beta ─┐   ┌─ calycopis-net-gamma ─┐   ┌─ calycopis-net-delta ─┐
│  calycopis-pod-alpha  │   │  calycopis-pod-beta  │   │  calycopis-pod-gamma  │   │  calycopis-pod-delta  │
│  PostgreSQL :5432     │   │  PostgreSQL :5432     │   │  PostgreSQL :5432     │   │  PostgreSQL :5432     │
│  Broker :8082         │   │  Broker :8082         │   │  Broker :8082         │   │  Broker :8082         │
│  (Green HPC)          │   │  (Cloud)              │   │  (Budget)             │   │  (General Purpose     │
└───────────────────────┘   └───────────────────────┘   └───────────────────────┘   │   Cloud, spare node)   │
                                                                                     └───────────────────────┘
```

Configuration is kept in **Podman named volumes** created by the deployment
scripts, so no configuration directories are bind-mounted from the host:

| Volume | Mounted at | Contents |
|--------|-----------|----------|
| `calycopis-broker-config-<node>` | `/etc/calycopis` | `admin.yaml`, `database.yaml`, `spring.yaml`, `metrics.yaml` |
| `calycopis-database-config-<node>` | `/etc/postgres` | `pgusername`, `pgpassword` |
| `calycopis-broker-logs-<node>` | `/var/calycopis/log` | broker log files (not needed to retrieve container output) |

### Broker Profiles

The cost/metric profile for each broker is defined in
`demo/config/broker-<node>/metrics.yaml`:

| Broker | Profile | Monetary | Energy (kWh) | Carbon (gCO2) | Compute | IO (MB/s) |
|--------|---------|----------|--------------|---------------|---------|-----------|
| Alpha  | Green HPC | $0.30-0.80 | 0.01-0.03 | 2-8 | 250-300 | 800-1200 |
| Beta   | Cloud | $0.05-0.15 | 0.05-0.15 | 15-40 | 120-160 | 200-400 |
| Gamma  | Budget | $0.02-0.08 | 0.10-0.30 | 30-80 | 60-90 | 50-100 |
| Delta  | General Purpose Cloud (spare) | $0.05-0.15 | 0.05-0.15 | 15-40 | 120-160 | 200-400 |

## Prerequisites

- Podman
- `pwgen`, `yq` (mikefarah), `jq`, `curl`
- The broker container image (e.g. `images.dev.uksrc.org/calycopis/calycopis-broker:1.0.7-SNAPSHOT`)
- The developer-tools container image (`ghcr.io/ivoa/calycopis/developer-tools:2026.09.17`)
- The DSH (DeepSeek Harness) container to build `demo/build/hosts.yaml`

See `notes/zrq/20260924-02-costs-demo.txt` for the full walk-through.

## Deployment

### 1. Clean everything from Podman

```bash
source "${HOME}/calycopis.env"
bin/test-clear.sh
```

### 2. Generate the demo user accounts

```bash
rm -f demo/build/users.yaml && mkdir -p demo/build && touch demo/build/users.yaml
for user_name in "alice" "bob" "gonzo" "cyberion"
do
    export user_name
    export user_pass=$(pwgen 32 1)
    yq eval '
        .calycopis.demo.users = (.calycopis.demo.users // []) + [{"name": strenv(user_name), "pass": strenv(user_pass)}]
        ' -i demo/build/users.yaml
done
```

### 3. Deploy each broker node

For each node in `alpha beta gamma delta`, create an override file and run the
deployment scripts (host side) which configure the broker and database through
the `developer-tools` container into the podman volumes:

```bash
portnum=8080
for nodename in alpha beta gamma delta
do
    export OVERRIDE_VARS=$(mktemp)
    cat > "${OVERRIDE_VARS}" << EOF
CALYCOPIS_POD_NAME=calycopis-pod-${nodename}
CALYCOPIS_NET_NAME=calycopis-net-${nodename}
CALYCOPIS_NODE_NAME=${nodename}
CALYCOPIS_BROKER_HOSTNAME=calycopis-broker-${nodename}
CALYCOPIS_BROKER_CONTAINER_NAME=calycopis-broker-${nodename}
CALYCOPIS_BROKER_CONFIG_VOLUME=calycopis-broker-config-${nodename}
CALYCOPIS_BROKER_LOGS_VOLUME=calycopis-broker-logs-${nodename}
CALYCOPIS_BROKER_CONTAINER_IMAGE=images.dev.uksrc.org/calycopis/calycopis-broker:1.0.7-SNAPSHOT
CALYCOPIS_DATABASE_HOSTNAME=calycopis-database-${nodename}
CALYCOPIS_DATABASE_CONTAINER_NAME=calycopis-database-${nodename}
CALYCOPIS_DATABASE_CONFIG_VOLUME=calycopis-database-config-${nodename}
CALYCOPIS_BROKER_INTERNAL_PORT=8082
CALYCOPIS_BROKER_EXTERNAL_PORT=${portnum}
EOF

    bin/test-outer.sh     # creates the pod/network and configures broker + database
    bin/test-users.sh     # creates the demo user accounts on the broker

    portnum=$((portnum+1))
done
```

The configuration scripts (`bin/config-broker.sh`, `bin/config-database.sh`,
`bin/config-users.sh`) run inside the `developer-tools` container and write to
the mounted volumes.

### 4. Build `demo/build/hosts.yaml`

From inside the DSH container, resolve each broker's network address and
record it as an endpoint (used by the agent tools):

```bash
rm -f demo/build/hosts.yaml && touch demo/build/hosts.yaml
for nodename in alpha beta gamma delta
do
    ipv4address=$(
        podman inspect "calycopis-broker-${nodename}" \
        | jq -r ".[0].NetworkSettings.Networks.\"calycopis-net-${nodename}\".IPAddress"
        )
    export nodename
    export endpoint="http://${ipv4address}:8082/actuator/health"
    yq eval '
        .calycopis.demo.hosts = (.calycopis.demo.hosts // []) + [{"name": strenv(nodename), "endpoint": strenv(endpoint)}]
        ' -i demo/build/hosts.yaml
done
```

### 5. Render the environment file

```bash
demo/bin/make-demo-env.sh
```

This writes `demo/run/demo-user.env` (`DEMO_USER`, `DEMO_PASS`, and one
`BROKER_<NAME>_URL` per host) from `demo/build/hosts.yaml` + `users.yaml`.

### 6. Run the smoke test

```bash
source demo/run/demo-user.env
python3 demo/bin/smoke-test.py
```

Verifies each broker returns offers with its configured cost/metric values and
that the captured container stdout/stderr are available through the session
connectors.

### 7. Start the interactive client

```bash
podman run -it \
    --pod calycopis-pod \
    --volume "$(pwd)/demo:/workspace:z" \
    --volume "$(pwd)/demo/USER-AGENT.md:/workspace/AGENTS.md:ro,z" \
    --env-file "$(pwd)/demo/run/demo-user.env" \
    calycopis/demo-client:latest \
    /bin/bash
```

The second `--volume` bind-mounts `USER-AGENT.md` as `AGENTS.md` at the
workspace root so that Cursor picks it up automatically. Inside the container,
start Cursor:

```bash
cursor /workspace
```

The agent instructions in `demo/USER-AGENT.md` point the AI at the
`agents/skills/calycopis-broker/` skill and the `bin/broker` CLI / `broker_tools`
library.

### 8. Teardown

```bash
bin/test-clear.sh
```

Removes all pods, containers, volumes, and networks.

## Demo Scenario

1. **User**: "Run a Docker container that generates 1000 random numbers and computes statistics"

2. **Agent** creates an `ExecutionRequest` and submits to all four brokers

3. **Agent** presents a comparison table (one column per broker)

4. **User** selects based on preference:
   - **Fastest**: Alpha (highest compute score)
   - **Cheapest**: Gamma (lowest monetary cost)
   - **Greenest**: Alpha (lowest carbon/energy)

5. **Agent** accepts the selected offer and monitors execution

6. **Agent** displays the final result with phase, messages, connectors, and the
   captured container stdout and stderr — retrieved through the session
   connectors, not from broker log files

## Session Connectors

Every Docker execution session advertises two session connectors (stdout-get
and stderr-get) with HTTP GET locations:

- `GET /sessions/{uuid}/docker/stdout-get`
- `GET /sessions/{uuid}/docker/stderr-get`

`bin/broker accept` and `bin/broker monitor` fetch both streams automatically;
`broker_tools.output.get_container_output()` provides the same access
programmatically. See `agents/skills/calycopis-broker/reference.md`.

## Directory Structure

```
demo/
├── README.md                     # This file
├── USER-AGENT.md                 # Cursor AI agent instructions
├── agents/
│   ├── rules/                    # Coding rules (licence header, AIMetrics, ...)
│   └── skills/calycopis-broker/
│       ├── SKILL.md              # Agent skill: standard broker workflow
│       └── reference.md          # URN labels, kinds, connectors, state schema
├── bin/
│   ├── broker                    # CLI for the agent workflow
│   ├── broker_tools/             # Shared Python library (client, offers, output, ...)
│   ├── make-demo-env.sh          # Render run/demo-user.env from build/*.yaml
│   └── smoke-test.py             # Verify deployment (offers + connectors)
├── build/                        # Generated at deploy time (gitignored)
│   ├── hosts.yaml                # Broker name -> endpoint
│   └── users.yaml                # Demo user accounts
├── config/
│   └── broker-<node>/metrics.yaml  # Cost/metric profile per broker
├── docker/
│   └── demo-client/              # Client container image (Python + Cursor CLI)
└── run/                          # Generated at runtime (gitignored)
    └── demo-user.env             # Demo user credentials + broker URLs
```