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

"""GenAI tool for data enrichment via the Firefly Data Starter."""

from __future__ import annotations

from typing import Any

from fireflyframework_genai.tools.base import BaseTool, ParameterSpec

from fireflyframework_genai_data.client import DataStarterClient


class DataEnrichmentTool(BaseTool):
    """Agent tool that triggers data enrichment through the data starter API."""

    def __init__(self, client: DataStarterClient) -> None:
        self._client = client
        super().__init__(
            name="data_enrichment",
            description="Enrich data records using configurable strategies (ENHANCE, MERGE, VALIDATE, etc.)",
            tags=("data", "enrichment"),
            parameters=[
                ParameterSpec(
                    name="type",
                    type_annotation="str",
                    description="The enrichment type identifier",
                    required=True,
                ),
                ParameterSpec(
                    name="strategy",
                    type_annotation="str",
                    description="Enrichment strategy to apply (e.g. ENHANCE, MERGE, VALIDATE)",
                    required=True,
                ),
                ParameterSpec(
                    name="parameters",
                    type_annotation="dict",
                    description="Strategy-specific parameters as a dictionary",
                    required=True,
                ),
                ParameterSpec(
                    name="tenant_id",
                    type_annotation="str",
                    description="Optional tenant identifier for multi-tenant isolation",
                    required=False,
                ),
            ],
        )

    async def _execute(self, **kwargs: Any) -> Any:
        return await self._client.enrich(
            type=kwargs["type"],
            strategy=kwargs["strategy"],
            parameters=kwargs["parameters"],
            tenant_id=kwargs.get("tenant_id"),
        )
