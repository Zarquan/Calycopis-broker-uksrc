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
#     "timestamp": "2026-09-24T14:32:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.5-rc.3",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 90,
#       "units": "%"
#       }
#     }
#   ]
#
"""Extract container output via the Docker session connectors.

The broker advertises two ``SimpleSessionConnector`` entries on every session
that executes a Docker compute resource:

  - docker-container-stdout-get  -> GET /sessions/{uuid}/docker/stdout-get
  - docker-container-stderr-get  -> GET /sessions/{uuid}/docker/stderr-get

The connectors start in the PREPARING state (no location), become AVAILABLE
(with an HTTP GET location) once the container logs are captured, and become
FINISHED when execution completes.  The connector locations remain valid after
completion, so the captured output is read from the session API rather than
from broker log files.
"""

import base64
import urllib.error
import urllib.request
from uuid import UUID

from broker_tools.client import get_brokers, get_env, make_client

STDOUT_CONNECTOR_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stdout-get.yaml"
STDERR_CONNECTOR_KIND = "https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/executable/docker-container-stderr-get.yaml"


def get_session_connectors(broker: str, session_uuid: str) -> list[dict]:
    """Fetch a session and return its connectors as plain dicts.

    Each entry has ``kind``, ``status`` (PREPARING/AVAILABLE/FINISHED),
    ``protocol`` and ``location`` fields.
    """
    brokers = get_brokers()
    if broker not in brokers:
        raise RuntimeError(f"Unknown broker: {broker}")

    client = make_client(brokers[broker])
    session = client.get_session(UUID(session_uuid))
    connectors = getattr(session, "connectors", None) or []
    return [
        {
            "kind": getattr(connector, "kind", None),
            "status": getattr(connector, "status", None),
            "protocol": getattr(connector, "protocol", None),
            "location": getattr(connector, "location", None),
        }
        for connector in connectors
    ]


def _connector_location(connectors: list[dict], kind: str) -> str | None:
    """Return the location of the connector with the given kind URI."""
    for connector in connectors:
        if connector.get("kind") == kind:
            return connector.get("location")
    return None


def _resolve_location(broker: str, location: str) -> str:
    """Resolve a connector location against the broker base URL when relative."""
    if location.startswith("http://") or location.startswith("https://"):
        return location
    base = get_brokers().get(broker, "")
    return base.rstrip("/") + "/" + location.lstrip("/")


def _fetch_connector(broker: str, location: str) -> str:
    """GET a connector location with the demo credentials."""
    username, password = get_env()
    request = urllib.request.Request(_resolve_location(broker, location), method="GET")
    request.add_header("Accept", "text/plain")
    creds = base64.b64encode(f"{username}:{password}".encode()).decode()
    request.add_header("Authorization", f"Basic {creds}")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return ""
        raise


def get_container_output(broker: str, session_uuid: str) -> dict:
    """Return captured container output via the session connectors.

    Returns ``{"stdout": ..., "stderr": ...}``.  A stream is ``None`` when the
    session does not (yet) advertise the matching connector with a location
    (e.g. the connector is still in the PREPARING state).
    """
    connectors = get_session_connectors(broker, session_uuid)
    output = {}
    for key, kind in (
        ("stdout", STDOUT_CONNECTOR_KIND),
        ("stderr", STDERR_CONNECTOR_KIND),
    ):
        location = _connector_location(connectors, kind)
        if not location:
            output[key] = None
            continue
        output[key] = _fetch_connector(broker, location)
    return output


def get_container_stdout(broker: str, session_uuid: str) -> str | None:
    """Backwards-compatible wrapper returning container stdout only."""
    return get_container_output(broker, session_uuid).get("stdout")