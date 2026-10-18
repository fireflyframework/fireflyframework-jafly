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

package org.fireflyframework.callbacks.web.controller;

import org.fireflyframework.callbacks.core.filters.FilterRequest;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.core.service.CallbackConfigurationService;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for callback configuration management.
 */
@RestController
@RequestMapping("/api/v1/callback-configurations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Callback Configurations", description = "Manage callback configurations for webhook dispatching")
public class CallbackConfigurationController {

    private final CallbackConfigurationService configurationService;

    @PostMapping(value = "/filter", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter callback configurations",
            description = "Filters and paginates callback configurations based on the provided criteria"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved filtered configurations",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<ResponseEntity<PaginationResponse<CallbackConfigurationDTO>>> filterConfigurations(
            @Valid @RequestBody FilterRequest<CallbackConfigurationDTO> filterRequest) {
        log.debug("Filtering callback configurations with request: {}", filterRequest);
        return configurationService.filterConfigurations(filterRequest)
                .map(ResponseEntity::ok);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create callback configuration", description = "Creates a new callback configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Configuration created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<CallbackConfigurationDTO> create(@Valid @RequestBody CallbackConfigurationDTO dto) {
        log.info("Creating callback configuration: name={}, url={}", dto.getName(), dto.getUrl());
        return configurationService.create(dto);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get configuration by ID", description = "Returns a specific callback configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration found"),
            @ApiResponse(responseCode = "404", description = "Configuration not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<CallbackConfigurationDTO> findById(@PathVariable UUID id) {
        log.debug("Finding callback configuration by id={}", id);
        return configurationService.findById(id);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update configuration", description = "Updates an existing callback configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration updated successfully"),
            @ApiResponse(responseCode = "404", description = "Configuration not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<CallbackConfigurationDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody CallbackConfigurationDTO dto) {
        log.info("Updating callback configuration: id={}", id);
        return configurationService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete configuration", description = "Deletes a callback configuration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Configuration deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Configuration not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<Void> delete(@PathVariable UUID id) {
        log.info("Deleting callback configuration: id={}", id);
        return configurationService.delete(id);
    }
}
