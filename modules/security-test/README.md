# Firefly Framework - Security Test

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Reactor](https://img.shields.io/badge/Project%20Reactor-3.x-blue.svg)](https://projectreactor.io)

> Test fixtures for the Firefly hexagonal security platform. Two small, dependency-light helpers — a principal factory and a configurable `PolicyDecisionPort` test double — so application and framework tests can build authenticated identities and exercise policy-enforcement wiring without standing up a real IdP or policy engine.

---

## Table of Contents

- [Overview](#overview)
- [Where it sits in the platform](#where-it-sits-in-the-platform)
- [Key types](#key-types)
- [Usage](#usage)
- [Dependencies](#dependencies)
- [Testing](#testing)
- [License](#license)

## Overview

This module is the **test-fixture binding** of the Firefly security platform. It exists so that test code never hand-rolls a `SecurityPrincipal`, never wrestles with the reactive security context to install an authenticated token, and never has to embed a real policy engine just to prove that a policy enforcement point (PEP) calls the policy decision point (PDP) correctly.

It ships exactly two production types — `TestPrincipals` and `FakePolicyDecisionPort` — plus the test that exercises them. There is no auto-configuration, no Spring context, and no vendor SDK. The fixtures are plain factories and a hand-written test double; they compile against the platform's domain and port contracts only.

Because the double records the last `(action, resource)` it was asked about and replays a predetermined `Decision`, tests can assert *that* a PEP consulted the PDP and *what* it asked, independently of any real authorization logic.

## Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and test fixtures attach as an outboard, test-time adapter:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  security-test
 (ports +         (driven           (neutral          (reactive             (this module:
  domain)          ports)            engine)            Spring Security        test doubles for
                                                        bindings)              domain + ports)
```

- **`security-api`** defines the domain this module builds and returns: `SecurityPrincipal`, `Decision`.
- **`security-spi`** defines the driven port this module fakes: `PolicyDecisionPort`.
- **`security-webflux`** supplies the reactive Spring Security token, `FireflyAuthenticationToken`, which `TestPrincipals` constructs for installation into the reactive security context.
- **This module** depends on `security-api`, `security-spi`, and `security-webflux` (plus `reactor-core`) and contributes no runtime beans — it is meant to be a `test`-scoped dependency of other modules and of applications.

It imports no vendor SDK and starts no Spring context.

## Key types

| Type | Role |
| --- | --- |
| `TestPrincipals` | Final factory of static helpers building `SecurityPrincipal`s and authenticated `FireflyAuthenticationToken`s for tests. |
| `FakePolicyDecisionPort` | Configurable `PolicyDecisionPort` test double: returns a predetermined `Decision` and records the last `action`/`resource` it was asked to authorize. |

`TestPrincipals` (all static, the class is non-instantiable):

- `user(String subject, String... authorities)` — a `SecurityPrincipal` with the given subject and authorities.
- `user(String subject, Set<String> authorities, Set<String> scopes)` — a principal carrying both authorities and OAuth2 scopes.
- `authentication(String subject, String... authorities)` — an authenticated `FireflyAuthenticationToken` ready for `ReactiveSecurityContextHolder`.

`FakePolicyDecisionPort`:

- `permitAll()` / `denyAll(String reason)` — factory shortcuts wrapping `Decision.permit()` / `Decision.deny(reason)`.
- `new FakePolicyDecisionPort(Decision next)` — start from an explicit decision.
- `returning(Decision decision)` — fluently swap the decision the double will replay next.
- `lastAction()` / `lastResource()` — read back what the last `authorize(...)` call was asked about.
- `authorize(SecurityPrincipal, String action, String resource, Map<String,Object> context)` — the `PolicyDecisionPort` contract; records `action`/`resource` and returns `Mono.just(next)`.

Domain types consumed (from `security-api`): `SecurityPrincipal`, `Decision`. Driven port faked (from `security-spi`): `PolicyDecisionPort`. Reactive token built (from `security-webflux`): `FireflyAuthenticationToken`.

## Usage

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

## Dependencies

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
    <version>26.06.01</version>
    <scope>test</scope>
</dependency>
```

## Testing

The module ships its own unit test, `SecurityTestFixturesTest`, which exercises both fixtures as plain JUnit 5 tests (no Spring context, `StepVerifier` for the reactive call):

- **`TestPrincipals`** — `user("u1", "admin").hasAuthority("admin")` is true; `authentication("u1", "admin").securityPrincipal().subject()` equals `"u1"` and the token reports `isAuthenticated()`.
- **`FakePolicyDecisionPort`** — `permitAll().authorize(...)` emits a `granted()` `Decision` and records `lastAction()` / `lastResource()` (`read` / `doc:1`); `denyAll("nope").authorize(...)` emits a non-granted `Decision`.

These fixtures themselves back the negative-path integration tests elsewhere in the platform — for example `ResourceServerIntegrationTest` in `fireflyframework-security-resource-server`, whose contributed deny rules prove the default-deny / policy-enforcement path independently of authentication.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
