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

package org.fireflyframework.security.core.cache;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

class CaffeineIntrospectionCacheTest {

    private final CaffeineIntrospectionCache cache = new CaffeineIntrospectionCache(100);
    private final SecurityPrincipal principal = SecurityPrincipal.builder().subject("u1").build();

    @Test
    void getMissReturnsEmpty() {
        StepVerifier.create(cache.get("missing")).verifyComplete();
    }

    @Test
    void putThenGetReturnsPrincipal() {
        cache.put("k1", principal, Duration.ofMinutes(5)).block();
        StepVerifier.create(cache.get("k1"))
                .expectNextMatches(p -> p.subject().equals("u1"))
                .verifyComplete();
    }

    @Test
    void evictRemovesEntry() {
        cache.put("k2", principal, Duration.ofMinutes(5)).block();
        cache.evict("k2").block();
        StepVerifier.create(cache.get("k2")).verifyComplete();
    }

    @Test
    void zeroTtlEntryExpiresImmediately() {
        cache.put("k3", principal, Duration.ZERO).block();
        StepVerifier.create(cache.get("k3")).verifyComplete();
    }
}
