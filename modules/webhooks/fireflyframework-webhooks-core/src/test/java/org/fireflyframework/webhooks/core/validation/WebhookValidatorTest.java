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

package org.fireflyframework.webhooks.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.core.config.WebhookSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebhookValidator.
 */
@DisplayName("WebhookValidator Tests")
class WebhookValidatorTest {

    private WebhookValidator validator;
    private WebhookSecurityProperties securityProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        securityProperties = new WebhookSecurityProperties();
        securityProperties.setValidateProviderName(true);
        securityProperties.setProviderNamePattern("^[a-z0-9-]+$");
        securityProperties.setValidatePayloadSize(true);
        securityProperties.setMaxPayloadSize(1024 * 1024); // 1MB
        securityProperties.setRequireContentType(true);
        securityProperties.setAllowedContentTypes(List.of("application/json"));
        securityProperties.setEnableIpWhitelist(false);

        validator = new WebhookValidator(securityProperties);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should validate valid provider name")
    void shouldValidateValidProviderName() {
        assertDoesNotThrow(() -> validator.validateProviderName("stripe"));
        assertDoesNotThrow(() -> validator.validateProviderName("github"));
        assertDoesNotThrow(() -> validator.validateProviderName("paypal-sandbox"));
    }

    @Test
    @DisplayName("Should reject invalid provider name")
    void shouldRejectInvalidProviderName() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validateProviderName("Stripe_Invalid")
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Invalid provider name format"));
    }

    @Test
    @DisplayName("Should reject null provider name")
    void shouldRejectNullProviderName() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validateProviderName(null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should validate payload size within limit")
    void shouldValidatePayloadSizeWithinLimit() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"test\": \"data\"}");
        assertDoesNotThrow(() -> validator.validatePayloadSize(payload));
    }

    @Test
    @DisplayName("Should reject payload exceeding size limit")
    void shouldRejectPayloadExceedingSizeLimit() throws Exception {
        securityProperties.setMaxPayloadSize(10); // Very small limit
        validator = new WebhookValidator(securityProperties);

        JsonNode payload = objectMapper.readTree("{\"test\": \"this is a large payload\"}");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validatePayloadSize(payload)
        );
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should reject null payload")
    void shouldRejectNullPayload() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validatePayloadSize(null)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should validate correct Content-Type")
    void shouldValidateCorrectContentType() {
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("Content-Type", "application/json")
                .build();

        assertDoesNotThrow(() -> validator.validateContentType(request));
    }

    @Test
    @DisplayName("Should validate Content-Type with charset")
    void shouldValidateContentTypeWithCharset() {
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("Content-Type", "application/json; charset=utf-8")
                .build();

        assertDoesNotThrow(() -> validator.validateContentType(request));
    }

    @Test
    @DisplayName("Should reject invalid Content-Type")
    void shouldRejectInvalidContentType() {
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("Content-Type", "text/plain")
                .build();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validateContentType(request)
        );
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should reject missing Content-Type")
    void shouldRejectMissingContentType() {
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .build();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validateContentType(request)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should allow all IPs when whitelist is disabled")
    void shouldAllowAllIpsWhenWhitelistDisabled() {
        securityProperties.setEnableIpWhitelist(false);
        validator = new WebhookValidator(securityProperties);

        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();

        assertDoesNotThrow(() -> validator.validateIpWhitelist("stripe", request));
    }

    @Test
    @DisplayName("Should allow whitelisted IP")
    void shouldAllowWhitelistedIp() {
        securityProperties.setEnableIpWhitelist(true);
        securityProperties.setIpWhitelist(Map.of("stripe", List.of("192.168.1.100")));
        validator = new WebhookValidator(securityProperties);

        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .remoteAddress(new InetSocketAddress("192.168.1.100", 8080))
                .build();

        assertDoesNotThrow(() -> validator.validateIpWhitelist("stripe", request));
    }

    @Test
    @DisplayName("Should allow IP in CIDR range")
    void shouldAllowIpInCidrRange() {
        securityProperties.setEnableIpWhitelist(true);
        securityProperties.setIpWhitelist(Map.of("stripe", List.of("192.168.1.0/24")));
        validator = new WebhookValidator(securityProperties);

        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .remoteAddress(new InetSocketAddress("192.168.1.150", 8080))
                .build();

        assertDoesNotThrow(() -> validator.validateIpWhitelist("stripe", request));
    }

    @Test
    @DisplayName("Should reject non-whitelisted IP")
    void shouldRejectNonWhitelistedIp() {
        securityProperties.setEnableIpWhitelist(true);
        securityProperties.setIpWhitelist(Map.of("stripe", List.of("192.168.1.100")));
        validator = new WebhookValidator(securityProperties);

        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                .build();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validateIpWhitelist("stripe", request)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    @DisplayName("Should handle X-Forwarded-For header")
    void shouldHandleXForwardedForHeader() {
        securityProperties.setEnableIpWhitelist(true);
        securityProperties.setIpWhitelist(Map.of("stripe", List.of("203.0.113.1")));
        validator = new WebhookValidator(securityProperties);

        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("X-Forwarded-For", "203.0.113.1, 192.168.1.1")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                .build();

        assertDoesNotThrow(() -> validator.validateIpWhitelist("stripe", request));
    }

    @Test
    @DisplayName("Should validate complete request successfully")
    void shouldValidateCompleteRequestSuccessfully() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"event\": \"test\"}");
        ServerHttpRequest request = MockServerHttpRequest
                .post("/webhook/stripe")
                .header("Content-Type", "application/json")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                .build();

        assertDoesNotThrow(() -> validator.validateRequest("stripe", payload, request));
    }

    @Test
    @DisplayName("Should allow provider when validation is disabled")
    void shouldAllowProviderWhenValidationDisabled() {
        securityProperties.setValidateProviderName(false);
        validator = new WebhookValidator(securityProperties);

        assertDoesNotThrow(() -> validator.validateProviderName("Invalid_Provider_Name!"));
    }
}

