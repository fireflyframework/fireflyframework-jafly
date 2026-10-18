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

"""Agent middleware that tracks data lineage per agent run."""

from __future__ import annotations

import time
import uuid
from typing import Any

from fireflyframework_genai.agents.middleware import MiddlewareContext


class DataLineageMiddleware:
    """An :class:`AgentMiddleware`-compatible middleware that captures lineage.

    Before every agent run a unique ``lineage_id`` is generated and attached
    to the :pyattr:`MiddlewareContext.metadata`.  After the run completes the
    middleware records the elapsed time and result summary so callers can
    reconstruct the full data lineage graph.

    The accumulated lineage records are available via the :attr:`records`
    property.
    """

    def __init__(self) -> None:
        self._records: list[dict[str, Any]] = []

    @property
    def records(self) -> list[dict[str, Any]]:
        """Return all lineage records collected so far."""
        return list(self._records)

    async def before_run(self, context: MiddlewareContext) -> None:
        """Attach lineage tracking identifiers to the context metadata."""
        lineage_id = uuid.uuid4().hex
        context.metadata["lineage_id"] = lineage_id
        context.metadata["lineage_agent"] = context.agent_name
        context.metadata["lineage_start_ns"] = time.monotonic_ns()

    async def after_run(self, context: MiddlewareContext, result: Any) -> Any:
        """Record the completed run in the lineage log and return *result*."""
        start_ns = context.metadata.pop("lineage_start_ns", None)
        elapsed_ms = (
            (time.monotonic_ns() - start_ns) / 1_000_000 if start_ns else None
        )

        record: dict[str, Any] = {
            "lineage_id": context.metadata.get("lineage_id"),
            "agent_name": context.agent_name,
            "method": context.method,
            "elapsed_ms": elapsed_ms,
            "has_result": result is not None,
        }
        self._records.append(record)

        return result
