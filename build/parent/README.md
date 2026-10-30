![Jafly. Build Parent](../../docs/site/assets/modules/parent.svg)

# Build Parent

Inherit a consistent Java build, dependency platform, and plugin configuration for framework-based services.

Use this POM as your Maven parent when you want the framework build defaults. Applications with a different corporate parent can import the BOM instead. Dependency management does not by itself place managed libraries on the runtime classpath.

[Module guide](../../docs/site/modules/parent.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

The onboarding example inherits this parent and keeps its own application version. Use that arrangement when the service should share the framework compiler and plugin settings.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.10.22</version>
    <relativePath/>
</parent>
```

## Configuration

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

### Generated Java SDK variants

The managed OpenAPI Generator plugin exposes two opt-in build properties for object variants described with `oneOf`:

```xml
<properties>
    <openapi.use.oneof.interfaces>true</openapi.use.oneof.interfaces>
    <openapi.use.oneof.deduction>true</openapi.use.oneof.deduction>
</properties>
```

Both properties default to `false`, preserving the existing generator behavior. For the qualified Java WebClient configuration, the interface option generates separate typed variants and the deduction option uses their JSON field shapes during Jackson deserialization. Qualify serialization, deserialization, ambiguous payloads, and compatibility with the owning API before enabling them in a consumer. Keep the API schema and normal generator lifecycle authoritative for SDK output. These properties do not change a service's business admission rules or publish an updated SDK.

## Continue the application

Use the [CQRS guide](../../docs/site/modules/cqrs.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-parent -am verify
./mvnw -pl :fireflyframework-parent -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/parent.md).
