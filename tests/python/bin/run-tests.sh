#!/bin/bash
#
# <meta:header>
#   <meta:licence>
#     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
#
#     This information is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This information is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
#

    set -euo pipefail

    echo "--------"
    echo "CALYCOPIS_BROKER_VERSION  [${CALYCOPIS_BROKER_VERSION}]"
    echo "CALYCOPIS_OPENAPI_SCHEMA_VERSION [${CALYCOPIS_OPENAPI_SCHEMA_VERSION}]"
    echo "CALYCOPIS_OPENAPI_SPRING_VERSION [${CALYCOPIS_OPENAPI_SPRING_VERSION}]"
    echo "CALYCOPIS_OPENAPI_PYTHON_VERSION [${CALYCOPIS_OPENAPI_PYTHON_VERSION}]"

    echo "CONTAINER_PATH [${CONTAINER_PATH}]"
    echo "CONTAINER_HOST [${CONTAINER_HOST}]"


# -----------------------------------------------------
# Create our Pod.
#[user@desktop]

    echo "--------"
    echo "Creating test pod"

    TEST_POD_NAME=calycopis-test-pod
    echo "TEST_POD_NAME [${TEST_POD_NAME}]"

    podman pod create \
        --replace \
        --name "${TEST_POD_NAME}"

    podman pod ls

# -----------------------------------------------------
# Create our temp directory.
#[user@desktop]

    echo "--------"
    echo "Creating temp directory"

    TEMP_DIR=${RUNNER_TEMP:-$(mktemp -d)}

    echo "TEMP_DIR [${TEMP_DIR}]"


# -----------------------------------------------------
# Create our broker configuration files.
#[user@desktop]

    echo "--------"
    echo "Creating broker config"

    CONFIG_DIR=${TEMP_DIR}/config
    mkdir ${CONFIG_DIR}

    echo "CONFIG_DIR [${CONFIG_DIR}]"

    cat > "${CONFIG_DIR}/admin.yaml" << EOF
calycopis:
    admin:
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
EOF

    cat > "${CONFIG_DIR}/database.yaml" << EOF
spring:
    datasource:
        url: jdbc:postgresql://postgres:5432/calycopis
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
        driverClassName: org.postgresql.Driver
        initialize: true
EOF

    cat > "${CONFIG_DIR}/spring.yaml" << EOF
spring:
    profiles:
        active: docker
EOF


# -----------------------------------------------------
# Create our database configuration files.
#[user@desktop]

    echo "--------"
    echo "Creating database config"

    yq '.spring.datasource.username' \
       "${CONFIG_DIR}/database.yaml" \
       > "${CONFIG_DIR}/pgusername"

    yq '.spring.datasource.password' \
       "${CONFIG_DIR}/database.yaml" \
       > "${CONFIG_DIR}/pgpassword"


# -----------------------------------------------------
# Create our log directory.
#[user@desktop]

    echo "--------"
    echo "Creating log directory"

    LOG_DIR=${TEMP_DIR}/logs
    mkdir ${LOG_DIR}

    echo "LOG_DIR [${LOG_DIR}]"


# -----------------------------------------------------
# Create our test data file.
#[user@desktop]

    echo "--------"
    echo "Creating test data"

    TEST_DATA_DIR=${TEMP_DIR}/data
    mkdir ${TEST_DATA_DIR}

    TEST_DATA_FILE=${TEST_DATA_DIR}/random.dat

    echo "TEST_DATA_DIR  [${TEST_DATA_DIR}]"
    echo "TEST_DATA_FILE [${TEST_DATA_FILE}]"

    dd if=/dev/urandom of=${TEST_DATA_FILE} bs=1MB count=10

    # https://stackoverflow.com/a/25045505
    # chcon -Rt svirt_sandbox_file_t "${TEST_DATA_FILE}"
    # https://chatgpt.com/share/6a7f1820-6488-83eb-a3ca-75b148b07151
    if command -v selinuxenabled >/dev/null 2>&1 && selinuxenabled
      then
        chcon -t svirt_sandbox_file_t "${TEST_DATA_FILE}"
      fi

    chmod a+r "${TEST_DATA_FILE}"
    stat "${TEST_DATA_FILE}"

