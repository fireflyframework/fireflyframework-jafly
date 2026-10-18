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
import org.fireflyframework.callbacks.core.service.DomainAuthorizationService;
import org.fireflyframework.callbacks.interfaces.dto.AuthorizedDomainDTO;
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
 * REST controller for authorized domain management.
 */
@RestController
@RequestMapping("/api/v1/authorized-domains")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authorized Domains", description = "Manage authorized callback domains")
public class AuthorizedDomainController {

    private final DomainAuthorizationService domainAuthorizationService;

    @PostMapping(value = "/filter", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter authorized domains",
            description = "Filters and paginates authorized domains based on the provided criteria"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved filtered domains",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<ResponseEntity<PaginationResponse<AuthorizedDomainDTO>>> filterDomains(
            @Valid @RequestBody FilterRequest<AuthorizedDomainDTO> filterRequest) {
        log.debug("Filtering authorized domains with request: {}", filterRequest);
        return domainAuthorizationService.filterDomains(filterRequest)
                .map(ResponseEntity::ok);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create authorized domain", description = "Authorizes a new domain for callbacks")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Domain created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<AuthorizedDomainDTO> create(@Valid @RequestBody AuthorizedDomainDTO dto) {
        log.info("Creating authorized domain: domain={}", dto.getDomain());
        return domainAuthorizationService.create(dto);
    }

    @PutMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update domain", description = "Updates an authorized domain")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domain updated successfully"),
            @ApiResponse(responseCode = "404", description = "Domain not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<AuthorizedDomainDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody AuthorizedDomainDTO dto) {
        log.info("Updating authorized domain: id={}", id);
        return domainAuthorizationService.update(id, dto);
    }

    @PostMapping(value = "/{domain}/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verify domain", description = "Marks a domain as verified")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Domain verified successfully"),
            @ApiResponse(responseCode = "404", description = "Domain not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<Void> verify(
            @PathVariable String domain,
            @RequestParam String verificationMethod) {
        log.info("Verifying domain: domain={}, method={}", domain, verificationMethod);
        return domainAuthorizationService.verifyDomain(domain, verificationMethod);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete domain", description = "Deletes an authorized domain")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Domain deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Domain not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public Mono<Void> delete(
            @PathVariable UUID id,
            @RequestParam String domain) {
        log.info("Deleting authorized domain: id={}, domain={}", id, domain);
        return domainAuthorizationService.delete(id, domain);
    }
}
