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

package org.fireflyframework.cache.observability;

import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.observability.health.FireflyHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Health indicator that pings the cache adapter with a synthetic read/write/delete
 * cycle and reports the round-trip latency.
 * <p>
 * Marks the cache as DOWN when the probe fails or exceeds {@code 1s}.
 */
public class CacheHealthIndicator extends FireflyHealthIndicator {

    private static final Duration LATENCY_THRESHOLD = Duration.ofSeconds(1);
    private static final String HEALTH_KEY_PREFIX = "firefly.cache.health.";

    private final CacheAdapter cacheAdapter;
    private final String adapterName;

    public CacheHealthIndicator(CacheAdapter cacheAdapter, String adapterName) {
        super("cache");
        this.cacheAdapter = cacheAdapter;
        this.adapterName = adapterName;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String probeKey = HEALTH_KEY_PREFIX + UUID.randomUUID();
        String probeValue = "ok";

        try {
            Instant start = Instant.now();
            cacheAdapter.put(probeKey, probeValue, Duration.ofSeconds(5))
                    .then(cacheAdapter.get(probeKey, String.class))
                    .then(cacheAdapter.evict(probeKey))
                    .block(Duration.ofSeconds(2));
            Duration elapsed = Duration.between(start, Instant.now());

            builder.up()
                    .withDetail("adapter", adapterName)
                    .withDetail("probe.latency.ms", elapsed.toMillis());

            if (elapsed.compareTo(LATENCY_THRESHOLD) > 0) {
                builder.status("DEGRADED")
                        .withDetail("threshold.ms", LATENCY_THRESHOLD.toMillis());
            }
        } catch (Exception e) {
            builder.down(e)
                    .withDetail("adapter", adapterName);
        }
    }
}
