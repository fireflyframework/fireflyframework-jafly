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

package org.fireflyframework.data.service;

import org.fireflyframework.data.event.JobEventPublisher;
import org.fireflyframework.data.model.JobStage;
import org.fireflyframework.data.model.JobStageRequest;
import org.fireflyframework.data.model.JobStageResponse;
import org.fireflyframework.data.observability.JobMetricsService;
import org.fireflyframework.data.observability.JobTracingService;
import org.fireflyframework.data.persistence.service.JobAuditService;
import org.fireflyframework.data.persistence.service.JobExecutionResultService;
import org.fireflyframework.data.resiliency.ResiliencyDecoratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for timeout enforcement in AbstractResilientDataJobService.
 */
@ExtendWith(MockitoExtension.class)
class AbstractResilientDataJobServiceTimeoutTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private JobEventPublisher eventPublisher;

    @Mock
    private JobAuditService auditService;

    @Mock
    private JobExecutionResultService resultService;

    @BeforeEach
    void setUp() {
        // Setup default mock behaviors with lenient() to avoid unnecessary stubbing errors
        lenient().when(tracingService.traceJobOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(auditService.recordOperationStarted(any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationCompleted(any(), any(), anyLong(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationFailed(any(), any(), anyLong(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveSuccessResult(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveFailureResult(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void executeWithObservability_shouldTimeoutAfterConfiguredDuration() {
        // Given
        TestDataJobServiceWithTimeout jobService = new TestDataJobServiceWithTimeout(
                tracingService, metricsService, resiliencyService,
                eventPublisher, auditService, resultService,
                Duration.ofMillis(100), Duration.ofMillis(500)
        );

        JobStageRequest request = JobStageRequest.builder()
                .executionId("timeout-exec-001")
                .jobType("slow-job")
                .parameters(Map.of("key", "value"))
                .build();

        // When
        Mono<JobStageResponse> result = jobService.startJob(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getExecutionId()).isEqualTo("timeout-exec-001");
                    assertThat(response.getError()).contains("timed out");
                    assertThat(response.getError()).contains("START");
                })
                .verifyComplete();
    }

    @Test
    void executeWithObservability_shouldNotTimeoutWhenNoTimeoutConfigured() {
        // Given - default getJobTimeout returns null (no timeout)
        TestDataJobServiceWithTimeout jobService = new TestDataJobServiceWithTimeout(
                tracingService, metricsService, resiliencyService,
                eventPublisher, auditService, resultService,
                null, Duration.ofMillis(50)
        );

        JobStageRequest request = JobStageRequest.builder()
                .executionId("no-timeout-exec-001")
                .jobType("slow-job")
                .parameters(Map.of("key", "value"))
                .build();

        // When
        Mono<JobStageResponse> result = jobService.startJob(request);

        // Then - should complete successfully despite the delay since no timeout is configured
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo("no-timeout-exec-001");
                    assertThat(response.getMessage()).isEqualTo("Job started successfully");
                })
                .verifyComplete();
    }

    @Test
    void executeWithObservability_shouldRecordTimeoutMetrics() {
        // Given
        TestDataJobServiceWithTimeout jobService = new TestDataJobServiceWithTimeout(
                tracingService, metricsService, resiliencyService,
                eventPublisher, auditService, resultService,
                Duration.ofMillis(100), Duration.ofMillis(500)
        );

        JobStageRequest request = JobStageRequest.builder()
                .executionId("metrics-exec-001")
                .jobType("slow-job")
                .build();

        // When
        Mono<JobStageResponse> result = jobService.startJob(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getError()).contains("timed out");
                })
                .verifyComplete();

        // Verify timeout metrics were recorded
        verify(metricsService).recordJobError(JobStage.START, "TimeoutException");
        verify(metricsService).incrementJobStageCounter(JobStage.START, "timeout");
    }

    /**
     * Test implementation of AbstractResilientDataJobService with configurable timeout.
     */
    static class TestDataJobServiceWithTimeout extends AbstractResilientDataJobService {

        private final Duration timeout;
        private final Duration operationDelay;

        TestDataJobServiceWithTimeout(JobTracingService tracingService,
                                      JobMetricsService metricsService,
                                      ResiliencyDecoratorService resiliencyService,
                                      JobEventPublisher eventPublisher,
                                      JobAuditService auditService,
                                      JobExecutionResultService resultService,
                                      Duration timeout,
                                      Duration operationDelay) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, auditService, resultService);
            this.timeout = timeout;
            this.operationDelay = operationDelay;
        }

        @Override
        protected Duration getJobTimeout(JobStage stage) {
            return timeout;
        }

        @Override
        protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
            return Mono.just(JobStageResponse.builder()
                            .success(true)
                            .executionId(request.getExecutionId())
                            .message("Job started successfully")
                            .data(Map.of("result", "test-data"))
                            .build())
                    .delayElement(operationDelay);
        }

        @Override
        protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.CHECK, request.getExecutionId(), "Check complete"));
        }

        @Override
        protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.COLLECT, request.getExecutionId(), "Collect complete"));
        }

        @Override
        protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.RESULT, request.getExecutionId(), "Result complete"));
        }

        @Override
        protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) {
            return Mono.just(JobStageResponse.success(JobStage.STOP, request.getExecutionId(), "Stop complete"));
        }
    }
}
