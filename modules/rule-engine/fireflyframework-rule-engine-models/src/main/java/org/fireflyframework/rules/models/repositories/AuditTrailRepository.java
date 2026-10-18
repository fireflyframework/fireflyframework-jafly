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

package org.fireflyframework.rules.models.repositories;

import org.fireflyframework.rules.models.entities.AuditTrail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Repository interface for AuditTrail entity operations.
 * Provides methods for querying audit trail records with various filters.
 */
public interface AuditTrailRepository extends BaseRepository<AuditTrail, UUID> {

    /**
     * Find audit trails by operation type
     */
    Flux<AuditTrail> findByOperationTypeOrderByCreatedAtDesc(String operationType, Pageable pageable);

    /**
     * Find audit trails by entity type
     */
    Flux<AuditTrail> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    /**
     * Find audit trails by entity ID
     */
    Flux<AuditTrail> findByEntityIdOrderByCreatedAtDesc(UUID entityId, Pageable pageable);

    /**
     * Find audit trails by rule code
     */
    Flux<AuditTrail> findByRuleCodeOrderByCreatedAtDesc(String ruleCode, Pageable pageable);

    /**
     * Find audit trails by user ID
     */
    Flux<AuditTrail> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find audit trails by success status
     */
    Flux<AuditTrail> findBySuccessOrderByCreatedAtDesc(Boolean success, Pageable pageable);

    /**
     * Find audit trails within a date range
     */
    Flux<AuditTrail> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime startDate, 
            OffsetDateTime endDate, 
            Pageable pageable);

    /**
     * Find audit trails by operation type and date range
     */
    @Query("SELECT * FROM audit_trails WHERE operation_type = :operationType " +
           "AND created_at BETWEEN :startDate AND :endDate " +
           "ORDER BY created_at DESC")
    Flux<AuditTrail> findByOperationTypeAndDateRange(
            @Param("operationType") String operationType,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Find audit trails by user ID and date range
     */
    @Query("SELECT * FROM audit_trails WHERE user_id = :userId " +
           "AND created_at BETWEEN :startDate AND :endDate " +
           "ORDER BY created_at DESC")
    Flux<AuditTrail> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Find audit trails by rule code and operation type
     */
    @Query("SELECT * FROM audit_trails WHERE rule_code = :ruleCode " +
           "AND operation_type = :operationType " +
           "ORDER BY created_at DESC")
    Flux<AuditTrail> findByRuleCodeAndOperationType(
            @Param("ruleCode") String ruleCode,
            @Param("operationType") String operationType,
            Pageable pageable);

    /**
     * Count audit trails by operation type
     */
    Mono<Long> countByOperationType(String operationType);

    /**
     * Count audit trails by user ID
     */
    Mono<Long> countByUserId(String userId);

    /**
     * Count audit trails by success status
     */
    Mono<Long> countBySuccess(Boolean success);

    /**
     * Count audit trails within a date range
     */
    Mono<Long> countByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    /**
     * Find audit trails with complex filtering
     */
    @Query("SELECT * FROM audit_trails WHERE " +
           "(:operationType IS NULL OR operation_type = :operationType) AND " +
           "(:entityType IS NULL OR entity_type = :entityType) AND " +
           "(:userId IS NULL OR user_id = :userId) AND " +
           "(:ruleCode IS NULL OR rule_code = :ruleCode) AND " +
           "(:success IS NULL OR success = :success) AND " +
           "(:startDate IS NULL OR created_at >= :startDate) AND " +
           "(:endDate IS NULL OR created_at <= :endDate) " +
           "ORDER BY created_at DESC")
    Flux<AuditTrail> findWithFilters(
            @Param("operationType") String operationType,
            @Param("entityType") String entityType,
            @Param("userId") String userId,
            @Param("ruleCode") String ruleCode,
            @Param("success") Boolean success,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Count audit trails with complex filtering
     */
    @Query("SELECT COUNT(*) FROM audit_trails WHERE " +
           "(:operationType IS NULL OR operation_type = :operationType) AND " +
           "(:entityType IS NULL OR entity_type = :entityType) AND " +
           "(:userId IS NULL OR user_id = :userId) AND " +
           "(:ruleCode IS NULL OR rule_code = :ruleCode) AND " +
           "(:success IS NULL OR success = :success) AND " +
           "(:startDate IS NULL OR created_at >= :startDate) AND " +
           "(:endDate IS NULL OR created_at <= :endDate)")
    Mono<Long> countWithFilters(
            @Param("operationType") String operationType,
            @Param("entityType") String entityType,
            @Param("userId") String userId,
            @Param("ruleCode") String ruleCode,
            @Param("success") Boolean success,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Find recent audit trails for a specific entity
     */
    @Query("SELECT * FROM audit_trails WHERE entity_id = :entityId " +
           "ORDER BY created_at DESC LIMIT :limit")
    Flux<AuditTrail> findRecentByEntityId(@Param("entityId") UUID entityId, @Param("limit") int limit);

    /**
     * Find audit trails by session ID
     */
    Flux<AuditTrail> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /**
     * Find audit trails by correlation ID
     */
    Flux<AuditTrail> findByCorrelationIdOrderByCreatedAtDesc(String correlationId, Pageable pageable);

    /**
     * Find audit trails created before a specific date
     */
    Flux<AuditTrail> findByCreatedAtBefore(OffsetDateTime cutoffDate);
}
