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

"""Pipeline step that applies quality-gate validation rules to data."""

from __future__ import annotations

from collections.abc import Callable, Sequence
from typing import Any

from fireflyframework_genai.pipeline.context import PipelineContext


class QualityGateStep:
    """A :class:`StepExecutor`-compatible pipeline step that validates data.

    Each *rule* is a callable that receives the inputs dict and returns
    ``True`` when the data passes or ``False`` (or raises) when it does not.
    An optional human-readable description can be attached to each rule by
    passing ``(callable, description)`` tuples.

    If **any** rule fails the step raises :class:`ValueError` listing all
    violations so downstream steps are not executed with bad data.

    Parameters
    ----------
    rules:
        Sequence of validation callables or ``(callable, description)``
        pairs.
    """

    def __init__(
        self,
        rules: Sequence[Callable[[dict[str, Any]], bool] | tuple[Callable[[dict[str, Any]], bool], str]],
    ) -> None:
        self._rules: list[tuple[Callable[[dict[str, Any]], bool], str]] = []
        for idx, rule in enumerate(rules):
            if isinstance(rule, tuple):
                self._rules.append(rule)
            else:
                self._rules.append((rule, f"rule_{idx}"))

    async def execute(
        self,
        context: PipelineContext,
        inputs: dict[str, Any],
    ) -> Any:
        """Validate *inputs* against all registered rules.

        Returns the original *inputs* unchanged when all rules pass.
        Raises :class:`ValueError` with a summary of failures otherwise.
        """
        violations: list[str] = []

        for check, description in self._rules:
            try:
                passed = check(inputs)
            except Exception as exc:
                violations.append(f"{description}: {exc}")
                continue
            if not passed:
                violations.append(description)

        if violations:
            context.metadata.setdefault("quality_violations", []).extend(violations)
            raise ValueError(
                f"Quality gate failed with {len(violations)} violation(s): "
                + "; ".join(violations)
            )

        context.metadata.setdefault("quality_checks_passed", 0)
        context.metadata["quality_checks_passed"] += len(self._rules)

        return inputs
