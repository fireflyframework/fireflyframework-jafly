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

import org.fireflyframework.callbacks.interfaces.enums.CallbackStatus;
import org.fireflyframework.callbacks.models.entity.CallbackConfiguration;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repository for callback configurations.
 */
@Repository
public interface CallbackConfigurationRepository extends BaseRepository<CallbackConfiguration, UUID> {

    /**
     * Finds configurations by status.
     */
    Flux<CallbackConfiguration> findByStatus(CallbackStatus status);

    /**
     * Finds active configurations subscribed to a specific event type.
     */
    @Query("SELECT * FROM callback_configurations " +
           "WHERE status = 'ACTIVE' " +
           "AND active = true " +
           "AND :eventType = ANY(subscribed_event_types)")
    Flux<CallbackConfiguration> findActiveByEventType(String eventType);

    /**
     * Finds configurations by tenant ID.
     */
    Flux<CallbackConfiguration> findByTenantId(String tenantId);

    /**
     * Finds all active configurations.
     */
    @Query("SELECT * FROM callback_configurations WHERE active = true AND status = 'ACTIVE'")
    Flux<CallbackConfiguration> findAllActive();
}
