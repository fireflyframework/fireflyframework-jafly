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

package org.fireflyframework.callbacks.core.service;

import org.fireflyframework.callbacks.core.filters.FilterRequest;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for managing callback configurations.
 */
public interface CallbackConfigurationService {

    /**
     * Filters callback configurations based on the given criteria.
     *
     * @param filterRequest the request object containing filtering criteria
     * @return a reactive {@code Mono} emitting a {@code PaginationResponse} containing the filtered list
     */
    Mono<PaginationResponse<CallbackConfigurationDTO>> filterConfigurations(FilterRequest<CallbackConfigurationDTO> filterRequest);

    /**
     * Creates a new callback configuration.
     */
    Mono<CallbackConfigurationDTO> create(CallbackConfigurationDTO dto);

    /**
     * Updates an existing callback configuration.
     */
    Mono<CallbackConfigurationDTO> update(UUID id, CallbackConfigurationDTO dto);

    /**
     * Deletes a callback configuration.
     */
    Mono<Void> delete(UUID id);

    /**
     * Finds a callback configuration by ID.
     */
    Mono<CallbackConfigurationDTO> findById(UUID id);

    /**
     * Finds active callback configurations subscribed to a specific event type.
     * Used internally by the callback router.
     */
    Flux<CallbackConfigurationDTO> findActiveByEventType(String eventType);

    /**
     * Records a successful callback execution.
     */
    Mono<Void> recordSuccess(UUID configurationId);

    /**
     * Records a failed callback execution.
     */
    Mono<Void> recordFailure(UUID configurationId);

    /**
     * Checks if a configuration should be paused due to too many failures.
     */
    Mono<Boolean> shouldPause(UUID configurationId);
}
