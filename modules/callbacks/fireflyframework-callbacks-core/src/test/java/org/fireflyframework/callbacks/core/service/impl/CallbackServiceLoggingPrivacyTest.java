/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.callbacks.core.service.impl;

import org.fireflyframework.callbacks.core.listener.DynamicListenerFactory;
import org.fireflyframework.callbacks.core.mapper.*;
import org.fireflyframework.callbacks.interfaces.dto.*;
import org.fireflyframework.callbacks.models.entity.*;
import org.fireflyframework.callbacks.models.repository.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallbackServiceLoggingPrivacyTest {
    private static final String SECRET = "PRIVATE_TEST\r\nforged\u2028";
    private static final UUID ID = UUID.fromString("0d09ff14-a0a0-4000-8000-000000000001");
    private static IllegalStateException failure() { return new IllegalStateException(SECRET, new IllegalArgumentException(SECRET)); }

    @Test void callbackCreateFailureLogsTypeWithoutChangingThePublisherError() {
        var repo = mock(CallbackConfigurationRepository.class); var mapper = mock(CallbackConfigurationMapper.class);
        var dto = CallbackConfigurationDTO.builder().name(SECRET).url(SECRET).build();
        when(mapper.toEntity(dto)).thenReturn(new CallbackConfiguration()); var error = failure();
        when(repo.save(any())).thenReturn(Mono.error(error));
        try (var logs = new Capture(CallbackConfigurationServiceImpl.class)) {
            assertSame(error, assertThrows(IllegalStateException.class, () -> new CallbackConfigurationServiceImpl(repo, mapper).create(dto).block()));
            logs.safe(1); assertTrue(logs.encoded().contains("IllegalStateException"));
        }
    }

    @Test void domainCreateFailureDoesNotLogDtoOrFailureGraph() {
        var repo = mock(AuthorizedDomainRepository.class); var mapper = mock(AuthorizedDomainMapper.class);
        var dto = AuthorizedDomainDTO.builder().domain(SECRET).build();
        when(mapper.toEntity(dto)).thenReturn(new AuthorizedDomain()); var error = failure();
        when(repo.save(any())).thenReturn(Mono.error(error));
        try (var logs = new Capture(DomainAuthorizationServiceImpl.class)) {
            assertSame(error, assertThrows(IllegalStateException.class, () -> new DomainAuthorizationServiceImpl(repo, mapper).create(dto).block()));
            logs.safe(1);
        }
    }

    @Test void subscriptionCreateFailureDoesNotLogNameOrFailureGraph() {
        var repo = mock(EventSubscriptionRepository.class); var mapper = mock(EventSubscriptionMapper.class);
        var dto = EventSubscriptionDTO.builder().name(SECRET).build();
        when(mapper.toEntity(dto)).thenReturn(new EventSubscription()); var error = failure();
        when(repo.save(any())).thenReturn(Mono.error(error));
        try (var logs = new Capture(EventSubscriptionServiceImpl.class)) {
            var service = new EventSubscriptionServiceImpl(repo, mapper, mock(DynamicListenerFactory.class));
            assertSame(error, assertThrows(IllegalStateException.class, () -> service.create(dto).block())); logs.safe(1);
        }
    }

    @Test void domainVerificationAndDeletionKeepValuesButOnlyLogMetadata() {
        var repo = mock(AuthorizedDomainRepository.class); var entity = new AuthorizedDomain(); entity.setId(ID);
        when(repo.findByDomain(SECRET)).thenReturn(Mono.just(entity)); when(repo.save(entity)).thenReturn(Mono.just(entity));
        when(repo.deleteById(ID)).thenReturn(Mono.empty());
        var service = new DomainAuthorizationServiceImpl(repo, mock(AuthorizedDomainMapper.class));
        try (var logs = new Capture(DomainAuthorizationServiceImpl.class)) {
            service.verifyDomain(SECRET, SECRET).block(); service.delete(ID, SECRET).block();
            assertTrue(entity.getVerified()); assertEquals(SECRET, entity.getVerificationMethod());
            logs.safe(2); assertTrue(logs.encoded().contains(ID.toString()));
        }
    }

    @Test void invalidAuthorizationAndRecordingUrlsRemainFalseOrEmpty() {
        var service = new DomainAuthorizationServiceImpl(mock(AuthorizedDomainRepository.class), mock(AuthorizedDomainMapper.class));
        try (var logs = new Capture(DomainAuthorizationServiceImpl.class)) {
            assertFalse(service.isAuthorized(SECRET).block()); assertNull(service.recordCallback(SECRET, false).block()); logs.safe(2);
        }
    }

    @Test void successfulCreationDoesNotLogNameOrCredentialBearingUrl() {
        var repo = mock(CallbackConfigurationRepository.class); var mapper = mock(CallbackConfigurationMapper.class);
        var entity = new CallbackConfiguration(); var dto = CallbackConfigurationDTO.builder().id(ID).name(SECRET).url(SECRET).build();
        when(mapper.toEntity(dto)).thenReturn(entity); when(repo.save(entity)).thenReturn(Mono.just(entity)); when(mapper.toDto(entity)).thenReturn(dto);
        try (var logs = new Capture(CallbackConfigurationServiceImpl.class)) {
            assertSame(dto, new CallbackConfigurationServiceImpl(repo, mapper).create(dto).block()); logs.safe(1);
            assertTrue(logs.encoded().contains(ID.toString()));
        }
    }

    @Test void listenerRegistrationFailureKeepsSuccessfulCreationWithoutRawCauseLogging() {
        var repo = mock(EventSubscriptionRepository.class); var mapper = mock(EventSubscriptionMapper.class); var listener = mock(DynamicListenerFactory.class);
        var entity = new EventSubscription(); var dto = EventSubscriptionDTO.builder().id(ID).name(SECRET).build();
        when(mapper.toEntity(dto)).thenReturn(entity); when(repo.save(entity)).thenReturn(Mono.just(entity)); when(mapper.toDto(entity)).thenReturn(dto);
        doThrow(failure()).when(listener).registerSubscription(dto);
        try (var logs = new Capture(EventSubscriptionServiceImpl.class)) {
            assertSame(dto, new EventSubscriptionServiceImpl(repo, mapper, listener).create(dto).block()); logs.safe(3);
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
