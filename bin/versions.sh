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

configfile=${1:?}

CALYCOPIS_BROKER_VERSION=$(
    yq '.broker.version' "${configfile:?}"
    )

CALYCOPIS_OPENAPI_SCHEMA_VERSION=$(
    yq '.openapi.schema.version' "${configfile:?}"
    )

CALYCOPIS_OPENAPI_SPRING_VERSION=$(
    yq '.openapi.spring.version' "${configfile:?}"
    )

CALYCOPIS_OPENAPI_PYTHON_VERSION=$(
    yq '.openapi.python.version' "${configfile:?}"
    )

export CALYCOPIS_BROKER_VERSION
export CALYCOPIS_OPENAPI_SCHEMA_VERSION
export CALYCOPIS_OPENAPI_SPRING_VERSION
export CALYCOPIS_OPENAPI_PYTHON_VERSION

#
# Update GitHub environment variables.
if [ -n "${GITHUB_ENV}" ]
then
cat >> "${GITHUB_ENV}" << EOF
CALYCOPIS_BROKER_VERSION=${CALYCOPIS_BROKER_VERSION}
CALYCOPIS_OPENAPI_SCHEMA_VERSION=${CALYCOPIS_OPENAPI_SCHEMA_VERSION}
CALYCOPIS_OPENAPI_SPRING_VERSION=${CALYCOPIS_OPENAPI_SPRING_VERSION}
CALYCOPIS_OPENAPI_PYTHON_VERSION=${CALYCOPIS_OPENAPI_PYTHON_VERSION}
EOF
fi

