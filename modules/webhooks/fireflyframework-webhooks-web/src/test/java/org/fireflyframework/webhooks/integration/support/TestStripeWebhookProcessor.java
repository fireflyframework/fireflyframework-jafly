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

import org.fireflyframework.webhooks.processor.model.WebhookProcessingContext;
import org.fireflyframework.webhooks.processor.model.WebhookProcessingResult;
import org.fireflyframework.webhooks.processor.port.WebhookProcessor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test implementation of WebhookProcessor for Stripe webhooks.
 * <p>
 * This processor tracks all processed events for test verification.
 */
@Getter
public class TestStripeWebhookProcessor implements WebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(TestStripeWebhookProcessor.class);
    private static final String PROVIDER_NAME = "stripe";

    private final List<WebhookProcessingContext> processedEvents = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private CountDownLatch latch;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean canProcess(WebhookProcessingContext context) {
        return PROVIDER_NAME.equalsIgnoreCase(context.getProviderName());
    }

    @Override
    public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
        log.info("Processing Stripe webhook: eventId={}, type={}",
                context.getEventId(),
                context.getPayload().path("type").asText());

        processedEvents.add(context);
        int count = processedCount.incrementAndGet();

        // Count down the latch if set
        if (latch != null) {
            latch.countDown();
            log.debug("Latch count down: remaining={}", latch.getCount());
        }

        return Mono.just(WebhookProcessingResult.success("Processed Stripe webhook successfully"));
    }

    @Override
    public Mono<Void> beforeProcess(WebhookProcessingContext context) {
        log.debug("Before processing Stripe webhook: eventId={}", context.getEventId());
        return Mono.empty();
    }

    @Override
    public Mono<Void> afterProcess(WebhookProcessingContext context, WebhookProcessingResult result) {
        log.debug("After processing Stripe webhook: eventId={}, status={}",
                context.getEventId(), result.getStatus());
        return Mono.empty();
    }

    @Override
    public Mono<Void> onError(WebhookProcessingContext context, Throwable error) {
        log.error("Error processing Stripe webhook: eventId={}, error={}",
                context.getEventId(), error.getMessage(), error);
        return Mono.empty();
    }

    /**
     * Resets the processor state for a new test.
     */
    public void reset() {
        processedEvents.clear();
        processedCount.set(0);
        latch = null;
        log.debug("Test processor reset");
    }

    /**
     * Prepares to expect a certain number of events.
     * Must be called BEFORE sending the webhooks.
     *
     * @param expectedCount the number of events to expect
     */
    public void expectEvents(int expectedCount) {
        latch = new CountDownLatch(expectedCount);
        log.debug("Expecting {} events", expectedCount);
    }

    /**
     * Waits for the expected number of events to be processed.
     * Must be called AFTER expectEvents() and sending the webhooks.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return true if the expected count was reached, false if timeout
     */
    public boolean awaitProcessing(long timeout, TimeUnit unit) throws InterruptedException {
        if (latch == null) {
            throw new IllegalStateException("Must call expectEvents() before awaitProcessing()");
        }
        log.debug("Waiting for events with timeout {} {}", timeout, unit);
        boolean result = latch.await(timeout, unit);
        log.debug("Wait completed: result={}, processedCount={}", result, processedCount.get());
        return result;
    }

    /**
     * Gets the last processed event.
     *
     * @return the last processed event, or null if none
     */
    public WebhookProcessingContext getLastProcessedEvent() {
        return processedEvents.isEmpty() ? null : processedEvents.get(processedEvents.size() - 1);
    }

    /**
     * Gets the current processed count.
     *
     * @return the number of processed events
     */
    public int getProcessedCount() {
        return processedCount.get();
    }
}

