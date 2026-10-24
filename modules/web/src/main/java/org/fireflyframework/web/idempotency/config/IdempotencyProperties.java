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


package org.fireflyframework.web.idempotency.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the idempotency module.
 * These properties can be set in application.yml or application.properties.
 *
 * <p>Cache provider configuration is now handled by fireflyframework-cache.
 * Configure cache providers using the firefly.cache.* properties.</p>
 *
 * <p>All idempotency cache entries are stored with keys prefixed as ":idempotency:" which,
 * combined with the cache's own prefix, results in keys like "firefly:cache:default::idempotency:{key}".</p>
 *
 * Example configuration:
 * <pre>
 * firefly:
 *   web:
 *     idempotency:
 *       header-name: X-Idempotency-Key
 *       cache:
 *         ttl-hours: 24  # Time-to-live for cached responses
 *
 * # Cache provider configuration (handled by fireflyframework-cache)
 * firefly:
 *   cache:
 *     default-cache-type: CAFFEINE  # or REDIS, AUTO, NOOP
 *     caffeine:
 *       default:
 *         maximum-size: 10000
 *         expire-after-write: PT24H
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "firefly.web.idempotency")
public class IdempotencyProperties {

    /**
     * Whether the idempotency module is enabled. Defaults to true.
     */
    private boolean enabled = true;

    /**
     * Name of the HTTP header carrying the idempotency key. Default is "X-Idempotency-Key".
     */
    private String headerName = "X-Idempotency-Key";

    /**
     * Cache configuration properties
     */
    private Cache cache = new Cache();

    /** Stable service/environment namespace; auto-configuration falls back to spring.application.name. */
    private String namespace;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /** Maximum buffered keyed request size; larger requests fail before handler execution. */
    private int maxRequestBodyBytes = 1024 * 1024;

    /** Maximum buffered response size. Streaming endpoints should disable this filter. */
    private int maxResponseBodyBytes = 4 * 1024 * 1024;

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("maxRequestBodyBytes must be positive");
        }
        this.maxRequestBodyBytes = value;
    }

    public int getMaxResponseBodyBytes() {
        return maxResponseBodyBytes;
    }

    public void setMaxResponseBodyBytes(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("maxResponseBodyBytes must be positive");
        }
        this.maxResponseBodyBytes = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Cache-specific configuration properties.
     *
     * <p>Note: Cache provider configuration (Caffeine, Redis, etc.) is now handled
     * by fireflyframework-cache. Use firefly.cache.* properties to configure cache providers.</p>
     *
     * <p>All idempotency cache entries are stored with the key prefix ":idempotency:" which,
     * combined with the cache's own prefix, results in keys like "firefly:cache:default::idempotency:{key}".</p>
     */
    public static class Cache {
        /**
         * Time-to-live in hours for cached responses.
         * Default is 24 hours.
         */
        private int ttlHours = 24;

        public int getTtlHours() {
            return ttlHours;
        }

        public void setTtlHours(int ttlHours) {
            this.ttlHours = ttlHours;
        }
    }
}
