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

    if [[ -z "${OVERRIDE_VARS:-}" ]]
    then
        echo "OVERRIDE_VARS is blank, creating a new one"
        OVERRIDE_VARS=$(mktemp)
    fi

    echo "OVERRIDE_VARS [${OVERRIDE_VARS}]"

    if [[ -z "${CALYCOPIS_VARS:-}" ]]
    then
        echo "CALYCOPIS_VARS is blank, setting to default"
        CALYCOPIS_VARS=${CALYCOPIS_CODE}/calycopis.vars
    fi

    echo "CALYCOPIS_CODE [${CALYCOPIS_CODE}]"
    echo "CALYCOPIS_VARS [${CALYCOPIS_VARS}]"

    source "${CALYCOPIS_VARS}"
    source "${OVERRIDE_VARS}"

#    source "${CALYCOPIS_CODE}/bin/versions.sh" "${CALYCOPIS_CODE}/config.yaml"
#    source "${CALYCOPIS_CODE}/bin/container-host.sh"

# -----------------------------------------------------
# Create our Pod.
#[user@desktop]

    echo "--------"
    echo "Creating network and Pod"

    echo "CALYCOPIS_NET_NAME [${CALYCOPIS_NET_NAME}]"
    echo "CALYCOPIS_POD_NAME [${CALYCOPIS_POD_NAME}]"

    podman network create \
        "${CALYCOPIS_NET_NAME:?}"

    podman pod create \
        --name "${CALYCOPIS_POD_NAME:?}" \
        --network "${CALYCOPIS_NET_NAME:?}" \
        --publish ${CALYCOPIS_BROKER_EXTERNAL_PORT}:${CALYCOPIS_BROKER_INTERNAL_PORT}


# -----------------------------------------------------
# Fetching container images.
#[user@desktop]

#   echo "--------"
#   echo "Pulling container images"
#
#   podman pull \
#       "ghcr.io/ivoa/calycopis/developer-tools:2026.09.17"
#
#   podman pull \
#       "ghcr.io/zarquan/heliophorus-androcles:sha-9a2513b"
#
#   podman pull \
#       "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
#
#   podman pull \
#       "docker.io/library/postgres:18.6"
#
#   podman pull \
#       "alpine:3.24.2"
#
#   podman images
#

# -----------------------------------------------------
# Configure the broker-config volume.
#[user@desktop]

    echo "--------"
    echo "Configuring broker"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --env-file "${CALYCOPIS_VARS}" \
        --env-file "${OVERRIDE_VARS}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_CODE}:${CALYCOPIS_BROKER_CODE:?}" \
        "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
            "${CALYCOPIS_BROKER_CODE:?}/bin/config-broker.sh"


# -----------------------------------------------------
# Configure the broker-database volume.
#[user@desktop]

    echo "--------"
    echo "Configuring database"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --env-file "${CALYCOPIS_VARS}" \
        --env-file "${OVERRIDE_VARS}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_DATABASE_CONFIG_VOLUME:?}:${CALYCOPIS_DATABASE_CONFIG_PATH}" \
        --volume "${CALYCOPIS_CODE}:${CALYCOPIS_BROKER_CODE:?}" \
        "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
            "${CALYCOPIS_BROKER_CODE:?}/bin/config-database.sh"


# -----------------------------------------------------
# Start our PostgreSQL database.
#[user@desktop]

    echo "--------"
    echo "Running database service"
    echo "Database container [${CALYCOPIS_DATABASE_CONTAINER_NAME}]"

    podman run \
        --rm \
        --detach \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --name "${CALYCOPIS_DATABASE_CONTAINER_NAME}" \
        --expose "${CALYCOPIS_DATABASE_PORT:?}" \
        --env "POSTGRES_DB=${CALYCOPIS_DATABASE_NAME:?}" \
        --env "POSTGRES_USER_FILE=${CALYCOPIS_DATABASE_CONFIG_PATH:?}/pgusername" \
        --env "POSTGRES_PASSWORD_FILE=${CALYCOPIS_DATABASE_CONFIG_PATH:?}/pgpassword" \
        --volume "${CALYCOPIS_DATABASE_CONFIG_VOLUME:?}:${CALYCOPIS_DATABASE_CONFIG_PATH}" \
        "${CALYCOPIS_DATABASE_CONTAINER_IMAGE}"


# -----------------------------------------------------
# Wait for our database to become available.
#[user@desktop]

    echo "--------"
    echo "Waiting for database health check"
    echo "Database hostname [${CALYCOPIS_DATABASE_HOSTNAME}]"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --env-file "${CALYCOPIS_VARS}" \
        --env-file "${OVERRIDE_VARS}" \
        --volume "${CALYCOPIS_CODE}:${CALYCOPIS_BROKER_CODE:?}" \
       "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
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
    echo "Broker container [${CALYCOPIS_BROKER_CONTAINER_NAME}]"

    podman run \
        --rm \
        --detach \
        --user 0:0 \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --expose "${CALYCOPIS_BROKER_INTERNAL_PORT:?}" \
        --name "${CALYCOPIS_BROKER_CONTAINER_NAME:?}" \
        --env-file "${CALYCOPIS_VARS}" \
        --env-file "${OVERRIDE_VARS}" \
        --volume "${CALYCOPIS_BROKER_LOGS}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_CODE:?}/demo/config/broker-${CALYCOPIS_NODE_NAME:?}/metrics.yaml:${CALYCOPIS_BROKER_CONFIG_PATH:?}/metrics.yaml" \
        --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${HOST_CONTAINER_PATH:?}:/run/podman/podman.sock:rw,z" \
        "${CALYCOPIS_BROKER_CONTAINER_IMAGE}"


# -----------------------------------------------------
# Wait for our service to become available.
#[user@desktop]

    echo "--------"
    echo "Waiting for broker health check"
    echo "Broker hostname [${CALYCOPIS_BROKER_HOSTNAME}]"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --env-file "${CALYCOPIS_VARS}" \
        --env-file "${OVERRIDE_VARS}" \
        --volume "${CALYCOPIS_CODE}:${CALYCOPIS_BROKER_CODE:?}" \
       "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
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








