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

package org.fireflyframework.webhooks.processor.port;

import org.fireflyframework.webhooks.processor.model.WebhookProcessingContext;
import org.fireflyframework.webhooks.processor.model.WebhookProcessingResult;
import reactor.core.publisher.Mono;

/**
 * Port interface for webhook event processors.
 * <p>
 * This interface defines the contract for processing webhook events consumed from Kafka.
 * Implementations should handle the specific business logic for their provider/domain.
 * <p>
 * <b>Example Implementation:</b>
 * <pre>
 * {@code
 * @Component
 * public class StripeWebhookProcessor implements WebhookProcessor {
 *     
 *     @Override
 *     public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
 *         String eventType = context.getPayload().path("type").asText();
 *         
 *         return switch (eventType) {
 *             case "payment_intent.succeeded" -> handlePaymentSuccess(context);
 *             case "payment_intent.failed" -> handlePaymentFailure(context);
 *             default -> Mono.just(WebhookProcessingResult.skipped("Unknown event type: " + eventType));
 *         };
 *     }
 *     
 *     @Override
 *     public String getProviderName() {
 *         return "stripe";
 *     }
 *     
 *     @Override
 *     public boolean canProcess(WebhookProcessingContext context) {
 *         return context.getProviderName().equals("stripe");
 *     }
 * }
 * }
 * </pre>
 */
public interface WebhookProcessor {

    /**
     * Processes a webhook event and returns the processing result.
     * <p>
     * Implementations should:
     * <ul>
     *   <li>Parse the webhook payload</li>
     *   <li>Validate the event (optionally verify signatures)</li>
     *   <li>Execute business logic</li>
     *   <li>Return appropriate result (success, failure, retry, skip)</li>
     * </ul>
     *
     * @param context the webhook processing context containing event data
     * @return a Mono emitting the processing result
     */
    Mono<WebhookProcessingResult> process(WebhookProcessingContext context);

    /**
     * Gets the provider name this processor handles (e.g., "stripe", "shopify", "twilio").
     * <p>
     * This is used to route webhook events to the appropriate processor.
     *
     * @return the provider name
     */
    String getProviderName();

    /**
     * Determines if this processor can handle the given webhook context.
     * <p>
     * Default implementation checks if the provider name matches.
     * Override for more complex routing logic.
     *
     * @param context the webhook processing context
     * @return true if this processor can handle the webhook
     */
    default boolean canProcess(WebhookProcessingContext context) {
        return getProviderName().equalsIgnoreCase(context.getProviderName());
    }

    /**
     * Gets the processor type identifier for logging and monitoring.
     * <p>
     * Default implementation returns the simple class name.
     *
     * @return the processor type
     */
    default String getProcessorType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Hook called before processing starts.
     * Can be used for validation, preprocessing, or setup.
     *
     * @param context the webhook processing context
     * @return a Mono that completes when preprocessing is done
     */
    default Mono<Void> beforeProcess(WebhookProcessingContext context) {
        return Mono.empty();
    }

    /**
     * Hook called after processing completes successfully.
     * Can be used for cleanup, notifications, or auditing.
     *
     * @param context the webhook processing context
     * @param result the processing result
     * @return a Mono that completes when post-processing is done
     */
    default Mono<Void> afterProcess(WebhookProcessingContext context, WebhookProcessingResult result) {
        return Mono.empty();
    }

    /**
     * Hook called when processing fails with an error.
     * Can be used for error handling, notifications, or recovery attempts.
     *
     * @param context the webhook processing context
     * @param error the error that occurred
     * @return a Mono that completes when error handling is done
     */
    default Mono<Void> onError(WebhookProcessingContext context, Throwable error) {
        return Mono.empty();
    }
}
