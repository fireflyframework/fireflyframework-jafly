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

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Port interface for validating webhook signatures from different providers.
 * <p>
 * Each provider (Stripe, Twilio, GitHub, etc.) has its own signature mechanism.
 * Implementations of this interface handle provider-specific validation logic.
 * <p>
 * This validation happens in the worker/processor, NOT during webhook ingestion.
 * The ingestion service stores the headers in the event store, and workers
 * validate signatures when processing events from the queue.
 * <p>
 * <b>Example Implementation:</b>
 * <pre>
 * {@code
 * @Component
 * public class StripeSignatureValidator implements WebhookSignatureValidator {
 *     
 *     @Override
 *     public Mono<Boolean> validateSignature(String payload, Map<String, String> headers, String secret) {
 *         return Mono.fromCallable(() -> {
 *             String signature = headers.get("stripe-signature");
 *             // Stripe-specific validation logic
 *             return computeHmac(payload, secret).equals(signature);
 *         });
 *     }
 *     
 *     @Override
 *     public String getProviderName() {
 *         return "stripe";
 *     }
 * }
 * }
 * </pre>
 */
public interface WebhookSignatureValidator {

    /**
     * Validates the webhook signature reactively.
     * <p>
     * This method should:
     * <ol>
     *   <li>Extract the signature from headers (provider-specific header name)</li>
     *   <li>Compute the expected signature using the payload and secret</li>
     *   <li>Compare the signatures securely (constant-time comparison)</li>
     * </ol>
     *
     * @param payload the raw webhook payload (as received)
     * @param headers the HTTP headers containing the signature
     * @param secret the webhook secret for this provider
     * @return a Mono emitting true if the signature is valid, false otherwise
     */
    Mono<Boolean> validateSignature(String payload, Map<String, String> headers, String secret);

    /**
     * Gets the provider name this validator supports.
     * <p>
     * This should match the provider name used in webhook events
     * (e.g., "stripe", "twilio", "github", "paypal").
     *
     * @return the provider name in lowercase
     */
    String getProviderName();

    /**
     * Checks if signature validation is required for this provider.
     * <p>
     * Some providers may not support signature validation, or it may be
     * optional based on configuration.
     *
     * @return true if validation is required, false if optional
     */
    default boolean isValidationRequired() {
        return true;
    }
}

