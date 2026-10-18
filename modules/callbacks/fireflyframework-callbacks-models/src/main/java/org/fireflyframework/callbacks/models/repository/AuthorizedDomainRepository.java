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

package org.fireflyframework.callbacks.models.repository;

import org.fireflyframework.callbacks.models.entity.AuthorizedDomain;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository for authorized domains.
 */
@Repository
public interface AuthorizedDomainRepository extends BaseRepository<AuthorizedDomain, UUID> {

    /**
     * Finds a domain by name.
     */
    Mono<AuthorizedDomain> findByDomain(String domain);

    /**
     * Finds all active and verified domains.
     */
    @Query("SELECT * FROM authorized_domains WHERE active = true AND verified = true")
    Flux<AuthorizedDomain> findActiveAndVerified();

    /**
     * Finds domains by tenant ID.
     */
    Flux<AuthorizedDomain> findByTenantId(String tenantId);

    /**
     * Finds domains that need verification.
     */
    @Query("SELECT * FROM authorized_domains WHERE verified = false")
    Flux<AuthorizedDomain> findUnverified();
}
