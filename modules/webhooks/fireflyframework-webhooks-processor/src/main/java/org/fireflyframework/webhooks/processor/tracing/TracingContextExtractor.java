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

package org.fireflyframework.webhooks.processor.tracing;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for extracting distributed tracing context from Kafka message headers.
 * <p>
 * Supports B3 propagation format for distributed tracing.
 */
@Slf4j
public class TracingContextExtractor {

    private static final String TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String SPAN_ID_HEADER = "X-B3-SpanId";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String REQUEST_ID_KEY = "requestId";

    /**
     * Extracts tracing context from Kafka message headers and sets it in MDC.
     * <p>
     * This allows logs in the worker to be correlated with the original HTTP request.
     *
     * @param message the Kafka message
     * @return a map of tracing context values
     */
    public static Map<String, String> extractAndSetMDC(Message<?> message) {
        Map<String, String> tracingContext = new HashMap<>();

        // Extract trace ID
        String traceId = extractHeader(message, TRACE_ID_HEADER);
        if (traceId != null) {
            tracingContext.put(TRACE_ID_KEY, traceId);
            MDC.put(TRACE_ID_KEY, traceId);
            log.debug("Extracted traceId from Kafka headers: {}", traceId);
        }

        // Extract span ID
        String spanId = extractHeader(message, SPAN_ID_HEADER);
        if (spanId != null) {
            tracingContext.put(SPAN_ID_KEY, spanId);
            MDC.put(SPAN_ID_KEY, spanId);
            log.debug("Extracted spanId from Kafka headers: {}", spanId);
        }

        // Extract request ID
        String requestId = extractHeader(message, REQUEST_ID_HEADER);
        if (requestId != null) {
            tracingContext.put(REQUEST_ID_KEY, requestId);
            MDC.put(REQUEST_ID_KEY, requestId);
            log.debug("Extracted requestId from Kafka headers: {}", requestId);
        }

        return tracingContext;
    }

    /**
     * Clears tracing context from MDC.
     * <p>
     * Should be called after processing is complete to avoid leaking context to other threads.
     */
    public static void clearMDC() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(REQUEST_ID_KEY);
    }

    /**
     * Extracts a header value from a Kafka message.
     *
     * @param message the Kafka message
     * @param headerName the header name
     * @return the header value, or null if not present
     */
    private static String extractHeader(Message<?> message, String headerName) {
        Object headerValue = message.getHeaders().get(headerName);
        
        if (headerValue == null) {
            return null;
        }

        // Handle byte array (Kafka native format)
        if (headerValue instanceof byte[]) {
            return new String((byte[]) headerValue, StandardCharsets.UTF_8);
        }

        // Handle string
        if (headerValue instanceof String) {
            return (String) headerValue;
        }

        // Fallback to toString
        return headerValue.toString();
    }

    /**
     * Extracts Kafka metadata for logging.
     *
     * @param message the Kafka message
     * @return a map of Kafka metadata
     */
    public static Map<String, Object> extractKafkaMetadata(Message<?> message) {
        Map<String, Object> metadata = new HashMap<>();

        // Extract topic
        Object topic = message.getHeaders().get(KafkaHeaders.RECEIVED_TOPIC);
        if (topic != null) {
            metadata.put("topic", topic.toString());
        }

        // Extract partition
        Object partition = message.getHeaders().get(KafkaHeaders.RECEIVED_PARTITION);
        if (partition != null) {
            metadata.put("partition", partition);
        }

        // Extract offset
        Object offset = message.getHeaders().get(KafkaHeaders.OFFSET);
        if (offset != null) {
            metadata.put("offset", offset);
        }

        // Extract timestamp
        Object timestamp = message.getHeaders().get(KafkaHeaders.RECEIVED_TIMESTAMP);
        if (timestamp != null) {
            metadata.put("timestamp", timestamp);
        }

        return metadata;
    }
}

