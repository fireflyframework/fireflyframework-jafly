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
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive Kafka publisher backed by {@code reactor-kafka}. Publishes the
 * {@link QueueMessageEnvelope}-encoded JSON payload to the configured topic;
 * propagates W3C trace context via {@code traceparent}/{@code tracestate}
 * headers carried in the {@link QueueInvocation}.
 */
public final class KafkaQueueAgenticPublisher implements QueueAgenticPublisher {

    private final String name;
    private final String topic;
    private final KafkaSender<String, byte[]> sender;
    private final ObjectMapper mapper;
    private final AgenticMetrics metrics;
    private final AtomicReference<AgenticHealth.Status> lastStatus = new AtomicReference<>(AgenticHealth.Status.UNKNOWN);

    public KafkaQueueAgenticPublisher(String name,
                                      String topic,
                                      String bootstrapServers,
                                      ObjectMapper mapper,
                                      AgenticMetrics metrics) {
        this(name, topic, defaultSender(bootstrapServers, name), mapper, metrics);
    }

    public KafkaQueueAgenticPublisher(String name,
                                      String topic,
                                      KafkaSender<String, byte[]> sender,
                                      ObjectMapper mapper,
                                      AgenticMetrics metrics) {
        this.name = Objects.requireNonNull(name, "name");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.metrics = Objects.requireNonNullElse(metrics, AgenticMetrics.NOOP);
    }

    private static KafkaSender<String, byte[]> defaultSender(String bootstrapServers, String name) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "firefly-agentic-bridge-" + name);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return KafkaSender.create(SenderOptions.create(props));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String transport() {
        return "kafka";
    }

    @Override
    public Mono<Void> publish(QueueInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        long start = System.nanoTime();
        byte[] body = QueueMessageEnvelope.encode(invocation, mapper);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, invocation.routingKey(), body);
        invocation.headers().forEach((k, v) ->
                record.headers().add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8))));
        record.headers().add(new RecordHeader("agent", invocation.agentName().getBytes(StandardCharsets.UTF_8)));

        return sender.send(Mono.just(SenderRecord.create(record, invocation.agentName())))
                .next()
                .doOnSuccess(result -> {
                    lastStatus.set(AgenticHealth.Status.UP);
                    metrics.recordPublish(name, "kafka", "success", Duration.ofNanos(System.nanoTime() - start));
                })
                .doOnError(err -> {
                    lastStatus.set(AgenticHealth.Status.DEGRADED);
                    metrics.recordPublish(name, "kafka", "error", Duration.ofNanos(System.nanoTime() - start));
                })
                .onErrorMap(err -> err instanceof AgenticBridgeException
                        ? err
                        : new AgenticTransportException("Kafka publish failed for topic " + topic,
                            AgenticErrorContext.builder().transport("kafka").attribute("topic", topic).build(), err))
                .then();
    }

    @Override
    public Mono<AgenticHealth> health() {
        Map<String, Object> details = Map.of("topic", topic, "publisher", name);
        return Mono.fromSupplier(() -> {
            AgenticHealth.Status status = lastStatus.get();
            return new AgenticHealth(status, "Kafka publisher status: " + status, java.time.Instant.now(), null, details);
        });
    }

    @Override
    public void close() {
        sender.close();
    }
}
