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


package org.fireflyframework.web.logging.filter;

import org.fireflyframework.web.logging.config.HttpRequestLoggingProperties;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WebFilter that logs HTTP requests and responses for auditing purposes.
 * This filter intercepts all HTTP requests and logs them according to the configuration.
 */
@EnableConfigurationProperties(HttpRequestLoggingProperties.class)
public class HttpRequestLoggingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingWebFilter.class);
    private static final String MASK_VALUE = "***MASKED***";
    
    private final HttpRequestLoggingProperties properties;
    private final Optional<PiiMaskingService> piiMaskingService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    public HttpRequestLoggingWebFilter(HttpRequestLoggingProperties properties, 
                                     Optional<PiiMaskingService> piiMaskingService) {
        this.properties = properties;
        this.piiMaskingService = piiMaskingService;
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates a properly configured ObjectMapper for JSON logging.
     * Ensures valid JSON output by handling special characters, null values, and other edge cases.
     */
    private ObjectMapper createObjectMapper() {
        // Configure to handle special characters, null values, and deterministic output
        return JsonMapper.builder()
                .configure(JsonWriteFeature.ESCAPE_NON_ASCII, true)
                .configure(JsonWriteFeature.WRITE_NAN_AS_STRINGS, true)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    /**
     * Sanitizes string content to ensure it's safe for JSON serialization.
     * Handles invalid UTF-8 sequences, control characters, and PII masking.
     */
    private String sanitizeForJson(String input) {
        if (input == null) {
            return null;
        }
        
        // Replace invalid UTF-8 sequences and control characters
        String sanitized = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                               .replaceAll("\\p{C}", ""); // Remove other control characters
        
        // Ensure the string is valid UTF-8
        byte[] bytes = sanitized.getBytes(StandardCharsets.UTF_8);
        sanitized = new String(bytes, StandardCharsets.UTF_8);
        
        // Truncate if too long to prevent memory issues
        if (sanitized.length() > 10000) {
            sanitized = sanitized.substring(0, 10000) + "...[TRUNCATED]";
        }
        
        // Apply PII masking if available
        if (piiMaskingService.isPresent()) {
            sanitized = piiMaskingService.get().maskPiiData(sanitized);
        }
        
        return sanitized;
    }

    /**
     * Safely extracts and sanitizes body content for JSON logging.
     * Applies PII masking to body content.
     */
    private String sanitizeBodyForJson(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        
        try {
            // Try to decode as UTF-8
            String bodyString = new String(body, StandardCharsets.UTF_8);
            String sanitized = sanitizeForJson(bodyString);
            
            // Apply additional body-specific PII masking if available
            if (piiMaskingService.isPresent()) {
                sanitized = piiMaskingService.get().maskBody(sanitized);
            }
            
            return sanitized;
        } catch (Exception e) {
            // If UTF-8 decoding fails, try to represent as base64 or hex
            log.debug("Failed to decode body as UTF-8, using fallback representation: {}", e.getMessage());
            try {
                // For binary content, represent as base64
                String base64Content = java.util.Base64.getEncoder().encodeToString(body);
                // Apply PII masking to base64 content if available (though unlikely to contain readable PII)
                if (piiMaskingService.isPresent()) {
                    base64Content = piiMaskingService.get().maskPiiData(base64Content);
                }
                return base64Content;
            } catch (Exception ex) {
                return "[BINARY_CONTENT_" + body.length + "_BYTES]";
            }
        }
    }

    /**
     * Safely serializes data to JSON with validation and fallback mechanisms.
     */
    private String serializeToJsonSafely(Map<String, Object> logData, String logType) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(logData);
            // Validate that the JSON is not null and not empty
            if (jsonMessage != null && !jsonMessage.trim().isEmpty()) {
                return jsonMessage;
            }
        } catch (JsonProcessingException e) {
            log.error("JSON serialization failed for {}: {}", logType, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during JSON serialization for {}: {}", logType, e.getMessage(), e);
        }
        
        // Fallback: create basic JSON structure manually
        try {
            StringBuilder fallbackJson = new StringBuilder();
            fallbackJson.append("{");
            fallbackJson.append("\"type\":\"").append(logType).append("\",");
            fallbackJson.append("\"requestId\":\"").append(logData.get("requestId")).append("\",");
            fallbackJson.append("\"timestamp\":\"").append(logData.get("timestamp")).append("\",");
            fallbackJson.append("\"error\":\"JSON_SERIALIZATION_FAILED\"");
            fallbackJson.append("}");
            return fallbackJson.toString();
        } catch (Exception fallbackException) {
            log.error("Fallback JSON creation failed for {}: {}", logType, fallbackException.getMessage());
            return "{\"type\":\"" + logType + "\",\"error\":\"COMPLETE_SERIALIZATION_FAILURE\"}";
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Check if path should be excluded from logging
        if (isPathExcluded(path)) {
            return chain.filter(exchange);
        }

        // Record start time for performance measurement
        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();

        // Log request
        logRequest(request, requestId);

        // Create decorated request and response for body logging
        ServerHttpRequestDecorator requestDecorator = createRequestDecorator(request, requestId);
        ServerHttpResponseDecorator responseDecorator = createResponseDecorator(exchange.getResponse(), requestId, startTime);

        ServerWebExchange decoratedExchange = exchange.mutate()
                .request(requestDecorator)
                .response(responseDecorator)
                .build();

        return chain.filter(decoratedExchange);
    }

    private boolean isPathExcluded(String path) {
        return properties.getExcludedPaths().stream()
                .anyMatch(excludedPath -> pathMatcher.match(excludedPath, path));
    }

    private String generateRequestId() {
        return String.valueOf(System.nanoTime());
    }

    private void logRequest(ServerHttpRequest request, String requestId) {
        try {
            Map<String, Object> logData = new LinkedHashMap<>();
            logData.put("type", "HTTP_REQUEST");
            logData.put("requestId", requestId);
            logData.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            logData.put("method", request.getMethod().toString());
            logData.put("path", request.getPath().value());

            if (properties.isIncludeQueryParams() && request.getQueryParams() != null && !request.getQueryParams().isEmpty()) {
                logData.put("queryParams", formatQueryParamsAsMap(request.getQueryParams()));
            }

            if (properties.isIncludeHeaders()) {
                logData.put("headers", formatHeadersAsMap(request.getHeaders()));
            }

            String jsonMessage = serializeToJsonSafely(logData, "HTTP_REQUEST");
            logAtConfiguredLevel(jsonMessage);
        } catch (Exception e) {
            log.error("Error logging HTTP request: {}", e.getMessage(), e);
        }
    }

    private void logResponse(ServerHttpResponse response, String requestId, long startTime) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> logData = new LinkedHashMap<>();
            logData.put("type", "HTTP_RESPONSE");
            logData.put("requestId", requestId);
            logData.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            logData.put("statusCode", response.getStatusCode() != null ? response.getStatusCode().value() : null);
            logData.put("durationMs", duration);

            if (properties.isIncludeHeaders()) {
                logData.put("headers", formatHeadersAsMap(response.getHeaders()));
            }

            String jsonMessage = serializeToJsonSafely(logData, "HTTP_RESPONSE");
            logAtConfiguredLevel(jsonMessage);
        } catch (Exception e) {
            log.error("Error logging HTTP response: {}", e.getMessage(), e);
        }
    }

    private ServerHttpRequestDecorator createRequestDecorator(ServerHttpRequest request, String requestId) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                if (!properties.isIncludeRequestBody()) {
                    return super.getBody();
                }
                
                return super.getBody().collectList().flatMapMany(dataBuffers -> {
                    try {
                        byte[] body = extractBody(dataBuffers, properties.getMaxBodySize());
                        if (body.length > 0) {
                            String sanitizedBodyString = sanitizeBodyForJson(body);
                            if (sanitizedBodyString != null) {
                                logRequestBody(requestId, sanitizedBodyString);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error logging request body: {}", e.getMessage(), e);
                    }
                    return Flux.fromIterable(dataBuffers);
                });
            }
        };
    }

    private ServerHttpResponseDecorator createResponseDecorator(ServerHttpResponse response, String requestId, long startTime) {
        return new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                logResponse(response, requestId, startTime);
                
                if (!properties.isIncludeResponseBody()) {
                    return super.writeWith(body);
                }

                return super.writeWith(Flux.from(body).collectList().map(dataBuffers -> {
                    try {
                        byte[] responseBody = extractBody(dataBuffers, properties.getMaxBodySize());
                        if (responseBody.length > 0) {
                            String sanitizedBodyString = sanitizeBodyForJson(responseBody);
                            if (sanitizedBodyString != null) {
                                logResponseBody(requestId, sanitizedBodyString);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error logging response body: {}", e.getMessage(), e);
                    }
                    return getDelegate().bufferFactory().join(dataBuffers);
                }).flux());
            }
        };
    }

    private byte[] extractBody(List<? extends DataBuffer> dataBuffers, int maxSize) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int totalSize = 0;
            for (DataBuffer dataBuffer : dataBuffers) {
                int bufferSize = dataBuffer.readableByteCount();
                if (totalSize + bufferSize > maxSize) {
                    // Only read up to maxSize
                    int remainingSize = maxSize - totalSize;
                    if (remainingSize > 0) {
                        byte[] bytes = new byte[remainingSize];
                        dataBuffer.read(bytes);
                        outputStream.write(bytes);
                    }
                    break;
                } else {
                    byte[] bytes = new byte[bufferSize];
                    dataBuffer.read(bytes);
                    outputStream.write(bytes);
                    totalSize += bufferSize;
                }
            }
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Error extracting body: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private String formatQueryParams(MultiValueMap<String, String> queryParams) {
        return queryParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private Map<String, Object> formatQueryParamsAsMap(MultiValueMap<String, String> queryParams) {
        Map<String, Object> paramsMap = new LinkedHashMap<>();
        queryParams.forEach((key, values) -> {
            String sanitizedKey = sanitizeForJson(key);
            if (sanitizedKey != null) {
                if (values.size() == 1) {
                    String sanitizedValue = sanitizeForJson(values.get(0));
                    paramsMap.put(sanitizedKey, sanitizedValue);
                } else {
                    List<String> sanitizedValues = values.stream()
                            .map(this::sanitizeForJson)
                            .filter(value -> value != null)
                            .collect(Collectors.toList());
                    paramsMap.put(sanitizedKey, sanitizedValues);
                }
            }
        });
        return paramsMap;
    }

    private String formatHeaders(HttpHeaders headers) {
        Map<String, String> sanitizedHeaders = new HashMap<>();
        headers.forEach((key, values) -> {
            String headerName = key.toLowerCase();
            if (properties.getSensitiveHeaders().contains(headerName)) {
                sanitizedHeaders.put(key, MASK_VALUE);
            } else {
                sanitizedHeaders.put(key, String.join(",", values));
            }
        });
        return sanitizedHeaders.toString();
    }

    private Map<String, Object> formatHeadersAsMap(HttpHeaders headers) {
        Map<String, Object> sanitizedHeaders = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            String sanitizedKey = sanitizeForJson(key);
            if (sanitizedKey != null) {
                String headerName = key.toLowerCase();
                if (properties.getSensitiveHeaders().contains(headerName)) {
                    sanitizedHeaders.put(sanitizedKey, MASK_VALUE);
                } else {
                    if (values.size() == 1) {
                        String sanitizedValue = sanitizeForJson(values.get(0));
                        sanitizedHeaders.put(sanitizedKey, sanitizedValue);
                    } else {
                        List<String> sanitizedValues = values.stream()
                                .map(this::sanitizeForJson)
                                .filter(value -> value != null)
                                .collect(Collectors.toList());
                        sanitizedHeaders.put(sanitizedKey, sanitizedValues);
                    }
                }
            }
        });
        return sanitizedHeaders;
    }

    private void logRequestBody(String requestId, String body) {
        try {
            Map<String, Object> logData = new LinkedHashMap<>();
            logData.put("type", "HTTP_REQUEST_BODY");
            logData.put("requestId", requestId);
            logData.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            logData.put("body", body);

            String jsonMessage = serializeToJsonSafely(logData, "HTTP_REQUEST_BODY");
            logAtConfiguredLevel(jsonMessage);
        } catch (Exception e) {
            log.error("Error logging HTTP request body: {}", e.getMessage(), e);
        }
    }

    private void logResponseBody(String requestId, String body) {
        try {
            Map<String, Object> logData = new LinkedHashMap<>();
            logData.put("type", "HTTP_RESPONSE_BODY");
            logData.put("requestId", requestId);
            logData.put("timestamp", Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            logData.put("body", body);

            String jsonMessage = serializeToJsonSafely(logData, "HTTP_RESPONSE_BODY");
            logAtConfiguredLevel(jsonMessage);
        } catch (Exception e) {
            log.error("Error logging HTTP response body: {}", e.getMessage(), e);
        }
    }

    private void logAtConfiguredLevel(String message) {
        String level = properties.getLogLevel().toUpperCase();
        switch (level) {
            case "DEBUG":
                log.debug(message);
                break;
            case "WARN":
                log.warn(message);
                break;
            case "ERROR":
                log.error(message);
                break;
            case "INFO":
            default:
                log.info(message);
                break;
        }
    }
}