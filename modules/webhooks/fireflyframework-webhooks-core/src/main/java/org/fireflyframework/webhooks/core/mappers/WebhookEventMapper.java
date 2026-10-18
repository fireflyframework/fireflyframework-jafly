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

package org.fireflyframework.webhooks.core.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.webhooks.core.domain.WebhookMetadata;
import org.fireflyframework.webhooks.core.domain.events.WebhookReceivedEvent;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * MapStruct mapper for converting between webhook DTOs and domain events.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class WebhookEventMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Converts a WebhookEventDto to a WebhookReceivedEvent.
     *
     * @param dto the DTO
     * @return the domain event
     */
    public abstract WebhookReceivedEvent toDomainEvent(WebhookEventDTO dto);

    /**
     * Converts a WebhookReceivedEvent to a WebhookEventDto.
     *
     * @param event the domain event
     * @return the DTO
     */
    public abstract WebhookEventDTO toDto(WebhookReceivedEvent event);

    /**
     * Converts WebhookMetadata to Map for DTO.
     *
     * @param metadata the metadata object
     * @return map representation
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> metadataToMap(WebhookMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return objectMapper.convertValue(metadata, Map.class);
    }

    /**
     * Converts Map to WebhookMetadata from DTO.
     *
     * @param map the map representation
     * @return metadata object
     */
    protected WebhookMetadata mapToMetadata(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return objectMapper.convertValue(map, WebhookMetadata.class);
    }
}
