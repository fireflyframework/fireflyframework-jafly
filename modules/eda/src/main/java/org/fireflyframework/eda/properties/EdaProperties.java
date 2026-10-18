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

package org.fireflyframework.eda.properties;

import org.fireflyframework.eda.annotation.PublisherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the Event-Driven Architecture library.
 * <p>
 * This class centralizes all configuration for the EDA library, including
 * publisher settings, connection configurations, and platform-specific properties.
 */
@ConfigurationProperties(prefix = "firefly.eda")
@Validated
@Data
public class EdaProperties {

    /**
     * Whether the EDA library is enabled.
     */
    private boolean enabled = true;

    /**
     * Default publisher type to use when none is specified.
     */
    @NotNull(message = "Default publisher type cannot be null")
    private PublisherType defaultPublisherType = PublisherType.AUTO;

    /**
     * Default connection ID to use when none is specified.
     */
    @NotBlank(message = "Default connection ID cannot be blank")
    @Size(max = 100, message = "Default connection ID must not exceed 100 characters")
    private String defaultConnectionId = "default";

    /**
     * Default destination for events when none is specified.
     */
    private String defaultDestination = "events";

    /**
     * Default serialization format.
     */
    @NotNull(message = "Default serialization format cannot be null")
    private String defaultSerializationFormat = "json";

    /**
     * Default timeout for publish operations.
     */
    @NotNull(message = "Default timeout cannot be null")
    private Duration defaultTimeout = Duration.ofSeconds(30);

    /**
     * Whether to enable metrics collection.
     */
    private boolean metricsEnabled = true;

    /**
     * Whether to enable health checks.
     */
    private boolean healthEnabled = true;

    /**
     * Whether to enable tracing integration.
     */
    private boolean tracingEnabled = true;

    /**
     * Resilience configuration.
     */
    @Valid
    private final Resilience resilience = new Resilience();

    /**
     * Publisher configurations by type.
     */
    @Valid
    private final Publishers publishers = new Publishers();

    /**
     * Consumer configurations.
     */
    @Valid
    private final Consumer consumer = new Consumer();

    @Data
    public static class Resilience {
        /**
         * Whether resilience features are enabled.
         */
        private boolean enabled = true;

        /**
         * Circuit breaker configuration.
         */
        @Valid
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();

        /**
         * Retry configuration.
         */
        @Valid
        private final Retry retry = new Retry();

        /**
         * Rate limiter configuration.
         */
        @Valid
        private final RateLimiter rateLimiter = new RateLimiter();

        @Data
        public static class CircuitBreaker {
            private boolean enabled = true;
            private int failureRateThreshold = 50;
            private int slowCallRateThreshold = 50;
            private Duration slowCallDurationThreshold = Duration.ofSeconds(60);
            private int minimumNumberOfCalls = 10;
            private int slidingWindowSize = 10;
            private Duration waitDurationInOpenState = Duration.ofSeconds(60);
            private int permittedNumberOfCallsInHalfOpenState = 3;
        }

        @Data
        public static class Retry {
            private boolean enabled = true;
            private int maxAttempts = 3;
            private Duration waitDuration = Duration.ofMillis(500);
            private double exponentialBackoffMultiplier = 2.0;
        }

        @Data
        public static class RateLimiter {
            private boolean enabled = false;
            private int limitForPeriod = 100;
            private Duration limitRefreshPeriod = Duration.ofSeconds(1);
            private Duration timeoutDuration = Duration.ofSeconds(5);
        }
    }

    @Data
    public static class Publishers {
        /**
         * Whether event publishers are enabled globally.
         */
        private boolean enabled = false;

        /**
         * Application Event publisher configuration.
         */
        @Valid
        private final ApplicationEvent applicationEvent = new ApplicationEvent();

        /**
         * Kafka publisher configurations by connection ID.
         */
        @Valid
        private final Map<String, KafkaConfig> kafka = new HashMap<>();

