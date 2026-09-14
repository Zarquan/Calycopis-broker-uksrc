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
# AIMetrics: [
#     {
#     "timestamp": "2026-09-14T11:11:41",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 30,
#       "units": "%"
#       }
#     }
#   ]
#
#

set -euo pipefail

# The broker URL for the test run.  The default here targets the broker
# container in the CI test pod (there is no dev container in CI).  Locally
# the tests default CALYCOPIS_URL to the dev container name themselves, so
# this export is only needed to override that default.
export CALYCOPIS_URL=${CALYCOPIS_URL:='http://calycopis-broker:8082'}

# The tests read the admin credentials, the test-data details and the
# database datasource directly from the /etc/calycopis YAML files
# (admin.yaml, testing.yaml and database.yaml) via the shared
# tests/python/conftest.py, so no environment variables are exported for
# those.

cd /opt/python-tests/

#pytest -v any
pytest -v states
pytest -v docker
#pytest -v docker/test_docker_androcles_md5.py


