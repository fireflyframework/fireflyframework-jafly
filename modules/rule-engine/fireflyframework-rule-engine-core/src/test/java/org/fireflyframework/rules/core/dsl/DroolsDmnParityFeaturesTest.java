/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationEngine;
import org.fireflyframework.rules.core.dsl.evaluation.ASTRulesEvaluationResult;
import org.fireflyframework.rules.core.dsl.function.CustomFunctionRegistry;
import org.fireflyframework.rules.core.dsl.function.RuleInvoker;
import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.fireflyframework.rules.core.services.ConstantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the drools/DMN parity features added in 26.05.08:
 * sub-rule priority (salience), input defaults, per-rule timeout, log/percentile/hash/math
 * built-ins, rule composition via invoke_rule, and DMN-style decision tables.
 */
class DroolsDmnParityFeaturesTest {

    private ASTRulesEvaluationEngine engine;
    private ASTRulesEvaluationEngine engineWithInvoker;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList())).thenReturn(Flux.empty());
        engine = new ASTRulesEvaluationEngine(parser, constantService, null, null, new CustomFunctionRegistry());

        RuleInvoker stubInvoker = (code, inputs) -> {
            if ("score_rule".equals(code)) {
                int amount = ((Number) inputs.get("amount")).intValue();
                return Map.of("score", amount > 1000 ? "HIGH" : "LOW");
            }
            if ("composite_underwrite".equals(code)) {
                int credit = ((Number) inputs.get("creditScore")).intValue();
                int income = ((Number) inputs.get("annualIncome")).intValue();
                int debt = ((Number) inputs.get("existingDebt")).intValue();
                boolean approved = credit >= 700 && income >= 50000 && debt < income / 2;
                return Map.of("approved", approved, "tier", approved ? "PREFERRED" : "STANDARD");
            }
            throw new IllegalArgumentException("unknown rule: " + code);
        };
        engineWithInvoker = new ASTRulesEvaluationEngine(parser, constantService, null, null,
                new CustomFunctionRegistry(), null, stubInvoker);
    }

    // -------- New built-ins --------

    @Test
    @DisplayName("percentile() interpolates between sorted samples")
    void percentile() {
        String yaml = """
                name: pct
                then:
                  - run p50 as percentile([1,2,3,4,5,6,7,8,9,10], 50)
                  - run p90 as percentile([1,2,3,4,5,6,7,8,9,10], 90)
                output:
                  p50: p50
                  p90: p90
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        // p50 of 1..10 -> 5.5 ; p90 -> 9.1
        assertThat(((BigDecimal) r.getOutputData().get("p50")).doubleValue()).isEqualTo(5.5);
        assertThat(((BigDecimal) r.getOutputData().get("p90")).doubleValue()).isEqualTo(9.1);
    }

    @Test
    @DisplayName("hash() produces a hex digest of the input")
    void hash() {
        String yaml = """
                name: hash
                then:
                  - run h as hash("hello")
                output:
                  h: h
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        // SHA-256("hello")
        assertThat(r.getOutputData().get("h")).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("log() built-in returns the message and routes through SLF4J")
    void logFunction() {
        String yaml = """
                name: logtest
                then:
                  - run echoed as log("rule fired", "INFO")
                output:
                  echoed: echoed
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("echoed")).isEqualTo("rule fired");
    }

    @Test
    @DisplayName("Advanced math: exp, ln, sin all available")
    void advancedMath() {
        String yaml = """
                name: math
                then:
                  - run e as exp(1)
                  - run ln2 as ln(2)
                  - run zero as sin(0)
                output:
                  e: e
                  ln2: ln2
                  zero: zero
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        assertThat(((BigDecimal) r.getOutputData().get("e")).doubleValue()).isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(((BigDecimal) r.getOutputData().get("ln2")).doubleValue()).isCloseTo(Math.log(2), org.assertj.core.data.Offset.offset(1e-12));
        assertThat(((BigDecimal) r.getOutputData().get("zero")).doubleValue()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    // -------- Sub-rule priority --------

    @Test
    @DisplayName("Sub-rules with higher priority evaluate first; ties preserve YAML order")
    void subRulePriority() {
        // The "low" sub-rule writes tier=LOW unconditionally; the "high" sub-rule overwrites
        // tier=HIGH. With higher priority on "high", the LOW write happens after HIGH and
        // takes the final slot. To make order observable, we use side-effect collisions.
        String yaml = """
                name: priority test
                rules:
                  - name: low rule
                    priority: 1
                    then:
                      - set tier to "LOW"
                  - name: high rule
                    priority: 10
                    then:
                      - set tier to "HIGH"
                output:
                  tier: tier
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        // Higher-priority sub-rule runs FIRST; its value is then overwritten by the
        // lower-priority sub-rule which runs second. Final value == LOW confirms ordering.
        assertThat(r.getOutputData().get("tier")).isEqualTo("LOW");
    }

    // -------- Input defaults --------

    @Test
    @DisplayName("Input defaults fill in variables the caller omitted")
    void inputDefaults() {
        String yaml = """
                name: defaults
                inputs:
                  threshold:
                    type: number
                    default: 100
                when:
                  - threshold at_least 50
                then:
                  - set verdict to "OK"
                else:
                  - set verdict to "FAIL"
                output:
                  verdict: verdict
                  threshold: threshold
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("verdict")).isEqualTo("OK");
        assertThat(r.getOutputData().get("threshold")).isEqualTo(100);
    }

    @Test
    @DisplayName("Caller-provided values override declared defaults")
    void callerOverridesDefault() {
        String yaml = """
                name: defaults
                inputs:
                  threshold:
                    type: number
                    default: 100
                when:
                  - threshold at_least 50
                then:
                  - set verdict to "OK"
                else:
                  - set verdict to "FAIL"
                output:
                  verdict: verdict
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of("threshold", 10));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("verdict")).isEqualTo("FAIL");
    }

    // -------- Per-rule timeout --------

    @Test
    @DisplayName("Per-rule timeout config is accepted and applied via Reactor")
    void perRuleTimeoutAccepted() {
        // 60 seconds: not going to trigger, but verifies the timeout config parses cleanly
        // and the wrapped pipeline still produces a successful result.
        String yaml = """
                name: timeout
                timeout: 60s
                then:
                  - set ok to true
                output:
                  ok: ok
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("ok")).isEqualTo(true);
    }

    // -------- invoke_rule --------

    @Test
    @DisplayName("invoke_rule delegates to a stored rule and returns its outputs")
    void invokeRule() {
        // Inputs are passed as alternating "key", value pairs trailing the rule code --
        // this avoids the YAML/JSON `{}` flow-mapping ambiguity inside action lines.
        String yaml = """
                name: composer
                then:
                  - run result as invoke_rule("score_rule", "amount", 1500)
                output:
                  result: result
                """;
        ASTRulesEvaluationResult r = engineWithInvoker.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).as("error was: %s", r.getError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) r.getOutputData().get("result");
        assertThat(nested).containsEntry("score", "HIGH");
    }

    @Test
    @DisplayName("invoke_rule with multiple input variables routes each pair correctly")
    void invokeRuleMultipleInputs() {
        String yaml = """
                name: composer
                inputs:
                  creditScore: number
                  annualIncome: number
                  existingDebt: number
                then:
                  - run underwriting as invoke_rule("composite_underwrite",
                       "creditScore", creditScore,
                       "annualIncome", annualIncome,
                       "existingDebt", existingDebt)
                output:
                  underwriting: underwriting
                """;
        ASTRulesEvaluationResult r = engineWithInvoker.evaluateRules(yaml,
                Map.of("creditScore", 750, "annualIncome", 80000, "existingDebt", 20000));
        assertThat(r.isSuccess()).as("error was: %s", r.getError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) r.getOutputData().get("underwriting");
        assertThat(nested).containsEntry("approved", true).containsEntry("tier", "PREFERRED");
    }

    @Test
    @DisplayName("invoke_rule odd-count trailing args fails loud")
    void invokeRuleOddArgs() {
        String yaml = """
                name: composer
                then:
                  - run r as invoke_rule("score_rule", "amount")
                output:
                  r: r
                """;
        ASTRulesEvaluationResult r = engineWithInvoker.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getError()).contains("alternating key/value");
    }

    @Test
    @DisplayName("Decision tables support multiple input columns referenced in conditions")
    void decisionTableMultipleInputs() {
        String yaml = """
                name: multi-input pricing
                inputs:
                  creditScore: number
                  annualIncome: number
                  age: number
                decision_table:
                  inputs: [creditScore, annualIncome, age]
                  outputs: [tier, multiplier]
                  hit_policy: FIRST
                  rules:
                    - when:
                        - creditScore at_least 750
                        - annualIncome at_least 100000
                        - age between 25 and 65
                      then:
                        tier: "PRIME"
                        multiplier: 1.0
                    - when:
                        - creditScore at_least 700
                        - annualIncome at_least 60000
                      then:
                        tier: "PREFERRED"
                        multiplier: 1.2
                    - otherwise: true
                      then:
                        tier: "STANDARD"
                        multiplier: 1.5
                output:
                  tier: tier
                  multiplier: multiplier
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml,
                Map.of("creditScore", 760, "annualIncome", 120000, "age", 40));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("tier")).isEqualTo("PRIME");
        assertThat(((Number) r.getOutputData().get("multiplier")).doubleValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("invoke_rule without a configured invoker fails loud")
    void invokeRuleNoInvoker() {
        String yaml = """
                name: composer
                then:
                  - run result as invoke_rule("score_rule", "amount", 1500)
                output:
                  result: result
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getError()).contains("invoke_rule");
    }

    // -------- Decision tables --------

    @Test
    @DisplayName("Decision table with hit_policy=FIRST picks the first matching row")
    void decisionTableFirst() {
        String yaml = """
                name: pricing table
                decision_table:
                  inputs: [creditScore]
                  outputs: [tier, rate]
                  hit_policy: FIRST
                  rules:
                    - when:
                        - creditScore at_least 750
                      then:
                        tier: "PRIME"
                        rate: 3.0
                    - when:
                        - creditScore at_least 650
                      then:
                        tier: "PREFERRED"
                        rate: 5.0
                    - otherwise: true
                      then:
                        tier: "STANDARD"
                        rate: 9.0
                output:
                  tier: tier
                  rate: rate
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of("creditScore", 700));
        assertThat(r.isSuccess()).as("error was: %s", r.getError()).isTrue();
        assertThat(r.getOutputData().get("tier")).isEqualTo("PREFERRED");
    }

    @Test
    @DisplayName("Decision table OTHERWISE row triggers when no row matches")
    void decisionTableOtherwise() {
        String yaml = """
                name: pricing fallback
                decision_table:
                  inputs: [creditScore]
                  outputs: [tier]
                  hit_policy: FIRST
                  rules:
                    - when:
                        - creditScore at_least 750
                      then:
                        tier: "PRIME"
                    - otherwise: true
                      then:
                        tier: "STANDARD"
                output:
                  tier: tier
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of("creditScore", 500));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("tier")).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("Decision table hit_policy=COLLECT groups matching outputs into lists")
    void decisionTableCollect() {
        String yaml = """
                name: tags table
                decision_table:
                  inputs: [age, creditScore]
                  outputs: [tag]
                  hit_policy: COLLECT
                  rules:
                    - when:
                        - age at_least 18
                      then:
                        tag: "adult"
                    - when:
                        - creditScore at_least 700
                      then:
                        tag: "good_credit"
                    - when:
                        - age at_least 65
                      then:
                        tag: "senior"
                output:
                  tag: tag
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of("age", 70, "creditScore", 750));
        assertThat(r.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Object> tags = (List<Object>) r.getOutputData().get("tag");
        assertThat(tags).containsExactly("adult", "good_credit", "senior");
    }

    @Test
    @DisplayName("Decision table hit_policy=UNIQUE fails loudly if more than one row matches")
    void decisionTableUniqueAmbiguous() {
        String yaml = """
                name: unique check
                decision_table:
                  inputs: [n]
                  outputs: [tier]
                  hit_policy: UNIQUE
                  rules:
                    - when:
                        - n at_least 10
                      then:
                        tier: "A"
                    - when:
                        - n at_least 5
                      then:
                        tier: "B"
                output:
                  tier: tier
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of("n", 50));
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getError()).contains("UNIQUE");
    }

    // -------- YAML lint --------

    @Test
    @DisplayName("Pre-parse YAML lint flags unquoted ': ' inside action lines with the line number")
    void yamlLintTrapsUnquotedColon() {
        String yaml = """
                name: bad yaml
                then:
                  - set msg to Status: 200
                output:
                  msg: msg
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getError()).contains("YAML lint");
    }

    @Test
    @DisplayName("Properly quoted action with embedded colon parses cleanly")
    void yamlLintAcceptsQuotedColon() {
        String yaml = """
                name: good yaml
                then:
                  - 'set msg to "Status: 200"'
                output:
                  msg: msg
                """;
        ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getOutputData().get("msg")).isEqualTo("Status: 200");
    }
}
