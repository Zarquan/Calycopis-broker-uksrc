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
# AIMetrics: [
#     {
#     "timestamp": "2026-09-14T11:11:41",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 10,
#       "units": "%"
#       }
#     }
#   ]
#
#


    source "${HOME}/calycopis.env"
    source "${CALYCOPIS_CODE}/calycopis.vars"
    source "${CALYCOPIS_CODE}/bin/versions.sh" "${CALYCOPIS_CODE}/config.yaml"
    source "${CALYCOPIS_CODE}/bin/container-host.sh"

    set -euo pipefail

# -----------------------------------------------------
# Create our Pod.
#[user@desktop]

    echo "--------"
    echo "Creating network and Pod"

    podman network create \
        "${CALYCOPIS_NET_NAME:?}"

    podman pod create \
        --name "${CALYCOPIS_POD_NAME:?}" \
        --network "${CALYCOPIS_NET_NAME:?}" \
        --publish 8082:8082 \
        --publish 3081:3081


# -----------------------------------------------------
# Configure the broker-config volume.
#[user@desktop]

    echo "--------"
    echo "Configuring broker"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_CODE}/bin:${CALYCOPIS_BROKER_OPT:?}/bin" \
        --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
        localhost/calycopis/developer-tools:2026.09.14 \
            "${CALYCOPIS_BROKER_OPT:?}/bin/config-broker.sh"


# -----------------------------------------------------
# Configure the broker-database volume.
#[user@desktop]

    echo "--------"
    echo "Configuring database"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_DATABASE_CONFIG_VOLUME:?}:${CALYCOPIS_DATABASE_CONFIG_PATH}" \
        --volume "${CALYCOPIS_CODE}/bin:${CALYCOPIS_BROKER_OPT:?}/bin" \
        --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
        localhost/calycopis/developer-tools:2026.09.14 \
            "${CALYCOPIS_BROKER_OPT:?}/bin/config-database.sh"


# -----------------------------------------------------
# Start our PostgreSQL database.
#[user@desktop]

    echo "--------"
    echo "Running database service"

    podman run \
        --rm \
        --detach \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --name "${CALYCOPIS_DATABASE_HOSTNAME}" \
        --expose "${CALYCOPIS_DATABASE_PORT:?}" \
        --env "POSTGRES_DB=${CALYCOPIS_DATABASE_NAME:?}" \
        --env "POSTGRES_USER_FILE=${CALYCOPIS_DATABASE_CONFIG_PATH:?}/pgusername" \
        --env "POSTGRES_PASSWORD_FILE=${CALYCOPIS_DATABASE_CONFIG_PATH:?}/pgpassword" \
        --volume "${CALYCOPIS_DATABASE_CONFIG_VOLUME:?}:${CALYCOPIS_DATABASE_CONFIG_PATH}" \
        "docker.io/library/postgres:latest"


# -----------------------------------------------------
# Wait for our database to become available.
#[user@desktop]

    echo "--------"
    echo "Waiting for database health check"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --volume "${CALYCOPIS_CODE}/bin:${CALYCOPIS_BROKER_OPT:?}/bin" \
        --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
        localhost/calycopis/developer-tools:2026.09.14 \
            bash -c '
                for ((i = 1; i <= 4; i++))
                do
                    if pg_isready \
                        --host   "${CALYCOPIS_DATABASE_HOSTNAME:?}" \
                        --port   "${CALYCOPIS_DATABASE_PORT:?}" \
                        --dbname "${CALYCOPIS_DATABASE_NAME:?}"
                    then
                        echo "[$(date)] PASS database is ready"
                        exit 0
                    fi
                    echo "[$(date)] waiting for database to start (${i}/10)."
                    sleep 10
                done
                echo "[$(date)] FAIL database is NOT ready"
                    exit 1
            '


# -----------------------------------------------------
# Start our broker service.
#[user@desktop]

    echo "--------"
    echo "Running broker service"

    podman run \
        --rm \
        --detach \
        --user 0:0 \
        --expose 8082 \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --name "${CALYCOPIS_BROKER_HOSTNAME:?}" \
        --volume "${CALYCOPIS_BROKER_LOGS}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${HOST_CONTAINER_PATH:?}:/run/podman/podman.sock:rw,z" \
        --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
            "localhost/calycopis/calycopis-broker:${CALYCOPIS_BROKER_VERSION}"


# -----------------------------------------------------
# Wait for our service to become available.
#[user@desktop]

    echo "--------"
    echo "Waiting for broker health check"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --volume "${CALYCOPIS_CODE}/bin:${CALYCOPIS_BROKER_OPT:?}/bin" \
        --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
        localhost/calycopis/developer-tools:2026.09.14 \
            bash -c '
                ENDPOINT_URL="http://${CALYCOPIS_BROKER_HOSTNAME}:8082/actuator/health"
                curl --silent \
                     --show-error \
                     --fail-with-body \
                     --retry 10 \
                     --retry-delay 10 \
                     --retry-connrefused \
                     "${ENDPOINT_URL:?}"
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
# Run our test container.
#[user@desktop]

    echo "--------"
    echo "Running test container"

#   podman run \
#       --rm \
#       --user 0:0 \
#       --pod "${CALYCOPIS_POD_NAME:?}" \
#       --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
#       --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
#       "localhost/calycopis/python-tester:${CALYCOPIS_BROKER_VERSION}"

    podman run \
        --rm \
        --user 0:0 \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --volume "${CALYCOPIS_TEST_DATA_VOLUME:?}:${CALYCOPIS_TEST_DATA_PATH}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_ROOT:?}:/Calycopis:rw,z" \
        --volume "${CALYCOPIS_CODE:?}/calycopis.env:/root/calycopis.env:rw,z" \
        --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${HOST_CONTAINER_PATH:?}:/run/podman/podman.sock:rw,z" \
        --env-file "${CALYCOPIS_CODE}/calycopis.vars" \
        localhost/calycopis/developer-tools:2026.09.14 \
            bash -c '
                source "${HOME}/calycopis.env"
                pushd "${CALYCOPIS_CODE}"

                    source calycopis.vars
                    source bin/versions.sh config.yaml

                    pushd tests/python

                        bin/config-tests.sh

                        pip install -r requirements.txt

                        pytest -v -s docker
                '

