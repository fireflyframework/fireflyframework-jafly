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

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.NoRepositoryBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Base repository interface with common methods for all repositories.
 *
 * @param <T>  Entity type
 * @param <ID> ID type
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends R2dbcRepository<T, ID> {

    /**
     * Finds all entities with pagination support.
     *
     * @param pageable Pagination information
     * @return Flux of entities
     */
    Flux<T> findAllBy(Pageable pageable);

    /**
     * Counts all entities.
     *
     * @return Mono of count
     */
    Mono<Long> count();
}

