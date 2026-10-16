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
#     },
#     {
#     "interval": "2026-10-11T11:49:00/2026-10-11T11:54:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 45,
#       "units": "%"
#       }
#     },
#     {
#     "interval": "2026-10-16T04:45:00/2026-10-16T05:35:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 60,
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
import collections
import functools
import json
import math
import os
import re
import secrets
import statistics
import sys
import urllib.error
import urllib.request
import uuid

from datetime import datetime, timezone

import pytest
import yaml

from calycopis_openapi_client import ApiClient, Configuration
from calycopis_openapi_client.exceptions import NotFoundException
from calycopis_openapi_client.models import SimpleExecutionSessionPhase
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
# Live sessions, and a clean broker (issue #174)
# ---------------------------------------------------------------------------

# The phases in which a session is still holding the resources it was offered.
# A session in any of these occupies capacity, so it can displace a later
# request, which matters to the timing-sensitive tests.
LIVE_SESSION_PHASES = (
    "OFFERED",
    "ACCEPTED",
    "WAITING",
    "PREPARING",
    "AVAILABLE",
    "RUNNING",
    "RELEASING",
)

# The phases from which a session will not move again.
TERMINAL_SESSION_PHASES = (
    "COMPLETED",
    "FAILED",
    "CANCELLED",
    "REJECTED",
    "EXPIRED",
)

# How long to wait for a session to stop during clean-up.
CLEANUP_TIMEOUT = float(os.environ.get("CALYCOPIS_CLEANUP_TIMEOUT", "120"))


def live_sessions():
    """The sessions the broker is holding, as a list of (uuid, phase).

    This reads the database, because the API has no way to list sessions yet.
    When the listing endpoints in issue #175 land, switch this to
    GET /sessions?phases=... so the platform independent tests stop needing a
    database connection. Keep the signature, so that the change stays in one
    function.

    psycopg is imported lazily, so a suite that never calls this does not need
    it installed.
    """
    import psycopg

    placeholders = ", ".join(["%s"] * len(LIVE_SESSION_PHASES))
    connection = psycopg.connect(**datasource_config())
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT uuid, phase FROM simpleexecutionsessions "
                f"WHERE phase IN ({placeholders}) "
                "ORDER BY phase, uuid",
                LIVE_SESSION_PHASES,
            )
            return [(str(row[0]), row[1]) for row in cursor.fetchall()]
    finally:
        connection.close()


def assert_clean_broker():
    """Fail unless the broker is holding no sessions.

    This fails rather than skipping. A timing-sensitive test that cannot get
    the block it asked for would otherwise report a result it cannot justify,
    or skip and hide the problem. See issue #174.
    """
    live = live_sessions()
    if live:
        listed = ", ".join(f"{uuid}({phase})" for uuid, phase in live[:10])
        suffix = "" if len(live) <= 10 else f", and {len(live) - 10} more"
        raise AssertionError(
            f"The broker is not clean: {len(live)} live session(s) "
            f"[{listed}{suffix}]. A timing-sensitive test needs a broker that "
            "is not holding reservations. See issue #175 for the listing "
            "endpoints that would make this an API call."
        )


@pytest.fixture
def clean_broker():
    """Fail the test unless the broker is holding no sessions.

    Used by the timing-sensitive tests, which need free capacity in the block
    they ask for.
    """
    assert_clean_broker()


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
def raw_alice_client(seed_identities, alice_creds):
    """Authenticated ExecutionBrokerClient for the 'alice' test user."""
    return _make_authenticated_client(
        CALYCOPIS_URL,
        alice_creds["username"],
        alice_creds["password"],
    )


@pytest.fixture(scope="session")
def raw_bob_client(seed_identities, bob_creds):
    """Authenticated ExecutionBrokerClient for the 'bob' test user."""
    return _make_authenticated_client(
        CALYCOPIS_URL,
        bob_creds["username"],
        bob_creds["password"],
    )


@pytest.fixture(scope="session")
def raw_anon_client(seed_identities):
    """Unauthenticated ExecutionBrokerClient for testing public access."""
    return ExecutionBrokerClient(host=CALYCOPIS_URL)


# ---------------------------------------------------------------------------
# Per-test session clean-up (issue #174)
# ---------------------------------------------------------------------------

