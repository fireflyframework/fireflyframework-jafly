# Webhook Processor Framework

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Key Components](#key-components)
- [Idempotency](#idempotency)
- [Signature Validation](#signature-validation)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [Best Practices](#best-practices)
- [Examples](#examples)

## Overview

The Webhook Processor Framework provides a **hexagonal architecture** (ports and adapters) framework for building webhook workers that consume and process webhook events from Kafka/RabbitMQ. It includes:

- **Abstract base class** (`AbstractWebhookEventListener`) for simplified event consumption
- **Idempotency service** for exactly-once processing using Redis
- **Signature validation** for provider-specific webhook verification
- **Processing lifecycle hooks** for before/after processing and error handling
- **Full integration with lib-common-eda** for event consumption and retry logic

### Why This Framework?

- **Hexagonal Architecture**: Clear separation between business logic (ports) and infrastructure (adapters)
- **Idempotency**: Prevents duplicate processing using distributed Redis locks
- **Signature Validation**: Verifies webhook authenticity before processing
- **Reactive**: Built on Project Reactor for non-blocking operations
- **Testable**: Core logic can be tested without infrastructure dependencies

## Architecture

### High-Level Flow

```
┌──────────────────────────────────────────────────────────────────┐
│                         Webhook Platform                         │
│  HTTP Webhook → Controller → Service → Kafka (lib-common-eda)    │
└──────────────────────────────────────────────────────────────────┘
                                 │
                                 │ Kafka Topic (e.g., "stripe")
                                 │ Message: WebhookReceivedEvent
                                 ↓
┌──────────────────────────────────────────────────────────────────┐
│                      Webhook Worker/Processor                    │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  AbstractWebhookEventListener (@EventListener)             │  │
│  │  - Consumes from Kafka                                     │  │
│  │  - Checks idempotency (Redis)                              │  │
│  │  - Validates signature                                     │  │
│  │  - Delegates to WebhookProcessor                           │  │
│  └────────────────────────────────────────────────────────────┘  │
│                           │                                      │
│                           ▼                                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  WebhookProcessor (Your Implementation)                    │  │
│  │  - Business logic                                          │  │
│  │  - External API calls                                      │  │
│  │  - Database updates                                        │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

### Hexagonal Architecture

```
   ┌───────────────────────────────────────────────────┐
   │                  Application Core                 │
   │                                                   │
   │  ┌─────────────────────────────────────────────┐  │
   │  │         WebhookProcessor (Port)             │  │
   │  │  - Business logic interface                 │  │
   │  └─────────────────────────────────────────────┘  │
   │                                                   │
   │  ┌─────────────────────────────────────────────┐  │
   │  │    WebhookIdempotencyService (Port)         │  │
   │  │  - Idempotency interface                    │  │
   │  └─────────────────────────────────────────────┘  │
   │                                                   │
   │  ┌─────────────────────────────────────────────┐  │
   │  │   WebhookSignatureValidator (Port)          │  │
   │  │  - Signature validation interface           │  │
   │  └─────────────────────────────────────────────┘  │
   └───────────────────────────────────────────────────┘
         ▲                    ▲                    ▲
         │                    │                    │
┌────────┴────────┐  ┌────────┴────────┐  ┌────────┴────────┐
│  Input Adapter  │  │ Output Adapter  │  │ Output Adapter  │
│                 │  │                 │  │                 │
│  Kafka Consumer │  │ Redis Cache     │  │ Signature       │
│  (EventListener)│  │ (Idempotency)   │  │ Validator       │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-webhooks-processor</artifactId>
    <version>${project.version}</version>
</dependency>

<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>lib-common-eda</artifactId>
    <version>${lib-common-eda.version}</version>
</dependency>

<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>lib-common-cache</artifactId>
    <version>${lib-common-cache.version}</version>
</dependency>
```

### 2. Implement WebhookProcessor

```java
@Component
public class StripeWebhookProcessor implements WebhookProcessor {

    private final PaymentService paymentService;

    public StripeWebhookProcessor(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }

    @Override
    public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
        String eventType = context.getPayload().path("type").asText();

        return switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentSuccess(context);
            case "payment_intent.failed" -> handlePaymentFailure(context);
            case "charge.refunded" -> handleRefund(context);
            default -> Mono.just(WebhookProcessingResult.success("Event type not handled"));
        };
    }

    private Mono<WebhookProcessingResult> handlePaymentSuccess(WebhookProcessingContext context) {
        JsonNode data = context.getPayload().path("data").path("object");
        String paymentIntentId = data.path("id").asText();
        long amount = data.path("amount").asLong();

        return paymentService.markPaymentAsSuccessful(paymentIntentId, amount)
            .thenReturn(WebhookProcessingResult.success("Payment marked as successful"));
    }

    private Mono<WebhookProcessingResult> handlePaymentFailure(WebhookProcessingContext context) {
        JsonNode data = context.getPayload().path("data").path("object");
        String paymentIntentId = data.path("id").asText();
        String failureReason = data.path("last_payment_error").path("message").asText();

        return paymentService.markPaymentAsFailed(paymentIntentId, failureReason)
            .thenReturn(WebhookProcessingResult.success("Payment marked as failed"));
    }

    private Mono<WebhookProcessingResult> handleRefund(WebhookProcessingContext context) {
        JsonNode data = context.getPayload().path("data").path("object");
        String chargeId = data.path("id").asText();
        long refundAmount = data.path("amount_refunded").asLong();

        return paymentService.processRefund(chargeId, refundAmount)
            .thenReturn(WebhookProcessingResult.success("Refund processed"));
    }
}
```

### 3. Create Event Listener

```java
@Component
public class StripeWebhookListener extends AbstractWebhookEventListener {

    public StripeWebhookListener(
            StripeWebhookProcessor processor,
            WebhookIdempotencyService idempotencyService,
            WebhookSignatureValidator signatureValidator,
            ObjectMapper objectMapper) {
        super(processor, idempotencyService, signatureValidator, objectMapper);
    }

    @org.fireflyframework.common.eda.annotation.EventListener(
        destinations = {"stripe"},
        groupId = "stripe-webhook-processor",
        consumerType = PublisherType.KAFKA,
        errorStrategy = ErrorHandlingStrategy.LOG_AND_CONTINUE,
        maxRetries = 3,
        retryDelayMs = 1000,
        autoAck = true
    )
    public Mono<Void> handleStripeWebhook(WebhookReceivedEvent event) {
        return handleWebhookEvent(event);
    }
}
```

### 4. Implement Signature Validator

```java
@Component
public class StripeSignatureValidator implements WebhookSignatureValidator {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Override
    public String getProviderName() {
        return "stripe";
    }

    @Override
    public Mono<Boolean> validate(WebhookProcessingContext context) {
        String signature = context.getHeaders().get("Stripe-Signature");
        if (signature == null) {
            return Mono.just(false);
        }

        // Extract timestamp and signature from header
        // Format: t=1234567890,v1=signature_here
        Map<String, String> parts = parseSignatureHeader(signature);
        String timestamp = parts.get("t");
        String expectedSignature = parts.get("v1");

        // Compute signature
        String payload = timestamp + "." + context.getPayload().toString();
        String computedSignature = HmacUtils.hmacSha256Hex(webhookSecret, payload);

        // Compare signatures
        boolean valid = computedSignature.equals(expectedSignature);

        return Mono.just(valid);
    }

    private Map<String, String> parseSignatureHeader(String signature) {
        Map<String, String> parts = new HashMap<>();
        for (String part : signature.split(",")) {
            String[] keyValue = part.split("=");
            if (keyValue.length == 2) {
                parts.put(keyValue[0], keyValue[1]);
            }
        }
        return parts;
    }
}
```

### 5. Configure Application

```yaml
firefly:
  # Cache Configuration (for idempotency)
  cache:
    enabled: true
    default-cache-type: REDIS
    redis:
      enabled: true
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      default-ttl: 7d

  # EDA Configuration (for Kafka consumer)
  eda:
    enabled: true
    default-consumer-type: KAFKA
    consumer:
      enabled: true
      group-id: stripe-webhook-processor
      kafka:
        default:
          enabled: true
          bootstrap-servers: ${FIREFLY_KAFKA_BOOTSTRAP_SERVERS}
          auto-offset-reset: earliest

# Stripe Configuration
stripe:
  webhook:
    secret: ${STRIPE_WEBHOOK_SECRET}
```

## Key Components

### WebhookProcessor (Port)

The core business logic interface:

```java
public interface WebhookProcessor {
    /**
     * Get the provider name this processor handles (e.g., "stripe", "paypal")
     */
    String getProviderName();

    /**
     * Check if this processor can handle the given context
     */
    default boolean canProcess(WebhookProcessingContext context) {
        return getProviderName().equalsIgnoreCase(context.getProviderName());
    }

    /**
     * Process the webhook event
     */
    Mono<WebhookProcessingResult> process(WebhookProcessingContext context);

    /**
     * Hook called before processing
     */
    default Mono<Void> beforeProcess(WebhookProcessingContext context) {
        return Mono.empty();
    }

    /**
     * Hook called after successful processing
     */
    default Mono<Void> afterProcess(WebhookProcessingContext context, WebhookProcessingResult result) {
        return Mono.empty();
    }

    /**
     * Hook called on error
     */
    default Mono<Void> onError(WebhookProcessingContext context, Throwable error) {
        return Mono.empty();
    }
}
```

### WebhookProcessingContext

Contains all webhook information:

```java
public class WebhookProcessingContext {
    private UUID eventId;
    private String providerName;
    private JsonNode payload;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private Instant receivedAt;
    private String topic;
    private Integer partition;
    private Long offset;
    private Integer attemptNumber;
    private Map<String, Object> metadata;

    // Getters and setters...
}
```

### WebhookProcessingResult

Result of webhook processing:

```java
public class WebhookProcessingResult {
    public enum Status {
        SUCCESS,    // Processing succeeded
        RETRY,      // Retry processing
        FAILED,     // Processing failed permanently
        SKIPPED     // Processing skipped (e.g., already processed)
    }

    private Status status;
    private String message;
    private String errorDetails;
    private Duration processingDuration;
    private boolean shouldRetry;
    private Duration retryDelay;
    private Map<String, Object> data;

    // Factory methods
    public static WebhookProcessingResult success(String message) { ... }
    public static WebhookProcessingResult retry(String message, Duration retryDelay) { ... }
    public static WebhookProcessingResult failed(String message, String errorDetails) { ... }
    public static WebhookProcessingResult skipped(String message) { ... }
}
```

### AbstractWebhookEventListener (Adapter)

Base class that handles the complete processing lifecycle:

```java
public abstract class AbstractWebhookEventListener {

    protected Mono<Void> handleWebhookEvent(WebhookReceivedEvent event) {
        return Mono.defer(() -> {
            // 1. Build processing context
            WebhookProcessingContext context = buildContext(event);

            // 2. Before processing hook
            return processor.beforeProcess(context)

                // 3. Check idempotency
                .then(idempotencyService.tryAcquireProcessingLock(context.getEventId()))
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.just(WebhookProcessingResult.skipped("Already processing"));
                    }

                    return idempotencyService.isAlreadyProcessed(context.getEventId())
                        .flatMap(processed -> {
                            if (processed) {
                                return Mono.just(WebhookProcessingResult.skipped("Already processed"));
                            }

                            // 4. Validate signature
                            return signatureValidator.validate(context)
                                .flatMap(valid -> {
                                    if (!valid) {
                                        return Mono.just(WebhookProcessingResult.failed(
                                            "Signature validation failed", "Invalid signature"));
                                    }

                                    // 5. Process webhook
                                    return processor.process(context)
                                        .flatMap(result -> {
                                            if (result.getStatus() == Status.SUCCESS) {
                                                // 6. Mark as processed
                                                return idempotencyService.markAsProcessed(context.getEventId())
                                                    .thenReturn(result);
                                            }
                                            return Mono.just(result);
                                        });
                                });
                        })
                        .doFinally(signal ->
                            // 7. Release lock
                            idempotencyService.releaseProcessingLock(context.getEventId()).subscribe());
                })

                // 8. After processing hook
                .flatMap(result -> processor.afterProcess(context, result).thenReturn(result))

                // 9. Error handling
                .onErrorResume(error ->
                    processor.onError(context, error)
                        .then(idempotencyService.recordProcessingFailure(context.getEventId(), error.getMessage()))
                        .then(Mono.error(error)))

                .then();
        });
    }
}
```

---

# 📖 Complete Implementation Guide

## How to Create Webhook Processors in Your Microservice

This guide shows you how to create webhook processors, validators, and listeners in your own microservices using the `fireflyframework-webhooks-processor` module.

### Prerequisites

- Java 21+
- Spring Boot 3.5.10+
- Access to Kafka (configured via `lib-common-eda`)
- Access to Redis (configured via `lib-common-cache`)
- The webhook platform is deployed and publishing events to Kafka

---

## Step 1: Add Dependencies

Add the processor framework to your microservice's `pom.xml`:

```xml
<dependencies>
    <!-- Webhook Processor Framework -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-webhooks-processor</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Webhook Interfaces (DTOs) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-webhooks-interfaces</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Firefly EDA Library (Kafka/RabbitMQ) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>lib-common-eda</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Firefly Cache Library (Redis/Caffeine) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>lib-common-cache</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring Boot Starter WebFlux (for reactive support) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
