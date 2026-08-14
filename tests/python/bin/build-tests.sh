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

    set -euo pipefail

    echo "--------"
    echo "CALYCOPIS_BROKER_VERSION  [${CALYCOPIS_BROKER_VERSION}]"
    echo "CALYCOPIS_OPENAPI_SCHEMA_VERSION [${CALYCOPIS_OPENAPI_SCHEMA_VERSION}]"
    echo "CALYCOPIS_OPENAPI_SPRING_VERSION [${CALYCOPIS_OPENAPI_SPRING_VERSION}]"
    echo "CALYCOPIS_OPENAPI_PYTHON_VERSION [${CALYCOPIS_OPENAPI_PYTHON_VERSION}]"

    echo "CONTAINER_PATH [${CONTAINER_PATH}]"
    echo "CONTAINER_HOST [${CONTAINER_HOST}]"

# -----------------------------------------------------
# Create our test container.
#[user@desktop]

    echo "--------"
    echo "Building test container"

    source "${HOME}/calycopis.env"
    pushd "${CALYCOPIS_CODE}"

        podman build \
            --build-arg "buildtime=$(date '+%Y-%m-%dT%H:%M:%S')" \
            --build-arg "CALYCOPIS_BROKER_VERSION=${CALYCOPIS_BROKER_VERSION}" \
            --build-arg "CALYCOPIS_OPENAPI_SPRING_VERSION=${CALYCOPIS_OPENAPI_SPRING_VERSION}" \
            --build-arg "CALYCOPIS_OPENAPI_PYTHON_VERSION=${CALYCOPIS_OPENAPI_PYTHON_VERSION}" \
            --tag "calycopis/python-tester:latest" \
            --tag "calycopis/python-tester:${CALYCOPIS_BROKER_VERSION}" \
            tests/python

    popd

