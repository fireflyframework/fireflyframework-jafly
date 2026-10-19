![Jafly. Security OPA Adapter](../assets/modules/security-adapter-opa.svg)

# Security OPA Adapter

Delegate policy decisions to Open Policy Agent through the security SPI.

Construct the adapter with the OPA endpoint/client and use it as PolicyDecisionPort. Align action/resource/context input with the deployed Rego policy and test unavailable/malformed responses as denial paths.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-adapter-opa` | `jar` | [modules/security-adapter-opa](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-opa) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Delegate an onboarding authorization decision to a configured OPA policy through PolicyDecisionPort. Match the application action/resource/context to the deployed Rego input contract.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-opa</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Contribute an `OpaPolicyDecisionAdapter` as your `PolicyDecisionPort`. Because `security-webflux` selects whatever `PolicyDecisionPort` bean is present, this single bean replaces the in-process `EmbeddedPolicyDecisionAdapter` for every non-permitted exchange. No other wiring required:

```java
import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class OpaPolicyConfiguration {

    @Bean
    PolicyDecisionPort policyDecisionPort() {
        WebClient opa = WebClient.create("http://opa:8181");
        return new OpaPolicyDecisionAdapter(opa, "firefly/allow");
    }
}
```

The decision is consumed reactively and is permit-only:

```java
adapter.authorize(principal, "read", "doc:1", Map.of())
        .map(Decision::granted); // true only when OPA returns result == true
