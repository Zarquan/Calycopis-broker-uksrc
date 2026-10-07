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
source "${CALYCOPIS_CODE}/calycopis.vars"

echo "Deleting pods"
for pod in $(
    podman pod ls -q
    )
do
    echo "Deleting pod [${pod}]"
    podman pod stop ${pod}
    podman pod rm ${pod}
done

echo "Deleting containers"
for container in $(
    podman ps -aq
    )
do
    echo "Deleting container [${container}]"
    podman stop ${container}
    podman rm ${container}
done

echo "Deleting volumes"
for volume in $(
    podman volume ls -q
    )
do
    echo "Deleting volume [${volume}]"
    podman volume rm ${volume}
done

echo "Deleting networks"
for network in $(
    podman network ls -q
    )
do
    if [[ ${network} != 'podman' ]]
    then
        echo "Deleting network [${network}]"
        podman network rm ${network}
    fi
done

