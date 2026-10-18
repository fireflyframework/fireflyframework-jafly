/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.rules.core.services.impl;

import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.function.RuleInvoker;
import org.fireflyframework.rules.core.services.RuleDefinitionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * Default {@link RuleInvoker} backed by {@link RuleDefinitionService}. Resolves the
 * stored rule by code, evaluates it synchronously (via {@code Mono.block()}), and
 * returns its output map. Nested invocations are intentionally blocking -- this
 * keeps the synchronous visitor model intact and runs entirely on the
 * {@code boundedElastic} worker the engine schedules itself onto.
 *
 * <p>The dependency on {@code RuleDefinitionService} is wired lazily via
 * {@link ObjectProvider} so we don't create a hard cycle between the evaluation
 * engine and the service that depends on it.
 */
@Component
public class RuleInvokerImpl implements RuleInvoker {

    private final ObjectProvider<RuleDefinitionService> ruleDefinitionServiceProvider;

    public RuleInvokerImpl(ObjectProvider<RuleDefinitionService> ruleDefinitionServiceProvider) {
        this.ruleDefinitionServiceProvider = ruleDefinitionServiceProvider;
    }

    @Override
    public Map<String, Object> invokeBlocking(String code, Map<String, Object> inputData) {
        RuleDefinitionService service = ruleDefinitionServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException(
                    "invoke_rule() requires RuleDefinitionService on the classpath. Include the "
                            + "rule-engine-models module and provide an R2DBC datasource.");
        }
        ASTRulesEvaluationResult nested = service.evaluateRuleByCode(code, inputData == null ? Collections.emptyMap() : inputData)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException(
                        "invoke_rule('" + code + "') returned no result"));
        if (!nested.isSuccess()) {
            throw new IllegalStateException(
                    "invoke_rule('" + code + "') failed: " + nested.getError());
        }
        return nested.getOutputData() != null ? nested.getOutputData() : Collections.emptyMap();
    }
}
