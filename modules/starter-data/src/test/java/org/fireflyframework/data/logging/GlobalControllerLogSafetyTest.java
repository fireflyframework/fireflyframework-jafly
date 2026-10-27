/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.data.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.data.controller.EnrichmentDiscoveryController;
import org.fireflyframework.data.controller.GlobalEnrichmentHealthController;
import org.fireflyframework.data.controller.GlobalOperationsController;
import org.fireflyframework.data.operation.EnricherOperationInterface;
import org.fireflyframework.data.operation.EnricherOperationMetadata;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalControllerLogSafetyTest {
    private static final String FIELD = "field\r\nline\u000b\f\u0085\u2028\u2029";
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void discoveryEscapesDiagnosticsAndPreservesProviderMetadata() {
        var f = new Fixture();
        when(f.enricher.supportsEnrichmentType(FIELD)).thenReturn(true);
        try (Capture logs = new Capture(EnrichmentDiscoveryController.class)) {
            var providers = new EnrichmentDiscoveryController(f.registry).listProviders(FIELD, TENANT).block();
            assertEquals(FIELD, providers.getFirst().providerName());
            assertEquals(FIELD, providers.getFirst().type());
            logs.safe();
        }
    }

    @Test void ordinaryAndAbsentDiscoveryFiltersKeepTheirRendering() {
        var f = new Fixture();
        try (Capture logs = new Capture(EnrichmentDiscoveryController.class)) {
            assertEquals(1, new EnrichmentDiscoveryController(f.registry).listProviders(null, null).block().size());
            assertTrue(logs.text().contains("Received request to list providers"));
            assertTrue(logs.text().contains("Found 1 enrichers"));
            logs.safe();
        }
    }

    @Test void emptyHealthCheckEscapesRequestedTypeAndPreservesEmptySuccess() {
        var f = new Fixture();
        when(f.registry.getAllEnrichersForTypeAndTenant(FIELD, TENANT)).thenReturn(List.of());
        try (Capture logs = new Capture(GlobalEnrichmentHealthController.class)) {
            var response = new GlobalEnrichmentHealthController(f.registry).checkGlobalHealth(TENANT, FIELD).block();
            assertTrue(response.overallHealthy());
            assertEquals(0, response.totalEnrichers());
            assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN));
            logs.safe();
        }
    }

    @Test void failedHealthCheckPreservesOriginalFailureAndProvider() {
        var f = new Fixture();
        when(f.enricher.isReady()).thenReturn(Mono.error(new IllegalStateException(FIELD)));
        try (Capture logs = new Capture(GlobalEnrichmentHealthController.class)) {
            var response = new GlobalEnrichmentHealthController(f.registry).checkGlobalHealth(TENANT, FIELD).block();
            assertFalse(response.overallHealthy());
            assertEquals(FIELD, response.enrichers().getFirst().providerName());
            assertEquals("Health check failed: " + FIELD, response.enrichers().getFirst().message());
            logs.safe();
        }
    }

    @Test void operationCatalogPreservesOriginalProviderAndOperation() {
        var f = new Fixture();
        try (Capture logs = new Capture(GlobalOperationsController.class)) {
            var response = f.controller.listOperations(FIELD, TENANT).block().getBody().getFirst();
            assertEquals(FIELD, response.getProviderName());
            assertEquals(FIELD, response.getOperations().getFirst().getOperationId());
            logs.safe();
        }
    }

    @Test void operationSuccessPreservesRequestAndResponseIdentity() {
        var f = new Fixture();
        Object body = new Object();
        when(f.operation.execute(body)).thenReturn(Mono.just(body));
        var request = f.request(body);
        try (Capture logs = new Capture(GlobalOperationsController.class)) {
            assertSame(body, f.controller.executeOperation(request).block().getBody());
            assertEquals(FIELD, request.getType());
            assertEquals(FIELD, request.getOperationId());
            logs.safe();
        }
    }

    @Test void operationFailurePreservesOriginalThrowable() {
        var f = new Fixture();
        Object body = new Object();
        var failure = new IllegalStateException(FIELD);
        when(f.operation.execute(body)).thenReturn(Mono.error(failure));
        try (Capture logs = new Capture(GlobalOperationsController.class)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> f.controller.executeOperation(f.request(body)).block()));
            var event = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
            assertSame(failure, ((ThrowableProxy) event.getThrowableProxy()).getThrowable());
            logs.safe();
        }
    }

    @Test void missingProviderPreservesNotFoundReason() {
        var f = new Fixture();
        when(f.registry.getEnricherForTypeAndTenant(FIELD, TENANT)).thenReturn(Optional.empty());
        try (Capture logs = new Capture(GlobalOperationsController.class)) {
            var failure = assertThrows(ResponseStatusException.class, () -> f.controller.executeOperation(f.request(new Object())));
            assertEquals(404, failure.getStatusCode().value());
            assertTrue(failure.getReason().contains(FIELD));
            logs.safe();
        }
    }

    private static final class Fixture {
        final DataEnricherRegistry registry = mock(DataEnricherRegistry.class);
        final DataEnricher<?, ?, ?> enricher = mock(DataEnricher.class);
        final EnricherOperationInterface<Object, Object> operation = mock(EnricherOperationInterface.class);
        final GlobalOperationsController controller = new GlobalOperationsController(registry);
        Fixture() {
            when(registry.getAllEnrichers()).thenReturn(List.of(enricher));
            when(registry.getAllEnrichersForTypeAndTenant(FIELD, TENANT)).thenReturn(List.of(enricher));
            when(registry.getEnricherForTypeAndTenant(FIELD, TENANT)).thenReturn(Optional.of(enricher));
            when(enricher.getProviderName()).thenReturn(FIELD);
            when(enricher.getTenantId()).thenReturn(TENANT);
            when(enricher.getSupportedEnrichmentTypes()).thenReturn(List.of(FIELD));
            doReturn(List.of(operation)).when(enricher).getOperations();
            when(operation.getMetadata()).thenReturn(EnricherOperationMetadata.builder().operationId(FIELD)
                    .method(RequestMethod.POST).tags(new String[0]).build());
            when(operation.getRequestType()).thenReturn(Object.class);
            when(operation.getResponseType()).thenReturn(Object.class);
        }
        GlobalOperationsController.OperationExecutionRequest request(Object body) {
            var request = new GlobalOperationsController.OperationExecutionRequest();
            request.setType(FIELD); request.setTenantId(TENANT); request.setOperationId(FIELD); request.setRequest(body);
            return request;
        }
    }

    private static final class Capture extends ListAppender<ILoggingEvent> implements AutoCloseable {
        private final Logger logger;
        private final Level original;
        Capture(Class<?> type) {
            logger = (Logger) LoggerFactory.getLogger(type); original = logger.getLevel();
            logger.setLevel(Level.DEBUG); start(); logger.addAppender(this);
        }
        void safe() {
            assertFalse(list.isEmpty());
            for (var event : list) for (char c : new char[]{'\r', '\n', '\u000b', '\f', '\u0085', '\u2028', '\u2029'})
                assertFalse(event.getFormattedMessage().indexOf(c) >= 0, event.getMessage());
        }
        String text() { return list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b); }
        @Override public void close() { logger.detachAppender(this); logger.setLevel(original); stop(); }
    }
}