        /**
         * RabbitMQ publisher configurations by connection ID.
         */
        @Valid
        private final Map<String, RabbitMqConfig> rabbitmq = new HashMap<>();

        /**
         * PostgreSQL publisher configurations by connection ID.
         */
        @Valid
        private final Map<String, PostgresConfig> postgres = new HashMap<>();

        // Initialize default connections
        public Publishers() {
            kafka.put("default", new KafkaConfig());
            rabbitmq.put("default", new RabbitMqConfig());
            postgres.put("default", new PostgresConfig());
        }

        @Data
        public static class ApplicationEvent {
            private boolean enabled = true;
            private String defaultDestination = "application-events";
        }

        @Data
        public static class KafkaConfig {
            private boolean enabled = false;
            private String bootstrapServers;
            private String defaultTopic = "events";
            private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
            private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";
            private Map<String, Object> properties = new HashMap<>();
        }

        @Data
        public static class RabbitMqConfig {
            private boolean enabled = false;
            private String host = "localhost";
            private int port = 5672;
            private String username = "guest";
            private String password = "guest";
            private String virtualHost = "/";
            private String defaultExchange = "events";
            private String defaultRoutingKey = "event";
            private Map<String, Object> properties = new HashMap<>();
        }

        @Data
        public static class PostgresConfig {
            /**
             * Whether this PostgreSQL publisher connection is enabled.
             */
            private boolean enabled = false;

            /**
             * PostgreSQL host name or IP.
             */
            private String host = "localhost";

            /**
             * PostgreSQL port (default 5432).
             */
            private int port = 5432;

            /**
             * Database name.
             */
            private String database;

            /**
             * Database username.
             */
            private String username;

            /**
             * Database password.
             */
            private String password;

            /**
             * Schema name where the outbox table lives.
             */
            private String schema = "public";

            /**
             * Outbox table name used to persist events.
             */
            private String outboxTable = "firefly_eda_outbox";

            /**
             * Default destination/channel when none is provided to publish().
             */
            private String defaultDestination = "events";

            /**
             * If true, the auto-configuration creates the outbox table and
             * NOTIFY trigger automatically at startup. Disable in environments
             * that prefer to manage schema externally (e.g., via Flyway).
             */
            private boolean autoCreateSchema = true;

            /**
             * Maximum size of the R2DBC connection pool used by the publisher.
             */
            private int maxPoolSize = 10;

            /**
             * Additional R2DBC connection properties.
             */
            private Map<String, Object> properties = new HashMap<>();
        }
    }

    @Data
    public static class Consumer {
        /**
         * Whether event consumers are enabled globally.
         */
        private boolean enabled = false;

        /**
         * Default consumer group ID.
         */
        private String groupId = "firefly-eda";

        /**
         * Consumer concurrency level.
         */
        @Min(value = 1, message = "Consumer concurrency must be at least 1")
        @Max(value = 100, message = "Consumer concurrency must not exceed 100")
        private int concurrency = 1;

        /**
         * Consumer retry configuration.
         */
        @Valid
        private final ConsumerRetry retry = new ConsumerRetry();

        /**
         * Application Event consumer configuration.
         */
        @Valid
        private final ApplicationEvent applicationEvent = new ApplicationEvent();

        /**
         * Kafka consumer configurations by connection ID.
         */
        @Valid
        private final Map<String, KafkaConfig> kafka = new HashMap<>();

        /**
         * RabbitMQ consumer configurations by connection ID.
         */
        @Valid
        private final Map<String, RabbitMqConfig> rabbitmq = new HashMap<>();

        /**
         * PostgreSQL consumer configurations by connection ID.
         */
        @Valid
        private final Map<String, PostgresConfig> postgres = new HashMap<>();

        /**
         * NOOP consumer configuration.
         */
        @Valid
        private final Noop noop = new Noop();

        // Initialize default connections
        public Consumer() {
            kafka.put("default", new KafkaConfig());
            rabbitmq.put("default", new RabbitMqConfig());
            postgres.put("default", new PostgresConfig());
        }

