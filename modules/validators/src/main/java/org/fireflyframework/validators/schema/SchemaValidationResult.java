package org.fireflyframework.validators.schema;

import java.util.List;

/** Vendor-neutral immutable validation outcome. */
public record SchemaValidationResult(boolean valid, List<SchemaValidationIssue> issues) {
    public SchemaValidationResult {
        issues = List.copyOf(issues);
        if (valid != issues.isEmpty()) throw new IllegalArgumentException("Validation outcome and issues disagree");
    }
}
