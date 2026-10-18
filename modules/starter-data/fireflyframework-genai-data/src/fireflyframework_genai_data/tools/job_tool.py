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

"""GenAI tool for managing data processing jobs."""

from __future__ import annotations

from typing import Any

from fireflyframework_genai.tools.base import BaseTool, ParameterSpec

from fireflyframework_genai_data.client import DataStarterClient


class DataJobTool(BaseTool):
    """Agent tool for starting, checking, and collecting results from data jobs."""

    def __init__(self, client: DataStarterClient) -> None:
        self._client = client
        super().__init__(
            name="data_job",
            description="Manage data processing jobs: start, check status, or collect results",
            tags=("data", "jobs"),
            parameters=[
                ParameterSpec(
                    name="action",
                    type_annotation="str",
                    description="The action to perform: 'start', 'check', or 'collect'",
                    required=True,
                ),
                ParameterSpec(
                    name="job_type",
                    type_annotation="str",
                    description="Type of job to start (required for 'start' action)",
                    required=False,
                ),
                ParameterSpec(
                    name="execution_id",
                    type_annotation="str",
                    description="Job execution ID (required for 'check' and 'collect' actions)",
                    required=False,
                ),
                ParameterSpec(
                    name="parameters",
                    type_annotation="dict",
                    description="Job parameters (used with 'start' action)",
                    required=False,
                ),
            ],
        )

    async def _execute(self, **kwargs: Any) -> Any:
        action: str = kwargs["action"]
        match action:
            case "start":
                job_type = kwargs.get("job_type")
                parameters = kwargs.get("parameters", {})
                if not job_type:
                    raise ValueError("'job_type' is required for the 'start' action")
                return await self._client.start_job(
                    job_type=job_type,
                    parameters=parameters,
                )
            case "check":
                execution_id = kwargs.get("execution_id")
                if not execution_id:
                    raise ValueError("'execution_id' is required for the 'check' action")
                return await self._client.check_job(execution_id=execution_id)
            case "collect":
                execution_id = kwargs.get("execution_id")
                if not execution_id:
                    raise ValueError("'execution_id' is required for the 'collect' action")
                return await self._client.collect_results(execution_id=execution_id)
            case _:
                raise ValueError(
                    f"Unknown action '{action}'. Must be one of: start, check, collect"
                )
