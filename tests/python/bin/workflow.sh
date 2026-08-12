#!/bin/sh
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

set -e

# -----------------------------------------------------
# Set the CONTAINER_HOST environment variable.
#[user@desktop]

    echo "--------"

    unset CONTAINER_HOST
    unset CONTAINER_PATH

    CONTAINER_PATH=$(
        podman info --format '{{.Host.RemoteSocket.Path}}'
        )

    if [[ ${CONTAINER_PATH} == unix://* ]]
    then
        CONTAINER_HOST=${CONTAINER_PATH}
        CONTAINER_PATH=${CONTAINER_PATH#unix://}
    else
        CONTAINER_HOST=unix://${CONTAINER_PATH}
    fi

    #export CONTAINER_HOST
    #export CONTAINER_PATH

    echo "CONTAINER_PATH [${CONTAINER_PATH}]"
    echo "CONTAINER_HOST [${CONTAINER_HOST}]"


# -----------------------------------------------------
# Create our Pod.
#[user@desktop]

    echo "--------"

    podman pod create \
        --replace \
        --name calycopis-pod

    podman pod ls


# -----------------------------------------------------
# Create our broker configuration files.
#[user@desktop]

    echo "--------"

    CONFIG_DIR=$(mktemp -d)

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

    echo "CONFIG_DIR [${CONFIG_DIR}]"


# -----------------------------------------------------
# Create our database configuration files.
#[user@desktop]

    echo "--------"

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

    LOG_DIR=$(mktemp -d)

    echo "LOG_DIR [${LOG_DIR}]"


# -----------------------------------------------------
# Create our test data file.
#[user@desktop]

    echo "--------"

    TESTDATA=$(
        mktemp -d
        )

    TESTFILE=${TESTDATA}/random.dat

    dd if=/dev/urandom of=${TESTFILE} bs=1MB count=10

    # https://stackoverflow.com/a/25045505
    chcon -Rt svirt_sandbox_file_t "${TESTFILE}"
    chmod a+r "${TESTFILE}"

    echo "TESTDATA [${TESTDATA}]"
    echo "TESTFILE [${TESTFILE}]"

    stat "${TESTFILE}"

# -----------------------------------------------------
# Start our PostgreSQL database.
#[user@desktop]

    echo "--------"

    podman run \
        --rm \
        --detach \
        --replace \
        --name postgres \
        --pod calycopis-pod \
        --expose 5432 \
        --env "POSTGRES_DB=calycopis" \
        --env "POSTGRES_USER_FILE=/etc/calycopis/pgusername" \
        --env "POSTGRES_PASSWORD_FILE=/etc/calycopis/pgpassword" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        docker.io/library/postgres:latest

    podman ps

# -----------------------------------------------------
# Start our broker service.
#[user@desktop]

    echo "--------"

    # Needed if our webapp isn't running as root.
    #chmod a+rwx "${LOG_DIR}"
    #chmod a+rx  "${CONFIG_DIR}"

    podman run \
        --rm \
        --detach \
        --replace \
        --user 0:0 \
        --expose 8082 \
        --pod calycopis-pod \
        --name calycopis-broker \
        --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${LOG_DIR}:/var/log/calycopis:rw,Z" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        --volume "${CONTAINER_PATH}:/run/podman/podman.sock:rw,Z" \
        images.dev.uksrc.org/calycopis/calycopis-broker:1.0.7-SNAPSHOT


    podman ps


# -----------------------------------------------------
# Create our test container.
#[user@desktop]

    echo "--------"

    buildtag=$(date '+%Y.%m.%d')
    buildtime=$(date '+%Y-%m-%dT%H:%M:%S')

    source "${HOME:?}/calycopis.env"
    pushd "${CALYCOPIS_CODE}"

        podman build \
            --build-arg "buildtag=${buildtag:?}" \
            --build-arg "buildtime=${buildtime:?}" \
            --tag "calycopis/python-tester:latest" \
            --tag "calycopis/python-tester:${buildtag:?}" \
            tests/python

    popd


# -----------------------------------------------------
# Run our test container.
#[user@desktop]

    echo "--------"
    echo "Running tests"

    podman run \
        --rm \
        --tty \
        --user 0:0 \
        --interactive \
        --pod  calycopis-pod \
        --name calycopis-tester \
        --env "TESTDATA=${TESTDATA:?}" \
        --env "TESTFILE=${TESTFILE:?}" \
        --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
        --volume "${CONFIG_DIR}:/etc/calycopis:ro,Z" \
        --volume "${CONTAINER_PATH}:/run/podman/podman.sock:rw,Z" \
        localhost/calycopis/python-tester:latest


