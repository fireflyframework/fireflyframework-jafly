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

package org.fireflyframework.rules.core.services;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service interface for making REST API calls from within rule evaluations.
 * Provides reactive, non-blocking HTTP operations with proper error handling.
 */
public interface RestCallService {
    
    /**
     * Perform a GET request to the specified URL
     * 
     * @param url the target URL
     * @param headers optional HTTP headers
     * @param timeoutMs timeout in milliseconds (default: 5000ms)
     * @return Mono containing the response as a Map (parsed JSON)
     */
    Mono<Map<String, Object>> get(String url, Map<String, String> headers, Long timeoutMs);
    
    /**
     * Perform a POST request to the specified URL
     * 
     * @param url the target URL
     * @param body the request body (will be serialized to JSON)
     * @param headers optional HTTP headers
     * @param timeoutMs timeout in milliseconds (default: 5000ms)
     * @return Mono containing the response as a Map (parsed JSON)
     */
    Mono<Map<String, Object>> post(String url, Object body, Map<String, String> headers, Long timeoutMs);
    
    /**
     * Perform a PUT request to the specified URL
     * 
     * @param url the target URL
     * @param body the request body (will be serialized to JSON)
     * @param headers optional HTTP headers
     * @param timeoutMs timeout in milliseconds (default: 5000ms)
     * @return Mono containing the response as a Map (parsed JSON)
     */
    Mono<Map<String, Object>> put(String url, Object body, Map<String, String> headers, Long timeoutMs);
    
    /**
     * Perform a DELETE request to the specified URL
     * 
     * @param url the target URL
     * @param headers optional HTTP headers
     * @param timeoutMs timeout in milliseconds (default: 5000ms)
     * @return Mono containing the response as a Map (parsed JSON)
     */
    Mono<Map<String, Object>> delete(String url, Map<String, String> headers, Long timeoutMs);
    
    /**
     * Perform a PATCH request to the specified URL
     * 
     * @param url the target URL
     * @param body the request body (will be serialized to JSON)
     * @param headers optional HTTP headers
     * @param timeoutMs timeout in milliseconds (default: 5000ms)
     * @return Mono containing the response as a Map (parsed JSON)
     */
    Mono<Map<String, Object>> patch(String url, Object body, Map<String, String> headers, Long timeoutMs);
    
    /**
     * Generic method to perform any HTTP request
     * 
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH)
     * @param url the target URL
     * @param body optional request body
     * @param headers optional HTTP headers
     * @param timeoutMs timeout in milliseconds (default: 5000ms)
     * @return Mono containing the response as a Map (parsed JSON)
     */
    Mono<Map<String, Object>> request(String method, String url, Object body, Map<String, String> headers, Long timeoutMs);
}
