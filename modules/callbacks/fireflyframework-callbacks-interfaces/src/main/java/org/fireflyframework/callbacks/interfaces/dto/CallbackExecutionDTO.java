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

package org.fireflyframework.callbacks.interfaces.dto;

import org.fireflyframework.callbacks.interfaces.enums.CallbackExecutionStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for callback execution tracking.
 * Represents an attempt to send a callback to a third party.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackExecutionDTO {

    /**
     * Unique identifier for the execution.
     */
    private UUID id;

    /**
     * Configuration ID that triggered this execution.
     */
    private UUID configurationId;

    /**
     * Event type that triggered the callback.
     * Maximum 255 characters.
     */
    @Size(max = 255, message = "Event type must not exceed 255 characters")
    private String eventType;

    /**
     * Event ID from the source event.
     */
    private UUID sourceEventId;

    /**
     * Execution status.
     */
    @NotNull(message = "Status is required")
    private CallbackExecutionStatus status;

    /**
     * Request payload sent to the callback URL.
     */
    private Map<String, Object> requestPayload;

    /**
     * HTTP response status code.
     * Must be between 100 and 599.
     */
    @Min(value = 100, message = "Response status code must be at least 100")
    @Max(value = 599, message = "Response status code must not exceed 599")
    private Integer responseStatusCode;

    /**
     * HTTP response body.
     * Maximum 10000 characters.
     */
    @Size(max = 10000, message = "Response body must not exceed 10000 characters")
    private String responseBody;

    /**
     * Request headers sent.
     */
    private Map<String, String> requestHeaders;

    /**
     * Response headers received.
     */
    private Map<String, String> responseHeaders;

    /**
     * Execution attempt number (0-indexed).
     * Must be non-negative.
     */
    @Builder.Default
    @Min(value = 0, message = "Attempt number must be non-negative")
    private Integer attemptNumber = 0;

    /**
     * Maximum retry attempts configured.
     * Must be between 0 and 10.
     */
    @Min(value = 0, message = "Max attempts must be at least 0")
    @Max(value = 10, message = "Max attempts must not exceed 10")
    private Integer maxAttempts;

    /**
     * Error message if execution failed.
     * Maximum 2000 characters.
     */
    @Size(max = 2000, message = "Error message must not exceed 2000 characters")
    private String errorMessage;

    /**
     * Error stack trace if available.
     * Maximum 10000 characters.
     */
    @Size(max = 10000, message = "Error stack trace must not exceed 10000 characters")
    private String errorStackTrace;

    /**
     * Duration of the HTTP request in milliseconds.
     * Must be non-negative.
     */
    @Min(value = 0, message = "Request duration must be non-negative")
    private Long requestDurationMs;

    /**
     * Next scheduled retry time (if applicable).
     */
    private Instant nextRetryAt;

    /**
     * Time when execution started.
     */
    private Instant executedAt;

    /**
     * Time when execution completed.
     */
    private Instant completedAt;

    /**
     * Metadata for the execution.
     */
    private Map<String, Object> metadata;
}
