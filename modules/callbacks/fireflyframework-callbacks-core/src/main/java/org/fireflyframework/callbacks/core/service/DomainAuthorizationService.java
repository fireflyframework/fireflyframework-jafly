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

package org.fireflyframework.callbacks.core.service;

import org.fireflyframework.callbacks.core.filters.FilterRequest;
import org.fireflyframework.callbacks.core.filters.PaginationResponse;
import org.fireflyframework.callbacks.interfaces.dto.AuthorizedDomainDTO;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for managing authorized callback domains.
 * <p>
 * Only domains registered and authorized in this service can receive callbacks.
 * This provides a security layer to prevent callbacks to arbitrary URLs.
 */
public interface DomainAuthorizationService {

    /**
     * Filters authorized domains based on the given criteria.
     *
     * @param filterRequest the request object containing filtering criteria
     * @return a reactive {@code Mono} emitting a {@code PaginationResponse} containing the filtered list
     */
    Mono<PaginationResponse<AuthorizedDomainDTO>> filterDomains(FilterRequest<AuthorizedDomainDTO> filterRequest);

    /**
     * Checks if a URL is authorized for callbacks.
     * Used internally by the callback dispatcher.
     *
     * @param url the callback URL to validate
     * @return Mono emitting true if authorized, false otherwise
     */
    Mono<Boolean> isAuthorized(String url);

    /**
     * Creates a new authorized domain.
     *
     * @param dto the domain to create
     * @return Mono emitting the created domain
     */
    Mono<AuthorizedDomainDTO> create(AuthorizedDomainDTO dto);

    /**
     * Updates an existing authorized domain.
     *
     * @param id the domain ID
     * @param dto the updated domain
     * @return Mono emitting the updated domain
     */
    Mono<AuthorizedDomainDTO> update(UUID id, AuthorizedDomainDTO dto);

    /**
     * Deletes an authorized domain.
     *
     * @param id the domain ID
     * @param domain the domain name
     * @return Mono that completes when deleted
     */
    Mono<Void> delete(UUID id, String domain);

    /**
     * Verifies a domain.
     *
     * @param domain the domain name
     * @param verificationMethod the verification method used
     * @return Mono that completes when verified
     */
    Mono<Void> verifyDomain(String domain, String verificationMethod);

    /**
     * Records a callback execution for statistics.
     * Used internally by the callback dispatcher.
     *
     * @param url the callback URL
     * @param success whether the callback succeeded
     * @return Mono that completes when stats are updated
     */
    Mono<Void> recordCallback(String url, boolean success);
}
