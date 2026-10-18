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

package org.fireflyframework.webhooks.integration.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.eda.annotation.EventListener;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.event.EventEnvelope;
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.processor.listener.AbstractWebhookEventListener;
import org.fireflyframework.webhooks.processor.port.WebhookIdempotencyService;
import org.fireflyframework.webhooks.processor.port.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Test webhook listener for Stripe webhooks.
 * <p>
 * This listener uses lib-common-eda's @EventListener annotation to consume
 * webhook events from Kafka and process them using the test processor.
 */
public class TestStripeWebhookListener extends AbstractWebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(TestStripeWebhookListener.class);
    private static final String STRIPE_SECRET = "whsec_test_secret_key_12345";

    public TestStripeWebhookListener(
            TestStripeWebhookProcessor processor,
            ObjectMapper objectMapper,
            WebhookSignatureValidator signatureValidator,
            WebhookIdempotencyService idempotencyService) {
        super(processor, objectMapper, signatureValidator, idempotencyService);
        log.info("Initialized TestStripeWebhookListener with signature validation and idempotency");
    }

    /**
     * Handles Stripe webhook events from Kafka.
     * <p>
     * Uses lib-common-eda's @EventListener annotation to consume from the "stripe" topic.
     */
    @EventListener(
            destinations = "stripe",
            groupId = "stripe-webhook-test-processor",
            consumerType = PublisherType.KAFKA,
            autoAck = true,
            async = true
    )
    public Mono<Void> onStripeWebhook(EventEnvelope envelope) {
        log.debug("Received Stripe webhook event from Kafka: {}", envelope);

        // Extract the WebhookReceivedEvent from the envelope
        Object payload = envelope.payload();

        if (!(payload instanceof WebhookReceivedEvent)) {
            log.error("Unexpected payload type: {}", payload.getClass().getName());
            return Mono.error(new IllegalArgumentException("Expected WebhookReceivedEvent but got: " + payload.getClass().getName()));
        }

        WebhookReceivedEvent event = (WebhookReceivedEvent) payload;

        // Delegate to the abstract handler which performs:
        // 1. Idempotency check
        // 2. Signature validation
        // 3. Business logic processing
        return handleWebhookEvent(event)
                .doOnSuccess(v -> log.debug("Successfully processed Stripe webhook: eventId={}", event.getEventId()))
                .doOnError(error -> log.error("Error processing Stripe webhook: eventId={}, error={}",
                        event.getEventId(), error.getMessage(), error));
    }

    @Override
    protected String getWebhookSecret(String providerName) {
        // Return the test secret for Stripe
        if ("stripe".equalsIgnoreCase(providerName)) {
            return STRIPE_SECRET;
        }
        return null;
    }
}