</dependencies>
```

---

## Step 2: Configure Your Application

Create or update `application.yml` in your microservice:

```yaml
spring:
  application:
    name: my-payment-service

  # Redis Configuration (for Spring Data Redis)
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0
      timeout: 10s
      connect-timeout: 10s
      ssl:
        enabled: ${REDIS_SSL:false}

# Firefly EDA Configuration (Kafka Consumer)
firefly:
  eda:
    consumer:
      kafka:
        default:
          bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
          group-id: payment-service-webhook-consumer
          auto-offset-reset: earliest
          enable-auto-commit: false
          max-poll-records: 100

  # Firefly Cache Configuration (Redis for Idempotency)
  cache:
    enabled: true
    default-cache-type: REDIS
    redis:
      enabled: true
      cache-name: "webhook-idempotency"
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0
      key-prefix: "firefly:webhooks:payment"
      default-ttl: 7d
      connection-timeout: 10s
      command-timeout: 5s
      ssl: ${REDIS_SSL:false}
    caffeine:
      enabled: true
      cache-name: "webhook-idempotency-fallback"
      default-ttl: 7d
      max-size: 10000

# Logging
logging:
  level:
    org.fireflyframework.payment.webhooks: DEBUG
    org.fireflyframework.webhooks.processor: DEBUG
