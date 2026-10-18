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

package org.fireflyframework.callbacks.core.mapper;

import org.fireflyframework.callbacks.interfaces.dto.EventSubscriptionDTO;
import org.fireflyframework.callbacks.models.entity.EventSubscription;
import org.mapstruct.Mapper;

/**
 * Mapper for EventSubscription entity and DTO conversion.
 */
@Mapper(componentModel = "spring", uses = JsonMapperHelper.class)
public interface EventSubscriptionMapper {

    @org.mapstruct.Mapping(source = "connectionConfig", target = "connectionConfig", qualifiedByName = "jsonToMapString")
    @org.mapstruct.Mapping(source = "consumerProperties", target = "consumerProperties", qualifiedByName = "jsonToMapString")
    @org.mapstruct.Mapping(source = "metadata", target = "metadata", qualifiedByName = "jsonToMapObject")
    EventSubscriptionDTO toDto(EventSubscription entity);

    @org.mapstruct.Mapping(source = "connectionConfig", target = "connectionConfig", qualifiedByName = "mapStringToJson")
    @org.mapstruct.Mapping(source = "consumerProperties", target = "consumerProperties", qualifiedByName = "mapStringToJson")
    @org.mapstruct.Mapping(source = "metadata", target = "metadata", qualifiedByName = "mapObjectToJson")
    EventSubscription toEntity(EventSubscriptionDTO dto);
}
