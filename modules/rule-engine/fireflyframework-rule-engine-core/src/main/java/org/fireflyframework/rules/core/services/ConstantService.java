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

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing system constants.
 * Constants are predefined values that don't change during rule execution,
 * acting as a feature store for the rule engine.
 */
public interface ConstantService {
    
    /**
     * Filters the constants based on the given criteria.
     *
     * @param filterRequest the request object containing filtering criteria for ConstantDTO
     * @return a reactive {@code Mono} emitting a {@code PaginationResponse} containing the filtered list of constants
     */
    Mono<PaginationResponse<ConstantDTO>> filterConstants(FilterRequest<ConstantDTO> filterRequest);
    
    /**
     * Creates a new constant based on the provided information.
     *
     * @param constantDTO the DTO object containing details of the constant to be created
     * @return a Mono that emits the created ConstantDTO object
     */
    Mono<ConstantDTO> createConstant(ConstantDTO constantDTO);
    
    /**
     * Updates an existing constant with updated information.
     *
     * @param constantId the unique identifier of the constant to be updated
     * @param constantDTO the data transfer object containing the updated details of the constant
     * @return a reactive Mono containing the updated ConstantDTO
     */
    Mono<ConstantDTO> updateConstant(UUID constantId, ConstantDTO constantDTO);
    
    /**
     * Deletes a constant identified by its unique ID.
     *
     * @param constantId the unique identifier of the constant to be deleted
     * @return a Mono that completes when the constant is successfully deleted or errors if the deletion fails
     */
    Mono<Void> deleteConstant(UUID constantId);
    
    /**
     * Retrieves a constant by its unique identifier.
     *
     * @param constantId the unique identifier of the constant to retrieve
     * @return a Mono emitting the {@link ConstantDTO} representing the constant if found,
     *         or an empty Mono if the constant does not exist
     */
    Mono<ConstantDTO> getConstantById(UUID constantId);
    
    /**
     * Retrieves a constant by its unique code.
     *
     * @param code the unique code of the constant to retrieve
     * @return a Mono emitting the {@link ConstantDTO} representing the constant if found,
     *         or an empty Mono if the constant does not exist
     */
    Mono<ConstantDTO> getConstantByCode(String code);

    /**
     * Retrieves multiple constants by their codes.
     *
     * @param codes the list of unique codes of the constants to retrieve
     * @return a Flux emitting the {@link ConstantDTO} objects for constants that are found
     */
    Flux<ConstantDTO> getConstantsByCodes(List<String> codes);
}
