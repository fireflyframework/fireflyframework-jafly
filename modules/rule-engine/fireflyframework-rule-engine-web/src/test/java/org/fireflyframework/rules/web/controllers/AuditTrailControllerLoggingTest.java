/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.rules.web.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.services.AuditTrailService;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailDTO;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditTrailControllerLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuditTrailController.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final AuditTrailService service = mock(AuditTrailService.class);
    private final AuditTrailController controller = new AuditTrailController(service);
    private final PaginationResponse<AuditTrailDTO> page = new PaginationResponse<>(List.of(), 17, 3, 2);
    private Level previousLevel;
    private boolean previousAdditive;

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        previousAdditive = logger.isAdditive();
        events.start();
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(events);
        when(service.getAuditTrails(any())).thenReturn(Mono.just(page));
    }

    @AfterEach
    void restore() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
        logger.setAdditive(previousAdditive);
        events.stop();
    }

    static Stream<Arguments> labels() {
        return Stream.of(
                Arguments.of("before\rafter", "before\\rafter"),
                Arguments.of("before\nafter", "before\\nafter"),
                Arguments.of("before\u000bafter", "before\\u000bafter"),
                Arguments.of("before\fafter", "before\\fafter"),
                Arguments.of("before\u0085after", "before\\u0085after"),
                Arguments.of("before\u2028after", "before\\u2028after"),
                Arguments.of("before\u2029after", "before\\u2029after"),
                Arguments.of("RULE_DEFINITION_CREATE", "RULE_DEFINITION_CREATE"),
                Arguments.of("alice@example.invalid", "alice@example.invalid"),
                Arguments.of(" 李 é\t ", " 李 é\t "),
                Arguments.of("literal\\n", "literal\\n"),
                Arguments.of("", ""),
                Arguments.of(null, "null"));
    }

    @ParameterizedTest
    @MethodSource("labels")
    void operationTypeLogsStaySingleLineWithoutChangingQuery(String raw, String escaped) {
        var response = controller.getAuditTrailsByOperationType(raw, 3, 17).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(page, response.getBody());
        var filter = query();
        assertEquals(raw, filter.getOperationType());
        assertNull(filter.getUserId());
        diagnostic("Getting audit trails by operation type: {} (page: {}, size: {})",
                "Getting audit trails by operation type: " + escaped + " (page: 3, size: 17)");
    }

    @ParameterizedTest
    @MethodSource("labels")
    void userLogsStaySingleLineWithoutChangingQuery(String raw, String escaped) {
        var response = controller.getAuditTrailsByUser(raw, 3, 17).block();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(page, response.getBody());
        var filter = query();
        assertEquals(raw, filter.getUserId());
        assertNull(filter.getOperationType());
        diagnostic("Getting audit trails by user: {} (page: {}, size: {})",
                "Getting audit trails by user: " + escaped + " (page: 3, size: 17)");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void serviceFailuresKeepOriginalErrorAndRawQuery(boolean operationType) {
        String raw = "before\r\nafter";
        var original = new IllegalStateException("service failure");
        when(service.getAuditTrails(any())).thenReturn(Mono.error(original));

        var result = operationType ? controller.getAuditTrailsByOperationType(raw, 3, 17)
                : controller.getAuditTrailsByUser(raw, 3, 17);

        assertSame(original, assertThrows(IllegalStateException.class, result::block));
        var filter = query();
        assertEquals(raw, operationType ? filter.getOperationType() : filter.getUserId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disabledLoggingKeepsRawQueryAndResponse(boolean operationType) {
        logger.setLevel(Level.OFF);
        String raw = "before\r\nafter";

        var result = operationType ? controller.getAuditTrailsByOperationType(raw, 3, 17)
                : controller.getAuditTrailsByUser(raw, 3, 17);

        assertSame(page, result.block().getBody());
        var filter = query();
        assertEquals(raw, operationType ? filter.getOperationType() : filter.getUserId());
        assertTrue(events.list.isEmpty());
    }

    private AuditTrailFilterDTO query() {
        var argument = ArgumentCaptor.forClass(AuditTrailFilterDTO.class);
        verify(service).getAuditTrails(argument.capture());
        var filter = argument.getValue();
        assertEquals(3, filter.getPage());
        assertEquals(17, filter.getSize());
        return filter;
    }

    private void diagnostic(String template, String expected) {
        var event = events.list.stream().filter(value -> template.equals(value.getMessage())).findFirst().orElseThrow();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals(expected, event.getFormattedMessage());
        for (var logged : events.list) {
            for (String separator : List.of("\r", "\n", "\u000b", "\f", "\u0085", "\u2028", "\u2029")) {
                assertFalse(logged.getFormattedMessage().contains(separator));
            }
        }
    }
}
