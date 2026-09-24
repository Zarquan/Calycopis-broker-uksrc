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
#     "timestamp": "2026-06-06T01:36:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-06-06T02:00:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 5,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-09-24T14:30:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.5-rc.3",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 60,
#       "units": "%"
#       }
#     }
#   ]
#
"""Broker client factory and environment helpers.

Broker endpoints and demo user credentials are read from the deployment
state files in ``demo/build/`` (``hosts.yaml`` and ``users.yaml``) with a
fallback to the ``BROKER_*_URL`` / ``DEMO_USER`` / ``DEMO_PASS`` environment
variables used by the earlier bind-mount deployments.
"""

import base64
import os
from pathlib import Path

import yaml

from calycopis_schema_client import ApiClient, Configuration
from calycopis_schema_client.wrappers import ExecutionBrokerClient

# Default broker name -> env var / label map, used as a fallback when
# demo/build/hosts.yaml is not available.
BROKERS = {
    "alpha": "BROKER_ALPHA_URL",
    "beta": "BROKER_BETA_URL",
    "gamma": "BROKER_GAMMA_URL",
    "delta": "BROKER_DELTA_URL",
}

BROKER_LABELS = {
    "alpha": "Alpha (Green HPC)",
    "beta": "Beta (Cloud)",
    "gamma": "Gamma (Budget)",
    "delta": "Delta (General Purpose Cloud)",
}

# Location of the deployment state files.  Override with CALYCOPIS_DEMO_BUILD
# when the tools are run from a directory other than the demo root.
DEMO_BUILD_DIR = Path(os.environ.get("CALYCOPIS_DEMO_BUILD", "demo/build"))
HOSTS_YAML = DEMO_BUILD_DIR / "hosts.yaml"
USERS_YAML = DEMO_BUILD_DIR / "users.yaml"

HEALTH_SUFFIX = "/actuator/health"


def _normalise_endpoint(endpoint: str) -> str:
    """Strip a trailing /actuator/health suffix to get the API base URL."""
    endpoint = endpoint.strip().rstrip("/")
    if endpoint.endswith(HEALTH_SUFFIX):
        endpoint = endpoint[: -len(HEALTH_SUFFIX)].rstrip("/")
    return endpoint


def load_demo_hosts(path: Path = HOSTS_YAML) -> dict[str, str]:
    """Read broker name -> base URL from demo/build/hosts.yaml.

    Returns an empty dict when the file does not exist or has no entries.
    """
    if not path.exists():
        return {}
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    hosts = {}
    for entry in data.get("calycopis", {}).get("demo", {}).get("hosts") or []:
        name = entry.get("name")
        endpoint = entry.get("endpoint")
        if name and endpoint:
            hosts[name] = _normalise_endpoint(endpoint)
    return hosts


def load_demo_users(path: Path = USERS_YAML) -> list[dict]:
    """Read demo user accounts from demo/build/users.yaml."""
    if not path.exists():
        return []
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return data.get("calycopis", {}).get("demo", {}).get("users") or []


def get_env():
    """Return demo user credentials from the environment or users.yaml."""
    username = os.environ.get("DEMO_USER")
    password = os.environ.get("DEMO_PASS")
    if not username or not password:
        users = load_demo_users()
        if users:
            username = users[0].get("name")
            password = users[0].get("pass")
    if not username or not password:
        raise RuntimeError(
            "DEMO_USER and DEMO_PASS must be set (or demo/build/users.yaml must "
            "exist). Run: bin/make-demo-env.sh && source run/demo-user.env"
        )
    return username, password


def make_client(broker_url: str) -> ExecutionBrokerClient:
    """Create an authenticated ExecutionBrokerClient."""
    username, password = get_env()
    cfg = Configuration(host=broker_url, username=username, password=password)
    api_client = ApiClient(cfg)
    creds = base64.b64encode(f"{username}:{password}".encode()).decode()
    api_client.default_headers["Authorization"] = f"Basic {creds}"
    return ExecutionBrokerClient(host=broker_url, api_client=api_client)


def get_brokers() -> dict[str, str]:
    """Return broker name to URL mapping.

    Prefers the deployment state file ``demo/build/hosts.yaml``; falls back
    to the ``BROKER_*_URL`` environment variables that are set.
    """
    brokers = load_demo_hosts()
    if brokers:
        return brokers

    brokers = {}
    for name, var in BROKERS.items():
        url = os.environ.get(var)
        if url:
            brokers[name] = url

    if not brokers:
        raise RuntimeError(
            "No brokers found. Set the BROKER_*_URL environment variables "
            "or provide demo/build/hosts.yaml."
        )
    return brokers