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

/**
 * End-to-end domain scenario tests covering rule shapes that the loan-eligibility
 * {@code EndToEndScenarioTest} doesn't exercise. The point of having these alongside
 * the loan test is breadth -- each domain stresses a different combination of DSL
 * features and operator types, so a regression that breaks one shape but not another
 * still surfaces at build time.
 */
class E2EDomainScenariosTest {

    private ASTRulesEvaluationEngine engine;
    private CustomFunctionRegistry registry;

    @BeforeEach
    void setUp() {
        DSLParser dslParser = new DSLParser();
        ASTRulesDSLParser parser = new ASTRulesDSLParser(dslParser);
        ConstantService constantService = Mockito.mock(ConstantService.class);
        Mockito.when(constantService.getConstantsByCodes(Mockito.anyList())).thenReturn(Flux.empty());
        registry = new CustomFunctionRegistry();
        engine = new ASTRulesEvaluationEngine(parser, constantService, null, null, registry);
    }

    // ---------------------------------------------------------------------------------
    // Insurance pricing: exercises tiered conditionals, multiplicative arithmetic,
    // mixed input-types (numeric + categorical), and the symmetric arithmetic grammar.
    // ---------------------------------------------------------------------------------

    private static final String INSURANCE_PRICING_RULE = """
            name: "Auto Insurance Premium"
            description: "Computes annual premium from driver and vehicle attributes"

            inputs:
              driverAge: "number"
              yearsLicensed: "number"
              vehicleValue: "number"
              accidentsInLastFiveYears: "number"
              region: "string"

            then:
              # Base premium: 4% of vehicle value
              - calculate base_premium as vehicleValue * 0.04

              # Age band multiplier
              - run age_multiplier as if_else(driverAge less_than 25, 1.6, if_else(driverAge at_least 65, 1.3, 1.0))

              # Experience discount: cap at 20% off for >= 10 years licensed
              - run experience_discount as if_else(yearsLicensed at_least 10, 0.2, yearsLicensed * 0.02)

              # Accident surcharge: 15% per incident, max 90%
              - run accident_surcharge as if_else(accidentsInLastFiveYears at_least 6, 0.9, accidentsInLastFiveYears * 0.15)

              # Region adjustment: high-risk urban areas pay more
              - set region_factor to 1.0
              - if region in_list ["NYC", "LA", "Chicago"] then set region_factor to 1.25

              # Final premium: base × age × region × (1 - discount + surcharge)
              - calculate final_premium as base_premium * age_multiplier * region_factor * (1 - experience_discount + accident_surcharge)

              # Banding for the rate card
              - run rate_tier as if_else(final_premium at_least 3000, "HIGH",
                                  if_else(final_premium at_least 1500, "STANDARD", "PREFERRED"))

            output:
              base_premium: number
              final_premium: number
              rate_tier: text
              age_multiplier: number
              region_factor: number
            """;

