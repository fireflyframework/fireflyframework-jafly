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
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for recording job metrics.
 */
@Slf4j
public class JobMetricsService extends FireflyMetricsSupport {

    private final JobOrchestrationProperties properties;
    private final ConcurrentMap<String, Long> executionStartTimes = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobCount = new AtomicInteger(0);

    public JobMetricsService(MeterRegistry meterRegistry, JobOrchestrationProperties properties) {
        super(meterRegistry, "data");
        this.properties = properties;

        gauge("active.count", activeJobCount, AtomicInteger::get);
    }

    /**
     * Records a job stage execution.
     */
    public void recordJobStageExecution(JobStage stage, String status, Duration duration) {
        if (!isMetricsEnabled()) {
            return;
        }

        timer("stage.execution",
                "stage", stage.name(),
                "status", status,
                "orchestrator", properties.getOrchestratorType())
                .record(duration);

        log.debug("Recorded metric for stage {} with status {} and duration {}", stage, status, duration);
    }

    /**
     * Increments the job stage counter.
     */
    public void incrementJobStageCounter(JobStage stage, String status) {
        if (!isMetricsEnabled()) {
            return;
        }

        counter("stage.count",
                "stage", stage.name(),
                "status", status,
                "orchestrator", properties.getOrchestratorType())
                .increment();

        log.debug("Incremented counter for stage {} with status {}", stage, status);
    }

    /**
     * Records job execution start.
     */
    public void recordJobExecutionStart(String executionId) {
        if (!isMetricsEnabled()) {
            return;
        }

        executionStartTimes.put(executionId, System.currentTimeMillis());

        counter("execution.started",
                "orchestrator", properties.getOrchestratorType())
                .increment();

        log.debug("Recorded job execution start for {}", executionId);
    }

    /**
     * Records job execution completion.
     */
    public void recordJobExecutionCompletion(String executionId, JobExecutionStatus status) {
        if (!isMetricsEnabled()) {
            return;
        }

        Long startTime = executionStartTimes.remove(executionId);
        if (startTime != null) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

            timer("execution.duration",
                    "status", status.name(),
                    "orchestrator", properties.getOrchestratorType())
                    .record(duration);

            log.debug("Recorded job execution completion for {} with status {} and duration {}",
                    executionId, status, duration);
        }

        counter("execution.completed",
                "status", status.name(),
                "orchestrator", properties.getOrchestratorType())
                .increment();
    }

    /**
     * Records a job error.
     */
    public void recordJobError(JobStage stage, String errorType) {
        if (!isMetricsEnabled()) {
            return;
        }

        counter("error",
                "stage", stage.name(),
                "error.type", errorType,
                "orchestrator", properties.getOrchestratorType())
                .increment();

        log.debug("Recorded error for stage {} with type {}", stage, errorType);
    }

    /**
     * Records mapper execution.
     */
    public void recordMapperExecution(String mapperName, boolean success, Duration duration) {
        if (!isMetricsEnabled()) {
            return;
        }

        timer("mapper.execution",
                "mapper", mapperName,
                "status", success ? "success" : "failure")
                .record(duration);

        log.debug("Recorded mapper execution for {} with status {} and duration {}",
                mapperName, success ? "success" : "failure", duration);
    }

    /**
     * Records orchestrator operation.
     */
    public void recordOrchestratorOperation(String operation, boolean success, Duration duration) {
        if (!isMetricsEnabled()) {
            return;
        }

        timer("orchestrator.operation",
                "operation", operation,
                "status", success ? "success" : "failure",
                "orchestrator", properties.getOrchestratorType())
                .record(duration);

        log.debug("Recorded orchestrator operation {} with status {} and duration {}",
                operation, success ? "success" : "failure", duration);
    }

    /**
     * Records active job count.
     */
    public void recordActiveJobCount(int count) {
        if (!isMetricsEnabled()) {
            return;
        }

        activeJobCount.set(count);

        log.debug("Recorded active job count: {}", count);
    }

    /**
     * Records enrichment operation metrics.
     *
     * @param enrichmentType the type of enrichment
     * @param providerName the provider name
     * @param success whether the enrichment was successful
     * @param durationMillis duration in milliseconds
     * @param fieldsEnriched number of fields enriched
     * @param cost cost of the operation
     */
    public void recordEnrichmentMetrics(String enrichmentType,
                                       String providerName,
                                       boolean success,
                                       long durationMillis,
                                       Integer fieldsEnriched,
                                       Double cost) {
        if (!isMetricsEnabled()) {
            return;
        }

        String status = success ? "success" : "failure";

        timer("enrichment.duration",
                "type", enrichmentType,
                "provider", providerName,
                "status", status)
                .record(Duration.ofMillis(durationMillis));

        counter("enrichment.count",
                "type", enrichmentType,
                "provider", providerName,
                "status", status)
                .increment();

        if (fieldsEnriched != null && fieldsEnriched > 0) {
            distributionSummary("enrichment.fields",
                    "type", enrichmentType,
                    "provider", providerName)
                    .record(fieldsEnriched);
        }

        if (cost != null && cost > 0) {
            distributionSummary("enrichment.cost",
                    "type", enrichmentType,
                    "provider", providerName)
                    .record(cost);
        }

        log.debug("Recorded enrichment metrics: type={}, provider={}, success={}, duration={}ms",
                enrichmentType, providerName, success, durationMillis);
    }

    /**
     * Records enrichment error metrics.
     *
     * @param enrichmentType the type of enrichment
     * @param providerName the provider name
     * @param errorType the error type
     * @param durationMillis duration in milliseconds
     */
    public void recordEnrichmentError(String enrichmentType,
                                     String providerName,
                                     String errorType,
                                     long durationMillis) {
        if (!isMetricsEnabled()) {
            return;
        }

        counter("enrichment.errors",
                "type", enrichmentType,
                "provider", providerName,
                "error.type", errorType)
                .increment();

        log.debug("Recorded enrichment error: type={}, provider={}, errorType={}",
                enrichmentType, providerName, errorType);
    }

    private boolean isMetricsEnabled() {
        return isEnabled() && properties.getObservability().isMetricsEnabled();
    }
}
