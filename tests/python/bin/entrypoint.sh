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

export CALYCOPIS_URL=${CALYCOPIS_URL:='http://calycopis-broker:8082'}

export CALYCOPIS_ADMIN_USERNAME="$(
    yq \
        '.calycopis.admin.username' \
        /etc/calycopis/admin.yaml
    )"

export CALYCOPIS_ADMIN_PASSWORD="$(
    yq \
        '.calycopis.admin.password' \
        /etc/calycopis/admin.yaml
    )"

cd /opt/python-tests/

#pytest -v any
pytest -v docker


