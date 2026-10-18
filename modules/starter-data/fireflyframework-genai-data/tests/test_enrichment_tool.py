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

"""Tests for DataEnrichmentTool."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from fireflyframework_genai_data.tools.enrichment_tool import DataEnrichmentTool


@pytest.fixture()
def mock_client() -> MagicMock:
    client = MagicMock()
    client.enrich = AsyncMock(return_value={"status": "enriched", "records": 42})
    return client


@pytest.fixture()
def tool(mock_client: MagicMock) -> DataEnrichmentTool:
    return DataEnrichmentTool(client=mock_client)


class TestDataEnrichmentTool:
    """DataEnrichmentTool unit tests."""

    def test_tool_metadata(self, tool: DataEnrichmentTool) -> None:
        assert tool.name == "data_enrichment"
        assert "enrichment" in tool.description.lower()
        assert "data" in tool.tags

    def test_parameter_specs(self, tool: DataEnrichmentTool) -> None:
        param_names = [p.name for p in tool.parameters]
        assert "type" in param_names
        assert "strategy" in param_names
        assert "parameters" in param_names
        assert "tenant_id" in param_names

        # Verify type_annotation is a string, not a type object
        for param in tool.parameters:
            assert isinstance(param.type_annotation, str)

    def test_tenant_id_is_optional(self, tool: DataEnrichmentTool) -> None:
        tenant_param = next(p for p in tool.parameters if p.name == "tenant_id")
        assert tenant_param.required is False

    @pytest.mark.asyncio()
    async def test_execute_calls_client_enrich(
        self, tool: DataEnrichmentTool, mock_client: MagicMock
    ) -> None:
        result = await tool.execute(
            type="ADDRESS",
            strategy="ENHANCE",
            parameters={"country": "US"},
        )

        mock_client.enrich.assert_awaited_once_with(
            type="ADDRESS",
            strategy="ENHANCE",
            parameters={"country": "US"},
            tenant_id=None,
        )
        assert result == {"status": "enriched", "records": 42}

    @pytest.mark.asyncio()
    async def test_execute_with_tenant_id(
        self, tool: DataEnrichmentTool, mock_client: MagicMock
    ) -> None:
        await tool.execute(
            type="EMAIL",
            strategy="VALIDATE",
            parameters={"domain": "example.com"},
            tenant_id="tenant-abc",
        )

        mock_client.enrich.assert_awaited_once_with(
            type="EMAIL",
            strategy="VALIDATE",
            parameters={"domain": "example.com"},
            tenant_id="tenant-abc",
        )
