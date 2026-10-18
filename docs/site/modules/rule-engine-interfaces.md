![Jafly. Rule Engine Interfaces](../assets/modules/rule-engine-interfaces.svg)

# Rule Engine Interfaces

Share request/response DTOs, rule definitions, validation results, and value/result enums for the rule engine.

Choose the request DTO for plain YAML, encoded YAML, or a stored rule code. ValidationResult carries structured diagnostics; do not reduce it to a successful HTTP status when presenting rule validation to an author.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-rule-engine-interfaces` | `jar` | [modules/rule-engine/fireflyframework-rule-engine-interfaces](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-interfaces) |

Part of [Business operations](index.md#business-operations).

## In the onboarding service

Choose the request DTO for plain YAML, encoded YAML, or a stored onboarding rule code. ValidationResult carries authoring diagnostics separately from the business result.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-interfaces</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Construct a request matching the evaluation mode. For plain YAML supplied by an application, use the actual builder:

```java
PlainYamlEvaluationRequestDTO request = PlainYamlEvaluationRequestDTO.builder()
        .yamlContent(validatedYaml)
        .inputData(Map.of("creditScore", 780))
        .includeDetails(false)
        .build();
```

The type is in `org.fireflyframework.rules.interfaces.dtos.evaluation`. `validatedYaml` must contain a rule in the supported YAML DSL; use the [platform example](rule-engine.md) and validate it before evaluation. The source constraints require non-blank YAML of 10-50,000 characters and a non-empty input map; input variable names use the dedicated naming validator. Stored-rule evaluation instead uses `RuleEvaluationByCodeRequestDTO`. Read `ValidationResult` diagnostics when returning authoring feedback.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`PlainYamlEvaluationRequestDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/main/java/org/fireflyframework/rules/interfaces/dtos/evaluation/PlainYamlEvaluationRequestDTO.java) | Request DTO for evaluating plain YAML rules (not base64 encoded). |
| [`RuleEvaluationByCodeRequestDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/main/java/org/fireflyframework/rules/interfaces/dtos/evaluation/RuleEvaluationByCodeRequestDTO.java) | Request DTO for evaluating rules by stored rule definition code. |
| [`RulesEvaluationResponseDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/main/java/org/fireflyframework/rules/interfaces/dtos/evaluation/RulesEvaluationResponseDTO.java) | Response DTO for rules evaluation operations. |
| [`ValidationResult`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/main/java/org/fireflyframework/rules/interfaces/dtos/validation/ValidationResult.java) | Comprehensive validation result for YAML DSL rules. |
| [`BatchRulesEvaluationRequestDTO`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/main/java/org/fireflyframework/rules/interfaces/dtos/evaluation/BatchRulesEvaluationRequestDTO.java) | Request DTO for batch rule evaluation operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Rule Engine Library](rule-engine.md) | Group the YAML rule evaluator, public DTOs, persistence models, HTTP service, and generated SDK. |
| [Utils](utils.md) | Render FreeMarker templates and convert rendered content to document formats; mark filter DTO fields. |
| [Validators](validators.md) | Apply Jakarta Bean Validation constraints to financial, identity, contact, and date fields. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-interfaces -am verify
./mvnw -pl :fireflyframework-rule-engine-interfaces -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [DTOValidationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/test/java/org/fireflyframework/rules/interfaces/dtos/validation/DTOValidationTest.java).

## Test fixture

The `testConstantDTO_CodeTooLong_ShouldFailValidation` test demonstrates one concrete behavior. Excerpt from [DTOValidationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/test/java/org/fireflyframework/rules/interfaces/dtos/validation/DTOValidationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void testConstantDTO_CodeTooLong_ShouldFailValidation() {
    // Given
    String longCode = "A".repeat(101); // 101 characters
    ConstantDTO constantDTO = ConstantDTO.builder()
            .code(longCode)
            .name("Test Name")
            .valueType(ValueType.STRING)
            .required(true)
            .build();

    // When
    Set<ConstraintViolation<ConstantDTO>> violations = validator.validate(constantDTO);

    // Then
    assertThat(violations).hasSize(1);
    assertThat(violations).anyMatch(v -> v.getMessage().contains("between 2 and 100 characters"));
}
```

## Continue the application

Use the [Rule Engine Core guide](rule-engine-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-interfaces/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
