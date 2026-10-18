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

package org.fireflyframework.callbacks.models.entity;

import org.fireflyframework.callbacks.interfaces.enums.CallbackExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for callback execution tracking.
 * Represents an attempt to send a callback to a third party.
 * This entity is mapped to the "callback_executions" table in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("callback_executions")
public class CallbackExecution {

    /**
     * Unique identifier for the execution.
     */
    @Id
    private UUID id;

    /**
     * Configuration ID that triggered this execution.
     */
    @Column("configuration_id")
    private UUID configurationId;

    /**
     * Event type that triggered the callback.
     */
    @Column("event_type")
    private String eventType;

    /**
     * Event ID from the source event.
     */
    @Column("source_event_id")
    private UUID sourceEventId;

    /**
     * Execution status (PENDING, IN_PROGRESS, SUCCESS, FAILED_RETRYING, FAILED_PERMANENT, SKIPPED).
     */
    private CallbackExecutionStatus status;

    /**
     * Request payload sent to the callback URL.
     * Stored as JSON TEXT in the database.
     */
    @Column("request_payload")
    private String requestPayload;

    /**
     * HTTP response status code.
     */
    @Column("response_status_code")
    private Integer responseStatusCode;

    /**
     * HTTP response body.
     */
    @Column("response_body")
    private String responseBody;

    /**
     * Request headers sent.
     * Stored as JSON TEXT in the database.
     */
    @Column("request_headers")
    private String requestHeaders;

    /**
     * Response headers received.
     * Stored as JSON TEXT in the database.
     */
    @Column("response_headers")
    private String responseHeaders;

    /**
     * Execution attempt number (0-indexed).
     */
    @Column("attempt_number")
    private Integer attemptNumber;

    /**
     * Maximum retry attempts configured.
     */
    @Column("max_attempts")
    private Integer maxAttempts;

    /**
     * Error message if execution failed.
     */
    @Column("error_message")
    private String errorMessage;

    /**
     * Error stack trace if available.
     */
    @Column("error_stack_trace")
    private String errorStackTrace;

    /**
     * Duration of the HTTP request in milliseconds.
     */
    @Column("request_duration_ms")
    private Long requestDurationMs;

    /**
     * Next scheduled retry time (if applicable).
     */
    @Column("next_retry_at")
    private Instant nextRetryAt;

    /**
     * Time when execution started.
     */
    @Column("executed_at")
    private Instant executedAt;

    /**
     * Time when execution completed.
     */
    @Column("completed_at")
    private Instant completedAt;

    /**
     * Metadata for the execution.
     * Stored as JSON TEXT in the database.
     */
    private String metadata;
}