```

---

## Step 3: Implement a Signature Validator

Create a signature validator for your webhook provider (e.g., Stripe):

```java
package org.fireflyframework.payment.webhooks.validators;

import org.fireflyframework.webhooks.processor.ports.WebhookSignatureValidator;
import org.fireflyframework.webhooks.processor.models.WebhookProcessingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stripe webhook signature validator.
 * Validates the Stripe-Signature header using HMAC SHA256.
 */
@Component
public class StripeSignatureValidator implements WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(StripeSignatureValidator.class);
    private static final String SIGNATURE_HEADER = "Stripe-Signature";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long TOLERANCE_SECONDS = 300; // 5 minutes

    private final String webhookSecret;

    public StripeSignatureValidator(@Value("${stripe.webhook.secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public Mono<Boolean> validateSignature(WebhookProcessingContext context) {
        return Mono.fromCallable(() -> {
            String signatureHeader = context.getHeaders().get(SIGNATURE_HEADER);

            if (signatureHeader == null || signatureHeader.isEmpty()) {
                log.warn("Missing Stripe-Signature header: eventId={}", context.getEventId());
                return false;
            }

            // Parse signature header: "t=timestamp,v1=signature"
            String[] parts = signatureHeader.split(",");
            Long timestamp = null;
            String expectedSignature = null;

            for (String part : parts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2) {
                    if ("t".equals(keyValue[0])) {
                        timestamp = Long.parseLong(keyValue[1]);
                    } else if ("v1".equals(keyValue[0])) {
                        expectedSignature = keyValue[1];
                    }
                }
            }

            if (timestamp == null || expectedSignature == null) {
                log.warn("Invalid Stripe-Signature format: eventId={}", context.getEventId());
                return false;
            }

            // Check timestamp tolerance
            long currentTime = System.currentTimeMillis() / 1000;
            if (Math.abs(currentTime - timestamp) > TOLERANCE_SECONDS) {
                log.warn("Signature timestamp outside tolerance: eventId={}, timestamp={}, current={}",
                    context.getEventId(), timestamp, currentTime);
                return false;
            }

            // Compute signature
            String signedPayload = timestamp + "." + context.getRawPayload();
            String computedSignature = computeHmacSha256(signedPayload, webhookSecret);

            boolean isValid = computedSignature.equals(expectedSignature);

            if (isValid) {
                log.debug("Signature validation successful: eventId={}", context.getEventId());
            } else {
                log.warn("Signature validation failed: eventId={}, expected={}, computed={}",
                    context.getEventId(), expectedSignature, computedSignature);
            }

            return isValid;
        });
    }

    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC SHA256", e);
        }
    }
}
```

---

## Step 4: Implement a Webhook Processor

Create the business logic processor:

```java
package org.fireflyframework.payment.webhooks.processors;