class SessionRecorder:
    """Record the sessions a test creates, and stop them afterwards.

    A session that is left in a live phase keeps holding the resources it was
    offered, and the next test to ask for that block is displaced. See
    issue #174.

    Use ``record()`` for a session or an offer set response, and
    ``clean_up()`` to reject or cancel everything recorded.
    """

    def __init__(self, client):
        self._client = client
        self._uuids = []

    def record(self, session_or_response):
        """Record a session, or every offer in an offer set response.

        A response is identified by having an ``offers`` attribute, which is
        present even when it is None or empty. This matters: a rejected request
        returns an offer set with no offers, whose own metadata carries a UUID
        that is not a session, so recording it would leave the clean-up asking
        the broker for a session that does not exist.

        The session UUID is on the component metadata (``meta.uuid``), which is
        how the tests identify a session elsewhere.
        """
        if hasattr(session_or_response, "offers"):
            for offer in getattr(session_or_response, "offers") or []:
                self.record(offer)
            return
        session_uuid = getattr(session_or_response, "uuid", None)
        if session_uuid is None:
            session_uuid = getattr(
                getattr(session_or_response, "meta", None),
                "uuid",
                None,
            )
        if session_uuid is not None and session_uuid not in self._uuids:
            self._uuids.append(session_uuid)

    def clean_up(self):
        """Stop every recorded session, and wait for each one to stop.

        An OFFERED session is rejected, anything else is cancelled. A failure
        to clean up is raised, so it is not mistaken for a quiet success.
        """
        failures = []
        for session_uuid in list(self._uuids):
            try:
                self._clean_up_one(session_uuid)
            except Exception as error:
                failures.append(
                    f"{session_uuid}: {type(error).__name__}: {error}"
                )
        self._uuids = []
        if failures:
            raise AssertionError(
                f"Unable to clean up {len(failures)} session(s): "
                + "; ".join(failures)
            )

    def _clean_up_one(self, session_uuid):
        try:
            session = self._client.get_session(session_uuid)
        except NotFoundException:
            #
            # The session has already gone, so there is nothing to stop.
            #
            return
        phase = getattr(session, "phase", None)
        phase_value = getattr(phase, "value", phase)
        if phase_value in TERMINAL_SESSION_PHASES:
            return
        if phase_value == "OFFERED":
            target = SimpleExecutionSessionPhase.REJECTED
        else:
            target = SimpleExecutionSessionPhase.CANCELLED
        self._client.set_session_phase(session_uuid, target)
        #
        # Wait for the broker to confirm. The client wrapper's
        # wait_until_terminal() does not cover REJECTED, so poll for our own
        # set of terminal phases.
        #
        self._client.wait_for_phase(
            session_uuid,
            {
                SimpleExecutionSessionPhase.COMPLETED,
                SimpleExecutionSessionPhase.FAILED,
                SimpleExecutionSessionPhase.CANCELLED,
                SimpleExecutionSessionPhase.REJECTED,
                SimpleExecutionSessionPhase.EXPIRED,
                },
            timeout=CLEANUP_TIMEOUT,
            interval=2.0,
            )


class RecordingClient:
    """A client that records the sessions a test creates (issue #174).

    Everything else is delegated to the wrapped client.
    """

    def __init__(self, client, recorder):
        self._client = client
        self._recorder = recorder

    def __getattr__(self, name):
        return getattr(self._client, name)

    def submit_execution(self, *args, **kwargs):
        response = self._client.submit_execution(*args, **kwargs)
        self._recorder.record(response)
        return response

    def direct_execute(self, *args, **kwargs):
        session = self._client.direct_execute(*args, **kwargs)
        self._recorder.record(session)
        return session


def _recording_client(request, raw_client):
    """Wrap a client so the test's sessions are stopped afterwards.

    A test marked ``leaves_sessions`` gets the raw client instead, for the
    cases that intend to leave a session behind, such as the session expiry
    tests.
    """
    if request.node.get_closest_marker("leaves_sessions") is not None:
        return raw_client
    recorder = SessionRecorder(raw_client)
    request.addfinalizer(recorder.clean_up)
    return RecordingClient(raw_client, recorder)


@pytest.fixture
def alice_client(request, raw_alice_client):
    """alice's client, stopping the sessions this test creates."""
    return _recording_client(request, raw_alice_client)


@pytest.fixture
def bob_client(request, raw_bob_client):
    """bob's client, stopping the sessions this test creates."""
    return _recording_client(request, raw_bob_client)


@pytest.fixture
def anon_client(request, raw_anon_client):
    """An unauthenticated client, stopping any session this test creates."""
    return _recording_client(request, raw_anon_client)


@pytest.fixture
def client(request, alice_client):
    """Default authenticated client (aliases alice_client).

    Provides backward compatibility with existing tests that use
    a 'client' fixture parameter.
    """
    return alice_client


def pytest_configure(config):
    """Register the markers this suite defines."""
    config.addinivalue_line(
        "markers",
        "leaves_sessions: the test intends to leave sessions behind, so the "
        "per-test clean-up is skipped (issue #174)",
        )


