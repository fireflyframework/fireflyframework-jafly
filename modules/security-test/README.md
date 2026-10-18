![Jafly. Security Test](../../docs/site/assets/modules/security-test.svg)

# Security Test

Create test principals and deterministic policy decisions for security tests.

Add this module with test scope and construct principals or configure the fake policy port to exercise permit, deny, and indeterminate results. Test fixtures are not production authorization adapters.

[Module guide](../../docs/site/modules/security-test.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Create applicant/reviewer test principals and deterministic policy outcomes for onboarding authorization tests. These fixtures do not replace a production policy source.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-test</artifactId>
    <version>26.10.17-SNAPSHOT</version>
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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FakePolicyDecisionPort`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/src/main/java/org/fireflyframework/security/test/FakePolicyDecisionPort.java) | A configurable `PolicyDecisionPort` test double that returns a predetermined decision and records the last request it received, so tests can assert PEP wiring without a real policy engine. |
| [`TestPrincipals`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-test/src/main/java/org/fireflyframework/security/test/TestPrincipals.java) | Factory helpers for building `SecurityPrincipal`s and `FireflyAuthenticationToken`s in tests, so application test code does not hand-roll principals or wrestle with the security context. |

## Continue the application

Use the [Security Method Policy guide](../../docs/site/modules/security-method-policy.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-test -am verify
./mvnw -pl :fireflyframework-security-test -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-test.md).