import com.fasterxml.jackson.databind.JsonNode;
import org.fireflyframework.webhooks.processor.models.WebhookProcessingContext;
import org.fireflyframework.webhooks.processor.models.WebhookProcessingResult;
import org.fireflyframework.webhooks.processor.ports.WebhookProcessor;
import org.fireflyframework.payment.services.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Stripe webhook processor - handles Stripe payment events.
 */
@Component
public class StripeWebhookProcessor implements WebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookProcessor.class);

    private final PaymentService paymentService;

    public StripeWebhookProcessor(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
        return Mono.fromCallable(() -> {
            JsonNode payload = context.getParsedPayload();
            String eventType = payload.path("type").asText();

            log.info("Processing Stripe webhook: eventId={}, type={}",
                context.getEventId(), eventType);

            return switch (eventType) {
                case "payment_intent.succeeded" -> handlePaymentSuccess(context, payload);
                case "payment_intent.payment_failed" -> handlePaymentFailure(context, payload);
                case "customer.subscription.created" -> handleSubscriptionCreated(context, payload);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(context, payload);
                default -> {
                    log.info("Unhandled event type: {}", eventType);
                    yield WebhookProcessingResult.success("Event type not handled");
                }
            };
        }).flatMap(Mono::just);
    }

    private WebhookProcessingResult handlePaymentSuccess(
            WebhookProcessingContext context,
            JsonNode payload) {

        JsonNode paymentIntent = payload.path("data").path("object");
        String paymentIntentId = paymentIntent.path("id").asText();
        long amount = paymentIntent.path("amount").asLong();
        String currency = paymentIntent.path("currency").asText();
        String customerId = paymentIntent.path("customer").asText();

        log.info("Payment succeeded: paymentIntentId={}, amount={}, currency={}, customer={}",
            paymentIntentId, amount, currency, customerId);

        // Call your business logic
        paymentService.recordPaymentSuccess(paymentIntentId, amount, currency, customerId);

        return WebhookProcessingResult.success("Payment processed successfully");
    }

    private WebhookProcessingResult handlePaymentFailure(
            WebhookProcessingContext context,
            JsonNode payload) {

        JsonNode paymentIntent = payload.path("data").path("object");
        String paymentIntentId = paymentIntent.path("id").asText();
        String failureMessage = paymentIntent.path("last_payment_error")
            .path("message").asText();

        log.warn("Payment failed: paymentIntentId={}, reason={}",
            paymentIntentId, failureMessage);

        paymentService.recordPaymentFailure(paymentIntentId, failureMessage);

        return WebhookProcessingResult.success("Payment failure recorded");
    }

    private WebhookProcessingResult handleSubscriptionCreated(
            WebhookProcessingContext context,
            JsonNode payload) {

        JsonNode subscription = payload.path("data").path("object");
        String subscriptionId = subscription.path("id").asText();
        String customerId = subscription.path("customer").asText();
        String status = subscription.path("status").asText();

        log.info("Subscription created: subscriptionId={}, customer={}, status={}",
            subscriptionId, customerId, status);

        paymentService.createSubscription(subscriptionId, customerId, status);

        return WebhookProcessingResult.success("Subscription created");
    }

    private WebhookProcessingResult handleSubscriptionDeleted(
            WebhookProcessingContext context,
            JsonNode payload) {

        JsonNode subscription = payload.path("data").path("object");
        String subscriptionId = subscription.path("id").asText();

        log.info("Subscription deleted: subscriptionId={}", subscriptionId);

        paymentService.cancelSubscription(subscriptionId);

        return WebhookProcessingResult.success("Subscription cancelled");
    }

    @Override
    public Mono<Void> beforeProcess(WebhookProcessingContext context) {
        return Mono.fromRunnable(() ->
            log.debug("Before processing: eventId={}", context.getEventId())
        );
    }

    @Override
    public Mono<Void> afterProcess(WebhookProcessingContext context, WebhookProcessingResult result) {
        return Mono.fromRunnable(() ->
            log.info("After processing: eventId={}, success={}, message={}",
                context.getEventId(), result.isSuccess(), result.getMessage())
        );
    }

    @Override
    public Mono<Void> onError(WebhookProcessingContext context, Throwable error) {
        return Mono.fromRunnable(() ->
            log.error("Error processing webhook: eventId={}, error={}",
                context.getEventId(), error.getMessage(), error)
        );
    }
}
```


---

## Step 5: Create the Event Listener

Create a Kafka event listener that extends `AbstractWebhookEventListener`:

```java
package org.fireflyframework.payment.webhooks.listeners;

