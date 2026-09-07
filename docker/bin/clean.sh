#!/bin/sh
#
# <meta:header>
#   <meta:licence>
#     Copyright (c) 2024, Manchester (http://www.manchester.ac.uk/)
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

# -----------------------------------------------------
# Delete all pods.
#[user@desktop]

    echo "Checking pods"
    if [ -n "$(podman pod ps -q)" ]
    then
        echo "Deleting pods"
        podman pod rm -f $(podman pod ps -q)
    fi

# -----------------------------------------------------
# Delete all containers.
#[user@desktop]

    echo "Checking containers"
    if [ -n "$(podman ps -aq)" ]
    then
        echo "Deleting containers"
        podman rm -f $(podman ps -aq)
    fi

# -----------------------------------------------------
# Delete all volumes.
#[user@desktop]

    echo "Checking volumes"
    if [ -n "$(podman volume ls -q)" ]
    then
        echo "Deleting volumes"
        podman volume rm -f $(podman volume ls -q)
    fi


# -----------------------------------------------------
# Delete all networks.
#[user@desktop]

    echo "Checking networks"
    if [ -n "$(podman network ls -q)" ]
    then
        echo "Deleting networks"
        podman network rm -f $(podman network ls -q)
    fi


# -----------------------------------------------------
# Delete all images.
#[user@desktop]

    echo "Checking images"
    if [ -n "$(podman images -q)" ]
    then
        echo "Deleting images"
        podman rmi -f $(podman images -q)

        echo "Checking images"
        if [ -n "$(podman images -q)" ]
        then
            echo "Deleting images"
            podman rmi -f $(podman images -q)
        fi
    fi