    @Test
    @DisplayName("Insurance pricing: young driver in NYC with one accident pays high tier")
    void insurancePricingYoungDriverWithAccident() {
        Map<String, Object> input = new HashMap<>();
        input.put("driverAge", 22);
        input.put("yearsLicensed", 4);
        input.put("vehicleValue", new BigDecimal("45000"));
        input.put("accidentsInLastFiveYears", 1);
        input.put("region", "NYC");

        ASTRulesEvaluationResult result = engine.evaluateRules(INSURANCE_PRICING_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        // base_premium = 45000 * 0.04 = 1800
        // age_multiplier 1.6 (young) × region 1.25 (NYC) × (1 - 0.08 discount + 0.15 surcharge) = 1.07
        // final_premium = 1800 * 1.6 * 1.25 * 1.07 = 3852 → HIGH tier (≥ 3000)
        assertThat(new BigDecimal(out.get("base_premium").toString()))
                .isEqualByComparingTo(new BigDecimal("1800.00"));
        assertThat(new BigDecimal(out.get("age_multiplier").toString())).isEqualByComparingTo("1.6");
        assertThat(new BigDecimal(out.get("region_factor").toString())).isEqualByComparingTo("1.25");
        assertThat((String) out.get("rate_tier")).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Insurance pricing: experienced suburban driver with clean record lands in preferred tier")
    void insurancePricingExperiencedCleanRecord() {
        Map<String, Object> input = new HashMap<>();
        input.put("driverAge", 42);
        input.put("yearsLicensed", 20);
        input.put("vehicleValue", new BigDecimal("18000"));
        input.put("accidentsInLastFiveYears", 0);
        input.put("region", "Suburb");

        ASTRulesEvaluationResult result = engine.evaluateRules(INSURANCE_PRICING_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> out = result.getOutputData();
        // age 42: multiplier 1.0; suburb: region 1.0; 20 years licensed: 20% discount; 0 accidents: 0 surcharge
        // base = 18000 * 0.04 = 720; final = 720 * 1.0 * 1.0 * (1 - 0.2 + 0) = 576
        assertThat(new BigDecimal(out.get("final_premium").toString()))
                .isEqualByComparingTo("576.00000");
        assertThat((String) out.get("rate_tier")).isEqualTo("PREFERRED");
    }

    // ---------------------------------------------------------------------------------
    // Fraud-risk scoring with a custom function (exercises the CustomFunctionRegistry
    // path end-to-end alongside the built-in operators).
    // ---------------------------------------------------------------------------------

    private static final String FRAUD_RISK_RULE = """
            name: "Card Transaction Fraud Risk"
            description: "Scores a card-not-present transaction for fraud risk"

            inputs:
              amount: "number"
              hoursSinceLastTx: "number"
              merchantCategory: "string"
              countryDistanceKm: "number"
              isVerifiedDevice: "boolean"

            when:
              - amount at_least 0

            then:
              - set risk_score to 0

              # Amount band
              - if amount at_least 5000 then add 35 to risk_score
              - if amount at_least 1000 and amount less_than 5000 then add 15 to risk_score
              - if amount less_than 1000 then add 5 to risk_score

              # Velocity (transactions clustered in time look suspicious)
              - if hoursSinceLastTx less_than 1 then add 20 to risk_score

              # Geographic anomaly via a custom merchant_country_risk function
              - run country_risk as merchant_country_risk(countryDistanceKm)
              - add country_risk to risk_score

              # High-risk merchant categories
              - if merchantCategory in_list ["gambling", "crypto-exchange", "wire-transfer"] then add 25 to risk_score

              # Device verification: significant discount
              - if isVerifiedDevice equals true then subtract 20 from risk_score
              - if risk_score less_than 0 then set risk_score to 0

              # Decision banding
              - run decision as if_else(risk_score at_least 70, "BLOCK",
                                if_else(risk_score at_least 40, "STEP_UP_AUTH", "ALLOW"))

            output:
              risk_score: number
              country_risk: number
              decision: text
            """;

    @Test
    @DisplayName("Fraud: large cross-border crypto purchase from unverified device → BLOCK")
    void fraudHighRiskBlocks() {
        // Custom risk function: ramps from 0 at 0km to 30 at 5000km
        registry.register("merchant_country_risk", args -> {
            double km = ((Number) args[0]).doubleValue();
            return BigDecimal.valueOf(Math.min(30, km / 5000.0 * 30));
        });

        Map<String, Object> input = new HashMap<>();
        input.put("amount", new BigDecimal("7500"));
        input.put("hoursSinceLastTx", new BigDecimal("0.5"));
        input.put("merchantCategory", "crypto-exchange");
        input.put("countryDistanceKm", new BigDecimal("4500"));
        input.put("isVerifiedDevice", false);

        ASTRulesEvaluationResult result = engine.evaluateRules(FRAUD_RISK_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        // 35 (amount) + 20 (velocity) + ~27 (country risk 4500/5000*30) + 25 (category) = ~107
        assertThat(new BigDecimal(result.getOutputData().get("risk_score").toString()))
                .isGreaterThan(BigDecimal.valueOf(70));
        assertThat((String) result.getOutputData().get("decision")).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("Fraud: small local purchase from verified device → ALLOW")
    void fraudLowRiskAllows() {
        registry.register("merchant_country_risk", args -> {
            double km = ((Number) args[0]).doubleValue();
            return BigDecimal.valueOf(Math.min(30, km / 5000.0 * 30));
        });

        Map<String, Object> input = new HashMap<>();
        input.put("amount", new BigDecimal("45"));
        input.put("hoursSinceLastTx", new BigDecimal("8"));
        input.put("merchantCategory", "grocery");
        input.put("countryDistanceKm", new BigDecimal("3"));
        input.put("isVerifiedDevice", true);

        ASTRulesEvaluationResult result = engine.evaluateRules(FRAUD_RISK_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        // 5 (amount) + 0 (velocity) + ~0 (country) + 0 (category) - 20 (verified) = clamped to 0
        assertThat(new BigDecimal(result.getOutputData().get("risk_score").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((String) result.getOutputData().get("decision")).isEqualTo("ALLOW");
    }

    // ---------------------------------------------------------------------------------
    // Compliance gate: exercises `else` actions, validation operators in conditions,
    // and list-aggregation of reasons.
    // ---------------------------------------------------------------------------------

    private static final String COMPLIANCE_GATE_RULE = """
            name: "KYC / Compliance Gate"
            description: "Pre-onboarding compliance check"

            inputs:
              applicantEmail: "string"
              applicantPhone: "string"
              applicantAge: "number"
              countryOfResidence: "string"
              hasGovernmentId: "boolean"

            when:
              - applicantEmail is_email
              - applicantPhone is_phone
              - applicantAge at_least 18
              - hasGovernmentId equals true
              - countryOfResidence not_in_list ["KP", "IR", "SY", "CU"]

            then:
              - set kyc_status to "PASS"
              - set rejection_reasons to []

            else:
              - set kyc_status to "FAIL"
              - set rejection_reasons to []
              - if not applicantEmail is_email then append "INVALID_EMAIL" to rejection_reasons
              - if not applicantPhone is_phone then append "INVALID_PHONE" to rejection_reasons
              - if applicantAge less_than 18 then append "UNDERAGE" to rejection_reasons
              - if not hasGovernmentId equals true then append "MISSING_GOVERNMENT_ID" to rejection_reasons
              - if countryOfResidence in_list ["KP", "IR", "SY", "CU"] then append "SANCTIONED_COUNTRY" to rejection_reasons

            output:
              kyc_status: text
              rejection_reasons: list
            """;

    @Test
    @DisplayName("Compliance gate: well-formed applicant passes KYC")
    void compliancePass() {
        Map<String, Object> input = new HashMap<>();
        input.put("applicantEmail", "alice@example.com");
        input.put("applicantPhone", "+1-555-123-4567");
        input.put("applicantAge", 30);
        input.put("countryOfResidence", "GB");
        input.put("hasGovernmentId", true);

        ASTRulesEvaluationResult result = engine.evaluateRules(COMPLIANCE_GATE_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("kyc_status", "PASS");
        @SuppressWarnings("unchecked")
        List<Object> reasons = (List<Object>) result.getOutputData().get("rejection_reasons");
        assertThat(reasons).isEmpty();
    }

    @Test
    @DisplayName("Compliance gate: multiple violations are all collected in rejection_reasons")
    void complianceMultipleFailures() {
        Map<String, Object> input = new HashMap<>();
        input.put("applicantEmail", "not-an-email");
        input.put("applicantPhone", "garbage");
        input.put("applicantAge", 15);                  // underage
        input.put("countryOfResidence", "KP");          // sanctioned
        input.put("hasGovernmentId", false);

        ASTRulesEvaluationResult result = engine.evaluateRules(COMPLIANCE_GATE_RULE, input);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputData()).containsEntry("kyc_status", "FAIL");

        @SuppressWarnings("unchecked")
        List<Object> reasons = (List<Object>) result.getOutputData().get("rejection_reasons");
        assertThat(reasons).containsExactlyInAnyOrder(
                "INVALID_EMAIL", "INVALID_PHONE", "UNDERAGE", "MISSING_GOVERNMENT_ID", "SANCTIONED_COUNTRY");
    }
}
