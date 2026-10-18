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
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test circuit breaker functionality
 */
@DisplayName("Circuit Breaker Tests")
public class CircuitBreakerTest {

    private ASTRulesEvaluationEngine evaluationEngine;
    private ConstantService constantService;

    @BeforeEach
    void setUp() {
        constantService = Mockito.mock(ConstantService.class);
        Mockito.lenient().when(constantService.getConstantsByCodes(Mockito.anyList()))
               .thenReturn(Flux.empty());

        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        evaluationEngine = new ASTRulesEvaluationEngine(parser, constantService);
    }

    @Test
    @DisplayName("Circuit breaker should trigger when condition is met")
    void testCircuitBreakerTriggered() {
        String yamlRule = """
            name: "Circuit Breaker Test"
            description: "Test circuit breaker triggering"
            
            inputs:
              - riskScore
            
            when:
              - riskScore greater_than 0
            
            then:
              - set initial_check to "PASSED"
              - if riskScore greater_than 90 then circuit_breaker "HIGH_RISK_DETECTED"
              - set final_check to "COMPLETED"
            
            output:
              initial_check: text
              final_check: text
            """;

        Map<String, Object> inputData = Map.of("riskScore", 95);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

        System.out.println("Result success: " + result.isSuccess());
        System.out.println("Circuit breaker triggered: " + result.isCircuitBreakerTriggered());
        System.out.println("Circuit breaker message: " + result.getCircuitBreakerMessage());
        System.out.println("Error: " + result.getError());
        System.out.println("Output data: " + result.getOutputData());

        // The circuit breaker should be triggered
        assertTrue(result.isCircuitBreakerTriggered(), "Circuit breaker should be triggered");
        assertEquals("HIGH_RISK_DETECTED", result.getCircuitBreakerMessage());
        
        // The evaluation should still be considered successful (controlled stop, not an error)
        assertTrue(result.isSuccess(), "Circuit breaker is a controlled stop, not an error");
        
        // initial_check should be set, but final_check should not (execution stopped)
        assertEquals("PASSED", result.getOutputData().get("initial_check"));
        assertNull(result.getOutputData().get("final_check"), "Execution should stop after circuit breaker");
    }

    @Test
    @DisplayName("Circuit breaker should not trigger when condition is not met")
    void testCircuitBreakerNotTriggered() {
        String yamlRule = """
            name: "Circuit Breaker Test"
            description: "Test circuit breaker not triggering"
            
            inputs:
              - riskScore
            
            when:
              - riskScore greater_than 0
            
            then:
              - set initial_check to "PASSED"
              - if riskScore greater_than 90 then circuit_breaker "HIGH_RISK_DETECTED"
              - set final_check to "COMPLETED"
            
            output:
              initial_check: text
              final_check: text
            """;

        Map<String, Object> inputData = Map.of("riskScore", 50);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

        System.out.println("Result success: " + result.isSuccess());
        System.out.println("Circuit breaker triggered: " + result.isCircuitBreakerTriggered());
        System.out.println("Output data: " + result.getOutputData());

        // The circuit breaker should NOT be triggered
        assertFalse(result.isCircuitBreakerTriggered(), "Circuit breaker should not be triggered");
        
        // The evaluation should be successful
        assertTrue(result.isSuccess());
        
        // Both checks should be set
        assertEquals("PASSED", result.getOutputData().get("initial_check"));
        assertEquals("COMPLETED", result.getOutputData().get("final_check"));
    }

    @Test
    @DisplayName("Circuit breaker in else block")
    void testCircuitBreakerInElseBlock() {
        String yamlRule = """
            name: "Circuit Breaker in Else"
            description: "Test circuit breaker in else block"
            
            inputs:
              - creditScore
            
            when:
              - creditScore at_least 700
            
            then:
              - set approval_status to "APPROVED"
            
            else:
              - set approval_status to "REJECTED"
              - circuit_breaker "CREDIT_SCORE_TOO_LOW"
              - set rejection_processed to true
            
            output:
              approval_status: text
              rejection_processed: boolean
            """;

        Map<String, Object> inputData = Map.of("creditScore", 600);

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

        System.out.println("Result success: " + result.isSuccess());
        System.out.println("Circuit breaker triggered: " + result.isCircuitBreakerTriggered());
        System.out.println("Circuit breaker message: " + result.getCircuitBreakerMessage());
        System.out.println("Error: " + result.getError());
        System.out.println("Output data: " + result.getOutputData());

        // The circuit breaker should be triggered
        assertTrue(result.isCircuitBreakerTriggered(), "Circuit breaker should be triggered");
        assertEquals("CREDIT_SCORE_TOO_LOW", result.getCircuitBreakerMessage());
        
        // The evaluation should still be successful
        assertTrue(result.isSuccess(), "Circuit breaker is a controlled stop, not an error");
        
        // approval_status should be set, but rejection_processed should not
        assertEquals("REJECTED", result.getOutputData().get("approval_status"));
        assertNull(result.getOutputData().get("rejection_processed"), "Execution should stop after circuit breaker");
    }

    @Test
    @DisplayName("Circuit breaker with multiple rules")
    void testCircuitBreakerWithMultipleRules() {
        String yamlRule = """
            name: "Multi-Rule Circuit Breaker"
            description: "Test circuit breaker with multiple rules"
            
            inputs:
              - amount
              - riskLevel
            
            rules:
              - name: "Amount Check"
                when:
                  - amount greater_than 0
                then:
                  - set amount_check to "PASSED"
                  - if amount greater_than 1000000 then circuit_breaker "AMOUNT_TOO_HIGH"
              
              - name: "Risk Check"
                when:
                  - riskLevel equals "HIGH"
                then:
                  - set risk_check to "EVALUATED"
                  - circuit_breaker "HIGH_RISK_TRANSACTION"
              
              - name: "Final Processing"
                when:
                  - amount greater_than 0
                then:
                  - set final_processing to "COMPLETED"
            
            output:
              amount_check: text
              risk_check: text
              final_processing: text
            """;

        Map<String, Object> inputData = Map.of(
            "amount", 500000,
            "riskLevel", "HIGH"
        );

        ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yamlRule, inputData);

        System.out.println("Result success: " + result.isSuccess());
        System.out.println("Circuit breaker triggered: " + result.isCircuitBreakerTriggered());
        System.out.println("Circuit breaker message: " + result.getCircuitBreakerMessage());
        System.out.println("Error: " + result.getError());
        System.out.println("Output data: " + result.getOutputData());

        // The circuit breaker should be triggered
        assertTrue(result.isCircuitBreakerTriggered(), "Circuit breaker should be triggered");
        assertEquals("HIGH_RISK_TRANSACTION", result.getCircuitBreakerMessage());
        
        // The evaluation should still be successful
        assertTrue(result.isSuccess(), "Circuit breaker is a controlled stop, not an error");
        
        // First rule should execute, second rule should trigger circuit breaker, third rule should not execute
        assertEquals("PASSED", result.getOutputData().get("amount_check"));
        assertEquals("EVALUATED", result.getOutputData().get("risk_check"));
        assertNull(result.getOutputData().get("final_processing"), "Third rule should not execute after circuit breaker");
    }
}

