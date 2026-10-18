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

package org.fireflyframework.rules.interfaces.dtos.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for audit trail records.
 * Contains information about rule engine operations for auditing purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Audit trail record for rule engine operations")
public class AuditTrailDTO {

    @Schema(description = "Unique identifier for the audit record", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Type of operation performed", 
            example = "RULE_DEFINITION_CREATE",
            allowableValues = {"RULE_DEFINITION_CREATE", "RULE_DEFINITION_UPDATE", "RULE_DEFINITION_DELETE", 
                              "RULE_EVALUATION_DIRECT", "RULE_EVALUATION_BY_CODE", "RULE_EVALUATION_PLAIN"})
    private String operationType;

    @Schema(description = "Type of entity being operated on", 
            example = "RULE_DEFINITION",
            allowableValues = {"RULE_DEFINITION", "RULE_EVALUATION"})
    private String entityType;

    @Schema(description = "ID of the entity being operated on", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID entityId;

    @Schema(description = "Code of the rule definition", example = "credit_scoring_v1")
    private String ruleCode;

    @Schema(description = "User who performed the operation", example = "john.doe@company.com")
    private String userId;

    @Schema(description = "IP address from which the operation was performed", example = "192.168.1.100")
    private String ipAddress;

    @Schema(description = "User agent string from the request")
    private String userAgent;

    @Schema(description = "HTTP method used", example = "POST")
    private String httpMethod;

    @Schema(description = "Request endpoint", example = "/api/v1/rules/definitions")
    private String endpoint;

    @Schema(description = "Request data as JSON string")
    private String requestData;

    @Schema(description = "Response data as JSON string")
    private String responseData;

    @Schema(description = "HTTP status code", example = "201")
    private Integer statusCode;

    @Schema(description = "Whether the operation was successful", example = "true")
    private Boolean success;

    @Schema(description = "Error message if operation failed")
    private String errorMessage;

    @Schema(description = "Execution time in milliseconds", example = "150")
    private Long executionTimeMs;

    @Schema(description = "Additional metadata as JSON string")
    private String metadata;

    @Schema(description = "Session ID for tracking related operations")
    private String sessionId;

    @Schema(description = "Correlation ID for distributed tracing")
    private String correlationId;

    @Schema(description = "Timestamp when the audit record was created")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime createdAt;
}
