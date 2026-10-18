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


package org.fireflyframework.web.error.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.exceptions.*;
import org.fireflyframework.web.error.models.ErrorResponse;
import org.fireflyframework.web.error.service.ErrorResponseCache;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.fireflyframework.web.logging.service.PiiMaskingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced global exception handler with enterprise-grade features.
 *
 * <p>This class provides comprehensive error handling with:</p>
 * <ul>
 *   <li>Distributed tracing integration (OpenTelemetry, Zipkin, Jaeger)</li>
 *   <li>Environment-aware error details (dev vs production)</li>
 *   <li>Metrics collection for observability</li>
 *   <li>Circuit breaker and resilience pattern support</li>
 *   <li>RFC 7807 Problem Details compliance</li>
 *   <li>PII masking and security features</li>
 *   <li>Comprehensive error categorization and severity levels</li>
 * </ul>
 *
 * @see ErrorResponse
 * @see ErrorHandlingProperties
 */
@Slf4j
@Hidden
@Order(-2)
@Configuration
@RestControllerAdvice
public class GlobalExceptionHandler extends FireflyMetricsSupport implements ErrorWebExceptionHandler {

    private final ExceptionConverterService converterService;
    private final Optional<PiiMaskingService> piiMaskingService;
    private final ErrorHandlingProperties errorProperties;
    private final Optional<Tracer> tracer;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final ErrorResponseNegotiator responseNegotiator;
    private final Optional<ErrorResponseCache> errorResponseCache;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    /**
     * Creates a new GlobalExceptionHandler with comprehensive dependencies.
     *
     * @param converterService the service used to convert exceptions to business exceptions
     * @param piiMaskingService optional service used to mask PII data in error messages
     * @param errorProperties configuration properties for error handling
     * @param tracer optional distributed tracing tracer
     * @param meterRegistry optional metrics registry
     * @param environment Spring environment for profile detection
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @param responseNegotiator service for content negotiation
     * @param errorResponseCache optional error response cache
     */
    public GlobalExceptionHandler(ExceptionConverterService converterService,
                                   Optional<PiiMaskingService> piiMaskingService,
                                   ErrorHandlingProperties errorProperties,
                                   Optional<Tracer> tracer,
                                   Optional<MeterRegistry> meterRegistry,
                                   Environment environment,
                                   ObjectMapper objectMapper,
                                   ErrorResponseNegotiator responseNegotiator,
                                   Optional<ErrorResponseCache> errorResponseCache) {
        super(meterRegistry.orElse(null), "web");
        this.converterService = converterService;
        this.piiMaskingService = piiMaskingService;
        this.errorProperties = errorProperties;
        this.tracer = tracer;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.responseNegotiator = responseNegotiator;
        this.errorResponseCache = errorResponseCache;

        // Log cache status
        errorResponseCache.ifPresent(cache ->
                log.info("Error response caching is ENABLED (maxSize={}, ttl={}s)",
                        errorProperties.getErrorCacheMaxSize(),
                        errorProperties.getErrorCacheTtlSeconds())
        );
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        long startTime = System.currentTimeMillis();

        return Mono.defer(() -> {
            // Record error metrics
            recordErrorMetric(ex);

            // Handle specific exception types
            if (ex instanceof BusinessException businessException) {
                return handleBusinessException(exchange, businessException);
            }

            if (ex instanceof ValidationException validationException) {
                return handleCustomValidationException(exchange, validationException);
            }

            if (ex instanceof WebExchangeBindException validationException) {
                return handleValidationException(exchange, validationException);
            }

            if (ex instanceof ResponseStatusException responseStatusException) {
                return handleResponseStatusException(exchange, responseStatusException);
            }

            // Try to convert other exceptions to BusinessException
            try {
                BusinessException convertedException = converterService.convertException(ex);
                return handleBusinessException(exchange, convertedException);
            } catch (Exception conversionError) {
                log.error("Error converting exception", conversionError);
                return handleUnexpectedError(exchange, ex);
            }
        }).doFinally(signalType -> {
            // Record error handling duration
            long duration = System.currentTimeMillis() - startTime;
            recordErrorDuration(ex, duration);
        });
    }

