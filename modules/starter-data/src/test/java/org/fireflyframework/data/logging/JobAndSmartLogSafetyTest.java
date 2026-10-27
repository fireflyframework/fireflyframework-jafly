/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.data.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.data.config.JobOrchestrationProperties;
import org.fireflyframework.data.controller.SmartEnrichmentController;
import org.fireflyframework.data.event.JobEvent;
import org.fireflyframework.data.event.JobEventPublisher;
import org.fireflyframework.data.model.*;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobAndSmartLogSafetyTest {
    private static final String FIELD = "field\r\nline\u000b\f\u0085\u2028\u2029";
    private static final String VISIBLE = "field\\r\\nline\\u000b\\f\\u0085\\u2028\\u2029";
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void jobDiagnosticsPreserveEveryPublishedEventAndOriginalIdentifiers() {
        AtomicReference<JobEvent> published = new AtomicReference<>();
        ApplicationEventPublisher events = event -> published.set((JobEvent) event);
        var properties = new JobOrchestrationProperties();
        properties.setPublishJobEvents(true);
        var publisher = new JobEventPublisher(events, properties);
        Map<String, Object> payload = Map.of("private", new Object());
        try (Capture logs = new Capture(JobEventPublisher.class)) {
            publisher.publishJobStarted(JobStageRequest.builder().jobType(FIELD).executionId(FIELD).parameters(payload).build());
            assertEquals(FIELD, published.get().getExecutionId());
            assertSame(payload, published.get().getData());
            publisher.publishJobStageCompleted(JobStage.START, JobStageResponse.success(JobStage.START, FIELD, FIELD));
            assertEquals(FIELD, published.get().getExecutionId());
            var error = new IllegalArgumentException(FIELD);
            publisher.publishJobFailed(FIELD, FIELD, FIELD, error);
            assertEquals(FIELD, published.get().getData().get("exceptionMessage"));
            publisher.publishJobCompleted(FIELD, FIELD, payload);
            assertSame(payload, published.get().getData());
            logs.safe();
            assertEquals(4, logs.list.stream().filter(e -> e.getLevel() == Level.DEBUG).count());
            assertTrue(logs.text().contains(VISIBLE));
        }
    }

    @Test void disabledJobPublicationDoesNotReadOrDispatchItsRequest() {
        var properties = new JobOrchestrationProperties();
        properties.setPublishJobEvents(false);
        var events = mock(ApplicationEventPublisher.class);
        var publisher = new JobEventPublisher(events, properties);
        publisher.publishJobStarted(null);
        publisher.publishJobFailed(FIELD, FIELD, FIELD, null);
        verifyNoInteractions(events);
    }

    @Test void previewSanitizesDiagnosticsWithoutChangingMetadataOrSelection() {
        var f = new Fixture();
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            var response = f.controller.previewEnrichment(f.request()).block().getBody();
            assertEquals(FIELD, response.getProviderName());
            assertEquals(FIELD, response.getEnrichmentType());
            verify(f.registry).getEnricherForTypeAndTenant(FIELD, TENANT);
            logs.safe();
            assertTrue(logs.text().contains(VISIBLE));
        }
    }

    @Test void missingPreviewPreservesNotFoundAndWarning() {
        var f = new Fixture();
        when(f.registry.getEnricherForTypeAndTenant(FIELD, TENANT)).thenReturn(Optional.empty());
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            assertEquals(404, f.controller.previewEnrichment(f.request()).block().getStatusCode().value());
            logs.safe();
            assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN));
        }
    }

    @Test void smartSuccessPreservesRawRequestResultAndCorrelation() {
        var f = new Fixture();
        var result = f.response(true);
        when(f.enricher.enrich(any())).thenReturn(Mono.just(result));
        var request = f.request();
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            MDC.put("correlationId", "unchanged");
            try {
                var actual = f.controller.smartEnrich(request).block();
                assertSame(result.getEnrichedData(), actual.getEnrichedData());
                assertSame(result.getRawProviderResponse(), actual.getRawProviderResponse());
                assertEquals(FIELD, actual.getProviderName());
                assertEquals(FIELD, actual.getType());
                assertEquals(FIELD, actual.getRequestId());
                var captured = ArgumentCaptor.forClass(EnrichmentRequest.class);
                verify(f.enricher).enrich(captured.capture());
                assertSame(request.getSource(), captured.getValue().getSourceDto());
                assertSame(request.getParams(), captured.getValue().getParameters());
                assertEquals(FIELD, captured.getValue().getRequestId());
                assertEquals("unchanged", MDC.get("correlationId"));
                logs.safe();
            } finally { MDC.remove("correlationId"); }
        }
    }

    @Test void smartFailureResponseKeepsOriginalMessageAndWarnLevel() {
        var f = new Fixture();
        when(f.enricher.enrich(any())).thenReturn(Mono.just(f.response(false)));
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            assertEquals(FIELD, f.controller.smartEnrich(f.request()).block().getMessage());
            logs.safe();
            assertTrue(logs.list.stream().anyMatch(e -> e.getLevel() == Level.WARN));
        }
    }

    @Test void missingSmartProviderKeepsExceptionMessageAndStatus() {
        var f = new Fixture();
        when(f.registry.getEnricherForTypeAndTenant(FIELD, TENANT)).thenReturn(Optional.empty());
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            var error = assertThrows(ResponseStatusException.class, () -> f.controller.smartEnrich(f.request()));
            assertEquals(404, error.getStatusCode().value());
            assertTrue(error.getReason().contains(FIELD));
            logs.safe();
        }
    }

    @Test void smartErrorsKeepThrowableIdentityAndOnlyEscapeDiagnosticArguments() {
        var f = new Fixture();
        var failure = new IllegalStateException(FIELD);
        when(f.enricher.enrich(any())).thenReturn(Mono.error(failure));
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> f.controller.smartEnrich(f.request()).block()));
            var event = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).findFirst().orElseThrow();
            assertSame(failure, ((ThrowableProxy) event.getThrowableProxy()).getThrowable());
            logs.safe();
        }
    }

    @Test void batchAndStreamKeepFailureBodiesAndContinuation() {
        var f = new Fixture();
        var failure = new IllegalArgumentException(FIELD);
        when(f.enricher.enrichBatch(anyList())).thenReturn(Flux.error(failure));
        when(f.enricher.enrich(any())).thenReturn(Mono.error(failure));
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            var batch = f.controller.smartEnrichBatch(List.of(f.request())).collectList().block();
            assertEquals(FIELD, batch.getFirst().getError());
            var stream = f.controller.streamEnrichment(List.of(f.request())).collectList().block();
            assertEquals("Enrichment failed: " + FIELD, stream.getFirst().data().getMessage());
            assertEquals("complete", stream.getLast().event());
            logs.safe();
        }
    }

    @Test void ordinaryAndNullDiagnosticStringsKeepTheirRendering() {
        var f = new Fixture();
        when(f.enricher.getProviderName()).thenReturn("ordinary-provider");
        when(f.registry.getEnricherForTypeAndTenant("ordinary", TENANT)).thenReturn(Optional.of(f.enricher));
        var result = f.response(true);
        result.setProviderName(null); result.setType("ordinary"); result.setRequestId(null);
        when(f.enricher.enrich(any())).thenReturn(Mono.just(result));
        try (Capture logs = new Capture(SmartEnrichmentController.class)) {
            f.controller.smartEnrich(EnrichmentApiRequest.builder().type("ordinary").tenantId(TENANT).build()).block();
            assertTrue(logs.text().contains("Selected enricher: ordinary-provider"));
            assertTrue(logs.text().contains("provider: null"));
            assertTrue(logs.text().contains("requestId: null"));
            logs.safe();
        }
    }

    private static final class Fixture {
        final DataEnricherRegistry registry = mock(DataEnricherRegistry.class);
        final DataEnricher<?, ?, ?> enricher = mock(DataEnricher.class);
        final SmartEnrichmentController controller = new SmartEnrichmentController(registry, null);
        Fixture() {
            when(registry.getEnricherForTypeAndTenant(FIELD, TENANT)).thenReturn(Optional.of(enricher));
            when(enricher.getProviderName()).thenReturn(FIELD);
            when(enricher.getTenantId()).thenReturn(TENANT);
        }
        EnrichmentApiRequest request() {
            return EnrichmentApiRequest.builder().type(FIELD).tenantId(TENANT).requestId(FIELD)
                    .source(new Object()).params(Map.of("private", "unchanged")).build();
        }
        EnrichmentResponse response(boolean success) {
            return EnrichmentResponse.builder().success(success).type(FIELD).providerName(FIELD).message(FIELD)
                    .requestId(FIELD).enrichedData(new Object()).rawProviderResponse(new Object()).build();
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
            for (var event : list) for (char c : new char[]{'\r', '\n', '\u000b', '\f', '\u0085', '\u2028', '\u2029'})
                assertFalse(event.getFormattedMessage().indexOf(c) >= 0, event.getMessage());
        }
        String text() { return list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b); }
        @Override public void close() { logger.detachAppender(this); logger.setLevel(original); stop(); }
    }
}
