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
      along with this software. If not, see <http://www.gnu.org/licenses/>.
    </meta:licence>
  </meta:header>

  AIMetrics: [
      {
      "timestamp": "2026-10-09T05:40:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 100,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-10-09T09:08:12",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 10,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-10-09T09:53:19",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 8,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-10-09T16:16:36",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 10,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-10-09T17:13:12",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 3,
        "units": "%"
        }
      },
      {
      "interval": "2026-10-13T11:06:00/2026-10-13T11:20:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 18,
        "units": "%"
        }
      },
      {
      "interval": "2026-10-13T11:33:00/2026-10-13T11:45:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 3,
        "units": "%"
        }
      },
      {
      "interval": "2026-10-15T05:50:00/2026-10-15T05:57:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 2,
        "units": "%"
        }
      },
      {
      "interval": "2026-10-15T06:20:00/2026-10-15T07:50:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 5,
        "units": "%"
        }
      },
      {
      "interval": "2026-10-15T09:30:00/2026-10-15T09:46:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 2,
        "units": "%"
        }
      }
    ]
-->

# Calycopis container deployment

This document describes the containerised development and deployment
environment for the Execution Broker. It was split out of
[`AGENTS.md`](../AGENTS.md) to keep the workspace instructions inside the
agent instruction budget.

> **Status** — the architecture sections describe an **earlier four-container
> deployment** (`calycopis-dev`, `calycopis-db-host`, `calycopis-pytest` and
> `calycopis-dsh`). The current development environment runs the harness and
> the broker in a **single container**, so treat the container names and the
> `podman run` commands as worked examples until this document is rewritten.
> The launch notes record the current pattern.

