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
#     "timestamp": "2026-11-20T14:52:19",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-v4.1-flash-1017",
#     "contribution": {
#       "value": 10,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Unit tests for the shared phase-wait helpers.

These use a fake client, so they need neither a broker nor a database. They
lock down the two rules the integration tests rely on:

  * AVAILABLE, RUNNING, RELEASING and COMPLETED all count as having reached
    AVAILABLE, because AVAILABLE can be transient.
  * A terminal phase before AVAILABLE is returned, not hidden behind a
    timeout, so the caller can report the real outcome.
"""

import uuid

import pytest

from calycopis_openapi_client.models import SimpleExecutionSessionPhase

from calycopis_conftest import (
    wait_for_available,
    wait_for_terminal_after_available,
)


SESSION_UUID = uuid.UUID("00000000-0000-0000-0000-000000000001")


class _Session:
    """The session fields the helpers read."""

    def __init__(self, phase):
        self.phase = phase


class _FakeClient:
    """A client that returns a fixed sequence of phases."""

    def __init__(self, phases):
        self._phases = list(phases)
        self._polls = 0
        self.terminal_calls = []

    def get_session(self, session_uuid):
        index = min(self._polls, len(self._phases) - 1)
        self._polls += 1
        return _Session(self._phases[index])

    def wait_for_phase(
        self,
        session_uuid,
        target_phases,
        timeout=300.0,
        interval=2.0,
    ):
        self.terminal_calls.append(
            (set(target_phases), timeout, interval)
        )
        return _Session(SimpleExecutionSessionPhase.COMPLETED)


@pytest.mark.parametrize(
    "phase",
    [
        SimpleExecutionSessionPhase.AVAILABLE,
        SimpleExecutionSessionPhase.RUNNING,
        SimpleExecutionSessionPhase.RELEASING,
        SimpleExecutionSessionPhase.COMPLETED,
    ],
)
def test_wait_for_available_accepts_available_or_later(phase):
    """AVAILABLE or any later phase stops the availability wait."""
    client = _FakeClient([phase])

    session = wait_for_available(
        client,
        SESSION_UUID,
        timeout=1.0,
        interval=0.01,
    )

    assert session.phase == phase
    assert client._polls == 1


def test_wait_for_available_polls_until_available():
    """A session still preparing is polled until AVAILABLE is seen."""
    client = _FakeClient(
        [
            SimpleExecutionSessionPhase.ACCEPTED,
            SimpleExecutionSessionPhase.PREPARING,
            SimpleExecutionSessionPhase.AVAILABLE,
        ]
    )

    session = wait_for_available(
        client,
        SESSION_UUID,
        timeout=1.0,
        interval=0.01,
    )

    assert session.phase == SimpleExecutionSessionPhase.AVAILABLE
    assert client._polls == 3


def test_wait_for_available_returns_terminal_phase():
    """A terminal phase before AVAILABLE is returned to the caller."""
    client = _FakeClient(
        [
            SimpleExecutionSessionPhase.ACCEPTED,
            SimpleExecutionSessionPhase.EXPIRED,
        ]
    )

    session = wait_for_available(
        client,
        SESSION_UUID,
        timeout=1.0,
        interval=0.01,
    )

    assert session.phase == SimpleExecutionSessionPhase.EXPIRED
    assert client._polls == 2


def test_wait_for_available_times_out_before_available():
    """A session that never leaves ACCEPTED raises a clear timeout."""
    client = _FakeClient([SimpleExecutionSessionPhase.ACCEPTED])

    with pytest.raises(TimeoutError, match=r"last phase \[ACCEPTED\]"):
        wait_for_available(
            client,
            SESSION_UUID,
            timeout=0.0,
            interval=0.01,
        )


def test_wait_for_terminal_after_available_uses_completion_timeout():
    """The completion wait starts after AVAILABLE, with its own timeout."""
    client = _FakeClient(
        [
            SimpleExecutionSessionPhase.ACCEPTED,
            SimpleExecutionSessionPhase.AVAILABLE,
        ]
    )

    session = wait_for_terminal_after_available(
        client,
        SESSION_UUID,
        timeout=42.0,
        interval=0.01,
        available_timeout=1.0,
    )

    assert session.phase == SimpleExecutionSessionPhase.COMPLETED
    assert len(client.terminal_calls) == 1
    targets, timeout, interval = client.terminal_calls[0]
    assert timeout == 42.0
    assert interval == 0.01
    assert SimpleExecutionSessionPhase.COMPLETED in targets
    assert SimpleExecutionSessionPhase.EXPIRED in targets


def test_wait_for_terminal_after_available_returns_terminal():
    """A session already terminal is returned without a completion wait."""
    client = _FakeClient([SimpleExecutionSessionPhase.FAILED])

    session = wait_for_terminal_after_available(
        client,
        SESSION_UUID,
        timeout=42.0,
        interval=0.01,
    )

    assert session.phase == SimpleExecutionSessionPhase.FAILED
    assert client.terminal_calls == []