# ---------------------------------------------------------------------------
# Offer-generation profiling (issue #145)
# ---------------------------------------------------------------------------

# How long to wait for the database clock. psycopg is imported lazily
# inside the helpers below, so a suite that never touches the database
# does not need it installed.
PROFILING_CONNECT_TIMEOUT = 5


def profiling_enabled():
    """True unless CALYCOPIS_PROFILING is set to a false value."""
    value = os.environ.get("CALYCOPIS_PROFILING", "on").strip().lower()
    return value not in ("", "0", "off", "false", "no")


def _profiling_now():
    """Read the database clock, best effort.

    Returns a (timestamp, from_database, note) tuple. If the database is
    unreachable the client clock is used, so a broken database connection
    cannot stop the test suite.
    """
    try:
        import psycopg

        settings = dict(datasource_config())
        settings["connect_timeout"] = PROFILING_CONNECT_TIMEOUT
        with psycopg.connect(**settings) as connection:
            with connection.cursor() as cursor:
                cursor.execute("SELECT now()")
                return cursor.fetchone()[0], True, None
    except Exception as error:
        return (
            datetime.now(timezone.utc),
            False,
            f"{type(error).__name__}: {error}",
        )


class ProfilingWindow:
    """
    The profiling window covered by one test session.

    The window start is taken from the database clock where possible, so it
    is expressed in the same clock the broker uses to store the profiling
    entry timestamps. Created once at the start of the session.
    """

    def __init__(self, started_at, database_clock, note=None):
        self.started_at = started_at
        self.database_clock = database_clock
        self.note = note

    @classmethod
    def capture(cls):
        """Capture the start of the window."""
        started_at, database_clock, note = _profiling_now()
        return cls(
            started_at=started_at,
            database_clock=database_clock,
            note=note,
        )


