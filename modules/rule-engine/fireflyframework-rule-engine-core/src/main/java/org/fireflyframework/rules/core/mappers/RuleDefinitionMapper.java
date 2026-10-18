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

import org.fireflyframework.rules.interfaces.dtos.crud.RuleDefinitionDTO;
import org.fireflyframework.rules.models.entities.RuleDefinition;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between RuleDefinition entity and DTO.
 */
@Component
public class RuleDefinitionMapper {

    /**
     * Convert RuleDefinition entity to DTO.
     * 
     * @param entity The entity to convert
     * @return The corresponding DTO
     */
    public RuleDefinitionDTO toDTO(RuleDefinition entity) {
        if (entity == null) {
            return null;
        }

        return RuleDefinitionDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .yamlContent(entity.getYamlContent())
                .version(entity.getVersion())
                .isActive(entity.getIsActive())
                .tags(entity.getTags())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Convert RuleDefinitionDTO to entity.
     * 
     * @param dto The DTO to convert
     * @return The corresponding entity
     */
    public RuleDefinition toEntity(RuleDefinitionDTO dto) {
        if (dto == null) {
            return null;
        }

        return RuleDefinition.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .yamlContent(dto.getYamlContent())
                .version(dto.getVersion())
                .isActive(dto.getIsActive())
                .tags(dto.getTags())
                .createdBy(dto.getCreatedBy())
                .updatedBy(dto.getUpdatedBy())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
