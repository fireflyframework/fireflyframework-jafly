![Jafly. Security SPI](../../docs/site/assets/modules/security-spi.svg)

# Security SPI

Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services.

Implement only the ports required by your security deployment and inject those implementations into the security engine. Keep external product types behind the adapter boundary; use the port contract for fail-closed behavior and reactive error handling.

[Module guide](../../docs/site/modules/security-spi.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Connect the onboarding service's policy, relationship, revocation, key, or secret source through the required port. Add only the adapter contracts needed by that deployment.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-spi</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

You implement these ports in an adapter module and let the platform's auto-configuration discover your bean. A minimal `PolicyDecisionPort` adapter, fail-closed by contract:

```java
package com.example.security;

import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpaPolicyDecisionAdapter implements PolicyDecisionPort {

    private final OpaClient opa; // your engine binding



    @Override
    public Mono<Decision> authorize(SecurityPrincipal principal, String action,
                                    String resource, Map<String, Object> context) {
        return opa.evaluate(principal.subject(), action, resource, context)
                  .map(allowed -> allowed ? Decision.permit() : Decision.deny())
                  // fail-closed: any error becomes a denial, never a permit
                  .onErrorReturn(Decision.deny());
    }
}
```

Conversely, the core engine consumes the ports. It never sees your adapter type, only the interface:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.security.spi.TokenValidationPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;

@RequiredArgsConstructor
public class AuthorizationFilter {

    private final TokenValidationPort tokenValidation;
    private final PolicyDecisionPort policy;



    public Mono<Decision> authorize(BearerToken token, String action, String resource) {
        return tokenValidation.validate(token)                          // → SecurityPrincipal (or TokenValidationException)
                .flatMap(p -> policy.authorize(p, action, resource, Map.of()));
    }
}
```

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`PolicyDecisionPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/PolicyDecisionPort.java) | Externalized authorization decision point (ABAC). |
| [`RelationshipPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/RelationshipPort.java) | Relationship-based access control (ReBAC / Zanzibar) check. |
| [`SecretsPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/SecretsPort.java) | Resolves named secrets from an external store (Vault, AWS Secrets Manager, Azure Key Vault), so credentials never live in YAML. |
| [`RevocationPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/RevocationPort.java) | Tracks revoked tokens/sessions so even an unexpired, validly-signed token can be rejected. |
| [`KeyManagementPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-spi/src/main/java/org/fireflyframework/security/spi/KeyManagementPort.java) | Manages first-party token signing keys and the published JWKS. |

## Continue the application

Use the [Security Core guide](../../docs/site/modules/security-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-spi -am verify
./mvnw -pl :fireflyframework-security-spi -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-spi.md).