    @ExceptionHandler(BusinessException.class)
    private Mono<Void> handleBusinessException(ServerWebExchange exchange, BusinessException ex) {
        // Log with appropriate level based on status code
        logError(ex, exchange);

        // Build comprehensive error response
        var builder = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(maskSensitiveData(ex.getMessage()))
                .path(exchange.getRequest().getPath().value())
                .code(ex.getCode())
                .metadata(ex.getMetadata());

        // Add distributed tracing information
        enrichWithTracingInfo(builder, exchange);

        // Add error categorization
        enrichWithCategorization(builder, ex);

        // Add resilience information if applicable
        enrichWithResilienceInfo(builder, ex);

        // Add suggestions and documentation
        enrichWithSuggestions(builder, ex);
        enrichWithDocumentation(builder, ex);

        // Add environment-specific debug information
        enrichWithDebugInfo(builder, ex, exchange);

        ErrorResponse errorResponse = builder.build();

        // Set response status and headers
        exchange.getResponse().setStatusCode(ex.getStatus());

        // Use content negotiation to determine response format
        MediaType contentType = responseNegotiator.determineContentType(exchange);
        exchange.getResponse().getHeaders().setContentType(contentType);
        addSecurityHeaders(exchange);

        // Add retry-after header if applicable
        if (errorResponse.getRetryAfter() != null) {
            exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER,
                String.valueOf(errorResponse.getRetryAfter()));
        }

