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

package org.fireflyframework.data.observability;

import org.fireflyframework.data.config.JobOrchestrationProperties;
import org.fireflyframework.data.model.JobStage;
import org.fireflyframework.data.orchestration.model.JobExecutionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JobMetricsServiceTest {

    private JobMetricsService metricsService;
    private MeterRegistry meterRegistry;
    private JobOrchestrationProperties properties;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new JobOrchestrationProperties();
        properties.getObservability().setMetricsEnabled(true);
        properties.getObservability().setMetricPrefix("firefly.data");
        properties.setOrchestratorType("AWS_STEP_FUNCTIONS");
        
        metricsService = new JobMetricsService(meterRegistry, properties);
    }

    @Test
    void shouldRecordJobStageExecution() {
        // Given
        JobStage stage = JobStage.START;
        String status = "success";
        Duration duration = Duration.ofSeconds(5);
        
        // When
        metricsService.recordJobStageExecution(stage, status, duration);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.stage.execution")
                .tag("stage", "START")
                .tag("status", "success")
                .timer()).isNotNull();
    }

    @Test
    void shouldIncrementJobStageCounter() {
        // Given
        JobStage stage = JobStage.CHECK;
        String status = "success";
        
        // When
        metricsService.incrementJobStageCounter(stage, status);
        metricsService.incrementJobStageCounter(stage, status);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.stage.count")
                .tag("stage", "CHECK")
                .tag("status", "success")
                .counter()).isNotNull();
        
        assertThat(meterRegistry.find("firefly.data.stage.count")
                .tag("stage", "CHECK")
                .tag("status", "success")
                .counter()
                .count()).isEqualTo(2.0);
    }

    @Test
    void shouldRecordJobExecutionStart() {
        // Given
        String executionId = "exec-123";
        
        // When
        metricsService.recordJobExecutionStart(executionId);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.execution.started")
                .counter()).isNotNull();
    }

    @Test
    void shouldRecordJobExecutionCompletion() {
        // Given
        String executionId = "exec-456";
        metricsService.recordJobExecutionStart(executionId);
        
        // When
        metricsService.recordJobExecutionCompletion(executionId, JobExecutionStatus.SUCCEEDED);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.execution.duration")
                .tag("status", "SUCCEEDED")
                .timer()).isNotNull();
        
        assertThat(meterRegistry.find("firefly.data.execution.completed")
                .tag("status", "SUCCEEDED")
                .counter()).isNotNull();
    }

    @Test
    void shouldRecordJobError() {
        // Given
        JobStage stage = JobStage.COLLECT;
        String errorType = "RuntimeException";
        
        // When
        metricsService.recordJobError(stage, errorType);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.error")
                .tag("stage", "COLLECT")
                .tag("error.type", "RuntimeException")
                .counter()).isNotNull();
    }

    @Test
    void shouldRecordMapperExecution() {
        // Given
        String mapperName = "CustomerMapper";
        Duration duration = Duration.ofMillis(100);
        
        // When
        metricsService.recordMapperExecution(mapperName, true, duration);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.mapper.execution")
                .tag("mapper", "CustomerMapper")
                .tag("status", "success")
                .timer()).isNotNull();
    }

    @Test
    void shouldRecordOrchestratorOperation() {
        // Given
        String operation = "startJob";
        Duration duration = Duration.ofMillis(200);
        
        // When
        metricsService.recordOrchestratorOperation(operation, true, duration);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.orchestrator.operation")
                .tag("operation", "startJob")
                .tag("status", "success")
                .timer()).isNotNull();
    }

    @Test
    void shouldRecordActiveJobCount() {
        // Given
        int count = 5;
        
        // When
        metricsService.recordActiveJobCount(count);
        
        // Then
        assertThat(meterRegistry.find("firefly.data.active.count")
                .gauge()).isNotNull();
    }

    @Test
    void shouldNotRecordMetricsWhenDisabled() {
        // Given
        properties.getObservability().setMetricsEnabled(false);
        
        // When
        metricsService.recordJobStageExecution(JobStage.START, "success", Duration.ofSeconds(1));
        
        // Then
        assertThat(meterRegistry.find("firefly.data.stage.execution").timer()).isNull();
    }
}

