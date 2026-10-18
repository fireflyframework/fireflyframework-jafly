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

package org.fireflyframework.callbacks.core.service.impl;

import org.fireflyframework.callbacks.core.filters.FilterRequest;
import org.fireflyframework.callbacks.core.filters.FilterUtils;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.core.listener.DynamicListenerFactory;
import org.fireflyframework.callbacks.core.mapper.EventSubscriptionMapper;
import org.fireflyframework.callbacks.core.service.EventSubscriptionService;
import org.fireflyframework.callbacks.interfaces.dto.EventSubscriptionDTO;
import org.fireflyframework.callbacks.models.entity.EventSubscription;
import org.fireflyframework.callbacks.models.repository.EventSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of event subscription service.
 */
@Service
@Slf4j
public class EventSubscriptionServiceImpl implements EventSubscriptionService {

    private final EventSubscriptionRepository subscriptionRepository;
    private final EventSubscriptionMapper subscriptionMapper;
    private final DynamicListenerFactory dynamicListenerFactory;

    public EventSubscriptionServiceImpl(
            EventSubscriptionRepository subscriptionRepository,
            EventSubscriptionMapper subscriptionMapper,
            @Lazy DynamicListenerFactory dynamicListenerFactory) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionMapper = subscriptionMapper;
        this.dynamicListenerFactory = dynamicListenerFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PaginationResponse<EventSubscriptionDTO>> filterSubscriptions(FilterRequest<EventSubscriptionDTO> filterRequest) {
        return FilterUtils
                .createFilter(EventSubscription.class, subscriptionMapper::toDto)
                .withRepository(subscriptionRepository)
                .filter(filterRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<EventSubscriptionDTO> create(EventSubscriptionDTO dto) {
        return Mono.just(dto)
                .map(subscriptionMapper::toEntity)
                .doOnNext(entity -> {
                    // Don't set ID - let R2DBC/PostgreSQL generate it
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    entity.setActive(dto.getActive() != null ? dto.getActive() : true);
                    entity.setTotalMessagesReceived(0L);
                    entity.setTotalMessagesFailed(0L);
                })
                .flatMap(subscriptionRepository::save)
                .map(subscriptionMapper::toDto)
                .doOnSuccess(created -> {
                    log.info("Created event subscription: id={}, name={}", created.getId(), created.getName());
                    // Register dynamic listener for this subscription
                    try {
                        log.info("About to register dynamic listener for subscription: {}", created.getId());
                        dynamicListenerFactory.registerSubscription(created);
                        log.info("Finished registering dynamic listener for subscription: {}", created.getId());
                    } catch (Exception e) {
                        log.error("Failed to register dynamic listener for subscription: {}", created.getId(), e);
                    }
                })
                .doOnError(error -> log.error("Error creating subscription: {}", dto.getName(), error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<EventSubscriptionDTO> update(UUID id, EventSubscriptionDTO dto) {
        return subscriptionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Subscription not found: " + id)))
                .flatMap(existing -> {
                    // Update fields
                    // Map DTO to entity (reuse mapper for complex fields)
                    var updated = subscriptionMapper.toEntity(dto);
                    updated.setId(existing.getId()); // Preserve ID
                    updated.setCreatedAt(existing.getCreatedAt()); // Preserve creation
                    updated.setCreatedBy(existing.getCreatedBy());
                    updated.setUpdatedAt(Instant.now());
                    updated.setTotalMessagesReceived(existing.getTotalMessagesReceived());
                    updated.setTotalMessagesFailed(existing.getTotalMessagesFailed());
                    updated.setLastMessageAt(existing.getLastMessageAt());
                    
                    return subscriptionRepository.save(updated);
                })
                .map(subscriptionMapper::toDto)
                .doOnSuccess(updated -> log.info("Updated event subscription: id={}, name={}", 
                        id, updated.getName()))
                .doOnError(error -> log.error("Error updating subscription: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> delete(UUID id) {
        return subscriptionRepository.deleteById(id)
                .doOnSuccess(v -> log.info("Deleted event subscription: id={}", id))
                .doOnError(error -> log.error("Error deleting subscription: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<EventSubscriptionDTO> findById(UUID id) {
        return subscriptionRepository.findById(id)
                .map(subscriptionMapper::toDto)
                .doOnError(error -> log.error("Error finding subscription: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<EventSubscriptionDTO> findAllActive() {
        return subscriptionRepository.findByActiveTrue()
                .map(subscriptionMapper::toDto)
                .doOnError(error -> log.error("Error finding active subscriptions", error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> updateStats(UUID subscriptionId, boolean success) {
        return subscriptionRepository.findById(subscriptionId)
                .flatMap(subscription -> {
                    subscription.setLastMessageAt(Instant.now());
                    subscription.setTotalMessagesReceived(subscription.getTotalMessagesReceived() + 1);
                    if (!success) {
                        subscription.setTotalMessagesFailed(subscription.getTotalMessagesFailed() + 1);
                    }
                    subscription.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(subscription);
                })
                .then()
                .doOnSuccess(v -> log.debug("Incremented message count for subscription: {}, success: {}", 
                        subscriptionId, success))
                .doOnError(error -> log.error("Error updating stats for subscription: {}", 
                        subscriptionId, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<EventSubscriptionDTO> activate(UUID id) {
        return subscriptionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Subscription not found: " + id)))
                .flatMap(subscription -> {
                    subscription.setActive(true);
                    subscription.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(subscription);
                })
                .map(subscriptionMapper::toDto)
                .doOnSuccess(activated -> log.info("Activated subscription: id={}", id))
                .doOnError(error -> log.error("Error activating subscription: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<EventSubscriptionDTO> deactivate(UUID id) {
        return subscriptionRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Subscription not found: " + id)))
                .flatMap(subscription -> {
                    subscription.setActive(false);
                    subscription.setUpdatedAt(Instant.now());
                    return subscriptionRepository.save(subscription);
                })
                .map(subscriptionMapper::toDto)
                .doOnSuccess(deactivated -> log.info("Deactivated subscription: id={}", id))
                .doOnError(error -> log.error("Error deactivating subscription: {}", id, error));
    }
}
