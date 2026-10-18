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


package org.fireflyframework.core.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Utility class for enhanced structured logging.
 * <p>
 * This class provides helper methods for creating structured logs in JSON format,
 * making it easier to include contextual information and structured data in log messages.
 * <p>
 * The methods in this class help create logs that are more easily parsed and analyzed
 * by log aggregation and analysis tools.
 */
public final class LoggingUtils {

    private static final Logger log = LoggerFactory.getLogger(LoggingUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Performance optimization: Cache for JSON validation results
    private static final Map<String, Boolean> jsonValidationCache = new ConcurrentHashMap<>();
    private static final Map<String, JsonNode> jsonParseCache = new ConcurrentHashMap<>();
    
    // Configuration constants for performance limits
    private static final int MAX_JSON_SIZE_BYTES = 1024 * 1024; // 1MB limit
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int MAX_RECURSION_DEPTH = 10;
    
    // Metrics for monitoring
    private static final AtomicLong jsonValidationHits = new AtomicLong(0);
    private static final AtomicLong jsonValidationMisses = new AtomicLong(0);
    private static final AtomicLong jsonParseHits = new AtomicLong(0);
    private static final AtomicLong jsonParseMisses = new AtomicLong(0);
    private static final AtomicLong oversizedJsonCount = new AtomicLong(0);
    private static final AtomicLong recursionLimitExceeded = new AtomicLong(0);

    private LoggingUtils() {
        // Utility class, no instantiation
    }

    /**
     * Checks if a string is valid JSON with caching and size limits.
     *
     * @param str The string to check
     * @return true if the string is valid JSON, false otherwise
     */
    private static boolean isValidJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = str.trim();
        
        // Check size limits to prevent memory issues
        if (trimmed.length() > MAX_JSON_SIZE_BYTES) {
            oversizedJsonCount.incrementAndGet();
            log.debug("JSON string too large ({} bytes), skipping validation", trimmed.length());
            return false;
        }
        
        // Quick format check before expensive parsing
        if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) || 
              (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
            return false;
        }
        
        // Check cache first
        Boolean cachedResult = jsonValidationCache.get(str);
        if (cachedResult != null) {
            jsonValidationHits.incrementAndGet();
            return cachedResult;
        }
        
        jsonValidationMisses.incrementAndGet();
        
