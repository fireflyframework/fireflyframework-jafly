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

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.mappers.ConstantMapper;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.core.utils.JsonLogger;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.models.entities.Constant;
import org.fireflyframework.rules.models.repositories.ConstantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ConstantServiceImpl implements ConstantService {

    private static final Logger log = LoggerFactory.getLogger(ConstantServiceImpl.class);

    @Autowired
    private ConstantRepository repository;

    @Autowired
    private ConstantMapper mapper;

    @Override
    public Mono<PaginationResponse<ConstantDTO>> filterConstants(FilterRequest<ConstantDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Constant.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ConstantDTO> createConstant(ConstantDTO constantDTO) {
        return Mono.just(constantDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ConstantDTO> updateConstant(UUID constantId, ConstantDTO constantDTO) {
        return repository.findById(constantId)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with ID: " + constantId)))
                .flatMap(existingConstant -> {
                    // Set the ID to ensure we're updating the correct entity
                    constantDTO.setId(constantId);
                    Constant updatedEntity = mapper.toEntity(constantDTO);
                    // Preserve audit fields
                    updatedEntity.setCreatedAt(existingConstant.getCreatedAt());
                    return repository.save(updatedEntity);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteConstant(UUID constantId) {
        return repository.findById(constantId)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with ID: " + constantId)))
                .flatMap(constant -> repository.deleteById(constantId));
    }

    @Override
    public Mono<ConstantDTO> getConstantById(UUID constantId) {
        return repository.findById(constantId)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with ID: " + constantId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ConstantDTO> getConstantByCode(String code) {
        return repository.findByCode(code)
                .switchIfEmpty(Mono.error(new RuntimeException("Constant not found with code: " + code)))
                .map(mapper::toDTO);
    }

    @Override
    public Flux<ConstantDTO> getConstantsByCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(codes)
                .flatMap(code -> repository.findByCode(code)
                        .map(mapper::toDTO)
                        .switchIfEmpty(Mono.empty()) // Don't fail here, let the caller handle missing constants
                        .onErrorResume(error -> {
                            // Log the error but don't fail - let the caller handle it
                            JsonLogger.error(log, "Error loading constant '" + code + "'", error);
                            return Mono.empty();
                        }));
    }
}
