![Jafly. Security WebFlux Binding](../../docs/site/assets/modules/security-webflux.svg)

# Security WebFlux Binding

Bind the framework principal and authorization policy to reactive Spring Security/WebFlux.

Use the principal projection and URL policy enforcement components in a reactive security chain. Configure security-core and the relevant token validation module; this binding alone is not a complete identity provider deployment.

[Module guide](../../docs/site/modules/security-webflux.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Project the authenticated reviewer into the framework principal and connect URL policy decisions to WebFlux. Token signature validation is configured in the resource-server module.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-webflux</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Wire the PEP into a `SecurityWebFilterChain` (normally done for you by `security-autoconfigure`):

```java
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.webflux.authz.PolicyAuthorizationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Bean
SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, PolicyDecisionPort pdp) {
    return http
            .authorizeExchange(ex -> ex
                    .pathMatchers("/actuator/health").permitAll()
                    // every other exchange goes through the policy decision point (fail-closed)
                    .anyExchange().access(new PolicyAuthorizationManager(pdp)))
            .build();
}
```

Read the current principal from a service through the port. No Spring Security import required:

```java
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GreetingService {

    private final SecurityContextPort securityContext;

    public GreetingService(SecurityContextPort securityContext) {
        this.securityContext = securityContext; // ReactorSecurityContextAdapter at runtime
    }

    public Mono<String> greet() {
        return securityContext.currentPrincipal()
                .map(SecurityPrincipal::subject)
                .map(subject -> "Hello, " + subject)
                .defaultIfEmpty("Hello, anonymous");
    }
}
```

When you mint a principal yourself (e.g. In a custom authentication converter), wrap it so Spring authorities and the rich principal stay in sync:

```java
SecurityPrincipal principal = SecurityPrincipal.builder()
        .subject("u1")
        .authorities(java.util.Set.of("ROLE_ADMIN"))
        .build();
Authentication auth = new FireflyAuthenticationToken(principal);
```

## Configuration

The `firefly.security.webflux.header-bridge` namespace is bound by [PrincipalHeaderBridgeProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.security.webflux.header-bridge.enabled` | `boolean` | `false` |
| `firefly.security.webflux.header-bridge.tenant-header` | `String` | `"X-Tenant-Id"` |
| `firefly.security.webflux.header-bridge.user-header` | `String` | `"X-User-Id"` |
| `firefly.security.webflux.header-bridge.roles-header` | `String` | `"X-User-Roles"` |
| `firefly.security.webflux.header-bridge.tenant-required` | `boolean` | `true` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`PrincipalHeaderBridgeProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeProperties.java) | Configuration for the principal→header bridge. |
| [`FireflyAuthenticationToken`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/authentication/FireflyAuthenticationToken.java) | Spring Security `org.springframework.security.core.Authentication` whose principal is the framework's `SecurityPrincipal`. |
| [`PrincipalSupport`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/authentication/PrincipalSupport.java) | Recovers a `SecurityPrincipal` from any Spring `Authentication`. |
| [`PolicyAuthorizationManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/authz/PolicyAuthorizationManager.java) | URL policy enforcement point. |
| [`PrincipalHeaderBridgeWebFilter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-webflux/src/main/java/org/fireflyframework/security/webflux/bridge/PrincipalHeaderBridgeWebFilter.java) | Derives trusted internal request headers from the validated token , never from the client. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-webflux -am verify
./mvnw -pl :fireflyframework-security-webflux -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-webflux.md).
