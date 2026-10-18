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
import org.fireflyframework.callbacks.core.filters.FilterUtils;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.core.mapper.CallbackExecutionMapper;
import org.fireflyframework.callbacks.interfaces.dto.CallbackExecutionDTO;
import org.fireflyframework.callbacks.interfaces.enums.CallbackExecutionStatus;
import org.fireflyframework.callbacks.models.entity.CallbackExecution;
import org.fireflyframework.callbacks.models.repository.CallbackExecutionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for callback execution history.
 */
@RestController
@RequestMapping("/api/v1/callback-executions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Callback Executions", description = "View callback execution history and status")
public class CallbackExecutionController {

    private final CallbackExecutionRepository executionRepository;
    private final CallbackExecutionMapper executionMapper;

    @PostMapping(value = "/filter", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter callback executions",
            description = "Filters and paginates callback executions based on the provided criteria"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved filtered executions",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<ResponseEntity<PaginationResponse<CallbackExecutionDTO>>> filterExecutions(
            @Valid @RequestBody FilterRequest<CallbackExecutionDTO> filterRequest) {
        log.debug("Filtering callback executions with request: {}", filterRequest);
        return FilterUtils
                .createFilter(CallbackExecution.class, executionMapper::toDto)
                .withRepository(executionRepository)
                .filter(filterRequest)
                .map(ResponseEntity::ok);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all executions", description = "Returns all callback executions")
    public Flux<CallbackExecutionDTO> findAll() {
        log.debug("Finding all callback executions");
        return executionRepository.findAll()
                .map(executionMapper::toDto);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get execution by ID", description = "Returns a specific callback execution")
    public Mono<CallbackExecutionDTO> findById(@PathVariable UUID id) {
        log.debug("Finding callback execution by id={}", id);
        return executionRepository.findById(id)
                .map(executionMapper::toDto);
    }

    @GetMapping(value = "/by-configuration/{configurationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List by configuration", description = "Returns executions for a specific configuration")
    public Flux<CallbackExecutionDTO> findByConfiguration(@PathVariable UUID configurationId) {
        log.debug("Finding callback executions for configuration: {}", configurationId);
        return executionRepository.findByConfigurationIdOrderByExecutedAtDesc(configurationId)
                .map(executionMapper::toDto);
    }

    @GetMapping(value = "/by-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List by status", description = "Returns executions with a specific status")
    public Flux<CallbackExecutionDTO> findByStatus(@RequestParam CallbackExecutionStatus status) {
        log.debug("Finding callback executions with status: {}", status);
        return executionRepository.findByStatus(status)
                .map(executionMapper::toDto);
    }

    @GetMapping(value = "/pending-retries", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List pending retries", description = "Returns executions pending retry")
    public Flux<CallbackExecutionDTO> findPendingRetries() {
        log.debug("Finding pending retry executions");
        return executionRepository.findPendingRetries(Instant.now())
                .map(executionMapper::toDto);
    }

    @GetMapping(value = "/recent", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List recent executions", description = "Returns recent executions for a configuration")
    public Flux<CallbackExecutionDTO> findRecent(
            @RequestParam UUID configurationId,
            @RequestParam(defaultValue = "PT24H") String duration) {
        log.debug("Finding recent executions for configuration: {}, duration: {}", configurationId, duration);
        Instant since = Instant.now().minus(java.time.Duration.parse(duration));
        return executionRepository.findRecentByConfigurationId(configurationId, since)
                .map(executionMapper::toDto);
    }
}
