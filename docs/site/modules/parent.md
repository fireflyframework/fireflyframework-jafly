![Jafly. Build Parent](../assets/modules/parent.svg)

# Build Parent

Inherit a consistent Java build, dependency platform, and plugin configuration for framework-based services.

Use this POM as your Maven parent when you want the framework build defaults. Applications with a different corporate parent can import the BOM instead. Dependency management does not by itself place managed libraries on the runtime classpath.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-parent` | `pom` | [build/parent](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/build/parent) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

The onboarding example inherits this parent and keeps its own application version. Use that arrangement when the service should share the framework compiler and plugin settings.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.10.22</version>
    <relativePath/>
</parent>
```

## Configuration and wiring

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-parent -am verify
./mvnw -pl :fireflyframework-parent -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## Detailed reference

### Managed platform and tool versions

Versions below are read directly from the parent POM. Spring Boot supplies additional dependency management; consult the effective POM for the complete resolved dependency set.

| Property | Managed value |
| --- | --- |
| `java.version` | `25` |
| `spring-boot.version` | `4.1.1` |
| `spring-cloud.version` | `2025.1.3` |
| `openapi-generator.version` | `7.26.0` |
| `lombok.version` | `1.18.46` |
| `testcontainers.version` | `2.0.5` |
| `spring-cloud-aws.version` | `4.2.0` |
| `spring-cloud-azure.version` | `7.4.0` |
| `spring-cloud-gcp.version` | `8.2.4` |
| `maven-compiler-plugin.version` | `3.15.0` |
| `maven-surefire-plugin.version` | `3.6.0` |
| `maven-failsafe-plugin.version` | `3.6.0` |
| `native-maven-plugin.version` | `1.1.14` |

### Imported dependency platforms

| BOM | Version expression |
| --- | --- |
| `com.fasterxml.jackson:jackson-bom` | `${jackson2-bom.version}` |
| `tools.jackson:jackson-bom` | `${jackson-bom.version}` |
| `org.glassfish.jersey:jersey-bom` | `${jersey.version}` |
| `org.springframework.boot:spring-boot-dependencies` | `${spring-boot.version}` |
| `org.springframework.cloud:spring-cloud-dependencies` | `${spring-cloud.version}` |
| `org.testcontainers:testcontainers-bom` | `${testcontainers.version}` |
| `io.github.resilience4j:resilience4j-bom` | `${resilience4j.version}` |
| `io.grpc:grpc-bom` | `${grpc.version}` |
| `io.opentelemetry:opentelemetry-bom` | `${opentelemetry.version}` |
| `software.amazon.awssdk:bom` | `${aws-sdk.version}` |
| `io.awspring.cloud:spring-cloud-aws-dependencies` | `${spring-cloud-aws.version}` |
| `com.azure.spring:spring-cloud-azure-dependencies` | `${spring-cloud-azure.version}` |
| `com.google.cloud:spring-cloud-gcp-dependencies` | `${spring-cloud-gcp.version}` |

### Build profiles

| Profile | Intended use |
| --- | --- |
| `java21` | See the exact activation and plugin configuration in the parent POM. |
| `native` | Opt-in GraalVM native tooling; activate in a consuming application and qualify that application independently. |
| `release` | Release packaging and validation. |
| `maven-central` | Release signing, flattening, and Central publication; use the root guarded release workflow rather than publishing ad hoc. |

### Compiler and annotation processing

The parent configures the compiler, annotation processors, source/Javadoc artifacts, test tooling, and build enforcement. Review its `pluginManagement` before overriding a plugin in a consumer. The parent is management-only for optional compatibility libraries: a managed Jackson 2 version does not automatically add Jackson 2 runtime dependencies to every application.

Native compilation depends on the consuming application, supported libraries, and a suitable GraalVM environment. A successful JVM reactor build does not establish native-image compatibility. See the root [release guide](../releases.md) for publication gates and the [Maven guide](../maven.md) for inheritance versus BOM import.

## Continue the application

Use the [CQRS guide](cqrs.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/build/parent/pom.xml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
