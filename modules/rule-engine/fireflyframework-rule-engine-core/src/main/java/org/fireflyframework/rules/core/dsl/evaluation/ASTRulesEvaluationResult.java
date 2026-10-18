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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Pure AST-based rules evaluation result with no legacy dependencies.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ASTRulesEvaluationResult {
    
    private boolean success;
    private boolean conditionResult;
    private Map<String, Object> outputData;
    private long executionTimeMs;
    private String error;
    private boolean circuitBreakerTriggered;
    private String circuitBreakerMessage;
    
    /**
     * Check if the evaluation was successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Check if any conditions were met
     */
    public boolean isConditionMet() {
        return conditionResult;
    }
    
    /**
     * Get a specific output value
     */
    public Object getOutputValue(String key) {
        return outputData != null ? outputData.get(key) : null;
    }
    
    /**
     * Check if output contains a specific key
     */
    public boolean hasOutput(String key) {
        return outputData != null && outputData.containsKey(key);
    }
    
    /**
     * Get the number of output variables
     */
    public int getOutputCount() {
        return outputData != null ? outputData.size() : 0;
    }
    
    /**
     * Check if there was an error
     */
    public boolean hasError() {
        return error != null && !error.trim().isEmpty();
    }
    
    /**
     * Create a successful result
     */
    public static ASTRulesEvaluationResult success(boolean conditionResult, Map<String, Object> outputData, long executionTimeMs) {
        return ASTRulesEvaluationResult.builder()
                .success(true)
                .conditionResult(conditionResult)
                .outputData(outputData)
                .executionTimeMs(executionTimeMs)
                .build();
    }
    
    /**
     * Create a failed result
     */
    public static ASTRulesEvaluationResult failure(String error) {
        return ASTRulesEvaluationResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
