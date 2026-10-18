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

package org.fireflyframework.webhooks.core.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Health indicator for Redis connectivity.
 * <p>
 * Performs actual cache read/write test to verify Redis is working correctly.
 * This is more reliable than just checking if the connection exists.
 */
@Component("redisConnectivity")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(CacheManager.class)
@ConditionalOnProperty(name = "firefly.cache.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConnectivityHealthIndicator implements HealthIndicator {

    private final CacheManager cacheManager;
    private static final String HEALTH_CHECK_CACHE = "health-check";
    private static final String HEALTH_CHECK_KEY = "health:check:";

    @Override
    public Health health() {
        try {
            // Get or create health check cache
            var cache = cacheManager.getCache(HEALTH_CHECK_CACHE);
            if (cache == null) {
                return Health.down()
                        .withDetail("error", "Health check cache not available")
                        .withDetail("status", "Disconnected")
                        .build();
            }

            // Test 1: Write test
            String testKey = HEALTH_CHECK_KEY + UUID.randomUUID();
            String testValue = "health-check-" + Instant.now().toEpochMilli();

            cache.put(testKey, testValue);

            // Test 2: Read test
            String readValue = cache.get(testKey, String.class);
            boolean readSuccess = testValue.equals(readValue);

            // Test 3: Cleanup
            cache.evict(testKey);

            if (readSuccess) {
                return Health.up()
                        .withDetail("readWrite", "OK")
                        .withDetail("cacheType", cache.getClass().getSimpleName())
                        .withDetail("status", "Connected")
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Read/write test failed")
                        .withDetail("expected", testValue)
                        .withDetail("actual", readValue)
                        .withDetail("status", "Degraded")
                        .build();
            }

        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("errorType", e.getClass().getSimpleName())
                    .withDetail("status", "Disconnected")
                    .build();
        }
    }
}

