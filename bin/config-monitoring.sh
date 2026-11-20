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
# AIMetrics: [
#     {
#     "timestamp": "2026-11-20T17:13:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-v4.1-flash-1017",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#

set -euo pipefail

echo "----------------"
echo "Configuring monitoring"
echo "Monitoring config [${CALYCOPIS_MONITORING_CONFIG_PATH:?}]"

config_path="${CALYCOPIS_MONITORING_CONFIG_PATH:?}"
code_path="${CALYCOPIS_BROKER_CODE:?}"

mkdir -p \
    "${config_path}/grafana/provisioning/datasources" \
    "${config_path}/grafana/provisioning/dashboards" \
    "${config_path}/grafana/dashboards"

chmod 755 \
    "${config_path}" \
    "${config_path}/grafana" \
    "${config_path}/grafana/provisioning" \
    "${config_path}/grafana/provisioning/datasources" \
    "${config_path}/grafana/provisioning/dashboards" \
    "${config_path}/grafana/dashboards"

#
# Prometheus scrapes the broker through the pod's internal network.
#
sed \
    "s|__BROKER_TARGET__|${CALYCOPIS_BROKER_HOSTNAME:?}:${CALYCOPIS_BROKER_INTERNAL_PORT:?}|g" \
    "${code_path}/docker/monitoring/prometheus/prometheus.yml" \
    > "${config_path}/prometheus.yml"

#
# Grafana uses the Prometheus service in the same pod.
#
sed \
    "s|__PROMETHEUS_URL__|http://${CALYCOPIS_PROMETHEUS_HOSTNAME:?}:${CALYCOPIS_PROMETHEUS_INTERNAL_PORT:?}|g" \
    "${code_path}/docker/monitoring/grafana/provisioning/datasources/prometheus.yml" \
    > "${config_path}/grafana/provisioning/datasources/prometheus.yml"

#
# The dashboard provider points at the copied dashboard directory in the
# monitoring volume, so an overridden monitoring path still works.
#
sed \
    "s|__DASHBOARD_PATH__|${config_path}/grafana/dashboards|g" \
    "${code_path}/docker/monitoring/grafana/provisioning/dashboards/calycopis.yml" \
    > "${config_path}/grafana/provisioning/dashboards/calycopis.yml"

cp \
    "${code_path}/docker/monitoring/grafana/dashboards/calycopis-compute-pools.json" \
    "${config_path}/grafana/dashboards/calycopis-compute-pools.json"

#
# The Grafana image reads its admin password from a file, so the password
# never appears in the container environment. Reuse an existing password when
# the deployment is rerun against the same config volume.
#
if [[ ! -s "${config_path}/grafanapassword" ]]
then
    pwgen 32 1 > "${config_path}/grafanapassword"
fi

chmod 644 \
    "${config_path}/prometheus.yml" \
    "${config_path}/grafanapassword" \
    "${config_path}/grafana/provisioning/datasources/prometheus.yml" \
    "${config_path}/grafana/provisioning/dashboards/calycopis.yml" \
    "${config_path}/grafana/dashboards/calycopis-compute-pools.json"

echo "--------"
yq '.' "${config_path}/prometheus.yml"

echo "--------"
yq '.' "${config_path}/grafana/provisioning/datasources/prometheus.yml"

echo "--------"
yq '.' "${config_path}/grafana/provisioning/dashboards/calycopis.yml"

echo "--------"
echo "Grafana admin password file [${config_path}/grafanapassword]"
