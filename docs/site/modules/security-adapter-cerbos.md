![Jafly. Security Cerbos Adapter](../assets/modules/security-adapter-cerbos.svg)

# Security Cerbos Adapter

Delegate PolicyDecisionPort decisions to the Cerbos CheckResources API.

Construct CerbosPolicyDecisionAdapter with a WebClient pointing to Cerbos. Resources are interpreted as kind:id; principal authorities become roles and context becomes resource attributes. Only EFFECT_ALLOW permits; transport failure returns an indeterminate decision.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-adapter-cerbos` | `jar` | [modules/security-adapter-cerbos](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-cerbos) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

A reviewer action can be checked against Cerbos when this adapter is the configured PolicyDecisionPort. Map the application resource to kind:id and supply the attributes required by the deployed policy.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-cerbos</artifactId>
    <version>26.10.17</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Set `CERBOS_URL` for the Cerbos HTTP API and deploy a policy whose version is `default`, which is the version sent by this adapter. Register it as the framework policy decision port:

```java
@Bean
PolicyDecisionPort policyDecisionPort() {
    WebClient client = WebClient.builder()
            .baseUrl(System.getenv("CERBOS_URL"))
            .build();
    return new CerbosPolicyDecisionAdapter(client);
}
```

With a framework `SecurityPrincipal`, call `policyDecisionPort.authorize(principal, "read", "document:budget", Map.of())`. The resource becomes kind `document`, id `budget`; authorities become Cerbos roles, and the context map becomes resource attributes. The adapter calls `/api/check/resources`; only `EFFECT_ALLOW` yields `Decision.permit()`. Other effects deny, and transport errors yield `Decision.indeterminate(...)`. Imports: `org.fireflyframework.security.spi.PolicyDecisionPort`, `org.fireflyframework.security.adapter.cerbos.CerbosPolicyDecisionAdapter`, Spring `Bean`, and WebFlux `WebClient`.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`CerbosPolicyDecisionAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-cerbos/src/main/java/org/fireflyframework/security/adapter/cerbos/CerbosPolicyDecisionAdapter.java) | Maps principals, actions, resources, and context to Cerbos CheckResources decisions; non-allow effects deny. |

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
./mvnw -pl :fireflyframework-security-adapter-cerbos -am verify
./mvnw -pl :fireflyframework-security-adapter-cerbos -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [CerbosPolicyDecisionAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-cerbos/src/test/java/org/fireflyframework/security/adapter/cerbos/CerbosPolicyDecisionAdapterIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `permitsAdminRole` test demonstrates one concrete behavior. Excerpt from [CerbosPolicyDecisionAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-cerbos/src/test/java/org/fireflyframework/security/adapter/cerbos/CerbosPolicyDecisionAdapterIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void permitsAdminRole() {
    StepVerifier.create(adapter().authorize(principal(Set.of("admin")), "view", "document:1", Map.of()))
            .expectNextMatches(d -> d.granted())
            .verifyComplete();
}
```

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-cerbos/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-cerbos/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
