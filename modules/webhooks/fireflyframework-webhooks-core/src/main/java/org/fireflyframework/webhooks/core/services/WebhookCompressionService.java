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

package org.fireflyframework.webhooks.core.services;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service for compressing and decompressing webhook payloads.
 * <p>
 * Compression reduces network bandwidth and Kafka storage for large payloads.
 */
public interface WebhookCompressionService {

    /**
     * Compresses a JSON payload if it exceeds the minimum size threshold.
     *
     * @param payload the JSON payload
     * @return compressed bytes, or null if compression not needed
     */
    byte[] compress(JsonNode payload);

    /**
     * Decompresses a compressed payload back to JSON.
     *
     * @param compressed the compressed bytes
     * @return the decompressed JSON payload
     */
    JsonNode decompress(byte[] compressed);

    /**
     * Checks if a payload should be compressed based on size.
     *
     * @param payload the JSON payload
     * @return true if payload should be compressed
     */
    boolean shouldCompress(JsonNode payload);

    /**
     * Gets the compression ratio achieved.
     *
     * @param originalSize original size in bytes
     * @param compressedSize compressed size in bytes
     * @return compression ratio (e.g., 0.5 = 50% reduction)
     */
    default double getCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) {
            return 0.0;
        }
        return 1.0 - ((double) compressedSize / originalSize);
    }
}

