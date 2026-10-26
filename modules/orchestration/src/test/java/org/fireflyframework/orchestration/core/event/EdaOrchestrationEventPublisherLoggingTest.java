/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.orchestration.core.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.eda.annotation.PublisherType;
import org.fireflyframework.eda.publisher.EventPublisher;
import org.fireflyframework.eda.publisher.EventPublisherFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EdaOrchestrationEventPublisherLoggingTest {
    private static final String BREAKS = "\r\n\u000b\f\u0085\u2028\u2029";
    private static final String VISIBLE = "\\r\\n\\u000b\\f\\u0085\\u2028\\u2029";
    private Logger logger;
    private Level level;
    private boolean additive;
    private ListAppender<ILoggingEvent> capture;

    @BeforeEach void capture() {
        logger = (Logger) LoggerFactory.getLogger(EdaOrchestrationEventPublisher.class);
        level = logger.getLevel(); additive = logger.isAdditive();
        logger.setLevel(Level.TRACE); logger.setAdditive(false);
        capture = new ListAppender<>(); capture.setContext(logger.getLoggerContext());
        capture.start(); logger.addAppender(capture);
    }

    @AfterEach void restore() {
        logger.detachAppender(capture); capture.stop();
        logger.setLevel(level); logger.setAdditive(additive);
    }

    private static final class RecordingPublisher implements EventPublisher {
        Object event;
        String destination;
        Map<String, Object> headers;
        RuntimeException failure;
        int calls;
        @Override public Mono<Void> publish(Object event, String destination, Map<String, Object> headers) {
            this.event = event; this.destination = destination; this.headers = headers; calls++;
            return failure == null ? Mono.empty() : Mono.error(failure);
        }
        @Override public boolean isAvailable() { return true; }
        @Override public PublisherType getPublisherType() { return null; }
    }

    private EventPublisherFactory factory(EventPublisher publisher) {
        return new EventPublisherFactory(List.of(), null, null) {
            @Override public EventPublisher getDefaultPublisher() { return publisher; }
        };
    }

    private OrchestrationEvent event(String text, String topic) {
        return new OrchestrationEvent(text, text, text, null, text, null,
                Map.of("data", "unchanged"), null, topic, text, text);
    }

    private ILoggingEvent logged(Level expectedLevel, Object... arguments) {
        assertEquals(1, capture.list.size());
        ILoggingEvent log = capture.list.getFirst();
        assertEquals(expectedLevel, log.getLevel());
        assertArrayEquals(arguments, log.getArgumentArray());
        assertNull(log.getThrowableProxy());
        for (char c : BREAKS.toCharArray()) assertFalse(log.getFormattedMessage().indexOf(c) >= 0);
        return log;
    }

    private DynamicTest checked(String name, Runnable assertion) {
        return DynamicTest.dynamicTest(name, () -> { capture.list.clear(); assertion.run(); });
    }

    private Stream<DynamicTest> scenarios(String actual, String expected) {
        return Stream.of(
            checked("missing destination", () -> {
                new EdaOrchestrationEventPublisher(null, null).publish(event(actual, null)).block();
                logged(Level.WARN, expected, expected, expected);
            }),
            checked("missing publisher", () -> {
                new EdaOrchestrationEventPublisher(factory(null), "fallback").publish(event(actual, actual)).block();
                logged(Level.WARN, expected, actual == null ? "fallback" : expected);
            }),
            checked("successful publication preserves raw transport", () -> {
                var publisher = new RecordingPublisher(); var event = event(actual, actual);
                new EdaOrchestrationEventPublisher(factory(publisher), "fallback").publish(event).block();
                assertEquals(1, publisher.calls); assertSame(event, publisher.event);
                assertEquals(actual == null ? "fallback" : actual, publisher.destination);
                if (actual != null) {
                    assertEquals(actual, publisher.headers.get("eventType"));
                    assertEquals(actual, publisher.headers.get("executionName"));
                    assertEquals(actual, publisher.headers.get("correlationId"));
                    assertEquals(actual, publisher.headers.get("routing_key"));
                } else assertEquals(":", publisher.headers.get("routing_key"));
                logged(Level.DEBUG, expected, actual == null ? "fallback" : expected);
            }),
            checked("failed publication preserves error", () -> {
                var publisher = new RecordingPublisher(); publisher.failure = new IllegalStateException(actual);
                var event = event(actual, actual);
                Mono<Void> result = new EdaOrchestrationEventPublisher(factory(publisher), "fallback").publish(event);
                assertSame(publisher.failure, assertThrows(IllegalStateException.class, result::block));
                assertEquals(1, publisher.calls); assertSame(event, publisher.event);
                logged(Level.ERROR, expected, actual == null ? "fallback" : expected, expected);
            })
        );
    }

    @TestFactory Stream<DynamicTest> escapesOnlyLogFields() { return scenarios("field" + BREAKS, "field" + VISIBLE); }
    @TestFactory Stream<DynamicTest> preservesOrdinaryFields() { return scenarios("ordinary", "ordinary"); }
    @TestFactory Stream<DynamicTest> preservesNullFields() { return scenarios(null, null); }
    @Test void nullEventProducesNoLogOrFactoryCall() {
        assertNull(new EdaOrchestrationEventPublisher(null, null).publish(null).block());
        assertTrue(capture.list.isEmpty());
    }
}
