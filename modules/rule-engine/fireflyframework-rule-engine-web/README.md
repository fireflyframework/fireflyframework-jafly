![Jafly. Rule Engine Web](../../../docs/site/assets/modules/rule-engine-web.svg)

# Rule Engine Web

Expose HTTP APIs for evaluation, stored rule definitions, constants, batch operations, validation, and audit trails.

Run RuleEngineApplication with the database/backend configuration and use the appropriate evaluation route. Keep rule authoring/validation and operational evaluation permissions aligned with your service security policy.

[Module guide](../../../docs/site/modules/rule-engine-web.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Run the rule service when onboarding applications evaluate rules over HTTP or manage stored definitions centrally. Configure its database and protect authoring/evaluation routes according to the service policy.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

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

Choose the evaluation endpoint from `RulesEvaluationController` for direct encoded YAML, plain YAML, or a stored rule code. Other controller roots include `/api/v1/constants`, `/api/v1/rules/definitions`, `/api/v1/validation`, `/api/v1/audit`, and the batch evaluation routes. Use the [platform guide](../../../docs/site/modules/rule-engine.md) for the request body and YAML DSL; API schema and controller declarations remain the reference for exact paths.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`RulesEvaluationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/RulesEvaluationController.java) | REST controller for rules evaluation operations. |
| [`ValidationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/ValidationController.java) | REST controller for YAML DSL validation services. |
| [`RuleDefinitionController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/RuleDefinitionController.java) | REST controller for managing YAML DSL rule definitions. |
| [`BatchRulesEvaluationController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/BatchRulesEvaluationController.java) | REST controller for batch rule evaluation operations. |
| [`AuditTrailController`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-web/src/main/java/org/fireflyframework/rules/web/controllers/AuditTrailController.java) | REST controller for audit trail operations. |

## Continue the application

Use the [Rule Engine SDK guide](../../../docs/site/modules/rule-engine-sdk.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-web -am verify
./mvnw -pl :fireflyframework-rule-engine-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/rule-engine-web.md).
