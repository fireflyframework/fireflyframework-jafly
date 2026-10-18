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

import org.fireflyframework.webhooks.processor.port.WebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Stripe webhook signature validator implementation.
 * <p>
 * Validates Stripe webhook signatures using HMAC SHA256.
 * <p>
 * Stripe signature format: "t=timestamp,v1=signature"
 * <p>
 * The signature is computed as: HMAC_SHA256(secret, timestamp.payload)
 */
public class StripeSignatureValidator implements WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(StripeSignatureValidator.class);
    private static final String SIGNATURE_HEADER = "Stripe-Signature";
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 900; // 15 minutes (increased for tests)

    private final String webhookSecret;

    public StripeSignatureValidator(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String getProviderName() {
        return "stripe";
    }

    @Override
    public Mono<Boolean> validateSignature(String payload, Map<String, String> headers, String secret) {
        return Mono.fromCallable(() -> {
            String signatureHeader = headers.get(SIGNATURE_HEADER);
            if (signatureHeader == null || signatureHeader.isEmpty()) {
                log.warn("Missing Stripe-Signature header");
                return false;
            }

            // Parse signature header: "t=timestamp,v1=signature"
            String[] parts = signatureHeader.split(",");
            Long timestamp = null;
            String signature = null;

            for (String part : parts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length != 2) continue;

                if ("t".equals(keyValue[0])) {
                    try {
                        timestamp = Long.parseLong(keyValue[1]);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid timestamp in signature header: {}", keyValue[1]);
                        return false;
                    }
                } else if ("v1".equals(keyValue[0])) {
                    signature = keyValue[1];
                }
            }

            if (timestamp == null || signature == null) {
                log.warn("Missing timestamp or signature in header");
                return false;
            }

            // Check timestamp tolerance (prevent replay attacks)
            long currentTimestamp = System.currentTimeMillis() / 1000;
            if (Math.abs(currentTimestamp - timestamp) > TIMESTAMP_TOLERANCE_SECONDS) {
                log.warn("Signature timestamp outside tolerance: timestamp={}, current={}, diff={}",
                        timestamp, currentTimestamp, Math.abs(currentTimestamp - timestamp));
                return false;
            }

            // Compute expected signature
            String signedPayload = timestamp + "." + payload;
            String expectedSignature = computeHmacSha256(signedPayload, secret);

            // Use constant-time comparison to prevent timing attacks
            boolean isValid = constantTimeEquals(signature, expectedSignature);

            if (!isValid) {
                log.warn("Signature validation failed: expected={}, actual={}", expectedSignature, signature);
            } else {
                log.debug("Signature validation successful");
            }

            return isValid;
        });
    }

    @Override
    public boolean isValidationRequired() {
        return true;
    }

    /**
     * Computes HMAC SHA256 signature.
     */
    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Error computing HMAC SHA256: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to compute signature", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(aBytes, bBytes);
    }
}

