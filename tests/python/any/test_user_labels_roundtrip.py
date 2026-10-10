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
#     "timestamp": "2026-10-10T10:06:15",
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
Tests for the user defined container labels round trip through the API.

These tests only submit an offer-set request and inspect the response. They
never accept an offer, so no lifecycle processing is triggered and they work
on either platform.

Usage:
  pytest tests/python/any/test_user_labels_roundtrip.py -v
"""

import pytest

from calycopis_openapi_client.models import (
    ExecutionRequest,
)
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
)


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

SESSION_UID_LABEL = "calycopis-broker-session-uid"

USER_LABELS = {
    "calycopis-test-alpha": "one",
    "calycopis-test-beta": "two",
}


# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _make_request(name: str, labels=None) -> ExecutionRequest:
    """Create an ExecutionRequest with the given labels."""
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=["5", "0"],
            labels=labels,
        ),
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name=f"{name}-compute"),
        ),
    )


def _message_types(response):
    """The message kinds from an OfferSetResponse."""
    if response.meta is None or response.meta.messages is None:
        return []
    return [message.kind for message in response.meta.messages]


# ===========================================================================
# Round trip tests
# ===========================================================================

class TestUserLabelsRoundTrip:
    """
    Tests that user defined labels survive the request/response round trip.
    """

    def test_labels_echoed_in_offer(self, client):
        """
        Labels supplied on the executable should be present on the
        executable in the offer.
        """
        request = _make_request("roundtrip-labels", labels=USER_LABELS)
        response = client.submit_execution(request, follow_redirect=True)

        assert response.result == "YES", (
            f"Expected YES, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        assert response.offers is not None and len(response.offers) > 0, (
            "Expected at least one offer"
        )

        offer = response.offers[0]
        assert offer.executable is not None, (
            "Offer should include the executable"
        )
        assert offer.executable.labels == USER_LABELS, (
            f"Expected labels {USER_LABELS}, got {offer.executable.labels}"
        )

    def test_no_labels_is_allowed(self, client):
        """
        A request with no labels should be accepted, and the response should
        not invent any.
        """
        request = _make_request("roundtrip-nolabels")
        response = client.submit_execution(request, follow_redirect=True)

        assert response.result == "YES", (
            f"Expected YES, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        offer = response.offers[0]
        assert not offer.executable.labels, (
            f"Expected no labels, got {offer.executable.labels}"
        )

    def test_reserved_label_is_rejected(self, client):
        """
        A label using the reserved calycopis-broker- prefix should be
        rejected.
        """
        request = _make_request(
            "roundtrip-reserved",
            labels={SESSION_UID_LABEL: "not-allowed"},
        )
        response = client.submit_execution(request, follow_redirect=True)

        assert response.result == "NO", (
            f"Expected the request to be rejected, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        assert "urn:reserved-label" in _message_types(response), (
            f"Expected an urn:reserved-label message, "
            f"got {_message_types(response)}"
        )