import org.fireflyframework.common.eda.annotations.EventListener;
import org.fireflyframework.webhooks.core.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.processor.idempotency.CacheBasedWebhookIdempotencyService;
import org.fireflyframework.webhooks.processor.listener.AbstractWebhookEventListener;
import org.fireflyframework.webhooks.processor.ports.WebhookProcessor;
import org.fireflyframework.webhooks.processor.ports.WebhookSignatureValidator;
import org.fireflyframework.payment.webhooks.processors.StripeWebhookProcessor;
import org.fireflyframework.payment.webhooks.validators.StripeSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kafka event listener for Stripe webhooks.
 * Consumes events from the "stripe" Kafka topic.
 */
@Component
public class StripeWebhookListener extends AbstractWebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookListener.class);

    private final StripeWebhookProcessor processor;
    private final StripeSignatureValidator validator;

    public StripeWebhookListener(
            StripeWebhookProcessor processor,
            StripeSignatureValidator validator,
            CacheBasedWebhookIdempotencyService idempotencyService) {
        super(idempotencyService);
        this.processor = processor;
        this.validator = validator;
    }

    /**
     * Event listener method - called by lib-common-eda when a message arrives.
     *
     * @EventListener annotation configures:
     * - topic: Kafka topic to consume from
     * - groupId: Consumer group ID
     * - errorStrategy: How to handle errors (LOG_AND_CONTINUE, RETRY, DEAD_LETTER)
     */
    @EventListener(
        topic = "stripe",
        groupId = "payment-service-stripe-consumer",
        errorStrategy = EventListener.ErrorStrategy.LOG_AND_CONTINUE
    )
    public Mono<Void> onStripeWebhook(WebhookReceivedEvent event) {
        log.info("Received Stripe webhook event: eventId={}, provider={}",
            event.getEventId(), event.getProviderName());

        return handleWebhookEvent(event)
            .doOnSuccess(v ->
                log.info("Successfully processed Stripe webhook: eventId={}", event.getEventId())
            )
            .doOnError(error ->
                log.error("Failed to process Stripe webhook: eventId={}, error={}",
                    event.getEventId(), error.getMessage(), error)
            );
    }

    @Override
    protected WebhookProcessor getProcessor() {
        return processor;
    }

    @Override
    protected WebhookSignatureValidator getSignatureValidator() {
        return validator;
    }
}
```

---

## Step 6: Create a Business Service (Example)

Create your business logic service that the processor calls:

```java
package org.fireflyframework.payment.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment service - handles payment-related business logic.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Inject your repositories, external clients, etc.
    // private final PaymentRepository paymentRepository;
    // private final NotificationService notificationService;

    @Transactional
    public void recordPaymentSuccess(String paymentIntentId, long amount, String currency, String customerId) {
        log.info("Recording payment success: paymentIntentId={}, amount={}, currency={}, customer={}",
            paymentIntentId, amount, currency, customerId);

        // Your business logic here:
        // 1. Update payment status in database
        // 2. Send confirmation email
        // 3. Update customer balance
        // 4. Trigger fulfillment process
        // etc.
    }

    @Transactional
    public void recordPaymentFailure(String paymentIntentId, String failureMessage) {
        log.warn("Recording payment failure: paymentIntentId={}, reason={}",
            paymentIntentId, failureMessage);

        // Your business logic here:
        // 1. Update payment status in database
        // 2. Send failure notification
        // 3. Retry payment if applicable
        // etc.
    }

    @Transactional
    public void createSubscription(String subscriptionId, String customerId, String status) {
        log.info("Creating subscription: subscriptionId={}, customer={}, status={}",
            subscriptionId, customerId, status);

        // Your business logic here
    }

    @Transactional
    public void cancelSubscription(String subscriptionId) {
        log.info("Cancelling subscription: subscriptionId={}", subscriptionId);

        // Your business logic here
    }
}
```

---

## Step 7: Enable Component Scanning

Make sure your Spring Boot application scans the webhook components:

```java
package org.fireflyframework.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "org.fireflyframework.payment",                              // Your application
    "org.fireflyframework.webhooks.processor",            // Webhook processor framework
    "org.fireflyframework.common.eda",                           // EDA library
    "org.fireflyframework.common.cache"                          // Cache library
})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

