![Jafly. Bill of Materials](../../docs/site/assets/modules/bom.svg)

# Bill of Materials

Align the versions of the 82 managed Jafly artifacts without inheriting the build parent.

Import this POM in `dependencyManagement` to align component versions. Add the concrete components your application uses. The BOM also carries selected upstream compatibility and security constraints, including aligned Jackson 2 and Jackson 3 BOMs. These are upstream dependencies, not additional Jafly components. It does not import the complete Spring Boot or Cloud platform.

[Module guide](../../docs/site/modules/bom.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A team that already has a corporate Maven parent can import this BOM into the onboarding service. It selects framework artifact versions while the existing parent continues to manage Spring and build tooling.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>26.10.20-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

After importing the BOM (or inheriting the parent), declare framework modules without versions. They are resolved from the BOM:

```xml
<dependencies>
    <!-- Opinionated Spring Boot starter for a core-tier microservice -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-starter-core</artifactId>
    </dependency>

    <!-- WebFlux server scaffolding -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-web</artifactId>
    </dependency>

    <!-- Event-driven architecture: core SPI ... -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-eda</artifactId>
    </dependency>

    <!-- ... plus the Kafka transport adapter (added only when needed) -->
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-eda-kafka</artifactId>
    </dependency>
</dependencies>
```

Need a different cache provider or messaging transport? Just add the matching adapter artifact. For example `fireflyframework-cache-redis` or `fireflyframework-eda-rabbitmq`. Still with no `<version>`.

## Configuration

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Continue the application

Use the [Build Parent guide](../../docs/site/modules/parent.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-bom -am verify
./mvnw -pl :fireflyframework-bom -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/bom.md).
