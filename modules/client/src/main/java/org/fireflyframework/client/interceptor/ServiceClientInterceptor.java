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

import reactor.core.publisher.Mono;

/**
 * Base interface for ServiceClient interceptors.
 * 
 * <p>Interceptors provide a way to add cross-cutting concerns to service client operations
 * such as logging, metrics collection, authentication, request/response transformation,
 * and custom business logic.
 *
 * <p>Example usage:
 * <pre>{@code
 * public class LoggingInterceptor implements ServiceClientInterceptor {
 *     @Override
 *     public Mono<InterceptorResponse> intercept(InterceptorRequest request, InterceptorChain chain) {
 *         log.info("Executing request to {}", request.getEndpoint());
 *         return chain.proceed(request)
 *             .doOnSuccess(response -> log.info("Request completed with status: {}", response.getStatusCode()))
 *             .doOnError(error -> log.error("Request failed: {}", error.getMessage()));
 *     }
 * }
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
public interface ServiceClientInterceptor {

    /**
     * Intercepts a service client request and optionally modifies the request or response.
     * 
     * <p>Implementations should call {@code chain.proceed(request)} to continue the chain
     * or return a custom response to short-circuit the execution.
     *
     * @param request the interceptor request containing request details
     * @param chain the interceptor chain to continue execution
     * @return a Mono containing the interceptor response
     */
    Mono<InterceptorResponse> intercept(InterceptorRequest request, InterceptorChain chain);

    /**
     * Returns the order of this interceptor in the chain.
     * Lower values have higher priority and execute first.
     *
     * @return the order value (default: 0)
     */
    default int getOrder() {
        return 0;
    }

    /**
     * Returns whether this interceptor should be applied to the given request.
     * 
     * <p>This allows for conditional interceptor application based on service name,
     * endpoint, client type, or other request characteristics.
     *
     * @param request the interceptor request
     * @return true if this interceptor should be applied, false otherwise
     */
    default boolean shouldIntercept(InterceptorRequest request) {
        return true;
    }

    /**
     * Called when the interceptor is registered with a client.
     * Can be used for initialization or validation.
     *
     * @param clientType the type of client this interceptor is registered with
     */
    default void onRegistration(String clientType) {
        // Default implementation does nothing
    }

    /**
     * Called when the client is shutting down.
     * Can be used for cleanup or resource release.
     */
    default void onShutdown() {
        // Default implementation does nothing
    }
}

// Interfaces moved to separate files:
// - InterceptorRequest.java
// - InterceptorResponse.java
// - InterceptorChain.java
