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

unset HOST_CONTAINER_HOST
unset HOST_CONTAINER_PATH

HOST_CONTAINER_PATH=$(
    podman info --format '{{.Host.RemoteSocket.Path}}'
    )

if [[ ${HOST_CONTAINER_PATH} == unix://* ]]
then
    HOST_CONTAINER_HOST=${HOST_CONTAINER_PATH}
    HOST_CONTAINER_PATH=${HOST_CONTAINER_PATH#unix://}
else
    HOST_CONTAINER_HOST=unix://${HOST_CONTAINER_PATH}
fi

export HOST_CONTAINER_PATH
export HOST_CONTAINER_HOST

#
# Update GitHub environment variables.
if [ -n "${GITHUB_ENV}" ]
then
cat >> "${GITHUB_ENV}" << EOF
HOST_CONTAINER_PATH=${HOST_CONTAINER_PATH}
HOST_CONTAINER_HOST=${HOST_CONTAINER_HOST}
EOF
fi

