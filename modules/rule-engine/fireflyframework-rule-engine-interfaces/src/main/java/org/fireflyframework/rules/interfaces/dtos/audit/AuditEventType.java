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

package org.fireflyframework.rules.interfaces.dtos.audit;

/**
 * Enumeration of audit event types for the rule engine.
 * Defines the different types of operations that can be audited.
 */
public enum AuditEventType {
    
    // Rule Definition Operations
    RULE_DEFINITION_CREATE("RULE_DEFINITION", "Create rule definition"),
    RULE_DEFINITION_UPDATE("RULE_DEFINITION", "Update rule definition"),
    RULE_DEFINITION_DELETE("RULE_DEFINITION", "Delete rule definition"),
    RULE_DEFINITION_GET("RULE_DEFINITION", "Get rule definition"),
    RULE_DEFINITION_FILTER("RULE_DEFINITION", "Filter rule definitions"),
    RULE_DEFINITION_VALIDATE("RULE_DEFINITION", "Validate rule definition"),
    
    // Rule Evaluation Operations
    RULE_EVALUATION_DIRECT("RULE_EVALUATION", "Evaluate rules directly with base64 YAML"),
    RULE_EVALUATION_PLAIN("RULE_EVALUATION", "Evaluate rules directly with plain YAML"),
    RULE_EVALUATION_BY_CODE("RULE_EVALUATION", "Evaluate rules by stored rule code"),
    RULE_EVALUATION_BATCH("RULE_EVALUATION", "Evaluate multiple rules in batch operation"),
    
    // Constant Operations
    CONSTANT_CREATE("CONSTANT", "Create constant"),
    CONSTANT_UPDATE("CONSTANT", "Update constant"),
    CONSTANT_DELETE("CONSTANT", "Delete constant"),
    CONSTANT_GET("CONSTANT", "Get constant"),
    CONSTANT_FILTER("CONSTANT", "Filter constants"),
    
    // Validation Operations
    YAML_VALIDATION("VALIDATION", "Validate YAML DSL content");

    private final String entityType;
    private final String description;

    AuditEventType(String entityType, String description) {
        this.entityType = entityType;
        this.description = description;
    }

    /**
     * Get the entity type for this audit event
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Get the description for this audit event
     */
    public String getDescription() {
        return description;
    }

    /**
     * Check if this is a rule definition operation
     */
    public boolean isRuleDefinitionOperation() {
        return "RULE_DEFINITION".equals(entityType);
    }

    /**
     * Check if this is a rule evaluation operation
     */
    public boolean isRuleEvaluationOperation() {
        return "RULE_EVALUATION".equals(entityType);
    }

    /**
     * Check if this is a constant operation
     */
    public boolean isConstantOperation() {
        return "CONSTANT".equals(entityType);
    }

    /**
     * Check if this is a validation operation
     */
    public boolean isValidationOperation() {
        return "VALIDATION".equals(entityType);
    }

    /**
     * Check if this is a create operation
     */
    public boolean isCreateOperation() {
        return name().endsWith("_CREATE");
    }

    /**
     * Check if this is an update operation
     */
    public boolean isUpdateOperation() {
        return name().endsWith("_UPDATE");
    }

    /**
     * Check if this is a delete operation
     */
    public boolean isDeleteOperation() {
        return name().endsWith("_DELETE");
    }

    /**
     * Check if this is a read operation
     */
    public boolean isReadOperation() {
        return name().endsWith("_GET") || name().endsWith("_FILTER");
    }
}
