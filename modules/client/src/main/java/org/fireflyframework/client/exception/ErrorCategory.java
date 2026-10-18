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

/**
 * Categories for service client errors.
 * 
 * <p>This enum provides a high-level classification of errors to help
 * with monitoring, alerting, and error handling strategies.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public enum ErrorCategory {
    
    /**
     * Client-side errors (4xx HTTP status codes).
     * These errors indicate problems with the request.
     */
    CLIENT_ERROR,
    
    /**
     * Server-side errors (5xx HTTP status codes).
     * These errors indicate problems with the service.
     */
    SERVER_ERROR,
    
    /**
     * Network connectivity errors.
     * These errors indicate problems with network communication.
     */
    NETWORK_ERROR,
    
    /**
     * Authentication or authorization errors.
     * These errors indicate problems with credentials or permissions.
     */
    AUTHENTICATION_ERROR,
    
    /**
     * Request validation errors.
     * These errors indicate problems with request data.
     */
    VALIDATION_ERROR,
    
    /**
     * Rate limiting or throttling errors.
     * These errors indicate too many requests.
     */
    RATE_LIMIT_ERROR,
    
    /**
     * Circuit breaker errors.
     * These errors indicate the circuit breaker is protecting the service.
     */
    CIRCUIT_BREAKER_ERROR,
    
    /**
     * Timeout errors.
     * These errors indicate the request took too long.
     */
    TIMEOUT_ERROR,
    
    /**
     * Serialization or deserialization errors.
     * These errors indicate problems parsing request/response data.
     */
    SERIALIZATION_ERROR,
    
    /**
     * Configuration errors.
     * These errors indicate problems with client configuration.
     */
    CONFIGURATION_ERROR,
    
    /**
     * Unknown or unclassified errors.
     */
    UNKNOWN_ERROR
}

