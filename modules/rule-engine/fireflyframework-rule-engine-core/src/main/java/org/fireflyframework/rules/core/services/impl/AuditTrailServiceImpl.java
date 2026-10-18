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

package org.fireflyframework.rules.core.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.services.AuditTrailService;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditEventType;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailDTO;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import org.fireflyframework.rules.models.entities.AuditTrail;
import org.fireflyframework.rules.models.repositories.AuditTrailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of AuditTrailService for managing audit trail operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailServiceImpl implements AuditTrailService {

    private final AuditTrailRepository auditTrailRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<AuditTrailDTO> recordAuditEvent(
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
            String correlationId) {

        log.debug("Recording audit event: {} for user: {} on entity: {}", 
                 eventType, userId, entityId);

        AuditTrail auditTrail = AuditTrail.builder()
                .id(UUID.randomUUID())
                .operationType(eventType.name())
                .entityType(eventType.getEntityType())
                .entityId(entityId)
                .ruleCode(ruleCode)
                .userId(userId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .httpMethod(httpMethod)
                .endpoint(endpoint)
                .requestData(requestData)
                .responseData(responseData)
                .statusCode(statusCode)
                .success(success)
                .errorMessage(errorMessage)
                .executionTimeMs(executionTimeMs)
                .metadata(metadata)
                .sessionId(sessionId)
                .correlationId(correlationId)
                .createdAt(OffsetDateTime.now())
                .build();

        return auditTrailRepository.save(auditTrail)
                .doOnNext(savedEntity -> savedEntity.markNotNew()) // Mark as not new after saving
                .map(this::convertToDTO)
                .doOnSuccess(dto -> log.debug("Audit event recorded successfully with ID: {}", dto.getId()))
                .doOnError(error -> log.error("Failed to record audit event: {}", error.getMessage(), error));
    }

    @Override
    public Mono<AuditTrailDTO> recordAuditEvent(
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
            Long executionTimeMs) {

        return recordAuditEvent(eventType, entityId, ruleCode, userId, httpMethod, endpoint,
                requestData, responseData, statusCode, success, null, executionTimeMs,
                null, null, null, null, null);
    }

    @Override
    public Mono<AuditTrailDTO> recordAuditEventWithMetadata(
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
            Map<String, Object> metadataMap) {

        String metadataJson = null;
        if (metadataMap != null && !metadataMap.isEmpty()) {
            try {
                metadataJson = objectMapper.writeValueAsString(metadataMap);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata map to JSON: {}", e.getMessage());
            }
        }

        return recordAuditEvent(eventType, entityId, ruleCode, userId, httpMethod, endpoint,
                requestData, responseData, statusCode, success, null, executionTimeMs,
                metadataJson, null, null, null, null);
    }

    @Override
    public Mono<PaginationResponse<AuditTrailDTO>> getAuditTrails(AuditTrailFilterDTO filterDTO) {
        log.debug("Getting audit trails with filter: {}", filterDTO);

        // Create pageable
        Sort.Direction direction = "ASC".equalsIgnoreCase(filterDTO.getSortDirection()) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(filterDTO.getPage(), filterDTO.getSize(),
                Sort.by(direction, filterDTO.getSortBy()));

        // Get filtered results and count
        Mono<List<AuditTrailDTO>> resultsMono = auditTrailRepository
                .findWithFilters(
                        filterDTO.getOperationType(),
                        filterDTO.getEntityType(),
                        filterDTO.getUserId(),
                        filterDTO.getRuleCode(),
                        filterDTO.getSuccess(),
                        filterDTO.getStartDate(),
                        filterDTO.getEndDate(),
                        pageable)
                .map(this::convertToDTO)
                .collectList();

        Mono<Long> countMono = auditTrailRepository
                .countWithFilters(
                        filterDTO.getOperationType(),
                        filterDTO.getEntityType(),
                        filterDTO.getUserId(),
                        filterDTO.getRuleCode(),
                        filterDTO.getSuccess(),
                        filterDTO.getStartDate(),
                        filterDTO.getEndDate());

        return Mono.zip(resultsMono, countMono)
                .map(tuple -> {
                    List<AuditTrailDTO> results = tuple.getT1();
                    Long totalCount = tuple.getT2();

                    return PaginationResponse.<AuditTrailDTO>builder()
                            .content(results)
                            .currentPage(filterDTO.getPage())
                            .totalElements(totalCount)
                            .totalPages((int) Math.ceil((double) totalCount / filterDTO.getSize()))
                            .build();
                })
                .doOnSuccess(result -> log.debug("Retrieved {} audit trails", result.getContent().size()))
                .doOnError(error -> log.error("Failed to get audit trails: {}", error.getMessage(), error));
    }

    @Override
    public Mono<AuditTrailDTO> getAuditTrailById(UUID id) {
        log.debug("Getting audit trail by ID: {}", id);

        return auditTrailRepository.findById(id)
                .map(this::convertToDTO)
                .doOnSuccess(dto -> log.debug("Retrieved audit trail: {}", dto.getId()))
                .doOnError(error -> log.error("Failed to get audit trail by ID {}: {}", id, error.getMessage(), error));
    }

    @Override
    public Mono<List<AuditTrailDTO>> getRecentAuditTrailsForEntity(UUID entityId, int limit) {
        log.debug("Getting recent audit trails for entity: {} with limit: {}", entityId, limit);

        return auditTrailRepository.findRecentByEntityId(entityId, limit)
                .map(this::convertToDTO)
                .collectList()
                .doOnSuccess(results -> log.debug("Retrieved {} recent audit trails for entity: {}", 
                           results.size(), entityId))
                .doOnError(error -> log.error("Failed to get recent audit trails for entity {}: {}", 
                          entityId, error.getMessage(), error));
    }

    @Override
    public Mono<Map<String, Object>> getAuditTrailStatistics() {
        log.debug("Getting audit trail statistics");

        // Get various statistics
        Mono<Long> totalCountMono = auditTrailRepository.count();
        Mono<Long> successCountMono = auditTrailRepository.countBySuccess(true);
        Mono<Long> failureCountMono = auditTrailRepository.countBySuccess(false);

        // Get counts by operation type
        Mono<Long> ruleDefCreateCountMono = auditTrailRepository.countByOperationType("RULE_DEFINITION_CREATE");
        Mono<Long> ruleDefUpdateCountMono = auditTrailRepository.countByOperationType("RULE_DEFINITION_UPDATE");
        Mono<Long> ruleDefDeleteCountMono = auditTrailRepository.countByOperationType("RULE_DEFINITION_DELETE");
        Mono<Long> ruleEvalCountMono = auditTrailRepository.countByOperationType("RULE_EVALUATION_DIRECT");

        return Mono.zip(totalCountMono, successCountMono, failureCountMono, 
                       ruleDefCreateCountMono, ruleDefUpdateCountMono, ruleDefDeleteCountMono, ruleEvalCountMono)
                .map(tuple -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalCount", tuple.getT1());
                    stats.put("successCount", tuple.getT2());
                    stats.put("failureCount", tuple.getT3());
                    stats.put("successRate", tuple.getT1() > 0 ? (double) tuple.getT2() / tuple.getT1() : 0.0);
                    
                    Map<String, Object> operationCounts = new HashMap<>();
                    operationCounts.put("ruleDefinitionCreate", tuple.getT4());
                    operationCounts.put("ruleDefinitionUpdate", tuple.getT5());
                    operationCounts.put("ruleDefinitionDelete", tuple.getT6());
                    operationCounts.put("ruleEvaluation", tuple.getT7());
                    stats.put("operationCounts", operationCounts);
                    
                    return stats;
                })
                .doOnSuccess(stats -> log.debug("Retrieved audit trail statistics: {}", stats))
                .doOnError(error -> log.error("Failed to get audit trail statistics: {}", error.getMessage(), error));
    }

    @Override
    public Mono<Long> deleteOldAuditTrails(int retentionDays) {
        log.info("Deleting audit trails older than {} days", retentionDays);

        OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);
        
        return auditTrailRepository.findByCreatedAtBefore(cutoffDate)
                .flatMap(auditTrailRepository::delete)
                .count()
                .doOnSuccess(count -> log.info("Deleted {} old audit trail records", count))
                .doOnError(error -> log.error("Failed to delete old audit trails: {}", error.getMessage(), error));
    }

    /**
     * Convert AuditTrail entity to DTO
     */
    private AuditTrailDTO convertToDTO(AuditTrail auditTrail) {
        return AuditTrailDTO.builder()
                .id(auditTrail.getId())
                .operationType(auditTrail.getOperationType())
                .entityType(auditTrail.getEntityType())
                .entityId(auditTrail.getEntityId())
                .ruleCode(auditTrail.getRuleCode())
                .userId(auditTrail.getUserId())
                .ipAddress(auditTrail.getIpAddress())
                .userAgent(auditTrail.getUserAgent())
                .httpMethod(auditTrail.getHttpMethod())
                .endpoint(auditTrail.getEndpoint())
                .requestData(auditTrail.getRequestData())
                .responseData(auditTrail.getResponseData())
                .statusCode(auditTrail.getStatusCode())
                .success(auditTrail.getSuccess())
                .errorMessage(auditTrail.getErrorMessage())
                .executionTimeMs(auditTrail.getExecutionTimeMs())
                .metadata(auditTrail.getMetadata())
                .sessionId(auditTrail.getSessionId())
                .correlationId(auditTrail.getCorrelationId())
                .createdAt(auditTrail.getCreatedAt())
                .build();
    }
}
