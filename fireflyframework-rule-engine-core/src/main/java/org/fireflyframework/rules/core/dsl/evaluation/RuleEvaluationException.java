/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.rules.core.dsl.evaluation;

/**
 * Raised when an action or condition fails during rule evaluation.
 * <p>
 * The engine wraps the underlying failure (e.g. unknown function, type-coercion error,
 * division by zero, missing variable) with the index of the offending action or condition
 * and its source debug string so the outer evaluator can report a precise
 * {@code success=false} result instead of silently flipping to the else branch.
 */
public class RuleEvaluationException extends RuntimeException {

    public RuleEvaluationException(String message) {
        super(message);
    }

    public RuleEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
