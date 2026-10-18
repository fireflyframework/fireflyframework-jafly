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

import java.util.List;

/**
 * Represents the interceptor chain for continuing execution.
 */
public interface InterceptorChain {
    /**
     * Proceeds with the request to the next interceptor or the actual service call.
     *
     * @param request the request to proceed with
     * @return a Mono containing the response
     */
    Mono<InterceptorResponse> proceed(InterceptorRequest request);

    /**
     * Gets the remaining interceptors in the chain.
     */
    List<ServiceClientInterceptor> getRemainingInterceptors();

    /**
     * Gets the current interceptor index.
     */
    int getCurrentIndex();
}

