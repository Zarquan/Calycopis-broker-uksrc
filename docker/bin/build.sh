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
# Build our containers.
#[user@desktop]

    buildtag=$(date '+%Y.%m.%d')
    buildtime=$(date '+%Y-%m-%dT%H:%M:%S')

    source "${HOME:?}/calycopis.env"
    pushd "${CALYCOPIS_CODE}"

        podman build \
            --build-arg "buildtag=${buildtag:?}" \
            --build-arg "buildtime=${buildtime:?}" \
            --tag "calycopis/fedora-base:latest" \
            --tag "calycopis/fedora-base:${buildtag:?}" \
            docker/fedora-base

        podman build \
            --build-arg "buildtag=${buildtag:?}" \
            --build-arg "buildtime=${buildtime:?}" \
            --tag "calycopis/developer-tools:latest" \
            --tag "calycopis/developer-tools:${buildtag:?}" \
            docker/developer-tools

        podman build \
            --build-arg "buildtag=${buildtag:?}" \
            --build-arg "buildtime=${buildtime:?}" \
            --tag "calycopis/deepseek-harness:latest" \
            --tag "calycopis/deepseek-harness:${buildtag:?}" \
            docker/deepseek-harness

    popd


