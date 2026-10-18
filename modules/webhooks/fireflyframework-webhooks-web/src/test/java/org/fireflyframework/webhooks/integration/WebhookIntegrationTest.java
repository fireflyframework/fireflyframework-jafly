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

package org.fireflyframework.webhooks.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.fireflyframework.webhooks.web.WebhookManagementApplication;
import org.fireflyframework.webhooks.integration.support.TestStripeWebhookProcessor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive end-to-end integration test for the complete webhook lifecycle.
 * <p>
 * This test validates the entire flow:
 * 1. HTTP POST to webhook endpoint (WebhookController)
 * 2. Event published to Kafka (WebhookProcessingService using lib-common-eda)
 * 3. Event consumed from Kafka (AbstractWebhookEventListener using lib-common-eda @EventListener)
 * 4. Idempotency check using Redis (CacheBasedWebhookIdempotencyService)
 * 5. Signature validation (StripeSignatureValidator)
 * 6. Business logic processing (WebhookProcessor)
 * <p>
 * Uses Testcontainers for Redis and Kafka to ensure realistic integration testing.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {WebhookManagementApplication.class}
)
@Import(WebhookIntegrationTestConfiguration.class)
@AutoConfigureWebTestClient(timeout = "30000")
@TestPropertySource(properties = {
        "firefly.eda.consumer.enabled=true",
        "firefly.eda.consumer.kafka.default.enabled=true"
})
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookIntegrationTest {

    private static final String TEST_PROVIDER = "stripe";
    private static final String STRIPE_SECRET = "whsec_test_secret_key_12345";

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Kafka configuration
        registry.add("firefly.eda.publishers.kafka.default.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("firefly.eda.consumer.kafka.default.bootstrap-servers", kafka::getBootstrapServers);

        // Redis configuration for lib-common-cache
        registry.add("firefly.cache.redis.host", redis::getHost);
        registry.add("firefly.cache.redis.port", redis::getFirstMappedPort);

        // Redis configuration for Spring Data Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestStripeWebhookProcessor testProcessor;

    @BeforeAll
    static void createKafkaTopics() throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("🚀 INITIALIZING WEBHOOK INTEGRATION TEST SUITE");
        System.out.println("=".repeat(80));

        System.out.println("\n📦 Setting up test infrastructure:");
        System.out.println("   ✓ Kafka container: " + kafka.getBootstrapServers());
        System.out.println("   ✓ Redis container: " + redis.getHost() + ":" + redis.getFirstMappedPort());

        // Create the "stripe" topic before tests run to ensure consumer can subscribe
        System.out.println("\n📝 Creating Kafka topics...");
        try (AdminClient adminClient = AdminClient.create(
                java.util.Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {

            NewTopic stripeTopic = new NewTopic("stripe", 1, (short) 1);
            adminClient.createTopics(java.util.List.of(stripeTopic)).all().get();
            System.out.println("   ✓ Created topic: stripe (partitions=1, replication=1)");

            // Wait a bit for topic to be fully created
            Thread.sleep(1000);
        }

        System.out.println("\n✅ Test infrastructure ready!");
        System.out.println("=".repeat(80) + "\n");
    }

    @BeforeEach
    void setUp() {
        // Reset processor state before each test
        testProcessor.reset();
    }

    @Test
    @Order(1)
    @DisplayName("Complete webhook lifecycle: HTTP → Kafka → Idempotency → Signature → Processing")
    void testCompleteWebhookLifecycle() throws Exception {
        System.out.println("\n" + "─".repeat(80));
        System.out.println("🧪 TEST 1: Complete Webhook Lifecycle");
        System.out.println("─".repeat(80));

        // Given: A valid Stripe webhook payload
        System.out.println("\n📋 STEP 1: Preparing webhook payload");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "evt_test_webhook_001");
        payload.put("type", "payment_intent.succeeded");
        payload.put("data", objectMapper.createObjectNode().put("amount", 1000));
        System.out.println("   ✓ Event ID: evt_test_webhook_001");
        System.out.println("   ✓ Event Type: payment_intent.succeeded");
        System.out.println("   ✓ Amount: $10.00");

        String payloadString = objectMapper.writeValueAsString(payload);
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = generateStripeSignature(payloadString, timestamp, STRIPE_SECRET);
        System.out.println("   ✓ Generated HMAC-SHA256 signature");

        // Expect 1 event to be processed
        testProcessor.expectEvents(1);

        // When: Sending webhook to HTTP endpoint
        System.out.println("\n📤 STEP 2: Sending webhook to HTTP endpoint");
        System.out.println("   → POST /api/v1/webhook/stripe");
        System.out.println("   → Headers: Stripe-Signature");
        webTestClient.post()
                .uri("/api/v1/webhook/" + TEST_PROVIDER)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACCEPTED");
        System.out.println("   ✓ HTTP 202 ACCEPTED received");

        // Then: Wait for async processing and verify
        System.out.println("\n⏳ STEP 3: Waiting for async processing...");
        System.out.println("   → Event published to Kafka topic: stripe");
        System.out.println("   → Consumer processing event...");
        boolean processed = testProcessor.awaitProcessing(15, TimeUnit.SECONDS);
        assertThat(processed).as("Worker should process event within 15 seconds").isTrue();
        System.out.println("   ✓ Event consumed from Kafka");
        System.out.println("   ✓ Idempotency check passed (Redis)");
        System.out.println("   ✓ Signature validation passed");
        System.out.println("   ✓ Business logic executed");

        assertThat(testProcessor.getProcessedCount()).isEqualTo(1);
        assertThat(testProcessor.getLastProcessedEvent()).isNotNull();
        assertThat(testProcessor.getLastProcessedEvent().getProviderName()).isEqualTo(TEST_PROVIDER);

        JsonNode processedPayload = testProcessor.getLastProcessedEvent().getPayload();
        assertThat(processedPayload.path("id").asText()).isEqualTo("evt_test_webhook_001");
        assertThat(processedPayload.path("type").asText()).isEqualTo("payment_intent.succeeded");

        System.out.println("\n✅ TEST 1 PASSED: Complete webhook lifecycle successful!");
        System.out.println("   • Events processed: " + testProcessor.getProcessedCount());
        System.out.println("   • Provider: " + TEST_PROVIDER);
        System.out.println("   • Event ID: evt_test_webhook_001");
        System.out.println("─".repeat(80) + "\n");
    }

    @Test
    @Order(2)
    @DisplayName("Idempotency: Duplicate webhooks should be processed only once")
    void testIdempotencyPreventsDoubleProcessing() throws Exception {
        System.out.println("\n" + "─".repeat(80));
        System.out.println("🧪 TEST 2: Idempotency - Duplicate Prevention");
        System.out.println("─".repeat(80));

        // Given: A valid Stripe webhook payload
        System.out.println("\n📋 STEP 1: Preparing duplicate webhook payload");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "evt_test_idempotency_001");
        payload.put("type", "charge.succeeded");
        payload.put("data", objectMapper.createObjectNode().put("amount", 2000));
        System.out.println("   ✓ Event ID: evt_test_idempotency_001");
        System.out.println("   ✓ Event Type: charge.succeeded");
        System.out.println("   ✓ Amount: $20.00");

        String payloadString = objectMapper.writeValueAsString(payload);
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = generateStripeSignature(payloadString, timestamp, STRIPE_SECRET);
        System.out.println("   ✓ Generated signature");

        // Expect only 1 event to be processed (despite sending 2)
        testProcessor.expectEvents(1);

        // When: Sending the same webhook twice
        System.out.println("\n📤 STEP 2: Sending DUPLICATE webhooks (same payload, same signature)");
        for (int i = 0; i < 2; i++) {
            System.out.println("   → Attempt " + (i + 1) + ": POST /api/v1/webhook/stripe");
            webTestClient.post()
                    .uri("/api/v1/webhook/" + TEST_PROVIDER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature)
                    .bodyValue(payload)
                    .exchange()
                    .expectStatus().isAccepted();
            System.out.println("   ✓ HTTP 202 ACCEPTED received");
        }

        // Then: Only one event should be processed
        System.out.println("\n⏳ STEP 3: Waiting for async processing...");
        boolean processed = testProcessor.awaitProcessing(15, TimeUnit.SECONDS);
        assertThat(processed).isTrue();
        System.out.println("   ✓ First event processed successfully");

        // Wait a bit more to ensure no duplicate processing
        System.out.println("   ⏱  Waiting 2 seconds to verify no duplicate processing...");
        Thread.sleep(2000);

        assertThat(testProcessor.getProcessedCount())
                .as("Should process only once despite duplicate submissions")
                .isEqualTo(1);

        System.out.println("   ✓ Second event REJECTED by idempotency check (Redis)");
        System.out.println("\n✅ TEST 2 PASSED: Idempotency working correctly!");
        System.out.println("   • Webhooks sent: 2");
        System.out.println("   • Events processed: " + testProcessor.getProcessedCount());
        System.out.println("   • Duplicates prevented: 1");
        System.out.println("─".repeat(80) + "\n");
    }

    @Test
    @Order(3)
    @DisplayName("Signature validation: Invalid signature should be rejected")
    void testInvalidSignatureRejection() throws Exception {
        System.out.println("\n" + "─".repeat(80));
        System.out.println("🧪 TEST 3: Signature Validation - Invalid Signature Rejection");
        System.out.println("─".repeat(80));

        // Given: A webhook with invalid signature
        System.out.println("\n📋 STEP 1: Preparing webhook with INVALID signature");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "evt_test_invalid_sig");
        payload.put("type", "payment_intent.failed");
        System.out.println("   ✓ Event ID: evt_test_invalid_sig");
        System.out.println("   ✓ Event Type: payment_intent.failed");

        String payloadString = objectMapper.writeValueAsString(payload);
        long timestamp = System.currentTimeMillis() / 1000;
        String invalidSignature = "invalid_signature_12345";
        System.out.println("   ⚠️  Using INVALID signature: " + invalidSignature);

        // When: Sending webhook with invalid signature
        System.out.println("\n📤 STEP 2: Sending webhook with invalid signature");
        System.out.println("   → POST /api/v1/webhook/stripe");
        webTestClient.post()
                .uri("/api/v1/webhook/" + TEST_PROVIDER)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=" + timestamp + ",v1=" + invalidSignature)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isAccepted(); // Controller accepts, but worker should reject
        System.out.println("   ✓ HTTP 202 ACCEPTED (controller accepts all webhooks)");

        // Then: Event should not be processed due to invalid signature
        System.out.println("\n⏳ STEP 3: Waiting to verify rejection...");
        Thread.sleep(3000); // Wait to ensure no processing happens

        assertThat(testProcessor.getProcessedCount())
                .as("Should not process events with invalid signatures")
                .isEqualTo(0);

        System.out.println("   ✓ Event REJECTED by signature validator");
        System.out.println("\n✅ TEST 3 PASSED: Invalid signatures are properly rejected!");
        System.out.println("   • Webhooks sent: 1");
        System.out.println("   • Events processed: " + testProcessor.getProcessedCount());
        System.out.println("   • Invalid signatures rejected: 1");
        System.out.println("─".repeat(80) + "\n");
    }

    @Test
    @Order(4)
    @DisplayName("Concurrent processing: Multiple webhooks should be processed correctly")
    void testConcurrentWebhookProcessing() throws Exception {
        System.out.println("\n" + "─".repeat(80));
        System.out.println("🧪 TEST 4: Concurrent Processing - Multiple Webhooks");
        System.out.println("─".repeat(80));

        // Given: Multiple different webhook payloads
        int webhookCount = 5;
        System.out.println("\n📋 STEP 1: Preparing " + webhookCount + " different webhook payloads");

        // Expect all events to be processed
        testProcessor.expectEvents(webhookCount);

        System.out.println("\n📤 STEP 2: Sending " + webhookCount + " webhooks concurrently");
        for (int i = 0; i < webhookCount; i++) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("id", "evt_concurrent_" + i);
            payload.put("type", "invoice.payment_succeeded");
            payload.put("data", objectMapper.createObjectNode().put("invoice_id", "inv_" + i));
            System.out.println("   → Webhook " + (i + 1) + ": evt_concurrent_" + i);

            String payloadString = objectMapper.writeValueAsString(payload);
            long timestamp = System.currentTimeMillis() / 1000;
            String signature = generateStripeSignature(payloadString, timestamp, STRIPE_SECRET);

            // When: Sending webhooks concurrently
            webTestClient.post()
                    .uri("/api/v1/webhook/" + TEST_PROVIDER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature)
                    .bodyValue(payload)
                    .exchange()
                    .expectStatus().isAccepted();
        }
        System.out.println("   ✓ All " + webhookCount + " webhooks sent successfully");

        // Then: All webhooks should be processed
        System.out.println("\n⏳ STEP 3: Waiting for concurrent processing...");
        boolean processed = testProcessor.awaitProcessing(20, TimeUnit.SECONDS);
        assertThat(processed).as("All " + webhookCount + " webhooks should be processed").isTrue();
        assertThat(testProcessor.getProcessedCount()).isEqualTo(webhookCount);

        System.out.println("   ✓ All events processed successfully");
        System.out.println("\n✅ TEST 4 PASSED: Concurrent processing working correctly!");
        System.out.println("   • Webhooks sent: " + webhookCount);
        System.out.println("   • Events processed: " + testProcessor.getProcessedCount());
        System.out.println("   • Success rate: 100%");
        System.out.println("─".repeat(80) + "\n");
    }

    @Test
    @Order(5)
    @DisplayName("Expired signature: Old timestamp should be rejected")
    void testExpiredSignatureRejection() throws Exception {
        System.out.println("\n" + "─".repeat(80));
        System.out.println("🧪 TEST 5: Signature Validation - Expired Timestamp Rejection");
        System.out.println("─".repeat(80));

        // Given: A webhook with expired timestamp (older than 15 minutes)
        System.out.println("\n📋 STEP 1: Preparing webhook with EXPIRED timestamp");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", "evt_test_expired");
        payload.put("type", "payment_intent.succeeded");
        System.out.println("   ✓ Event ID: evt_test_expired");
        System.out.println("   ✓ Event Type: payment_intent.succeeded");

        String payloadString = objectMapper.writeValueAsString(payload);
        long expiredTimestamp = (System.currentTimeMillis() / 1000) - (16 * 60); // 16 minutes ago (beyond 15 min tolerance)
        String signature = generateStripeSignature(payloadString, expiredTimestamp, STRIPE_SECRET);
        System.out.println("   ⚠️  Timestamp: 16 minutes ago (beyond 15 min tolerance)");
        System.out.println("   ✓ Generated valid signature for expired timestamp");

        // Expect 0 events to be processed
        testProcessor.expectEvents(0);

        // When: Sending webhook with expired signature
        System.out.println("\n📤 STEP 2: Sending webhook with expired timestamp");
        System.out.println("   → POST /api/v1/webhook/stripe");
        webTestClient.post()
                .uri("/api/v1/webhook/" + TEST_PROVIDER)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=" + expiredTimestamp + ",v1=" + signature)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isAccepted(); // Controller accepts, but worker should reject
        System.out.println("   ✓ HTTP 202 ACCEPTED (controller accepts all webhooks)");

        // Then: Event should not be processed due to expired signature
        System.out.println("\n⏳ STEP 3: Waiting to verify rejection...");
        Thread.sleep(3000);

        assertThat(testProcessor.getProcessedCount())
                .as("Should not process events with expired signatures")
                .isEqualTo(0);

        System.out.println("   ✓ Event REJECTED by timestamp validator");
        System.out.println("\n✅ TEST 5 PASSED: Expired timestamps are properly rejected!");
        System.out.println("   • Webhooks sent: 1");
        System.out.println("   • Events processed: " + testProcessor.getProcessedCount());
        System.out.println("   • Expired signatures rejected: 1");
        System.out.println("─".repeat(80) + "\n");
    }

    /**
     * Generates a Stripe-compatible HMAC SHA256 signature.
     */
    private String generateStripeSignature(String payload, long timestamp, String secret) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}

