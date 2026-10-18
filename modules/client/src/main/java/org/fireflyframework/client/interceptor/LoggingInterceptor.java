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

package org.fireflyframework.client.interceptor;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

/**
 * Logging interceptor for ServiceClient operations.
 * 
 * <p>This interceptor provides comprehensive logging of service client requests and responses,
 * including timing information, headers, and error details. It's particularly useful for
 * debugging and monitoring service interactions.
 *
 * <p>Example usage:
 * <pre>{@code
 * LoggingInterceptor loggingInterceptor = LoggingInterceptor.builder()
 *     .logLevel(LogLevel.INFO)
 *     .logHeaders(true)
 *     .logBody(false) // Don't log sensitive data
 *     .slowRequestThreshold(Duration.ofSeconds(5))
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class LoggingInterceptor implements ServiceClientInterceptor {

    private final LogLevel logLevel;
    private final boolean logHeaders;
    private final boolean logBody;
    private final boolean logQueryParams;
    private final Duration slowRequestThreshold;
    private final Set<String> sensitiveHeaders;
    private final Set<String> excludedServices;

    private LoggingInterceptor(Builder builder) {
        this.logLevel = builder.logLevel;
        this.logHeaders = builder.logHeaders;
        this.logBody = builder.logBody;
        this.logQueryParams = builder.logQueryParams;
        this.slowRequestThreshold = builder.slowRequestThreshold;
        this.sensitiveHeaders = Set.copyOf(builder.sensitiveHeaders);
        this.excludedServices = Set.copyOf(builder.excludedServices);
    }

    @Override
    public Mono<InterceptorResponse> intercept(InterceptorRequest request, InterceptorChain chain) {
        if (excludedServices.contains(request.getServiceName())) {
            return chain.proceed(request);
        }

        long startTime = System.currentTimeMillis();
        String requestId = generateRequestId();

        logRequest(request, requestId);

        return chain.proceed(request)
            .doOnSuccess(response -> logResponse(request, response, requestId, startTime))
            .doOnError(error -> logError(request, error, requestId, startTime))
            .doFinally(signal -> {
                long duration = System.currentTimeMillis() - startTime;
                if (Duration.ofMillis(duration).compareTo(slowRequestThreshold) > 0) {
                    logSlowRequest(request, requestId, duration);
                }
            });
    }

    @Override
    public boolean shouldIntercept(InterceptorRequest request) {
        return !excludedServices.contains(request.getServiceName());
    }

    @Override
    public int getOrder() {
        return 100; // Execute early in the chain
    }

    private void logRequest(InterceptorRequest request, String requestId) {
        if (!shouldLog()) return;

        StringBuilder logMessage = new StringBuilder()
            .append("ServiceClient Request [").append(requestId).append("] ")
            .append(request.getMethod()).append(" ")
            .append(request.getServiceName()).append(request.getEndpoint());

        if (logQueryParams && !request.getQueryParams().isEmpty()) {
            logMessage.append(" with query params: ").append(request.getQueryParams());
        }

        if (logHeaders && !request.getHeaders().isEmpty()) {
            logMessage.append(" with headers: ").append(sanitizeHeaders(request.getHeaders()));
        }

        if (logBody && request.getBody() != null) {
            logMessage.append(" with body: ").append(sanitizeBody(request.getBody()));
        }

        logAtLevel(logMessage.toString());
    }

    private void logResponse(InterceptorRequest request, InterceptorResponse response, String requestId, long startTime) {
        if (!shouldLog()) return;

        long duration = System.currentTimeMillis() - startTime;
        
        StringBuilder logMessage = new StringBuilder()
            .append("ServiceClient Response [").append(requestId).append("] ")
            .append("status: ").append(response.getStatusCode())
            .append(", duration: ").append(duration).append("ms");

        if (logHeaders && !response.getHeaders().isEmpty()) {
            logMessage.append(", headers: ").append(sanitizeHeaders(response.getHeaders()));
        }

        if (logBody && response.getBody() != null) {
            logMessage.append(", body: ").append(sanitizeBody(response.getBody()));
        }

        if (response.isSuccessful()) {
            logAtLevel(logMessage.toString());
        } else {
            log.warn(logMessage.toString());
        }
    }

    private void logError(InterceptorRequest request, Throwable error, String requestId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        
        log.error("ServiceClient Error [{}] {} {} failed after {}ms: {}", 
            requestId, 
            request.getMethod(), 
            request.getServiceName() + request.getEndpoint(),
            duration,
            error.getMessage(), 
            error);
    }

    private void logSlowRequest(InterceptorRequest request, String requestId, long duration) {
        log.warn("Slow ServiceClient Request [{}] {} {} took {}ms (threshold: {}ms)", 
            requestId,
            request.getMethod(),
            request.getServiceName() + request.getEndpoint(),
            duration,
            slowRequestThreshold.toMillis());
    }

    private boolean shouldLog() {
        return switch (logLevel) {
            case TRACE -> log.isTraceEnabled();
            case DEBUG -> log.isDebugEnabled();
            case INFO -> log.isInfoEnabled();
            case WARN -> log.isWarnEnabled();
            case ERROR -> log.isErrorEnabled();
        };
    }

    private void logAtLevel(String message) {
        switch (logLevel) {
            case TRACE -> log.trace(message);
            case DEBUG -> log.debug(message);
            case INFO -> log.info(message);
            case WARN -> log.warn(message);
            case ERROR -> log.error(message);
        }
    }

    private java.util.Map<String, String> sanitizeHeaders(java.util.Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                entry -> sensitiveHeaders.contains(entry.getKey().toLowerCase()) ? "***" : entry.getValue()
            ));
    }

    private String sanitizeBody(Object body) {
        String bodyStr = String.valueOf(body);
        // Basic sanitization - in practice, you might want more sophisticated logic
        if (bodyStr.length() > 1000) {
            return bodyStr.substring(0, 1000) + "... (truncated)";
        }
        return bodyStr;
    }

    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis() + "-" + Thread.currentThread().threadId();
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    public static class Builder {
        private LogLevel logLevel = LogLevel.INFO;
        private boolean logHeaders = true;
        private boolean logBody = false;
        private boolean logQueryParams = true;
        private Duration slowRequestThreshold = Duration.ofSeconds(5);
        private java.util.Set<String> sensitiveHeaders = java.util.Set.of(
            "authorization", "x-api-key", "cookie", "set-cookie", "x-auth-token"
        );
        private java.util.Set<String> excludedServices = java.util.Set.of();

        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder logHeaders(boolean logHeaders) {
            this.logHeaders = logHeaders;
            return this;
        }

        public Builder logBody(boolean logBody) {
            this.logBody = logBody;
            return this;
        }

        public Builder logQueryParams(boolean logQueryParams) {
            this.logQueryParams = logQueryParams;
            return this;
        }

        public Builder slowRequestThreshold(Duration threshold) {
            this.slowRequestThreshold = threshold;
            return this;
        }

        public Builder sensitiveHeaders(java.util.Set<String> headers) {
            this.sensitiveHeaders = headers;
            return this;
        }

        public Builder excludedServices(java.util.Set<String> services) {
            this.excludedServices = services;
            return this;
        }

        public LoggingInterceptor build() {
            return new LoggingInterceptor(this);
        }
    }
}
