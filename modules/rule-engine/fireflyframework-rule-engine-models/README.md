![Jafly. Rule Engine Models](../../../docs/site/assets/modules/rule-engine-models.svg)

# Rule Engine Models

Persist rule definitions, constants, and audit trails with reactive repositories.

Apply the included database migrations, then use RuleDefinitionRepository and ConstantRepository for stored rule evaluation. AuditTrail tracks persistence state explicitly; preserve its new/existing semantics when constructing audit entities.

[Module guide](../../../docs/site/modules/rule-engine-models.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Use these repositories when onboarding rules and constants need a stored, versioned definition. The rule schema and audit trail are separate from the applicant's submitted record.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-models</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Apply the three supplied migrations for constants, rule definitions, and audit trails. A service can then inject `RuleDefinitionRepository` and look up a stored rule by its code:

```java
Mono<RuleDefinition> rule = repository.findByCode("credit-check");
```

The example code is application-defined; create the corresponding stored rule before evaluating it. The repository is in `org.fireflyframework.rules.models.repositories`, while the exact entity package is linked in the API table. `AuditTrail` implements `Persistable<UUID>` and uses `isNew()`/`markNotNew()` to control persistence state; preserve that behavior when mapping existing audit records.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`RuleDefinition`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/RuleDefinition.java) | Entity representing a stored YAML DSL rule definition. |
| [`Constant`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/Constant.java) | Entity representing a system constant that can be used in rule expressions. |
| [`AuditTrail`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/AuditTrail.java) | Entity representing an audit trail record for rule engine operations. |
| [`RuleDefinitionRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/repositories/RuleDefinitionRepository.java) | Repository interface for RuleDefinition entities. |
| [`AuditTrailRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/repositories/AuditTrailRepository.java) | Repository interface for AuditTrail entity operations. |

## Continue the application

Use the [Rule Engine Web guide](../../../docs/site/modules/rule-engine-web.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-models -am verify
./mvnw -pl :fireflyframework-rule-engine-models -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/rule-engine-models.md).
