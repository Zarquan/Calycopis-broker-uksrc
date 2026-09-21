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

    source "${HOME}/calycopis.env"

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
# Configure the test user accounts.
#[user@desktop]

    echo "--------"
    echo "Configuring users"

    source "${HOME}/calycopis.env"

    podman run \
        --rm \
        --pod "${CALYCOPIS_POD_NAME:?}" \
        --env-file "${CALYCOPIS_VARS}" \
        --env-file "${OVERRIDE_VARS}" \
        --volume "${CALYCOPIS_BROKER_CONFIG_VOLUME:?}:${CALYCOPIS_BROKER_CONFIG_PATH}" \
        --volume "${CALYCOPIS_CODE}:${CALYCOPIS_BROKER_CODE:?}" \
        "${CALYCOPIS_TOOLS_CONTAINER_IMAGE:?}" \
            "${CALYCOPIS_BROKER_CODE:?}/bin/config-users.sh"






