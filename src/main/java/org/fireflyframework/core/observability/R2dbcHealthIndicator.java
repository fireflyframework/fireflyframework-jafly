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

package org.fireflyframework.core.observability;

import io.r2dbc.spi.ConnectionFactory;
import org.fireflyframework.observability.health.FireflyHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Health indicator that probes the R2DBC {@link ConnectionFactory} with a lightweight
 * {@code SELECT 1} round-trip and reports latency.
 * <p>
 * Marks the database as DOWN when the probe times out or fails.
 */
public class R2dbcHealthIndicator extends FireflyHealthIndicator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration LATENCY_THRESHOLD = Duration.ofMillis(500);

    private final ConnectionFactory connectionFactory;

    public R2dbcHealthIndicator(ConnectionFactory connectionFactory) {
        super("r2dbc");
        this.connectionFactory = connectionFactory;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            Instant start = Instant.now();
            Long result = Mono.from(connectionFactory.create())
                    .flatMap(connection -> Mono.from(connection.createStatement("SELECT 1").execute())
                            .flatMap(r -> Mono.from(r.map((row, meta) -> row.get(0, Long.class))))
                            .doFinally(s -> Mono.from(connection.close()).subscribe()))
                    .block(PROBE_TIMEOUT);
            Duration elapsed = Duration.between(start, Instant.now());

            builder.up()
                    .withDetail("database", connectionFactory.getMetadata().getName())
                    .withDetail("probe.result", String.valueOf(result))
                    .withDetail("probe.latency.ms", elapsed.toMillis());

            if (elapsed.compareTo(LATENCY_THRESHOLD) > 0) {
                builder.status("DEGRADED")
                        .withDetail("threshold.ms", LATENCY_THRESHOLD.toMillis());
            }
        } catch (Exception e) {
            builder.down(e)
                    .withDetail("database", connectionFactory.getMetadata().getName());
        }
    }
}
