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

package org.fireflyframework.rules.core.mappers;

import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import org.fireflyframework.rules.models.entities.Constant;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for converting between Constant entities and ConstantDTO objects.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ConstantMapper {

    /**
     * Converts a Constant entity to a ConstantDTO.
     *
     * @param constant the Constant entity to convert
     * @return the corresponding ConstantDTO
     */
    ConstantDTO toDTO(Constant constant);

    /**
     * Converts a ConstantDTO to a Constant entity.
     *
     * @param constantDTO the ConstantDTO to convert
     * @return the corresponding Constant entity
     */
    Constant toEntity(ConstantDTO constantDTO);
}
