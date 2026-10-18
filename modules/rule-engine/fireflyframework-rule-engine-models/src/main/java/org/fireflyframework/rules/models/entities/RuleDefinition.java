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

package org.fireflyframework.rules.models.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a stored YAML DSL rule definition.
 * Rule definitions are validated before storage and can be referenced
 * by UUID for rule evaluation.
 * 
 * The YAML content is stored as validated DSL that follows naming conventions
 * and passes all static analysis checks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("rule_definitions")
public class RuleDefinition {

    /**
     * UUID primary key
     */
    @Id
    @Column("id")
    private UUID id;

    /**
     * Unique code identifier for the rule definition.
     * Must follow naming convention: alphanumeric with underscores, starting with letter.
     * Examples: "credit_scoring_v1", "fraud_detection_basic"
     */
    @Column("code")
    private String code;

    /**
     * Human-readable name for the rule definition
     */
    @Column("name")
    private String name;

    /**
     * Detailed description of what this rule definition does
     */
    @Column("description")
    private String description;

    /**
     * The YAML DSL content as a string.
     * This content has been validated before storage to ensure:
     * - Valid YAML syntax
     * - Correct DSL structure
     * - Proper naming conventions
     * - No dependency issues
     * - Performance optimizations
     */
    @Column("yaml_content")
    private String yamlContent;

    /**
     * Version of the rule definition for tracking changes
     */
    @Column("version")
    private String version;

    /**
     * Whether this rule definition is currently active and can be used for evaluation
     */
    @Column("is_active")
    private Boolean isActive;

    /**
     * Tags for categorizing and searching rule definitions
     */
    @Column("tags")
    private String tags;

    /**
     * User who created this rule definition
     */
    @Column("created_by")
    private String createdBy;

    /**
     * User who last modified this rule definition
     */
    @Column("updated_by")
    private String updatedBy;

    /**
     * Timestamp when the rule definition was created
     */
    @CreatedDate
    @Column("created_at")
    private OffsetDateTime createdAt;

    /**
     * Timestamp when the rule definition was last modified
     */
    @LastModifiedDate
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
