/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.data.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.fireflyframework.data.config.JobOrchestrationProperties;
import org.fireflyframework.data.controller.AbstractSyncDataJobController;
import org.fireflyframework.data.event.*;
import org.fireflyframework.data.model.*;
import org.fireflyframework.data.observability.JobMetricsService;
import org.fireflyframework.data.orchestration.model.JobExecutionStatus;
import org.fireflyframework.data.persistence.model.JobExecutionResult;
import org.fireflyframework.data.persistence.port.JobExecutionResultRepository;
import org.fireflyframework.data.persistence.service.JobExecutionResultService;
import org.fireflyframework.data.service.SyncDataJobService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemainingDataLogSafetyTest {
    private static final String FIELD = "field\r\nline\u000b\f\u0085\u2028\u2029";

    @Test void enrichmentMetricDiagnosticsPreserveExactRawTags() {
        var properties = new JobOrchestrationProperties();
        var registry = new SimpleMeterRegistry();
        try (var logs = new Capture(JobMetricsService.class)) {
            var service = new JobMetricsService(registry, properties);
            service.recordEnrichmentMetrics(FIELD, FIELD, true, 42, 3, 1.5);
            service.recordEnrichmentError(FIELD, FIELD, FIELD, 42);
            assertNotNull(registry.find("firefly.data.enrichment.duration").tags("type", FIELD, "provider", FIELD).timer());
            assertEquals(1, registry.find("firefly.data.enrichment.errors").tags("type", FIELD, "provider", FIELD, "error.type", FIELD).counter().count());
            logs.safe();
        } finally { registry.close(); }
    }

    @Test void ordinaryJobMetricsKeepRawExecutionKeysAndStatuses() {
        var properties = new JobOrchestrationProperties();
        var registry = new SimpleMeterRegistry();
        try (var logs = new Capture(JobMetricsService.class)) {
            var service = new JobMetricsService(registry, properties);
            service.recordJobStageExecution(JobStage.START, FIELD, Duration.ofMillis(2));
            service.incrementJobStageCounter(JobStage.CHECK, FIELD);
            service.recordJobExecutionStart(FIELD);
            service.recordJobExecutionCompletion(FIELD, JobExecutionStatus.SUCCEEDED);
            service.recordJobError(JobStage.CHECK, FIELD);
            service.recordMapperExecution(FIELD, true, Duration.ofMillis(2));
            service.recordOrchestratorOperation(FIELD, false, Duration.ofMillis(2));
            assertNotNull(registry.find("firefly.data.execution.duration").timer());
            assertEquals(FIELD, registry.find("firefly.data.stage.execution").timer().getId().getTag("status"));
            logs.safe();
        } finally { registry.close(); }
    }

    @Test void enrichmentEventsRetainRawFieldsAndMetadata() {
        var events = new ArrayList<EnrichmentEvent>();
        ApplicationEventPublisher publisher = event -> events.add((EnrichmentEvent) event);
        var request = EnrichmentRequest.builder().type(FIELD).requestId(FIELD).metadata(Map.of("raw", FIELD)).build();
        var response = EnrichmentResponse.builder().type(FIELD).providerName(FIELD).requestId(FIELD).success(true).message(FIELD).metadata(Map.of("raw", FIELD)).build();
        try (var logs = new Capture(EnrichmentEventPublisher.class)) {
            var service = new EnrichmentEventPublisher(publisher);
            service.publishEnrichmentStarted(request, FIELD);
            service.publishEnrichmentCompleted(request, response, 42);
            service.publishEnrichmentFailed(request, FIELD, FIELD, new IllegalStateException(FIELD), 42);
            assertEquals(3, events.size());
            for (var event : events) { assertEquals(FIELD, event.getEnrichmentType()); assertEquals(FIELD, event.getProviderName()); assertEquals(FIELD, event.getMetadata().get("raw")); }
            assertEquals(FIELD, events.get(2).getError());
            assertEquals(FIELD, events.get(2).getMetadata().get("exceptionMessage"));
            logs.safe();
        }
    }

    @Test void eventPublicationFailureRetainsOriginalThrowable() {
        var failure = new IllegalStateException(FIELD);
        ApplicationEventPublisher publisher = event -> { throw failure; };
        try (var logs = new Capture(EnrichmentEventPublisher.class)) {
            new EnrichmentEventPublisher(publisher).publishEnrichmentStarted(EnrichmentRequest.builder().type(FIELD).build(), FIELD);
            logs.throwable(failure, Level.WARN); logs.safe();
        }
    }

    @Test void persistenceSizeDiagnosticsPreserveRawDataAndExecutionIdentity() {
        var repository = mock(JobExecutionResultRepository.class);
        when(repository.save(any())).thenAnswer(call -> { JobExecutionResult row = call.getArgument(0); row.setResultId(FIELD); return Mono.just(row); });
        var request = JobStageRequest.builder().executionId(FIELD).requestId(FIELD).jobType("job").build();
        Map<String,Object> raw = Map.of("raw", "RAW_BODY_ONLY");
        try (var logs = new Capture(JobExecutionResultService.class)) {
            var result = resultService(repository).saveSuccessResult(request, JobStageResponse.builder().success(true).build(), Instant.now(), raw, raw, "kind", "definition").block();
            assertEquals(FIELD, result.getExecutionId()); assertEquals(FIELD, result.getResultId()); assertSame(raw, result.getRawOutput());
            assertTrue(result.getDataSizeBytes() > 0); logs.safe();
        }
    }

    @Test void persistedFailureTextAndRepositoryErrorIdentityRemainUnmodified() {
        var repository = mock(JobExecutionResultRepository.class);
        when(repository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var request = JobStageRequest.builder().executionId(FIELD).jobType("job").build();
        try (var logs = new Capture(JobExecutionResultService.class)) {
            var result = resultService(repository).saveFailureResult(request, Instant.now(), FIELD, FIELD, "kind", "definition").block();
            assertEquals(FIELD, result.getErrorMessage()); assertEquals(FIELD, result.getErrorCause());
            var failure = new IllegalStateException(FIELD);
            doReturn(Mono.error(failure)).when(repository).save(any());
            assertNull(resultService(repository).saveFailureResult(request, Instant.now(), FIELD, FIELD, "kind", "definition").block());
            logs.throwable(failure, Level.ERROR); logs.safe();
        }
    }

    @Test void persistenceUpdateCacheAndCleanupKeepOriginalInputsAndCounts() {
        var repository = mock(JobExecutionResultRepository.class);
        var row = JobExecutionResult.builder().resultId(FIELD).executionId(FIELD).cacheable(true).expiresAt(Instant.now().plusSeconds(60)).build();
        when(repository.findByExecutionId(FIELD)).thenReturn(Mono.just(row));
        when(repository.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(repository.deleteByEndTimeBefore(any())).thenReturn(Mono.just(4L));
        when(repository.deleteExpired()).thenReturn(Mono.just(5L));
        try (var logs = new Capture(JobExecutionResultService.class)) {
            var service = resultService(repository); Map<String,Object> output = Map.of("raw", FIELD);
            assertSame(row, service.updateWithTransformedOutput(FIELD, output, FIELD, FIELD).block());
            assertSame(output, row.getTransformedOutput()); assertSame(row, service.getCachedResult(FIELD).block().orElseThrow());
            assertEquals(4L, service.cleanupOldResults().block()); assertEquals(5L, service.cleanupExpiredCachedResults().block());
            verify(repository, times(2)).findByExecutionId(FIELD); logs.safe();
        }
    }

    @Test void syncRequestAndResponseDoNotTraverseOpaqueMaps() {
        var parameters = new OpaqueMap(); var data = new OpaqueMap();
        var response = JobStageResponse.builder().success(true).executionId(FIELD).data(data).build();
        var service = mock(SyncDataJobService.class); when(service.getJobName()).thenReturn(FIELD); when(service.execute(any())).thenReturn(Mono.just(response));
        try (var logs = new Capture(AbstractSyncDataJobController.class)) {
            assertSame(response, new Controller(service).execute(parameters, FIELD, FIELD, "{\"raw\":\"RAW_METADATA_ONLY\"}").block());
            var request = org.mockito.ArgumentCaptor.forClass(JobStageRequest.class); verify(service).execute(request.capture());
            assertSame(parameters, request.getValue().getParameters()); assertEquals("RAW_METADATA_ONLY", request.getValue().getMetadata().get("raw"));
            assertFalse(logs.text().contains("RAW_METADATA_ONLY")); logs.safe();
        }
    }

    @Test void syncResponseDiagnosticsNeverFormatTheResponseObject() {
        var formatted = new AtomicInteger();
        var response = new JobStageResponse() { @Override public String toString() { formatted.incrementAndGet(); return "RAW_RESPONSE_ONLY"; } };
        response.setSuccess(true); response.setExecutionId("execution");
        var service = mock(SyncDataJobService.class); when(service.getJobName()).thenReturn("job"); when(service.execute(any())).thenReturn(Mono.just(response));
        try (var logs = new Capture(AbstractSyncDataJobController.class)) {
            assertSame(response, new Controller(service).execute(null, null, null, null).block());
            logs.text(); assertEquals(0, formatted.get()); assertFalse(logs.text().contains("RAW_RESPONSE_ONLY"));
        }
    }

    @Test void invalidMetadataKeepsEmptyFallbackAndOnlyLogsExceptionType() {
        var mapper = new ObjectMapper() { @Override public <T> T readValue(String text, Class<T> type) throws JsonProcessingException { throw new JsonParseException(null, "RAW_INVALID_METADATA_ONLY"); } };
        var service = mock(SyncDataJobService.class); when(service.getJobName()).thenReturn("job"); when(service.execute(any())).thenReturn(Mono.just(JobStageResponse.builder().success(true).build()));
        try (var logs = new Capture(AbstractSyncDataJobController.class)) {
            new Controller(service, mapper).execute(null, null, null, "ordinary-invalid-metadata").block();
            var request = org.mockito.ArgumentCaptor.forClass(JobStageRequest.class); verify(service).execute(request.capture());
            assertEquals(Map.of(), request.getValue().getMetadata()); assertFalse(logs.text().contains("RAW_INVALID_METADATA_ONLY"));
            assertTrue(logs.text().contains("JsonParseException")); logs.safe();
        }
    }

    @Test void syncFailureAndErrorPreserveResponseThrowableAndMdc() {
        var service = mock(SyncDataJobService.class); when(service.getJobName()).thenReturn(FIELD);
        var response = JobStageResponse.builder().success(false).executionId(FIELD).message(FIELD).build(); when(service.execute(any())).thenReturn(Mono.just(response));
        MDC.put("ordinary-context", FIELD);
        try (var logs = new Capture(AbstractSyncDataJobController.class)) {
            var controller = new Controller(service); assertSame(response, controller.execute(null, FIELD, FIELD, null).block());
            var failure = new IllegalStateException(FIELD); when(service.execute(any())).thenReturn(Mono.error(failure));
            assertSame(failure, assertThrows(IllegalStateException.class, () -> controller.execute(null, FIELD, FIELD, null).block()));
            assertEquals(FIELD, MDC.get("ordinary-context")); logs.throwable(failure, Level.ERROR); logs.safe();
        } finally { MDC.remove("ordinary-context"); }
    }

    @Test void ordinaryAndNullStringsKeepTheirDiagnosticRendering() {
        var service = mock(SyncDataJobService.class); when(service.getJobName()).thenReturn("ordinary-job");
        var response = JobStageResponse.builder().success(false).executionId("ordinary-execution").message(null).build(); when(service.execute(any())).thenReturn(Mono.just(response));
        try (var logs = new Capture(AbstractSyncDataJobController.class)) {
            assertSame(response, new Controller(service).execute(null, "ordinary-request", null, null).block());
            assertTrue(logs.text().contains("jobName: ordinary-job")); assertTrue(logs.text().contains("requestId: ordinary-request")); assertTrue(logs.text().contains("initiator: null"));
            assertTrue(logs.text().contains("message: null")); logs.safe();
        }
    }

    private static JobExecutionResultService resultService(JobExecutionResultRepository repository) { return new JobExecutionResultService(Optional.of(repository), new JobOrchestrationProperties(), ObservationRegistry.NOOP); }
    private static final class Controller extends AbstractSyncDataJobController {
        Controller(SyncDataJobService service) { super(service); }
        Controller(SyncDataJobService service, ObjectMapper mapper) { super(service, mapper); }
    }
    private static final class OpaqueMap extends HashMap<String,Object> {
        @Override public Set<String> keySet() { throw new AssertionError("Logging must not traverse payload keys"); }
        @Override public Set<Map.Entry<String,Object>> entrySet() { throw new AssertionError("Logging must not traverse payload entries"); }
        @Override public String toString() { throw new AssertionError("Logging must not format payload maps"); }
    }
    private static final class Capture extends ListAppender<ILoggingEvent> implements AutoCloseable {
        final Logger logger; final Level original;
        Capture(Class<?> type) { logger=(Logger) LoggerFactory.getLogger(type); original=logger.getLevel(); logger.setLevel(Level.TRACE); start();logger.addAppender(this); }
        void safe() { assertFalse(list.isEmpty()); for(var e:list) for(char c:new char[]{'\r','\n','\u000b','\f','\u0085','\u2028','\u2029'})assertFalse(e.getFormattedMessage().indexOf(c)>=0,e.getMessage()); }
        String text() { return list.stream().map(ILoggingEvent::getFormattedMessage).reduce("",(a,b)->a+b); }
        void throwable(Throwable failure,Level level) { var event=list.stream().filter(e->e.getLevel()==level&&e.getThrowableProxy()!=null).findFirst().orElseThrow();assertSame(failure,((ThrowableProxy)event.getThrowableProxy()).getThrowable()); }
        @Override public void close(){logger.detachAppender(this);logger.setLevel(original);stop();}
    }
}
