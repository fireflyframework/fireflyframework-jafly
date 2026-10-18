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


package org.fireflyframework.web.error.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enhanced error response model compliant with RFC 7807 Problem Details for HTTP APIs.
 *
 * <p>This class represents the structure of error responses returned to clients with
 * comprehensive error information including:</p>
 * <ul>
 *   <li>Standard HTTP error details (status, message, path)</li>
 *   <li>Distributed tracing support (traceId, spanId, correlationId)</li>
 *   <li>Environment-aware debugging information (stackTrace, debugInfo)</li>
 *   <li>Resilience patterns support (retry, rate limit, circuit breaker info)</li>
 *   <li>Error categorization and severity levels</li>
 *   <li>Validation errors with detailed field-level information</li>
 *   <li>Actionable suggestions and documentation links</li>
 * </ul>
 *
 * <p>The class uses Lombok's Builder and Data annotations to generate boilerplate code.</p>
 *
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807 Problem Details for HTTP APIs</a>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Default constructor for ErrorResponse.
     * This constructor is required for Jackson deserialization and Lombok's @Builder annotation.
     * It is not meant to be used directly - use the builder pattern instead.
     */
    @SuppressWarnings("unused")
    private ErrorResponse() {
        // Default constructor for Jackson and Lombok
    }

    /**
     * All-args constructor for ErrorResponse.
     * This constructor is used by Lombok's @Builder annotation.
     * It is not meant to be used directly - use the builder pattern instead.
     *
     * @param timestamp the timestamp when the error occurred
     * @param path the request path that caused the error
     * @param status the HTTP status code
     * @param error the error type
     * @param message the error message
     * @param code the error code
     * @param traceId the trace ID for distributed tracing
     * @param spanId the span ID for distributed tracing
     * @param correlationId the correlation ID for request tracking across services
     * @param requestId the unique request identifier
     * @param details additional details about the error
     * @param suggestion a suggestion for how to fix the error
     * @param documentation a link to documentation about the error
     * @param helpUrl a URL to get help with this error
     * @param metadata additional metadata about the error
     * @param errors a list of validation errors
     * @param stackTrace the stack trace (only in non-production environments)
     * @param debugInfo additional debug information (only in non-production environments)
     * @param instance the URI reference that identifies the specific occurrence of the problem
     * @param category the error category (e.g., VALIDATION, BUSINESS, TECHNICAL, SECURITY)
     * @param severity the error severity level (e.g., LOW, MEDIUM, HIGH, CRITICAL)
     * @param retryable whether the operation can be retried
     * @param retryAfter suggested time to wait before retrying (in seconds)
     * @param rateLimitInfo rate limit information if applicable
     * @param circuitBreakerInfo circuit breaker information if applicable
     */
    @SuppressWarnings("unused")
    @lombok.Builder
    private ErrorResponse(LocalDateTime timestamp, String path, Integer status, String error,
                         String message, String code, String traceId, String spanId,
                         String correlationId, String requestId, String details,
                         String suggestion, String documentation, String helpUrl,
                         Map<String, Object> metadata, List<ValidationError> errors,
                         String stackTrace, Map<String, Object> debugInfo, String instance,
                         ErrorCategory category, ErrorSeverity severity, Boolean retryable,
                         Integer retryAfter, RateLimitInfo rateLimitInfo,
                         CircuitBreakerInfo circuitBreakerInfo) {
        this.timestamp = timestamp;
        this.path = path;
        this.status = status;
        this.error = error;
        this.message = message;
        this.code = code;
        this.traceId = traceId;
        this.spanId = spanId;
        this.correlationId = correlationId;
        this.requestId = requestId;
        this.details = details;
        this.suggestion = suggestion;
        this.documentation = documentation;
        this.helpUrl = helpUrl;
        this.metadata = metadata;
        this.errors = errors;
        this.stackTrace = stackTrace;
        this.debugInfo = debugInfo;
        this.instance = instance;
        this.category = category;
        this.severity = severity;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.rateLimitInfo = rateLimitInfo;
        this.circuitBreakerInfo = circuitBreakerInfo;
    }

    // ========== Core Error Information ==========

    /**
     * Timestamp when the error occurred (ISO 8601 format).
     */
    @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "dd/MM/yyyy'T'HH:mm:ss.SSSSSS",
            timezone = "Europe/Madrid"
    )
    private LocalDateTime timestamp;

    /**
     * HTTP status code.
     */
    private Integer status;

    /**
     * HTTP status reason phrase (e.g., "Bad Request", "Internal Server Error").
     */
    private String error;

    /**
     * Human-readable error message.
     */
    private String message;

    /**
     * Application-specific error code for programmatic error handling.
     */
    private String code;

    /**
     * Request path that caused the error.
     */
    private String path;

    // ========== Distributed Tracing & Correlation ==========

    /**
     * Distributed trace ID for correlating logs across services.
     * Compatible with OpenTelemetry, Zipkin, and Jaeger.
     */
    private String traceId;

    /**
     * Span ID for the current operation in distributed tracing.
     */
    private String spanId;

    /**
     * Correlation ID for tracking requests across multiple services.
     * Useful for end-to-end request tracking in microservices architectures.
     */
    private String correlationId;

    /**
     * Unique request identifier for this specific request.
     */
    private String requestId;

    // ========== Error Details & Documentation ==========

    /**
     * Additional technical details about the error.
     * May include stack traces or debug information in non-production environments.
     */
    private String details;

    /**
     * Actionable suggestion for how to resolve the error.
     * Provides guidance to the client on how to fix the issue.
     */
    private String suggestion;

    /**
     * Link to documentation that provides more information about the error.
     * Can be a URL to API documentation or a knowledge base article.
     */
    private String documentation;

    /**
     * URL to get help with this specific error.
     * Can link to support portal, FAQ, or contact form.
     */
    private String helpUrl;

    /**
     * URI reference that identifies the specific occurrence of the problem (RFC 7807).
     * This can be used to uniquely identify this error instance.
     */
    private String instance;

    // ========== Error Classification ==========

    /**
     * Error category for classification and filtering.
     */
    private ErrorCategory category;

    /**
     * Error severity level for prioritization and alerting.
     */
    private ErrorSeverity severity;

    // ========== Retry & Resilience Information ==========

    /**
     * Indicates whether the failed operation can be retried.
     */
    private Boolean retryable;

    /**
     * Suggested time to wait before retrying (in seconds).
     * Useful for rate limiting and backoff strategies.
     */
    @JsonProperty("retry_after")
    private Integer retryAfter;

    /**
     * Rate limit information if the error is due to rate limiting.
     */
    private RateLimitInfo rateLimitInfo;

    /**
     * Circuit breaker information if the error is due to circuit breaker activation.
     */
    private CircuitBreakerInfo circuitBreakerInfo;

    // ========== Additional Context ==========

    /**
     * Additional metadata about the error.
     * Can include any additional information useful for debugging or understanding the error.
     */
    private Map<String, Object> metadata;

    /**
     * List of validation errors for field-level validation failures.
     */
    private List<ValidationError> errors;

    /**
     * Stack trace of the exception (only included in non-production environments).
     * Should be excluded in production for security reasons.
     */
    private String stackTrace;

    /**
     * Additional debug information (only included in non-production environments).
     * Can include request parameters, headers, or other contextual data.
     */
    private Map<String, Object> debugInfo;

    /**
     * Represents a field-specific validation error.
     * This class is used to provide detailed information about validation failures
     * for specific fields in a request.
     *
     * The class uses Lombok's Builder and Data annotations to generate boilerplate code.
     */
    @Data
    public static class ValidationError {
        /**
         * Default constructor for ValidationError.
         * This constructor is required for Jackson deserialization and Lombok's @Builder annotation.
         * It is not meant to be used directly - use the builder pattern instead.
         */
        @SuppressWarnings("unused")
        private ValidationError() {
            // Default constructor for Jackson and Lombok
        }

        /**
         * All-args constructor for ValidationError.
         * This constructor is used by Lombok's @Builder annotation.
         * It is not meant to be used directly - use the builder pattern instead.
         *
         * @param field the field that failed validation
         * @param code the error code for this validation error
         * @param message the validation error message
         * @param metadata additional metadata about the validation error
         */
        @SuppressWarnings("unused")
        @lombok.Builder
        private ValidationError(String field, String code, String message, Map<String, Object> metadata) {
            this.field = field;
            this.code = code;
            this.message = message;
            this.metadata = metadata;
        }
        /**
         * The field that failed validation.
         */
        private String field;

        /**
         * The error code for this validation error.
         * This can be used to identify the specific validation rule that failed.
         */
        private String code;

        /**
         * The validation error message.
         * This provides a human-readable description of the validation failure.
         */
        private String message;

        /**
         * Additional metadata about the validation error.
         * This can include information such as allowed values, min/max values, etc.
         */
        private Map<String, Object> metadata;
    }

    /**
     * Error category enumeration for classifying errors.
     */
    public enum ErrorCategory {
        /**
         * Validation errors (e.g., invalid input, missing required fields).
         */
        VALIDATION,

        /**
         * Business logic errors (e.g., insufficient funds, duplicate resource).
         */
        BUSINESS,

        /**
         * Technical errors (e.g., database connection failure, timeout).
         */
        TECHNICAL,

        /**
         * Security errors (e.g., authentication failure, authorization denied).
         */
        SECURITY,

        /**
         * External service errors (e.g., third-party API failure).
         */
        EXTERNAL,

        /**
         * Resource errors (e.g., not found, already exists).
         */
        RESOURCE,

        /**
         * Rate limiting errors.
         */
        RATE_LIMIT,

        /**
         * Circuit breaker errors.
         */
        CIRCUIT_BREAKER,

        /**
         * Unknown or unclassified errors.
         */
        UNKNOWN
    }

    /**
     * Error severity enumeration for prioritizing errors.
     */
    public enum ErrorSeverity {
        /**
         * Low severity - minor issues that don't significantly impact functionality.
         */
        LOW,

        /**
         * Medium severity - issues that impact some functionality but have workarounds.
         */
        MEDIUM,

        /**
         * High severity - significant issues that impact core functionality.
         */
        HIGH,

        /**
         * Critical severity - severe issues that prevent system operation.
         */
        CRITICAL
    }

    /**
     * Rate limit information for rate-limited requests.
     */
    @Data
    @lombok.Builder
    public static class RateLimitInfo {
        /**
         * Maximum number of requests allowed in the time window.
         */
        private Integer limit;

        /**
         * Number of requests remaining in the current time window.
         */
        private Integer remaining;

        /**
         * Time when the rate limit resets (Unix timestamp in seconds).
         */
        private Long resetTime;

        /**
         * Duration of the rate limit window in seconds.
         */
        private Integer windowSeconds;

        /**
         * Type of rate limit (e.g., "user", "ip", "api-key").
         */
        private String limitType;
    }

    /**
     * Circuit breaker information for circuit breaker errors.
     */
    @Data
    @lombok.Builder
    public static class CircuitBreakerInfo {
        /**
         * Current state of the circuit breaker (OPEN, HALF_OPEN, CLOSED).
         */
        private String state;

        /**
         * Name of the circuit breaker.
         */
        private String name;

        /**
         * Failure rate that triggered the circuit breaker (percentage).
         */
        private Double failureRate;

        /**
         * Threshold failure rate for opening the circuit (percentage).
         */
        private Double failureRateThreshold;

        /**
         * Number of recent failures.
         */
        private Integer failureCount;

        /**
         * Time when the circuit breaker will transition to HALF_OPEN (Unix timestamp).
         */
        private Long nextAttemptTime;

        /**
         * Suggested fallback action or service.
         */
        private String fallbackSuggestion;
    }
}

