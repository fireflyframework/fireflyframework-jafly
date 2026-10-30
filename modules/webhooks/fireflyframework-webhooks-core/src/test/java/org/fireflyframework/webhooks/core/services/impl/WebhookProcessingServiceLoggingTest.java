/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.webhooks.core.services.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.core.mappers.WebhookEventMapper;
import org.fireflyframework.webhooks.core.services.WebhookBatchingService;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookProcessingServiceLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(WebhookProcessingServiceImpl.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level oldLevel;
    private boolean oldAdditive;
    private Map<String, String> oldMdc;

    @BeforeEach void capture() {
        oldLevel = logger.getLevel(); oldAdditive = logger.isAdditive(); oldMdc = MDC.getCopyOfContextMap();
        logger.setLevel(Level.DEBUG); logger.setAdditive(false); events.start(); logger.addAppender(events); MDC.clear();
    }
    @AfterEach void restore() {
        logger.detachAppender(events); events.stop(); logger.setLevel(oldLevel); logger.setAdditive(oldAdditive);
        MDC.clear(); if (oldMdc != null) MDC.setContextMap(oldMdc);
    }

    @Test void providerDiagnosticsEscapeLineSeparatorsWhileTransportKeepsOriginalValues() {
        String provider = "sandbox\r\nprovider\u0085\u2028\u2029";
        var f = new Fixture(provider); var response = f.process(Context.empty());
        f.assertOriginalTransport(response, provider);
        assertSingleLine();
        assertEquals("Processing webhook from provider: sandbox\\r\\nprovider\\u0085\\u2028\\u2029", events.list.getFirst().getFormattedMessage());
        assertEquals(Level.INFO, events.list.getFirst().getLevel());
        assertEquals(Level.INFO, events.list.getLast().getLevel());
    }
    @Test void configuredDestinationDiagnosticsDoNotRewritePublishedDestinationOrMetadata() {
        var f = new Fixture("sandbox"); String destination = "owned\nqueue";
        ReflectionTestUtils.setField(f.service, "customDestination", destination);
        var response = f.process(Context.empty());
        assertEquals(destination, f.destination); assertEquals(destination, response.getMetadata().get("destination"));
        assertTrue(events.list.stream().anyMatch(e -> e.getFormattedMessage().equals("Using custom destination: owned\\nqueue")));
        assertSingleLine();
    }
    @Test void reactiveTracingDiagnosticsEscapeLabelsWhileHeadersRemainUnchanged() {
        var f = new Fixture("sandbox"); String trace = "trace\nlabel", span = "span\rlabel", request = "request\u2028label";
        f.process(Context.of("traceId", trace, "spanId", span, "requestId", request));
        assertEquals(trace, f.headers.get("X-B3-TraceId")); assertEquals(span, f.headers.get("X-B3-SpanId")); assertEquals(request, f.headers.get("X-Request-ID"));
        assertSingleLine();
        assertTrue(events.list.stream().anyMatch(e -> e.getFormattedMessage().equals("Propagating traceId to Kafka: trace\\nlabel")));
    }
    @Test void mdcFallbackPreservesOriginalTracingValuesAndEscapesOnlyDiagnostics() {
        var f = new Fixture("sandbox"); MDC.put("traceId", "trace\nlabel"); MDC.put("spanId", "span\u0085label"); MDC.put("requestId", "request\u2029label");
        f.process(Context.empty()); assertEquals("trace\nlabel", f.headers.get("X-B3-TraceId")); assertEquals("span\u0085label", f.headers.get("X-B3-SpanId")); assertEquals("request\u2029label", f.headers.get("X-Request-ID")); assertSingleLine();
    }
    @Test void errorResponseAndThrowableKeepOriginalMessageWhileProviderFieldIsEscaped() {
        var f = new Fixture("sandbox\nprovider"); RuntimeException failure = new RuntimeException("sandbox\nfailure"); f.failure = failure;
        var response = f.process(Context.empty()); assertEquals("ERROR", response.getStatus()); assertEquals("Error processing webhook: sandbox\nfailure", response.getMessage()); assertSame(f.dto.getPayload(), response.getReceivedPayload());
        var error = events.list.getLast(); assertEquals(Level.ERROR, error.getLevel()); assertEquals("Error processing webhook from provider: sandbox\\nprovider", error.getFormattedMessage()); assertSame(failure, ((ThrowableProxy) error.getThrowableProxy()).getThrowable()); assertSingleLine();
    }
    @Test void batchingRetainsRawDestinationHeadersAndDomainEvent() {
        var f = new Fixture("sandbox\nprovider"); var batching = mock(WebhookBatchingService.class);
        when(batching.addToBatch(any(), anyString(), anyMap())).thenAnswer(inv -> { f.retain(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)); return Mono.empty(); });
        ReflectionTestUtils.setField(f.service, "batchingEnabled", true); ReflectionTestUtils.setField(f.service, "batchingService", batching);
        var response = f.process(Context.empty()); f.assertOriginalTransport(response, "sandbox\nprovider"); assertSingleLine();
        assertTrue(events.list.stream().anyMatch(e -> e.getFormattedMessage().contains("to batch for destination: sandbox\\nprovider")));
    }
    @Test void ordinaryDiagnosticsKeepTextLevelsAndTransportValues() {
        var f = new Fixture("sandbox"); var response = f.process(Context.empty()); f.assertOriginalTransport(response, "sandbox");
        assertEquals("Processing webhook from provider: sandbox", events.list.getFirst().getFormattedMessage());
        assertEquals("Successfully processed webhook " + f.dto.getEventId() + " from provider: sandbox", events.list.getLast().getFormattedMessage());
        assertTrue(events.list.stream().allMatch(e -> e.getThrowableProxy() == null));
    }
    @Test void nullProviderRetainsExistingNullDiagnosticAndResponseBehavior() {
        var f = new Fixture(null); var response = f.process(Context.empty()); f.assertOriginalTransport(response, null);
        assertEquals("Processing webhook from provider: null", events.list.getFirst().getFormattedMessage()); assertEquals("null", f.destination);
    }
    @Test void disabledLoggingDoesNotChangePublisherInputsOrResponse() {
        logger.setLevel(Level.OFF); var f = new Fixture("sandbox\nprovider"); var response = f.process(Context.of("traceId", "trace\nlabel"));
        f.assertOriginalTransport(response, "sandbox\nprovider"); assertEquals("trace\nlabel", f.headers.get("X-B3-TraceId")); assertTrue(events.list.isEmpty());
    }
    private void assertSingleLine() {
        for (var event : events.list) for (String separator : List.of("\r", "\n", "\u0085", "\u2028", "\u2029")) assertFalse(event.getFormattedMessage().contains(separator));
    }
    private static final class Fixture {
        final WebhookEventDTO dto;
        final WebhookReceivedEvent domain;
        final WebhookProcessingServiceImpl service;
        WebhookReceivedEvent published;
        String destination;
        Map<String, Object> headers;
        RuntimeException failure;
        Fixture(String provider) {
            var payload = JsonNodeFactory.instance.objectNode().put("kind", "sandbox");
            dto = WebhookEventDTO.builder().eventId(UUID.randomUUID()).providerName(provider).receivedAt(Instant.parse("2026-10-26T00:00:00Z")).payload(payload).headers(Map.of("sandbox", "value")).httpMethod("POST").sourceIp("127.0.0.1").build();
            domain = WebhookReceivedEvent.builder().eventId(dto.getEventId()).providerName(provider).receivedAt(dto.getReceivedAt()).payload(payload).build();
            var mapper = mock(WebhookEventMapper.class); when(mapper.toDomainEvent(dto)).thenReturn(domain);
            var publisher = mock(EventPublisher.class); var factory = mock(EventPublisherFactory.class); when(factory.getDefaultPublisher()).thenReturn(publisher);
            when(publisher.publish(any(), anyString(), anyMap())).thenAnswer(inv -> { retain(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)); return failure == null ? Mono.empty() : Mono.error(failure); });
            service = new WebhookProcessingServiceImpl(factory, mapper); ReflectionTestUtils.setField(service, "useProviderAsTopic", true);
        }
        void retain(WebhookReceivedEvent event, String topic, Map<String, Object> values) { published = event; destination = topic; headers = new HashMap<>(values); }
        WebhookResponseDTO process(Context context) { return service.processWebhook(dto).contextWrite(context).block(java.time.Duration.ofSeconds(2)); }
        void assertOriginalTransport(WebhookResponseDTO response, String provider) {
            assertEquals("ACCEPTED", response.getStatus()); assertSame(domain, published); assertSame(dto.getPayload(), response.getReceivedPayload()); assertEquals(provider, response.getProviderName()); assertEquals(provider, headers.get("provider")); assertEquals(dto.getEventId().toString(), headers.get("eventId")); assertEquals(destination, response.getMetadata().get("destination"));
        }
    }
}
