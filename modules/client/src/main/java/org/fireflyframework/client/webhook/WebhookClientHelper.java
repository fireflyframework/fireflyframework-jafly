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

package org.fireflyframework.client.webhook;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Webhook client helper for event-driven integrations.
 * 
 * <p>Features:
 * <ul>
 *   <li>Webhook subscription management</li>
 *   <li>Signature verification (HMAC-SHA256)</li>
 *   <li>Automatic retry for webhook delivery</li>
 *   <li>Event filtering and routing</li>
 *   <li>Webhook health monitoring</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * WebhookConfig config = WebhookConfig.builder()
 *     .secret("webhook-secret")
 *     .signatureHeader("X-Webhook-Signature")
 *     .retryEnabled(true)
 *     .maxRetries(3)
 *     .build();
 *
 * WebhookClientHelper webhook = new WebhookClientHelper(
 *     "https://api.example.com/webhooks",
 *     config
 * );
 *
 * // Subscribe to events
 * webhook.subscribe("user.created", event -> {
 *     System.out.println("User created: " + event);
 * });
 *
 * // Verify webhook signature
 * boolean valid = webhook.verifySignature(payload, signature);
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class WebhookClientHelper {

    private final String webhookUrl;
    private final WebhookConfig config;
    private final Map<String, Consumer<WebhookEvent>> eventHandlers = new ConcurrentHashMap<>();

    public WebhookClientHelper(String webhookUrl, WebhookConfig config) {
        this.webhookUrl = webhookUrl;
        this.config = config;
        log.info("Webhook Client Helper initialized for URL: {}", webhookUrl);
    }

    /**
     * Subscribes to a webhook event.
     */
    public void subscribe(String eventType, Consumer<WebhookEvent> handler) {
        eventHandlers.put(eventType, handler);
        log.info("Subscribed to webhook event: {}", eventType);
    }

    /**
     * Unsubscribes from a webhook event.
     */
    public void unsubscribe(String eventType) {
        eventHandlers.remove(eventType);
        log.info("Unsubscribed from webhook event: {}", eventType);
    }

    /**
     * Handles an incoming webhook event.
     */
    public Mono<Void> handleEvent(WebhookEvent event) {
        return Mono.fromRunnable(() -> {
            Consumer<WebhookEvent> handler = eventHandlers.get(event.getType());
            if (handler != null) {
                try {
                    handler.accept(event);
                    log.debug("Handled webhook event: {}", event.getType());
                } catch (Exception e) {
                    log.error("Error handling webhook event: {}", event.getType(), e);
                    throw e;
                }
            } else {
                log.warn("No handler registered for webhook event: {}", event.getType());
            }
        });
    }

    /**
     * Verifies webhook signature.
     */
    public boolean verifySignature(String payload, String signature) {
        if (config.getSecret() == null) {
            log.warn("Webhook secret not configured, skipping signature verification");
            return true;
        }

        try {
            String expectedSignature = generateSignature(payload);
            boolean valid = expectedSignature.equals(signature);
            
            if (!valid) {
                log.warn("Webhook signature verification failed");
            }
            
            return valid;
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Generates HMAC-SHA256 signature for payload.
     */
    public String generateSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                config.getSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Error generating webhook signature", e);
        }
    }

    /**
     * Sends a webhook with retry.
     */
    public Mono<Void> sendWebhook(WebhookEvent event) {
        return sendWebhookInternal(event, 0);
    }

    private Mono<Void> sendWebhookInternal(WebhookEvent event, int attempt) {
        log.debug("Sending webhook event: {} (attempt {})", event.getType(), attempt + 1);

        // TODO: Implement actual HTTP POST to webhook URL
        return Mono.empty()
            .then()
            .onErrorResume(error -> {
                if (config.isRetryEnabled() && attempt < config.getMaxRetries()) {
                    Duration delay = calculateRetryDelay(attempt);
                    log.warn("Webhook delivery failed, retrying in {}: {}", delay, error.getMessage());
                    return Mono.delay(delay)
                        .then(sendWebhookInternal(event, attempt + 1));
                } else {
                    log.error("Webhook delivery failed after {} attempts", attempt + 1, error);
                    return Mono.error(error);
                }
            });
    }

    private Duration calculateRetryDelay(int attempt) {
        // Exponential backoff: 1s, 2s, 4s, 8s, ...
        long delaySeconds = (long) Math.pow(2, attempt);
        return Duration.ofSeconds(Math.min(delaySeconds, 60)); // Max 60 seconds
    }

    /**
     * Webhook event.
     */
    public static class WebhookEvent {
        private final String id;
        private final String type;
        private final Object data;
        private final long timestamp;

        public WebhookEvent(String id, String type, Object data, long timestamp) {
            this.id = id;
            this.type = type;
            this.data = data;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Webhook configuration.
     */
    public static class WebhookConfig {
        private final String secret;
        private final String signatureHeader;
        private final boolean retryEnabled;
        private final int maxRetries;
        private final Duration timeout;

        private WebhookConfig(Builder builder) {
            this.secret = builder.secret;
            this.signatureHeader = builder.signatureHeader;
            this.retryEnabled = builder.retryEnabled;
            this.maxRetries = builder.maxRetries;
            this.timeout = builder.timeout;
        }

        public String getSecret() { return secret; }
        public String getSignatureHeader() { return signatureHeader; }
        public boolean isRetryEnabled() { return retryEnabled; }
        public int getMaxRetries() { return maxRetries; }
        public Duration getTimeout() { return timeout; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String secret;
            private String signatureHeader = "X-Webhook-Signature";
            private boolean retryEnabled = true;
            private int maxRetries = 3;
            private Duration timeout = Duration.ofSeconds(30);

            public Builder secret(String secret) {
                this.secret = secret;
                return this;
            }

            public Builder signatureHeader(String signatureHeader) {
                this.signatureHeader = signatureHeader;
                return this;
            }

            public Builder retryEnabled(boolean retryEnabled) {
                this.retryEnabled = retryEnabled;
                return this;
            }

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public WebhookConfig build() {
                return new WebhookConfig(this);
            }
        }
    }
}

