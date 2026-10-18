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

package org.fireflyframework.data.integration;

import org.fireflyframework.data.quality.DataQualityEngine;
import org.fireflyframework.data.quality.DataQualityRule;
import org.fireflyframework.data.quality.QualitySeverity;
import org.fireflyframework.data.quality.QualityStrategy;
import org.fireflyframework.data.quality.rules.NotNullRule;
import org.fireflyframework.data.quality.rules.PatternRule;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for quality gates using real rule implementations
 * with different evaluation strategies.
 */
class QualityGateIntegrationTest {

    record CustomerRecord(String name, String email, String phone, String taxId) {}

    @Test
    void qualityGate_allRulesPass_reportsPassed() {
        // Given - valid data that satisfies all rules
        DataQualityRule<CustomerRecord> nameRule = new NotNullRule<>(
                "name", CustomerRecord::name, QualitySeverity.CRITICAL);
        DataQualityRule<CustomerRecord> emailRule = new PatternRule<>(
                "email", Pattern.compile(".+@.+\\..+"), CustomerRecord::email, QualitySeverity.CRITICAL);
        DataQualityRule<CustomerRecord> phoneRule = new PatternRule<>(
                "phone", Pattern.compile("\\+?\\d[\\d\\-]{7,14}"), CustomerRecord::phone, QualitySeverity.WARNING);
        DataQualityRule<CustomerRecord> taxIdRule = new NotNullRule<>(
                "taxId", CustomerRecord::taxId, QualitySeverity.CRITICAL);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule, emailRule, phoneRule, taxIdRule));
        CustomerRecord validRecord = new CustomerRecord("Alice Smith", "alice@example.com", "+1234567890", "TX-12345");

        // When & Then
        StepVerifier.create(engine.evaluate(validRecord, QualityStrategy.COLLECT_ALL))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isTrue();
                    assertThat(report.getTotalRules()).isEqualTo(4);
                    assertThat(report.getPassedRules()).isEqualTo(4);
                    assertThat(report.getFailedRules()).isEqualTo(0);
                    assertThat(report.getFailures()).isEmpty();
                    assertThat(report.getTimestamp()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void qualityGate_failFast_stopsOnFirstCritical() {
        // Given - first rule (CRITICAL) will fail, second rule (CRITICAL) should not be evaluated
        DataQualityRule<CustomerRecord> nameRule = new NotNullRule<>(
                "name", CustomerRecord::name, QualitySeverity.CRITICAL);
        DataQualityRule<CustomerRecord> emailRule = new PatternRule<>(
                "email", Pattern.compile(".+@.+\\..+"), CustomerRecord::email, QualitySeverity.CRITICAL);
        DataQualityRule<CustomerRecord> phoneRule = new PatternRule<>(
                "phone", Pattern.compile("\\+?\\d[\\d\\-]{7,14}"), CustomerRecord::phone, QualitySeverity.WARNING);
        DataQualityRule<CustomerRecord> taxIdRule = new NotNullRule<>(
                "taxId", CustomerRecord::taxId, QualitySeverity.CRITICAL);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule, emailRule, phoneRule, taxIdRule));

        // Name is null (CRITICAL fail), email is invalid, phone is invalid, taxId is null
        CustomerRecord invalidRecord = new CustomerRecord(null, "not-an-email", "abc", null);

        // When & Then - FAIL_FAST stops at first CRITICAL failure
        StepVerifier.create(engine.evaluate(invalidRecord, QualityStrategy.FAIL_FAST))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isFalse();
                    // Should have only evaluated the first rule (which is CRITICAL and failed)
                    assertThat(report.getTotalRules()).isEqualTo(1);
                    assertThat(report.getFailedRules()).isEqualTo(1);
                    assertThat(report.getPassedRules()).isEqualTo(0);

                    // The single failure should be for the "name" field
                    assertThat(report.getFailures()).hasSize(1);
                    assertThat(report.getFailures().get(0).getRuleName()).isEqualTo("not-null:name");
                    assertThat(report.getFailures().get(0).getSeverity()).isEqualTo(QualitySeverity.CRITICAL);
                })
                .verifyComplete();
    }

    @Test
    void qualityGate_collectAll_reportsAllFailures() {
        // Given - multiple rules that will fail
        DataQualityRule<CustomerRecord> nameRule = new NotNullRule<>(
                "name", CustomerRecord::name, QualitySeverity.CRITICAL);
        DataQualityRule<CustomerRecord> emailRule = new PatternRule<>(
                "email", Pattern.compile(".+@.+\\..+"), CustomerRecord::email, QualitySeverity.CRITICAL);
        DataQualityRule<CustomerRecord> phoneRule = new PatternRule<>(
                "phone", Pattern.compile("\\+?\\d[\\d\\-]{7,14}"), CustomerRecord::phone, QualitySeverity.WARNING);
        DataQualityRule<CustomerRecord> taxIdRule = new NotNullRule<>(
                "taxId", CustomerRecord::taxId, QualitySeverity.CRITICAL);

        DataQualityEngine engine = new DataQualityEngine(List.of(nameRule, emailRule, phoneRule, taxIdRule));

        // All fields invalid: name null, email invalid format, phone letters, taxId null
        CustomerRecord invalidRecord = new CustomerRecord(null, "not-an-email", "abc", null);

        // When & Then - COLLECT_ALL evaluates every rule
        StepVerifier.create(engine.evaluate(invalidRecord, QualityStrategy.COLLECT_ALL))
                .assertNext(report -> {
                    assertThat(report.isPassed()).isFalse();
                    // All 4 rules should have been evaluated
                    assertThat(report.getTotalRules()).isEqualTo(4);
                    // All 4 should fail
                    assertThat(report.getFailedRules()).isEqualTo(4);
                    assertThat(report.getPassedRules()).isEqualTo(0);

                    // Check that all failures are reported
                    assertThat(report.getFailures()).hasSize(4);

                    // Verify by severity
                    assertThat(report.getBySeverity(QualitySeverity.CRITICAL))
                            .hasSize(3); // name, email, taxId
                    assertThat(report.getBySeverity(QualitySeverity.WARNING))
                            .hasSize(1); // phone

                    // Verify specific rule names
                    List<String> failedRuleNames = report.getFailures().stream()
                            .map(r -> r.getRuleName())
                            .toList();
                    assertThat(failedRuleNames).containsExactlyInAnyOrder(
                            "not-null:name",
                            "pattern:email",
                            "pattern:phone",
                            "not-null:taxId"
                    );
                })
                .verifyComplete();
    }
}
