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

package org.fireflyframework.webhooks.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service for recording webhook metrics via {@link FireflyMetricsSupport}.
 * All metrics are prefixed with {@code firefly.webhooks.*}.
 */
@Service
@Slf4j
public class WebhookMetricsService extends FireflyMetricsSupport {

    public WebhookMetricsService(MeterRegistry meterRegistry) {
        super(meterRegistry, "webhooks");
    }

    public void recordWebhookReceived(String providerName) {
        counter("received", "provider", providerName).increment();
        log.debug("Recorded webhook received for provider: {}", providerName);
    }

    public void recordWebhookPublished(String providerName) {
        counter("published", "provider", providerName).increment();
        log.debug("Recorded webhook published for provider: {}", providerName);
    }

    public void recordWebhookFailed(String providerName, String errorType) {
        counter("failed", "provider", providerName, "error.type", errorType).increment();
        log.debug("Recorded webhook failure for provider: {}, error: {}", providerName, errorType);
    }

    public void recordWebhookRejected(String providerName, String reason) {
        counter("rejected", "provider", providerName, "reason", reason).increment();
        log.debug("Recorded webhook rejection for provider: {}, reason: {}", providerName, reason);
    }

    public void recordDuplicateWebhook(String providerName) {
        counter("duplicates", "provider", providerName).increment();
        log.debug("Recorded duplicate webhook for provider: {}", providerName);
    }

    public void recordProcessingTime(String providerName, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());
        timer("processing.time", "provider", providerName).record(duration);
        log.debug("Recorded processing time for provider: {} - {}ms", providerName, duration.toMillis());
    }

    public void recordPayloadSize(String providerName, long sizeBytes) {
        distributionSummary("payload.size", "provider", providerName).record(sizeBytes);
    }
}
