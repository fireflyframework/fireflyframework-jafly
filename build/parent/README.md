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
    <version>26.10.21-SNAPSHOT</version>
    <relativePath/>
</parent>
```

## Configuration

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

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
