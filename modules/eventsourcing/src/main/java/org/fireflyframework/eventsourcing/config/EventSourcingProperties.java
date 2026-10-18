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

package org.fireflyframework.eventsourcing.config;

import org.fireflyframework.eda.annotation.PublisherType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for the Event Sourcing library.
 * <p>
 * These properties control various aspects of the event sourcing system:
 * - Event store configuration
 * - Snapshot configuration
 * - Event publishing settings
 * - Performance tuning parameters
 * <p>
 * Properties can be configured in application.yml or application.properties:
 * <pre>
 * firefly:
 *   eventsourcing:
 *     enabled: true
 *     store:
 *       type: r2dbc
 *       batch-size: 100
 *     snapshot:
 *       enabled: true
 *       threshold: 50
 *     publisher:
 *       enabled: true
 *       type: KAFKA
 * </pre>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "firefly.eventsourcing")
public class EventSourcingProperties {

    /**
     * Whether event sourcing is enabled.
     */
    private boolean enabled = true;

    /**
     * Event store configuration.
     */
    private EventStore store = new EventStore();

    /**
     * Snapshot configuration.
     */
    private Snapshot snapshot = new Snapshot();

    /**
     * Event publisher configuration.
     */
    private Publisher publisher = new Publisher();

    /**
     * Performance tuning configuration.
     */
    private Performance performance = new Performance();

    @Data
    public static class EventStore {
        /**
         * Type of event store (r2dbc, mongodb, etc.).
         */
        private String type = "r2dbc";

        /**
         * Batch size for event operations.
         */
        private int batchSize = 100;

        /**
         * Connection timeout.
         */
        private Duration connectionTimeout = Duration.ofSeconds(30);

        /**
         * Query timeout.
         */
        private Duration queryTimeout = Duration.ofSeconds(30);

        /**
         * Whether to validate event schemas.
         */
        private boolean validateSchemas = true;

        /**
         * Maximum number of events to load at once.
         */
        private int maxEventsPerLoad = 1000;

        /**
         * Event store specific properties.
         */
        private Map<String, Object> properties = Map.of();
    }

    @Data
    public static class Snapshot {
        /**
         * Whether snapshotting is enabled.
         */
        private boolean enabled = true;

        /**
         * Event count threshold for creating snapshots.
         * A snapshot will be created after this many events.
         */
        private int threshold = 50;

        /**
         * How often to check for snapshot opportunities.
         */
        private Duration checkInterval = Duration.ofMinutes(5);

        /**
         * Number of snapshots to keep per aggregate.
         */
        private int keepCount = 3;

        /**
         * Maximum age of snapshots before cleanup.
         */
        private Duration maxAge = Duration.ofDays(30);

        /**
         * Snapshot store type (same as event store, cache, etc.).
         */
        private String storeType = "same";
    }

    @Data
    public static class Publisher {
        /**
         * Whether event publishing is enabled.
         */
        private boolean enabled = true;

        /**
         * Type of publisher to use.
         */
        private PublisherType type = PublisherType.AUTO;

        /**
         * Destination prefix for published events.
         */
        private String destinationPrefix = "events";

        /**
         * Custom destination mappings for specific event types.
         */
        private Map<String, String> destinationMappings = Map.of();

        /**
         * Whether to publish events asynchronously.
         */
        private boolean async = true;

        /**
         * Batch size for event publishing.
         */
        private int batchSize = 10;

        /**
         * Timeout for publishing operations.
         */
        private Duration publishTimeout = Duration.ofSeconds(10);

        /**
         * Whether to continue on publish failures.
         */
        private boolean continueOnFailure = true;

        /**
         * Retry configuration for failed publishes.
         */
        private Retry retry = new Retry();

        @Data
        public static class Retry {
            /**
             * Whether retry is enabled.
             */
            private boolean enabled = true;

            /**
             * Maximum number of retry attempts.
             */
            private int maxAttempts = 3;

            /**
             * Initial delay between retries.
             */
            private Duration initialDelay = Duration.ofSeconds(1);

            /**
             * Maximum delay between retries.
             */
            private Duration maxDelay = Duration.ofSeconds(10);

            /**
             * Backoff multiplier.
             */
            private double backoffMultiplier = 2.0;
        }
    }

    @Data
    public static class Performance {
        /**
         * Thread pool size for parallel operations.
         */
        private int threadPoolSize = Runtime.getRuntime().availableProcessors();

        /**
         * Buffer size for reactive streams.
         */
        private int bufferSize = 1000;

        /**
         * Whether to enable metrics collection.
         */
        private boolean metricsEnabled = true;

        /**
         * Whether to enable health checks.
         */
        private boolean healthChecksEnabled = true;

        /**
         * Interval for collecting statistics.
         */
        private Duration statisticsInterval = Duration.ofMinutes(1);

        /**
         * Whether to enable distributed tracing.
         */
        private boolean tracingEnabled = true;

        /**
         * Circuit breaker configuration.
         */
        private CircuitBreaker circuitBreaker = new CircuitBreaker();

        @Data
        public static class CircuitBreaker {
            /**
             * Whether circuit breaker is enabled.
             */
            private boolean enabled = false;

            /**
             * Failure rate threshold (percentage).
             */
            private float failureRateThreshold = 50.0f;

            /**
             * Minimum number of calls before evaluating failure rate.
             */
            private int minimumNumberOfCalls = 10;

            /**
             * Time window for failure rate calculation.
             */
            private Duration slidingWindowSize = Duration.ofSeconds(60);

            /**
             * Wait duration in open state.
             */
            private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        }
    }
}