class ProfilingReport:
    """
    A report of the offer-generation profiling entries for one session.

    Selects the profiling_entries rows created between the window start and
    the time the report was captured, and renders per-method statistics.
    Read-only and best effort: it never changes the outcome of a test run.
    """

    def __init__(self, window, completed_at, database_clock, note=None):
        self.window = window
        self.completed_at = completed_at
        self.database_clock = database_clock
        self.note = note

    @classmethod
    def capture(cls, window):
        """Capture the end of the window."""
        completed_at, database_clock, note = _profiling_now()
        return cls(
            window=window,
            completed_at=completed_at,
            database_clock=database_clock,
            note=note,
        )

    def fetch_entries(self):
        """The profiling entries created in the window, ordered by ident."""
        import psycopg

        from psycopg.rows import dict_row

        settings = dict(datasource_config())
        settings["connect_timeout"] = PROFILING_CONNECT_TIMEOUT
        sql = """
            SELECT
                ident,
                parent,
                class_name,
                method_name,
                start_time,
                end_time
            FROM
                profiling_entries
            WHERE
                start_time >= %s
              AND start_time <= %s
              AND end_time IS NOT NULL
            ORDER BY
                ident
        """
        with psycopg.connect(**settings, row_factory=dict_row) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    sql,
                    (self.window.started_at, self.completed_at),
                )
                entries = cursor.fetchall()
        for entry in entries:
            entry["duration_ms"] = (
                entry["end_time"] - entry["start_time"]
            ).total_seconds() * 1000.0
        return entries

    @staticmethod
    def summarise(entries):
        """
        Per-method statistics, keyed by (class name, method name).

        Durations are in milliseconds. The offer-generation methods are
        nested inside each other, so the groups overlap by design.
        """
        grouped = collections.defaultdict(list)
        for entry in entries:
            grouped[
                (entry["class_name"], entry["method_name"])
            ].append(entry["duration_ms"])
        summary = {}
        for key in sorted(grouped):
            values = sorted(grouped[key])
            count = len(values)
            p95_index = max(0, math.ceil(0.95 * count) - 1)
            summary[key] = {
                "count": count,
                "min_ms": values[0],
                "mean_ms": statistics.fmean(values),
                "median_ms": statistics.median(values),
                "max_ms": values[-1],
                "p95_ms": values[p95_index],
            }
        return summary

    def render(self, entries):
        """The human readable report, as a list of lines."""
        summary = self.summarise(entries)
        offersets = {entry["parent"] for entry in entries}
        if self.window.database_clock and self.database_clock:
            clock = "database"
        else:
            clock = "client"
        lines = [
            "",
            "=" * 78,
            "Calycopis offer-generation profiling",
            "Window  : "
            f"{self.window.started_at.isoformat()} .. {self.completed_at.isoformat()}"
            f" ({clock} clock)",
            f"Entries : {len(entries)} profiling entries from {len(offersets)} offer sets",
        ]
        if self.window.note:
            lines.append(
                f"Note    : window start used the client clock [{self.window.note}]"
            )
        if self.note:
            lines.append(
                f"Note    : window end used the client clock [{self.note}]"
            )
        if not entries:
            lines.append(
                "Warning : no profiling entries in this window. Check that the "
                "broker build records profiling and that the profiling_entries "
                "table exists."
            )
        else:
            lines.append("")
            lines.append(
                f"{'Method':<60} {'Count':>6} {'Min':>9} {'Mean':>9} "
                f"{'Median':>9} {'Max':>9} {'P95':>9}"
            )
            for (class_name, method_name), stats in summary.items():
                name = f"{class_name.rsplit('.', 1)[-1]}.{method_name}"
                lines.append(
                    f"{name:<60} {stats['count']:>6} {stats['min_ms']:>9.1f} "
                    f"{stats['mean_ms']:>9.1f} {stats['median_ms']:>9.1f} "
                    f"{stats['max_ms']:>9.1f} {stats['p95_ms']:>9.1f}"
                )
            lines.append("")
            lines.append(
                "Durations in milliseconds. stageTwo and generate are nested "
                "inside create."
            )
        lines.append("=" * 78)
        return lines

    def json_path(self):
        """
        The report path, or None if the JSON report is suppressed.

        CALYCOPIS_PROFILING_JSON overrides the default, which is a
        timestamped file under target/ relative to the working directory.
        The timestamp keeps the two sessions of bin/entrypoint.sh apart.
        """
        explicit = os.environ.get("CALYCOPIS_PROFILING_JSON", "").strip()
        if explicit:
            if explicit.lower() in ("off", "none", "false", "0"):
                return None
            return explicit
        stamp = self.completed_at.astimezone(timezone.utc).strftime("%Y%m%d-%H%M%S")
        return os.path.join(
            "target",
            f"profiling-report-{stamp}.json",
        )

    def write_json(self, entries):
        """Write the machine readable report, returns the path or None."""
        path = self.json_path()
        if path is None:
            return None
        summary = self.summarise(entries)
        document = {
            "window": {
                "started_at": self.window.started_at.isoformat(),
                "completed_at": self.completed_at.isoformat(),
                "database_clock": self.window.database_clock and self.database_clock,
            },
            "entries": len(entries),
            "offersets": len({entry["parent"] for entry in entries}),
            "methods": [
                {
                    "class_name": class_name,
                    "method_name": method_name,
                    **stats,
                }
                for (class_name, method_name), stats in summary.items()
            ],
            "measurements": [
                {
                    "ident": entry["ident"],
                    "offerset": str(entry["parent"]),
                    "class_name": entry["class_name"],
                    "method_name": entry["method_name"],
                    "start_time": entry["start_time"].isoformat(),
                    "end_time": entry["end_time"].isoformat(),
                    "duration_ms": entry["duration_ms"],
                }
                for entry in entries
            ],
        }
        directory = os.path.dirname(path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(document, handle, indent=2)
            handle.write("\n")
        return path


def pytest_sessionstart(session):
    """Capture the start of the offer-generation profiling window."""
    if not profiling_enabled():
        return
    session.config._calycopis_profiling_window = ProfilingWindow.capture()


def pytest_sessionfinish(session, exitstatus):
    """Remember how many tests ran, so we can skip an empty report.

    --collect-only counts as collected tests, so it is excluded here and
    never writes a report file.
    """
    if not profiling_enabled():
        return
    if session.config.option.collectonly:
        return
    session.config._calycopis_profiling_tests = getattr(
        session,
        "testscollected",
        0,
    )


def pytest_unconfigure(config):
    """Report the offer-generation profiling entries from this session.

    This runs after the pytest summary, so the report is the last thing in
    the log. It is best effort: a missing database, an old broker build
    without the profiling_entries table or an unwritable report path all
    produce a warning rather than a failure.
    """
    if not profiling_enabled():
        return
    window = getattr(config, "_calycopis_profiling_window", None)
    if window is None:
        return
    if getattr(config, "_calycopis_profiling_tests", 0) == 0:
        return
    try:
        report = ProfilingReport.capture(window)
        entries = report.fetch_entries()
        lines = report.render(entries)
        path = report.write_json(entries)
        if path is not None:
            lines.append(f"Profiling report written to [{path}]")
        for line in lines:
            print(line)
    except Exception as error:
        print(
            "[profiling] unable to generate the profiling report: "
            f"{type(error).__name__}: {error}"
        )


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