        // Negotiate and write response
        byte[] responseBytes = responseNegotiator.negotiate(exchange, errorResponse);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(responseBytes)));
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private Mono<Void> handleCustomValidationException(ServerWebExchange exchange, ValidationException ex) {
        log.error("Validation exception occurred: ", ex);

        // Convert validation errors to the enhanced format
        List<ErrorResponse.ValidationError> validationErrors = ex.getValidationErrors().stream()
                .map(error -> ErrorResponse.ValidationError.builder()
                        .field(error.getField())
                        .code(error.getCode())
                        .message(error.getMessage())
                        .metadata(error.getMetadata())
                        .build())
                .collect(Collectors.toList());

        // Create a more detailed error response
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .path(exchange.getRequest().getPath().value())
                .traceId(UUID.randomUUID().toString())
                .code(ex.getCode())
                .errors(validationErrors)
                .metadata(ex.getMetadata())
                .suggestion("Please check the validation errors and correct your request.")
                .documentation(errorProperties.getDocumentationBaseUrl() != null ? errorProperties.getDocumentationBaseUrl() + "/validation" : null)
                .build();

        exchange.getResponse().setStatusCode(ex.getStatus());

        // Use content negotiation
        MediaType contentType = responseNegotiator.determineContentType(exchange);
        exchange.getResponse().getHeaders().setContentType(contentType);

        byte[] responseBytes = responseNegotiator.negotiate(exchange, errorResponse);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(responseBytes)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private Mono<Void> handleValidationException(ServerWebExchange exchange, WebExchangeBindException ex) {
        log.error("Validation exception occurred: ", ex);

        // Convert Spring validation errors to our enhanced format
        List<ErrorResponse.ValidationError> validationErrors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError ?
                            fieldError.getField() : error.getObjectName();
                    String code = error.getCode();

                    // Create validation error with the basic information
                    Map<String, Object> metadata = new HashMap<>();

                    // Add additional metadata for specific validation errors
                    if (error instanceof FieldError fieldError) {
                        if (fieldError.getRejectedValue() != null) {
                            metadata.put("rejectedValue", fieldError.getRejectedValue().toString());
                            metadata.put("bindingFailure", fieldError.isBindingFailure());
                        }
                    }

                    return ErrorResponse.ValidationError.builder()
                            .field(field)
                            .code(code)
                            .message(error.getDefaultMessage())
                            .metadata(metadata.isEmpty() ? null : metadata)
                            .build();
                })
                .collect(Collectors.toList());

        // Create a more detailed error response
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid request parameters")
                .path(exchange.getRequest().getPath().value())
                .traceId(UUID.randomUUID().toString())
                .code("VALIDATION_ERROR")
                .errors(validationErrors)
                .suggestion("Please check the validation errors and correct your request.")
                .documentation(errorProperties.getDocumentationBaseUrl() != null ? errorProperties.getDocumentationBaseUrl() + "/validation" : null)
                .details("The request failed validation. Check the 'errors' field for details.")
                .build();

        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);

        // Use content negotiation
        MediaType contentType = responseNegotiator.determineContentType(exchange);
        exchange.getResponse().getHeaders().setContentType(contentType);

        byte[] responseBytes = responseNegotiator.negotiate(exchange, errorResponse);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(responseBytes)));
    }

    private Mono<Void> handleResponseStatusException(ServerWebExchange exchange, ResponseStatusException ex) {
        log.error("Response status exception occurred: ", ex);

        // Create a more detailed error response
        var builder = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(ex.getStatusCode().toString())
                .message(ex.getReason())
                .path(exchange.getRequest().getPath().value())
                .traceId(UUID.randomUUID().toString())
                .code("HTTP_STATUS_ERROR");

        // Try to convert the exception to get metadata with suggestions
        try {
            BusinessException convertedException = converterService.convertException(ex);
            Map<String, Object> metadata = convertedException.getMetadata();

            // Add metadata to the response
            builder.metadata(metadata);

            // Add suggestion from metadata if available
            if (metadata != null && metadata.containsKey("suggestion")) {
                builder.suggestion(metadata.get("suggestion").toString());
            } else {
                // Add suggestion based on the status code
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    builder.suggestion("The requested resource could not be found. Please check the URL and try again.");
                } else if (ex.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED) {
                    builder.suggestion("The HTTP method used is not allowed for this resource. Please check the documentation for allowed methods.");
                } else if (ex.getStatusCode() == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
                    builder.suggestion("The request format is not supported. Please check the Content-Type header.");
                } else if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    builder.suggestion("The request was malformed. Please check your request parameters and try again.");
                } else {
                    builder.suggestion("Please check your request and try again.");
                }
            }
        } catch (Exception conversionError) {
            // If conversion fails, use default suggestions
            log.debug("Error converting ResponseStatusException", conversionError);

            // Add suggestion based on the status code
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                builder.suggestion("The requested resource could not be found. Please check the URL and try again.");
            } else if (ex.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED) {
                builder.suggestion("The HTTP method used is not allowed for this resource. Please check the documentation for allowed methods.");
            } else if (ex.getStatusCode() == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
                builder.suggestion("The request format is not supported. Please check the Content-Type header.");
            } else if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                builder.suggestion("The request was malformed. Please check your request parameters and try again.");
            } else {
                builder.suggestion("Please check your request and try again.");
            }
        }

        // Add documentation link
        if (errorProperties.getDocumentationBaseUrl() != null) {
            builder.documentation(errorProperties.getDocumentationBaseUrl() + "/http-status");
        }

        ErrorResponse errorResponse = builder.build();

        exchange.getResponse().setStatusCode(ex.getStatusCode());

        // Use content negotiation
        MediaType contentType = responseNegotiator.determineContentType(exchange);
        exchange.getResponse().getHeaders().setContentType(contentType);

        byte[] responseBytes = responseNegotiator.negotiate(exchange, errorResponse);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(responseBytes)));
    }

    private Mono<Void> handleUnexpectedError(ServerWebExchange exchange, Throwable ex) {
        log.error("Unexpected error occurred: ", ex);

        String traceId = UUID.randomUUID().toString();
        log.error("Trace ID: {}", traceId, ex);

        // Create a more detailed error response
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please contact support with the trace ID.")
                .path(exchange.getRequest().getPath().value())
                .traceId(traceId)
                .code("INTERNAL_ERROR")
                .suggestion("Please try again later or contact support with the trace ID.")
                .documentation(errorProperties.getDocumentationBaseUrl() != null ? errorProperties.getDocumentationBaseUrl() + "/internal-error" : null)
                .details("The server encountered an unexpected condition that prevented it from fulfilling the request.")
                .build();

        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        // Use content negotiation
        MediaType contentType = responseNegotiator.determineContentType(exchange);
        exchange.getResponse().getHeaders().setContentType(contentType);

        byte[] responseBytes = responseNegotiator.negotiate(exchange, errorResponse);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(responseBytes)));
    }

    // ========== Helper Methods ==========

    /**
     * Enriches error response with distributed tracing information.
     *
     * @param builder the error response builder
     * @param exchange the server web exchange
     */
    private void enrichWithTracingInfo(Object builder, ServerWebExchange exchange) {
        @SuppressWarnings("unchecked")
        var typedBuilder = (ErrorResponse.ErrorResponseBuilder) builder;
        if (!errorProperties.isEnableDistributedTracing()) {
            return;
        }

        // Extract trace ID from tracer or generate new one
        String traceId = null;
        String spanId = null;

        if (tracer.isPresent()) {
            var currentSpan = tracer.get().currentSpan();
            if (currentSpan != null) {
                var context = currentSpan.context();
                traceId = context.traceId();
                spanId = context.spanId();
            }
        }

        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        if (errorProperties.isIncludeCorrelationId()) {
            typedBuilder.traceId(traceId);
        }

        if (errorProperties.isIncludeSpanId() && spanId != null) {
            typedBuilder.spanId(spanId);
        }

        // Extract correlation ID from headers
        if (errorProperties.isIncludeCorrelationId()) {
            String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
            if (correlationId == null) {
                correlationId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
            }
            typedBuilder.correlationId(correlationId);
        }

        // Generate request ID
        if (errorProperties.isIncludeRequestId()) {
            String requestId = exchange.getRequest().getId();
            typedBuilder.requestId(requestId);
        }

        // Add instance identifier
        if (errorProperties.getInstanceId() != null) {
            typedBuilder.instance(errorProperties.getInstanceId());
        }
    }

    /**
     * Enriches error response with error categorization and severity.
     *
     * @param builder the error response builder
     * @param ex the business exception
     */
    private void enrichWithCategorization(Object builder, BusinessException ex) {
        @SuppressWarnings("unchecked")
        var typedBuilder = (ErrorResponse.ErrorResponseBuilder) builder;
        // Determine error category
        ErrorResponse.ErrorCategory category = determineCategory(ex);
        typedBuilder.category(category);

        // Determine error severity
        ErrorResponse.ErrorSeverity severity = determineSeverity(ex);
        typedBuilder.severity(severity);
    }

    /**
     * Enriches error response with resilience pattern information.
     *
     * @param builder the error response builder
     * @param ex the business exception
     */
    private void enrichWithResilienceInfo(Object builder, BusinessException ex) {
        @SuppressWarnings("unchecked")
        var typedBuilder = (ErrorResponse.ErrorResponseBuilder) builder;
        // Handle circuit breaker exceptions
        if (ex instanceof CircuitBreakerException) {
            typedBuilder.retryable(true);
            Map<String, Object> metadata = ex.getMetadata();

            var cbInfo = ErrorResponse.CircuitBreakerInfo.builder()
                    .state((String) metadata.getOrDefault("state", "OPEN"))
                    .name((String) metadata.getOrDefault("name", "unknown"))
                    .failureRate(((Number) metadata.getOrDefault("failureRate", 0.0)).doubleValue())
                    .failureRateThreshold(((Number) metadata.getOrDefault("failureRateThreshold", 50.0)).doubleValue())
                    .failureCount(((Number) metadata.getOrDefault("failureCount", 0)).intValue())
                    .fallbackSuggestion((String) metadata.get("fallbackSuggestion"))
                    .build();

            typedBuilder.circuitBreakerInfo(cbInfo);

            Integer retryAfter = (Integer) metadata.get("retryAfter");
            if (retryAfter != null) {
                typedBuilder.retryAfter(retryAfter);
            }
        }

        // Handle rate limit exceptions
        if (ex instanceof RateLimitException || ex instanceof QuotaExceededException) {
            typedBuilder.retryable(true);
            Map<String, Object> metadata = ex.getMetadata();

            var rateLimitInfo = ErrorResponse.RateLimitInfo.builder()
                    .limit(((Number) metadata.getOrDefault("limit", 0)).intValue())
                    .remaining(((Number) metadata.getOrDefault("remaining", 0)).intValue())
                    .resetTime(((Number) metadata.getOrDefault("resetTime", 0L)).longValue())
                    .windowSeconds(((Number) metadata.getOrDefault("windowSeconds", 60)).intValue())
                    .limitType((String) metadata.getOrDefault("limitType", "unknown"))
                    .build();

            typedBuilder.rateLimitInfo(rateLimitInfo);

            Integer retryAfter = (Integer) metadata.get("retryAfter");
            if (retryAfter != null) {
                typedBuilder.retryAfter(retryAfter);
            }
        }

        // Handle retry exhausted exceptions
        if (ex instanceof RetryExhaustedException) {
            typedBuilder.retryable(false);
            Map<String, Object> metadata = ex.getMetadata();
            Integer retryAfter = (Integer) metadata.get("retryAfter");
            if (retryAfter != null) {
                typedBuilder.retryAfter(retryAfter);
            }
        }

        // Handle bulkhead exceptions
        if (ex instanceof BulkheadException) {
            typedBuilder.retryable(true);
            Map<String, Object> metadata = ex.getMetadata();
            Integer retryAfter = (Integer) metadata.getOrDefault("retryAfter", 5);
            typedBuilder.retryAfter(retryAfter);
        }

        // Determine if retryable for other exceptions
        if (typedBuilder.build().getRetryable() == null) {
            typedBuilder.retryable(isRetryable(ex));
        }
    }

    /**
     * Enriches error response with suggestions.
     *
     * @param builder the error response builder
     * @param ex the business exception
     */
    private void enrichWithSuggestions(Object builder, BusinessException ex) {
        @SuppressWarnings("unchecked")
        var typedBuilder = (ErrorResponse.ErrorResponseBuilder) builder;
        if (!errorProperties.isIncludeSuggestions()) {
            return;
        }

        Map<String, Object> metadata = ex.getMetadata();
        if (metadata != null && metadata.containsKey("suggestion")) {
            typedBuilder.suggestion(maskSensitiveData(metadata.get("suggestion").toString()));
            return;
        }

        // Default suggestions based on status code
        String suggestion = getDefaultSuggestion(ex.getStatus());
        if (suggestion != null) {
            typedBuilder.suggestion(suggestion);
        }
    }

    /**
     * Enriches error response with documentation links.
     *
     * @param builder the error response builder
     * @param ex the business exception
     */
    private void enrichWithDocumentation(Object builder, BusinessException ex) {
        @SuppressWarnings("unchecked")
        var typedBuilder = (ErrorResponse.ErrorResponseBuilder) builder;
        if (!errorProperties.isIncludeDocumentation()) {
            return;
        }

        String docBase = errorProperties.getDocumentationBaseUrl();
        if (ex.getCode() != null && docBase != null) {
            typedBuilder.documentation(docBase + "/" + ex.getCode().toLowerCase());
        }

        if (errorProperties.isIncludeHelpUrl() && errorProperties.getHelpBaseUrl() != null) {
            String helpUrl = errorProperties.getHelpBaseUrl();
            if (ex.getCode() != null) {
                helpUrl += "/" + ex.getCode().toLowerCase();
            }
            typedBuilder.helpUrl(helpUrl);
        }
    }

    /**
     * Enriches error response with environment-specific debug information.
     *
     * @param builder the error response builder
     * @param ex the business exception
     * @param exchange the server web exchange
     */
    private void enrichWithDebugInfo(Object builder, BusinessException ex,
                                      ServerWebExchange exchange) {
        @SuppressWarnings("unchecked")
        var typedBuilder = (ErrorResponse.ErrorResponseBuilder) builder;
        // Only include debug info in non-production environments
        if (!isProductionEnvironment()) {
            if (errorProperties.isIncludeStackTrace() && ex.getCause() != null) {
                typedBuilder.stackTrace(getStackTraceAsString(ex));
            }

            if (errorProperties.isIncludeDebugInfo()) {
                Map<String, Object> debugInfo = new HashMap<>();
                debugInfo.put("exceptionClass", ex.getClass().getName());
                debugInfo.put("timestamp", Instant.now().toString());
                debugInfo.put("method", exchange.getRequest().getMethod().toString());
                debugInfo.put("headers", sanitizeHeaders(exchange.getRequest().getHeaders()));

                if (errorProperties.isIncludeExceptionCause() && ex.getCause() != null) {
                    debugInfo.put("cause", ex.getCause().getClass().getName());
                    debugInfo.put("causeMessage", ex.getCause().getMessage());
                }

                typedBuilder.debugInfo(debugInfo);
            }

            if (errorProperties.isIncludeExceptionCause() && ex.getCause() != null) {
                typedBuilder.details("Caused by: " + ex.getCause().getMessage());
            }
        }
    }

    /**
     * Determines the error category based on exception type.
     */
    private ErrorResponse.ErrorCategory determineCategory(BusinessException ex) {
        if (ex instanceof ValidationException || ex instanceof InvalidRequestException) {
            return ErrorResponse.ErrorCategory.VALIDATION;
        } else if (ex instanceof UnauthorizedException || ex instanceof ForbiddenException ||
                   ex instanceof AuthorizationException) {
            return ErrorResponse.ErrorCategory.SECURITY;
        } else if (ex instanceof ResourceNotFoundException || ex instanceof ConflictException ||
                   ex instanceof GoneException) {
            return ErrorResponse.ErrorCategory.RESOURCE;
        } else if (ex instanceof RateLimitException || ex instanceof QuotaExceededException) {
            return ErrorResponse.ErrorCategory.RATE_LIMIT;
        } else if (ex instanceof CircuitBreakerException || ex instanceof BulkheadException) {
            return ErrorResponse.ErrorCategory.CIRCUIT_BREAKER;
        } else if (ex instanceof ThirdPartyServiceException || ex instanceof BadGatewayException ||
                   ex instanceof GatewayTimeoutException) {
            return ErrorResponse.ErrorCategory.EXTERNAL;
        } else if (ex instanceof ServiceException || ex instanceof ServiceUnavailableException ||
                   ex instanceof OperationTimeoutException) {
            return ErrorResponse.ErrorCategory.TECHNICAL;
        } else {
            return ErrorResponse.ErrorCategory.BUSINESS;
        }
    }

    /**
     * Determines the error severity based on exception type and status code.
     */
    private ErrorResponse.ErrorSeverity determineSeverity(BusinessException ex) {
        HttpStatus status = ex.getStatus();

        // Critical errors
        if (status.is5xxServerError() && !(ex instanceof ServiceUnavailableException)) {
            return ErrorResponse.ErrorSeverity.CRITICAL;
        }

        // High severity
        if (ex instanceof CircuitBreakerException || ex instanceof DataIntegrityException ||
            ex instanceof UnauthorizedException) {
            return ErrorResponse.ErrorSeverity.HIGH;
        }

        // Low severity
        if (ex instanceof ValidationException || ex instanceof ResourceNotFoundException) {
            return ErrorResponse.ErrorSeverity.LOW;
        }

        // Default to medium
        return ErrorResponse.ErrorSeverity.MEDIUM;
    }

    /**
     * Determines if an exception is retryable.
     */
    private boolean isRetryable(BusinessException ex) {
        // Retryable status codes
        HttpStatus status = ex.getStatus();
        if (status == HttpStatus.REQUEST_TIMEOUT ||
            status == HttpStatus.TOO_MANY_REQUESTS ||
            status == HttpStatus.SERVICE_UNAVAILABLE ||
            status == HttpStatus.GATEWAY_TIMEOUT) {
            return true;
        }

        // Specific exception types
        if (ex instanceof OperationTimeoutException ||
            ex instanceof ServiceUnavailableException ||
            ex instanceof GatewayTimeoutException) {
            return true;
        }

        return false;
    }

    /**
     * Gets default suggestion based on HTTP status.
     */
    private String getDefaultSuggestion(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Verify the resource identifier and ensure it exists.";
            case BAD_REQUEST -> "Check your request parameters and try again.";
            case UNAUTHORIZED -> "Please authenticate and try again.";
            case FORBIDDEN -> "You don't have permission to access this resource. Contact an administrator if you need access.";
            case CONFLICT -> "The request conflicts with the current state of the resource. Refresh and try again.";
            case TOO_MANY_REQUESTS -> "You've exceeded the rate limit. Please wait and try again later.";
            case PAYLOAD_TOO_LARGE -> "The request payload is too large. Try reducing the size of your request.";
            case UNSUPPORTED_MEDIA_TYPE -> "The request format is not supported. Check the Content-Type header.";
            case INTERNAL_SERVER_ERROR -> "An unexpected error occurred. Please contact support with the trace ID.";
            case SERVICE_UNAVAILABLE -> "The service is temporarily unavailable. Please try again later.";
            case GATEWAY_TIMEOUT -> "The request timed out. Please try again.";
            default -> "Please check your request and try again.";
        };
    }

    /**
     * Logs error with appropriate level based on status code.
     */
    private void logError(BusinessException ex, ServerWebExchange exchange) {
        if (!errorProperties.isLogAllErrors()) {
            return;
        }

        String message = String.format("Error occurred: %s - %s [%s %s]",
                ex.getCode(), maskSensitiveData(ex.getMessage()),
                exchange.getRequest().getMethod(), exchange.getRequest().getPath());

        HttpStatus status = ex.getStatus();
        if (status.is5xxServerError()) {
            logAtLevel(errorProperties.getServerErrorLogLevel(), message, ex);
        } else if (status.is4xxClientError()) {
            logAtLevel(errorProperties.getClientErrorLogLevel(), message, ex);
        } else {
            log.info(message);
        }
    }

    /**
     * Logs message at specified level.
     */
    private void logAtLevel(String level, String message, Throwable ex) {
        switch (level.toUpperCase()) {
            case "ERROR" -> log.error(message, ex);
            case "WARN" -> log.warn(message, ex);
            case "INFO" -> log.info(message);
            case "DEBUG" -> log.debug(message, ex);
            case "TRACE" -> log.trace(message, ex);
            default -> log.error(message, ex);
        }
    }

    /**
     * Records error metric.
     */
    private void recordErrorMetric(Throwable ex) {
        if (!errorProperties.isEnableMetrics() || !isEnabled()) {
            return;
        }

        String exceptionType = ex.getClass().getSimpleName();
        counter("errors.count", "exception", exceptionType, "application", applicationName).increment();
    }

    /**
     * Records error handling duration.
     */
    private void recordErrorDuration(Throwable ex, long durationMs) {
        if (!errorProperties.isEnableMetrics() || !isEnabled()) {
            return;
        }

        String exceptionType = ex.getClass().getSimpleName();
        timer("errors.duration", "exception", exceptionType, "application", applicationName)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    /**
     * Adds security headers to response.
     */
    private void addSecurityHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "DENY");
        headers.add("X-XSS-Protection", "1; mode=block");
    }

    /**
     * Masks sensitive data in messages.
     */
    private String maskSensitiveData(String message) {
        if (!errorProperties.isMaskSensitiveData() || message == null) {
            return message;
        }

        if (piiMaskingService.isPresent()) {
            return piiMaskingService.get().maskPiiData(message);
        }

        return message;
    }

    /**
     * Checks if running in production environment.
     */
    private boolean isProductionEnvironment() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod") ||
               Arrays.asList(environment.getActiveProfiles()).contains("production");
    }

    /**
     * Gets stack trace as string.
     */
    private String getStackTraceAsString(Throwable ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Sanitizes headers by removing sensitive information.
     */
    private Map<String, String> sanitizeHeaders(HttpHeaders headers) {
        Map<String, String> sanitized = new HashMap<>();
        List<String> sensitiveHeaders = Arrays.asList(
                "authorization", "cookie", "set-cookie", "x-api-key", "api-key"
        );

        headers.forEach((key, values) -> {
            if (sensitiveHeaders.contains(key.toLowerCase())) {
                sanitized.put(key, "***REDACTED***");
            } else {
                sanitized.put(key, String.join(", ", values));
            }
        });

        return sanitized;
    }

    /**
     * Converts ErrorResponse to JSON.
     */
    private String toJson(ErrorResponse errorResponse) {
        try {
            return objectMapper.writeValueAsString(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Error converting ErrorResponse to JSON", e);
            return "{\"message\":\"Error processing response\",\"status\":500}";
        }
    }
}

