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

import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import org.fireflyframework.callbacks.models.entity.CallbackConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for CallbackConfiguration entity and DTO conversion.
 */
@Mapper(componentModel = "spring", uses = JsonMapperHelper.class)
public interface CallbackConfigurationMapper {

    @Mapping(source = "customHeaders", target = "customHeaders", qualifiedByName = "jsonToMapString")
    @Mapping(source = "metadata", target = "metadata", qualifiedByName = "jsonToMapObject")
    CallbackConfigurationDTO toDto(CallbackConfiguration entity);

    @Mapping(source = "customHeaders", target = "customHeaders", qualifiedByName = "mapStringToJson")
    @Mapping(source = "metadata", target = "metadata", qualifiedByName = "mapObjectToJson")
    CallbackConfiguration toEntity(CallbackConfigurationDTO dto);
}
