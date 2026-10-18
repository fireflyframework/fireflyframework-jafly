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

import org.fireflyframework.callbacks.interfaces.dto.CallbackExecutionDTO;
import org.fireflyframework.callbacks.models.entity.CallbackExecution;
import org.mapstruct.Mapper;

/**
 * Mapper for CallbackExecution entity and DTO conversion.
 */
@Mapper(componentModel = "spring", uses = JsonMapperHelper.class)
public interface CallbackExecutionMapper {

    @org.mapstruct.Mapping(source = "requestPayload", target = "requestPayload", qualifiedByName = "jsonToMapObject")
    @org.mapstruct.Mapping(source = "requestHeaders", target = "requestHeaders", qualifiedByName = "jsonToMapString")
    @org.mapstruct.Mapping(source = "responseHeaders", target = "responseHeaders", qualifiedByName = "jsonToMapString")
    @org.mapstruct.Mapping(source = "metadata", target = "metadata", qualifiedByName = "jsonToMapObject")
    CallbackExecutionDTO toDto(CallbackExecution entity);

    @org.mapstruct.Mapping(source = "requestPayload", target = "requestPayload", qualifiedByName = "mapObjectToJson")
    @org.mapstruct.Mapping(source = "requestHeaders", target = "requestHeaders", qualifiedByName = "mapStringToJson")
    @org.mapstruct.Mapping(source = "responseHeaders", target = "responseHeaders", qualifiedByName = "mapStringToJson")
    @org.mapstruct.Mapping(source = "metadata", target = "metadata", qualifiedByName = "mapObjectToJson")
    CallbackExecution toEntity(CallbackExecutionDTO dto);
}