---

## Step 8: Add Configuration Properties

Add Stripe webhook secret to your `application.yml`:

```yaml
stripe:
  webhook:
    secret: ${STRIPE_WEBHOOK_SECRET:whsec_test_secret}
```

Or use environment variables:

```bash
export STRIPE_WEBHOOK_SECRET=whsec_your_actual_secret_here
```

---

## Step 9: Testing Your Implementation

### Unit Test for Processor

```java
package org.fireflyframework.payment.webhooks.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.processor.models.WebhookProcessingContext;
import org.fireflyframework.webhooks.processor.models.WebhookProcessingResult;
import org.fireflyframework.payment.services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookProcessorTest {

    @Mock
    private PaymentService paymentService;

    private StripeWebhookProcessor processor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        processor = new StripeWebhookProcessor(paymentService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldProcessPaymentSuccessEvent() throws Exception {
        // Given
        String payload = """
            {
              "id": "evt_123",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_123",
                  "amount": 2000,
                  "currency": "usd",
                  "customer": "cus_123"
                }
              }
            }
            """;

        WebhookProcessingContext context = WebhookProcessingContext.builder()
            .eventId(UUID.randomUUID())
            .providerName("stripe")
            .rawPayload(payload)
            .parsedPayload(objectMapper.readTree(payload))
            .headers(new HashMap<>())
            .receivedAt(System.currentTimeMillis())
            .build();

        // When
        StepVerifier.create(processor.process(context))
            // Then
            .expectNextMatches(result ->
                result.isSuccess() &&
                result.getMessage().equals("Payment processed successfully")
            )
            .verifyComplete();

        // Verify business logic was called
        verify(paymentService).recordPaymentSuccess(
            eq("pi_123"),
            eq(2000L),
            eq("usd"),
            eq("cus_123")
        );
    }
}
```

