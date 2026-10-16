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
#     },
#     {
#     "timestamp": "2026-09-14T11:11:41",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 20,
#       "units": "%"
#       }
#     },
#     {
#     "interval": "2026-10-15T06:20:00/2026-10-15T07:50:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 15,
#       "units": "%"
#       }
#     },
#     {
#     "interval": "2026-10-16T04:45:00/2026-10-16T05:35:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 5,
#       "units": "%"
#       }
#     },
#     {
#     "interval": "2026-10-16T14:40:00/2026-10-16T16:30:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 20,
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
/etc/calycopis/database.yaml by the shared conftest.py (see
``datasource_config``), which can be overridden with the
CALYCOPIS_DATABASE_YAML and CALYCOPIS_DB_HOST environment variables.
"""

import psycopg
import pytest

from calycopis_conftest import LIVE_SESSION_PHASES, datasource_config


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

    def max_reserved_cores_per_block(self, block_step_seconds=60):
        """
        The largest number of CPU cores reserved by live sessions in any one
        block of time.

        The reservation is the hull of the preparing, available and releasing
        windows, which is what the booking query reserves (see issue #158), and
        it uses the same block rounding as that query: the start of the hull is
        rounded down to a block boundary and its end is rounded up, so a hull
        that does not sit on block boundaries still counts against every block
        it touches (see issue #154). The live phases are the same set the
        booking query counts, taken from the shared definition in the top level
        conftest.
        """
        phases = ", ".join(f"'{phase}'" for phase in LIVE_SESSION_PHASES)
        return self._select_one(
            f"""
            SELECT
                COALESCE(max(used), 0)
            FROM (
                SELECT
                    sum(cores) AS used
                FROM (
                    SELECT
                        generate_series(
                            (
                                least(
                                    lc.prepare_start_instant_seconds,
                                    lc.available_start_instant_seconds,
                                    lc.release_start_instant_seconds
                                    ) / %s
                            ),
                            (
                                (
                                    greatest(
                                        lc.prepare_start_instant_seconds
                                        + lc.prepare_duration_seconds,
                                        lc.available_start_instant_seconds
                                        + lc.available_start_duration_seconds
                                        + lc.available_duration_seconds,
                                        lc.release_start_instant_seconds
                                        + lc.release_duration_seconds
                                        )
                                    + (%s - 1)
                                ) / %s
                            ) - 1
                            ) AS block,
                        COALESCE(
                            sc.maxofferedcores,
                            sc.maxrequestedcores
                            ) AS cores
                    FROM
                        lifecyclecomponents lc
                    JOIN
                        abstractcomputeresources ac
                    ON
                        ac.uuid = lc.uuid
                    JOIN
                        simplecomputeresources sc
                    ON
                        sc.uuid = lc.uuid
                    JOIN
                        simpleexecutionsessions s
                    ON
                        s.uuid = ac.session
                    WHERE
                        s.phase IN ({phases})
                    ) AS windows
                GROUP BY
                    block
                ) AS per_block
            """,
            (
                block_step_seconds,
                block_step_seconds,
                block_step_seconds,
            ),
        )


@pytest.fixture(scope="session")
def database():
    """
    Session-scoped fixture that provides read-only access to the broker
    database for verifying state transitions.
    """
    connection = psycopg.connect(**datasource_config())
    try:
        yield StateDatabase(connection)
    finally:
        connection.close()