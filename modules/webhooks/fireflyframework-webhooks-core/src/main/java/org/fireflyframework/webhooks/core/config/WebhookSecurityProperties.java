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

package org.fireflyframework.webhooks.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for webhook security and validation.
 * <p>
 * All properties can be configured via:
 * <ul>
 *   <li>application.yml: {@code firefly.webhooks.security.max-payload-size: 2097152}</li>
 *   <li>Environment variables: {@code FIREFLY_WEBHOOKS_SECURITY_MAX_PAYLOAD_SIZE=2097152}</li>
 *   <li>System properties: {@code -Dfirefly.webhooks.security.max-payload-size=2097152}</li>
 * </ul>
 * <p>
 * Example environment variables:
 * <pre>
 * FIREFLY_WEBHOOKS_SECURITY_MAX_PAYLOAD_SIZE=2097152
 * FIREFLY_WEBHOOKS_SECURITY_VALIDATE_PAYLOAD_SIZE=true
 * FIREFLY_WEBHOOKS_SECURITY_VALIDATE_PROVIDER_NAME=true
 * FIREFLY_WEBHOOKS_SECURITY_PROVIDER_NAME_PATTERN=^[a-z0-9-]+$
 * FIREFLY_WEBHOOKS_SECURITY_ENABLE_IP_WHITELIST=false
 * FIREFLY_WEBHOOKS_SECURITY_ENABLE_REQUEST_VALIDATION=true
 * FIREFLY_WEBHOOKS_SECURITY_REQUIRE_CONTENT_TYPE=true
 * </pre>
 */
@Configuration
@Validated
@ConfigurationProperties(prefix = "firefly.webhooks.security")
@Data
public class WebhookSecurityProperties {

    /**
     * Maximum payload size in bytes (default: 1MB)
     */
    private long maxPayloadSize = 1048576; // 1MB

    /**
     * Enable payload size validation
     */
    private boolean validatePayloadSize = true;

    /**
     * Enable provider name validation
     */
    private boolean validateProviderName = true;

    /**
     * Allowed provider name pattern (default: lowercase alphanumeric and hyphens)
     */
    private String providerNamePattern = "^[a-z0-9-]+$";

    /**
     * Enable IP whitelisting
     */
    private boolean enableIpWhitelist = false;

    /**
     * IP whitelist per provider
     * Example:
     * stripe:
     *   - "54.187.174.169"
     *   - "54.187.205.235"
     * github:
     *   - "140.82.112.0/20"
     */
    private Map<String, List<String>> ipWhitelist = new HashMap<>();

    /**
     * Note: HTTP-level idempotency (X-Idempotency-Key header) is now handled by
     * lib-common-web's IdempotencyWebFilter. Configure it using:
     * <pre>
     * idempotency:
     *   header-name: X-Idempotency-Key
     *   cache:
     *     ttl-hours: 24
     * </pre>
     */

    /**
     * Enable request validation
     */
    private boolean enableRequestValidation = true;

    /**
     * Allowed HTTP methods
     */
    private List<String> allowedMethods = List.of("POST");

    /**
     * Require Content-Type header
     */
    private boolean requireContentType = true;

    /**
     * Allowed Content-Type values
     */
    private List<String> allowedContentTypes = List.of(
            "application/json",
            "application/x-www-form-urlencoded"
    );
}

