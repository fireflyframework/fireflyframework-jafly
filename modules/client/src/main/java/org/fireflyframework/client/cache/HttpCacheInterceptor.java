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

package org.fireflyframework.client.cache;

import org.fireflyframework.client.interceptor.InterceptorChain;
import org.fireflyframework.client.interceptor.InterceptorRequest;
import org.fireflyframework.client.interceptor.InterceptorResponse;
import org.fireflyframework.client.interceptor.ServiceClientInterceptor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * HTTP caching interceptor for ServiceClient operations.
 * 
 * <p>This interceptor implements HTTP caching with support for:
 * <ul>
 *   <li>ETag-based validation</li>
 *   <li>Cache-Control directives</li>
 *   <li>Conditional requests (If-None-Match, If-Modified-Since)</li>
 *   <li>TTL-based expiration</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
public class HttpCacheInterceptor implements ServiceClientInterceptor {

    private final HttpCacheManager cacheManager;
    private final HttpCacheConfig config;

    public HttpCacheInterceptor(HttpCacheManager cacheManager, HttpCacheConfig config) {
        this.cacheManager = cacheManager;
        this.config = config;
    }

    @Override
    public Mono<InterceptorResponse> intercept(InterceptorRequest request, InterceptorChain chain) {
        if (!config.isEnabled()) {
            return chain.proceed(request);
        }

        // Only cache GET requests if configured
        if (config.isCacheGetOnly() && !"GET".equalsIgnoreCase(request.getMethod())) {
            return chain.proceed(request);
        }

        // Check if service and endpoint should be cached
        if (!config.shouldCache(request.getServiceName()) || 
            !config.shouldCacheEndpoint(request.getEndpoint())) {
            return chain.proceed(request);
        }

        // Generate cache key
        String cacheKey = generateCacheKey(request);

        // Try to get from cache
        return cacheManager.getOrExecute(
            cacheKey,
            chain.proceed(request)
                .doOnSuccess(response -> {
                    // Extract ETag and Last-Modified from response
                    String etag = response.getHeaders().get("ETag");
                    String lastModified = response.getHeaders().get("Last-Modified");
                    
                    // Cache the response
                    cacheManager.put(cacheKey, response, etag, lastModified);
                })
        );
    }

    /**
     * Generates a cache key from the request.
     */
    private String generateCacheKey(InterceptorRequest request) {
        return String.format("%s:%s:%s",
            request.getServiceName(),
            request.getMethod(),
            request.getEndpoint()
        );
    }

    @Override
    public int getOrder() {
        return 20; // Execute early, but after chaos engineering
    }
}

