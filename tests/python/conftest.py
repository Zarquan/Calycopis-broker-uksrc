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
#     "timestamp": "2026-05-30T11:37:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-08-27T16:35:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 5,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-09-14T11:11:41",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 40,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Shared configuration and pytest fixtures for Calycopis integration tests.

Reads the test configuration from the YAML files in the shared config
directory (``/etc/calycopis`` by default) instead of environment variables:

  * ``admin.yaml``      - the broker admin credentials
                          (``calycopis.admin.username`` / ``calycopis.admin.password``).
  * ``testing.yaml``    - the test-data files
                          (``calycopis.broker.testing.testdata``, looked up by name).
  * ``database.yaml``   - the broker datasource settings used by the
                          state-transition tests (``spring.datasource``).

Provides session-scoped fixtures that:
- Seed test identities into the broker via the admin endpoint
- Create authenticated ExecutionBrokerClient instances for each test user

The config directory can be overridden with the ``CALYCOPIS_CONFIG_DIR``
environment variable for non-standard setups. The broker URL defaults to the
development container name (``CALYCOPIS_DEV_NAME``, falling back to
``calycopis-dev``) and can be overridden with ``CALYCOPIS_URL``.
"""

import base64
import functools
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.request
import uuid

import pytest
import yaml

from calycopis_openapi_client import ApiClient, Configuration
from calycopis_openapi_client.wrappers.execution_client import ExecutionBrokerClient


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# The directory holding the broker configuration files. Defaults to the
# shared /etc/calycopis volume; overridable for non-standard setups.
CONFIG_DIR = os.environ.get(
    "CALYCOPIS_BROKER_CONFIG_PATH",
    "/etc/calycopis"
    )

# Normalise the base URL so a trailing slash in the configuration cannot
# produce double-slash request paths (e.g. '//admin/identities').
# The default targets the development container (CALYCOPIS_DEV_NAME from
# calycopis.env, e.g. 'calycopis-dev') on the broker port 8082.
CALYCOPIS_URL = os.environ.get(
    "CALYCOPIS_URL",
    f"http://{os.environ.get('CALYCOPIS_BROKER_HOSTNAME', 'calycopis-broker')}:8082",
).rstrip("/")

# The Docker/Podman service socket used by the docker platform tests.
CONTAINER_HOST = os.environ.get(
    "CONTAINER_HOST",
    "unix:///run/podman/podman.sock",
)

# The broker database YAML has the form:
#   spring:
#       datasource:
#           url: jdbc:postgresql://postgres:5432/calycopis
#           username: <generated-username>
#           password: <generated-password>
#           driverClassName: org.postgresql.Driver
JDBC_URL_PATTERN = re.compile(
    r"jdbc:postgresql://(?P<host>[^:/]+):(?P<port>\d+)/(?P<dbname>[^/\s]+)"
)


@functools.lru_cache(maxsize=None)
def _load_yaml(filename):
    """Load a YAML file from the config directory.

    Results are cached so repeated lookups do not re-read the file.
    """
    path = os.path.join(CONFIG_DIR, filename)
    try:
        with open(path, encoding="utf-8") as handle:
            return yaml.safe_load(handle)
    except FileNotFoundError as error:
        raise RuntimeError(
            f"Missing test configuration file [{path}]. "
            f"Create the /etc/calycopis configuration files as described "
            f"in AGENTS.md (see 'Python tests' and 'Database service')."
        ) from error


def admin_credentials():
    """The broker admin (username, password) from admin.yaml."""
    data = _load_yaml("admin.yaml")
    admin = data["calycopis"]["admin"]
    return admin["username"], admin["password"]


def test_data(name):
    """The testing.yaml testdata entry with the given name.

    Raises KeyError (listing the available names) if no entry matches.
    """
    data = _load_yaml("testing.yaml")
    testdata = data["calycopis"]["broker"]["testing"]["testdata"]
    for entry in testdata:
        if entry.get("name") == name:
            return entry
    available = ", ".join(str(entry.get("name")) for entry in testdata)
    raise KeyError(
        f"Test data entry [{name}] not found in [{CONFIG_DIR}/testing.yaml]. "
        f"Available entries: [{available}]"
    )


def test_data_file(name="random.dat"):
    """The host path of a test-data file from testing.yaml.

    This is the path as seen by the host Podman service, which is the path
    that must be bind-mounted into application containers.
    """
    return test_data(name)["location"]


def datasource_config():
    """Read the broker datasource settings from the database YAML.

    The YAML path can be overridden with CALYCOPIS_DATABASE_YAML and the
    database host with CALYCOPIS_DB_HOST (for setups where the database is
    not reachable via the hostname in the datasource URL).
    """
    path = os.environ.get(
        "CALYCOPIS_DATABASE_YAML",
        os.path.join(CONFIG_DIR, "database.yaml"),
    )
    with open(path, encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    datasource = data["spring"]["datasource"]
    url = datasource["url"]
    match = JDBC_URL_PATTERN.match(url)
    if match is None:
        raise RuntimeError(f"Unable to parse datasource url [{url}]")
    host = os.environ.get(
        "CALYCOPIS_DATABASE_HOSTNAME",
        match.group("host")
        )
    return {
        "host": host,
        "port": int(match.group("port")),
        "dbname": match.group("dbname"),
        "user": datasource["username"],
        "password": datasource["password"],
    }


def phase_timeout(default=120.0):
    """The PHASE_TIMEOUT setting as a float, with a per-suite default."""
    return float(os.environ.get("PHASE_TIMEOUT", str(default)))


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _random_username(prefix):
    """Generate a random username with a recognisable prefix."""
    return f"{prefix}-{uuid.uuid4().hex[:8]}"


def _random_password():
    """Generate a random password."""
    return secrets.token_urlsafe(16)


def _seed_user(username, password, admin_username, admin_password):
    """Create a user identity via the admin endpoint.

    Returns the UUID of the created identity.
    Handles 409 Conflict gracefully (user already exists).
    """
    url = f"{CALYCOPIS_URL}/admin/identities"
    # Debug: show the exact request URL so a misconfigured base URL
    # (e.g. one with a trailing slash producing '//admin/identities')
    # is immediately obvious in the test output.
    print(f"[seed-user] POST {url}")
    data = json.dumps({"username": username, "password": password}).encode("utf-8")
    creds = base64.b64encode(
        f"{admin_username}:{admin_password}".encode("utf-8")
    ).decode("utf-8")

    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    req.add_header("Authorization", f"Basic {creds}")

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return body.get("uuid")
    except urllib.error.HTTPError as e:
        if e.code == 409:
            body = json.loads(e.read().decode("utf-8"))
            return body.get("uuid")
        raise RuntimeError(
            f"Failed to seed user '{username}': HTTP {e.code} "
            f"- request {url} - {e.read().decode('utf-8', errors='replace')}"
        ) from e


def _make_authenticated_client(host, username, password):
    """Create an ExecutionBrokerClient with HTTP Basic auth credentials."""
    cfg = Configuration(host=host, username=username, password=password)
    api_client = ApiClient(cfg)
    basic_creds = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    api_client.default_headers["Authorization"] = f"Basic {basic_creds}"
    return ExecutionBrokerClient(host=host, api_client=api_client)


def _server_reachable():
    """Return True if the Calycopis broker is responding."""
    try:
        urllib.request.urlopen(CALYCOPIS_URL, timeout=5)
        return True
    except urllib.error.HTTPError:
        return True
    except Exception:
        return False


# ---------------------------------------------------------------------------
# Session-scoped fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def alice_creds():
    """Random credentials for the 'alice' test user."""
    return {"username": _random_username("alice"), "password": _random_password()}


@pytest.fixture(scope="session")
def bob_creds():
    """Random credentials for the 'bob' test user."""
    return {"username": _random_username("bob"), "password": _random_password()}


@pytest.fixture(scope="session", autouse=True)
def seed_identities(alice_creds, bob_creds):
    """Seed test identities into the broker before any tests run."""
    if not _server_reachable():
        pytest.skip(f"Calycopis broker not reachable at {CALYCOPIS_URL}")
    admin_username, admin_password = admin_credentials()
    _seed_user(
        alice_creds["username"],
        alice_creds["password"],
        admin_username,
        admin_password,
    )
    _seed_user(
        bob_creds["username"],
        bob_creds["password"],
        admin_username,
        admin_password,
    )


@pytest.fixture(scope="session")
def alice_client(seed_identities, alice_creds):
    """Authenticated ExecutionBrokerClient for the 'alice' test user."""
    return _make_authenticated_client(
        CALYCOPIS_URL,
        alice_creds["username"],
        alice_creds["password"],
    )


@pytest.fixture(scope="session")
def bob_client(seed_identities, bob_creds):
    """Authenticated ExecutionBrokerClient for the 'bob' test user."""
    return _make_authenticated_client(
        CALYCOPIS_URL,
        bob_creds["username"],
        bob_creds["password"],
    )


@pytest.fixture(scope="session")
def anon_client(seed_identities):
    """Unauthenticated ExecutionBrokerClient for testing public access."""
    return ExecutionBrokerClient(host=CALYCOPIS_URL)


@pytest.fixture(scope="session")
def client(alice_client):
    """Default authenticated client (aliases alice_client).

    Provides backward compatibility with existing tests that use
    a 'client' fixture parameter.
    """
    return alice_client


# ---------------------------------------------------------------------------
# Stable module alias
# ---------------------------------------------------------------------------

# Register this module under a stable name so that test modules and
# subdirectory conftest files can import the shared configuration helpers.
# Pytest imports each conftest.py as a module named "conftest" and deletes
# the previous one from sys.modules before loading a conftest from a
# subdirectory (see _pytest.config.__init__), so importing "conftest"
# directly would hit a circular import or resolve to the wrong module.
sys.modules.setdefault("calycopis_conftest", sys.modules[__name__])
