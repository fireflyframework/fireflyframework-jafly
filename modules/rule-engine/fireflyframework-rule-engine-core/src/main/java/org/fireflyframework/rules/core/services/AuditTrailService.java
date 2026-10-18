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

package org.fireflyframework.rules.core.services;

import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailDTO;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Service interface for audit trail operations.
 * Provides methods for recording audit events and querying audit history.
 */
public interface AuditTrailService {

    /**
     * Record an audit event for a rule definition operation
     *
     * @param eventType the type of audit event
     * @param entityId the ID of the entity being operated on (optional)
     * @param ruleCode the code of the rule definition (optional)
     * @param userId the user performing the operation
     * @param httpMethod the HTTP method used
     * @param endpoint the request endpoint
     * @param requestData the request data as JSON string
     * @param responseData the response data as JSON string
     * @param statusCode the HTTP status code
     * @param success whether the operation was successful
     * @param errorMessage error message if operation failed (optional)
     * @param executionTimeMs execution time in milliseconds
     * @param metadata additional metadata (optional)
     * @param ipAddress client IP address (optional)
     * @param userAgent client user agent (optional)
     * @param sessionId session ID (optional)
     * @param correlationId correlation ID (optional)
     * @return Mono of the created audit trail DTO
     */
    Mono<AuditTrailDTO> recordAuditEvent(
            AuditEventType eventType,
            UUID entityId,
            String ruleCode,
            String userId,
            String httpMethod,
            String endpoint,
            String requestData,
            String responseData,
            Integer statusCode,
            Boolean success,
            String errorMessage,
            Long executionTimeMs,
            String metadata,
            String ipAddress,
            String userAgent,
            String sessionId,
            String correlationId
    );

    /**
     * Record an audit event with simplified parameters
     *
     * @param eventType the type of audit event
     * @param entityId the ID of the entity being operated on (optional)
     * @param ruleCode the code of the rule definition (optional)
     * @param userId the user performing the operation
     * @param httpMethod the HTTP method used
     * @param endpoint the request endpoint
     * @param requestData the request data as JSON string
     * @param responseData the response data as JSON string
     * @param statusCode the HTTP status code
     * @param success whether the operation was successful
     * @param executionTimeMs execution time in milliseconds
     * @return Mono of the created audit trail DTO
     */
    Mono<AuditTrailDTO> recordAuditEvent(
            AuditEventType eventType,
            UUID entityId,
            String ruleCode,
            String userId,
            String httpMethod,
            String endpoint,
            String requestData,
            String responseData,
            Integer statusCode,
            Boolean success,
            Long executionTimeMs
    );

    /**
     * Record an audit event with metadata map
     *
     * @param eventType the type of audit event
     * @param entityId the ID of the entity being operated on (optional)
     * @param ruleCode the code of the rule definition (optional)
     * @param userId the user performing the operation
     * @param httpMethod the HTTP method used
     * @param endpoint the request endpoint
     * @param requestData the request data as JSON string
     * @param responseData the response data as JSON string
     * @param statusCode the HTTP status code
     * @param success whether the operation was successful
     * @param executionTimeMs execution time in milliseconds
     * @param metadataMap additional metadata as map
     * @return Mono of the created audit trail DTO
     */
    Mono<AuditTrailDTO> recordAuditEventWithMetadata(
            AuditEventType eventType,
            UUID entityId,
            String ruleCode,
            String userId,
            String httpMethod,
            String endpoint,
            String requestData,
            String responseData,
            Integer statusCode,
            Boolean success,
            Long executionTimeMs,
            Map<String, Object> metadataMap
    );

    /**
     * Get audit trails with filtering and pagination
     *
     * @param filterDTO filter criteria and pagination parameters
     * @return Mono of paginated audit trail results
     */
    Mono<PaginationResponse<AuditTrailDTO>> getAuditTrails(AuditTrailFilterDTO filterDTO);

    /**
     * Get audit trail by ID
     *
     * @param id the audit trail ID
     * @return Mono of audit trail DTO
     */
    Mono<AuditTrailDTO> getAuditTrailById(UUID id);

    /**
     * Get recent audit trails for a specific entity
     *
     * @param entityId the entity ID
     * @param limit maximum number of records to return
     * @return Mono of audit trail DTOs list
     */
    Mono<java.util.List<AuditTrailDTO>> getRecentAuditTrailsForEntity(UUID entityId, int limit);

    /**
     * Get audit trail statistics
     *
     * @return Mono of audit trail statistics
     */
    Mono<Map<String, Object>> getAuditTrailStatistics();

    /**
     * Delete old audit trails based on retention policy
     *
     * @param retentionDays number of days to retain audit trails
     * @return Mono of number of deleted records
     */
    Mono<Long> deleteOldAuditTrails(int retentionDays);
}
