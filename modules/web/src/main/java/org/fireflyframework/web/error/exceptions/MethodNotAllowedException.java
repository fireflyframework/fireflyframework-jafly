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

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Exception thrown when a request uses an HTTP method that is not allowed for the resource.
 * Results in a 405 METHOD NOT ALLOWED response.
 */
public class MethodNotAllowedException extends BusinessException {
    
    /**
     * The HTTP method that was used.
     */
    private final HttpMethod method;
    
    /**
     * The allowed HTTP methods for the resource.
     */
    private final Set<HttpMethod> allowedMethods;
    
    /**
     * Creates a new MethodNotAllowedException with the given message.
     *
     * @param message the error message
     * @param method the HTTP method that was used
     * @param allowedMethods the allowed HTTP methods for the resource
     */
    public MethodNotAllowedException(String message, HttpMethod method, Set<HttpMethod> allowedMethods) {
        super(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message);
        this.method = method;
        this.allowedMethods = Collections.unmodifiableSet(new HashSet<>(allowedMethods));
    }
    
    /**
     * Creates a new MethodNotAllowedException with a code and message.
     *
     * @param code the error code
     * @param message the error message
     * @param method the HTTP method that was used
     * @param allowedMethods the allowed HTTP methods for the resource
     */
    public MethodNotAllowedException(String code, String message, HttpMethod method, Set<HttpMethod> allowedMethods) {
        super(HttpStatus.METHOD_NOT_ALLOWED, code, message);
        this.method = method;
        this.allowedMethods = Collections.unmodifiableSet(new HashSet<>(allowedMethods));
    }
    
    /**
     * Returns the HTTP method that was used.
     *
     * @return the method
     */
    public HttpMethod getMethod() {
        return method;
    }
    
    /**
     * Returns the allowed HTTP methods for the resource.
     *
     * @return the allowed methods
     */
    public Set<HttpMethod> getAllowedMethods() {
        return allowedMethods;
    }
    
    /**
     * Creates a new MethodNotAllowedException for a resource.
     *
     * @param method the HTTP method that was used
     * @param allowedMethods the allowed HTTP methods for the resource
     * @return a new MethodNotAllowedException
     */
    public static MethodNotAllowedException forResource(HttpMethod method, HttpMethod... allowedMethods) {
        Set<HttpMethod> allowed = new HashSet<>(Arrays.asList(allowedMethods));
        return new MethodNotAllowedException(
                String.format("Method %s is not allowed for this resource. Allowed methods: %s", 
                        method, Arrays.toString(allowedMethods)),
                method,
                allowed
        );
    }
    
    /**
     * Creates a new MethodNotAllowedException for a specific resource.
     *
     * @param resourcePath the path of the resource
     * @param method the HTTP method that was used
     * @param allowedMethods the allowed HTTP methods for the resource
     * @return a new MethodNotAllowedException
     */
    public static MethodNotAllowedException forResource(String resourcePath, HttpMethod method, HttpMethod... allowedMethods) {
        Set<HttpMethod> allowed = new HashSet<>(Arrays.asList(allowedMethods));
        return new MethodNotAllowedException(
                String.format("Method %s is not allowed for resource '%s'. Allowed methods: %s", 
                        method, resourcePath, Arrays.toString(allowedMethods)),
                method,
                allowed
        );
    }
}
