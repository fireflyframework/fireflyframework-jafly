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
package org.fireflyframework.client.exception;

import org.fireflyframework.client.ClientType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Maps HTTP status codes to typed service client exceptions.
 * 
 * <p>This class provides automatic error mapping for REST service clients,
 * converting HTTP error responses into appropriate exception types with
 * rich error context.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public class HttpErrorMapper {

    /**
     * Maps an HTTP error response to a typed exception.
     *
     * @param response the HTTP response
     * @param serviceName the name of the service
     * @param endpoint the endpoint that was called
     * @param method the HTTP method
     * @param requestId the request ID
     * @param startTime the request start time
     * @return a Mono that emits the appropriate exception
     */
    public static Mono<? extends Throwable> mapHttpError(
            ClientResponse response,
            String serviceName,
            String endpoint,
            String method,
            String requestId,
            Instant startTime) {
        
        HttpStatusCode statusCode = response.statusCode();
        int status = statusCode.value();
        
        return response.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> {
                ErrorContext context = buildErrorContext(
                    response, serviceName, endpoint, method, requestId, startTime, body);
                
                return createException(status, body, context);
            });
    }

    /**
     * Creates the appropriate exception based on HTTP status code.
     */
    private static ServiceClientException createException(int status, String body, ErrorContext context) {
        String message = extractErrorMessage(body, status);
        
        return switch (status) {
            case 400 -> new ServiceValidationException(message, context);
            case 401, 403 -> new ServiceAuthenticationException(message, context);
            case 404 -> new ServiceNotFoundException(message, context);
            case 408 -> new ServiceTimeoutException(message, context);
            case 409 -> new ServiceConflictException(message, context);
            case 422 -> new ServiceUnprocessableEntityException(message, 
                ErrorResponseParser.parseValidationErrors(body), context);
            case 429 -> new ServiceRateLimitException(message, 
                extractRetryAfter(context), context);
            case 500 -> new ServiceInternalErrorException(message, context);
            case 502, 503, 504 -> new ServiceTemporarilyUnavailableException(message, context);
            default -> {
                if (status >= 400 && status < 500) {
                    yield new ServiceClientException("Client error: " + message, context);
                } else if (status >= 500) {
                    yield new ServiceUnavailableException("Server error: " + message, context);
                } else {
                    yield new ServiceClientException("HTTP error " + status + ": " + message, context);
                }
            }
        };
    }

    /**
     * Builds error context from the HTTP response.
     */
    private static ErrorContext buildErrorContext(
            ClientResponse response,
            String serviceName,
            String endpoint,
            String method,
            String requestId,
            Instant startTime,
            String body) {
        
        Duration elapsedTime = startTime != null ? Duration.between(startTime, Instant.now()) : null;
        
        return ErrorContext.builder()
            .serviceName(serviceName)
            .endpoint(endpoint)
            .method(method)
            .clientType(ClientType.REST)
            .requestId(requestId)
            .httpStatusCode(response.statusCode().value())
            .responseBody(body != null && body.length() > 1000 ? body.substring(0, 1000) + "..." : body)
            .headers(extractHeaders(response.headers().asHttpHeaders()))
            .elapsedTime(elapsedTime)
            .build();
    }

    /**
     * Extracts error message from response body or uses default.
     */
    private static String extractErrorMessage(String body, int status) {
        if (body == null || body.isEmpty()) {
            return "HTTP " + status + " error";
        }
        
        String message = ErrorResponseParser.parseErrorMessage(body);
        return message != null ? message : "HTTP " + status + " error";
    }

    /**
     * Extracts Retry-After header value in seconds.
     */
    private static Integer extractRetryAfter(ErrorContext context) {
        if (context.getHeaders().containsKey("Retry-After")) {
            try {
                return Integer.parseInt(context.getHeaders().get("Retry-After"));
            } catch (NumberFormatException e) {
                // Ignore, return null
            }
        }
        return null;
    }

    /**
     * Extracts relevant headers from the response.
     */
    private static java.util.Map<String, String> extractHeaders(HttpHeaders headers) {
        java.util.Map<String, String> headerMap = new java.util.HashMap<>();
        
        // Extract important headers
        if (headers.containsKey("X-Request-ID")) {
            headerMap.put("X-Request-ID", headers.getFirst("X-Request-ID"));
        }
        if (headers.containsKey("X-Correlation-ID")) {
            headerMap.put("X-Correlation-ID", headers.getFirst("X-Correlation-ID"));
        }
        if (headers.containsKey("Retry-After")) {
            headerMap.put("Retry-After", headers.getFirst("Retry-After"));
        }
        if (headers.containsKey("Content-Type")) {
            headerMap.put("Content-Type", headers.getFirst("Content-Type"));
        }
        
        return headerMap;
    }
}

