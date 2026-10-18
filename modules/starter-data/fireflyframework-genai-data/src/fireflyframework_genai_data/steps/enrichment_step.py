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

"""Pipeline step that performs data enrichment via the data-starter API."""

from __future__ import annotations

from typing import Any

from fireflyframework_genai.pipeline.context import PipelineContext

from fireflyframework_genai_data.client import DataStarterClient


class EnrichmentStep:
    """A :class:`StepExecutor`-compatible pipeline step for data enrichment.

    Parameters
    ----------
    client:
        Pre-configured :class:`DataStarterClient`.
    enrichment_type:
        The enrichment type identifier forwarded to the API.
    strategy:
        Enrichment strategy name (default ``"ENHANCE"``).
    """

    def __init__(
        self,
        client: DataStarterClient,
        enrichment_type: str,
        strategy: str = "ENHANCE",
    ) -> None:
        self._client = client
        self._enrichment_type = enrichment_type
        self._strategy = strategy

    async def execute(
        self,
        context: PipelineContext,
        inputs: dict[str, Any],
    ) -> Any:
        """Run the enrichment and return the API response.

        The *inputs* dict is forwarded as the ``parameters`` payload.  An
        optional ``tenant_id`` key in *inputs* is extracted and sent
        separately.
        """
        parameters = dict(inputs)
        tenant_id = parameters.pop("tenant_id", None)

        result = await self._client.enrich(
            type=self._enrichment_type,
            strategy=self._strategy,
            parameters=parameters,
            tenant_id=tenant_id,
        )

        # Store enrichment metadata on the pipeline context for downstream
        # steps that may need it.
        context.metadata.setdefault("enrichment_results", []).append(
            {
                "type": self._enrichment_type,
                "strategy": self._strategy,
                "correlation_id": context.correlation_id,
            }
        )

        return result
