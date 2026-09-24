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
#     "timestamp": "2026-09-24T14:34:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.5-rc.3",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 50,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-09-24T15:05:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.5-rc.3",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 10,
#       "units": "%"
#       }
#     }
#   ]
#
"""Resolve Docker image digests for broker submissions.

Digests are resolved through the broker API: a minimal request is submitted
with a deliberately wrong digest so the broker reports the cached local
digest in its response messages.  Results are cached in
``run/image-digests.json``.  Broker log files are not used, matching the
volume-based deployment where logs are not available on the host.

The demo tools use a pinned image (``alpine:3.23``) with a known digest, so
submissions carry an explicit digest and do not depend on the broker probe.
"""

from pathlib import Path

from calycopis_schema_client.models import ComponentMetadata, DockerImageSpec, ExecutionRequest
from calycopis_schema_client.wrappers import DockerContainer

from broker_tools.client import get_brokers, make_client
from broker_tools.state import DIGEST_CACHE_PATH, load_digest_cache, save_digest_cache


# Pinned demo image: a specific Alpine release with a known digest, so that
# submissions do not depend on a moving tag.  The broker checks the requested
# digest against the image in its local Docker cache; alpine:3.23 is
# available with this digest on the demo deployment.
DEMO_IMAGE = "alpine:3.23"
DEMO_IMAGE_DIGEST = "sha256:85fe1e81d6758c208f3e1eed4338a1997e19d4be002d4dd32d3100c9a8c010a0"

# Digests that are known without querying the broker.  The broker probe can
# only surface a digest for images already present in its local cache, so
# pinned demo images are listed here as a fallback.
KNOWN_DIGESTS = {
    DEMO_IMAGE: DEMO_IMAGE_DIGEST,
}


def _probe_digest_via_submit(image: str, broker: str) -> str | None:
    """Submit a minimal request to surface a digest-mismatch message."""
    brokers = get_brokers()
    if broker not in brokers:
        raise RuntimeError(f"Unknown broker: {broker}")

    request = ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name="digest-probe"),
            image=DockerImageSpec(
                locations=[image],
                digest="sha256:0000000000000000000000000000000000000000000000000000000000000000",
            ),
            command=["echo", "probe"],
        ),
    )
    client = make_client(brokers[broker])
    response = client.submit_execution(request, follow_redirect=True)

    if response.meta and response.meta.messages:
        for message in response.meta.messages:
            template = getattr(message, "template", "") or ""
            values = getattr(message, "values", None)
            if "digest does not match" in template and values:
                for value in values:
                    if isinstance(value, str) and value.startswith("sha256:"):
                        return value

    return None


def resolve_digest(image: str, broker: str = "alpha", cache_path: Path = DIGEST_CACHE_PATH) -> str:
    """Resolve the digest for an image tag cached on the brokers."""
    cache = load_digest_cache(cache_path)
    if image in cache:
        return cache[image]

    if image in KNOWN_DIGESTS:
        digest = KNOWN_DIGESTS[image]
        cache[image] = digest
        save_digest_cache(cache_path, cache)
        return digest

    digest = _probe_digest_via_submit(image, broker)

    if not digest:
        raise RuntimeError(
            f"Could not resolve digest for image [{image}]. "
            f"Ensure brokers are deployed and the image is cached."
        )

    cache[image] = digest
    save_digest_cache(cache_path, cache)
    return digest