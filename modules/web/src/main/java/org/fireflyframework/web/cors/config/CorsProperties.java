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

package org.fireflyframework.web.cors.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for CORS settings.
 *
 * <p>Secure by default: when no origins are configured, CORS is effectively
 * disabled (no Access-Control-Allow-Origin header is sent).</p>
 *
 * <p>Usage in application.yml:</p>
 * <pre>
 * firefly:
 *   web:
 *     cors:
 *       allowed-origins:
 *         - "https://app.example.com"
 *         - "https://admin.example.com"
 *       allowed-methods:
 *         - GET
 *         - POST
 *         - PUT
 *         - DELETE
 *       allowed-headers:
 *         - "*"
 *       exposed-headers:
 *         - "X-Transaction-Id"
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "firefly.web.cors")
@Getter
@Setter
public class CorsProperties {

    /**
     * Whether CORS support is enabled.
     */
    private boolean enabled = true;

    /**
     * URL path pattern for CORS mapping.
     */
    private String pathPattern = "/**";

    /**
     * Allowed origins. Must be explicitly configured for production use.
     * Empty list means no origins are allowed (secure by default).
     */
    private List<String> allowedOrigins = List.of();

    /**
     * Allowed HTTP methods.
     */
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    /**
     * Allowed request headers.
     */
    private List<String> allowedHeaders = List.of("*");

    /**
     * Response headers exposed to the client.
     */
    private List<String> exposedHeaders = List.of("X-Transaction-Id");

    /**
     * Whether credentials (cookies, authorization headers) are supported.
     */
    private boolean allowCredentials = false;

    /**
     * Max age (in seconds) for the preflight response cache.
     */
    private long maxAge = 3600;
}