        @Data
        public static class ConsumerRetry {
            private boolean enabled = true;
            private int maxAttempts = 3;
            private Duration initialDelay = Duration.ofSeconds(1);
            private Duration maxDelay = Duration.ofMinutes(5);
            private double multiplier = 2.0;
        }

        @Data
        public static class ApplicationEvent {
            private boolean enabled = true;
        }

        @Data
        public static class KafkaConfig {
            private boolean enabled = false;
            private String bootstrapServers;
            private String topics = "events";
            private String autoOffsetReset = "earliest";
            private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
            private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
            private Map<String, Object> properties = new HashMap<>();
        }

        @Data
        public static class RabbitMqConfig {
            private boolean enabled = false;
            private String host = "localhost";
            private int port = 5672;
            private String username = "guest";
            private String password = "guest";
            private String virtualHost = "/";
            private String queues = "events-queue";
            private int concurrentConsumers = 1;
            private int maxConcurrentConsumers = 5;
            private int prefetchCount = 10;
            private Map<String, Object> properties = new HashMap<>();
        }

        @Data
        public static class PostgresConfig {
            /**
             * Whether this PostgreSQL consumer connection is enabled.
             */
            private boolean enabled = false;

            /**
             * PostgreSQL host name or IP.
             */
            private String host = "localhost";

            /**
             * PostgreSQL port (default 5432).
             */
            private int port = 5432;

            /**
             * Database name.
             */
            private String database;

            /**
             * Database username.
             */
            private String username;

            /**
             * Database password.
             */
            private String password;

            /**
             * Schema name where the outbox table lives.
             */
            private String schema = "public";

            /**
             * Outbox table name used to read events.
             */
            private String outboxTable = "firefly_eda_outbox";

            /**
             * Comma-separated list of channels/destinations to listen on when no
             * {@code @EventListener} annotations are discovered. Defaults to
             * the global default destination.
             */
            private String channels = "events";

            /**
             * Polling interval used as a fallback to LISTEN/NOTIFY in case
             * notifications are missed (server restart, dropped connection,
             * payload too large, etc.). Set to {@link Duration#ZERO} to
             * disable polling.
             */
            private Duration pollingInterval = Duration.ofSeconds(30);

            /**
             * Maximum number of attempts before an event is moved to dead
             * letter status in the outbox table.
             */
            @Min(value = 1, message = "Max attempts must be at least 1")
            private int maxAttempts = 3;

            /**
             * Maximum number of events fetched in a single poll cycle.
             */
            @Min(value = 1, message = "Batch size must be at least 1")
            private int batchSize = 50;

            /**
             * Maximum size of the R2DBC connection pool used by the consumer.
             * Note: LISTEN/NOTIFY uses one long-lived connection outside this
             * pool; this pool services SELECT/UPDATE queries on the outbox.
             */
            @Min(value = 1, message = "Max pool size must be at least 1")
            private int maxPoolSize = 5;

            /**
             * Additional R2DBC connection properties.
             */
            private Map<String, Object> properties = new HashMap<>();
        }

        @Data
        public static class Noop {
            private boolean enabled = false;
        }
    }

    /**
     * Gets the consumer configuration.
     *
     * @return the consumer configuration
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * Gets the configuration for a specific publisher connection.
     *
     * @param publisherType the publisher type
     * @param connectionId the connection ID
     * @return the configuration or null if not found
     */
    public Object getPublisherConfig(PublisherType publisherType, String connectionId) {
        String connId = connectionId != null ? connectionId : defaultConnectionId;

        return switch (publisherType) {
            case APPLICATION_EVENT -> publishers.getApplicationEvent();
            case KAFKA -> publishers.getKafka().get(connId);
            case RABBITMQ -> publishers.getRabbitmq().get(connId);
            case POSTGRES -> publishers.getPostgres().get(connId);
            default -> null;
        };
    }
}