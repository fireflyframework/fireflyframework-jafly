/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.transport.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive Redis Pub/Sub publisher backed by Lettuce. Wraps the encoded
 * {@link QueueInvocation} into a JSON envelope so the agentic consumer can
 * recover trace headers (matches its existing
 * {@code body/headers} convention).
 */
public final class RedisQueueAgenticPublisher implements QueueAgenticPublisher {

    private final String name;
    private final String channel;
    private final RedisClient client;
    private final StatefulRedisConnection<String, byte[]> connection;
    private final RedisReactiveCommands<String, byte[]> commands;
    private final ObjectMapper mapper;
    private final AgenticMetrics metrics;
    private final AtomicReference<AgenticHealth.Status> lastStatus = new AtomicReference<>(AgenticHealth.Status.UNKNOWN);

    public RedisQueueAgenticPublisher(String name,
                                      String channel,
                                      String url,
                                      ObjectMapper mapper,
                                      AgenticMetrics metrics) {
        this.name = Objects.requireNonNull(name, "name");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.metrics = Objects.requireNonNullElse(metrics, AgenticMetrics.NOOP);
        this.client = RedisClient.create(url);
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        this.connection = client.connect(codec);
        this.commands = connection.reactive();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String transport() {
        return "redis";
    }

    @Override
    public Mono<Void> publish(QueueInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        long start = System.nanoTime();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("agent", invocation.agentName());
        envelope.put("body", new String(QueueMessageEnvelope.encode(invocation, mapper)));
        if (!invocation.headers().isEmpty()) envelope.put("headers", invocation.headers());
        if (invocation.routingKey() != null) envelope.put("routing_key", invocation.routingKey());
        if (invocation.replyTo() != null) envelope.put("reply_to", invocation.replyTo());

        byte[] payload;
        try {
            payload = mapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            return Mono.error(new AgenticTransportException("Failed to encode Redis envelope",
                    AgenticErrorContext.builder().transport("redis").attribute("channel", channel).build(), e));
        }

        return commands.publish(channel, payload)
                .doOnSuccess(count -> {
                    lastStatus.set(AgenticHealth.Status.UP);
                    metrics.recordPublish(name, "redis", "success", Duration.ofNanos(System.nanoTime() - start));
                })
                .doOnError(err -> {
                    lastStatus.set(AgenticHealth.Status.DEGRADED);
                    metrics.recordPublish(name, "redis", "error", Duration.ofNanos(System.nanoTime() - start));
                })
                .onErrorMap(err -> err instanceof AgenticBridgeException
                        ? err
                        : new AgenticTransportException("Redis publish to channel '" + channel + "' failed",
                            AgenticErrorContext.builder().transport("redis").attribute("channel", channel).build(), err))
                .then();
    }

    @Override
    public Mono<AgenticHealth> health() {
        Map<String, Object> details = Map.of("channel", channel, "publisher", name);
        return Mono.fromSupplier(() -> {
            AgenticHealth.Status status = lastStatus.get();
            return new AgenticHealth(status, "Redis publisher status: " + status, java.time.Instant.now(), null, details);
        });
    }

    @Override
    public void close() {
        try { connection.close(); } catch (Exception ignored) {}
        try { client.shutdown(); } catch (Exception ignored) {}
    }
}
