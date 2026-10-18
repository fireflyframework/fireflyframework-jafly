/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.rules.core.dsl.function;

import java.util.Map;

/**
 * Cross-rule composition hook. Implementations resolve a stored rule by its
 * {@code code} and evaluate it against the supplied inputs, returning the
 * resulting output map. Used by the {@code invoke_rule(code, inputs)} built-in
 * so rules can call other rules without coupling the evaluator directly to the
 * services layer.
 *
 * <p>Implementations must be safe to call from a {@code Schedulers.boundedElastic()}
 * worker (the synchronous visitor's host thread). They are expected to block
 * until the invoked rule completes -- nested invocations cost a thread but keep
 * the visitor model simple and avoid threading a Mono through the AST.
 */
public interface RuleInvoker {

    /**
     * Synchronously evaluate the rule with the given code against the given inputs.
     *
     * @param code      the stored rule's code (matches RuleDefinition.code)
     * @param inputData the inputs passed to the nested rule
     * @return the output map produced by the nested rule (never {@code null};
     *         empty if the nested rule succeeded but produced no outputs)
     * @throws RuntimeException if the rule does not exist, fails to parse, or the nested
     *                          evaluation reports {@code success=false}
     */
    Map<String, Object> invokeBlocking(String code, Map<String, Object> inputData);
}