In-container paths are **not fixed** — they are decided by the `--volume`
mounts of the `podman run` command that launched the container. See
[Container paths and environment variables](../AGENTS.md#container-paths-and-environment-variables)
in AGENTS.md for how to locate things.

## Container images

Development is performed inside the `calycopis-dev` container. It is built
from the `docker/fedora-base/` base image with the tooling added by the
`docker/developer-tools/` layer, and the `calycopis-pytest` container uses
the same `developer-tools` image. The DSH harness now runs in its own image
and container, defined by the
[`lithosia-quadra`](https://github.com/Zarquan/lithosia-quadra) project rather
than by this one; the `deepseek-harness` layer formerly used for
`calycopis-dsh` is described below for reference.

 * The `fedora-base` image is a RedHat Fedora container with the following tools installed:
   * atop, bind-utils, curl, dateutils, diffutils, findutils, git, gnupg, gzip, hostname,
     htop, iotop, ipcalc, jq, less, nano, openssh-clients, patch, procps-ng, pwgen, rsync,
     s3cmd, sed, tar, wget, which, xmlstarlet, yamllint, yq, zip
 * The `developer-tools` layer adds:
   * Java 25 JDK (`java-25-openjdk-devel`) — pinned to a specific version;
     installing the `latest` package caused problems with the Maven compiler plugin
   * Podman
   * Python 3 and pip (`python3`, `python3-pip`)
   * PyYAML (`python3-pyyaml`) for the OpenAPI processor
   * The PostgreSQL client (`postgresql`) for `pg_isready` / `psql`

 * Additional tools can be installed using `dnf` but requires user permission to do so.

 * The `deepseek-harness` image is built from `developer-tools` (via the
   `docker/deepseek-harness/` layer) and adds Node.js, pnpm, and the DeepSeek
   harness itself. It is used for the DSH web container (`calycopis-dsh`):
   * Exposes the DSH web proxy port **3081**.
   * Declares `VOLUME /root/.dsh` so the user's DSH configuration is mounted
     at runtime rather than baked into the image; the web proxy plugin must be
     (re)installed at runtime against that configuration (see
     [Running the DSH web container](#running-the-dsh-web-container)).

 * The harness image and its launch have since moved to the
   [`lithosia-quadra`](https://github.com/Zarquan/lithosia-quadra) project,
   which is reusable across projects. The image above is no longer part of the
   current setup.

## Architecture

The development environment involves four containers, plus the application
containers created by the broker:

 1. **The host machine** — runs the Podman service and owns the host filesystem.
 2. **`calycopis-dev`** — the development container where the agent and the
    Java broker run. It owns the anonymous volumes (see
    [Launching the containers](#launching-the-containers)).
 3. **`calycopis-db-host`** — the PostgreSQL database container.
 4. **`calycopis-pytest`** — the container used to create the test data and
    run the Python test suite.
 5. **`calycopis-dsh`** — the DSH (DeepSeek harness) web container, running
    the DSH harness (agents and web UI) and providing the web proxy on
    port 3081.
 6. **Application containers** (e.g. `heliophorus-cantliei`,
    `heliophorus-androcles`) — created by the broker via the Podman API.

Each task should be run in the container that is intended for it, rather
than in whichever container happens to be convenient:

| Task | Container |
|------|-----------|
| Build and run the Java broker | `calycopis-dev` |
| Create the test data and build/run the Python tests | `calycopis-pytest` |
| Run the DSH harness (agents and web UI) | `calycopis-dsh` |

The three development containers currently share the same base image
(`developer-tools`) and a similar set of tools, so any of them could
technically perform any of the tasks. However, the images are expected to
become more specialised over time for the task they are intended for, so
where possible keep each task in its designated container. Running each
task in its own container also keeps the task-specific tooling (for example
the Python test dependencies in `calycopis-pytest`, or the DSH plugins in
`calycopis-dsh`) installed only where it is needed, and stops one container
from becoming an uncontrolled accumulation of everything.

## Host Podman service

The host system runs Podman as a rootless service (see
https://docs.podman.io/en/latest/markdown/podman-system-service.1.html).
Every development container gets a volume mount mapping the unix socket for
that service, so API calls made inside a container are forwarded to the
Podman service on the host. The `bin/container-host.sh` script discovers the
socket path and exports it as the `CONTAINER_HOST` / `CONTAINER_PATH`
environment variables; it is used by the CI workflow, while locally the
socket is mounted with an explicit `CONTAINER_HOST` environment variable:

```
podman run \
  ....
  --env "DOCKER_HOST=unix:///run/podman/podman.sock" \
  --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
  --volume "${XDG_RUNTIME_DIR}/podman/podman.sock:/run/podman/podman.sock:rw,z" \
  ....
  ....
```

> **WARNING** — mounting the Podman socket into a container exposes the
> user's Podman service to that container.

The socket is what makes the environment container-in-container: the broker
runs in a development container, but the containers it creates are created
by the **host** Podman service, so their bind mounts resolve against the
host filesystem. See
[Host filesystem side effects](#host-filesystem-side-effects).

## Network and pod

The containers run inside a dedicated Podman network and pod:

```bash
podman network create \
    --subnet 172.30.100.0/24 \
    "${CALYCOPIS_NET_NAME:?}"

podman pod create \
    --name "${CALYCOPIS_POD_NAME:?}" \
    --network "${CALYCOPIS_NET_NAME:?}" \
    --publish 8082:8082 \
    --publish 3081:3081
```

 * Port **8082** is the broker service.
 * Port **3081** is the DSH web proxy.

Running the database in the same pod makes it reachable at
`calycopis-db-host:5432` from the other containers, matching the datasource
URL in the configuration files.

## Environment files

Two files at the repository root carry deployment settings. They answer
different questions, and the distinction matters when reading the commands
below.

### `calycopis.env` — where the source is

On the host platform this file lives outside the repository, at
`${HOME}/calycopis.env`, so that each user can point at their own clones:

```bash
source "${HOME:?}/projects.env"
CALYCOPIS_ROOT="${PROJECTS_ROOT}/IVOA/ivoa/Calycopis"

CALYCOPIS_REPO='git@github.com:Zarquan/Calycopis-broker-uksrc.git'
CALYCOPIS_HOME="${CALYCOPIS_ROOT}/Calycopis-broker"
CALYCOPIS_CODE="${CALYCOPIS_HOME:?}/Calycopis-broker-uksrc-zrq"

ISOBEON_REPO='git@github.com:Zarquan/Calycopis-Isobeon.git'
ISOBEON_HOME="${CALYCOPIS_ROOT}/Calycopis-Isobeon"
ISOBEON_CODE="${ISOBEON_HOME:?}/github-zrq"

TREBULA_REPO='git@github.com:Zarquan/Calycopis-openapi-uksrc.git'
TREBULA_HOME="${CALYCOPIS_ROOT}/Calycopis-openapi"
TREBULA_CODE="${TREBULA_HOME:?}/Calycopis-openapi-uksrc-zrq"
```

The copy in the repository mirrors that file inside a container, giving the
same variables at the container's mount paths, so a development or DSH
container finds the source the same way the host does. A variable can be set
even where its volume is not mounted in that instance.

Using variables rather than literal paths is what makes this portable.
Development happens on more than one machine, the clones move, and these paths
have changed repeatedly over the life of the project, so the scripts and the
notes refer to `CALYCOPIS_CODE` and its siblings rather than to a fixed
directory. Getting started is a clone and one local file.

An earlier version of the file also set `CALYCOPIS_ENVIRONMENT` to record
whether it was the host (`desktop`) or a container (`containerized`); the
variable is no longer used.

### `calycopis.vars` — where the deployed services are

A deployed service container does not need the source, but it does need its
configuration paths and the names of the services it connects to:

```bash
CALYCOPIS_POD_NAME=calycopis-pod
CALYCOPIS_NET_NAME=calycopis-network

CALYCOPIS_BROKER_CONFIG_PATH=/etc/calycopis
CALYCOPIS_BROKER_LOGS=/var/calycopis/log
CALYCOPIS_BROKER_INTERNAL_PORT=8082
CALYCOPIS_BROKER_EXTERNAL_PORT=8082

CALYCOPIS_DATABASE_CONFIG_PATH=/etc/postgres
CALYCOPIS_DATABASE_HOSTNAME=calycopis-database
CALYCOPIS_DATABASE_PORT=5432

CALYCOPIS_TEST_DATA_PATH=/var/calycopis/data
```

It is passed to containers with `--env-file` and sourced by the deployment
scripts. `CALYCOPIS_BROKER_CODE` is the one source location it carries, for the
containers that do see a copy of the code.

A deployment can layer a second vars file over it to override individual
settings. The costs-and-metrics demo does exactly that: it sources
`calycopis.vars`, writes a temporary override holding the pod, network,
container, volume and port names for one of four nodes, and runs the
deployment against that. See [`demo/README.md`](../demo/README.md) and
[`notes/zrq/20260924-03-costs-demo.txt`](../notes/zrq/20260924-03-costs-demo.txt).

For paths inside a container, [Container paths and environment variables](../AGENTS.md#container-paths-and-environment-variables)
in AGENTS.md is the authoritative description.

### The agent scratch area

An agent needs somewhere durable for working files that do not belong in a
repository — a generated patch, a pull request description, a hand-off document
before it has a home. `/tmp` disappears with the container, and anything written
in a repository either lands in git or has to be cleaned up, so the deployment
provides a third place, named by `CALYCOPIS_SCRATCH`:

 * container path — `/Calycopis/agents/scratch`
 * host path — `${CALYCOPIS_ROOT}/agents/scratch`
 * variable — `CALYCOPIS_SCRATCH`

It sits at the workspace root, a sibling of the two repository mounts, so
nothing written there can be committed by accident, and **inside the session
workspace** so that an agent shell can write to it. That second point is a
constraint rather than a preference: under the DSH `workspace-write` file policy
the writable roots are the session workspace, `/tmp` and the per-user temporary
directory, so a mount at a fresh top-level path is readable but not writable by
an agent shell. `DSH_HOME` is the example — an `rw` mount that an agent cannot
write.

Create the host directory first, so that Podman does not create it owned by
`root`, and point the host's `${HOME}/calycopis.env` at it:

```bash
mkdir -p "${CALYCOPIS_ROOT:?}/agents/scratch"
CALYCOPIS_SCRATCH="${CALYCOPIS_ROOT:?}/agents/scratch"
```

Then add the matching pair to the `podman run` command, in the same convention
as the other mounts — the variable names the host path on the left of the
`--volume`, and the container path in the `--env`:

```bash
--env    "CALYCOPIS_SCRATCH=/Calycopis/agents/scratch" \
--volume "${CALYCOPIS_SCRATCH:?}:/Calycopis/agents/scratch:rw,Z" \
```

The container in this deployment, `calycopis-dev`, mounts `${CALYCOPIS_ROOT}` at
`/Calycopis` wholesale, so it already sees the directory without an extra mount.
The area is not version controlled and not backed up. A session whose workspace
root contains the directory reads and writes it directly; any other session
reads it freely, and writes it with a single approved sandbox exception, which
is how a hand-over is completed from another workspace. Never put credentials
there. The retention rule is deliberately undecided: watch how often the area is
used and for what before adding one.

## Launching the containers

`calycopis-dev` is launched with **anonymous volumes** for the shared
configuration and data directories. The lifetime of an anonymous volume is
linked to the lifetime of the container that created it:

```bash
podman run \
    --rm \
    --tty \
    --interactive \
    --pod  "${CALYCOPIS_POD_NAME:?}" \
    --name "${CALYCOPIS_DEV_NAME:?}" \
    --volume /etc/calycopis \
    --volume /var/calycopis/log \
    --volume /var/calycopis/data \
    --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --volume "${XDG_RUNTIME_DIR:?}/podman/podman.sock:/run/podman/podman.sock:rw,z" \
    --volume "${CALYCOPIS_ROOT:?}:/Calycopis:rw,z" \
    --volume "${HOME:?}/.m2:/root/.m2:rw,z" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.env" \
    localhost/calycopis/developer-tools:2026.09.14 \
    bash
```

The three anonymous volumes hold the configuration, the log output, and the
test data. The other containers are launched in the same pod with
`--volumes-from calycopis-dev`, so they see the same three directories.
Because the volumes are anonymous they are removed with `calycopis-dev`:
recreating it means re-running the configuration setup (see
[Database service](#database-service)).

### Running the Python test container

```bash
podman run \
    --rm \
    --tty \
    --interactive \
    --pod  "${CALYCOPIS_POD_NAME:?}" \
    --name "calycopis-pytest" \
    --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.env" \
    --volumes-from "${CALYCOPIS_DEV_NAME:?}" \
    localhost/calycopis/developer-tools:2026.09.14 \
    bash
```

This is the four-container deployment. For the current single-container
development environment, where the harness and the broker run in one container
with the host Podman socket mounted, see
[Running the test infrastructure from an agent container](#running-the-test-infrastructure-from-an-agent-container).

### Running the DSH web container

The harness runs in its own container, defined by the
[`lithosia-quadra`](https://github.com/Zarquan/lithosia-quadra) project rather
than by this one, so it is not tied to the Execution Broker and can be reused
across projects. The harness home is mounted separately from any project
source, which keeps the two independent:

 * `LITHOSIA_CODE` locates the deployment project's source.
 * `DSH_HOME` locates the harness home — its configuration, profiles, plugins
   and session state.

The image, the three launch variants (source only, harness only, or both), the
web proxy plugin install and the client-connection patch are documented in the
[Lithosia README](https://github.com/Zarquan/lithosia-quadra/blob/main/README.md#running-the-container).
The web UI listens on port 3080 inside the container, and the proxy publishes
3081.

## Host filesystem side effects

When the broker (running inside a development container) calls the Podman
API to create a container with a bind mount, the Podman service on the host
resolves the bind mount path against the **host filesystem**. This has
important consequences:

 * **Files created inside a development container are not visible to
   application containers.** For example, if you create
   `/var/calycopis/data/random.txt` inside the development container, that
   file exists only in the container's filesystem. When the broker launches
   an application container with `-v /var/calycopis/data/random.txt:/input:ro`,
   the Podman service mounts the file at that path on the **host** filesystem
   — which may be a completely different file, or may not exist at all.

 * **The host file and the container file can have the same path but
   different content.** If `/var/calycopis/data/random.txt` exists on both
   the host and inside the development container, the application container
   will always see the host copy.

 * **Anonymous volumes have hash-named host paths.** The anonymous volumes
   owned by the development container are stored on the host under the Podman
   storage directory in hash-named sub-directories (e.g.
   `/home/<user>/.local/share/containers/storage/volumes/<hash>/_data/`).
   To bind-mount a file from a shared volume into an application container
   you must use this host path, discovered with:

   ```bash
   podman inspect calycopis-dev \
   | jq -r '
       .[0].Mounts.[]
       | select(.Destination == "/var/calycopis/data")
       | .Source
       '
   ```

 * **Containers launched via the Podman API see the same host filesystem.**
   Both the application container and any helper container (for example an
   Alpine container used to compute a reference checksum) are launched
   through the same host Podman service, so `podman run -v /path:/input
   alpine md5sum /input` produces the same result as the broker's own
   container.

### Practical implications for testing

 * **Do not rely on local file I/O for reference values.** When a Python
   test needs an expected checksum, or needs to verify file content that an
   application container will see, it must compute the reference by running
   a container with the same bind mount (via `docker-py` or `podman run`),
   rather than reading the file from the development container's filesystem.

 * **Test data files must be created in a shared volume**, so that a
   container sharing the development volumes writes into the anonymous
   `/var/calycopis/data` volume. The **host path** of the file (the
   hash-named volume directory plus the file name) is what must be used for
   bind mounts into application containers.

 * **Verify test data with a helper container** before relying on it, for
   example by bind-mounting the discovered host path and comparing the
   checksums:

   ```bash
   podman run \
       --rm \
       --volume "${testfilehostpath:?}:/input" \
       alpine:3 sh -c '
           md5sum /input | awk "{print \$1}"
           sha256sum /input | awk "{print \$1}"
           '
   ```

## Database service

The broker requires a PostgreSQL database. H2 is not supported because the
application uses `GENERATE_SERIES` and other PostgreSQL-specific SQL features.

### Configuration chain

The main `application.yaml` does **not** contain database credentials
directly. Instead it imports external files:

```yaml
spring:
    config:
        import:
          - file:/etc/calycopis/admin.yaml
          - file:/etc/calycopis/database.yaml
          - optional:file:/etc/calycopis/spring.yaml
          - optional:file:/etc/calycopis/timings.yaml
          - optional:file:/etc/calycopis/metrics.yaml
          - optional:file:/etc/calycopis/testing.yaml
```

The external file `/etc/calycopis/database.yaml` supplies the Spring
datasource properties, and the `spring.yaml` file selects the platform
profile. This separation keeps credentials out of the version-controlled
source tree; templates for the files live in the `config/` directory.

`/etc/calycopis/testing.yaml` carries test-only settings under
`calycopis.broker.testing`:

```yaml
calycopis:
  broker:
    testing:
      enabled: false
      delays:
        booking-query-to-save: 0
```

Everything under it is inert while `enabled` is false, which is the default,
so a deployment with no testing file behaves exactly as before. When it is
enabled the broker logs a warning at startup naming the armed values, and
reports its state on `/actuator/info`. Delays are in seconds;
`delays.booking-query-to-save` holds the window between the booking query and
the offer save open, so the in-flight atomicity race
([issue #156](https://github.com/uksrc/Calycopis-broker/issues/156)) can be
reproduced. Fault injection switches are added under `faults` as consumers
need them.

The same file also carries the test-data configuration that the Python tests
read from `calycopis.broker.testing.testdata` (see
[Practical implications for testing](#practical-implications-for-testing)).
The broker does not bind that key and ignores it, so one file serves both.

### Creating the configuration files

The configuration files are created inside the development container (which
owns the `/etc/calycopis` anonymous volume), using `pwgen` (available in the
`developer-tools` container) to generate random credentials:

```bash
cat > "${CALYCOPIS_CONFIG_DIR:?}/admin.yaml" << EOF
calycopis:
    admin:
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
EOF

cat > "${CALYCOPIS_CONFIG_DIR:?}/database.yaml" << EOF
spring:
    datasource:
        url: jdbc:postgresql://${CALYCOPIS_DB_HOST:?}:5432/${CALYCOPIS_DB_NAME:?}
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
        driverClassName: org.postgresql.Driver
        initialize: true
EOF

cat > "${CALYCOPIS_CONFIG_DIR:?}/spring.yaml" << EOF
spring:
    profiles:
        active: docker
EOF

cat > "${CALYCOPIS_CONFIG_DIR:?}/timings.yaml" << EOF
calycopis:
  broker:
    timing:
      session:
        EXPIRED:
          timeout: 300
          polling: 5
EOF
```

The PostgreSQL container cannot read the credentials from the YAML files, so
extract them into plain files with `yq`:

```bash
yq '.spring.datasource.username' \
   "${CALYCOPIS_CONFIG_DIR:?}/database.yaml" \
   > "${CALYCOPIS_CONFIG_DIR:?}/pgusername"

yq '.spring.datasource.password' \
   "${CALYCOPIS_CONFIG_DIR:?}/database.yaml" \
   > "${CALYCOPIS_CONFIG_DIR:?}/pgpassword"
```

TODO: this configuration generation should be moved into an initialisation
script.

### Starting the PostgreSQL container

Start a PostgreSQL instance in the same pod, sharing the development
volumes so it can read the configuration:

```bash
podman run \
    --rm \
    --detach \
    --expose "${CALYCOPIS_DB_PORT:?}" \
    --pod "${CALYCOPIS_POD_NAME:?}" \
    --name "${CALYCOPIS_DB_HOST}" \
    --env "POSTGRES_DB=${CALYCOPIS_DB_NAME:?}" \
    --env "POSTGRES_USER_FILE=${CALYCOPIS_CONFIG_DIR:?}/pgusername" \
    --env "POSTGRES_PASSWORD_FILE=${CALYCOPIS_CONFIG_DIR:?}/pgpassword" \
    --volumes-from "${CALYCOPIS_DEV_NAME}" \
    "docker.io/library/postgres:latest"
```

### Verifying the database is ready

The `postgresql` client (installed in the `developer-tools` image) provides
`pg_isready`. Wait for the database to accept connections:

```bash
postgreswait()
    {
    for ((i = 1; i <= 4; i++))
    do
        if pg_isready \
            --host "${CALYCOPIS_DB_HOST:?}" \
            --port "${CALYCOPIS_DB_PORT:?}" \
            --dbname "${CALYCOPIS_DB_NAME:?}"
        then
            echo "[$(date)] database is ready"
            return 0
        fi
        echo "[$(date)] waiting for database to start (${i}/10)."
        sleep 10
    done
    echo "[$(date)] database is NOT ready"
        return 1
    }

postgreswait
```

### Re-creating the database

The broker uses `spring.jpa.hibernate.ddl-auto: create`, so the schema is
recreated on every broker restart. If you need a completely fresh database
(e.g. after schema changes that cause migration errors), stop and re-create
the PostgreSQL container with the command above. The credentials are read
from `/etc/calycopis/pgusername` and `/etc/calycopis/pgpassword`, so they
remain consistent without needing to be regenerated.

The configuration and data live in anonymous volumes owned by the
development container: removing that container removes the volumes too, so a
full clean start means re-running the whole setup (configuration files,
database, and test data).

## Running the test infrastructure from an agent container

The current development environment runs the harness and the broker in a
single container, with the host Podman service mounted as a socket. The
integration tests can be run from that container, but two things differ from
[`tests/python/bin/run-tests.sh`](../tests/python/bin/run-tests.sh), and both
of them come from the same cause: it is the Podman **service** that resolves
paths, not the container that runs the `podman` command.

### Bind mount sources must be service-side paths

A `--volume` source is opened by the Podman service, so it must be a path as
the service sees it. Mounting the service socket into a container makes that
container a client of the service; it does not make the two filesystems the
same. Inside the development container the broker clone is at
`/Calycopis/Calycopis-broker`, while the service sees the host path that was
mounted there, for example
`/home/<user>/Projects/IVOA/ivoa/Calycopis/Calycopis-broker/Calycopis-broker-uksrc-zrq`.

A `--volume "${CALYCOPIS_CODE}:/opt/calycopis"` therefore mounts an **empty
directory** on the service side. That is why `run-tests.sh` cannot be run
verbatim from inside the container: three of its mounts use `${CALYCOPIS_CODE}`
(the `bin` directory in the two configuration steps, and the whole clone for
the test container). Its socket mount is already correct, because it uses
`HOST_CONTAINER_PATH` from
[`bin/container-host.sh`](../bin/container-host.sh).

To find the service-side path for a container path, find this container's own
entry in the service's view and translate the path against its mounts:

```bash
for id in $(podman ps --format '{{.ID}}')
do
    if podman inspect "${id}" \
        --format '{{range .Mounts}}{{.Destination}}{{"\n"}}{{end}}' \
        | grep -qx "${CALYCOPIS_CODE}"
    then
        podman inspect "${id}" \
            --format '{{range .Mounts}}{{.Destination}} {{.Source}}{{"\n"}}{{end}}'
    fi
done
```

The directory itself is shared, so a file written on one side is visible on the
other; only the path name differs. `HOST_CONTAINER_PATH` in the example above
is `/run/user/1000/podman/podman.sock`, and that is also the service-side path
of the socket.

Do not try to avoid this with `--volumes-from` on the agent container. Podman
carries a source container's named volumes (`/root/.dsh`, `/var/cache/dnf` and
`/var/cache/yum` here), not its bind mounts, so it does not bring the source
clone, the scratch area or the socket at all; and handing the harness's own
state volume to a throwaway test container is not wanted. An attempt to use it
left the new container in the `initialized` state and the development
container needing a restart. The named configuration and test-data volumes
need no such treatment, because they are referenced by name.

### The broker image cannot be built from the container

`./mvnw spring-boot:build-image` fails inside the container. The plugin binds
the Docker socket into the builder container at the path the client uses
(`unix:///run/podman/podman.sock`), and the service cannot resolve that path:
its own socket is at `/run/user/1000/podman/podman.sock`. `/run` is not
writable in the container, so the two paths cannot be made to match. The build
works on the host, where the client and the service share one filesystem.

### Running the broker from source instead

Run the broker from source in a `developer-tools` container that joins the pod,
so it finds the database by container name and the tests find it by name too:

```bash
podman run \
    --detach \
    --user 0:0 \
    --security-opt label=disable \
    --pod "${CALYCOPIS_POD_NAME:?}" \
    --name "${CALYCOPIS_BROKER_HOSTNAME:?}" \
    --volume "${HOST_CODE:?}:/opt/calycopis:rw" \
    --volume "${HOST_M2:?}:/m2:rw" \
    --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH:?}" \
    --volume "${HOST_SOCKET:?}:/run/podman/podman.sock:rw" \
    --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --env "MAVEN_USER_HOME=/m2" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.vars" \
    "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
        bash -c '
            set -eo pipefail
            source /opt/calycopis/bin/versions.sh /opt/calycopis/config.yaml
            set -u
            cd /opt/calycopis/java
            exec ./mvnw -o -B \
                -Dmaven.repo.local=/m2/repository \
                spring-boot:run
            '
```

`HOST_CODE` is the service-side source path, `HOST_M2` is the service-side
path of the shared Maven repository (`${CALYCOPIS_SCRATCH}/m2`), and
`HOST_SOCKET` is `HOST_CONTAINER_PATH`. `--security-opt label=disable` keeps a
throwaway test container from relabelling the host source tree; the labels that
`:z` would set belong to the development container. `bin/versions.sh` reads
`${GITHUB_ENV}` when it is set, so `set -u` has to wait until it has been
sourced. The broker then answers inside the pod as
`http://${CALYCOPIS_BROKER_HOSTNAME}:8082`, with the health endpoint at
`/actuator/health`.

### The rest of the sequence

The other steps are the CI script's: create the network and pod, run
`bin/config-broker.sh` and `bin/config-database.sh` inside the configuration
volumes, start the PostgreSQL container and wait for `pg_isready`. Then run the
tests in another `developer-tools` container joined to the pod:

```bash
podman run \
    --rm \
    --user 0:0 \
    --security-opt label=disable \
    --pod "${CALYCOPIS_POD_NAME:?}" \
    --volume "${CALYCOPIS_TEST_DATA_VOLUME:?}:${CALYCOPIS_TEST_DATA_PATH:?}" \
    --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH:?}" \
    --volume "${HOST_CODE:?}:/opt/calycopis:rw" \
    --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --volume "${HOST_SOCKET:?}:/run/podman/podman.sock:rw" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.vars" \
    "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
        bash -c '
            set -eo pipefail
            pushd /opt/calycopis
                source calycopis.vars
                source bin/versions.sh config.yaml
                set -u
                pushd tests/python
                    bin/config-tests.sh
                    pip install -r requirements.txt
                    pytest -v docker
                popd
            popd
            '
```

`bin/config-tests.sh` needs the Podman socket too, because it reads the host
path of the test-data volume with `podman volume inspect`.

CI runs only `pytest -v docker`. The platform independent tests in
`tests/python/any/` have to be asked for by name (`pytest -v any`), and the
same goes for a single file. The broker recreates its schema on every restart
(`spring.jpa.hibernate.ddl-auto: create`), so restart the broker between runs
when a clean database matters, in particular before the lifecycle and stress
tests, which accumulate sessions.

Finally, remove the pod and network when the run is finished. The
configuration, log and test-data volumes are reusable, and the next run
re-creates the database from the credentials already in the database
configuration volume.

### Running the atomicity race test

`tests/python/states/test_atomicity_race.py` demonstrates the in-flight
atomicity race in offer generation
([issue #159](https://github.com/uksrc/Calycopis-broker/issues/159)): the booking
query runs before the offer set and its reservation window are committed, so two
concurrent immediate requests can both be offered the block that contains the
requested start.

The test needs a broker configured to make that window observable, and it cannot
be the configuration the rest of the docker suite uses, because the suite needs
far more capacity than the race test does (its stress test submits 100
concurrent sessions). Start a second broker for it, in its own pod, with
`${CALYCOPIS_BROKER_CONFIG_PATH}/spring.yaml`:

```yaml
spring:
    profiles:
        active: docker
calycopis:
    broker:
        booking:
            capacity:
                cores: 1
                memory: 1
```

and `${CALYCOPIS_BROKER_CONFIG_PATH}/testing.yaml`:

```yaml
calycopis:
    broker:
        testing:
            enabled: true
            delays:
                booking-query-to-save: 5
```

Both files have to be in place **before the broker starts**, because the broker
reads its configuration once at startup. `bin/config-broker.sh` writes the other
configuration files and runs before the broker container starts, so the race
configuration belongs there, or in a file written by hand into the broker
configuration volume.

Then run the test against that broker:

```bash
pytest -v tests/python/states/test_atomicity_race.py
```

The test reads `/actuator/info` for the capacity and the testing state, and skips
with a message naming what is missing when the broker is not configured for it.
It has to pass. The booking query and the reservation commit run in one
transaction, under a lock scoped to the resource type, so a concurrent request
waits for the reservations to become visible instead of booking the same space
([issue #156](https://github.com/uksrc/Calycopis-broker/issues/156)). While the
race existed the test was marked `xfail(strict=True)`, and the demonstration that
produced the fix is recorded in
[`notes/zrq/20261015-01-atomicity-race-test.md`](../notes/zrq/20261015-01-atomicity-race-test.md).

To check that the invariant itself is sound, run the same test with the two
requests submitted one after another, on a freshly restarted broker. The second
request then sees the first request's reservation and is displaced, so the
invariant holds:

```bash
RACE_SUBMIT_WORKERS=1 pytest -v tests/python/states/test_atomicity_race.py
```

CI does not run this test yet. It runs only `pytest -v docker`, and the
configuration above has to be written before the broker starts, so wiring the
test into CI needs the harness to start a second broker for it. That is a
separate change.
