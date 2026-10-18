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

"""Tests for DataJobTool."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from fireflyframework_genai_data.tools.job_tool import DataJobTool


@pytest.fixture()
def mock_client() -> MagicMock:
    client = MagicMock()
    client.start_job = AsyncMock(return_value={"executionId": "exec-123", "status": "STARTED"})
    client.check_job = AsyncMock(return_value={"executionId": "exec-123", "status": "RUNNING"})
    client.collect_results = AsyncMock(return_value={"executionId": "exec-123", "records": [1, 2, 3]})
    return client


@pytest.fixture()
def tool(mock_client: MagicMock) -> DataJobTool:
    return DataJobTool(client=mock_client)


class TestDataJobTool:
    """DataJobTool unit tests."""

    def test_tool_metadata(self, tool: DataJobTool) -> None:
        assert tool.name == "data_job"
        assert "jobs" in tool.tags

    def test_parameter_specs(self, tool: DataJobTool) -> None:
        param_names = [p.name for p in tool.parameters]
        assert "action" in param_names
        assert "job_type" in param_names
        assert "execution_id" in param_names
        assert "parameters" in param_names

        action_param = next(p for p in tool.parameters if p.name == "action")
        assert action_param.required is True

        job_type_param = next(p for p in tool.parameters if p.name == "job_type")
        assert job_type_param.required is False

    @pytest.mark.asyncio()
    async def test_start_action(
        self, tool: DataJobTool, mock_client: MagicMock
    ) -> None:
        result = await tool.execute(
            action="start",
            job_type="BATCH_ENRICHMENT",
            parameters={"batchSize": 100},
        )

        mock_client.start_job.assert_awaited_once_with(
            job_type="BATCH_ENRICHMENT",
            parameters={"batchSize": 100},
        )
        assert result["executionId"] == "exec-123"

    @pytest.mark.asyncio()
    async def test_check_action(
        self, tool: DataJobTool, mock_client: MagicMock
    ) -> None:
        result = await tool.execute(action="check", execution_id="exec-123")

        mock_client.check_job.assert_awaited_once_with(execution_id="exec-123")
        assert result["status"] == "RUNNING"

    @pytest.mark.asyncio()
    async def test_collect_action(
        self, tool: DataJobTool, mock_client: MagicMock
    ) -> None:
        result = await tool.execute(action="collect", execution_id="exec-123")

        mock_client.collect_results.assert_awaited_once_with(execution_id="exec-123")
        assert result["records"] == [1, 2, 3]

    @pytest.mark.asyncio()
    async def test_unknown_action_raises(self, tool: DataJobTool) -> None:
        with pytest.raises(ValueError, match="Unknown action 'explode'"):
            await tool.execute(action="explode")

    @pytest.mark.asyncio()
    async def test_start_without_job_type_raises(self, tool: DataJobTool) -> None:
        with pytest.raises(ValueError, match="'job_type' is required"):
            await tool.execute(action="start")

    @pytest.mark.asyncio()
    async def test_check_without_execution_id_raises(self, tool: DataJobTool) -> None:
        with pytest.raises(ValueError, match="'execution_id' is required"):
            await tool.execute(action="check")

    @pytest.mark.asyncio()
    async def test_collect_without_execution_id_raises(self, tool: DataJobTool) -> None:
        with pytest.raises(ValueError, match="'execution_id' is required"):
            await tool.execute(action="collect")