# -----------------------------------------------------
# Start our PostgreSQL database.
#[user@desktop]

    echo "--------"
    echo "Running database service"

    podman run \
        --rm \
        --detach \
        --pod "${TEST_POD_NAME}" \
        --name postgres \
        --expose 5432 \
        --env "PGPORT=5432" \
        --env "PGHOST=postgres" \
        --env "POSTGRES_DB=calycopis" \
        --env "POSTGRES_USER_FILE=/etc/calycopis/pgusername" \
        --env "POSTGRES_PASSWORD_FILE=/etc/calycopis/pgpassword" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        "docker.io/library/postgres:latest"


# -----------------------------------------------------
# Wait for our database to become available.
#[user@desktop]

    echo "--------"
    echo "Waiting for database health check"

    podman run \
        --rm \
        --pod "${TEST_POD_NAME}" \
        --name postgres-check \
        --env "POSTGRES_PORT=5432" \
        --env "POSTGRES_HOST=postgres" \
        --env "POSTGRES_DB=calycopis" \
        --env "POSTGRES_USER_FILE=/etc/calycopis/pgusername" \
        --env "POSTGRES_PASSWORD_FILE=/etc/calycopis/pgpassword" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        "docker.io/library/postgres:latest" \
          bash -c '
            for ((i = 1; i <= 10; i++))
            do
                if pg_isready \
                    --host "${POSTGRES_HOST}" \
                    --port "${POSTGRES_PORT}" \
                    --dbname "${POSTGRES_DB}"
                then
                    echo "[$(date)] database is ready"
                    exit 0
                fi
                echo "[$(date)] waiting for database to start (${i}/10)."
                sleep 10
            done
            echo "[$(date)] database is NOT ready"
            exit 1
            '


# -----------------------------------------------------
# Start our broker service.
#[user@desktop]

    echo "--------"
    echo "Running broker service"

    # Needed if our webapp isn't run as root.
    # chmod a+rwx "${LOG_DIR}"
    # chmod a+rx  "${CONFIG_DIR}"

    podman run \
        --rm \
        --detach \
        --user 0:0 \
        --expose 8082 \
        --pod "${TEST_POD_NAME}" \
        --name calycopis-broker \
        --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${LOG_DIR}:/var/log/calycopis:rw,Z" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        --volume "${CONTAINER_PATH}:/run/podman/podman.sock:rw,Z" \
        "localhost/calycopis/calycopis-broker:${CALYCOPIS_BROKER_VERSION}"

# -----------------------------------------------------
# Wait for our service to become available.
#[user@desktop]

    echo "--------"
    echo "Waiting for broker health check"

    podman run \
        --rm \
        --pod "${TEST_POD_NAME}" \
        fedora \
          bash -c '
            # Wait for a http service to be available
            # https://wissel.net/blog/2023/01/wait-for-service-availability.html
            ENDPOINT_URL="http://calycopis-broker:8082/actuator/health"
            curl --silent \
                 --show-error \
                 --fail-with-body \
                 --retry 10 \
                 --retry-delay 10 \
                 --retry-connrefused \
                 "${ENDPOINT_URL}"
            if [[ "$?" == 0 ]]
            then
              echo ""
              echo "PASS - [${ENDPOINT_URL}] available"
              exit 0
            else
              echo ""
              echo "FAIL - [${ENDPOINT_URL}] not available"
              exit 1
            fi
            '

# -----------------------------------------------------
# Loading utility containers.
#[user@desktop]

    #
    # Pull the utility containers into our local cache
    # in an effort to make the tests repeatable.
    #

    echo "--------"
    echo "Loading utility containers"

    podman pull \
        "alpine:3"

    podman pull \
        "ghcr.io/zarquan/heliophorus-androcles:sha-9a2513b"

    podman pull \
        "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"


# -----------------------------------------------------
# Run our test container.
#[user@desktop]

    echo "--------"
    echo "Running test container"

    podman run \
        --rm \
        --user 0:0 \
        --pod "${TEST_POD_NAME}" \
        --name calycopis-tester \
        --env "TEST_DATA_DIR=${TEST_DATA_DIR}" \
        --env "TEST_DATA_FILE=${TEST_DATA_FILE}" \
        --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        --volume "${CONTAINER_PATH}:/run/podman/podman.sock:rw,Z" \
        "localhost/calycopis/python-tester:${CALYCOPIS_BROKER_VERSION}"


