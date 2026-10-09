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
the same `developer-tools` image. The `calycopis-dsh` container instead uses
the `deepseek-harness` image, which is built on top of `developer-tools`.

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

## Environment file

The container, pod, and network names, the shared directories, and the
database details are defined in a `calycopis.env` file at the repository
root. It is created during setup and passed to every container with
`--env-file`:

```
CALYCOPIS_DEV_NAME=calycopis-dev
CALYCOPIS_POD_NAME=calycopis-pod
CALYCOPIS_NET_NAME=calycopis-network
CALYCOPIS_LOG_DIR=/var/calycopis/log
CALYCOPIS_DATA_DIR=/var/calycopis/data
CALYCOPIS_CONFIG_DIR=/etc/calycopis
CALYCOPIS_DB_NAME=calycopis-db-name
CALYCOPIS_DB_HOST=calycopis-db-host
CALYCOPIS_DB_PORT=5432
```

The outer environment (sourced from `${HOME}/calycopis.env` and
`${HOME}/dsh.env` on the host) supplies the supporting variables used in the
commands below: `CALYCOPIS_CODE`, `CALYCOPIS_ROOT`, `XDG_RUNTIME_DIR`, and
`DSH_HOME`.

The values here describe one particular deployment, and the variable names
have since been revised in `calycopis.vars`. For paths inside the container,
[Container paths and environment variables](../AGENTS.md#container-paths-and-environment-variables)
in AGENTS.md is the authoritative description.

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

### Running the DSH web container

The DSH harness (agents and the web UI) runs in the `calycopis-dsh`
container:

```bash
podman run \
    --rm \
    --tty \
    --interactive \
    --pod  "${CALYCOPIS_POD_NAME:?}" \
    --name "calycopis-dsh" \
    --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.env" \
    --volumes-from "${CALYCOPIS_DEV_NAME:?}" \
    --env "DSH_HOME=/opt/dsh" \
    --volume "${DSH_HOME:?}:/opt/dsh:rw,z" \
    localhost/calycopis/deepseek-harness:2026.09.14 \
    bash
```

Inside `calycopis-dsh`, the web proxy plugin is (re)installed at runtime —
the plugins live in the user's DSH configuration, not in the image:

```bash
dsh plugin --profile web add github:smanx/dsh-proxy#master
```

A runtime patch is also needed so the web profile injects the web server
credentials into the client connection (workaround; see the notes for the
reference):

```bash
sed -i '
    /^const inject =/ {
        s/^const inject = \["credentials"\];/const inject = \["webServer", "credentials"\];/
        }
    ' /usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-client-connection/lib/index.js
```

Then start the web profile:

```bash
dsh web --no-open
```

The web UI runs on port 3080 inside the container and is reachable through
the proxy at `http://127.0.0.1:3081/?token=....` on the host (the token is
printed by `dsh web`).

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
```

The external file `/etc/calycopis/database.yaml` supplies the Spring
datasource properties, and the `spring.yaml` file selects the platform
profile. This separation keeps credentials out of the version-controlled
source tree; templates for the files live in the `config/` directory.

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
