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
#     along with this software. If not, see <http://www.gnu.org/licenses/>.
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
#

"""
Integration tests for the Prometheus compute pool metrics.

The broker exposes the metrics on the actuator Prometheus endpoint. The test
creates an isolated pool with a known allocation, waits for the metrics refresh,
and checks the allocated, reserved and available values for that pool.

Requires:
  - A running Calycopis broker with the Prometheus registry.
  - The admin credentials from admin.yaml, which conftest reads.
"""

import json
import re
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

import pytest

from calycopis_conftest import admin_request

POOLS_PATH = "/admin/pools/compute"
METRICS_PATH = "/actuator/prometheus"

CORES_METRIC = "calycopis_compute_pool_cores"
MEMORY_METRIC = "calycopis_compute_pool_memory_bytes"

BYTES_PER_GIB = 1_073_741_824

METRIC_LINE = re.compile(
    r"^(?P<name>[A-Za-z_:][A-Za-z0-9_:]*)"
    r"(?:\{(?P<labels>[^}]*)\})? "
    r"(?P<value>\S+)$"
)

LABEL = re.compile(
    r'(?P<name>[A-Za-z_][A-Za-z0-9_]*)="(?P<value>(?:[^"\\]|\\.)*)"'
)


def _get_metrics(broker_url):
    """Read the Prometheus endpoint and return (content type, text)."""
    with urllib.request.urlopen(
        broker_url + METRICS_PATH,
        timeout=10
    ) as response:
        return (
            response.headers.get_content_type(),
            response.read().decode("utf-8")
        )


def _parse_samples(text):
    """Parse Prometheus exposition lines into (name, labels, value)."""
    samples = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        match = METRIC_LINE.match(line)
        if match is None:
            continue
        labels = {}
        if match.group("labels"):
            for label in LABEL.finditer(match.group("labels")):
                labels[label.group("name")] = label.group("value")
        try:
            value = float(match.group("value"))
        except ValueError:
            continue
        samples.append((match.group("name"), labels, value))
    return samples


def _metric_value(samples, metric_name, pool_name, state):
    """Find one sample by metric, pool name and state."""
    for name, labels, value in samples:
        if name != metric_name:
            continue
        if labels.get("pool") != pool_name:
            continue
        if labels.get("state") != state:
            continue
        return value
    return None


def _create_pool():
    """Create an isolated pool and return its name and identifier."""
    name = "metrics-pool-" + uuid.uuid4().hex[:12]
    status, body = admin_request(
        "POST",
        POOLS_PATH,
        {
            "name": name,
            "description": "A pool for Prometheus metrics tests."
        }
    )
    assert status == 201, f"Expected 201 Created, got {status}: {body}"
    return name, json.loads(body)["uuid"]


def _create_allocation(pool_uuid, cores, memory_gib):
    """Create an allocation covering the current time."""
    now = datetime.now(timezone.utc)
    status, body = admin_request(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}/allocations",
        {
            "cores": cores,
            "memory": memory_gib,
            "start": (now - timedelta(hours=1)).isoformat(),
            "end": (now + timedelta(hours=1)).isoformat()
        }
    )
    assert status in (200, 201, 303), (
        f"Expected an allocation, got {status}: {body}"
    )


def test_prometheus_compute_pool_metrics(broker_url):
    """The endpoint must expose allocated, reserved and available values."""
    pool_name, pool_uuid = _create_pool()
    _create_allocation(
        pool_uuid,
        cores=7,
        memory_gib=3
    )

    deadline = time.monotonic() + 45
    last_text = ""
    samples = []

    while time.monotonic() < deadline:
        content_type, last_text = _get_metrics(broker_url)
        assert content_type == "text/plain", (
            f"Expected text/plain from the Prometheus endpoint, got {content_type}"
        )

        samples = _parse_samples(last_text)
        allocated_cores = _metric_value(
            samples,
            CORES_METRIC,
            pool_name,
            "allocated"
        )
        available_cores = _metric_value(
            samples,
            CORES_METRIC,
            pool_name,
            "available"
        )
        if allocated_cores == 7.0 and available_cores == 7.0:
            break

        time.sleep(2)
    else:
        pytest.fail(
            "The compute pool metrics did not appear within 45 seconds. "
            f"Last metrics tail:\n{last_text[-2000:]}"
        )

    assert f"# HELP {CORES_METRIC}" in last_text
    assert f"# TYPE {CORES_METRIC} gauge" in last_text
    assert f"# HELP {MEMORY_METRIC}" in last_text
    assert f"# TYPE {MEMORY_METRIC} gauge" in last_text

    assert _metric_value(
        samples,
        CORES_METRIC,
        pool_name,
        "reserved"
    ) == 0.0
    assert _metric_value(
        samples,
        CORES_METRIC,
        pool_name,
        "available"
    ) == 7.0

    assert _metric_value(
        samples,
        MEMORY_METRIC,
        pool_name,
        "allocated"
    ) == 3 * BYTES_PER_GIB
    assert _metric_value(
        samples,
        MEMORY_METRIC,
        pool_name,
        "reserved"
    ) == 0.0
    assert _metric_value(
        samples,
        MEMORY_METRIC,
        pool_name,
        "available"
    ) == 3 * BYTES_PER_GIB
