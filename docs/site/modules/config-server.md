![Jafly. Config Server](../assets/modules/config-server.svg)

# Config Server

Run a Spring Cloud Config Server for centralized service configuration.

Operate this as a separate configuration service, choose its native or Git backend, and arrange client access and credentials. Configure the application name/profile/label on consumers to match the repository layout.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-config-server` | `jar` | [modules/config-server](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/config-server) |

Part of [Service operations](index.md#service-operations).

## In the onboarding service

When several onboarding services share centrally managed configuration, operate this server separately and give each consumer its application/profile settings. The first-service example does not require it.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-config-server</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The application class is provided for you. There is nothing to write to stand up a config server:

```java
package org.fireflyframework.config.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class FireflyConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FireflyConfigServerApplication.class, args);
    }
}
```

Run it locally:

```bash
./mvnw -f modules/config-server/pom.xml spring-boot:run
# or, after packaging
java -jar target/fireflyframework-config-server-*.jar
```

The server starts on port 8888 by default. Fetch a service's merged configuration over the Config Server HTTP API (`/{application}/{profile}`):

```bash
# Merged config for the "distributor-domain-branding" app in the dev profile
curl http://localhost:8888/distributor-domain-branding/dev

# Health, info and Prometheus endpoints (via fireflyframework-observability)
curl http://localhost:8888/actuator/health
curl http://localhost:8888/actuator/prometheus
```

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyConfigServerApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/java/org/fireflyframework/config/server/FireflyConfigServerApplication.java) | Defines `main` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-config-server -am verify
./mvnw -pl :fireflyframework-config-server -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

This subproject has no authored `src/test/java` suite. Its Maven build still validates the configured compilation/generation and any reactor prerequisites. Use the consuming component tests to validate integration behavior.

## API reference excerpt

Contract excerpt from [FireflyConfigServerApplication.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/java/org/fireflyframework/config/server/FireflyConfigServerApplication.java). This fragment needs the full type and its imports.

```java
public class FireflyConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FireflyConfigServerApplication.class, args);
    }
}
```

## Detailed reference

### Configuration

The server's own behavior is defined in `src/main/resources/application.yml`. It runs the Spring Cloud Config `native` profile and serves configuration from a layered set of classpath search locations:

```yaml
server:
  port: 8888

spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: >
            classpath:/config/common,
            classpath:/config/core,
            classpath:/config/domain,
            classpath:/config/common/{application},
            classpath:/config/core/{application},
            classpath:/config/domain/{application}
```

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8888` | HTTP port the config server listens on. |
| `spring.profiles.active` | `native` | Selects the native (classpath/filesystem) backend rather than Git. |
| `spring.cloud.config.server.native.search-locations` | the six tiered locations above | Ordered list of locations searched per request; later/more-specific locations override earlier shared ones. `{application}` is substituted with the requested application name. |

#### Switching to a Git backend

The native backend is the default, but Spring Cloud Config Server supports a Git backend with no code changes. Set the `git` profile and a repository URI:

```yaml
spring:
  profiles:
    active: git
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo
          default-label: main
          search-paths: '{application}'
```

### Config Repository Layout

Bundled configuration lives under `src/main/resources/config/` and is organized into three tiers, each searched in order. Shared defaults are resolved from the broad tiers first, then refined by more specific, per-application files:

```
config/
├── common/                         # Cross-cutting defaults for every service
│   ├── application.yaml            # base
│   ├── application-dev.yaml        # dev profile overlay
│   ├── application-pre.yaml        # pre (staging) profile overlay
│   └── application-pro.yaml        # pro (production) profile overlay
├── core/                           # Core-tier defaults
│   ├── application.yaml
│   ├── application-{dev,pre,pro}.yaml
└── domain/                         # Domain-tier defaults
    ├── application.yaml
    ├── application-{dev,pre,pro}.yaml
    └── distributor-domain-branding/        # Per-application overlay
        ├── distributor-domain-branding.yaml
        └── distributor-domain-branding-{dev,pre,pro}.yaml
```

- Tiers (`common`, `core`, `domain`) hold defaults shared by all services in that layer of the Firefly architecture.
- Profiles (`dev`, `pre`, `pro`) provide environment-specific overrides via the `{name}-{profile}.yaml` suffix.
- Per-application directories (e.g. `distributor-domain-branding/`) carry the deltas specific to one microservice, layered on top of the shared tiers.

Per-application files configure the conventional Firefly stack a service expects. `firefly.cqrs`, `firefly.eda`, `firefly.stepevents`, `springdoc`, Actuator exposure, and logging. So onboarding a new service is mostly a matter of adding its overlay.

### Client Integration

Firefly microservices consume this server through the Spring Cloud Config client wired up by `fireflyframework-starter-core`. A client points at the server and identifies itself by application name and active profile:

```yaml
spring:
  application:
    name: distributor-domain-branding
  config:
    import: optional:configserver:http://localhost:8888
  profiles:
    active: dev
```

At bootstrap the client fetches `http://localhost:8888/distributor-domain-branding/dev`, receiving the merged result of the `common`, `core`, and `domain` tiers plus its own per-application overlay.

## Continue the application

Use the [Starter Core guide](starter-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/config-server/src/main/java).
- [application.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/resources/application.yml).
- [application-dev.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/resources/config/common/application-dev.yaml).
- [application-pre.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/resources/config/common/application-pre.yaml).
- [application-pro.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/resources/config/common/application-pro.yaml).
- [application.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/resources/config/common/application.yaml).
- [application-dev.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/resources/config/core/application-dev.yaml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
