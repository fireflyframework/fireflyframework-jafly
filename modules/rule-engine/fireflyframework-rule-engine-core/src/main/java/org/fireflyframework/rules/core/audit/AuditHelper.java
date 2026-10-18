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

package org.fireflyframework.rules.core.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.rules.core.services.AuditTrailService;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Helper class for audit trail operations.
 * Provides convenient methods for recording audit events with context extraction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditHelper {

    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    /**
     * Record an audit event with web context
     */
    public Mono<Void> recordAuditEvent(
            AuditEventType eventType,
            UUID entityId,
            String ruleCode,
            Object requestData,
            Object responseData,
            Integer statusCode,
            Boolean success,
            Long executionTimeMs,
            ServerWebExchange exchange) {

        return extractWebContext(exchange)
                .flatMap(context -> {
                    String requestJson = serializeToJson(requestData);
                    String responseJson = serializeToJson(responseData);

                    return auditTrailService.recordAuditEvent(
                            eventType,
                            entityId,
                            ruleCode,
                            context.getUserId(),
                            context.getHttpMethod(),
                            context.getEndpoint(),
                            requestJson,
                            responseJson,
                            statusCode,
                            success,
                            null, // errorMessage
                            executionTimeMs,
                            null, // metadata
                            context.getIpAddress(),
                            context.getUserAgent(),
                            context.getSessionId(),
                            context.getCorrelationId()
                    );
                })
                .then()
                .doOnError(error -> log.warn("Failed to record audit event: {}", error.getMessage()));
    }

    /**
     * Record an audit event with error
     */
    public Mono<Void> recordAuditEventWithError(
            AuditEventType eventType,
            UUID entityId,
            String ruleCode,
            Object requestData,
            String errorMessage,
            Integer statusCode,
            Long executionTimeMs,
            ServerWebExchange exchange) {

        return extractWebContext(exchange)
                .flatMap(context -> {
                    String requestJson = serializeToJson(requestData);

                    return auditTrailService.recordAuditEvent(
                            eventType,
                            entityId,
                            ruleCode,
                            context.getUserId(),
                            context.getHttpMethod(),
                            context.getEndpoint(),
                            requestJson,
                            null, // responseData
                            statusCode,
                            false, // success
                            errorMessage,
                            executionTimeMs,
                            null, // metadata
                            context.getIpAddress(),
                            context.getUserAgent(),
                            context.getSessionId(),
                            context.getCorrelationId()
                    );
                })
                .then()
                .doOnError(error -> log.warn("Failed to record audit event with error: {}", error.getMessage()));
    }

    /**
     * Record an audit event with metadata
     */
    public Mono<Void> recordAuditEventWithMetadata(
            AuditEventType eventType,
            UUID entityId,
            String ruleCode,
            Object requestData,
            Object responseData,
            Integer statusCode,
            Boolean success,
            Long executionTimeMs,
            Map<String, Object> metadata,
            ServerWebExchange exchange) {

        return extractWebContext(exchange)
                .flatMap(context -> {
                    String requestJson = serializeToJson(requestData);
                    String responseJson = serializeToJson(responseData);

                    return auditTrailService.recordAuditEventWithMetadata(
                            eventType,
                            entityId,
                            ruleCode,
                            context.getUserId(),
                            context.getHttpMethod(),
                            context.getEndpoint(),
                            requestJson,
                            responseJson,
                            statusCode,
                            success,
                            executionTimeMs,
                            metadata
                    );
                })
                .then()
                .doOnError(error -> log.warn("Failed to record audit event with metadata: {}", error.getMessage()));
    }

    /**
     * Extract web context from ServerWebExchange
     */
    private Mono<WebContext> extractWebContext(ServerWebExchange exchange) {
        if (exchange == null) {
            return Mono.just(WebContext.builder()
                    .userId("system")
                    .httpMethod("INTERNAL")
                    .endpoint("N/A")
                    .ipAddress("127.0.0.1")
                    .build());
        }

        return Mono.fromCallable(() -> {
            String userId = extractUserId(exchange);
            String httpMethod = exchange.getRequest().getMethod().name();
            String endpoint = exchange.getRequest().getPath().value();
            String ipAddress = extractIpAddress(exchange);
            String userAgent = extractUserAgent(exchange);
            String sessionId = extractSessionId(exchange);
            String correlationId = extractCorrelationId(exchange);

            return WebContext.builder()
                    .userId(userId)
                    .httpMethod(httpMethod)
                    .endpoint(endpoint)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .sessionId(sessionId)
                    .correlationId(correlationId)
                    .build();
        });
    }

    /**
     * Extract user ID from request headers or authentication context
     */
    private String extractUserId(ServerWebExchange exchange) {
        // Try to get from custom header first
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null) {
            return userId;
        }

        // Try to get from Authorization header (if JWT token)
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In a real implementation, you would decode the JWT token here
            // For now, return a placeholder
            return "authenticated-user";
        }

        // Default to anonymous
        return "anonymous";
    }

    /**
     * Extract IP address from request
     */
    private String extractIpAddress(ServerWebExchange exchange) {
        // Check for X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // Get remote address
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Extract user agent from request headers
     */
    private String extractUserAgent(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("User-Agent");
    }

    /**
     * Extract session ID from request
     */
    private String extractSessionId(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("X-Session-Id");
    }

    /**
     * Extract correlation ID from request headers
     */
    private String extractCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        }
        return correlationId;
    }

    /**
     * Serialize object to JSON string
     */
    private String serializeToJson(Object object) {
        if (object == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return object.toString();
        }
    }

    /**
     * Web context data class
     */
    @lombok.Data
    @lombok.Builder
    private static class WebContext {
        private String userId;
        private String httpMethod;
        private String endpoint;
        private String ipAddress;
        private String userAgent;
        private String sessionId;
        private String correlationId;
    }
}
