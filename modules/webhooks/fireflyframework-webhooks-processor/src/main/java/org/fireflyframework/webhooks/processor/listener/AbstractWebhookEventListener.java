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

package org.fireflyframework.webhooks.processor.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.processor.model.WebhookProcessingContext;
import org.fireflyframework.webhooks.processor.model.WebhookProcessingResult;
import org.fireflyframework.webhooks.processor.port.WebhookIdempotencyService;
import org.fireflyframework.webhooks.processor.port.WebhookProcessor;
import org.fireflyframework.webhooks.processor.port.WebhookSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Abstract base class for webhook event listeners using lib-common-eda.
 * <p>
 * This class uses lib-common-eda's @EventListener annotation to consume
 * {@link WebhookReceivedEvent} from Kafka and delegates processing to {@link WebhookProcessor}.
 * <p>
 * <b>Example Usage:</b>
 * <pre>
 * {@code
 * @Component
 * public class StripeWebhookListener extends AbstractWebhookEventListener {
 *     
 *     public StripeWebhookListener(StripeWebhookProcessor processor, ObjectMapper objectMapper) {
 *         super(processor, objectMapper);
 *     }
 *     
 *     @org.fireflyframework.eda.annotation.EventListener(
 *         destinations = {"stripe", "stripe-test"},  // Can listen to multiple topics
 *         groupId = "stripe-webhook-processor",
 *         consumerType = PublisherType.KAFKA,
 *         errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE
 *     )
 *     public Mono<Void> handleStripeWebhook(WebhookReceivedEvent event) {
 *         return handleWebhookEvent(event);
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
public abstract class AbstractWebhookEventListener {

    private final WebhookProcessor webhookProcessor;
    private final ObjectMapper objectMapper;
    private final Optional<WebhookSignatureValidator> signatureValidator;
    private final Optional<WebhookIdempotencyService> idempotencyService;

    // Default configuration
    private static final Duration DEFAULT_PROCESSING_LOCK_DURATION = Duration.ofMinutes(5);
    private static final Duration DEFAULT_PROCESSED_TTL = Duration.ofDays(7);

    /**
     * Constructor with signature validation and idempotency support.
     *
     * @param webhookProcessor the processor for handling webhooks
     * @param objectMapper the JSON object mapper
     * @param signatureValidator optional signature validator for this provider
     * @param idempotencyService optional idempotency service for deduplication
     */
    protected AbstractWebhookEventListener(
            WebhookProcessor webhookProcessor,
            ObjectMapper objectMapper,
            WebhookSignatureValidator signatureValidator,
            WebhookIdempotencyService idempotencyService) {
        this.webhookProcessor = webhookProcessor;
        this.objectMapper = objectMapper;
        this.signatureValidator = Optional.ofNullable(signatureValidator);
        this.idempotencyService = Optional.ofNullable(idempotencyService);

        log.info("Initialized {} for provider: {} (signatureValidation={}, idempotency={})",
                getClass().getSimpleName(),
                webhookProcessor.getProviderName(),
                this.signatureValidator.isPresent(),
                this.idempotencyService.isPresent());
    }

    /**
     * Constructor without signature validation or idempotency (backward compatible).
     *
     * @param webhookProcessor the processor for handling webhooks
     * @param objectMapper the JSON object mapper
     */
    protected AbstractWebhookEventListener(WebhookProcessor webhookProcessor, ObjectMapper objectMapper) {
        this(webhookProcessor, objectMapper, null, null);
    }

    /**
     * Handles webhook events from lib-common-eda.
     * <p>
     * This method should be called from concrete implementations' @EventListener methods.
     * <p>
     * Example:
     * <pre>
     * {@code
     * @org.fireflyframework.eda.annotation.EventListener(
     *     destinations = {"stripe"},
     *     groupId = "stripe-webhook-processor"
     * )
     * public Mono<Void> onStripeWebhook(WebhookReceivedEvent event) {
     *     return handleWebhookEvent(event);
     * }
     * }
     * </pre>
     *
     * @param event the WebhookReceivedEvent from lib-common-eda
     * @return a Mono that completes when processing is done
     */
    protected Mono<Void> handleWebhookEvent(WebhookReceivedEvent event) {
        log.debug("Received webhook event: provider={}, eventId={}",
                event.getProviderName(), event.getEventId());

        try {
            // Build processing context from WebhookReceivedEvent
            WebhookProcessingContext context = buildContextFromEvent(event);

            // Check if this processor can handle the event
            if (!webhookProcessor.canProcess(context)) {
                log.info("Skipping event - processor cannot handle provider: {}", context.getProviderName());
                return Mono.empty();
            }

            // Process with idempotency and signature validation
            return checkIdempotency(context)
                    .flatMap(shouldProcess -> {
                        if (!shouldProcess) {
                            log.info("Skipping already processed event: eventId={}", context.getEventId());
                            return Mono.empty();
                        }
                        log.info("Proceeding to signature validation: eventId={}", context.getEventId());
                        return validateSignature(context)
                                .flatMap(isValid -> {
                                    if (!isValid) {
                                        log.warn("Invalid signature for event: eventId={}, provider={}",
                                                context.getEventId(), context.getProviderName());
                                        return releaseIdempotencyLock(context)
                                                .then(Mono.error(new SecurityException("Invalid webhook signature")));
                                    }
                                    log.info("Proceeding to process webhook: eventId={}", context.getEventId());
                                    return processWebhookEvent(context);
                                });
                    })
                    .doOnSuccess(result -> {
                        if (result != null) {
                            log.info("Webhook processed: status={}, message={}, provider={}, eventId={}",
                                    result.getStatus(), result.getMessage(),
                                    context.getProviderName(), context.getEventId());
                        }
                    })
                    .doOnError(error -> {
                        log.error("Failed to process webhook: provider={}, eventId={}, error={}",
                                context.getProviderName(), context.getEventId(), error.getMessage(), error);
                        // Release lock on error so it can be retried
                        releaseIdempotencyLock(context).subscribe();
                    })
                    .then();

        } catch (Exception e) {
            log.error("Fatal error processing webhook event: provider={}, eventId={}, error={}",
                    event.getProviderName(), event.getEventId(), e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * Processes the webhook event using the configured processor.
     *
     * @param context the processing context
     * @return a Mono emitting the processing result
     */
    protected Mono<WebhookProcessingResult> processWebhookEvent(WebhookProcessingContext context) {
        Instant startTime = Instant.now();

        return webhookProcessor.beforeProcess(context)
                .then(webhookProcessor.process(context))
                .flatMap(result -> {
                    result.setProcessingDuration(java.time.Duration.between(startTime, Instant.now()));

                    // Mark as processed if successful (using content-based key)
                    Mono<Void> markProcessed = idempotencyService
                            .map(service -> {
                                UUID idempotencyKey = getIdempotencyKey(context);
                                return service.markAsProcessed(idempotencyKey, DEFAULT_PROCESSED_TTL);
                            })
                            .orElse(Mono.empty());

                    return markProcessed
                            .then(webhookProcessor.afterProcess(context, result))
                            .thenReturn(result);
                })
                .doOnError(error -> {
                    webhookProcessor.onError(context, error).subscribe();
                    // Record failure for monitoring (using content-based key)
                    idempotencyService.ifPresent(service -> {
                        UUID idempotencyKey = getIdempotencyKey(context);
                        service.recordProcessingFailure(idempotencyKey, error).subscribe();
                    });
                })
                .onErrorResume(error -> {
                    log.error("Error in webhook processing pipeline: {}", error.getMessage());
                    return Mono.just(WebhookProcessingResult.failed("Processing error", error));
                });
    }

    /**
     * Builds the processing context from the WebhookReceivedEvent.
     *
     * @param event the webhook received event from lib-common-eda
     * @return the processing context
     */
    protected WebhookProcessingContext buildContextFromEvent(WebhookReceivedEvent event) {
        // The topic/destination is typically the provider name
        // It can also come from message headers if lib-common-eda adds them
        String topic = event.getProviderName(); // Default to provider name

        return WebhookProcessingContext.builder()
                .eventId(event.getEventId())
                .providerName(event.getProviderName())
                .payload(event.getPayload())
                .headers(event.getHeaders())
                .queryParams(event.getQueryParams())
                .receivedAt(event.getReceivedAt())
                .topic(topic)
                .metadata(new java.util.HashMap<>())
                .build();
    }

    /**
     * Gets the webhook processor used by this listener.
     *
     * @return the webhook processor
     */
    protected WebhookProcessor getWebhookProcessor() {
        return webhookProcessor;
    }

    /**
     * Gets the object mapper for JSON operations.
     *
     * @return the object mapper
     */
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Checks idempotency - ensures event hasn't been processed and acquires lock.
     * <p>
     * This method checks both the eventId AND a content-based idempotency key
     * derived from the payload. This ensures that duplicate webhooks with the
     * same content are detected even if they have different eventIds.
     *
     * @param context the processing context
     * @return a Mono emitting true if should process, false if already processed
     */
    private Mono<Boolean> checkIdempotency(WebhookProcessingContext context) {
        if (idempotencyService.isEmpty()) {
            return Mono.just(true); // No idempotency service, always process
        }

        WebhookIdempotencyService service = idempotencyService.get();

        // Generate content-based idempotency key from payload
        UUID contentBasedKey = generateContentBasedIdempotencyKey(context);
        log.info("Generated content-based idempotency key: contentKey={}, eventId={}",
                contentBasedKey, context.getEventId());

        // Check if already processed by content
        return service.isAlreadyProcessed(contentBasedKey)
                .flatMap(alreadyProcessed -> {
                    if (alreadyProcessed) {
                        log.info("Event already processed (content-based): contentKey={}, eventId={}",
                                contentBasedKey, context.getEventId());
                        return Mono.just(false);
                    }
                    // Try to acquire processing lock using content-based key
                    return service.tryAcquireProcessingLock(
                            contentBasedKey,
                            DEFAULT_PROCESSING_LOCK_DURATION
                    ).map(lockAcquired -> {
                        if (!lockAcquired) {
                            log.info("Could not acquire processing lock (content-based): contentKey={}, eventId={}",
                                    contentBasedKey, context.getEventId());
                        } else {
                            log.info("Acquired processing lock (content-based): contentKey={}, eventId={}",
                                    contentBasedKey, context.getEventId());
                            // Store the content-based key in context for later use
                            context.getMetadata().put("idempotencyKey", contentBasedKey.toString());
                        }
                        return lockAcquired;
                    });
                });
    }

    /**
     * Generates a content-based idempotency key from the webhook payload.
     * <p>
     * This creates a deterministic UUID based on the payload content,
     * ensuring that duplicate webhooks with the same payload are detected.
     *
     * @param context the processing context
     * @return a UUID derived from the payload content
     */
    private UUID generateContentBasedIdempotencyKey(WebhookProcessingContext context) {
        try {
            // Use the payload's "id" field if available (e.g., Stripe event ID)
            if (context.getPayload().has("id")) {
                String payloadId = context.getPayload().get("id").asText();
                String provider = context.getProviderName();
                // Combine provider + payload ID for uniqueness across providers
                String combinedKey = provider + ":" + payloadId;
                return UUID.nameUUIDFromBytes(combinedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            // Fallback: hash the entire payload
            String payloadString = objectMapper.writeValueAsString(context.getPayload());
            return UUID.nameUUIDFromBytes(payloadString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to generate content-based idempotency key, falling back to eventId: {}", e.getMessage());
            return context.getEventId();
        }
    }

    /**
     * Validates the webhook signature if validator is configured.
     *
     * @param context the processing context
     * @return a Mono emitting true if valid or no validator configured, false if invalid
     */
    private Mono<Boolean> validateSignature(WebhookProcessingContext context) {
        return signatureValidator
                .map(validator -> {
                    String secret = getWebhookSecret(context.getProviderName());
                    if (secret == null || secret.isEmpty()) {
                        log.warn("No webhook secret configured for provider: {}", context.getProviderName());
                        return validator.isValidationRequired()
                                ? Mono.just(false)
                                : Mono.just(true);
                    }

                    String payload = context.getPayload() != null
                            ? context.getPayload().toString()
                            : "";

                    return validator.validateSignature(payload, context.getHeaders(), secret)
                            .doOnNext(isValid -> {
                                if (isValid) {
                                    log.info("Signature validation passed: eventId={}", context.getEventId());
                                } else {
                                    log.warn("Signature validation failed: eventId={}", context.getEventId());
                                }
                            });
                })
                .orElse(Mono.just(true)); // No validator configured, skip validation
    }

    /**
     * Releases the idempotency lock for an event.
     *
     * @param context the processing context
     * @return a Mono that completes when lock is released
     */
    private Mono<Void> releaseIdempotencyLock(WebhookProcessingContext context) {
        return idempotencyService
                .map(service -> {
                    UUID idempotencyKey = getIdempotencyKey(context);
                    return service.releaseProcessingLock(idempotencyKey);
                })
                .orElse(Mono.empty());
    }

    /**
     * Gets the idempotency key for the context.
     * <p>
     * Returns the content-based key if stored in metadata, otherwise generates it.
     *
     * @param context the processing context
     * @return the idempotency key
     */
    private UUID getIdempotencyKey(WebhookProcessingContext context) {
        Object storedKeyObj = context.getMetadata().get("idempotencyKey");
        if (storedKeyObj instanceof String) {
            return UUID.fromString((String) storedKeyObj);
        }
        return generateContentBasedIdempotencyKey(context);
    }

    /**
     * Gets the webhook secret for the given provider.
     * <p>
     * Subclasses should override this to provide provider-specific secrets
     * from configuration or secret management systems.
     *
     * @param providerName the provider name
     * @return the webhook secret, or null if not configured
     */
    protected String getWebhookSecret(String providerName) {
        // Default implementation returns null
        // Subclasses should override to provide actual secrets
        return null;
    }
}
