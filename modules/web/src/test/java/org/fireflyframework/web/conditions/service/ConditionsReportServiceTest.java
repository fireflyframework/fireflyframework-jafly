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

package org.fireflyframework.web.conditions.service;

import org.fireflyframework.web.conditions.config.ConditionsReportProperties;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Condition;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ConditionsReportServiceTest {

    private static final Condition CONDITION = (context, metadata) -> true;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportsMatchedAndUnmatchedTotalsAcrossCategories(boolean summaryOnly, CapturedOutput output) {
        ConditionsReportProperties properties = properties(summaryOnly);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            recordConditions(context);

            new ConditionsReportService(properties).generateReport(context);

            assertThat(output.getOut()).contains(summaryOnly
                    ? "TOTAL: 2 active, 2 inactive"
                    : "SUMMARY: 2 configurations active, 2 inactive");
            if (summaryOnly) {
                assertThat(output.getOut()).containsPattern("Firefly Custom +1 active \\| +1 inactive");
                assertThat(output.getOut()).containsPattern("Web & HTTP +1 active");
                assertThat(output.getOut()).containsPattern("Databases & Data Access +0 active \\| +1 inactive");
            } else {
                assertThat(output.getOut()).contains(
                        "FIREFLY CUSTOM (1 active, 1 inactive)",
                        "WEB & HTTP (1 active, 0 inactive)",
                        "DATABASES & DATA ACCESS (0 active, 1 inactive)");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void excludesHiddenNegativeConfigurationsFromTotals(boolean summaryOnly, CapturedOutput output) {
        ConditionsReportProperties properties = properties(summaryOnly);
        properties.setShowNegative(false);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            recordConditions(context);

            new ConditionsReportService(properties).generateReport(context);

            assertThat(output.getOut()).contains(summaryOnly
                    ? "TOTAL: 2 active, 0 inactive"
                    : "SUMMARY: 2 configurations active, 0 inactive");
            assertThat(output.getOut()).doesNotContain("Databases & Data Access", "DATABASES & DATA ACCESS", "[INACTIVE]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reportsZeroTotalsForAnEmptyReport(boolean summaryOnly, CapturedOutput output) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            new ConditionsReportService(properties(summaryOnly)).generateReport(context);

            assertThat(output.getOut()).contains(summaryOnly
                    ? "TOTAL: 0 active, 0 inactive"
                    : "SUMMARY: 0 configurations active, 0 inactive");
        }
    }

    private ConditionsReportProperties properties(boolean summaryOnly) {
        ConditionsReportProperties properties = new ConditionsReportProperties();
        properties.setSummaryOnly(summaryOnly);
        properties.setUseColors(false);
        properties.setShowDetails(false);
        return properties;
    }

    private void recordConditions(GenericApplicationContext context) {
        ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
        report.recordConditionEvaluation("org.fireflyframework.ActiveConfiguration", CONDITION, ConditionOutcome.match());
        report.recordConditionEvaluation("org.fireflyframework.InactiveConfiguration", CONDITION, ConditionOutcome.noMatch("disabled"));
        report.recordConditionEvaluation("org.example.web.WebConfiguration", CONDITION, ConditionOutcome.match());
        report.recordConditionEvaluation("org.example.data.JdbcConfiguration", CONDITION, ConditionOutcome.noMatch("disabled"));
    }
}
