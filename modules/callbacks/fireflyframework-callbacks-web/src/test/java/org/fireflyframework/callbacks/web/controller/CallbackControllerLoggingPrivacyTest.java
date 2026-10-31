/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.callbacks.web.controller;

import org.fireflyframework.callbacks.core.filters.FilterRequest;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.core.mapper.CallbackExecutionMapper;
import org.fireflyframework.callbacks.core.service.*;
import org.fireflyframework.callbacks.interfaces.dto.*;
import org.fireflyframework.callbacks.models.repository.CallbackExecutionRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallbackControllerLoggingPrivacyTest {
    private static final String SECRET = "PRIVATE_TEST\r\nforged\u2028" + "x".repeat(4096);
    private static final UUID ID = UUID.fromString("0d09ff14-a0a0-4000-8000-000000000001");

    @Test void domainRequestsKeepOriginalValuesWithoutLoggingTheirContents() {
        var service = mock(DomainAuthorizationService.class);
        var controller = new AuthorizedDomainController(service);
        var dto = AuthorizedDomainDTO.builder().id(ID).domain(SECRET).notes(SECRET).build();
        var filter = FilterRequest.<AuthorizedDomainDTO>builder().page(2).size(7).search(SECRET).filters(Map.of("secret", SECRET)).build();
        var page = PaginationResponse.<AuthorizedDomainDTO>builder().content(java.util.List.of(dto)).build();
        when(service.filterDomains(same(filter))).thenReturn(Mono.just(page));
        when(service.create(same(dto))).thenReturn(Mono.just(dto));
        when(service.verifyDomain(SECRET, SECRET)).thenReturn(Mono.empty());
        when(service.delete(ID, SECRET)).thenReturn(Mono.empty());
        try (var logs = new Capture(AuthorizedDomainController.class)) {
            assertSame(page, controller.filterDomains(filter).block().getBody());
            assertSame(dto, controller.create(dto).block());
            controller.verify(SECRET, SECRET).block(); controller.delete(ID, SECRET).block();
            assertEquals(SECRET, dto.getDomain()); assertEquals(SECRET, filter.getSearch());
            logs.safe(4); assertTrue(logs.encoded().contains(ID.toString()));
        }
    }

    @Test void callbackConfigurationDoesNotRenderUrlsNamesOrFilters() {
        var service = mock(CallbackConfigurationService.class);
        var controller = new CallbackConfigurationController(service);
        var dto = CallbackConfigurationDTO.builder().id(ID).name(SECRET).url("https://user:" + SECRET + "@example.invalid/?token=" + SECRET).build();
        var filter = FilterRequest.<CallbackConfigurationDTO>builder().search(SECRET).build();
        var page = PaginationResponse.<CallbackConfigurationDTO>builder().content(java.util.List.of(dto)).build();
        when(service.filterConfigurations(same(filter))).thenReturn(Mono.just(page));
        when(service.create(same(dto))).thenReturn(Mono.just(dto));
        try (var logs = new Capture(CallbackConfigurationController.class)) {
            assertSame(page, controller.filterConfigurations(filter).block().getBody());
            assertSame(dto, controller.create(dto).block()); assertEquals(SECRET, dto.getName());
            logs.safe(2);
        }
    }

    @Test void subscriptionDoesNotRenderNamesOrFilterContents() {
        var service = mock(EventSubscriptionService.class);
        var controller = new EventSubscriptionController(service);
        var dto = EventSubscriptionDTO.builder().id(ID).name(SECRET).build();
        var filter = FilterRequest.<EventSubscriptionDTO>builder().search(SECRET).build();
        var page = PaginationResponse.<EventSubscriptionDTO>builder().content(java.util.List.of(dto)).build();
        when(service.filterSubscriptions(same(filter))).thenReturn(Mono.just(page));
        when(service.create(same(dto))).thenReturn(Mono.just(dto));
        try (var logs = new Capture(EventSubscriptionController.class)) {
            assertSame(page, controller.filterSubscriptions(filter).block().getBody());
            assertSame(dto, controller.create(dto).block()); logs.safe(2);
        }
    }

    @Test void executionFilteringAndInvalidDurationKeepTheirExistingContracts() {
        var repository = mock(CallbackExecutionRepository.class);
        var controller = new CallbackExecutionController(repository, mock(CallbackExecutionMapper.class));
        when(repository.count()).thenReturn(Mono.just(0L));
        var filter = FilterRequest.<CallbackExecutionDTO>builder().page(2).size(7).search(SECRET).build();
        try (var logs = new Capture(CallbackExecutionController.class)) {
            assertEquals(2, controller.filterExecutions(filter).block().getBody().getPage());
            assertThrows(java.time.format.DateTimeParseException.class, () -> controller.findRecent(ID, SECRET));
            assertEquals(SECRET, filter.getSearch()); logs.safe(2);
            assertTrue(logs.encoded().contains(ID.toString()));
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
