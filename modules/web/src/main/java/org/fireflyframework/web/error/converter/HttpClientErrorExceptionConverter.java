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


package org.fireflyframework.web.error.converter;

import org.fireflyframework.web.error.exceptions.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Converter for Spring's HttpClientErrorException.
 * Converts HTTP client error exceptions to the appropriate business exceptions.
 */
@Component
@ConditionalOnClass(HttpClientErrorException.class)
public class HttpClientErrorExceptionConverter implements ExceptionConverter<HttpClientErrorException> {

    /**
     * Creates a new HttpClientErrorExceptionConverter.
     */
    public HttpClientErrorExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<HttpClientErrorException> getExceptionType() {
        return HttpClientErrorException.class;
    }

    @Override
    public BusinessException convert(HttpClientErrorException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String responseBody = exception.getResponseBodyAsString();

        switch (status) {
            case NOT_FOUND:
                return new ResourceNotFoundException(
                        "RESOURCE_NOT_FOUND",
                        "The requested resource was not found: " + responseBody
                );
            case UNAUTHORIZED:
                return new UnauthorizedException(
                        "AUTHENTICATION_REQUIRED",
                        "Authentication is required: " + responseBody
                );
            case FORBIDDEN:
                return new ForbiddenException(
                        "ACCESS_DENIED",
                        "Access denied: " + responseBody
                );
            case BAD_REQUEST:
                return new InvalidRequestException(
                        "INVALID_REQUEST",
                        "Invalid request: " + responseBody
                );
            case CONFLICT:
                return new ConflictException(
                        "RESOURCE_CONFLICT",
                        "Resource conflict: " + responseBody
                );
            case TOO_MANY_REQUESTS:
                Integer retryAfter = null;
                if (exception.getResponseHeaders() != null && exception.getResponseHeaders().getFirst("Retry-After") != null) {
                    try {
                        retryAfter = Integer.parseInt(exception.getResponseHeaders().getFirst("Retry-After"));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                return new RateLimitException(
                        "RATE_LIMIT_EXCEEDED",
                        "Rate limit exceeded: " + responseBody,
                        retryAfter
                );
            case REQUEST_TIMEOUT:
                return new OperationTimeoutException(
                        "REQUEST_TIMEOUT",
                        "Request timed out: " + responseBody,
                        "http-request",
                        0
                );
            default:
                return new BusinessException(
                        status,
                        "HTTP_CLIENT_ERROR",
                        "HTTP client error: " + responseBody
                );
        }
    }
}
