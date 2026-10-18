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

package org.fireflyframework.callbacks.models.repository;

import org.fireflyframework.callbacks.interfaces.enums.CallbackExecutionStatus;
import org.fireflyframework.callbacks.models.entity.CallbackExecution;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for callback executions.
 */
@Repository
public interface CallbackExecutionRepository extends BaseRepository<CallbackExecution, UUID> {

    /**
     * Finds executions by configuration ID.
     */
    Flux<CallbackExecution> findByConfigurationIdOrderByExecutedAtDesc(UUID configurationId);

    /**
     * Finds executions by status.
     */
    Flux<CallbackExecution> findByStatus(CallbackExecutionStatus status);

    /**
     * Finds pending retries.
     */
    @Query("SELECT * FROM callback_executions " +
           "WHERE status = 'FAILED_RETRYING' " +
           "AND next_retry_at <= :now " +
           "ORDER BY next_retry_at")
    Flux<CallbackExecution> findPendingRetries(Instant now);

    /**
     * Finds recent executions for a configuration.
     */
    @Query("SELECT * FROM callback_executions " +
           "WHERE configuration_id = :configurationId " +
           "AND executed_at >= :since " +
           "ORDER BY executed_at DESC")
    Flux<CallbackExecution> findRecentByConfigurationId(UUID configurationId, Instant since);
}
