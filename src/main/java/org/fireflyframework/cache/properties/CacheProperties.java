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

package org.fireflyframework.cache.properties;

import org.fireflyframework.cache.core.CacheType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the Cache library.
 * <p>
 * Simplified configuration for a single cache with optional fallback support.
 * The cache type is selected based on availability and preference.
 */
@ConfigurationProperties(prefix = "firefly.cache")
@Validated
@Data
public class CacheProperties {

    /**
     * Whether the cache library is enabled.
     */
    private boolean enabled = true;

    /**
     * Preferred cache type to use.
     * <ul>
     *   <li>CAFFEINE - Use in-memory cache (always available)</li>
     *   <li>REDIS - Use distributed cache (requires Redis dependencies)</li>
     *   <li>AUTO - Automatically select based on availability (Redis preferred if available)</li>
     * </ul>
     * Defaults to CAFFEINE for optimal performance and reliability.
     */
    @NotNull(message = "Default cache type cannot be null")
    private CacheType defaultCacheType = CacheType.CAFFEINE;

    /**
     * Whether to enable metrics collection.
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to enable health checks.
     */
    private boolean healthEnabled = true;

    /**
     * Whether to enable cache statistics.
     */
    private boolean statsEnabled = true;

    /**
     * Caffeine cache configuration.
     */
    @Valid
    @NotNull
    private CaffeineConfig caffeine = new CaffeineConfig();

    /**
     * Redis cache configuration.
     */
    @Valid
    @NotNull
    private RedisConfig redis = new RedisConfig();

    /**
     * PostgreSQL cache configuration.
     */
    @Valid
    @NotNull
    private PostgresConfig postgres = new PostgresConfig();

    /**
     * Smart (L1+L2) cache configuration.
     */
    @Valid
    @NotNull
    private SmartConfig smart = new SmartConfig();

    /**
     * Caffeine cache configuration.
     */
    @Data
    public static class CaffeineConfig {
        /**
         * Cache name for the Caffeine adapter.
         */
        private String cacheName = "default";


        /**
         * Whether Caffeine cache is enabled.
         */
        private boolean enabled = true;

        /**
         * Key prefix for all cache entries.
         */
        private String keyPrefix = "firefly:cache";

        /**
         * Maximum number of entries the cache may contain.
         */
        private Long maximumSize = 1000L;

        /**
         * Duration after which entries should be automatically removed
         * after the entry's creation or replacement.
         */
        private Duration expireAfterWrite = Duration.ofHours(1);

        /**
         * Duration after which entries should be automatically removed
         * after the last access.
         */
        private Duration expireAfterAccess;

        /**
         * Duration after which entries are eligible for automatic refresh.
         */
        private Duration refreshAfterWrite;

        /**
         * Whether to record cache statistics.
         */
        private boolean recordStats = true;

        /**
         * Whether to use weak references for keys.
         */
        private boolean weakKeys = false;

        /**
         * Whether to use weak references for values.
         */
        private boolean weakValues = false;

        /**
         * Whether to use soft references for values.
         */
        private boolean softValues = false;
    }

    /**
     * Redis cache configuration.
     */
    @Data
    public static class RedisConfig {
        /**
         * Cache name for the Redis adapter.
         */
        private String cacheName = "default";

        /**
         * Whether Redis cache is enabled.
         */
        private boolean enabled = true;

        /**
         * Redis server host.
         */
        private String host = "localhost";

        /**
         * Redis server port.
         */
        private int port = 6379;

        /**
         * Redis database index.
         */
        private int database = 0;

        /**
         * Redis authentication password.
         */
        private String password;

        /**
         * Redis username for ACL authentication.
         */
        private String username;

        /**
         * Connection timeout.
         */
        private Duration connectionTimeout = Duration.ofSeconds(10);

        /**
         * Command timeout.
         */
        private Duration commandTimeout = Duration.ofSeconds(5);

        /**
         * Key prefix for all cache entries.
         */
        private String keyPrefix = "firefly:cache";

        /**
         * Default TTL for cache entries.
         */
        private Duration defaultTtl;

        /**
         * Whether to enable key expiration events.
         */
        private boolean enableKeyspaceNotifications = false;

        /**
         * Maximum number of connections in the pool.
         */
        private int maxPoolSize = 8;

        /**
         * Minimum number of connections in the pool.
         */
        private int minPoolSize = 0;

        /**
         * Whether to use SSL/TLS for connection.
         */
        private boolean ssl = false;

        /**
         * Additional Redis configuration properties.
         */
        private Map<String, String> properties = new HashMap<>();
    }

    /**
     * PostgreSQL cache configuration.
     */
    @Data
    public static class PostgresConfig {
        /**
         * Cache name for the Postgres adapter.
         */
        private String cacheName = "default";

        /**
         * Whether Postgres cache is enabled.
         */
        private boolean enabled = false;

        /**
         * PostgreSQL server host.
         */
        private String host = "localhost";

        /**
         * PostgreSQL server port.
         */
        private int port = 5432;

        /**
         * PostgreSQL database name.
         */
        private String database;

        /**
         * PostgreSQL username.
         */
        private String username;

        /**
         * PostgreSQL password.
         */
        private String password;

        /**
         * PostgreSQL schema for cache tables.
         */
        private String schema = "public";

        /**
         * TLS/SSL mode for the R2DBC connection. Maps to the r2dbc-postgresql
         * {@code SSLMode} enum: {@code disable}, {@code allow}, {@code prefer},
         * {@code require}, {@code verify-ca}, {@code verify-full}, {@code tunnel}.
         * Default {@code disable} for backward compatibility; set to
         * {@code require} against TLS-enforcing servers such as AWS Aurora with
         * {@code rds.force_ssl} enabled (otherwise the connection is rejected with
         * "no pg_hba.conf entry ... no encryption").
         */
        private String sslMode = "disable";

        /**
         * Table name used to store cache entries.
         */
        private String cacheTable = "firefly_cache_entries";

        /**
         * Key prefix for all cache entries.
         */
        private String keyPrefix = "";

        /**
         * Default TTL for cache entries.
         */
        private Duration defaultTtl = Duration.ofMinutes(30);

        /**
         * Whether to auto-create the cache schema/table on startup.
         */
        private boolean autoCreateSchema = true;

        /**
         * Maximum number of connections in the pool.
         */
        private int maxPoolSize = 10;

        /**
         * Minimum number of connections in the pool.
         */
        private int minPoolSize = 1;

        /**
         * Additional Postgres configuration properties.
         */
        private Map<String, Object> properties = new HashMap<>();
    }

    /**
     * Smart (L1+L2) composite cache configuration.
     */
    @Data
    public static class SmartConfig {
        /** Enable smart L1+L2 cache for distributed providers (write-through). */
        private boolean enabled = true;
        /**
         * Preferred write strategy. Only WRITE_THROUGH is implemented now.
         * Possible values in future: WRITE_THROUGH, WRITE_BACK.
         */
        private String writeStrategy = "WRITE_THROUGH";
        /** Whether to backfill L1 on L2 hits. */
        private boolean backfillL1OnRead = true;
    }
}