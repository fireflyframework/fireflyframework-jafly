/*
 * Copyright 2024-2026 Firefly Software Foundation.
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

package org.fireflyframework.webhooks.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookMetricsService Tests")
class WebhookMetricsServiceTest {

    private WebhookMetricsService metricsService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new WebhookMetricsService(meterRegistry);
    }

    // ========================================
    // Webhook Received Metrics Tests
    // ========================================

    @Test
    @DisplayName("Should record webhook received metric")
    void shouldRecordWebhookReceivedMetric() {
        String providerName = "stripe";

        metricsService.recordWebhookReceived(providerName);

        Counter counter = meterRegistry.find("firefly.webhooks.received")
                .tag("provider", providerName)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment webhook received counter on multiple calls")
    void shouldIncrementWebhookReceivedCounterOnMultipleCalls() {
        String providerName = "github";

        metricsService.recordWebhookReceived(providerName);
        metricsService.recordWebhookReceived(providerName);
        metricsService.recordWebhookReceived(providerName);

        Counter counter = meterRegistry.find("firefly.webhooks.received")
                .tag("provider", providerName)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Should use separate counters for different providers")
    void shouldUseSeparateCountersForDifferentProviders() {
        metricsService.recordWebhookReceived("stripe");
        metricsService.recordWebhookReceived("stripe");
        metricsService.recordWebhookReceived("github");

        Counter stripeCounter = meterRegistry.find("firefly.webhooks.received")
                .tag("provider", "stripe")
                .counter();

        Counter githubCounter = meterRegistry.find("firefly.webhooks.received")
                .tag("provider", "github")
                .counter();

        assertThat(stripeCounter).isNotNull();
        assertThat(stripeCounter.count()).isEqualTo(2.0);

        assertThat(githubCounter).isNotNull();
        assertThat(githubCounter.count()).isEqualTo(1.0);
    }

    // ========================================
    // Webhook Published Metrics Tests
    // ========================================

    @Test
    @DisplayName("Should record webhook published metric")
    void shouldRecordWebhookPublishedMetric() {
        String providerName = "paypal";

        metricsService.recordWebhookPublished(providerName);

        Counter counter = meterRegistry.find("firefly.webhooks.published")
                .tag("provider", providerName)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should increment webhook published counter on multiple calls")
    void shouldIncrementWebhookPublishedCounterOnMultipleCalls() {
        String providerName = "shopify";

        metricsService.recordWebhookPublished(providerName);
        metricsService.recordWebhookPublished(providerName);
        metricsService.recordWebhookPublished(providerName);
        metricsService.recordWebhookPublished(providerName);

        Counter counter = meterRegistry.find("firefly.webhooks.published")
                .tag("provider", providerName)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(4.0);
    }

    // ========================================
    // Processing Time Metrics Tests
    // ========================================

    @Test
    @DisplayName("Should record processing time metric")
    void shouldRecordProcessingTimeMetric() throws InterruptedException {
        String providerName = "twilio";
        Instant startTime = Instant.now().minusMillis(150);

        metricsService.recordProcessingTime(providerName, startTime);

        Timer timer = meterRegistry.find("firefly.webhooks.processing.time")
                .tag("provider", providerName)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        // Processing time should be at least 150ms
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(150);
    }

    @Test
    @DisplayName("Should record multiple processing times and calculate statistics")
    void shouldRecordMultipleProcessingTimesAndCalculateStatistics() {
        String providerName = "slack";

        metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(100));
        metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(200));
        metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(300));

        Timer timer = meterRegistry.find("firefly.webhooks.processing.time")
                .tag("provider", providerName)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(3);
        // Total time should be at least 600ms (100 + 200 + 300)
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(600);
    }

    @Test
    @DisplayName("Should use separate timers for different providers")
    void shouldUseSeparateTimersForDifferentProviders() {
        metricsService.recordProcessingTime("stripe", Instant.now().minusMillis(100));
        metricsService.recordProcessingTime("stripe", Instant.now().minusMillis(200));
        metricsService.recordProcessingTime("github", Instant.now().minusMillis(50));

        Timer stripeTimer = meterRegistry.find("firefly.webhooks.processing.time")
                .tag("provider", "stripe")
                .timer();

        Timer githubTimer = meterRegistry.find("firefly.webhooks.processing.time")
                .tag("provider", "github")
                .timer();

        assertThat(stripeTimer).isNotNull();
        assertThat(stripeTimer.count()).isEqualTo(2);
        assertThat(stripeTimer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(300);

        assertThat(githubTimer).isNotNull();
        assertThat(githubTimer.count()).isEqualTo(1);
        assertThat(githubTimer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(50);
    }

    // ========================================
    // Integration Tests
    // ========================================

    @Test
    @DisplayName("Should track complete webhook lifecycle metrics")
    void shouldTrackCompleteWebhookLifecycleMetrics() {
        String providerName = "complete-test";

        // Simulate complete webhook lifecycle
        metricsService.recordWebhookReceived(providerName);
        metricsService.recordProcessingTime(providerName, Instant.now().minusMillis(250));
        metricsService.recordWebhookPublished(providerName);

        // Verify all metrics are recorded
        Counter receivedCounter = meterRegistry.find("firefly.webhooks.received")
                .tag("provider", providerName)
                .counter();

        Counter publishedCounter = meterRegistry.find("firefly.webhooks.published")
                .tag("provider", providerName)
                .counter();

        Timer processingTimer = meterRegistry.find("firefly.webhooks.processing.time")
                .tag("provider", providerName)
                .timer();

        assertThat(receivedCounter).isNotNull();
        assertThat(receivedCounter.count()).isEqualTo(1.0);

        assertThat(publishedCounter).isNotNull();
        assertThat(publishedCounter.count()).isEqualTo(1.0);

        assertThat(processingTimer).isNotNull();
        assertThat(processingTimer.count()).isEqualTo(1);
        assertThat(processingTimer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(250);
    }

    @Test
    @DisplayName("Should handle multiple concurrent providers")
    void shouldHandleMultipleConcurrentProviders() {
        // Simulate multiple providers processing webhooks
        metricsService.recordWebhookReceived("stripe");
        metricsService.recordWebhookReceived("github");
        metricsService.recordWebhookReceived("paypal");

        metricsService.recordProcessingTime("stripe", Instant.now().minusMillis(100));
        metricsService.recordProcessingTime("github", Instant.now().minusMillis(150));
        metricsService.recordProcessingTime("paypal", Instant.now().minusMillis(200));

        metricsService.recordWebhookPublished("stripe");
        metricsService.recordWebhookPublished("github");
        metricsService.recordWebhookPublished("paypal");

        // Verify all providers have their own metrics
        assertThat(meterRegistry.find("firefly.webhooks.received").tag("provider", "stripe").counter()).isNotNull();
        assertThat(meterRegistry.find("firefly.webhooks.received").tag("provider", "github").counter()).isNotNull();
        assertThat(meterRegistry.find("firefly.webhooks.received").tag("provider", "paypal").counter()).isNotNull();

        assertThat(meterRegistry.find("firefly.webhooks.published").tag("provider", "stripe").counter()).isNotNull();
        assertThat(meterRegistry.find("firefly.webhooks.published").tag("provider", "github").counter()).isNotNull();
        assertThat(meterRegistry.find("firefly.webhooks.published").tag("provider", "paypal").counter()).isNotNull();

        assertThat(meterRegistry.find("firefly.webhooks.processing.time").tag("provider", "stripe").timer()).isNotNull();
        assertThat(meterRegistry.find("firefly.webhooks.processing.time").tag("provider", "github").timer()).isNotNull();
        assertThat(meterRegistry.find("firefly.webhooks.processing.time").tag("provider", "paypal").timer()).isNotNull();
    }
}

