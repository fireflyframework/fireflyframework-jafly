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

package org.fireflyframework.cqrs.command;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.observability.metrics.FireflyMetricsSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Dedicated service for collecting and managing command processing metrics in the CQRS framework.
 *
 * <p>This service separates metrics collection concerns from the CommandBus, providing:
 * <ul>
 *   <li>Comprehensive command processing metrics (success, failure, timing)</li>
 *   <li>Per-command-type metrics for detailed monitoring</li>
 *   <li>Validation failure tracking with categorization</li>
 *   <li>Performance monitoring with percentile distributions</li>
 *   <li>Thread-safe metrics collection for high-throughput scenarios</li>
 * </ul>
 *
 * <p>The service automatically creates and manages the following metrics:
 * <ul>
 *   <li><strong>firefly.cqrs.command.processed:</strong> Total commands processed successfully</li>
 *   <li><strong>firefly.cqrs.command.failed:</strong> Total commands that failed processing</li>
 *   <li><strong>firefly.cqrs.command.validation.failed:</strong> Total commands that failed validation</li>
 *   <li><strong>firefly.cqrs.command.processing.time:</strong> Command processing duration</li>
 *   <li><strong>firefly.cqrs.command.type.processed:</strong> Per-command-type success metrics</li>
 *   <li><strong>firefly.cqrs.command.type.failed:</strong> Per-command-type failure metrics</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 1.0.0
 */
@Slf4j
@Component
public class CommandMetricsService extends FireflyMetricsSupport {

    /**
     * Constructs a new CommandMetricsService with the provided MeterRegistry.
     *
     * <p>If no MeterRegistry is provided (null), the service will operate in a no-op mode
     * where all metric operations are silently ignored.
     *
     * @param meterRegistry the meter registry for metrics collection, may be null
     */
    @Autowired
    public CommandMetricsService(@Autowired(required = false) MeterRegistry meterRegistry) {
        super(meterRegistry, "cqrs");

        if (meterRegistry != null) {
            log.info("CommandMetricsService initialized with metrics collection enabled");
        } else {
            log.warn("CommandMetricsService initialized in no-op mode - no MeterRegistry available");
        }
    }

    /**
     * Records a successful command processing event.
     *
     * @param command the command that was processed successfully
     * @param processingTime the time taken to process the command
     * @since 1.0.0
     */
    public void recordCommandSuccess(Command<?> command, Duration processingTime) {
        if (!isEnabled()) {
            return;
        }

        String commandType = command.getClass().getSimpleName();

        counter("command.processed").increment();
        timer("command.processing.time").record(processingTime);

        counter("command.type.processed", "command.type", commandType).increment();
        timer("command.type.processing.time", "command.type", commandType).record(processingTime);

        log.debug("Recorded success metrics for command: {} [{}] in {}ms",
            commandType, command.getCommandId(), processingTime.toMillis());
    }

    /**
     * Records a command processing failure event.
     *
     * @param command the command that failed processing
     * @param error the error that caused the failure
     * @param processingTime the time taken before the failure occurred
     * @since 1.0.0
     */
    public void recordCommandFailure(Command<?> command, Throwable error, Duration processingTime) {
        if (!isEnabled()) {
            return;
        }

        String commandType = command.getClass().getSimpleName();
        String errorType = error.getClass().getSimpleName();

        counter("command.failed").increment();

        counter("command.type.failed", "command.type", commandType).increment();

        log.debug("Recorded failure metrics for command: {} [{}] - Error: {} in {}ms",
            commandType, command.getCommandId(), errorType, processingTime.toMillis());
    }

    /**
     * Records a command validation failure event.
     *
     * @param command the command that failed validation
     * @param validationPhase the phase of validation that failed (e.g., "Jakarta", "Custom")
     * @since 1.0.0
     */
    public void recordValidationFailure(Command<?> command, String validationPhase) {
        if (!isEnabled()) {
            return;
        }

        String commandType = command.getClass().getSimpleName();

        counter("command.validation.failed").increment();

        log.debug("Recorded validation failure for command: {} [{}] - Phase: {}",
            commandType, command.getCommandId(), validationPhase);
    }

    /**
     * Checks if metrics collection is enabled.
     *
     * @return true if metrics collection is enabled, false if in no-op mode
     * @since 1.0.0
     */
    public boolean isMetricsEnabled() {
        return isEnabled();
    }

    /**
     * Gets the current count of successfully processed commands.
     *
     * @return the success count, or 0 if metrics are disabled
     * @since 1.0.0
     */
    public double getSuccessCount() {
        return counter("command.processed").count();
    }

    /**
     * Gets the current count of failed commands.
     *
     * @return the failure count, or 0 if metrics are disabled
     * @since 1.0.0
     */
    public double getFailureCount() {
        return counter("command.failed").count();
    }

    /**
     * Gets the current count of validation failures.
     *
     * @return the validation failure count, or 0 if metrics are disabled
     * @since 1.0.0
     */
    public double getValidationFailureCount() {
        return counter("command.validation.failed").count();
    }
}
