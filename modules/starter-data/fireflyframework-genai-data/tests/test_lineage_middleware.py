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

"""Tests for DataLineageMiddleware."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from fireflyframework_genai_data.middleware.lineage import DataLineageMiddleware


@dataclass
class FakeMiddlewareContext:
    """Lightweight stand-in for MiddlewareContext used in tests."""

    agent_name: str = "test-agent"
    prompt: Any = None
    method: str = "run"
    deps: Any = None
    kwargs: dict[str, Any] = field(default_factory=dict)
    metadata: dict[str, Any] = field(default_factory=dict)
    context: Any = None


@pytest.fixture()
def middleware() -> DataLineageMiddleware:
    return DataLineageMiddleware()


@pytest.fixture()
def fake_context() -> FakeMiddlewareContext:
    return FakeMiddlewareContext()


class TestDataLineageMiddleware:
    """DataLineageMiddleware unit tests."""

    @pytest.mark.asyncio()
    async def test_before_run_sets_lineage_id(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)

        assert "lineage_id" in fake_context.metadata
        assert isinstance(fake_context.metadata["lineage_id"], str)
        assert len(fake_context.metadata["lineage_id"]) == 32  # uuid4 hex length

    @pytest.mark.asyncio()
    async def test_before_run_sets_agent_name(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)

        assert fake_context.metadata["lineage_agent"] == "test-agent"

    @pytest.mark.asyncio()
    async def test_before_run_sets_start_timestamp(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)

        assert "lineage_start_ns" in fake_context.metadata
        assert isinstance(fake_context.metadata["lineage_start_ns"], int)

    @pytest.mark.asyncio()
    async def test_after_run_returns_result(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)
        sentinel = {"answer": 42}
        returned = await middleware.after_run(fake_context, sentinel)

        assert returned is sentinel

    @pytest.mark.asyncio()
    async def test_after_run_records_lineage(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)
        await middleware.after_run(fake_context, "result-value")

        assert len(middleware.records) == 1
        record = middleware.records[0]
        assert record["agent_name"] == "test-agent"
        assert record["method"] == "run"
        assert record["has_result"] is True
        assert record["elapsed_ms"] is not None
        assert record["elapsed_ms"] >= 0

    @pytest.mark.asyncio()
    async def test_after_run_with_none_result(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)
        await middleware.after_run(fake_context, None)

        assert middleware.records[0]["has_result"] is False

    @pytest.mark.asyncio()
    async def test_multiple_runs_accumulate_records(
        self, middleware: DataLineageMiddleware
    ) -> None:
        for i in range(3):
            ctx = FakeMiddlewareContext(agent_name=f"agent-{i}")
            await middleware.before_run(ctx)
            await middleware.after_run(ctx, f"result-{i}")

        assert len(middleware.records) == 3
        assert middleware.records[0]["agent_name"] == "agent-0"
        assert middleware.records[2]["agent_name"] == "agent-2"

    @pytest.mark.asyncio()
    async def test_records_returns_copy(
        self, middleware: DataLineageMiddleware, fake_context: FakeMiddlewareContext
    ) -> None:
        await middleware.before_run(fake_context)
        await middleware.after_run(fake_context, "x")

        records_a = middleware.records
        records_b = middleware.records
        assert records_a is not records_b
        assert records_a == records_b

    @pytest.mark.asyncio()
    async def test_lineage_ids_are_unique(
        self, middleware: DataLineageMiddleware
    ) -> None:
        ids = []
        for _ in range(5):
            ctx = FakeMiddlewareContext()
            await middleware.before_run(ctx)
            ids.append(ctx.metadata["lineage_id"])
            await middleware.after_run(ctx, None)

        assert len(set(ids)) == 5
