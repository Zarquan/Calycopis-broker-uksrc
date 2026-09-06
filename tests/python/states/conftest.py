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
#     "timestamp": "2026-09-05T12:41:19",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Pytest fixtures for the state-transition tests in tests/python/states.

Tests in this directory verify session/component state transitions and are
allowed to access the broker database directly using a Python database client
(see AGENTS.md). The datasource settings are read from
/etc/calycopis/database.yaml, which can be overridden with the
CALYCOPIS_DATABASE_YAML environment variable. For developer setups where the
database is not reachable via the hostname in the datasource URL, the host can
be overridden with the CALYCOPIS_DB_HOST environment variable.
"""

import os
import re

import psycopg
import pytest
import yaml


DEFAULT_DATABASE_YAML = "/etc/calycopis/database.yaml"

# The broker database YAML has the form:
#   spring:
#       datasource:
#           url: jdbc:postgresql://postgres:5432/calycopis
#           username: <generated-username>
#           password: <generated-password>
#           driverClassName: org.postgresql.Driver
JDBC_URL_PATTERN = re.compile(
    r"jdbc:postgresql://(?P<host>[^:/]+):(?P<port>\d+)/(?P<dbname>\w+)"
)


def _datasource_config():
    """Read the broker datasource settings from the database YAML."""
    path = os.environ.get("CALYCOPIS_DATABASE_YAML", DEFAULT_DATABASE_YAML)
    with open(path, encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    datasource = data["spring"]["datasource"]
    url = datasource["url"]
    match = JDBC_URL_PATTERN.match(url)
    if match is None:
        raise RuntimeError(f"Unable to parse datasource url [{url}]")
    host = os.environ.get("CALYCOPIS_DB_HOST", match.group("host"))
    return {
        "host": host,
        "port": int(match.group("port")),
        "dbname": match.group("dbname"),
        "user": datasource["username"],
        "password": datasource["password"],
    }


class StateDatabase:
    """
    Read-only helpers used by the state-transition tests to verify
    session/component state transitions directly in the broker database.
    """

    def __init__(self, connection):
        self._connection = connection

    def _select_one(self, sql, parameters):
        cursor = self._connection.cursor()
        try:
            cursor.execute(sql, parameters)
            row = cursor.fetchone()
            return row[0] if row is not None else None
        finally:
            cursor.close()

    def _select_all(self, sql, parameters):
        cursor = self._connection.cursor()
        try:
            cursor.execute(sql, parameters)
            return cursor.fetchall()
        finally:
            cursor.close()

    def session_phase(self, session_uuid):
        """The stored phase of a SimpleExecutionSessionEntity row."""
        return self._select_one(
            """
            SELECT
                phase
            FROM
                simpleexecutionsessions
            WHERE
                uuid = %s
            """,
            (session_uuid,),
        )

    def expire_request_count(self, session_uuid):
        """
        The number of ExpireSessionRequest rows still queued for a session.
        A processed expire request is deleted, so this should be zero.
        """
        return self._select_one(
            """
            SELECT
                count(*)
            FROM
                processingrequests p
            JOIN
                expiresessionrequests e
            ON
                e.uuid = p.uuid
            JOIN
                sessionprocessingrequests s
            ON
                s.uuid = p.uuid
            WHERE
                s.session = %s
            """,
            (session_uuid,),
        )

    def compute_component_phases(self, session_uuid):
        """
        The stored phases of the compute component linked to a session.
        """
        rows = self._select_all(
            """
            SELECT
                lc.phase
            FROM
                lifecyclecomponents lc
            JOIN
                abstractcomputeresources ac
            ON
                ac.uuid = lc.uuid
            WHERE
                ac.session = %s
            """,
            (session_uuid,),
        )
        return [row[0] for row in rows]


@pytest.fixture(scope="session")
def database():
    """
    Session-scoped fixture that provides read-only access to the broker
    database for verifying state transitions.
    """
    connection = psycopg.connect(**_datasource_config())
    try:
        yield StateDatabase(connection)
    finally:
        connection.close()
