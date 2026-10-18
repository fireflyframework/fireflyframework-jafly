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
import org.fireflyframework.callbacks.core.mapper.CallbackConfigurationMapper;
import org.fireflyframework.callbacks.core.service.CallbackConfigurationService;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import org.fireflyframework.callbacks.interfaces.enums.CallbackStatus;
import org.fireflyframework.callbacks.models.entity.CallbackConfiguration;
import org.fireflyframework.callbacks.models.repository.CallbackConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of callback configuration service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CallbackConfigurationServiceImpl implements CallbackConfigurationService {

    private final CallbackConfigurationRepository configurationRepository;
    private final CallbackConfigurationMapper configurationMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<PaginationResponse<CallbackConfigurationDTO>> filterConfigurations(FilterRequest<CallbackConfigurationDTO> filterRequest) {
        return FilterUtils
                .createFilter(CallbackConfiguration.class, configurationMapper::toDto)
                .withRepository(configurationRepository)
                .filter(filterRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<CallbackConfigurationDTO> create(CallbackConfigurationDTO dto) {
        return Mono.just(dto)
                .map(configurationMapper::toEntity)
                .doOnNext(entity -> {
                    // Don't set ID - let R2DBC/PostgreSQL generate it
                    entity.setCreatedAt(Instant.now());
                    entity.setUpdatedAt(Instant.now());
                    entity.setStatus(dto.getStatus() != null ? dto.getStatus() : CallbackStatus.ACTIVE);
                    entity.setActive(dto.getActive() != null ? dto.getActive() : true);
                    entity.setFailureCount(0);
                })
                .flatMap(configurationRepository::save)
                .map(configurationMapper::toDto)
                .doOnSuccess(created -> log.info("Created callback configuration: id={}, name={}, url={}", 
                        created.getId(), created.getName(), created.getUrl()))
                .doOnError(error -> log.error("Error creating callback configuration: {}", dto.getName(), error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<CallbackConfigurationDTO> update(UUID id, CallbackConfigurationDTO dto) {
        return configurationRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Configuration not found: " + id)))
                .flatMap(existing -> {
                    // Update fields by re-mapping
                    var updated = configurationMapper.toEntity(dto);
                    updated.setId(existing.getId());
                    updated.setCreatedAt(existing.getCreatedAt());
                    updated.setCreatedBy(existing.getCreatedBy());
                    updated.setUpdatedAt(Instant.now());
                    updated.setFailureCount(existing.getFailureCount());
                    updated.setLastSuccessAt(existing.getLastSuccessAt());
                    updated.setLastFailureAt(existing.getLastFailureAt());
                    
                    return configurationRepository.save(updated);
                })
                .map(configurationMapper::toDto)
                .doOnSuccess(updated -> log.info("Updated callback configuration: id={}, name={}", 
                        id, updated.getName()))
                .doOnError(error -> log.error("Error updating callback configuration: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> delete(UUID id) {
        return configurationRepository.deleteById(id)
                .doOnSuccess(v -> log.info("Deleted callback configuration: id={}", id))
                .doOnError(error -> log.error("Error deleting callback configuration: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<CallbackConfigurationDTO> findById(UUID id) {
        return configurationRepository.findById(id)
                .map(configurationMapper::toDto)
                .doOnError(error -> log.error("Error finding callback configuration: {}", id, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Flux<CallbackConfigurationDTO> findActiveByEventType(String eventType) {
        return configurationRepository.findActiveByEventType(eventType)
                .map(configurationMapper::toDto)
                .doOnError(error -> log.error("Error finding callback configurations for event type: {}",
                        eventType, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> recordSuccess(UUID configurationId) {
        return configurationRepository.findById(configurationId)
                .flatMap(config -> {
                    config.setLastSuccessAt(Instant.now());
                    config.setFailureCount(0); // Reset failure count on success
                    config.setUpdatedAt(Instant.now());
                    
                    // Reactivate if was paused
                    if (config.getStatus() == CallbackStatus.PAUSED) {
                        config.setStatus(CallbackStatus.ACTIVE);
                        log.info("Reactivated callback configuration after successful execution: id={}", 
                                configurationId);
                    }
                    
                    return configurationRepository.save(config);
                })
                .then()
                .doOnSuccess(v -> log.debug("Recorded success for callback configuration: {}", configurationId))
                .doOnError(error -> log.error("Error recording success for callback configuration: {}", 
                        configurationId, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Void> recordFailure(UUID configurationId) {
        return configurationRepository.findById(configurationId)
                .flatMap(config -> {
                    config.setLastFailureAt(Instant.now());
                    config.setFailureCount(config.getFailureCount() + 1);
                    config.setUpdatedAt(Instant.now());
                    
                    // Check if should pause
                    Integer threshold = config.getFailureThreshold();
                    if (threshold != null && config.getFailureCount() >= threshold) {
                        config.setStatus(CallbackStatus.PAUSED);
                        log.warn("Paused callback configuration due to failures: id={}, failures={}, threshold={}", 
                                configurationId, config.getFailureCount(), threshold);
                    }
                    
                    return configurationRepository.save(config);
                })
                .then()
                .doOnSuccess(v -> log.debug("Recorded failure for callback configuration: {}", configurationId))
                .doOnError(error -> log.error("Error recording failure for callback configuration: {}", 
                        configurationId, error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mono<Boolean> shouldPause(UUID configurationId) {
        return configurationRepository.findById(configurationId)
                .map(config -> {
                    Integer threshold = config.getFailureThreshold();
                    Integer failureCount = config.getFailureCount();
                    
                    if (threshold == null || failureCount == null) {
                        return false;
                    }
                    
                    return failureCount >= threshold;
                })
                .defaultIfEmpty(false)
                .doOnError(error -> log.error("Error checking if should pause callback configuration: {}", 
                        configurationId, error));
    }
}
