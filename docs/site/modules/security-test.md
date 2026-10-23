![Jafly. Security Test](../assets/modules/security-test.svg)

# Security Test

Create test principals and deterministic policy decisions for security tests.

Add this module with test scope and construct principals or configure the fake policy port to exercise permit, deny, and indeterminate results. Test fixtures are not production authorization adapters.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-test` | `jar` | [modules/security-test](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-test) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Create applicant/reviewer test principals and deterministic policy outcomes for onboarding authorization tests. These fixtures do not replace a production policy source.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-test</artifactId>
    <version>26.10.22</version>
    <scope>test</scope>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Add the module as a `test`-scoped dependency, then build identities and inject policy decisions directly:

```java
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.test.FakePolicyDecisionPort;
import org.fireflyframework.security.test.TestPrincipals;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

// A principal with authorities and scopes
SecurityPrincipal alice = TestPrincipals.user("alice", Set.of("admin"), Set.of("reports.read"));

// Install an authenticated token into the reactive security context
var auth = TestPrincipals.authentication("alice", "admin");
Mono<String> underTest = handler.handle(request)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));

// Drive a PEP with a configurable PDP and assert what it asked
FakePolicyDecisionPort pdp = FakePolicyDecisionPort.denyAll("reports.read scope required");
StepVerifier.create(pep.check(alice, "read", "/api/reports", Map.of()))
        .expectNextMatches(Decision::granted)   // ... or assert the deny path
        .verifyComplete();

assertThat(pdp.lastAction()).isEqualTo("read");
assertThat(pdp.lastResource()).isEqualTo("/api/reports");
```

`FakePolicyDecisionPort` is mutable and `volatile`-backed, so the same instance can be reconfigured between assertions with `returning(...)`.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`FakePolicyDecisionPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/src/main/java/org/fireflyframework/security/test/FakePolicyDecisionPort.java) | A configurable `PolicyDecisionPort` test double that returns a predetermined decision and records the last request it received, so tests can assert PEP wiring without a real policy engine. |
| [`TestPrincipals`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/src/main/java/org/fireflyframework/security/test/TestPrincipals.java) | Factory helpers for building `SecurityPrincipal`s and `FireflyAuthenticationToken`s in tests, so application test code does not hand-roll principals or wrestle with the security context. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security API](security-api.md) | Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation. |
| [Security SPI](security-spi.md) | Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services. |
| [Security WebFlux Binding](security-webflux.md) | Bind the framework principal and authorization policy to reactive Spring Security/WebFlux. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-test -am verify
./mvnw -pl :fireflyframework-security-test -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [SecurityTestFixturesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/src/test/java/org/fireflyframework/security/test/SecurityTestFixturesTest.java).

## Test fixture

The `fakePolicyDecisionPortReturnsConfiguredDecisionAndRecordsRequest` test demonstrates one concrete behavior. Excerpt from [SecurityTestFixturesTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/src/test/java/org/fireflyframework/security/test/SecurityTestFixturesTest.java). The test class supplies its fixtures and imports.

```java
@Test
void fakePolicyDecisionPortReturnsConfiguredDecisionAndRecordsRequest() {
    FakePolicyDecisionPort permit = FakePolicyDecisionPort.permitAll();
    StepVerifier.create(permit.authorize(TestPrincipals.user("u1"), "read", "doc:1", Map.of()))
            .expectNextMatches(d -> d.granted())
            .verifyComplete();
    assertThat(permit.lastAction()).isEqualTo("read");
    assertThat(permit.lastResource()).isEqualTo("doc:1");

    FakePolicyDecisionPort deny = FakePolicyDecisionPort.denyAll("nope");
    StepVerifier.create(deny.authorize(TestPrincipals.user("u1"), "write", "doc:1", Map.of()))
            .expectNextMatches(d -> !d.granted())
            .verifyComplete();
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and test fixtures attach as an outboard, test-time adapter:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  security-test
 (ports +         (driven           (neutral          (reactive             (this module:
  domain)          ports)            engine)            Spring Security        test doubles for
                                                        bindings)              domain + ports)
```

