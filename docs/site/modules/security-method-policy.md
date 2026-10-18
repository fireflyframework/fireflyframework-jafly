![Jafly. Security Method Policy](../assets/modules/security-method-policy.svg)

# Security Method Policy

Enable reactive method authorization through Secure and the core authorization evaluator.

Annotate the protected reactive method with the security API annotation and enable the module configuration. Configure the required principal roles, scopes, permissions, or principal-based SpEL expression, and test both permit and deny paths.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-method-policy` | `jar` | [modules/security-method-policy](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-method-policy) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Protect a reactive review method on a Spring bean with the required reviewer authorities/scopes. External action/resource policy decisions need their own policy-port integration.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-method-policy</artifactId>
    <version>26.10.18</version>
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

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`MethodSecurityAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/main/java/org/fireflyframework/security/method/MethodSecurityAutoConfiguration.java) | Enables reactive method security (`@PreAuthorize`/`@PostAuthorize`) and registers the `Secure` interceptor so `@Secure` works as ergonomic, fail-closed sugar on top of it. |
| [`SecureMethodAuthorizationManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/main/java/org/fireflyframework/security/method/SecureMethodAuthorizationManager.java) | Reactive `ReactiveAuthorizationManager` that enforces the `Secure` annotation on methods (or their declaring class) using the framework's `SecureAuthorizationEvaluator`. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security Core](security-core.md) | Evaluate the framework security model with authority mapping, policy decisions, introspection caching, and principal projection. |
| [Security WebFlux Binding](security-webflux.md) | Bind the framework principal and authorization policy to reactive Spring Security/WebFlux. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-method-policy -am verify
./mvnw -pl :fireflyframework-security-method-policy -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [MethodSecurityIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/test/java/org/fireflyframework/security/method/MethodSecurityIntegrationTest.java).

## Test fixture

The `secureAllowsWhenRequiredRolePresent` test demonstrates one concrete behavior. Excerpt from [MethodSecurityIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/test/java/org/fireflyframework/security/method/MethodSecurityIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void secureAllowsWhenRequiredRolePresent() {
    StepVerifier.create(service.adminOnly()
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth("admin"))))
            .expectNext("admin-ok").verifyComplete();
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and providers attach as outboard adapters:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  security-method-policy  →  adapters
 (ports +         (driven           (neutral          (reactive             (this module:              (Vault, KMS,
  domain,          ports)            engine,            Spring Security        @EnableReactiveMethod-     OPA, Keycloak,
  @Secure)                           SecureAuth-        bindings)              Security + @Secure         internal-db, …)
                                     Evaluator)                                interceptor)
```

- `security-api` defines the domain (`SecurityPrincipal`) and the `@Secure` annotation this module enforces.
- `security-core` supplies the framework-neutral engine: `SecureRequirement` (a neutral view of `@Secure`) and `SecureAuthorizationEvaluator`, which this module calls to render an allow/deny `Decision`.
- `security-webflux` supplies the reactive Spring Security glue. `FireflyAuthenticationToken` (carrying a rich `SecurityPrincipal`) and `PrincipalSupport`, which recovers a `SecurityPrincipal` from any Spring `Authentication`.
- This module binds those into Spring Security's reactive method-interception machinery and is delivered to applications transitively via the application starter.
- Adapters are unaffected; method security reads whatever principal the edge produced.

This module depends only on `security-core` and `security-webflux` (which transitively bring `-spi` and `-api`) plus `spring-security-config` and `spring-boot-autoconfigure`. It imports no vendor SDK.

### What it provides

`MethodSecurityAutoConfiguration` (annotated `@AutoConfiguration @EnableReactiveMethodSecurity`) contributes:

- Reactive method security, by carrying `@EnableReactiveMethodSecurity`. This enables Spring's standard `@PreAuthorize`/`@PostAuthorize` reactive interceptors against `ReactiveSecurityContextHolder`.
- The `@Secure` interceptor. a single `AuthorizationManagerBeforeReactiveMethodInterceptor` bean (`fireflySecureMethodInterceptor`, registered with `BeanDefinition.ROLE_INFRASTRUCTURE`). Its pointcut matches the `@Secure` annotation on either a method or its declaring type (a `ComposablePointcut` union of two `AnnotationMatchingPointcut`s), and it delegates the allow/deny decision to `SecureMethodAuthorizationManager`.

`SecureMethodAuthorizationManager` is a `ReactiveAuthorizationManager<MethodInvocation>` that:

1. Resolves the effective `@Secure` annotation. Method-level first (`AnnotatedElementUtils.findMergedAnnotation`), then the target class (via `AopUtils.getTargetClass`). If none is present, it permits (so the interceptor is inert on unannotated methods).
2. Converts the annotation to a neutral `SecureRequirement` (`SecureRequirement.from(...)`).
3. Extracts a `SecurityPrincipal` from the current `Authentication` via `PrincipalSupport.extract(...)`, filtered on `Authentication::isAuthenticated`.
4. Renders the decision through `SecureAuthorizationEvaluator.evaluate(...)` and returns its `granted()` flag.

It is fail-closed: an unauthenticated caller (empty/anonymous context) yields `new AuthorizationDecision(false)` via `defaultIfEmpty`, and a principal that does not satisfy the requirement is denied. A denial surfaces to the caller as Spring Security's `AuthorizationDeniedException` on the reactive stream.

### Key types

| Type | Role |
| --- | --- |
| `MethodSecurityAutoConfiguration` | `@AutoConfiguration @EnableReactiveMethodSecurity` entry point; enables reactive method security and registers the `@Secure` interceptor. |
| `SecureMethodAuthorizationManager` | `ReactiveAuthorizationManager<MethodInvocation>` that resolves `@Secure`, builds a `SecureRequirement`, and evaluates it against the reactive `SecurityPrincipal`, fail-closed. |

Annotation enforced (from `security-api`): `@Secure` (`roles`, `scopes`, `permissions`, `requireAllRoles`/`requireAllScopes`/`requireAllPermissions`, `expression`). Engine consumed (from `security-core`): `SecureRequirement`, `SecureAuthorizationEvaluator`, `Decision`. Reactive glue consumed (from `security-webflux`): `PrincipalSupport`, `FireflyAuthenticationToken`. Domain (from `security-api`): `SecurityPrincipal`.

The auto-configuration is registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### How `@Secure` is evaluated

```
Mono<T> method() guarded by @Secure
   → fireflySecureMethodInterceptor (pointcut: @Secure on method or class)
   → SecureMethodAuthorizationManager.check(...)
      → resolve @Secure (method first, then target class)
      → SecureRequirement.from(secure)
      → PrincipalSupport.extract(Authentication)  // from ReactiveSecurityContextHolder
      → SecureAuthorizationEvaluator.evaluate(principal, requirement) → Decision
   → AuthorizationDecision(granted)
   → granted ? proceed : AuthorizationDeniedException
```

A `null`/unauthenticated principal is denied; each declared dimension is checked in turn (roles, scopes, permissions, then expression), and a failing or throwing SpEL expression denies. Only a principal that satisfies every declared dimension reaches the method body.

### Testing

The module ships a `@SpringBootTest` integration test, `MethodSecurityIntegrationTest` (`webEnvironment = NONE`), that boots a real Spring context with the real `MethodSecurityAutoConfiguration` and drives a `SecuredService` whose methods are annotated with `@Secure` and `@PreAuthorize`. It exercises both annotation styles end to end against the reactive security context. Establishing identity with `ReactiveSecurityContextHolder.withAuthentication(...)` and a `FireflyAuthenticationToken` built over a `SecurityPrincipal`, then asserting outcomes with `StepVerifier`:

- Denied (unauthenticated). `@Secure(roles = "admin")` with no authentication in context errors with `AuthorizationDeniedException` (fail-closed default).
- Allowed (role present). the same method, with an `admin`-authorized principal in context, emits its value.
- Denied (role missing). the same method, with a `teller`-only principal, errors with `AuthorizationDeniedException`.
- `@PreAuthorize` enabled. `@PreAuthorize("hasAuthority('teller')")` permits a `teller` principal, proving native Spring method security is active alongside `@Secure`.
- Unannotated method is open. a method with no annotation runs without an authenticated context, proving the interceptor is inert where `@Secure` is absent.

These mirror the platform's negative-path verification strategy: the unauthenticated and missing-authority deny paths are proven, not assumed.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-method-policy/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-method-policy/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
