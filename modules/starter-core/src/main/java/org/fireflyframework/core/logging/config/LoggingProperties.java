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


package org.fireflyframework.core.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the enhanced structured logging system.
 * <p>
 * These properties allow customization of JSON parsing behavior, performance limits,
 * and caching settings for the logging system.
 * <p>
 * Properties can be configured in application.yml or application.properties using the
 * 'logging' prefix.
 */
@Validated
@ConfigurationProperties(prefix = "firefly.logging")
public class LoggingProperties {

    /**
     * JSON parsing configuration
     */
    private Json json = new Json();

    /**
     * Performance configuration
     */
    private Performance performance = new Performance();

    /**
     * Cache configuration
     */
    private Cache cache = new Cache();

    public Json getJson() {
        return json;
    }

    public void setJson(Json json) {
        this.json = json;
    }

    public Performance getPerformance() {
        return performance;
    }

    public void setPerformance(Performance performance) {
        this.performance = performance;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * JSON parsing specific configuration
     */
    public static class Json {
        
        /**
         * Enable recursive JSON parsing in log messages and data values
         */
        private boolean recursiveParsingEnabled = true;

        /**
         * Maximum recursion depth for nested JSON parsing
         */
        private int maxRecursionDepth = 10;

        /**
         * Maximum size in bytes for JSON strings to be parsed
         */
        private int maxJsonSizeBytes = 1024 * 1024; // 1MB

        /**
         * Enable strict JSON validation (requires proper JSON format)
         */
        private boolean strictValidation = false;

        public boolean isRecursiveParsingEnabled() {
            return recursiveParsingEnabled;
        }

        public void setRecursiveParsingEnabled(boolean recursiveParsingEnabled) {
            this.recursiveParsingEnabled = recursiveParsingEnabled;
        }

        public int getMaxRecursionDepth() {
            return maxRecursionDepth;
        }

        public void setMaxRecursionDepth(int maxRecursionDepth) {
            this.maxRecursionDepth = maxRecursionDepth;
        }

        public int getMaxJsonSizeBytes() {
            return maxJsonSizeBytes;
        }

        public void setMaxJsonSizeBytes(int maxJsonSizeBytes) {
            this.maxJsonSizeBytes = maxJsonSizeBytes;
        }

        public boolean isStrictValidation() {
            return strictValidation;
        }

        public void setStrictValidation(boolean strictValidation) {
            this.strictValidation = strictValidation;
        }
    }

    /**
     * Performance related configuration
     */
    public static class Performance {

        /**
         * Enable performance metrics collection
         */
        private boolean metricsEnabled = true;

        /**
         * Enable debug logging for JSON parsing operations
         */
        private boolean debugLoggingEnabled = false;

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }

        public boolean isDebugLoggingEnabled() {
            return debugLoggingEnabled;
        }

        public void setDebugLoggingEnabled(boolean debugLoggingEnabled) {
            this.debugLoggingEnabled = debugLoggingEnabled;
        }
    }

    /**
     * Caching configuration
     */
    public static class Cache {

        /**
         * Enable JSON validation and parsing caching
         */
        private boolean enabled = true;

        /**
         * Maximum number of entries in validation cache
         */
        private int maxValidationCacheSize = 1000;

        /**
         * Maximum number of entries in parsing cache
         */
        private int maxParseCacheSize = 1000;

        /**
         * Cache eviction strategy (CLEAR_ALL, LRU)
         */
        private CacheEvictionStrategy evictionStrategy = CacheEvictionStrategy.CLEAR_ALL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxValidationCacheSize() {
            return maxValidationCacheSize;
        }

        public void setMaxValidationCacheSize(int maxValidationCacheSize) {
            this.maxValidationCacheSize = maxValidationCacheSize;
        }

        public int getMaxParseCacheSize() {
            return maxParseCacheSize;
        }

        public void setMaxParseCacheSize(int maxParseCacheSize) {
            this.maxParseCacheSize = maxParseCacheSize;
        }

        public CacheEvictionStrategy getEvictionStrategy() {
            return evictionStrategy;
        }

        public void setEvictionStrategy(CacheEvictionStrategy evictionStrategy) {
            this.evictionStrategy = evictionStrategy;
        }
    }

    /**
     * Cache eviction strategies
     */
    public enum CacheEvictionStrategy {
        /**
         * Clear all cache entries when cache is full
         */
        CLEAR_ALL,
        
        /**
         * Use Least Recently Used eviction strategy
         */
        LRU
    }
}