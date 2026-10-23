package org.fireflyframework.validators.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineJsonSchemaValidatorTest {
    private final OfflineJsonSchemaValidator validator = new OfflineJsonSchemaValidator();
    private static final String PREFIX = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",";

    @Test void validatesTheSchemaAgainstTheBundledMetaschema() {
        assertThat(validator.validateSchema(PREFIX + "\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}").valid()).isTrue();
        assertThat(validator.validateSchema(PREFIX + "\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"invented\"}}}").valid()).isFalse();
        assertThat(validator.validateSchema(PREFIX + "\"required\":\"name\"}").valid()).isFalse();
        assertThat(validator.validateSchema(PREFIX + "\"minLength\":-1}").valid()).isFalse();
    }

    @Test void localNonrecursiveReferencesActuallyValidateInstanceData() {
        String schema = PREFIX + "\"type\":\"object\",\"$defs\":{\"positive\":{\"type\":\"integer\",\"minimum\":1}},"
                + "\"properties\":{\"amount\":{\"$ref\":\"#/$defs/positive\"}},\"required\":[\"amount\"]}";
        assertThat(validator.validateInstance(schema, "{\"amount\":2}").valid()).isTrue();
        assertThat(validator.validateInstance(schema, "{\"amount\":0}").valid()).isFalse();
        assertThat(validator.validateInstance(schema, "{\"amount\":\"invented\"}").valid()).isFalse();
    }

    @Test void unavailableReferencesAndUnsupportedDialectsFailClosed() {
        for (String schema : java.util.List.of(
                "{\"$schema\":\"http://json-schema.org/draft-07/schema#\"}",
                PREFIX + "\"$ref\":\"#/$defs/missing\"}",
                PREFIX + "\"$id\":\"https://example.invalid/other\"}",
                PREFIX + "\"$dynamicRef\":\"#name\"}",
                PREFIX + "\"$ref\":\"#\"}")) {
            assertThat(validator.validateSchema(schema).valid()).as(schema).isFalse();
        }
    }

    @Test void deniesEveryExternalRetrievalWithoutContactingTheSuppliedServer() throws Exception {
        try (var socket = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            socket.setSoTimeout(100);
            for (String uri : java.util.List.of("http://127.0.0.1:" + socket.getLocalPort() + "/schema", "file:///etc/passwd",
                    "classpath:application.yml", "https://json-schema.org/draft/2020-12/schema")) {
                assertThat(validator.validateSchema(PREFIX + "\"$ref\":\"" + uri + "\"}").valid()).isFalse();
            }
            org.assertj.core.api.Assertions.assertThatThrownBy(socket::accept).isInstanceOf(java.net.SocketTimeoutException.class);
        }
    }

    @Test void parserRejectsDuplicatesTrailingTokensAndBoundedInputs() {
        assertThat(validator.validateSchema(PREFIX + "\"type\":\"object\",\"type\":\"string\"}").valid()).isFalse();
        assertThat(validator.validateSchema(PREFIX + "\"type\":\"object\"} {}").valid()).isFalse();
        assertThat(validator.validateSchema(PREFIX + "\"description\":\"" + "x".repeat(70_000) + "\"}").valid()).isFalse();
        assertThat(validator.validateInstance(PREFIX + "\"type\":\"object\"}", "[".repeat(40) + "0" + "]".repeat(40)).valid()).isFalse();
        assertThat(validator.validateInstance(PREFIX + "\"type\":\"number\"}", "1".repeat(200)).valid()).isFalse();
    }

    @Test void integerAndDecimalConstraintsRetainExactPrecision() {
        assertThat(validator.validateInstance(PREFIX + "\"minimum\":9007199254740993}", "9007199254740992").valid()).isFalse();
        assertThat(validator.validateInstance(PREFIX + "\"minimum\":9007199254740993}", "9007199254740993").valid()).isTrue();
        assertThat(validator.validateInstance(PREFIX + "\"multipleOf\":0.000000000000000003}", "0.123456789012345678").valid()).isTrue();
        assertThat(validator.validateInstance(PREFIX + "\"multipleOf\":0.000000000000000003}", "0.123456789012345679").valid()).isFalse();
    }

    @Test void dateFormatAssertionsAreEnabledAndErrorsDoNotDiscloseValues() {
        var result = validator.validateInstance(PREFIX + "\"type\":\"string\",\"format\":\"date\"}", "\"2026-02-30\"");
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).allSatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("INSTANCE_CONSTRAINT");
            assertThat(issue.message()).doesNotContain("2026-02-30");
        });
        assertThat(validator.validateInstance(PREFIX + "\"type\":\"string\",\"format\":\"date\"}", "\"2026-02-28\"").valid()).isTrue();
    }

    @Test void localPointersPreserveEscapedAndEncodedPropertyNames() {
        String schema = PREFIX + "\"$defs\":{\"a/b~c\":{\"type\":\"integer\"}},\"$ref\":\"#%2F$defs%2Fa~1b~0c\"}";
        assertThat(validator.validateInstance(schema, "123").valid()).isTrue();
        assertThat(validator.validateInstance(schema, "\"123\"").valid()).isFalse();
        assertThat(validator.validateSchema(PREFIX + "\"$ref\":\"#%2F..%2Ffile:%2F%2Fetc%2Fpasswd\"}").valid()).isFalse();
    }

    @Test void configurableNodeReferenceAndUtf8ByteLimitsActuallyRejectOverages() {
        var small = new OfflineJsonSchemaValidator(new OfflineJsonSchemaValidator.Limits(150, 100, 8, 100, 20, 20, 1));
        assertThat(small.validateSchema(PREFIX + "\"description\":\"" + "é".repeat(45) + "\"}").valid()).isFalse();
        assertThat(small.validateInstance(PREFIX + "\"type\":\"array\"}", "[" + "1,".repeat(20) + "1]").valid()).isFalse();
        var refs = new OfflineJsonSchemaValidator(new OfflineJsonSchemaValidator.Limits(1024, 100, 8, 100, 20, 100, 1));
        assertThat(refs.validateSchema(PREFIX + "\"$defs\":{\"x\":{}},\"allOf\":[{\"$ref\":\"#/$defs/x\"},{\"$ref\":\"#/$defs/x\"}]}").valid()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new OfflineJsonSchemaValidator.Limits(0,1,1,1,1,1,1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void closedProfileDistinguishesUnsupportedSchemasFromInvalidSchemas() {
        assertThat(validator.validateSchema("true").issues()).extracting(SchemaValidationIssue::code).containsOnly("UNSUPPORTED_SCHEMA");
        assertThat(validator.validateSchema(PREFIX + "\"type\":13}").issues()).extracting(SchemaValidationIssue::code).containsOnly("INVALID_SCHEMA");
        assertThat(validator.validateSchema(PREFIX + "\"unknownApplicationAnnotation\":\"permitted annotation\"}").valid()).isTrue();
        assertThat(validator.validateSchema(PREFIX + "\"const\":{\"$ref\":\"https://example.invalid/data\"}}").issues())
                .extracting(SchemaValidationIssue::code).containsOnly("UNSUPPORTED_SCHEMA");
    }

    @Test void normalizationNeverRewritesConstOrEnumConstraintData() {
        String schema = PREFIX + "\"$defs\":{\"x\":{}},\"const\":{\"$ref\":\"#%2F$defs%2Fx\"}}";
        assertThat(validator.validateInstance(schema, "{\"$ref\":\"#%2F$defs%2Fx\"}").valid()).isTrue();
        assertThat(validator.validateInstance(schema, "{\"$ref\":\"#/$defs/x\"}").valid()).isFalse();
        String enumeration = PREFIX + "\"$defs\":{\"x\":{}},\"enum\":[{\"$ref\":\"#%2F$defs%2Fx\"}]}";
        assertThat(validator.validateInstance(enumeration, "{\"$ref\":\"#%2F$defs%2Fx\"}").valid()).isTrue();
    }

    @Test void blankInputIsNeverAnInstanceEvenForAnUnconstrainedSchema() {
        for (String input : java.util.List.of("", " ", "\n\t")) {
            var result = validator.validateInstance(PREFIX + "\"description\":\"Any valid JSON value\"}", input);
            assertThat(result.valid()).isFalse();
            assertThat(result.issues()).extracting(SchemaValidationIssue::code).containsOnly("INVALID_INSTANCE");
        }
    }

    @Test void referencesCannotExecuteLiteralOrUnknownAnnotationObjectsAsSchemas() {
        for (String schema : java.util.List.of(PREFIX + "\"const\":{\"type\":\"string\"},\"$ref\":\"#/const\"}",
                PREFIX + "\"applicationAnnotation\":{\"type\":\"string\"},\"$ref\":\"#/applicationAnnotation\"}")) {
            assertThat(validator.validateSchema(schema).issues()).extracting(SchemaValidationIssue::code).containsOnly("UNSUPPORTED_SCHEMA");
        }
        assertThat(validator.validateSchema(" ").issues()).extracting(SchemaValidationIssue::code).containsOnly("INVALID_SCHEMA");
    }

    @Test void recursiveReferenceGraphsAreRejectedBeforeExecution() {
        String schema = PREFIX + "\"$defs\":{\"a\":{\"$ref\":\"#/$defs/b\"},\"b\":{\"$ref\":\"#/$defs/a\"}},\"$ref\":\"#/$defs/a\"}";
        assertThat(validator.validateSchema(schema).valid()).isFalse();
    }
}
