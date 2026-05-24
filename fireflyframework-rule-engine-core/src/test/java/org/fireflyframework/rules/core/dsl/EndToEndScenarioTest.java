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
import org.fireflyframework.rules.core.dsl.function.CustomFunctionRegistry;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.interfaces.enums.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;

/**
 * End-to-end DSL integration test that exercises the full evaluation pipeline against a
 * realistic, multi-step rule. Single failures here usually indicate a regression in one
 * of: parser, AST cache, constant resolution, condition evaluation, action execution,
 * arithmetic coercion, loops, sub-rules, circuit breaker, custom-function registry, or
 * error propagation.
 *
 * <p>The scenario models a simplified loan-eligibility pipeline. It is wide rather than
 * deep on purpose -- a "smoke test" that catches integration regressions a single-purpose
 * unit test would miss.</p>
 */
class EndToEndScenarioTest {

    private ASTRulesEvaluationEngine engine;
    private CustomFunctionRegistry registry;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);

        ConstantService constants = Mockito.mock(ConstantService.class);
        Mockito.when(constants.getConstantsByCodes(anyList())).thenReturn(Flux.just(
                ConstantDTO.builder().code("MIN_CREDIT_SCORE").valueType(ValueType.NUMBER)
                        .currentValue(new BigDecimal("650")).build(),
                ConstantDTO.builder().code("MIN_ANNUAL_INCOME").valueType(ValueType.NUMBER)
                        .currentValue(new BigDecimal("50000")).build(),
                ConstantDTO.builder().code("MAX_DTI").valueType(ValueType.NUMBER)
                        .currentValue(new BigDecimal("0.4")).build()
        ));

        registry = new CustomFunctionRegistry();
        registry.register("regional_risk_score",
                args -> ("CA".equals(args[0]) || "NY".equals(args[0])) ? 10 : 0);

        engine = new ASTRulesEvaluationEngine(parser, constants, null, null, registry);
    }

    /** Multi-stage loan-eligibility rule covering most DSL surface area. */
    private static final String LOAN_RULE = """
            name: "Loan Eligibility End-to-End"
            description: "Exercises constants, sub-rules, loops, conditionals, and custom functions"

            inputs:
              creditScore: "number"
              annualIncome: "number"
              existingDebtPayments: "list"
              region: "string"
              applicant_email: "string"

            rules:
              - name: "Financial Validation"
                when:
                  - creditScore at_least MIN_CREDIT_SCORE
                  - annualIncome at_least MIN_ANNUAL_INCOME
                then:
                  - set financial_check to "PASSED"
                  - calculate monthly_income as annualIncome / 12
                else:
                  - set financial_check to "FAILED"

              - name: "Debt Aggregation"
                then:
                  - set total_debt to 0
                  - forEach payment in existingDebtPayments: add payment to total_debt

              - name: "DTI Computation"
                then:
                  - calculate debt_to_income as total_debt / annualIncome

              - name: "Final Decision"
                when:
                  - financial_check equals "PASSED"
                  - debt_to_income at_most MAX_DTI
                then:
                  - run risk_adjustment as regional_risk_score(region)
                  - run validated_email as validate_email(applicant_email)
                  - run tier as if_else(creditScore at_least 750, "PRIME", "STANDARD")
                  - run display_name as coalesce(applicant_email, "anonymous")
                  - set decision to "APPROVED"
                else:
                  - set decision to "DECLINED"

            output:
              decision: decision
              tier: tier
              monthly_income: monthly_income
              total_debt: total_debt
              debt_to_income: debt_to_income
              risk_adjustment: risk_adjustment
              validated_email: validated_email
              display_name: display_name
            """;

    @Test
    @DisplayName("Approves an applicant who satisfies every rule and computes all derived values")
    void approvedApplicantEndToEnd() {
        Map<String, Object> input = new HashMap<>();
        input.put("creditScore", 760);
        input.put("annualIncome", new BigDecimal("120000"));
        input.put("existingDebtPayments", List.of(new BigDecimal("400"), new BigDecimal("250"), new BigDecimal("150")));
        input.put("region", "CA");
        input.put("applicant_email", "alice@example.com");

        ASTRulesEvaluationResult result = engine.evaluateRules(LOAN_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        assertThat(out).containsEntry("decision", "APPROVED");
        assertThat(out).containsEntry("tier", "PRIME");
        assertThat(out).containsEntry("validated_email", true);
        assertThat(out).containsEntry("display_name", "alice@example.com");
        assertThat(out).containsEntry("risk_adjustment", 10);
        // monthly_income = 120000 / 12 = 10000 (BigDecimal with 10-scale division)
        assertThat(new BigDecimal(out.get("monthly_income").toString()))
                .isEqualByComparingTo(new BigDecimal("10000"));
        // total_debt = 400 + 250 + 150 = 800
        assertThat(new BigDecimal(out.get("total_debt").toString()))
                .isEqualByComparingTo(new BigDecimal("800"));
        // debt_to_income = 800 / 120000 ≈ 0.0067 (well under MAX_DTI 0.4)
        BigDecimal dti = new BigDecimal(out.get("debt_to_income").toString());
        assertThat(dti).isLessThan(new BigDecimal("0.4"));
    }

    @Test
    @DisplayName("Declines an applicant who fails the financial-validation gate")
    void declinedOnLowCreditScore() {
        Map<String, Object> input = new HashMap<>();
        input.put("creditScore", 600);                                  // < MIN_CREDIT_SCORE
        input.put("annualIncome", new BigDecimal("120000"));
        input.put("existingDebtPayments", List.of(new BigDecimal("400")));
        input.put("region", "TX");
        input.put("applicant_email", "bob@example.com");

        ASTRulesEvaluationResult result = engine.evaluateRules(LOAN_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        assertThat(out).containsEntry("decision", "DECLINED");
        assertThat(out).containsEntry("financial_check", "FAILED");
    }

    @Test
    @DisplayName("Picks STANDARD tier when credit score is below the PRIME cutoff")
    void standardTierBelowPrimeCutoff() {
        Map<String, Object> input = new HashMap<>();
        input.put("creditScore", 700);                                  // qualifies but < 750
        input.put("annualIncome", new BigDecimal("80000"));
        input.put("existingDebtPayments", List.of(new BigDecimal("500")));
        input.put("region", "TX");
        input.put("applicant_email", "carol@example.com");

        ASTRulesEvaluationResult result = engine.evaluateRules(LOAN_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        assertThat(out).containsEntry("decision", "APPROVED");
        assertThat(out).containsEntry("tier", "STANDARD");
        assertThat(out).containsEntry("risk_adjustment", 0); // non-CA/NY region
    }

    @Test
    @DisplayName("Handles an empty debt list without arithmetic errors")
    void zeroDebtScenarioComputesZeroDti() {
        Map<String, Object> input = new HashMap<>();
        input.put("creditScore", 800);
        input.put("annualIncome", new BigDecimal("100000"));
        input.put("existingDebtPayments", List.of());                   // no debt
        input.put("region", "NY");
        input.put("applicant_email", "dave@example.com");

        ASTRulesEvaluationResult result = engine.evaluateRules(LOAN_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        assertThat(out).containsEntry("decision", "APPROVED");
        assertThat(new BigDecimal(out.get("total_debt").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(out.get("debt_to_income").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Circuit-breaker action stops evaluation cleanly with structured metadata")
    void circuitBreakerStopsEvaluation() {
        String yaml = """
                inputs:
                  riskScore: "number"
                when:
                  - riskScore at_least 0
                then:
                  - set started to true
                  - if riskScore at_least 90 then circuit_breaker "HIGH_RISK"
                  - set finished to true
                output:
                  started: started
                  finished: finished
                """;

        ASTRulesEvaluationResult result = engine.evaluateRules(yaml, Map.of("riskScore", 95));

        assertThat(result.isSuccess()).isTrue();          // circuit breaker is a controlled stop
        assertThat(result.isCircuitBreakerTriggered()).isTrue();
        assertThat(result.getCircuitBreakerMessage()).contains("HIGH_RISK");
        // started was set before the break; finished should NOT be set
        assertThat(result.getOutputData()).containsEntry("started", true);
        assertThat(result.getOutputData()).doesNotContainKey("finished");
    }
}
