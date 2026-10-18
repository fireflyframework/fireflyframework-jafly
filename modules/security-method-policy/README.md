![Jafly. Security Method Policy](../../docs/site/assets/modules/security-method-policy.svg)

# Security Method Policy

Enable reactive method authorization through Secure and the core authorization evaluator.

Annotate the protected reactive method with the security API annotation and enable the module configuration. Configure the required principal roles, scopes, permissions, or principal-based SpEL expression, and test both permit and deny paths.

[Module guide](../../docs/site/modules/security-method-policy.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Protect a reactive review method on a Spring bean with the required reviewer authorities/scopes. External action/resource policy decisions need their own policy-port integration.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-method-policy</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

`@Secure` evaluates the current principal's authorities/scopes and its configured expression. This interceptor does not call an external `PolicyDecisionPort` for an action/resource check. Use the URL-policy binding or an explicit application policy-port call when the operation needs OPA, Cerbos, or another external decision.

With the module on the classpath, reactive method security is active with zero code. Annotate the methods (or classes) you want to protect:

```java
import org.fireflyframework.security.api.annotation.Secure;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
class AccountService {

    // Framework sugar: ANY of the listed roles suffices (fail-closed).
    @Secure(roles = "admin")
    Mono<Account> close(String accountId) {
        return ...;
    }

    // Require a scope AND a fine-grained permission, both fully matched.
    @Secure(scopes = "accounts.write", permissions = "account:close", requireAllPermissions = true)
    Mono<Void> freeze(String accountId) {
        return ...;
    }

    // Standard Spring annotation, enforced against the same reactive context.
    @PreAuthorize("hasAuthority('teller')")
    Mono<Balance> balance(String accountId) {
        return ...;
    }
}
```

`@Secure` accepts `roles`, `scopes`, `permissions`, and an optional SpEL `expression`. Across those dimensions all declared dimensions must pass (AND); within a single dimension the `requireAll*` flag switches between ANY (default) and ALL matching. An empty `@Secure` simply requires an authenticated principal.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`MethodSecurityAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/main/java/org/fireflyframework/security/method/MethodSecurityAutoConfiguration.java) | Enables reactive method security (`@PreAuthorize`/`@PostAuthorize`) and registers the `Secure` interceptor so `@Secure` works as ergonomic, fail-closed sugar on top of it. |
| [`SecureMethodAuthorizationManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/main/java/org/fireflyframework/security/method/SecureMethodAuthorizationManager.java) | Reactive `ReactiveAuthorizationManager` that enforces the `Secure` annotation on methods (or their declaring class) using the framework's `SecureAuthorizationEvaluator`. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-method-policy -am verify
./mvnw -pl :fireflyframework-security-method-policy -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-method-policy.md).
