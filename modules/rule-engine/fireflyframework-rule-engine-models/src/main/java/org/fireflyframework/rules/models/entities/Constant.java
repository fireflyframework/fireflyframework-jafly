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

import org.fireflyframework.rules.interfaces.enums.ValueType;
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
 * Entity representing a system constant that can be used in rule expressions.
 * Constants are predefined values that don't change during rule execution,
 * acting as a feature store for the rule engine.
 * 
 * Examples: MINIMUM_CREDIT_SCORE = 650, MAX_LOAN_AMOUNT = 1000000
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("constants")
public class Constant {

    /**
     * UUID primary key
     */
    @Id
    private UUID id;

    /**
     * Unique constant code (e.g., MINIMUM_CREDIT_SCORE, MAX_LOAN_AMOUNT)
     */
    @Column("code")
    private String code;

    /**
     * Human-readable constant name
     */
    @Column("name")
    private String name;

    /**
     * Data type (STRING, NUMBER, BOOLEAN, DATE, OBJECT)
     */
    @Column("value_type")
    private ValueType valueType;

    /**
     * Whether constant must have value for evaluation
     */
    @Column("required")
    private Boolean required;

    /**
     * Optional constant description
     */
    @Column("description")
    private String description;

    /**
     * Current value of the constant (stored as JSON for flexibility)
     */
    @Column("current_value")
    private Object currentValue;

    /**
     * Creation timestamp
     */
    @CreatedDate
    @Column("created_at")
    private OffsetDateTime createdAt;

    /**
     * Last update timestamp
     */
    @LastModifiedDate
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
