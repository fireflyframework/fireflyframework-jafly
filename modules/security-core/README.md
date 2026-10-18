![Jafly. Security Core](../../docs/site/assets/modules/security-core.svg)

# Security Core

Evaluate the framework security model with authority mapping, policy decisions, introspection caching, and principal projection.

Configure the core engine and its ports, then use the authorization evaluator from method or web bindings. Review the default-deny and non-permit behavior in the tests before adding a custom policy adapter.

[Module guide](../../docs/site/modules/security-core.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Map the authenticated applicant/reviewer claims and evaluate the configured authority requirements. Choose the HTTP and method bindings separately from this core engine.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-core</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The core is normally wired by `security-autoconfigure`, but every component is a constructable plain object. Direct authorization-engine usage:

```java
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper;
import org.fireflyframework.security.core.authz.SecureAuthorizationEvaluator;
import org.fireflyframework.security.core.authz.SecureRequirement;
import org.fireflyframework.security.core.context.PrincipalFactory;

import java.util.List;
import java.util.Map;

// 1. Project validated claims into a generic principal, normalizing authorities/scopes.
var authorityMapper = new ConfigurableAuthorityMapper(null); // null -> sensible defaults
var principalFactory = new PrincipalFactory(authorityMapper);

Map<String, Object> claims = Map.of(
        "sub", "user-123",
        "realm_access", Map.of("roles", List.of("admin")),
        "scope", "accounts:read accounts:write");
SecurityPrincipal principal = principalFactory.fromClaims("user-123", "https://issuer.example", claims);

// 2. Evaluate a requirement (here: ALL of these roles AND a scope), fail-closed.
var evaluator = new SecureAuthorizationEvaluator();
var requirement = new SecureRequirement(
        List.of("admin"),            // roles
        List.of("accounts:write"),   // scopes
        List.of(),                   // permissions
        true, false, false,          // requireAllRoles / Scopes / Permissions
        "principal.subject != null"); // SpEL expression (denies on throw)

Decision decision = evaluator.evaluate(principal, requirement);
boolean allowed = decision.granted();
```

`SecureRequirement.from(secure)` builds the same requirement straight from a `@Secure` annotation, which is how the `security-webflux` / method-policy PEPs invoke the evaluator.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`SecureAuthorizationEvaluator`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-core/src/main/java/org/fireflyframework/security/core/authz/SecureAuthorizationEvaluator.java) | Evaluates a `SecureRequirement` against a `SecurityPrincipal`. |
| [`EmbeddedPolicyDecisionAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-core/src/main/java/org/fireflyframework/security/core/policy/EmbeddedPolicyDecisionAdapter.java) | Zero-dependency, in-process default `PolicyDecisionPort`. |
| [`AuthorityMappingProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-core/src/main/java/org/fireflyframework/security/core/authority/AuthorityMappingProperties.java) | Configuration for `ConfigurableAuthorityMapper`: which claim paths carry roles/groups and scopes, and an optional authority prefix. |
| [`PolicyRule`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-core/src/main/java/org/fireflyframework/security/core/policy/PolicyRule.java) | A single in-process ABAC rule for the `EmbeddedPolicyDecisionAdapter`. |
| [`LoggingAuditEventAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-core/src/main/java/org/fireflyframework/security/core/audit/LoggingAuditEventAdapter.java) | Default `AuditEventPort`: emits structured audit lines to a dedicated logger. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-core -am verify
./mvnw -pl :fireflyframework-security-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-core.md).
