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

package org.fireflyframework.rules.models.repositories;

import org.fireflyframework.rules.models.entities.RuleDefinition;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Repository interface for RuleDefinition entities.
 * Provides reactive database operations for managing YAML DSL rule definitions.
 */
public interface RuleDefinitionRepository extends BaseRepository<RuleDefinition, UUID> {

    /**
     * Find a rule definition by its unique code.
     * 
     * @param code The unique code identifier
     * @return Mono containing the rule definition if found, empty otherwise
     */
    @Query("SELECT * FROM rule_definitions WHERE code = :code")
    Mono<RuleDefinition> findByCode(@Param("code") String code);

    /**
     * Find all active rule definitions.
     * 
     * @return Flux of active rule definitions
     */
    @Query("SELECT * FROM rule_definitions WHERE is_active = true ORDER BY created_at DESC")
    Flux<RuleDefinition> findAllActive();

    /**
     * Find rule definitions by tags (contains search).
     * 
     * @param tag The tag to search for
     * @return Flux of rule definitions containing the tag
     */
    @Query("SELECT * FROM rule_definitions WHERE tags LIKE CONCAT('%', :tag, '%') ORDER BY created_at DESC")
    Flux<RuleDefinition> findByTagsContaining(@Param("tag") String tag);

    /**
     * Find rule definitions by version.
     * 
     * @param version The version to search for
     * @return Flux of rule definitions with the specified version
     */
    @Query("SELECT * FROM rule_definitions WHERE version = :version ORDER BY created_at DESC")
    Flux<RuleDefinition> findByVersion(@Param("version") String version);

    /**
     * Find rule definitions created by a specific user.
     * 
     * @param createdBy The user who created the rule definitions
     * @return Flux of rule definitions created by the user
     */
    @Query("SELECT * FROM rule_definitions WHERE created_by = :createdBy ORDER BY created_at DESC")
    Flux<RuleDefinition> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Check if a rule definition with the given code already exists.
     * 
     * @param code The code to check
     * @return Mono<Boolean> true if exists, false otherwise
     */
    @Query("SELECT COUNT(*) > 0 FROM rule_definitions WHERE code = :code")
    Mono<Boolean> existsByCode(@Param("code") String code);

    /**
     * Find rule definitions by name (case-insensitive partial match).
     * 
     * @param name The name to search for
     * @return Flux of rule definitions matching the name
     */
    @Query("SELECT * FROM rule_definitions WHERE LOWER(name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY created_at DESC")
    Flux<RuleDefinition> findByNameContainingIgnoreCase(@Param("name") String name);
}
