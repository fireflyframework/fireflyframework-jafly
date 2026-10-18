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

package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Test helper for rule evaluation tests
 */
@Component
public class TestHelper {

    @Autowired
    private ASTRulesEvaluationEngine evaluationEngine;

    /**
     * Evaluate rules with input data and return result
     */
    public ASTRulesEvaluationResult evaluateRules(String yamlRule, Map<String, Object> inputData) {
        return evaluationEngine.evaluateRules(yamlRule, inputData);
    }

    /**
     * Create a new input data map
     */
    public Map<String, Object> createInputData() {
        return new HashMap<>();
    }

    /**
     * Get output variable from result
     */
    public Object getOutputVariable(ASTRulesEvaluationResult result, String variableName) {
        if (result.getOutputData() == null) {
            return null;
        }
        return result.getOutputData().get(variableName);
    }
}
