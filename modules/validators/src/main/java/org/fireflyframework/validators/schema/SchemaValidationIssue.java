package org.fireflyframework.validators.schema;

/** Sanitized issue with no schema or instance values. */
public record SchemaValidationIssue(String code, String instanceLocation, String message) {
}
