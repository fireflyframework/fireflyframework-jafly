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

import java.time.Duration;
import java.util.Map;

/**
 * Represents a request in the interceptor chain.
 */
public interface InterceptorRequest {
    /**
     * Gets the service name.
     */
    String getServiceName();

    /**
     * Gets the endpoint being called.
     */
    String getEndpoint();

    /**
     * Gets the HTTP method (for REST clients).
     */
    String getMethod();

    /**
     * Gets the request body.
     */
    Object getBody();

    /**
     * Gets request headers.
     */
    Map<String, String> getHeaders();

    /**
     * Gets query parameters.
     */
    Map<String, Object> getQueryParams();

    /**
     * Gets path parameters.
     */
    Map<String, Object> getPathParams();

    /**
     * Gets the client type.
     */
    String getClientType();

    /**
     * Gets the request timeout.
     */
    Duration getTimeout();

    /**
     * Gets additional request attributes.
     */
    Map<String, Object> getAttributes();

    /**
     * Creates a modified copy of this request.
     */
    InterceptorRequest withHeader(String name, String value);
    InterceptorRequest withBody(Object body);
    InterceptorRequest withTimeout(Duration timeout);
    InterceptorRequest withAttribute(String name, Object value);
}

