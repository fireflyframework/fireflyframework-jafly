![Jafly. Rule Engine Core](../assets/modules/rule-engine-core.svg)

# Rule Engine Core

Parse YAML rule DSL into an AST, evaluate conditions/actions, and provide rule, constant, batch, and audit services.

Use the AST parser/evaluation engine for the embedded rule path; use RulesEvaluationService for the request/audit service path. Validate YAML and input variable names before production evaluation and inspect action/function behavior in the focused DSL tests.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-rule-engine-core` | `jar` | [modules/rule-engine/fireflyframework-rule-engine-core](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-core) |

Part of [Business operations](index.md#business-operations).

## In the onboarding service

Evaluate a validated onboarding rule against application input through the parser/engine or the audited service path. Custom functions and external calls must be registered/configured by the application.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-core</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Choose the entry path to match your runtime:

1. For embedded DSL evaluation, use `ASTRulesDSLParser` and `ASTRulesEvaluationEngine`; the [platform quick start](rule-engine.md) demonstrates the YAML and evaluation flow.
2. For service evaluation with an audit context, inject `RulesEvaluationService` and call `evaluateRulesPlainWithAudit(request, exchange)`, `evaluateRulesDirectWithAudit(...)`, or `evaluateRuleByCodeWithAudit(...)`.
3. Use `YamlDslValidator` and the structured validation DTOs to report authoring problems before evaluating a stored rule.

`RulesEvaluationService` accepts the matching interfaces DTO and a `ServerWebExchange` for audit context. A base64-encoded rule and plain YAML use different request DTOs. Custom functions are registered through the source `CustomFunctionRegistry`; the DSL tests are the reference for supported expressions and actions.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [RuleEngineObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java/org/fireflyframework/rules/core/observability/RuleEngineObservabilityAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`ASTRulesDSLParser`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java/org/fireflyframework/rules/core/dsl/parser/ASTRulesDSLParser.java) | Pure AST-based rules DSL parser that converts YAML directly to AST nodes. |
| [`ASTRulesEvaluationEngine`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java/org/fireflyframework/rules/core/dsl/evaluation/ASTRulesEvaluationEngine.java) | Pure AST-based rules evaluation engine with no legacy dependencies. |
| [`RulesEvaluationService`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java/org/fireflyframework/rules/core/services/RulesEvaluationService.java) | Service interface for rules evaluation operations. |
| [`YamlDslValidator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java/org/fireflyframework/rules/core/validation/YamlDslValidator.java) | Comprehensive YAML DSL validator that orchestrates all validation components. |
| [`CustomFunctionRegistry`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java/org/fireflyframework/rules/core/dsl/function/CustomFunctionRegistry.java) | Registry of user-defined `RuleFunction` implementations available to the rule DSL. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Rule Engine Library](rule-engine.md) | Group the YAML rule evaluator, public DTOs, persistence models, HTTP service, and generated SDK. |
| [Rule Engine Interfaces](rule-engine-interfaces.md) | Share request/response DTOs, rule definitions, validation results, and value/result enums for the rule engine. |
| [Rule Engine Models](rule-engine-models.md) | Persist rule definitions, constants, and audit trails with reactive repositories. |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Starter Core](starter-core.md) | Assemble core infrastructure conventions for WebClient, actuator, logging, configuration, and service discovery. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-core -am verify
./mvnw -pl :fireflyframework-rule-engine-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [AuditHelperTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/audit/AuditHelperTest.java).
- [CacheIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/cache/CacheIntegrationTest.java).
- [ASTParserIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/dsl/ASTParserIntegrationTest.java).
- [AdvancedDSLFeaturesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/dsl/AdvancedDSLFeaturesTest.java).
- [ArithmeticActionSymmetryTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/dsl/ArithmeticActionSymmetryTest.java).
- [B2BCreditScoringEngineTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/dsl/B2BCreditScoringEngineTest.java).

## Test fixture

The `percentile` test demonstrates one concrete behavior. Excerpt from [DroolsDmnParityFeaturesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/test/java/org/fireflyframework/rules/core/dsl/DroolsDmnParityFeaturesTest.java). The test class supplies its fixtures and imports.

```java
@Test
@DisplayName("percentile() interpolates between sorted samples")
void percentile() {
    String yaml = """
            name: pct
            then:
              - run p50 as percentile([1,2,3,4,5,6,7,8,9,10], 50)
              - run p90 as percentile([1,2,3,4,5,6,7,8,9,10], 90)
            output:
              p50: p50
              p90: p90
            """;
    ASTRulesEvaluationResult r = engine.evaluateRules(yaml, Map.of());
    assertThat(r.isSuccess()).isTrue();
    // p50 of 1..10 -> 5.5 ; p90 -> 9.1
    assertThat(((BigDecimal) r.getOutputData().get("p50")).doubleValue()).isEqualTo(5.5);
    assertThat(((BigDecimal) r.getOutputData().get("p90")).doubleValue()).isEqualTo(9.1);
}
```

## Continue the application

Use the [Rule Engine Interfaces guide](rule-engine-interfaces.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
