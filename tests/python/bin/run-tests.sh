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
#     },
#     {
#     "interval": "2026-10-15T17:15:00/2026-10-16T07:00:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-11-20T12:23:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-v4.1-flash-1017",
#     "contribution": {
#       "value": 20,
#       "units": "%"
#       }
#     }
#   ]
#
#


#   source "${HOME}/calycopis.env"
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
        --ignore \
        "${CALYCOPIS_NET_NAME:?}"

    podman pod create \
        --replace \
        --name "${CALYCOPIS_POD_NAME:?}" \
        --network "${CALYCOPIS_NET_NAME:?}" \
        --publish 8082:8082


# -----------------------------------------------------
# Fetching container images.
#[user@desktop]

    echo "--------"
    echo "Pulling container images"

    podman pull \
        "ghcr.io/ivoa/calycopis/developer-tools:2026.09.17"

    podman pull \
        "ghcr.io/zarquan/heliophorus-androcles:sha-9a2513b"

    podman pull \
        "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"

    podman pull \
        "docker.io/library/postgres:18.6"

    podman pull \
        "alpine:3.24.2"

    podman images


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
        ghcr.io/ivoa/calycopis/developer-tools:2026.09.17 \
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
        ghcr.io/ivoa/calycopis/developer-tools:2026.09.17 \
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
        "docker.io/library/postgres:18.6"


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
        ghcr.io/ivoa/calycopis/developer-tools:2026.09.17 \
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
# Broker lifecycle helpers.
#
# A broker has one active profile, and only one broker fits on port 8082.
# Run the docker profile first, then stop it and run the mock profile, so
# each suite gets a broker with the platform it needs. The database and
# configuration volumes are reused, and each broker start recreates the
# schema. The 'any' suite runs against both profiles.
#[user@desktop]

    start_broker() {

        local profile="${1:?}"

        echo "--------"
        echo "Starting broker with profile [${profile}]"

        if podman container exists "${CALYCOPIS_BROKER_HOSTNAME:?}"
        then
            echo "Removing the broker container left by an earlier run"
            podman rm -f "${CALYCOPIS_BROKER_HOSTNAME:?}"
        fi

        podman run \
            --rm \
            --detach \
            --user 0:0 \
            --expose 8082 \
            --pod "${CALYCOPIS_POD_NAME:?}" \
            --name "${CALYCOPIS_BROKER_HOSTNAME:?}" \
            --volume "${CALYCOPIS_BROKER_LOGS}" \
            --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH:?}" \
            --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
            --volume "${HOST_CONTAINER_PATH:?}:/run/podman/podman.sock:rw,z" \
            --env    "SPRING_PROFILES_ACTIVE=${profile}" \
            --env-file "${CALYCOPIS_CODE:?}/calycopis.vars" \
                "localhost/calycopis/calycopis-broker:${CALYCOPIS_BROKER_VERSION:?}"
    }

    wait_for_broker() {

        echo "--------"
        echo "Waiting for broker health check"

        if podman run \
            --rm \
            --pod "${CALYCOPIS_POD_NAME:?}" \
            --env-file "${CALYCOPIS_CODE:?}/calycopis.vars" \
            ghcr.io/ivoa/calycopis/developer-tools:2026.09.17 \
                bash -c '
                    ENDPOINT_URL="http://${CALYCOPIS_BROKER_HOSTNAME}:8082/actuator/health"
                    curl --silent \
                         --show-error \
                         --fail-with-body \
                         --retry 30 \
                         --retry-delay 5 \
                         --retry-connrefused \
                         "${ENDPOINT_URL:?}"
                    '
        then
            echo "PASS - broker is available"
        else
            echo "FAIL - broker is NOT available" >&2
            return 1
        fi
    }

    stop_broker() {

        echo "--------"
        echo "Stopping broker"

        if podman container exists "${CALYCOPIS_BROKER_HOSTNAME:?}"
        then
            podman rm -f "${CALYCOPIS_BROKER_HOSTNAME:?}"
        else
            echo "Broker container is already gone"
        fi
    }

    run_suite() {

        local profile="${1:?}"
        local suites="${2:?}"
        local generate_test_data="${3:?}"

        echo "--------"
        echo "Running test container for profile [${profile}] suites [${suites}]"

        podman run \
            --rm \
            --tty \
            --interactive \
            --user 0:0 \
            --pod "${CALYCOPIS_POD_NAME:?}" \
            --volume "${CALYCOPIS_TEST_DATA_VOLUME:?}:${CALYCOPIS_TEST_DATA_PATH}" \
            --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
            --env    "CALYCOPIS_CODE=/opt/calycopis" \
            --volume "${CALYCOPIS_CODE:?}:/opt/calycopis:rw,z" \
            --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
            --volume "${HOST_CONTAINER_PATH:?}:/run/podman/podman.sock:rw,z" \
            --env-file "${CALYCOPIS_CODE:?}/calycopis.vars" \
            ghcr.io/ivoa/calycopis/developer-tools:2026.09.17 \
                bash -c '
                    set -eo pipefail

                    pushd "${CALYCOPIS_CODE}"

                        source calycopis.vars
                        source bin/versions.sh config.yaml

                        pushd tests/python

                            if [[ "${1:-no}" == "yes" ]]
                            then
                                bin/config-tests.sh
                            fi

                            pip install -r requirements.txt

                            pytest -v ${2:?}
                    '
                bash "${generate_test_data}" "${suites}"
    }

    run_lifecycle() {

        local profile="${1:?}"
        local suites="${2:?}"
        local generate_test_data="${3:?}"

        start_broker "${profile}"

        if ! wait_for_broker
        then
            stop_broker
            return 1
        fi

        if ! run_suite "${profile}" "${suites}" "${generate_test_data}"
        then
            stop_broker
            return 1
        fi

        stop_broker
    }


# -----------------------------------------------------
# Run the docker and mock suites.
#
# The mock suite is run against a mock-profile broker so that changes to
# shared entities, factories and validator base classes cannot pass CI while
# breaking the mock platform. The states suite is not run here: it needs
# direct database access and a broker configured with a specific pool and
# allocation, and the expiry tests take several minutes. It remains manual.
#[user@desktop]

    run_lifecycle docker "docker any" yes

    run_lifecycle mock "mock any" no

