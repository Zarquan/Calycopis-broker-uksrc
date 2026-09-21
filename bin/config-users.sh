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

echo "----------------"
echo "Configuring users"
echo "Broker config [${CALYCOPIS_BROKER_CONFIG_PATH:?}]"

CALYCOPIS_BROKER_ENDPOINT="http://${CALYCOPIS_BROKER_HOSTNAME}:8082"

echo "Broker endpoint [${CALYCOPIS_BROKER_ENDPOINT:?}]"

create_user() {
    local user_name=$1
    local user_pass=$2
    local admin_auth=$3

    echo "----"
    echo "Creating user [${user_name}][${user_pass}] on broker [${CALYCOPIS_BROKER_ENDPOINT}]"

    curl \
        --silent \
        --show-error \
        -X POST \
        -H "Content-Type: application/json" \
        -H "Accept: application/json" \
        -H "Authorization: Basic ${admin_auth}" \
        -d "{\"username\": \"${user_name}\", \"password\": \"${user_pass}\"}" \
        "${CALYCOPIS_BROKER_ENDPOINT}/admin/identities"

    echo "----"
    }

admin_user=$(
    yq '.calycopis.admin.username' \
        "${CALYCOPIS_BROKER_CONFIG_PATH:?}/admin.yaml"
    )

admin_pass=$(
    yq '.calycopis.admin.password' \
        "${CALYCOPIS_BROKER_CONFIG_PATH:?}/admin.yaml"
    )

admin_auth=$(
    echo -n "${admin_user}:${admin_pass}" | base64 -w 0
    )

#echo "Admin user [${admin_user}]"
#echo "Admin pass [${admin_pass}]"
#echo "Admin auth [${admin_auth}]"


for user_name in $(
    yq '.calycopis.demo.users.[].name' "${CALYCOPIS_BROKER_CODE:?}/demo/build/users.yaml"
    )
do
    export user_name
    user_pass=$(
        yq '.calycopis.demo.users.[] | select(.name == strenv(user_name)) | .pass' "${CALYCOPIS_BROKER_CODE:?}/demo/build/users.yaml"
        )
    create_user "${user_name}" "${user_pass}" "${admin_auth}"

done