### Integration Test

```java
package org.fireflyframework.payment.webhooks.integration;

import org.fireflyframework.webhooks.core.events.WebhookReceivedEvent;
import org.fireflyframework.payment.services.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class StripeWebhookIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("firefly.eda.consumer.kafka.default.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private KafkaTemplate<String, WebhookReceivedEvent> kafkaTemplate;

    @MockBean
    private PaymentService paymentService;

    @Test
    void shouldProcessStripeWebhookEndToEnd() {
        // Given
        String payload = """
            {
              "id": "evt_123",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_123",
                  "amount": 2000,
                  "currency": "usd",
                  "customer": "cus_123"
                }
              }
            }
            """;

        Map<String, String> headers = new HashMap<>();
        headers.put("Stripe-Signature", "t=1234567890,v1=abc123...");

        WebhookReceivedEvent event = WebhookReceivedEvent.builder()
            .eventId(UUID.randomUUID())
            .providerName("stripe")
            .rawPayload(payload)
            .headers(headers)
            .receivedAt(System.currentTimeMillis())
            .build();

        // When
        kafkaTemplate.send("stripe", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(paymentService).recordPaymentSuccess(
                    eq("pi_123"),
                    eq(2000L),
                    eq("usd"),
                    eq("cus_123")
                )
            );
    }
}
```


---

## Step 10: Running Your Microservice

### Local Development

```bash
# Set environment variables
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export REDIS_HOST=localhost
export REDIS_PORT=6379
export STRIPE_WEBHOOK_SECRET=whsec_your_secret

# Run the application
mvn spring-boot:run
```

### Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  payment-service:
    build: .
    ports:
      - "8081:8081"
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET}
    depends_on:
      - kafka
      - redis

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      # ... other Kafka config

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

### Kubernetes Deployment

Create `k8s/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
    spec:
      containers:
      - name: payment-service
        image: payment-service:1.0.0
        ports:
        - containerPort: 8081
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-service:9092"
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PORT
          value: "6379"
        - name: STRIPE_WEBHOOK_SECRET
          valueFrom:
            secretKeyRef:
              name: stripe-secrets
              key: webhook-secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 20
          periodSeconds: 5
```

---

## 📋 Best Practices

### 1. **Idempotency is Critical**
- Always use the idempotency service provided by the framework
- Never skip idempotency checks - duplicate webhooks are common
- Use content-based idempotency (default) for providers that don't send unique IDs

### 2. **Signature Validation**
- Always validate webhook signatures before processing
- Store webhook secrets securely (use environment variables or secret managers)
- Implement timestamp tolerance to prevent replay attacks
- Return `false` from validator if signature is invalid - don't throw exceptions

### 3. **Error Handling**
- Use appropriate error strategies: `LOG_AND_CONTINUE`, `RETRY`, or `DEAD_LETTER`
- Log all errors with context (eventId, provider, error message)
- Implement the `onError` hook for custom error handling
- Don't let exceptions bubble up - handle them gracefully

### 4. **Performance**
- Keep processing logic fast - offload heavy work to async jobs
- Use reactive patterns (Mono/Flux) throughout
- Configure appropriate Kafka consumer settings (max-poll-records, fetch-min-size)
- Monitor processing latency and throughput

### 5. **Testing**
- Write unit tests for processors (mock dependencies)
- Write integration tests with Testcontainers
- Test idempotency behavior (send duplicate events)
- Test signature validation with real signatures
- Test error scenarios (invalid payloads, network failures)

### 6. **Monitoring**
- Add metrics for processed events, failures, latency
- Use structured logging with eventId, provider, and correlation IDs
- Set up alerts for high failure rates
- Monitor Kafka consumer lag

### 7. **Security**
- Never log sensitive data (payment details, PII)
- Validate all input data before processing
- Use HTTPS for all external API calls
- Rotate webhook secrets regularly

### 8. **Scalability**
- Design processors to be stateless
- Use horizontal scaling (multiple instances)
- Partition Kafka topics by provider or tenant
- Use Redis cluster for high availability

---

## 🔍 Troubleshooting

