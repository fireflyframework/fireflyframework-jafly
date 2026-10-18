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
 * DTO for filtering audit trail records.
 * Contains various filter criteria for querying audit trails.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter criteria for audit trail queries")
public class AuditTrailFilterDTO {

    @Schema(description = "Filter by operation type", 
            example = "RULE_DEFINITION_CREATE",
            allowableValues = {"RULE_DEFINITION_CREATE", "RULE_DEFINITION_UPDATE", "RULE_DEFINITION_DELETE", 
                              "RULE_EVALUATION_DIRECT", "RULE_EVALUATION_BY_CODE", "RULE_EVALUATION_PLAIN"})
    private String operationType;

    @Schema(description = "Filter by entity type", 
            example = "RULE_DEFINITION",
            allowableValues = {"RULE_DEFINITION", "RULE_EVALUATION"})
    private String entityType;

    @Schema(description = "Filter by entity ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID entityId;

    @Schema(description = "Filter by rule code", example = "credit_scoring_v1")
    private String ruleCode;

    @Schema(description = "Filter by user ID", example = "john.doe@company.com")
    private String userId;

    @Schema(description = "Filter by success status", example = "true")
    private Boolean success;

    @Schema(description = "Filter by start date (inclusive)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime startDate;

    @Schema(description = "Filter by end date (inclusive)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime endDate;

    @Schema(description = "Filter by session ID")
    private String sessionId;

    @Schema(description = "Filter by correlation ID")
    private String correlationId;

    @Builder.Default
    @Schema(description = "Page number (0-based)", example = "0", defaultValue = "0")
    private Integer page = 0;

    @Builder.Default
    @Schema(description = "Page size", example = "20", defaultValue = "20")
    private Integer size = 20;

    @Builder.Default
    @Schema(description = "Sort field", example = "createdAt", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Builder.Default
    @Schema(description = "Sort direction", example = "DESC", defaultValue = "DESC", allowableValues = {"ASC", "DESC"})
    private String sortDirection = "DESC";
}
