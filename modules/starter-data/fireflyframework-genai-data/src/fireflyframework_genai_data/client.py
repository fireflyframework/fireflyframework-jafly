# Copyright 2026 Firefly Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Async HTTP client wrapping the Firefly Data Starter REST API."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import httpx


@dataclass
class DataStarterClient:
    """Lightweight async wrapper around the data-starter HTTP endpoints."""

    base_url: str
    timeout: float = 30.0
    _client: httpx.AsyncClient | None = field(default=None, init=False, repr=False)

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _ensure_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                timeout=self.timeout,
            )
        return self._client

    # ------------------------------------------------------------------
    # Enrichment
    # ------------------------------------------------------------------

    async def enrich(
        self,
        type: str,
        strategy: str,
        parameters: dict[str, Any],
        tenant_id: str | None = None,
    ) -> dict[str, Any]:
        """Execute a data enrichment request."""
        client = self._ensure_client()
        payload: dict[str, Any] = {
            "type": type,
            "strategy": strategy,
            "parameters": parameters,
        }
        if tenant_id is not None:
            payload["tenantId"] = tenant_id
        response = await client.post("/api/v1/enrichment", json=payload)
        response.raise_for_status()
        return response.json()

    async def preview_enrichment(
        self,
        type: str,
        strategy: str,
        parameters: dict[str, Any],
        tenant_id: str | None = None,
    ) -> dict[str, Any]:
        """Preview a data enrichment without committing changes."""
        client = self._ensure_client()
        payload: dict[str, Any] = {
            "type": type,
            "strategy": strategy,
            "parameters": parameters,
        }
        if tenant_id is not None:
            payload["tenantId"] = tenant_id
        response = await client.post("/api/v1/enrichment/preview", json=payload)
        response.raise_for_status()
        return response.json()

    # ------------------------------------------------------------------
    # Jobs
    # ------------------------------------------------------------------

    async def start_job(
        self,
        job_type: str,
        parameters: dict[str, Any],
    ) -> dict[str, Any]:
        """Start a data processing job."""
        client = self._ensure_client()
        payload = {"jobType": job_type, "parameters": parameters}
        response = await client.post("/api/v1/jobs", json=payload)
        response.raise_for_status()
        return response.json()

    async def check_job(self, execution_id: str) -> dict[str, Any]:
        """Check the status of a running job."""
        client = self._ensure_client()
        response = await client.get(f"/api/v1/jobs/{execution_id}")
        response.raise_for_status()
        return response.json()

    async def collect_results(self, execution_id: str) -> dict[str, Any]:
        """Collect the results of a completed job."""
        client = self._ensure_client()
        response = await client.get(f"/api/v1/jobs/{execution_id}/results")
        response.raise_for_status()
        return response.json()

    # ------------------------------------------------------------------
    # Providers / Operations
    # ------------------------------------------------------------------

    async def list_providers(self, type: str | None = None) -> list[dict[str, Any]]:
        """List available data providers, optionally filtered by type."""
        client = self._ensure_client()
        params: dict[str, str] = {}
        if type is not None:
            params["type"] = type
        response = await client.get("/api/v1/providers", params=params)
        response.raise_for_status()
        return response.json()

    async def execute_operation(
        self,
        type: str,
        operation_id: str,
        request: dict[str, Any],
    ) -> dict[str, Any]:
        """Execute a provider-specific data operation."""
        client = self._ensure_client()
        response = await client.post(
            f"/api/v1/operations/{type}/{operation_id}",
            json=request,
        )
        response.raise_for_status()
        return response.json()

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    async def close(self) -> None:
        """Close the underlying HTTP client."""
        if self._client is not None:
            await self._client.aclose()
            self._client = None