### Events Not Being Consumed

**Problem**: Your listener is not receiving events from Kafka.

**Solutions**:
1. Check Kafka topic exists: `kafka-topics --list --bootstrap-server localhost:9092`
2. Verify consumer group is active: `kafka-consumer-groups --describe --group payment-service-stripe-consumer`
3. Check component scanning includes `org.fireflyframework.common.eda`
4. Verify `@EventListener` annotation is present and correct
5. Check Kafka connection in logs

### Idempotency Not Working

**Problem**: Duplicate events are being processed.

**Solutions**:
1. Verify Redis is running and accessible
2. Check Redis configuration in `application.yml`
3. Verify `CacheBasedWebhookIdempotencyService` bean is created
4. Check logs for "Failed to acquire processing lock" messages
5. Ensure content-based idempotency is enabled if provider doesn't send unique IDs

### Signature Validation Failing

**Problem**: All webhooks are rejected due to invalid signatures.

**Solutions**:
1. Verify webhook secret is correct
2. Check signature header name matches provider's format
3. Verify timestamp tolerance is appropriate
4. Test with a known-good signature from provider's documentation
5. Check if payload is being modified before validation

### High Memory Usage

**Problem**: Application consumes too much memory.

**Solutions**:
1. Reduce `max-poll-records` in Kafka consumer config
2. Implement backpressure in reactive chains
3. Don't hold large payloads in memory - stream them
4. Configure appropriate JVM heap size
5. Monitor for memory leaks with profiler

---

## 📚 Additional Examples

### GitHub Webhook Processor

```java
@Component
public class GitHubWebhookProcessor implements WebhookProcessor {

    @Override
    public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
        return Mono.fromCallable(() -> {
            JsonNode payload = context.getParsedPayload();
            String action = payload.path("action").asText();

            return switch (action) {
                case "opened" -> handlePullRequestOpened(payload);
                case "closed" -> handlePullRequestClosed(payload);
                case "synchronize" -> handlePullRequestUpdated(payload);
                default -> WebhookProcessingResult.success("Action not handled");
            };
        }).flatMap(Mono::just);
    }

    private WebhookProcessingResult handlePullRequestOpened(JsonNode payload) {
        // Your logic here
        return WebhookProcessingResult.success("PR opened");
    }

    // ... other methods
}
```

### Shopify Webhook Processor

```java
@Component
public class ShopifyWebhookProcessor implements WebhookProcessor {

    private final OrderService orderService;

    @Override
    public Mono<WebhookProcessingResult> process(WebhookProcessingContext context) {
        return Mono.fromCallable(() -> {
            JsonNode payload = context.getParsedPayload();

            // Shopify sends topic in header
            String topic = context.getHeaders().get("X-Shopify-Topic");

            return switch (topic) {
                case "orders/create" -> handleOrderCreated(payload);
                case "orders/updated" -> handleOrderUpdated(payload);
                case "orders/cancelled" -> handleOrderCancelled(payload);
                default -> WebhookProcessingResult.success("Topic not handled");
            };
        }).flatMap(Mono::just);
    }

    private WebhookProcessingResult handleOrderCreated(JsonNode payload) {
        long orderId = payload.path("id").asLong();
        String email = payload.path("email").asText();
        String totalPrice = payload.path("total_price").asText();

        orderService.createOrder(orderId, email, totalPrice);

        return WebhookProcessingResult.success("Order created");
    }

    // ... other methods
}
```

---

## 🎓 Summary

You now have a complete guide to implementing webhook processors in your microservices:

1. ✅ **Add dependencies** - Include processor framework and required libraries
2. ✅ **Configure application** - Set up Kafka, Redis, and other properties
3. ✅ **Implement validator** - Create signature validation logic
4. ✅ **Implement processor** - Write business logic for webhook processing
5. ✅ **Create listener** - Extend `AbstractWebhookEventListener` for Kafka consumption
6. ✅ **Create services** - Implement business logic services
7. ✅ **Enable scanning** - Configure Spring component scanning
8. ✅ **Add secrets** - Configure webhook secrets securely
9. ✅ **Test thoroughly** - Write unit and integration tests
10. ✅ **Deploy** - Run locally, in Docker, or Kubernetes

The framework handles:
- ✅ Kafka event consumption
- ✅ Idempotency (exactly-once processing)
- ✅ Signature validation
- ✅ Error handling and retries
- ✅ Lifecycle hooks (before/after processing)
- ✅ Reactive processing

You focus on:
- ✅ Business logic (what to do with the webhook)
- ✅ Provider-specific signature validation
- ✅ Data transformation and persistence

**Happy webhook processing! 🚀**
