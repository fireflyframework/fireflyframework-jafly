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

package org.fireflyframework.cache.adapter.postgres;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration for the PostgreSQL R2DBC cache adapter.
 * <p>
 * Holds the cache identity (name, key prefix), the default time-to-live applied
 * to entries when none is supplied explicitly, and the physical storage location
 * (schema and table) for cache rows.
 */
@Data
@Builder
public class PostgresCacheConfig {

    /**
     * Logical cache name exposed in metadata. Physical storage combines the
     * separate service namespace, this name and the effective feature prefix.
     */
    private final String cacheName;

    /**
     * Feature prefix applied to every key. The four-argument direct adapter
     * constructor also treats this required prefix as its explicit service namespace. Used to namespace keys within a cache.
     */
    private final String keyPrefix;

    /**
     * Default time-to-live applied when no explicit TTL is provided on a put.
     */
    @Builder.Default
    private final Duration defaultTtl = Duration.ofMinutes(30);

    /**
     * PostgreSQL schema that holds the cache table.
     */
    @Builder.Default
    private final String schema = "firefly_cache";

    /**
     * Name of the table that stores cache entries.
     */
    @Builder.Default
    private final String cacheTable = "firefly_cache_entries";

    public String qualifiedTable() {
        return quoteIdentifier(schema != null ? schema : "firefly_cache") + "."
                + quoteIdentifier(cacheTable != null ? cacheTable : "firefly_cache_entries");
    }

    public static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("PostgreSQL cache identifier must contain 1 to 63 UTF-8 bytes without NUL");
        }
        requireWellFormedUtf16(identifier);
        if (identifier.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 63) {
            throw new IllegalArgumentException("PostgreSQL cache identifier must contain 1 to 63 UTF-8 bytes without NUL");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public String effectiveKeyPrefix(String serviceNamespace) {
        String prefix = keyPrefix != null ? keyPrefix : serviceNamespace;
        return prefix.trim().isEmpty() ? "cache" : prefix;
    }

    public void validate(String name) {
        validate(name, keyPrefix);
    }

    public void validate(String name, String serviceNamespace) {
        qualifiedTable();
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.codePointCount(0, name.length()) > 255) {
            throw new IllegalArgumentException("PostgreSQL cache name must contain 1 to 255 characters without NUL");
        }
        requireWellFormedUtf16(name);
        if (serviceNamespace == null || serviceNamespace.isBlank() || serviceNamespace.indexOf('\0') >= 0
                || serviceNamespace.codePointCount(0, serviceNamespace.length()) > 1024) {
            throw new IllegalArgumentException("PostgreSQL cache service namespace must contain 1 to 1024 characters without NUL");
        }
        requireWellFormedUtf16(serviceNamespace);
        if (keyPrefix != null) requireWellFormedUtf16(keyPrefix);
        String prefix = effectiveKeyPrefix(serviceNamespace);
        if (prefix.indexOf('\0') >= 0 || prefix.codePointCount(0, prefix.length())
                + name.codePointCount(0, name.length()) + 2 >= 1024) {
            throw new IllegalArgumentException("PostgreSQL cache prefix leaves no space for a cache key");
        }
        if (defaultTtl == null || defaultTtl.isNegative()) {
            throw new IllegalArgumentException("PostgreSQL cache default TTL must be nonnegative");
        }
    }

    private static void requireWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("PostgreSQL cache names must contain well-formed Unicode");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("PostgreSQL cache names must contain well-formed Unicode");
            }
        }
    }
}
