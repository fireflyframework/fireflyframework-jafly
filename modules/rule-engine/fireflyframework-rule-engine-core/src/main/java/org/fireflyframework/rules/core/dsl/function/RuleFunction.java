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

package org.fireflyframework.rules.core.dsl.function;

/**
 * Pluggable function callable from the rule DSL via the {@code call} action or as part of an
 * expression (e.g., {@code calculate result as my_function(amount, rate)}).
 * <p>
 * Implementations are registered with {@link CustomFunctionRegistry}. Custom functions are
 * looked up <i>before</i> the built-in catalog, so a registration with the same name as a
 * built-in (e.g., {@code "max"}) deliberately shadows the built-in. Names are matched
 * case-insensitively.
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Arguments arrive as evaluated values (literals, resolved variables, or nested
 *       function results) -- not as AST nodes.</li>
 *   <li>Implementations should throw {@link IllegalArgumentException} for invalid argument
 *       counts or types; the evaluator surfaces these to the caller.</li>
 *   <li>Implementations must be thread-safe; the same instance can be invoked concurrently
 *       across rule evaluations.</li>
 * </ul>
 */
@FunctionalInterface
public interface RuleFunction {

    /**
     * Apply this function to the evaluated argument list.
     *
     * @param args evaluated argument values (may be empty, never {@code null})
     * @return the function result; may be {@code null} if the function legitimately returns a null value
     */
    Object apply(Object[] args);
}
