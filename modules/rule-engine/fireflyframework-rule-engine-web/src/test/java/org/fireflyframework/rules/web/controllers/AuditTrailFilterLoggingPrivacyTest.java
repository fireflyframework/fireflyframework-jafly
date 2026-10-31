/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.rules.web.controllers;

import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.services.AuditTrailService;
import org.fireflyframework.rules.interfaces.dtos.audit.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditTrailFilterLoggingPrivacyTest {
    private static final String SECRET = "PRIVATE_TEST\r\nforged\u2028";
    @Test void filterRemainsUnchangedAndResponseIsPreservedWithoutDtoLogging() {
        var service = mock(AuditTrailService.class); var controller = new AuditTrailController(service);
        var filter = AuditTrailFilterDTO.builder().userId(SECRET).sessionId(SECRET).page(3).size(17).build();
        var page = PaginationResponse.<AuditTrailDTO>builder().content(java.util.List.of()).currentPage(3).build();
        when(service.getAuditTrails(same(filter))).thenReturn(Mono.just(page));
        try (var logs = new Capture(AuditTrailController.class)) {
            assertSame(page, controller.getAuditTrails(filter).block().getBody());
            assertEquals(SECRET, filter.getUserId()); logs.safe(2);
        }
    }
    @Test void filterFailureKeepsOriginalErrorWithoutRawMessageCauseOrStack() {
        var service = mock(AuditTrailService.class); var controller = new AuditTrailController(service);
        var error = new IllegalStateException(SECRET, new IllegalArgumentException(SECRET));
        var filter = AuditTrailFilterDTO.builder().userId(SECRET).build();
        when(service.getAuditTrails(same(filter))).thenReturn(Mono.error(error));
        try (var logs = new Capture(AuditTrailController.class)) {
            assertSame(error, assertThrows(IllegalStateException.class, () -> controller.getAuditTrails(filter).block()));
            logs.safe(2);
        }
    }

    private static final class Capture implements AutoCloseable {
        private final java.util.List<ch.qos.logback.classic.Logger> loggers = new java.util.ArrayList<>();
        private final java.util.List<ch.qos.logback.classic.Level> levels = new java.util.ArrayList<>();
        private final java.util.List<Boolean> additive = new java.util.ArrayList<>();
        private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events = new ch.qos.logback.core.read.ListAppender<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        private final ch.qos.logback.core.OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> output = new ch.qos.logback.core.OutputStreamAppender<>();
        Capture(Class<?>... types) {
            var context = ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(types[0])).getLoggerContext();
            encoder.setContext(context); encoder.setPattern("%level %msg %ex%n"); encoder.start();
            output.setContext(context); output.setEncoder(encoder); output.setOutputStream(bytes); output.start();
            events.setContext(context); events.start();
            for (Class<?> type : types) {
                var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(type);
                loggers.add(logger); levels.add(logger.getLevel()); additive.add(logger.isAdditive());
                logger.setLevel(ch.qos.logback.classic.Level.DEBUG); logger.setAdditive(false);
                logger.addAppender(events); logger.addAppender(output);
            }
        }
        String encoded() { return bytes.toString(java.nio.charset.StandardCharsets.UTF_8); }
        void safe(int minimumEvents) {
            assertTrue(events.list.size() >= minimumEvents, "The real call sites must emit diagnostics");
            assertFalse(encoded().contains("PRIVATE_TEST"), "Sensitive payload reached encoded bytes");
            for (var event : events.list) {
                assertNull(event.getThrowableProxy(), "Raw failure/cause must not reach encoder");
                assertFalse(event.getFormattedMessage().contains("PRIVATE_TEST"));
                for (String separator : java.util.List.of("\r", "\n", "\u000b", "\f", "\u0085", "\u2028", "\u2029")) {
                    assertFalse(event.getFormattedMessage().contains(separator), "Log record boundary injection");
                }
                Object[] arguments = event.getArgumentArray();
                if (arguments != null) for (Object argument : arguments) {
                    assertFalse(argument instanceof Throwable || argument instanceof java.util.Map<?, ?>,
                            "Only diagnostic metadata may reach log arguments");
                }
            }
        }
        @Override public void close() {
            for (int i = 0; i < loggers.size(); i++) {
                var logger = loggers.get(i); logger.detachAppender(events); logger.detachAppender(output);
                logger.setLevel(levels.get(i)); logger.setAdditive(additive.get(i));
            }
            output.stop(); events.stop(); encoder.stop();
        }
    }
}
