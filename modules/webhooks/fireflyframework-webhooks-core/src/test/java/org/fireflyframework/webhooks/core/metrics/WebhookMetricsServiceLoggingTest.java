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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class WebhookMetricsServiceLoggingTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WebhookMetricsServiceImpl service = new WebhookMetricsServiceImpl(registry);
    private final Logger logger = (Logger) LoggerFactory.getLogger(WebhookMetricsServiceImpl.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;
    private boolean previousAdditivity;

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        previousAdditivity = logger.isAdditive();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditivity);
        registry.close();
    }

    @Test
    void providerDiagnosticsStaySingleLineWhileRealMetricLabelsRemainUnchanged() {
        String provider = "sandbox\r\nprovider";
        recordAll(provider);
        assertEquals(6, events.list.size());
        for (var event : events.list) {
            assertEquals(Level.DEBUG, event.getLevel());
            assertNull(event.getThrowableProxy());
            assertTrue(event.getFormattedMessage().contains("sandbox\\r\\nprovider"));
            assertFalse(event.getFormattedMessage().contains("\r"));
            assertFalse(event.getFormattedMessage().contains("\n"));
        }
        assertMetrics(provider);
    }

    @Test
    void errorAndRejectionDiagnosticsEscapeTheirContextLabels() {
        service.recordWebhookFailed("sandbox", "validation\u2028error");
        service.recordWebhookRejected("sandbox", "rejected\u0085reason");
        assertEquals("Recorded webhook failure for provider: sandbox, error: validation\\u2028error",
                events.list.get(0).getFormattedMessage());
        assertEquals("Recorded webhook rejection for provider: sandbox, reason: rejected\\u0085reason",
                events.list.get(1).getFormattedMessage());
        assertEquals(1, registry.get("firefly.webhooks.failed").tags("provider", "sandbox", "error.type", "validation\u2028error").counter().count());
        assertEquals(1, registry.get("firefly.webhooks.rejected").tags("provider", "sandbox", "reason", "rejected\u0085reason").counter().count());
    }

    @Test
    void ordinaryAndNullLabelsRetainDiagnosticTextAndLevel() {
        service.recordWebhookReceived("sandbox");
        new WebhookMetricsServiceImpl(null).recordWebhookFailed(null, null);
        assertEquals("Recorded webhook received for provider: sandbox", events.list.get(0).getFormattedMessage());
        assertEquals("Recorded webhook failure for provider: null, error: null", events.list.get(1).getFormattedMessage());
        assertTrue(events.list.stream().allMatch(event -> event.getLevel() == Level.DEBUG));
    }

    @Test
    void disabledDiagnosticsPreserveCountersTimerAndPayloadSummary() {
        logger.setLevel(Level.OFF);
        recordAll("sandbox");
        assertTrue(events.list.isEmpty());
        assertMetrics("sandbox");
    }

    private void recordAll(String provider) {
        service.recordWebhookReceived(provider);
        service.recordWebhookPublished(provider);
        service.recordWebhookFailed(provider, "validation");
        service.recordWebhookRejected(provider, "rejected");
        service.recordDuplicateWebhook(provider);
        service.recordProcessingTime(provider, Instant.now().minusMillis(20));
        service.recordPayloadSize(provider, 123);
    }

    private void assertMetrics(String provider) {
        for (String name : new String[] {"received", "published", "failed", "rejected", "duplicates"}) {
            assertEquals(1, registry.get("firefly.webhooks." + name).tag("provider", provider).counter().count());
        }
        assertEquals(1, registry.get("firefly.webhooks.processing.time").tag("provider", provider).timer().count());
        var payload = registry.get("firefly.webhooks.payload.size").tag("provider", provider).summary();
        assertEquals(1, payload.count());
        assertEquals(123, payload.totalAmount());
    }
}
