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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WebhookClientHelper.
 */
class WebhookClientHelperTest {

    private WebhookClientHelper helper;
    private WebhookClientHelper.WebhookConfig config;

    @BeforeEach
    void setUp() {
        config = WebhookClientHelper.WebhookConfig.builder()
            .secret("test-secret")
            .signatureHeader("X-Webhook-Signature")
            .retryEnabled(false)
            .timeout(Duration.ofSeconds(30))
            .build();

        helper = new WebhookClientHelper("https://api.example.com/webhooks", config);
    }

    @Test
    void shouldGenerateSignature() {
        // Given
        String payload = "{\"event\":\"user.created\"}";

        // When
        String signature = helper.generateSignature(payload);

        // Then
        assertThat(signature).isNotNull();
        assertThat(signature).isNotEmpty();
    }

    @Test
    void shouldVerifyValidSignature() {
        // Given
        String payload = "{\"event\":\"user.created\"}";
        String signature = helper.generateSignature(payload);

        // When
        boolean isValid = helper.verifySignature(payload, signature);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectInvalidSignature() {
        // Given
        String payload = "{\"event\":\"user.created\"}";
        String invalidSignature = "invalid-signature";

        // When
        boolean isValid = helper.verifySignature(payload, invalidSignature);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void shouldSubscribeToEvent() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);

        // When
        helper.subscribe("user.created", event -> callCount.incrementAndGet());

        // Then - Just verify no exception is thrown
        assertThat(callCount.get()).isZero();
    }

    @Test
    void shouldUnsubscribeFromEvent() {
        // Given
        helper.subscribe("user.created", event -> {});

        // When
        helper.unsubscribe("user.created");

        // Then - Just verify no exception is thrown
        assertThat(true).isTrue();
    }

    @Test
    void shouldHandleMultipleSubscriptions() {
        // Given
        helper.subscribe("user.created", event -> {});
        helper.subscribe("user.updated", event -> {});

        // When
        helper.unsubscribe("user.created");
        helper.unsubscribe("user.updated");

        // Then - Just verify no exception is thrown
        assertThat(true).isTrue();
    }

    @Test
    void shouldUseDefaultConfig() {
        // When
        WebhookClientHelper.WebhookConfig defaultConfig = WebhookClientHelper.WebhookConfig.builder()
            .secret("secret")
            .build();

        // Then
        assertThat(defaultConfig.getSecret()).isEqualTo("secret");
        assertThat(defaultConfig.getSignatureHeader()).isEqualTo("X-Webhook-Signature");
        assertThat(defaultConfig.isRetryEnabled()).isTrue();
    }
}

