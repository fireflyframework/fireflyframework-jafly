![Jafly. Rule Engine Models](../assets/modules/rule-engine-models.svg)

# Rule Engine Models

Persist rule definitions, constants, and audit trails with reactive repositories.

Apply the included database migrations, then use RuleDefinitionRepository and ConstantRepository for stored rule evaluation. AuditTrail tracks persistence state explicitly; preserve its new/existing semantics when constructing audit entities.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-rule-engine-models` | `jar` | [modules/rule-engine/fireflyframework-rule-engine-models](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-models) |

Part of [Business operations](index.md#business-operations).

## In the onboarding service

Use these repositories when onboarding rules and constants need a stored, versioned definition. The rule schema and audit trail are separate from the applicant's submitted record.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-models</artifactId>
    <version>26.10.19</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Apply the three supplied migrations for constants, rule definitions, and audit trails. A service can then inject `RuleDefinitionRepository` and look up a stored rule by its code:

```java
Mono<RuleDefinition> rule = repository.findByCode("credit-check");
```

The example code is application-defined; create the corresponding stored rule before evaluating it. The repository is in `org.fireflyframework.rules.models.repositories`, while the exact entity package is linked in the API table. `AuditTrail` implements `Persistable<UUID>` and uses `isNew()`/`markNotNew()` to control persistence state; preserve that behavior when mapping existing audit records.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`RuleDefinition`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/RuleDefinition.java) | Entity representing a stored YAML DSL rule definition. |
| [`Constant`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/Constant.java) | Entity representing a system constant that can be used in rule expressions. |
| [`AuditTrail`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/AuditTrail.java) | Entity representing an audit trail record for rule engine operations. |
| [`RuleDefinitionRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/repositories/RuleDefinitionRepository.java) | Repository interface for RuleDefinition entities. |
| [`AuditTrailRepository`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/repositories/AuditTrailRepository.java) | Repository interface for AuditTrail entity operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Rule Engine Library](rule-engine.md) | Group the YAML rule evaluator, public DTOs, persistence models, HTTP service, and generated SDK. |
| [Rule Engine Interfaces](rule-engine-interfaces.md) | Share request/response DTOs, rule definitions, validation results, and value/result enums for the rule engine. |
| [R2DBC](r2dbc.md) | Build reactive repository filtering and pagination on Spring Data R2DBC. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine-models -am verify
./mvnw -pl :fireflyframework-rule-engine-models -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [RuleDefinition.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java/org/fireflyframework/rules/models/entities/RuleDefinition.java). This fragment needs the full type and its imports.

```java
public class RuleDefinition {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private String yamlContent;
    private String version;
    private Boolean isActive;
    private String tags;
    private String createdBy;
    private String updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

## Continue the application

Use the [Rule Engine Web guide](rule-engine-web.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/java).
- [V1__Create_constants_table.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/resources/db/migration/V1__Create_constants_table.sql).
- [V2__Create_rule_definitions_table.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/resources/db/migration/V2__Create_rule_definitions_table.sql).
- [V3__Create_audit_trails_table.sql](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/rule-engine/fireflyframework-rule-engine-models/src/main/resources/db/migration/V3__Create_audit_trails_table.sql).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
