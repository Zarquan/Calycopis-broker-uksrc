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

cat > "${CALYCOPIS_BROKER_CONFIG_PATH:?}/admin.yaml" << EOF
calycopis:
    admin:
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
EOF

cat > "${CALYCOPIS_BROKER_CONFIG_PATH:?}/database.yaml" << EOF
spring:
    datasource:
        url: jdbc:postgresql://${CALYCOPIS_DATABASE_HOSTNAME:?}:${CALYCOPIS_DATABASE_PORT:?}/${CALYCOPIS_DATABASE_NAME:?}
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
        driverClassName: org.postgresql.Driver
        initialize: true
EOF

cat > "${CALYCOPIS_BROKER_CONFIG_PATH:?}/spring.yaml" << EOF
spring:
    profiles:
        active: docker
EOF

#yq '.' "${CALYCOPIS_BROKER_CONFIG_PATH:?}/admin.yaml"

#yq '.' "${CALYCOPIS_BROKER_CONFIG_PATH:?}/database.yaml"

#yq '.' "${CALYCOPIS_BROKER_CONFIG_PATH:?}/spring.yaml"


