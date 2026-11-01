/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.data.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.fireflyframework.data.config.JobOrchestrationProperties;
import org.fireflyframework.data.controller.AbstractDataJobController;
import org.fireflyframework.data.model.*;
import org.fireflyframework.data.observability.JobMetricsServiceImpl;
import org.fireflyframework.data.observability.JobTracingService;
import org.fireflyframework.data.resiliency.ResiliencyDecoratorService;
import org.fireflyframework.data.service.AbstractResilientDataJobService;
import org.fireflyframework.data.service.DataEnricher;
import org.fireflyframework.data.service.DataJobService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StarterDataLogSafetyTest {
    private static final String ID = "correlation\r\nline\u2028end\u2029";
    private static final String PRIVATE = "private-payload-marker";

    @Test
    void controllerSuccessKeepsAllStageInputsAndResultsWithoutPayloadDiagnostics() {
        try (Capture logs = new Capture(AbstractDataJobController.class)) {
            ForwardingService service = new ForwardingService();
            AbstractDataJobController controller = new AbstractDataJobController(service) {};
            for (JobStage stage : List.of(JobStage.START, JobStage.CHECK, JobStage.COLLECT, JobStage.RESULT, JobStage.STOP)) {
                service.response = JobStageResponse.success(stage, ID, PRIVATE);
                service.response.setData(Map.of(PRIVATE, PRIVATE));
                assertSame(service.response, call(controller, stage));
                assertEquals(stage, service.request.getStage());
                assertEquals(ID, service.request.getRequestId());
                if (stage == JobStage.START) {
                    assertEquals(Map.of(PRIVATE, PRIVATE), service.request.getParameters());
                    assertEquals(ID, service.request.getInitiator());
                } else assertEquals(ID, service.request.getExecutionId());
                if (stage == JobStage.STOP) assertEquals(PRIVATE, service.reason);
                if (stage == JobStage.RESULT) assertEquals(ID, service.request.getTargetDtoClass());
            }
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertTrue(logs.text().contains("parametersPresent: true"));
            assertTrue(logs.text().contains("dataPresent: true"));
        }
    }

    @Test
    void controllerFailureResponsesStayUnchangedAndRetainWarnLevel() {
        try (Capture logs = new Capture(AbstractDataJobController.class)) {
            ForwardingService service = new ForwardingService();
            AbstractDataJobController controller = new AbstractDataJobController(service) {};
            for (JobStage stage : List.of(JobStage.START, JobStage.CHECK, JobStage.COLLECT, JobStage.RESULT, JobStage.STOP)) {
                service.response = JobStageResponse.failure(stage, ID, PRIVATE);
                service.response.setMessage(PRIVATE);
                assertSame(service.response, call(controller, stage));
                assertEquals(PRIVATE, service.response.getError());
            }
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertEquals(5, logs.list.stream().filter(event -> event.getLevel() == Level.WARN).count());
        }
    }

    @Test
    void controllerPropagatesOriginalErrorsButLogsOnlyTheirType() {
        try (Capture logs = new Capture(AbstractDataJobController.class)) {
            ForwardingService service = new ForwardingService();
            service.error = new IllegalStateException(PRIVATE + "\n");
            AbstractDataJobController controller = new AbstractDataJobController(service) {};
            for (JobStage stage : List.of(JobStage.START, JobStage.CHECK, JobStage.COLLECT, JobStage.RESULT, JobStage.STOP)) {
                assertSame(service.error, assertThrows(IllegalStateException.class, () -> call(controller, stage)));
            }
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertTrue(logs.text().contains(IllegalStateException.class.getName()));
            assertEquals(5, logs.list.stream().filter(event -> event.getLevel() == Level.ERROR).count());
        }
    }

    @Test
    void reactiveTracingRetainsResultAndCorrelationWithoutLineBreaks() {
        try (Capture logs = new Capture(JobTracingService.class)) {
            JobTracingService tracing = tracing();
            Object result = new Object();
            MDC.put("correlationId", "unchanged-correlation");
            try {
                assertSame(result, tracing.traceJobOperation(JobStage.START, ID, Mono.just(result)).block());
                assertSame(result, tracing.traceOperation(ID, ID, Mono.just(result)).block());
                tracing.recordEvent(ID, PRIVATE);
                logs.safe();
                assertTrue(logs.text().contains("correlation\\r\\nline\\u2028end\\u2029"));
                assertEquals("unchanged-correlation", MDC.get("correlationId"));
            } finally { MDC.remove("correlationId"); }
        }
    }

    @Test
    void reactiveTracingPropagatesOriginalErrorsWithoutMessageOrStackLogs() {
        try (Capture logs = new Capture(JobTracingService.class)) {
            IllegalStateException error = new IllegalStateException(PRIVATE);
            JobTracingService tracing = tracing();
            assertSame(error, assertThrows(IllegalStateException.class,
                    () -> tracing.traceJobOperation(JobStage.START, ID, Mono.error(error)).block()));
            assertSame(error, assertThrows(IllegalStateException.class,
                    () -> tracing.traceOperation(ID, ID, Mono.error(error)).block()));
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertTrue(logs.text().contains(IllegalStateException.class.getName()));
        }
    }

    @Test
    void synchronousTracingPreservesNormalValuesNullIdsAndOriginalException() {
        try (Capture logs = new Capture(JobTracingService.class)) {
            JobTracingService tracing = tracing();
            assertEquals("result", tracing.traceJobOperationSync(JobStage.START, "ordinary-id", () -> "result"));
            IllegalArgumentException error = new IllegalArgumentException(PRIVATE);
            assertSame(error, assertThrows(IllegalArgumentException.class,
                    () -> tracing.traceJobOperationSync(JobStage.CHECK, null, () -> { throw error; })));
            logs.safe();
            assertTrue(logs.text().contains("execution ID ordinary-id"));
            assertTrue(logs.text().contains("execution null"));
            assertFalse(logs.text().contains(PRIVATE));
        }
    }

    @Test
    void disabledTracingDoesNotSubscribeOrInvokeAnOperationMoreThanOnce() {
        JobOrchestrationProperties properties = new JobOrchestrationProperties();
        properties.getObservability().setTracingEnabled(false);
        JobTracingService tracing = new JobTracingService(ObservationRegistry.create(), properties);
        AtomicInteger calls = new AtomicInteger();
        Mono<Integer> operation = Mono.fromSupplier(calls::incrementAndGet);
        assertSame(operation, tracing.traceOperation(ID, ID, operation));
        assertEquals(0, calls.get());
        assertEquals(1, operation.block());
        assertEquals(2, tracing.traceJobOperationSync(JobStage.START, ID, calls::incrementAndGet));
    }

    @Test
    void resilientJobPreservesResponseAndFailureConversionWithoutPayloadLogs() {
        try (Capture logs = new Capture(AbstractResilientDataJobService.class)) {
            JobStageRequest request = JobStageRequest.builder().executionId(ID)
                    .parameters(Map.of(PRIVATE, PRIVATE)).build();
            JobStageResponse response = JobStageResponse.success(JobStage.START, ID, PRIVATE);
            response.setData(Map.of(PRIVATE, PRIVATE));
            ResilientJob job = new ResilientJob(Mono.just(response));
            assertSame(response, job.startJob(request).block());
            assertSame(request, job.seen);
            IllegalStateException error = new IllegalStateException(PRIVATE);
            JobStageResponse failure = new ResilientJob(Mono.error(error)).startJob(request).block();
            assertFalse(failure.isSuccess());
            assertEquals("Error executing START stage: " + PRIVATE, failure.getError());
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertTrue(logs.text().contains(IllegalStateException.class.getName()));
        }
    }

    @Test
    void resilientUnsuccessfulResponseIsReturnedWithoutLoggingItsMessage() {
        try (Capture logs = new Capture(AbstractResilientDataJobService.class)) {
            JobStageResponse response = JobStageResponse.failure(JobStage.CHECK, ID, PRIVATE);
            response.setMessage(PRIVATE);
            assertSame(response, new ResilientJob(Mono.just(response)).checkJob(
                    JobStageRequest.builder().executionId(ID).build()).block());
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertTrue(logs.list.stream().anyMatch(event -> event.getLevel() == Level.WARN));
        }
    }

    @Test
    void enricherRetainsRawOutputAndExistingFailureResponseWithoutPayloadLogs() {
        try (Capture logs = new Capture(DataEnricher.class)) {
            EnrichmentRequest request = EnrichmentRequest.builder().type(ID).requestId(ID)
                    .strategy(EnrichmentStrategy.RAW).build();
            Map<String,Object> provider = Map.of("result", PRIVATE);
            TestEnricher success = new TestEnricher(Mono.just(provider));
            EnrichmentResponse response = success.enrich(request).block();
            assertTrue(response.isSuccess());
            assertSame(provider, response.getEnrichedData());
            assertSame(request, success.seen);
            assertEquals(1, success.enrichBatch(List.of(request)).collectList().block().size());
            IllegalStateException error = new IllegalStateException(PRIVATE);
            EnrichmentResponse failure = new TestEnricher(Mono.error(error)).enrich(request).block();
            assertFalse(failure.isSuccess());
            assertEquals("Enrichment failed: " + PRIVATE, failure.getError());
            logs.safe();
            assertFalse(logs.text().contains(PRIVATE));
            assertTrue(logs.text().contains(IllegalStateException.class.getName()));
        }
    }

    @Test
    void diagnosticsDoNotEvaluateArbitraryParameterOrResultMaps() {
        Map<String,Object> opaque = new java.util.AbstractMap<>() {
            public java.util.Set<Entry<String,Object>> entrySet() { throw new AssertionError("Payload traversal forbidden"); }
            public int size() { throw new IllegalStateException("Diagnostic count unavailable"); }
            public java.util.Set<String> keySet() { throw new IllegalStateException("Diagnostic keys unavailable"); }
        };
        ForwardingService service = new ForwardingService();
        service.response = JobStageResponse.success(JobStage.COLLECT, "ordinary-id", "ordinary");
        service.response.setData(opaque);
        AbstractDataJobController controller = new AbstractDataJobController(service) {};
        try (Capture logs = new Capture(AbstractDataJobController.class)) {
            assertSame(service.response, controller.startJob(JobStartRequest.builder().parameters(opaque).build()).block());
            assertSame(opaque, service.request.getParameters());
            assertSame(service.response, controller.collectJobResults("ordinary-id", null).block());
            logs.safe();
        }
        try (Capture logs = new Capture(AbstractResilientDataJobService.class)) {
            assertSame(service.response, new ResilientJob(Mono.just(service.response)).startJob(
                    JobStageRequest.builder().parameters(opaque).build()).block());
            logs.safe();
        }
    }

    private static JobTracingService tracing() {
        return new JobTracingService(ObservationRegistry.create(), new JobOrchestrationProperties());
    }

    private static JobStageResponse call(AbstractDataJobController controller, JobStage stage) {
        return switch (stage) {
            case START -> controller.startJob(JobStartRequest.builder().requestId(ID).initiator(ID)
                    .parameters(Map.of(PRIVATE, PRIVATE)).build()).block();
            case CHECK -> controller.checkJob(ID, ID).block();
            case COLLECT -> controller.collectJobResults(ID, ID).block();
            case RESULT -> controller.getJobResult(ID, ID, ID).block();
            case STOP -> controller.stopJob(ID, ID, PRIVATE).block();
            default -> throw new IllegalArgumentException("Unexpected stage");
        };
    }

    private static class ForwardingService implements DataJobService {
        JobStageRequest request; JobStageResponse response; RuntimeException error; String reason;
        Mono<JobStageResponse> result(JobStageRequest value) {
            request = value;
            return error == null ? Mono.just(response) : Mono.error(error);
        }
        public Mono<JobStageResponse> startJob(JobStageRequest value) { return result(value); }
        public Mono<JobStageResponse> checkJob(JobStageRequest value) { return result(value); }
        public Mono<JobStageResponse> collectJobResults(JobStageRequest value) { return result(value); }
        public Mono<JobStageResponse> getJobResult(JobStageRequest value) { return result(value); }
        public Mono<JobStageResponse> stopJob(JobStageRequest value, String why) { reason = why; return result(value); }
    }

    private static class ResilientJob extends AbstractResilientDataJobService {
        private final Mono<JobStageResponse> outcome; JobStageRequest seen;
        ResilientJob(Mono<JobStageResponse> outcome) {
            super(tracing(), new JobMetricsServiceImpl(new SimpleMeterRegistry(), new JobOrchestrationProperties()),
                    new ResiliencyDecoratorService(new JobOrchestrationProperties()));
            this.outcome = outcome;
        }
        protected Mono<JobStageResponse> doStartJob(JobStageRequest request) { seen = request; return outcome; }
        protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) { seen = request; return outcome; }
        protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) { seen = request; return outcome; }
        protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) { seen = request; return outcome; }
        protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) { seen = request; return outcome; }
    }

    private static class TestEnricher extends DataEnricher<Object,Map<String,Object>,Object> {
        private final Mono<Map<String,Object>> outcome; EnrichmentRequest seen;
        TestEnricher(Mono<Map<String,Object>> outcome) {
            super(tracing(), null, new ResiliencyDecoratorService(new JobOrchestrationProperties()), Object.class);
            this.outcome = outcome;
        }
        protected Mono<Map<String,Object>> fetchProviderData(EnrichmentRequest request) { seen = request; return outcome; }
        protected Object mapToTarget(Map<String,Object> value) { return value; }
        public String getProviderName() { return ID; }
    }

    private static class Capture extends ListAppender<ILoggingEvent> implements AutoCloseable {
        private final Logger logger; private final Level previous;
        Capture(Class<?> type) {
            logger = (Logger) LoggerFactory.getLogger(type); previous = logger.getLevel();
            logger.setLevel(Level.DEBUG); start(); logger.addAppender(this);
        }
        String text() { return String.join(" | ", list.stream().map(ILoggingEvent::getFormattedMessage).toList()); }
        void safe() {
            assertFalse(list.isEmpty());
            for (ILoggingEvent event : list) {
                assertFalse(event.getFormattedMessage().matches("(?s).*[\r\n\u000b\f\u0085\u2028\u2029].*"), event.getFormattedMessage());
                assertNull(event.getThrowableProxy());
            }
        }
        public void close() { logger.detachAppender(this); logger.setLevel(previous); stop(); }
    }
}