- `security-api` defines the domain this module builds and returns: `SecurityPrincipal`, `Decision`.
- `security-spi` defines the driven port this module fakes: `PolicyDecisionPort`.
- `security-webflux` supplies the reactive Spring Security token, `FireflyAuthenticationToken`, which `TestPrincipals` constructs for installation into the reactive security context.
- This module depends on `security-api`, `security-spi`, and `security-webflux` (plus `reactor-core`) and contributes no runtime beans. it is meant to be a `test`-scoped dependency of other modules and of applications.

It imports no vendor SDK and starts no Spring context.

### Key types

| Type | Role |
| --- | --- |
| `TestPrincipals` | Final factory of static helpers building `SecurityPrincipal`s and authenticated `FireflyAuthenticationToken`s for tests. |
| `FakePolicyDecisionPort` | Configurable `PolicyDecisionPort` test double: returns a predetermined `Decision` and records the last `action`/`resource` it was asked to authorize. |

`TestPrincipals` (all static, the class is non-instantiable):

- `user(String subject, String... authorities)`. a `SecurityPrincipal` with the given subject and authorities.
- `user(String subject, Set<String> authorities, Set<String> scopes)`. a principal carrying both authorities and OAuth2 scopes.
- `authentication(String subject, String... authorities)`. an authenticated `FireflyAuthenticationToken` ready for `ReactiveSecurityContextHolder`.

`FakePolicyDecisionPort`:

- `permitAll()` / `denyAll(String reason)`. factory shortcuts wrapping `Decision.permit()` / `Decision.deny(reason)`.
- `new FakePolicyDecisionPort(Decision next)`. start from an explicit decision.
- `returning(Decision decision)`. fluently swap the decision the double will replay next.
- `lastAction()` / `lastResource()`. read back what the last `authorize(...)` call was asked about.
- `authorize(SecurityPrincipal, String action, String resource, Map<String,Object> context)`. the `PolicyDecisionPort` contract; records `action`/`resource` and returns `Mono.just(next)`.

Domain types consumed (from `security-api`): `SecurityPrincipal`, `Decision`. Driven port faked (from `security-spi`): `PolicyDecisionPort`. Reactive token built (from `security-webflux`): `FireflyAuthenticationToken`.

### Dependencies

| Scope | Artifact | Why |
| --- | --- | --- |
| compile | `fireflyframework-security-api` | `SecurityPrincipal`, `Decision` |
| compile | `fireflyframework-security-spi` | `PolicyDecisionPort` |
| compile | `fireflyframework-security-webflux` | `FireflyAuthenticationToken` |
| compile | `io.projectreactor:reactor-core` | `Mono` return type of `authorize(...)` |
| test | `org.junit.jupiter:junit-jupiter`, `org.assertj:assertj-core`, `io.projectreactor:reactor-test` | the module's own self-test |

Versions are managed by the Firefly parent/BOM. Depend on it from a consuming module like:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

If you are not inheriting the Firefly parent, pin the version explicitly:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-test</artifactId>
    <version>26.10.22</version>
    <scope>test</scope>
</dependency>
```

### Testing

The module ships its own unit test, `SecurityTestFixturesTest`, which exercises both fixtures as plain JUnit 5 tests (no Spring context, `StepVerifier` for the reactive call):

- `TestPrincipals`. `user("u1", "admin").hasAuthority("admin")` is true; `authentication("u1", "admin").securityPrincipal().subject()` equals `"u1"` and the token reports `isAuthenticated()`.
- `FakePolicyDecisionPort`. `permitAll().authorize(...)` emits a `granted()` `Decision` and records `lastAction()` / `lastResource()` (`read` / `doc:1`); `denyAll("nope").authorize(...)` emits a non-granted `Decision`.

These fixtures themselves back the negative-path integration tests elsewhere in the platform. For example `ResourceServerIntegrationTest` in `fireflyframework-security-resource-server`, whose contributed deny rules prove the default-deny / policy-enforcement path independently of authentication.

## Continue the application

Use the [Security Method Policy guide](security-method-policy.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-test/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
