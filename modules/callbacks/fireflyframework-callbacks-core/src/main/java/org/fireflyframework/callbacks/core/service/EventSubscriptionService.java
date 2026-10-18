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
import org.fireflyframework.callbacks.interfaces.dto.EventSubscriptionDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for managing event subscriptions.
 */
public interface EventSubscriptionService {

    /**
     * Filters event subscriptions based on the given criteria.
     *
     * @param filterRequest the request object containing filtering criteria
     * @return a reactive {@code Mono} emitting a {@code PaginationResponse} containing the filtered list
     */
    Mono<PaginationResponse<EventSubscriptionDTO>> filterSubscriptions(FilterRequest<EventSubscriptionDTO> filterRequest);

    /**
     * Creates a new event subscription.
     *
     * @param subscription the subscription to create
     * @return Mono emitting the created subscription
     */
    Mono<EventSubscriptionDTO> create(EventSubscriptionDTO subscription);

    /**
     * Finds all active event subscriptions.
     * Used internally by the dynamic listener factory.
     *
     * @return Flux of active subscriptions
     */
    Flux<EventSubscriptionDTO> findAllActive();

    /**
     * Finds a subscription by ID.
     *
     * @param id the subscription ID
     * @return Mono emitting the subscription
     */
    Mono<EventSubscriptionDTO> findById(UUID id);

    /**
     * Updates an existing subscription.
     *
     * @param id the subscription ID
     * @param subscription the updated subscription
     * @return Mono emitting the updated subscription
     */
    Mono<EventSubscriptionDTO> update(UUID id, EventSubscriptionDTO subscription);

    /**
     * Deletes a subscription.
     *
     * @param id the subscription ID
     * @return Mono that completes when deleted
     */
    Mono<Void> delete(UUID id);

    /**
     * Updates subscription statistics.
     *
     * @param id the subscription ID
     * @param success whether the message was processed successfully
     * @return Mono that completes when stats are updated
     */
    Mono<Void> updateStats(UUID id, boolean success);

    /**
     * Activates a subscription.
     *
     * @param id the subscription ID
     * @return Mono emitting the activated subscription
     */
    Mono<EventSubscriptionDTO> activate(UUID id);

    /**
     * Deactivates a subscription.
     *
     * @param id the subscription ID
     * @return Mono emitting the deactivated subscription
     */
    Mono<EventSubscriptionDTO> deactivate(UUID id);
}
