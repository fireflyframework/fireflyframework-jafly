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

"""Tests for EnrichmentStep."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from fireflyframework_genai_data.steps.enrichment_step import EnrichmentStep


@pytest.fixture()
def mock_client() -> MagicMock:
    client = MagicMock()
    client.enrich = AsyncMock(return_value={"status": "enriched", "count": 10})
    return client


@pytest.fixture()
def mock_context() -> MagicMock:
    ctx = MagicMock()
    ctx.metadata = {}
    ctx.correlation_id = "corr-abc-123"
    return ctx


@pytest.fixture()
def step(mock_client: MagicMock) -> EnrichmentStep:
    return EnrichmentStep(
        client=mock_client,
        enrichment_type="ADDRESS",
        strategy="ENHANCE",
    )


class TestEnrichmentStep:
    """EnrichmentStep unit tests."""

    @pytest.mark.asyncio()
    async def test_execute_calls_client(
        self, step: EnrichmentStep, mock_client: MagicMock, mock_context: MagicMock
    ) -> None:
        result = await step.execute(
            context=mock_context,
            inputs={"country": "US", "zip": "90210"},
        )

        mock_client.enrich.assert_awaited_once_with(
            type="ADDRESS",
            strategy="ENHANCE",
            parameters={"country": "US", "zip": "90210"},
            tenant_id=None,
        )
        assert result == {"status": "enriched", "count": 10}

    @pytest.mark.asyncio()
    async def test_execute_extracts_tenant_id(
        self, step: EnrichmentStep, mock_client: MagicMock, mock_context: MagicMock
    ) -> None:
        await step.execute(
            context=mock_context,
            inputs={"country": "US", "tenant_id": "tenant-xyz"},
        )

        mock_client.enrich.assert_awaited_once_with(
            type="ADDRESS",
            strategy="ENHANCE",
            parameters={"country": "US"},
            tenant_id="tenant-xyz",
        )

    @pytest.mark.asyncio()
    async def test_execute_records_metadata(
        self, step: EnrichmentStep, mock_context: MagicMock
    ) -> None:
        await step.execute(context=mock_context, inputs={"key": "value"})

        assert "enrichment_results" in mock_context.metadata
        records = mock_context.metadata["enrichment_results"]
        assert len(records) == 1
        assert records[0]["type"] == "ADDRESS"
        assert records[0]["strategy"] == "ENHANCE"
        assert records[0]["correlation_id"] == "corr-abc-123"

    @pytest.mark.asyncio()
    async def test_default_strategy_is_enhance(self, mock_client: MagicMock) -> None:
        default_step = EnrichmentStep(client=mock_client, enrichment_type="EMAIL")
        assert default_step._strategy == "ENHANCE"

    @pytest.mark.asyncio()
    async def test_does_not_mutate_original_inputs(
        self, step: EnrichmentStep, mock_context: MagicMock
    ) -> None:
        original = {"country": "US", "tenant_id": "t1"}
        await step.execute(context=mock_context, inputs=original)

        # Original dict should still have tenant_id (step works on a copy)
        assert "tenant_id" in original
