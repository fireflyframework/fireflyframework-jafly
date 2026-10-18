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

package org.fireflyframework.webhooks.processor.model;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Result of processing a webhook event.
 * <p>
 * Contains status, messages, and optional data from the processing.
 */
@Data
@Builder
public class WebhookProcessingResult {

    /**
     * The processing status.
     */
    private ProcessingStatus status;

    /**
     * Human-readable message describing the result.
     */
    private String message;

    /**
     * Detailed error message if processing failed.
     */
    private String errorDetails;

    /**
     * Timestamp when processing started.
     */
    private Instant processedAt;

    /**
     * Duration of processing.
     */
    private Duration processingDuration;

    /**
     * Whether the message should be retried.
     */
    @Builder.Default
    private boolean shouldRetry = false;

    /**
     * Suggested delay before retry (if shouldRetry is true).
     */
    private Duration retryDelay;

    /**
     * Optional data produced by the processor.
     */
    private Map<String, Object> data;

    /**
     * Processing status enumeration.
     */
    public enum ProcessingStatus {
        /** Processing completed successfully */
        SUCCESS,
        /** Processing failed and should be retried */
        RETRY,
        /** Processing failed permanently (no retry) */
        FAILED,
        /** Event was skipped (not applicable for this processor) */
        SKIPPED
    }

    // Static factory methods for common results

    /**
     * Creates a successful result.
     *
     * @param message success message
     * @return a success result
     */
    public static WebhookProcessingResult success(String message) {
        return WebhookProcessingResult.builder()
                .status(ProcessingStatus.SUCCESS)
                .message(message)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates a successful result with data.
     *
     * @param message success message
     * @param data result data
     * @return a success result
     */
    public static WebhookProcessingResult success(String message, Map<String, Object> data) {
        return WebhookProcessingResult.builder()
                .status(ProcessingStatus.SUCCESS)
                .message(message)
                .data(data)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates a retry result.
     *
     * @param message retry reason
     * @param retryDelay suggested delay before retry
     * @return a retry result
     */
    public static WebhookProcessingResult retry(String message, Duration retryDelay) {
        return WebhookProcessingResult.builder()
                .status(ProcessingStatus.RETRY)
                .message(message)
                .shouldRetry(true)
                .retryDelay(retryDelay)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates a retry result with default delay.
     *
     * @param message retry reason
     * @return a retry result
     */
    public static WebhookProcessingResult retry(String message) {
        return retry(message, Duration.ofMinutes(1));
    }

    /**
     * Creates a failed result.
     *
     * @param message failure message
     * @param errorDetails detailed error information
     * @return a failed result
     */
    public static WebhookProcessingResult failed(String message, String errorDetails) {
        return WebhookProcessingResult.builder()
                .status(ProcessingStatus.FAILED)
                .message(message)
                .errorDetails(errorDetails)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed result from an exception.
     *
     * @param message failure message
     * @param error the exception
     * @return a failed result
     */
    public static WebhookProcessingResult failed(String message, Throwable error) {
        return failed(message, error.getMessage());
    }

    /**
     * Creates a skipped result.
     *
     * @param message reason for skipping
     * @return a skipped result
     */
    public static WebhookProcessingResult skipped(String message) {
        return WebhookProcessingResult.builder()
                .status(ProcessingStatus.SKIPPED)
                .message(message)
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Checks if processing was successful.
     *
     * @return true if status is SUCCESS
     */
    public boolean isSuccess() {
        return status == ProcessingStatus.SUCCESS;
    }

    /**
     * Checks if processing requires retry.
     *
     * @return true if status is RETRY
     */
    public boolean isRetry() {
        return status == ProcessingStatus.RETRY;
    }

    /**
     * Checks if processing failed.
     *
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status == ProcessingStatus.FAILED;
    }

    /**
     * Checks if processing was skipped.
     *
     * @return true if status is SKIPPED
     */
    public boolean isSkipped() {
        return status == ProcessingStatus.SKIPPED;
    }
}
