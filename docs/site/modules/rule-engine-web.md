![Jafly. Rule Engine Web](../assets/modules/rule-engine-web.svg)

# Rule Engine Web

Expose HTTP APIs for evaluation, stored rule definitions, constants, batch operations, validation, and audit trails.

Run RuleEngineApplication with the database/backend configuration and use the appropriate evaluation route. Keep rule authoring/validation and operational evaluation permissions aligned with your service security policy.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-rule-engine-web` | `jar` | [modules/rule-engine/fireflyframework-rule-engine-web](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-web) |

Part of [Business operations](index.md#business-operations).

## In the onboarding service

Run the rule service when onboarding applications evaluate rules over HTTP or manage stored definitions centrally. Configure its database and protect authoring/evaluation routes according to the service policy.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-web</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Build the service and its prerequisites, configure its database/backend, and start the Boot application from the repository root:

```bash
./mvnw -pl :fireflyframework-rule-engine-web -am install
./mvnw -f modules/rule-engine/fireflyframework-rule-engine-web/pom.xml spring-boot:run
```

Choose the evaluation endpoint from `RulesEvaluationController` for direct encoded YAML, plain YAML, or a stored rule code. Other controller roots include `/api/v1/constants`, `/api/v1/rules/definitions`, `/api/v1/validation`, `/api/v1/audit`, and the batch evaluation routes. Use the [platform guide](rule-engine.md) for the request body and YAML DSL; API schema and controller declarations remain the reference for exact paths.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`RulesEvaluationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/RulesEvaluationController.java) | REST controller for rules evaluation operations. |
| [`ValidationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/ValidationController.java) | REST controller for YAML DSL validation services. |
| [`RuleDefinitionController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/RuleDefinitionController.java) | REST controller for managing YAML DSL rule definitions. |
| [`BatchRulesEvaluationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/BatchRulesEvaluationController.java) | REST controller for batch rule evaluation operations. |
| [`AuditTrailController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/AuditTrailController.java) | REST controller for audit trail operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Rule Engine Library](rule-engine.md) | Group the YAML rule evaluator, public DTOs, persistence models, HTTP service, and generated SDK. |
| [Rule Engine Core](rule-engine-core.md) | Parse YAML rule DSL into an AST, evaluate conditions/actions, and provide rule, constant, batch, and audit services. |
| [Rule Engine Interfaces](rule-engine-interfaces.md) | Share request/response DTOs, rule definitions, validation results, and value/result enums for the rule engine. |
| [Web](web.md) | Handle WebFlux errors, request metadata, idempotency, CORS, and sensitive-data masking consistently. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-web -am verify
./mvnw -pl :fireflyframework-rule-engine-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [RulesEvaluationController.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/RulesEvaluationController.java). This fragment needs the full type and its imports.

```java
public class RulesEvaluationController {
    private final RulesEvaluationService rulesEvaluationService;
               description = "Evaluate a base64-encoded YAML DSL rules definition against provided input data. " +
                           "Input variables must use camelCase (creditScore, annualIncome), " +
                           "computed variables will be in snake_case (debt_to_income, is_eligible), " +
                           "and constants use UPPER_CASE (MIN_CREDIT_SCORE).")
    })
    public Mono<ResponseEntity<RulesEvaluationResponseDTO>> evaluateRulesDirect(
            ServerWebExchange exchange) {
        return rulesEvaluationService.evaluateRulesDirectWithAudit(request, exchange)
                .map(ResponseEntity::ok);
    }
               description = "Evaluate a plain (non-base64) YAML DSL rules definition against provided input data. " +
                           "Input variables must use camelCase (creditScore, annualIncome), " +
                           "computed variables will be in snake_case (debt_to_income, is_eligible), " +
                           "and constants use UPPER_CASE (MIN_CREDIT_SCORE). " +
                           "This endpoint accepts YAML content directly without base64 encoding.")
    })
    public Mono<ResponseEntity<RulesEvaluationResponseDTO>> evaluateRulesPlain(
            ServerWebExchange exchange) {
        return rulesEvaluationService.evaluateRulesPlainWithAudit(request, exchange)
                .map(ResponseEntity::ok);
    }
               description = "Evaluate a stored YAML DSL rule definition by its code against provided input data. " +
```

## Continue the application

Use the [Rule Engine SDK guide](rule-engine-sdk.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java).
- [application.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/resources/application.yaml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
