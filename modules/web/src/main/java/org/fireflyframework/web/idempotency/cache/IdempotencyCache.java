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


package org.fireflyframework.web.idempotency.cache;

import org.fireflyframework.web.idempotency.model.CachedResponse;
import reactor.core.publisher.Mono;

/**
 * Interface for idempotency caching operations.
 * This abstraction allows for different cache implementations (in-memory, Redis, etc.)
 * without creating direct dependencies on specific technologies.
 */
public interface IdempotencyCache {
    
    /**
     * Gets a cached response by its key.
     *
     * @param key the idempotency key
     * @return a Mono that emits the cached response if found, or empty if not found
     */
    Mono<CachedResponse> get(String key);
    
    /**
     * Puts a response in the cache with the specified key.
     *
     * @param key the idempotency key
     * @param response the response to cache
     * @return a Mono that completes when the operation is done
     */
    Mono<Void> put(String key, CachedResponse response);
}