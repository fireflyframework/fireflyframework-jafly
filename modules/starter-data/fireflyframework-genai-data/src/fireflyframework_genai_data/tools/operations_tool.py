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

"""GenAI tool for executing provider-specific data operations."""

from __future__ import annotations

from typing import Any

from fireflyframework_genai.tools.base import BaseTool, ParameterSpec

from fireflyframework_genai_data.client import DataStarterClient


class DataOperationsTool(BaseTool):
    """Agent tool for executing provider-specific data operations."""

    def __init__(self, client: DataStarterClient) -> None:
        self._client = client
        super().__init__(
            name="data_operations",
            description="Execute provider-specific data operations by type and operation ID",
            tags=("data", "operations"),
            parameters=[
                ParameterSpec(
                    name="type",
                    type_annotation="str",
                    description="The provider type identifier",
                    required=True,
                ),
                ParameterSpec(
                    name="operation_id",
                    type_annotation="str",
                    description="The operation identifier to execute",
                    required=True,
                ),
                ParameterSpec(
                    name="request",
                    type_annotation="dict",
                    description="Operation request payload as a dictionary",
                    required=True,
                ),
            ],
        )

    async def _execute(self, **kwargs: Any) -> Any:
        return await self._client.execute_operation(
            type=kwargs["type"],
            operation_id=kwargs["operation_id"],
            request=kwargs["request"],
        )
