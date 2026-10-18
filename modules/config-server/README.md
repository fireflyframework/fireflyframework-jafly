![Jafly. Config Server](../../docs/site/assets/modules/config-server.svg)

# Config Server

Run a Spring Cloud Config Server for centralized service configuration.

Operate this as a separate configuration service, choose its native or Git backend, and arrange client access and credentials. Configure the application name/profile/label on consumers to match the repository layout.

[Module guide](../../docs/site/modules/config-server.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

When several onboarding services share centrally managed configuration, operate this server separately and give each consumer its application/profile settings. The first-service example does not require it.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-config-server</artifactId>
    <version>26.10.17-SNAPSHOT</version>
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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FireflyConfigServerApplication`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/config-server/src/main/java/org/fireflyframework/config/server/FireflyConfigServerApplication.java) | Defines `main` operations. |

## Continue the application

Use the [Starter Core guide](../../docs/site/modules/starter-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-config-server -am verify
./mvnw -pl :fireflyframework-config-server -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/config-server.md).
