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

import org.fireflyframework.core.queries.PaginationResponse;
import org.fireflyframework.rules.core.services.AuditTrailService;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailDTO;
import org.fireflyframework.rules.interfaces.dtos.audit.AuditTrailFilterDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for audit trail operations.
 * Provides endpoints for querying audit trail records with filtering and pagination.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Trails", description = "Operations for querying audit trail records")
public class AuditTrailController {

    private final AuditTrailService auditTrailService;

    @Operation(summary = "Get audit trails with filtering and pagination",
               description = "Retrieve audit trail records with optional filtering by operation type, " +
                           "entity type, user, date range, and other criteria. Supports pagination and sorting.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit trails retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter criteria"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/trails")
    public Mono<ResponseEntity<PaginationResponse<AuditTrailDTO>>> getAuditTrails(
            @Parameter(description = "Filter criteria and pagination parameters", required = true)
            @Valid @RequestBody AuditTrailFilterDTO filterDTO) {
        
        log.info("Getting audit trails with filter: {}", filterDTO);
        
        return auditTrailService.getAuditTrails(filterDTO)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Audit trails retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving audit trails", error));
    }

    @Operation(summary = "Get audit trail by ID",
               description = "Retrieve a specific audit trail record by its unique identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Audit trail not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/trails/{id}")
    public Mono<ResponseEntity<AuditTrailDTO>> getAuditTrailById(
            @Parameter(description = "Audit trail ID", required = true)
            @PathVariable UUID id) {
        
        log.info("Getting audit trail by ID: {}", id);
        
        return auditTrailService.getAuditTrailById(id)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .doOnSuccess(result -> log.info("Audit trail retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving audit trail", error));
    }

    @Operation(summary = "Get recent audit trails for entity",
               description = "Retrieve recent audit trail records for a specific entity (e.g., rule definition).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recent audit trails retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid entity ID or limit"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/trails/entity/{entityId}")
    public Mono<ResponseEntity<List<AuditTrailDTO>>> getRecentAuditTrailsForEntity(
            @Parameter(description = "Entity ID", required = true)
            @PathVariable UUID entityId,
            @Parameter(description = "Maximum number of records to return", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Getting recent audit trails for entity: {} with limit: {}", entityId, limit);
        
        return auditTrailService.getRecentAuditTrailsForEntity(entityId, limit)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Recent audit trails retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving recent audit trails", error));
    }

    @Operation(summary = "Get audit trail statistics",
               description = "Retrieve statistical information about audit trails including counts by operation type, " +
                           "success rates, and other metrics.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit trail statistics retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/statistics")
    public Mono<ResponseEntity<Map<String, Object>>> getAuditTrailStatistics() {
        
        log.info("Getting audit trail statistics");
        
        return auditTrailService.getAuditTrailStatistics()
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Audit trail statistics retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving audit trail statistics", error));
    }

    @Operation(summary = "Delete old audit trails",
               description = "Delete audit trail records older than the specified retention period. " +
                           "This is typically used for maintenance and compliance with data retention policies.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Old audit trails deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid retention days parameter"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/trails/cleanup")
    public Mono<ResponseEntity<Map<String, Object>>> deleteOldAuditTrails(
            @Parameter(description = "Number of days to retain audit trails", example = "90")
            @RequestParam int retentionDays) {
        
        log.info("Deleting audit trails older than {} days", retentionDays);
        
        if (retentionDays <= 0) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "Retention days must be greater than 0")));
        }
        
        return auditTrailService.deleteOldAuditTrails(retentionDays)
                .map(deletedCount -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Old audit trails deleted successfully");
                    response.put("deletedCount", deletedCount);
                    response.put("retentionDays", retentionDays);
                    return ResponseEntity.ok(response);
                })
                .doOnSuccess(result -> log.info("Old audit trails deleted successfully"))
                .doOnError(error -> log.error("Error deleting old audit trails", error));
    }

    @Operation(summary = "Get audit trails by operation type",
               description = "Retrieve audit trail records filtered by a specific operation type.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit trails retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid operation type"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/trails/operation/{operationType}")
    public Mono<ResponseEntity<PaginationResponse<AuditTrailDTO>>> getAuditTrailsByOperationType(
            @Parameter(description = "Operation type", required = true,
                      example = "RULE_DEFINITION_CREATE")
            @PathVariable String operationType,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting audit trails by operation type: {} (page: {}, size: {})", 
                operationType, page, size);
        
        AuditTrailFilterDTO filterDTO = AuditTrailFilterDTO.builder()
                .operationType(operationType)
                .page(page)
                .size(size)
                .build();
        
        return auditTrailService.getAuditTrails(filterDTO)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Audit trails by operation type retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving audit trails by operation type", error));
    }

    @Operation(summary = "Get audit trails by user",
               description = "Retrieve audit trail records filtered by a specific user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Audit trails retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/trails/user/{userId}")
    public Mono<ResponseEntity<PaginationResponse<AuditTrailDTO>>> getAuditTrailsByUser(
            @Parameter(description = "User ID", required = true, example = "john.doe@company.com")
            @PathVariable String userId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting audit trails by user: {} (page: {}, size: {})", userId, page, size);
        
        AuditTrailFilterDTO filterDTO = AuditTrailFilterDTO.builder()
                .userId(userId)
                .page(page)
                .size(size)
                .build();
        
        return auditTrailService.getAuditTrails(filterDTO)
                .map(ResponseEntity::ok)
                .doOnSuccess(result -> log.info("Audit trails by user retrieved successfully"))
                .doOnError(error -> log.error("Error retrieving audit trails by user", error));
    }
}