```

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`OpaPolicyDecisionAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-opa/src/main/java/org/fireflyframework/security/adapter/opa/OpaPolicyDecisionAdapter.java) | `PolicyDecisionPort` backed by Open Policy Agent. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Security API](security-api.md) | Define product-neutral principals, authorization decisions, driving use cases, and the Secure annotation. |
| [Security SPI](security-spi.md) | Define driven ports for policy, relationships, revocation, keys, secrets, and other external security services. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-adapter-opa -am verify
./mvnw -pl :fireflyframework-security-adapter-opa -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [OpaPolicyDecisionAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-opa/src/test/java/org/fireflyframework/security/adapter/opa/OpaPolicyDecisionAdapterIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `permitsAllowedActionForAnySubject` test demonstrates one concrete behavior. Excerpt from [OpaPolicyDecisionAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-opa/src/test/java/org/fireflyframework/security/adapter/opa/OpaPolicyDecisionAdapterIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void permitsAllowedActionForAnySubject() {
    OpaPolicyDecisionAdapter adapter = new OpaPolicyDecisionAdapter(webClient, "firefly/allow");
    StepVerifier.create(adapter.authorize(principal(Set.of("teller")), "read", "doc:1", Map.of()))
            .expectNextMatches(d -> d.granted())
            .verifyComplete();
}
```

## Detailed reference

### Where it sits in the platform

The security platform is layered hexagonally; dependencies point inward, and providers attach as outboard adapters:

```
security-api  →  security-spi  →  security-core  →  security-webflux  →  adapters
 (ports +         (driven           (neutral          (reactive             (this module:
  domain)          ports)            engine +          Spring Security        OPA policy-decision
                                     embedded PDP)     bindings)              adapter, + Vault, KMS,
                                                                              Keycloak, Cerbos, …)
```

- `security-api` defines the domain this adapter speaks in: `SecurityPrincipal` (the subject) and `Decision` (the outcome, with its `Effect` enum).
- `security-spi` defines the driven port this module implements: `PolicyDecisionPort`.
- `security-core` ships the in-process default, `EmbeddedPolicyDecisionAdapter`, which this module replaces.
- `security-webflux` consumes whichever `PolicyDecisionPort` bean is present through its `PolicyAuthorizationManager`, so swapping the embedded engine for OPA is transparent to the filter chain.
- This module is an outboard adapter: contribute an `OpaPolicyDecisionAdapter` bean and every non-permitted exchange is authorized by your OPA server instead of in-process rules.

This adapter depends only on `security-api`, `security-spi`, `spring-webflux`, and `jackson-databind`. It pulls in no vendor SDK and no Spring Boot auto-configuration. It is a plain port implementation you wire as a bean.

### What it provides

`OpaPolicyDecisionAdapter` contributes a single capability: a `PolicyDecisionPort` whose decisions come from a live OPA server.

- Reactive, SDK-free transport. Construction takes a `WebClient` whose base URL points at the OPA server plus a `decisionPath` (the OPA data path of the boolean allow rule, e.g. `firefly/allow`). A leading slash on the path is tolerated and stripped. The call is issued against `/v1/data/<decisionPath>` and is non-blocking throughout.
- A stable `input` shape. Every request sends OPA an `input` document with a nested `subject` (`subject`, `authorities`, `scopes`, `tenantId` drawn from the `SecurityPrincipal`), plus top-level `action`, `resource`, and `context` (a `null` context is sent as an empty object). Rego policies bind against this contract.
- Boolean-result mapping. OPA's `{"result": <bool>}` envelope is deserialized into the internal `OpaResult` record (ignoring unknown fields). `true` maps to `Decision.permit()`; anything else maps to `Decision.deny("denied by OPA policy")`.
- Fail-closed error handling. A transport error or any failure during evaluation is logged at WARN and resolved to `Decision.indeterminate("OPA error: …")` rather than propagated. a denial to every caller, never an exception and never a permit.

### Key types

| Type | Role |
| --- | --- |
| `OpaPolicyDecisionAdapter` | `PolicyDecisionPort` implementation; POSTs the `input` document to OPA's Data API and maps the boolean result to a `Decision`, fail-closed. |
| `OpaPolicyDecisionAdapter.OpaResult` | Package-private `record OpaResult(Boolean result)`. the deserialized subset of OPA's data response (`{"result": <bool>}`), `@JsonIgnoreProperties(ignoreUnknown = true)`. |

Port implemented (from `security-spi`): `PolicyDecisionPort`. Domain types consumed (from `security-api`): `SecurityPrincipal`, `Decision` (and its `Decision.Effect`).

### The request contract

For a call `authorize(principal, action, resource, context)`, the adapter POSTs to `/v1/data/<decisionPath>`:

```json
{
  "input": {
    "subject": {
      "subject": "u1",
      "authorities": ["teller"],
      "scopes": ["reports.read"],
      "tenantId": "acme"
    },
    "action": "read",
    "resource": "doc:1",
    "context": {}
  }
}
```

A Rego policy binds against this `input` and exposes a boolean rule at the decision path. For the path `firefly/allow`:

```rego
package firefly

default allow = false

allow {
    input.action == "read"
}

allow {
    input.subject.authorities[_] == "admin"
}
```

OPA replies `{"result": true}` or `{"result": false}`; a path with no defined rule yields a response with no `result`, which the adapter treats as a denial.

### Testing

The module is verified against a real Open Policy Agent server, not a mock. `OpaPolicyDecisionAdapterIntegrationTest` is a `@Testcontainers` test that starts the official `openpolicyagent/opa:0.70.0` image (`run --server --addr=0.0.0.0:8181`), waits for OPA's `/health` to return `200`, then uploads a Rego policy through the live Policy API (`PUT /v1/policies/firefly`). The policy permits the `read` action or any subject holding the `admin` authority. Tests drive the adapter with `StepVerifier` and assert every branch of the fail-closed contract:

- Permit (allowed action). A non-admin subject performing `read` is granted. `Decision.granted()` is `true`.
- Permit (admin override). An `admin` subject performing `write` is granted, proving the second Rego rule fires.
- Deny (explicit `false`). A non-admin subject performing `write` evaluates to `false` and is denied.
- Fail-closed (undefined path). Pointing the adapter at a non-existent decision path (`firefly/does_not_exist`) returns a non-granting `Decision` even for an `admin` subject. an absent result denies, exactly as production transport errors must.

This exercises the genuine HTTP round-trip, Rego evaluation, and JSON envelope against a containerized OPA, so the permit, explicit-deny, and undefined-path branches are proven, not assumed.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-opa/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-opa/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
