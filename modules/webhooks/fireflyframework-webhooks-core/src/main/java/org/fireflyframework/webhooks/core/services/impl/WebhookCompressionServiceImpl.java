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

package org.fireflyframework.webhooks.core.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.core.config.CompressionProperties;
import org.fireflyframework.webhooks.core.services.WebhookCompressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of webhook compression service using GZIP.
 * <p>
 * GZIP provides good compression ratio and is widely supported.
 * For better performance, consider using LZ4 or ZSTD in the future.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firefly.webhooks.compression.enabled", havingValue = "true")
public class WebhookCompressionServiceImpl implements WebhookCompressionService {

    private final CompressionProperties compressionProperties;
    private final ObjectMapper objectMapper;

    @Override
    public byte[] compress(JsonNode payload) {
        if (!shouldCompress(payload)) {
            return null;
        }

        try {
            byte[] originalBytes = objectMapper.writeValueAsBytes(payload);
            long originalSize = originalBytes.length;

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
                gzipStream.write(originalBytes);
            }

            byte[] compressed = byteStream.toByteArray();
            long compressedSize = compressed.length;
            double ratio = getCompressionRatio(originalSize, compressedSize);

            log.debug("Compressed payload: {} bytes -> {} bytes ({}% reduction)",
                    originalSize, compressedSize, String.format("%.1f", ratio * 100));

            return compressed;
        } catch (IOException e) {
            log.error("Error compressing payload", e);
            return null;
        }
    }

    @Override
    public JsonNode decompress(byte[] compressed) {
        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(compressed);
            try (GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
                return objectMapper.readTree(gzipStream);
            }
        } catch (IOException e) {
            log.error("Error decompressing payload", e);
            throw new RuntimeException("Failed to decompress payload", e);
        }
    }

    @Override
    public boolean shouldCompress(JsonNode payload) {
        if (payload == null) {
            return false;
        }

        try {
            long payloadSize = objectMapper.writeValueAsBytes(payload).length;
            return payloadSize >= compressionProperties.getMinSize();
        } catch (IOException e) {
            log.error("Error checking payload size for compression", e);
            return false;
        }
    }
}

