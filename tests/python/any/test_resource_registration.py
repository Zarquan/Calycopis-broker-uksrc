#
# AIMetrics: [
#     {
#     "timestamp": "2026-06-02T13:37:00",
#     "name": "Cursor CLI",
#     "version": "2026.02.13-41ac335",
#     "model": "Claude 4.6 Opus (Thinking)",
#     "contribution": {
#       "value": 8,
#       "units": "%"
#       }
#     },
#     {
#     "timestamp": "2026-09-14T11:11:41",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.1.1-rc.2",
#     "model": "deepseek-v4-flash",
#     "contribution": {
#       "value": 1,
#       "units": "%"
#       }
#     },
#     {
#     "interval": "2026-10-16T06:05:00/2026-10-16T06:45:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-flash",
#     "contribution": {
#       "value": 15,
#       "units": "%"
#       }
#     }
#   ]
#

"""
Resource registration tests that work on either platform.

These tests never list a storage resource, so they work on the docker and mock
platforms alike. The tests that do list one, and cross-reference data resources
to it by name, need a platform that supports the simple storage resource and
live in tests/python/mock/test_storage_registration.py (see issue #147).

They verify that cross-referencing resources (data <-> storage) are handled by
the two-step validation process:
  Step 1: Register all resources and assign UUIDs.
  Step 2: Validate all resources, resolving cross-references.

The tests submit ExecutionRequests to a running Calycopis broker service
and verify the responses.

Requires:
  - A running Calycopis broker service (default: http://calycopis-dev:8082, overridable via CALYCOPIS_URL)
  - The calycopis_schema_client Python package installed

Usage:
  pytest tests/python/any/test_resource_registration.py -v
  CALYCOPIS_URL=http://host:port pytest tests/python/any/test_resource_registration.py -v
"""

from calycopis_openapi_client.models import (
    ExecutionRequest,
    OfferSetResponse,
)
from calycopis_openapi_client.models.docker_image_spec import DockerImageSpec
from calycopis_openapi_client.models.component_metadata import ComponentMetadata
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleDataResource,
)



def _make_executable(name: str = "test-container") -> DockerContainer:
    """Helper: create a minimal DockerContainer executable."""
    return DockerContainer(
        meta=ComponentMetadata(name=name),
        image=DockerImageSpec(
            locations=["ghcr.io/ivoa/oligia-webtop:ubuntu-2022.01.13"],
            digest="sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
        ),
    )


# ---------------------------------------------------------------------------
# Helper to submit and return the OfferSetResponse
# ---------------------------------------------------------------------------

def _submit(client, request: ExecutionRequest) -> OfferSetResponse:
    """Submit a request and return the OfferSetResponse."""
    response = client.submit_execution(request, follow_redirect=True)
    assert isinstance(response, OfferSetResponse), (
        f"Expected OfferSetResponse, got {type(response)}"
    )
    return response


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------


class TestBaseline:
    """Baseline tests: executable only, no explicit data/storage."""

    def test_executable_only(self, client):
        """
        A request with just an executable should succeed.
        The server creates a default compute resource automatically.
        """
        request = ExecutionRequest(
            executable=_make_executable("baseline-exec-only"),
        )
        response = _submit(client, request)

        assert response.result == "YES", (
            f"Expected YES, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        assert response.offers is not None
        assert len(response.offers) > 0


class TestDataWithDefaultStorage:
    """Data resources without explicit storage → server creates default storage."""

    def test_single_data_no_storage(self, client):
        """
        A data resource with no storage reference should get default storage
        auto-created by the server.
        """
        request = ExecutionRequest(
            executable=_make_executable("data-default-storage"),
            data=[
                SimpleDataResource(
                    meta=ComponentMetadata(name="my-data"),
                    location="https://example.org/data/test-file.fits",
                ),
            ],
        )
        response = _submit(client, request)

        assert response.result == "YES", (
            f"Expected YES, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        assert response.offers is not None
        assert len(response.offers) > 0

        # Each offer should have both data and storage resources.
        offer = response.offers[0]
        assert offer.data is not None, "Offer should contain data resources"
        assert len(offer.data) > 0
        assert offer.storage is not None, "Offer should contain auto-created storage"
        assert len(offer.storage) > 0

    def test_multiple_data_no_storage(self, client):
        """
        Multiple data resources with no storage reference should each get
        their own default storage.
        """
        request = ExecutionRequest(
            executable=_make_executable("multi-data-default-storage"),
            data=[
                SimpleDataResource(
                    meta=ComponentMetadata(name="data-alpha"),
                    location="https://example.org/data/alpha.fits",
                ),
                SimpleDataResource(
                    meta=ComponentMetadata(name="data-beta"),
                    location="https://example.org/data/beta.fits",
                ),
            ],
        )
        response = _submit(client, request)

        assert response.result == "YES", (
            f"Expected YES, got {response.result}. "
            f"Messages: {response.meta.messages if response.meta else 'none'}"
        )
        offer = response.offers[0]
        assert offer.data is not None
        assert len(offer.data) == 2, "Should have two data resources"
        assert offer.storage is not None
        assert len(offer.storage) >= 2, (
            f"Should have at least two storage resources (one per data), got {len(offer.storage)}"
        )
