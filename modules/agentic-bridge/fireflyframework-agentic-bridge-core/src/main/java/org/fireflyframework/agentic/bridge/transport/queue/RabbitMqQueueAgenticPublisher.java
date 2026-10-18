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
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException;
import org.fireflyframework.agentic.bridge.exception.AgenticBridgeException.AgenticErrorContext;
import org.fireflyframework.agentic.bridge.exception.AgenticTransportException;
import org.fireflyframework.agentic.bridge.health.AgenticHealth;
import org.fireflyframework.agentic.bridge.observability.AgenticMetrics;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive RabbitMQ publisher backed by {@code reactor-rabbitmq}. Publishes
 * the encoded {@link QueueInvocation} to the configured exchange + routing
 * key, propagating any caller-supplied headers.
 */
public final class RabbitMqQueueAgenticPublisher implements QueueAgenticPublisher {

    private final String name;
    private final String exchange;
    private final String defaultRoutingKey;
    private final Sender sender;
    private final ObjectMapper mapper;
    private final AgenticMetrics metrics;
    private final AtomicReference<AgenticHealth.Status> lastStatus = new AtomicReference<>(AgenticHealth.Status.UNKNOWN);

    public RabbitMqQueueAgenticPublisher(String name,
                                         String exchange,
                                         String defaultRoutingKey,
                                         String url,
                                         ObjectMapper mapper,
                                         AgenticMetrics metrics) {
        this(name, exchange, defaultRoutingKey, defaultSender(url), mapper, metrics);
    }

    public RabbitMqQueueAgenticPublisher(String name,
                                         String exchange,
                                         String defaultRoutingKey,
                                         Sender sender,
                                         ObjectMapper mapper,
                                         AgenticMetrics metrics) {
        this.name = Objects.requireNonNull(name, "name");
        this.exchange = Objects.requireNonNullElse(exchange, "");
        this.defaultRoutingKey = Objects.requireNonNullElse(defaultRoutingKey, "");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.metrics = Objects.requireNonNullElse(metrics, AgenticMetrics.NOOP);
    }

    private static Sender defaultSender(String url) {
        ConnectionFactory cf = new ConnectionFactory();
        try {
            cf.setUri(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RabbitMQ URL: " + url, e);
        }
        return RabbitFlux.createSender(new SenderOptions().connectionFactory(cf));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String transport() {
        return "rabbitmq";
    }

    @Override
    public Mono<Void> publish(QueueInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        long start = System.nanoTime();
        byte[] body = QueueMessageEnvelope.encode(invocation, mapper);
        Map<String, Object> headers = new HashMap<>(invocation.headers());
        headers.put("agent", invocation.agentName());

        AMQP.BasicProperties.Builder propsBuilder = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .deliveryMode(2)
                .headers(headers);
        if (invocation.replyTo() != null) {
            propsBuilder.replyTo(invocation.replyTo());
        }

        String routingKey = invocation.routingKey() != null ? invocation.routingKey() : defaultRoutingKey;
        OutboundMessage message = new OutboundMessage(exchange, routingKey, propsBuilder.build(), body);

        return sender.send(Mono.just(message))
                .doOnSuccess(v -> {
                    lastStatus.set(AgenticHealth.Status.UP);
                    metrics.recordPublish(name, "rabbitmq", "success", Duration.ofNanos(System.nanoTime() - start));
                })
                .doOnError(err -> {
                    lastStatus.set(AgenticHealth.Status.DEGRADED);
                    metrics.recordPublish(name, "rabbitmq", "error", Duration.ofNanos(System.nanoTime() - start));
                })
                .onErrorMap(err -> err instanceof AgenticBridgeException
                        ? err
                        : new AgenticTransportException("RabbitMQ publish failed for exchange='" + exchange + "', routingKey='" + routingKey + "'",
                            AgenticErrorContext.builder().transport("rabbitmq").attribute("exchange", exchange).attribute("routingKey", routingKey).build(), err));
    }

    @Override
    public Mono<AgenticHealth> health() {
        Map<String, Object> details = Map.of("exchange", exchange, "publisher", name);
        return Mono.fromSupplier(() -> {
            AgenticHealth.Status status = lastStatus.get();
            return new AgenticHealth(status, "RabbitMQ publisher status: " + status, java.time.Instant.now(), null, details);
        });
    }

    @Override
    public void close() {
        sender.close();
    }
}
