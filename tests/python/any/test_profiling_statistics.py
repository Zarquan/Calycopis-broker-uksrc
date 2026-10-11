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
#     "timestamp": "2026-10-11T11:49:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Unit tests for the offer-generation profiling report.

These exercise the statistics, rendering and JSON output of ProfilingReport
with synthetic entries, so they need neither a broker nor a database and run
with either platform.
"""

import json
import os

from datetime import datetime, timedelta, timezone

from calycopis_conftest import ProfilingReport, ProfilingWindow

# A fixed window, so the expected output does not depend on the wall clock.
START = datetime(2026, 10, 11, 12, 0, 0, tzinfo=timezone.utc)

OFFERSET_A = "00000000-0000-0000-0000-00000000000a"
OFFERSET_B = "00000000-0000-0000-0000-00000000000b"


def _window():
    """A ProfilingWindow anchored on the database clock."""
    return ProfilingWindow(
        started_at=START,
        database_clock=True,
    )


def _report():
    """A ProfilingReport covering the sixty seconds after START."""
    return ProfilingReport(
        window=_window(),
        completed_at=START + timedelta(seconds=60),
        database_clock=True,
    )


def _entry(ident, parent, class_name, method_name, duration_ms):
    """A synthetic profiling entry, shaped like a profiling_entries row."""
    return {
        "ident": ident,
        "parent": parent,
        "class_name": class_name,
        "method_name": method_name,
        "start_time": START,
        "end_time": START + timedelta(milliseconds=duration_ms),
        "duration_ms": float(duration_ms),
    }


def test_summarise_groups_by_class_and_method():
    """Entries are grouped by class and method, with the right counts."""
    entries = [
        _entry(1, OFFERSET_A, "net.example.ClassA", "create", 10.0),
        _entry(2, OFFERSET_A, "net.example.ClassA", "stageTwo", 4.0),
        _entry(3, OFFERSET_B, "net.example.ClassA", "create", 20.0),
    ]

    summary = ProfilingReport.summarise(entries)

    assert set(summary) == {
        ("net.example.ClassA", "create"),
        ("net.example.ClassA", "stageTwo"),
    }
    create = summary[("net.example.ClassA", "create")]
    assert create["count"] == 2
    assert create["min_ms"] == 10.0
    assert create["max_ms"] == 20.0
    assert summary[("net.example.ClassA", "stageTwo")]["count"] == 1


def test_summarise_median_and_p95():
    """The median, mean and p95 are computed from the sorted durations."""
    entries = [
        _entry(index + 1, OFFERSET_A, "net.example.ClassA", "create", value)
        for index, value in enumerate([1.0, 2.0, 3.0, 4.0, 100.0])
    ]

    stats = ProfilingReport.summarise(entries)[("net.example.ClassA", "create")]

    assert stats["median_ms"] == 3.0
    assert stats["mean_ms"] == 22.0
    assert stats["p95_ms"] == 100.0

    single = ProfilingReport.summarise(
        [_entry(1, OFFERSET_A, "net.example.ClassA", "create", 7.5)]
    )[("net.example.ClassA", "create")]

    assert single["p95_ms"] == 7.5
    assert single["min_ms"] == 7.5
    assert single["max_ms"] == 7.5


def test_render_without_entries_warns():
    """An empty window still renders, with a warning rather than an error."""
    text = "\n".join(_report().render([]))

    assert "Entries : 0 profiling entries from 0 offer sets" in text
    assert "no profiling entries in this window" in text


def test_render_uses_simple_class_names():
    """The table uses the simple class name, not the fully qualified one."""
    entries = [
        _entry(1, OFFERSET_A, "net.example.SomeClass", "create", 12.5),
    ]

    text = "\n".join(_report().render(entries))

    assert "SomeClass.create" in text
    assert "net.example.SomeClass" not in text


def test_json_path_default_is_timestamped(monkeypatch):
    """The default JSON path is a timestamped file under target/."""
    monkeypatch.delenv("CALYCOPIS_PROFILING_JSON", raising=False)

    path = _report().json_path()

    assert path is not None
    assert path.startswith(os.path.join("target", "profiling-report-"))
    assert path.endswith(".json")


def test_json_path_can_be_suppressed(monkeypatch):
    """CALYCOPIS_PROFILING_JSON=off suppresses the JSON report."""
    monkeypatch.setenv("CALYCOPIS_PROFILING_JSON", "off")

    assert _report().json_path() is None


def test_json_path_can_be_overridden(monkeypatch):
    """CALYCOPIS_PROFILING_JSON replaces the default path."""
    monkeypatch.setenv("CALYCOPIS_PROFILING_JSON", "/tmp/calycopis-profiling.json")

    assert _report().json_path() == "/tmp/calycopis-profiling.json"


def test_write_json_round_trip(tmp_path, monkeypatch):
    """The JSON report holds the window, aggregates and measurements."""
    path = tmp_path / "profiling-report.json"
    monkeypatch.setenv("CALYCOPIS_PROFILING_JSON", str(path))
    entries = [
        _entry(1, OFFERSET_A, "net.example.ClassA", "create", 10.0),
    ]

    written = _report().write_json(entries)

    assert written == str(path)
    with open(path, encoding="utf-8") as handle:
        document = json.load(handle)
    assert document["window"]["database_clock"] is True
    assert document["entries"] == 1
    assert document["offersets"] == 1
    assert document["methods"] == [
        {
            "class_name": "net.example.ClassA",
            "method_name": "create",
            "count": 1,
            "min_ms": 10.0,
            "mean_ms": 10.0,
            "median_ms": 10.0,
            "max_ms": 10.0,
            "p95_ms": 10.0,
        }
    ]
    assert document["measurements"][0]["offerset"] == OFFERSET_A
    assert document["measurements"][0]["duration_ms"] == 10.0
