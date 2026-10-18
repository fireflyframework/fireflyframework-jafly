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

/**
 * Configuration properties for webhook payload compression.
 * <p>
 * Compression reduces network bandwidth and Kafka storage for large payloads.
 * <p>
 * Environment variables:
 * <pre>
 * FIREFLY_WEBHOOKS_COMPRESSION_ENABLED=true
 * FIREFLY_WEBHOOKS_COMPRESSION_MIN_SIZE=1024
 * FIREFLY_WEBHOOKS_COMPRESSION_ALGORITHM=GZIP
 * </pre>
 */
@Configuration
@Validated
@ConfigurationProperties(prefix = "firefly.webhooks.compression")
@Data
public class CompressionProperties {

    /**
     * Enable payload compression
     */
    private boolean enabled = false;

    /**
     * Minimum payload size (in bytes) to trigger compression.
     * Payloads smaller than this will not be compressed.
     * Default: 1KB
     */
    private int minSize = 1024;

    /**
     * Compression algorithm to use
     */
    private CompressionAlgorithm algorithm = CompressionAlgorithm.GZIP;

    /**
     * Compression level (1-9 for GZIP, 1-22 for ZSTD)
     * Higher = better compression but slower
     * Default: 6 (balanced)
     */
    private int level = 6;

    /**
     * Supported compression algorithms
     */
    public enum CompressionAlgorithm {
        /**
         * GZIP compression (widely supported, good compression ratio)
         */
        GZIP,

        /**
         * ZSTD compression (better compression ratio and speed, requires library)
         */
        ZSTD,

        /**
         * LZ4 compression (very fast, lower compression ratio)
         */
        LZ4
    }
}

