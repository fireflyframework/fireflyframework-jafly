/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.fireflyframework.agentic.bridge.AgenticClientBuilder;
import org.fireflyframework.agentic.bridge.auth.AuthStrategy;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import org.fireflyframework.agentic.bridge.transport.queue.KafkaQueueAgenticPublisher;
import org.fireflyframework.agentic.bridge.transport.queue.QueueAgenticPublisher;
import org.fireflyframework.agentic.bridge.transport.queue.RabbitMqQueueAgenticPublisher;
import org.fireflyframework.agentic.bridge.transport.queue.RedisQueueAgenticPublisher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory that materialises framework objects from
 * {@link AgenticBridgeProperties}. Kept separate from the auto-configuration
 * class so it can be unit-tested without booting Spring.
 */
public final class AgenticBridgeFactory {

    private AgenticBridgeFactory() {
    }

    public static AgenticClient buildClient(AgenticBridgeProperties.Client cfg,
                                            ObjectMapper objectMapper,
                                            MeterRegistry meterRegistry) {
        Objects.requireNonNull(cfg, "client config");
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            throw new IllegalStateException("firefly.agentic-bridge.primary.base-url must be set");
        }
        AgenticClientBuilder builder = AgenticClient.builder(cfg.getName())
                .baseUrl(cfg.getBaseUrl())
                .connectTimeout(cfg.getConnectTimeout())
                .responseTimeout(cfg.getResponseTimeout())
                .writeTimeout(cfg.getWriteTimeout())
                .compression(cfg.isCompression())
                .followRedirects(cfg.isFollowRedirects())
                .requireTls(cfg.isRequireTls())
                .userAgent(cfg.getUserAgent())
                .maxInMemorySize(cfg.getMaxInMemorySize())
                .auth(authStrategy(cfg.getAuth()))
                .retry(r -> r
                        .enabled(cfg.getRetry().isEnabled())
                        .maxAttempts(cfg.getRetry().getMaxAttempts())
                        .initialBackoff(cfg.getRetry().getInitialBackoff())
                        .maxBackoff(cfg.getRetry().getMaxBackoff())
                        .jitter(cfg.getRetry().isJitter()))
                .circuitBreaker(cb -> cb
                        .enabled(cfg.getCircuitBreaker().isEnabled())
                        .failureRateThreshold(cfg.getCircuitBreaker().getFailureRateThreshold())
                        .slidingWindowSize(cfg.getCircuitBreaker().getSlidingWindowSize())
                        .minimumNumberOfCalls(cfg.getCircuitBreaker().getMinimumNumberOfCalls())
                        .waitDurationInOpenState(cfg.getCircuitBreaker().getWaitDurationInOpenState())
                        .slowCallDurationThreshold(cfg.getCircuitBreaker().getSlowCallDurationThreshold()));
        cfg.getDefaultHeaders().forEach(builder::defaultHeader);
        if (objectMapper != null) builder.objectMapper(objectMapper);
        if (meterRegistry != null) builder.meterRegistry(meterRegistry);
        return builder.build();
    }

    public static AuthStrategy authStrategy(AgenticBridgeProperties.Auth cfg) {
        if (cfg == null || cfg.getType() == null || cfg.getType() == AgenticBridgeProperties.Auth.Type.NONE) {
            return AuthStrategy.none();
        }
        return switch (cfg.getType()) {
            case API_KEY -> AuthStrategy.apiKey(cfg.getApiKeyHeader(), cfg::getApiKey);
            case BEARER -> AuthStrategy.bearer(cfg::getToken);
            default -> AuthStrategy.none();
        };
    }

    public static Map<String, QueueAgenticPublisher> buildPublishers(AgenticBridgeProperties properties,
                                                                     ObjectMapper objectMapper,
                                                                     MeterRegistry meterRegistry) {
        Map<String, QueueAgenticPublisher> publishers = new LinkedHashMap<>();
        properties.getPublishers().forEach((name, cfg) -> {
            QueueAgenticPublisher publisher = buildPublisher(name, cfg, objectMapper, meterRegistry);
            if (publisher != null) {
                publishers.put(name, publisher);
            }
        });
        return publishers;
    }

    public static QueueAgenticPublisher buildPublisher(String name,
                                                       AgenticBridgeProperties.Publisher cfg,
                                                       ObjectMapper objectMapper,
                                                       MeterRegistry meterRegistry) {
        AgenticMetrics metrics = meterRegistry == null ? AgenticMetrics.NOOP : new AgenticMetrics(meterRegistry, name);
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
        if (cfg == null || cfg.getType() == null) {
            throw new IllegalArgumentException("Publisher '" + name + "' is missing required 'type'");
        }
        return switch (cfg.getType()) {
            case KAFKA -> {
                if (!classpathHas("org.apache.kafka.clients.producer.ProducerConfig")
                        || !classpathHas("reactor.kafka.sender.KafkaSender")) {
                    throw new IllegalStateException("Publisher '" + name + "' is configured for kafka but reactor-kafka is not on the classpath");
                }
                yield new KafkaQueueAgenticPublisher(name, cfg.getTopic(), cfg.getBootstrapServers(), mapper, metrics);
            }
            case RABBITMQ -> {
                if (!classpathHas("reactor.rabbitmq.Sender")) {
                    throw new IllegalStateException("Publisher '" + name + "' is configured for rabbitmq but reactor-rabbitmq is not on the classpath");
                }
                yield new RabbitMqQueueAgenticPublisher(name, cfg.getExchange(), cfg.getRoutingKey(), cfg.getUrl(), mapper, metrics);
            }
            case REDIS -> {
                if (!classpathHas("io.lettuce.core.RedisClient")) {
                    throw new IllegalStateException("Publisher '" + name + "' is configured for redis but lettuce-core is not on the classpath");
                }
                yield new RedisQueueAgenticPublisher(name, cfg.getChannel(), cfg.getUrl(), mapper, metrics);
            }
        };
    }

    private static boolean classpathHas(String className) {
        try {
            Class.forName(className, false, AgenticBridgeFactory.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
