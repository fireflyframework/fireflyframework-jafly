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

import java.util.Map;

/**
 * Represents a response in the interceptor chain.
 */
public interface InterceptorResponse {
    /**
     * Gets the response body.
     */
    Object getBody();

    /**
     * Gets the HTTP status code (for REST clients).
     */
    int getStatusCode();

    /**
     * Gets response headers.
     */
    Map<String, String> getHeaders();

    /**
     * Gets the response time in milliseconds.
     */
    long getResponseTimeMs();

    /**
     * Gets whether the response was successful.
     */
    boolean isSuccessful();

    /**
     * Gets any error that occurred.
     */
    Throwable getError();

    /**
     * Gets additional response attributes.
     */
    Map<String, Object> getAttributes();

    /**
     * Creates a modified copy of this response.
     */
    InterceptorResponse withBody(Object body);
    InterceptorResponse withHeader(String name, String value);
    InterceptorResponse withAttribute(String name, Object value);
}

