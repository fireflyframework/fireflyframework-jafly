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

package org.fireflyframework.data.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link EnrichmentCostTracker}.
 */
class EnrichmentCostTrackerTest {

    private EnrichmentCostTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new EnrichmentCostTracker();
    }

    @Test
    void registerAndRecordCalls_shouldTrackCosts() {
        // Given
        tracker.registerProvider("Financial Data Provider", 0.05, "USD");
        tracker.registerProvider("Credit Bureau Provider", 1.50, "USD");

        // When
        tracker.recordCall("Financial Data Provider");
        tracker.recordCall("Financial Data Provider");
        tracker.recordCall("Financial Data Provider");
        tracker.recordCall("Credit Bureau Provider");
        tracker.recordCall("Credit Bureau Provider");

        CostReport report = tracker.getReport();

        // Then
        assertThat(report.getProviderCosts()).hasSize(2);
        assertThat(report.getCurrency()).isEqualTo("USD");
        assertThat(report.getGeneratedAt()).isNotNull();

        CostReport.ProviderCost financialCost = report.getProviderCosts().get("Financial Data Provider");
        assertThat(financialCost.getCallCount()).isEqualTo(3);
        assertThat(financialCost.getCostPerCall()).isCloseTo(0.05, within(0.001));
        assertThat(financialCost.getTotalCost()).isCloseTo(0.15, within(0.001));

        CostReport.ProviderCost creditCost = report.getProviderCosts().get("Credit Bureau Provider");
        assertThat(creditCost.getCallCount()).isEqualTo(2);
        assertThat(creditCost.getCostPerCall()).isCloseTo(1.50, within(0.001));
        assertThat(creditCost.getTotalCost()).isCloseTo(3.00, within(0.001));

        assertThat(report.getTotalCost()).isCloseTo(3.15, within(0.001));
    }

    @Test
    void getReport_shouldReturnEmptyWhenNoCalls() {
        // When
        CostReport report = tracker.getReport();

        // Then
        assertThat(report.getProviderCosts()).isEmpty();
        assertThat(report.getTotalCost()).isEqualTo(0.0);
        assertThat(report.getCurrency()).isEqualTo("USD");
        assertThat(report.getGeneratedAt()).isNotNull();
    }

    @Test
    void recordCall_shouldIncrementCount() {
        // Given
        tracker.registerProvider("Test Provider", 0.10, "EUR");

        // When
        for (int i = 0; i < 10; i++) {
            tracker.recordCall("Test Provider");
        }

        CostReport report = tracker.getReport();

        // Then
        CostReport.ProviderCost cost = report.getProviderCosts().get("Test Provider");
        assertThat(cost.getCallCount()).isEqualTo(10);
        assertThat(cost.getTotalCost()).isCloseTo(1.00, within(0.001));
        assertThat(tracker.getTotalCost()).isCloseTo(1.00, within(0.001));
    }
}
