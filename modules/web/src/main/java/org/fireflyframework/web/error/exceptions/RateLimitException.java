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


package org.fireflyframework.web.error.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a rate limit is exceeded.
 * Results in a 429 TOO MANY REQUESTS response.
 */
public class RateLimitException extends BusinessException {
    
    /**
     * The number of seconds to wait before retrying.
     */
    private final Integer retryAfterSeconds;
    
    /**
     * Creates a new RateLimitException with the given message.
     *
     * @param message the error message
     */
    public RateLimitException(String message) {
        this(message, null);
    }
    
    /**
     * Creates a new RateLimitException with the given message and retry after seconds.
     *
     * @param message the error message
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public RateLimitException(String message, Integer retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    /**
     * Creates a new RateLimitException with a code, message, and retry after seconds.
     *
     * @param code the error code
     * @param message the error message
     * @param retryAfterSeconds the number of seconds to wait before retrying
     */
    public RateLimitException(String code, String message, Integer retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    /**
     * Returns the number of seconds to wait before retrying.
     *
     * @return the retry after seconds, or null if not specified
     */
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    /**
     * Creates a new RateLimitException for a specific resource.
     *
     * @param resource the resource that has been rate limited
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @return a new RateLimitException
     */
    public static RateLimitException forResource(String resource, Integer retryAfterSeconds) {
        return new RateLimitException(
                "RATE_LIMIT_EXCEEDED",
                String.format("Rate limit exceeded for resource '%s'", resource),
                retryAfterSeconds
        );
    }
    
    /**
     * Creates a new RateLimitException for a specific user.
     *
     * @param userId the ID of the user that has been rate limited
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @return a new RateLimitException
     */
    public static RateLimitException forUser(String userId, Integer retryAfterSeconds) {
        return new RateLimitException(
                "USER_RATE_LIMIT_EXCEEDED",
                String.format("Rate limit exceeded for user '%s'", userId),
                retryAfterSeconds
        );
    }
    
    /**
     * Creates a new RateLimitException for a specific IP address.
     *
     * @param ipAddress the IP address that has been rate limited
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @return a new RateLimitException
     */
    public static RateLimitException forIpAddress(String ipAddress, Integer retryAfterSeconds) {
        return new RateLimitException(
                "IP_RATE_LIMIT_EXCEEDED",
                String.format("Rate limit exceeded for IP address '%s'", ipAddress),
                retryAfterSeconds
        );
    }
}