        try {
            objectMapper.readTree(str);
            cacheJsonValidationResult(str, true);
            return true;
        } catch (JsonProcessingException e) {
            cacheJsonValidationResult(str, false);
            return false;
        }
    }
    
    /**
     * Caches JSON validation result with size management.
     */
    private static void cacheJsonValidationResult(String json, Boolean isValid) {
        if (jsonValidationCache.size() >= MAX_CACHE_SIZE) {
            // Simple cache eviction: clear when full
            jsonValidationCache.clear();
        }
        jsonValidationCache.put(json, isValid);
    }

    /**
     * Parses a JSON string into a JsonNode for better formatting with caching.
     * If the string is not valid JSON, returns the original string.
     *
     * @param str The string to parse
     * @return JsonNode if valid JSON, original string otherwise
     */
    private static Object parseJsonIfValid(String str) {
        if (!isValidJson(str)) {
            return str;
        }
        
        // Check parse cache first
        JsonNode cachedNode = jsonParseCache.get(str);
        if (cachedNode != null) {
            jsonParseHits.incrementAndGet();
            return cachedNode;
        }
        
        jsonParseMisses.incrementAndGet();
        
        try {
            JsonNode parsed = objectMapper.readTree(str);
            cacheJsonParseResult(str, parsed);
            return parsed;
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse JSON string, using original: {}", e.getMessage());
            return str;
        }
    }
    
    /**
     * Caches JSON parse result with size management.
     */
    private static void cacheJsonParseResult(String json, JsonNode parsed) {
        if (jsonParseCache.size() >= MAX_CACHE_SIZE) {
            // Simple cache eviction: clear when full
            jsonParseCache.clear();
        }
        jsonParseCache.put(json, parsed);
    }

    /**
     * Recursively processes a JsonNode, parsing any string values that contain JSON.
     *
     * @param node The JsonNode to process
     * @return A new JsonNode with nested JSON strings parsed recursively
     */
    private static JsonNode processJsonNodeRecursively(JsonNode node) {
        return processJsonNodeRecursively(node, 0);
    }
    
    /**
     * Recursively processes a JsonNode with depth tracking.
     *
     * @param node The JsonNode to process
     * @param depth Current recursion depth
     * @return A new JsonNode with nested JSON strings parsed recursively
     */
    private static JsonNode processJsonNodeRecursively(JsonNode node, int depth) {
        if (node == null) {
            return node;
        }
        
        // Check recursion depth limit
        if (depth >= MAX_RECURSION_DEPTH) {
            recursionLimitExceeded.incrementAndGet();
            log.debug("Recursion depth limit ({}) exceeded, stopping JSON processing", MAX_RECURSION_DEPTH);
            return node;
        }
        
        if (node.isObject()) {
            return processObjectNodeRecursively(node, depth);
        } else if (node.isArray()) {
            return processArrayNodeRecursively(node, depth);
        } else if (node.isTextual()) {
            // If it's a text node, check if the text is valid JSON and parse it
            String textValue = node.asText();
            if (isValidJson(textValue)) {
                try {
                    JsonNode parsedNode = objectMapper.readTree(textValue);
                    // Recursively process the parsed node with incremented depth
                    return processJsonNodeRecursively(parsedNode, depth + 1);
                } catch (JsonProcessingException e) {
                    log.debug("Failed to parse nested JSON in text node: {}", e.getMessage());
                }
            }
        }
        
        return node;
    }
    
    /**
     * Recursively processes an object JsonNode.
     *
     * @param objectNode The object node to process
     * @param depth Current recursion depth
     * @return A new object node with processed values
     */
    private static JsonNode processObjectNodeRecursively(JsonNode objectNode, int depth) {
        var objectNodeBuilder = objectMapper.createObjectNode();
        
        objectNode.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            JsonNode processedValue = processJsonNodeRecursively(value, depth + 1);
            objectNodeBuilder.set(key, processedValue);
        });
        
        return objectNodeBuilder;
    }
    
    /**
     * Recursively processes an array JsonNode.
     *
     * @param arrayNode The array node to process
     * @param depth Current recursion depth
     * @return A new array node with processed values
     */
    private static JsonNode processArrayNodeRecursively(JsonNode arrayNode, int depth) {
        var arrayNodeBuilder = objectMapper.createArrayNode();
        
        for (JsonNode element : arrayNode) {
            JsonNode processedElement = processJsonNodeRecursively(element, depth + 1);
            arrayNodeBuilder.add(processedElement);
        }
        
        return arrayNodeBuilder;
    }

    /**
     * Processes a value, parsing nested JSON recursively if it's a JSON string.
     *
     * @param value The value to process
     * @return Parsed JSON object if value is a valid JSON string, original value otherwise
     */
    private static Object processValue(Object value) {
        if (value instanceof String) {
            Object parsedValue = parseJsonIfValid((String) value);
            if (parsedValue instanceof JsonNode) {
                // Apply recursive processing to the parsed JSON
                return processJsonNodeRecursively((JsonNode) parsedValue);
            }
            return parsedValue;
        } else if (value instanceof JsonNode) {
            // If it's already a JsonNode, apply recursive processing
            return processJsonNodeRecursively((JsonNode) value);
        }
        return value;
    }

    /**
     * Gets current JSON parsing metrics for monitoring and health checks.
     *
     * @return A map containing current metrics values
     */
    public static Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("jsonValidationHits", jsonValidationHits.get());
        metrics.put("jsonValidationMisses", jsonValidationMisses.get());
        metrics.put("jsonParseHits", jsonParseHits.get());
        metrics.put("jsonParseMisses", jsonParseMisses.get());
        metrics.put("oversizedJsonCount", oversizedJsonCount.get());
        metrics.put("recursionLimitExceeded", recursionLimitExceeded.get());
        metrics.put("validationCacheSize", jsonValidationCache.size());
        metrics.put("parseCacheSize", jsonParseCache.size());
        
        // Calculate cache hit rates
        long totalValidationRequests = jsonValidationHits.get() + jsonValidationMisses.get();
        long totalParseRequests = jsonParseHits.get() + jsonParseMisses.get();
        
        if (totalValidationRequests > 0) {
            metrics.put("validationCacheHitRate", 
                (double) jsonValidationHits.get() / totalValidationRequests);
        } else {
            metrics.put("validationCacheHitRate", 0.0);
        }
        
        if (totalParseRequests > 0) {
            metrics.put("parseCacheHitRate", 
                (double) jsonParseHits.get() / totalParseRequests);
        } else {
            metrics.put("parseCacheHitRate", 0.0);
        }
        
        return metrics;
    }

    /**
     * Resets all performance metrics. Useful for testing or monitoring reset.
     */
    public static void resetMetrics() {
        jsonValidationHits.set(0);
        jsonValidationMisses.set(0);
        jsonParseHits.set(0);
        jsonParseMisses.set(0);
        oversizedJsonCount.set(0);
        recursionLimitExceeded.set(0);
    }

    /**
     * Clears all caches. Useful for memory management or testing.
     */
    public static void clearCaches() {
        jsonValidationCache.clear();
        jsonParseCache.clear();
    }

    /**
     * Gets cache statistics for monitoring.
     *
     * @return A map containing cache statistics
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("validationCacheSize", jsonValidationCache.size());
        stats.put("parseCacheSize", jsonParseCache.size());
        stats.put("maxCacheSize", MAX_CACHE_SIZE);
        stats.put("validationCacheUtilization", 
            (double) jsonValidationCache.size() / MAX_CACHE_SIZE);
        stats.put("parseCacheUtilization", 
            (double) jsonParseCache.size() / MAX_CACHE_SIZE);
        return stats;
    }

    /**
     * Creates a structured log entry with additional context.
     * Automatically parses nested JSON strings in both message and data values.
     *
     * @param message The log message
     * @param data    Additional data to include in the log entry
     * @return A map containing the message and data, suitable for structured logging
     */
    public static Map<String, Object> structuredLog(String message, Map<String, Object> data) {
        Map<String, Object> structuredData = new HashMap<>();
        
        // Process data, parsing any JSON strings
        if (data != null) {
            data.forEach((key, value) -> structuredData.put(key, processValue(value)));
        }
        
        // Process message for nested JSON
        structuredData.put("message", processValue(message));
        return structuredData;
    }

    /**
     * Creates a structured log entry with a single data field.
     * Automatically parses nested JSON strings in both message and data values.
     *
     * @param message The log message
     * @param key     The key for the data field
     * @param value   The value for the data field
     * @return A map containing the message and data, suitable for structured logging
     */
    public static Map<String, Object> structuredLog(String message, String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, processValue(value));
        return structuredLog(message, data);
    }

    /**
     * Executes an operation with temporary MDC context values that are removed after execution.
     *
     * @param key       The MDC key
     * @param value     The MDC value
     * @param operation The operation to execute
     * @param <T>       The return type of the operation
     * @return The result of the operation
     */
    public static <T> T withMdc(String key, String value, Supplier<T> operation) {
        MDC.put(key, value);
        try {
            return operation.get();
        } finally {
            MDC.remove(key);
        }
    }

    /**
     * Executes an operation with multiple temporary MDC context values that are removed after execution.
     *
     * @param context   Map of MDC keys and values
     * @param operation The operation to execute
     * @param <T>       The return type of the operation
     * @return The result of the operation
     */
    public static <T> T withMdc(Map<String, String> context, Supplier<T> operation) {
        Map<String, String> previousContext = new HashMap<>();
        
        // Store previous values and set new ones
        context.forEach((key, value) -> {
            String previousValue = MDC.get(key);
            if (previousValue != null) {
                previousContext.put(key, previousValue);
            }
            MDC.put(key, value);
        });
        
        try {
            return operation.get();
        } finally {
            // Restore previous context
            context.keySet().forEach(key -> {
                if (previousContext.containsKey(key)) {
                    MDC.put(key, previousContext.get(key));
                } else {
                    MDC.remove(key);
                }
            });
        }
    }

    /**
     * Adds a user ID to the MDC context.
     *
     * @param userId The user ID
     */
    public static void setUserId(@Nullable String userId) {
        if (userId != null) {
            MDC.put("userId", userId);
        }
    }

    /**
     * Adds a request ID to the MDC context.
     *
     * @param requestId The request ID
     */
    public static void setRequestId(@Nullable String requestId) {
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
    }

    /**
     * Adds a correlation ID to the MDC context.
     *
     * @param correlationId The correlation ID
     */
    public static void setCorrelationId(@Nullable String correlationId) {
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
    }

    /**
     * Creates a builder for constructing structured log entries.
     *
     * @param message The log message
     * @return A builder for the structured log entry
     */
    public static StructuredLogBuilder log(String message) {
        return new StructuredLogBuilder(message);
    }

    /**
     * Builder class for creating structured log entries.
     */
    public static class StructuredLogBuilder {
        private final String message;
        private final Map<String, Object> data = new HashMap<>();

        private StructuredLogBuilder(String message) {
            this.message = message;
        }

        /**
         * Adds a field to the structured log entry.
         * Automatically parses nested JSON strings in the value.
         *
         * @param key   The field key
         * @param value The field value
         * @return This builder for method chaining
         */
        public StructuredLogBuilder with(String key, Object value) {
            data.put(key, processValue(value));
            return this;
        }

        /**
         * Adds multiple fields to the structured log entry.
         * Automatically parses nested JSON strings in the values.
         *
         * @param fields A map of fields to add
         * @return This builder for method chaining
         */
        public StructuredLogBuilder with(Map<String, Object> fields) {
            if (fields != null) {
                fields.forEach((key, value) -> data.put(key, processValue(value)));
            }
            return this;
        }

        /**
         * Builds the structured log entry.
         *
         * @return A map containing the message and data, suitable for structured logging
         */
        public Map<String, Object> build() {
            return structuredLog(message, data);
        }

        /**
         * Logs the structured entry at INFO level.
         *
         * @param logger The logger to use
         */
        public void info(Logger logger) {
            logger.info("{}", build());
        }

        /**
         * Logs the structured entry at DEBUG level.
         *
         * @param logger The logger to use
         */
        public void debug(Logger logger) {
            logger.debug("{}", build());
        }

        /**
         * Logs the structured entry at WARN level.
         *
         * @param logger The logger to use
         */
        public void warn(Logger logger) {
            logger.warn("{}", build());
        }

        /**
         * Logs the structured entry at ERROR level.
         *
         * @param logger The logger to use
         */
        public void error(Logger logger) {
            logger.error("{}", build());
        }

        /**
         * Logs the structured entry at ERROR level with an exception.
         *
         * @param logger    The logger to use
         * @param throwable The exception to log
         */
        public void error(Logger logger, Throwable throwable) {
            logger.error("{}", build(), throwable);
        }
    }
}
