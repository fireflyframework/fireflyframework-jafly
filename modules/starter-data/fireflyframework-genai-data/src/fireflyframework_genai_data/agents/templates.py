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

"""Pre-built agent templates for common data-centric workflows."""

from __future__ import annotations

from typing import Any

from fireflyframework_genai.agents.agent import FireflyAgent

from fireflyframework_genai_data.middleware.lineage import DataLineageMiddleware
from fireflyframework_genai_data.tools.toolkit import DataToolKit


def create_data_analyst_agent(
    base_url: str,
    name: str = "data-analyst",
    model: Any | None = None,
    **kwargs: Any,
) -> FireflyAgent:
    """Create a pre-configured data analyst agent.

    The agent comes with:

    * A full :class:`DataToolKit` wired to *base_url*.
    * :class:`DataLineageMiddleware` for automatic lineage tracking.
    * Default instructions oriented toward data analysis tasks.

    Parameters
    ----------
    base_url:
        Base URL of the Firefly Data Starter service.
    name:
        Agent name (default ``"data-analyst"``).
    model:
        LLM model identifier or instance.  ``None`` uses the framework default.
    **kwargs:
        Additional keyword arguments forwarded to :class:`FireflyAgent`.

    Returns
    -------
    FireflyAgent
        A ready-to-use agent instance.
    """
    toolkit = DataToolKit(base_url=base_url)
    lineage_middleware = DataLineageMiddleware()

    default_instructions = (
        "You are a data analyst agent with access to data enrichment, "
        "job management, and data operations tools. Analyze data requests, "
        "choose appropriate enrichment strategies, and manage data processing "
        "jobs efficiently. Always validate inputs before processing and "
        "provide clear summaries of results."
    )

    # Allow callers to override instructions via kwargs
    instructions = kwargs.pop("instructions", (default_instructions,))
    if isinstance(instructions, str):
        instructions = (instructions,)

    # Merge caller-provided tools with the data toolkit
    extra_tools = kwargs.pop("tools", ())
    tools = (toolkit, *extra_tools)

    # Merge caller-provided middleware with lineage middleware
    extra_middleware = kwargs.pop("middleware", ())
    middleware = (lineage_middleware, *extra_middleware)

    return FireflyAgent(
        name=name,
        model=model,
        instructions=instructions,
        tools=tools,
        middleware=middleware,
        tags=("data", "analyst"),
        description="Pre-configured data analyst agent with enrichment, job, and operations tools",
        **kwargs,
    )
