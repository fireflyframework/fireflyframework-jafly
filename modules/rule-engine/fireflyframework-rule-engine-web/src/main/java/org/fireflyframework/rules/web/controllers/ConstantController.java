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

package org.fireflyframework.rules.web.controllers;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.services.ConstantService;
import org.fireflyframework.rules.interfaces.dtos.crud.ConstantDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for managing system constants.
 * Constants are predefined values that don't change during rule execution,
 * acting as a feature store for the rule engine.
 */
@RestController
@RequestMapping("/api/v1/constants")
@RequiredArgsConstructor
@Tag(name = "Constants", description = "System constants management API. Constants use UPPER_CASE_WITH_UNDERSCORES naming convention and are automatically loaded in YAML DSL rules.")
public class ConstantController {

    private final ConstantService constantService;

    @PostMapping("/filter")
    @Operation(summary = "Filter constants", description = "Retrieve constants based on filtering criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Constants retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter request",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    public Mono<ResponseEntity<PaginationResponse<ConstantDTO>>> filterConstants(
            @Valid @RequestBody FilterRequest<ConstantDTO> filterRequest) {
        return constantService.filterConstants(filterRequest)
                .map(response -> ResponseEntity.ok(response));
    }

    @PostMapping
    @Operation(summary = "Create a new constant",
               description = "Create a new system constant with UPPER_CASE_WITH_UNDERSCORES naming convention. " +
                           "Constants are automatically loaded in YAML DSL rules when referenced.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Constant created successfully",
                    content = @Content(schema = @Schema(implementation = ConstantDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid constant data or naming convention violation",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "Constant with the same code already exists",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    public Mono<ResponseEntity<ConstantDTO>> createConstant(
            @Parameter(description = "Constant to create with UPPER_CASE_WITH_UNDERSCORES code") @Valid @RequestBody ConstantDTO constantDTO) {
        return constantService.createConstant(constantDTO)
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @PutMapping("/{constantId}")
    @Operation(summary = "Update a constant", description = "Update an existing system constant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Constant updated successfully",
                    content = @Content(schema = @Schema(implementation = ConstantDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid constant data",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Constant not found",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    public Mono<ResponseEntity<ConstantDTO>> updateConstant(
            @Parameter(description = "Constant ID") @PathVariable UUID constantId,
            @Valid @RequestBody ConstantDTO constantDTO) {
        return constantService.updateConstant(constantId, constantDTO)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{constantId}")
    @Operation(summary = "Delete a constant", description = "Delete a system constant by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Constant deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Constant not found",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    public Mono<ResponseEntity<Void>> deleteConstant(
            @Parameter(description = "Constant ID") @PathVariable UUID constantId) {
        return constantService.deleteConstant(constantId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @GetMapping("/{constantId}")
    @Operation(summary = "Get constant by ID", description = "Retrieve a system constant by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Constant retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ConstantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Constant not found",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    public Mono<ResponseEntity<ConstantDTO>> getConstantById(
            @Parameter(description = "Constant ID") @PathVariable UUID constantId) {
        return constantService.getConstantById(constantId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get constant by code", description = "Retrieve a system constant by its unique code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Constant retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ConstantDTO.class))),
            @ApiResponse(responseCode = "404", description = "Constant not found",
                    content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content)
    })
    public Mono<ResponseEntity<ConstantDTO>> getConstantByCode(
            @Parameter(description = "Constant code") @PathVariable String code) {
        return constantService.getConstantByCode(code)
                .map(ResponseEntity::ok);
    }
}
