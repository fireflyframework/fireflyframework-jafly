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

package org.fireflyframework.rules.models.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing an audit trail record for rule engine operations.
 * Tracks all rule definition operations (create, update, delete) and 
 * rule evaluations with detailed request/response information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("audit_trails")
public class AuditTrail implements Persistable<UUID> {

    /**
     * UUID primary key
     */
    @Id
    @Column("id")
    private UUID id;

    /**
     * Type of operation being audited
     * Examples: RULE_DEFINITION_CREATE, RULE_DEFINITION_UPDATE, RULE_DEFINITION_DELETE,
     *          RULE_EVALUATION_DIRECT, RULE_EVALUATION_BY_CODE, RULE_EVALUATION_PLAIN
     */
    @Column("operation_type")
    private String operationType;

    /**
     * Entity type being operated on
     * Examples: RULE_DEFINITION, RULE_EVALUATION
     */
    @Column("entity_type")
    private String entityType;

    /**
     * ID of the entity being operated on (if applicable)
     * For rule definitions: the rule definition UUID
     * For rule evaluations: null (since they don't have persistent IDs)
     */
    @Column("entity_id")
    private UUID entityId;

    /**
     * Code of the rule definition (if applicable)
     * For rule definition operations: the rule code
     * For rule evaluations by code: the rule code used
     * For direct evaluations: null
     */
    @Column("rule_code")
    private String ruleCode;

    /**
     * User who performed the operation
     */
    @Column("user_id")
    private String userId;

    /**
     * IP address from which the operation was performed
     */
    @Column("ip_address")
    private String ipAddress;

    /**
     * User agent string from the request
     */
    @Column("user_agent")
    private String userAgent;

    /**
     * HTTP method used for the operation
     */
    @Column("http_method")
    private String httpMethod;

    /**
     * Request endpoint/path
     */
    @Column("endpoint")
    private String endpoint;

    /**
     * Request data as JSON string
     * For rule definitions: the rule definition DTO
     * For rule evaluations: the evaluation request DTO
     */
    @Column("request_data")
    private String requestData;

    /**
     * Response data as JSON string
     * For rule definitions: the created/updated rule definition DTO
     * For rule evaluations: the evaluation response DTO
     */
    @Column("response_data")
    private String responseData;

    /**
     * HTTP status code of the response
     */
    @Column("status_code")
    private Integer statusCode;

    /**
     * Whether the operation was successful
     */
    @Column("success")
    private Boolean success;

    /**
     * Error message if the operation failed
     */
    @Column("error_message")
    private String errorMessage;

    /**
     * Execution time in milliseconds
     */
    @Column("execution_time_ms")
    private Long executionTimeMs;

    /**
     * Additional metadata as JSON string
     * Can include information like:
     * - Rule validation results
     * - Circuit breaker status
     * - Performance metrics
     * - Business context
     */
    @Column("metadata")
    private String metadata;

    /**
     * Session ID for tracking related operations
     */
    @Column("session_id")
    private String sessionId;

    /**
     * Correlation ID for distributed tracing
     */
    @Column("correlation_id")
    private String correlationId;

    /**
     * Timestamp when the audit record was created
     */
    @Column("created_at")
    private OffsetDateTime createdAt;

    /**
     * Transient field to track if this is a new entity
     */
    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    /**
     * Mark this entity as not new (used after saving)
     */
    public void markNotNew() {
        this.isNew = false;
    }
}
