![Jafly. Security OpenFGA Adapter](../assets/modules/security-adapter-openfga.svg)

# Security OpenFGA Adapter

Check relationship authorization through an OpenFGA store.

Construct OpenFgaRelationshipAdapter with a WebClient and store ID, then call check(subject, relation, object). The adapter posts tuple_key to the store check route and returns false for transport errors. Provision the authorization model and tuples in OpenFGA separately.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-security-adapter-openfga` | `jar` | [modules/security-adapter-openfga](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-openfga) |

Part of [Security and identity](index.md#security-and-identity).

## In the onboarding service

Use RelationshipPort to check whether a reviewer is related to a particular application resource. Provision the OpenFGA model and relationship tuples before invoking the check.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-openfga</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Set `OPENFGA_URL` and `OPENFGA_STORE_ID` for an existing store whose model and relationship tuples have been provisioned. Register the adapter through the security SPI:

```java
@Bean
RelationshipPort relationshipPort() {
    WebClient client = WebClient.builder()
            .baseUrl(System.getenv("OPENFGA_URL"))
            .build();
    return new OpenFgaRelationshipAdapter(
            client, System.getenv("OPENFGA_STORE_ID"));
}
```

Call `relationshipPort.check("user:alice", "viewer", "document:budget")` to obtain `Mono<Boolean>`. The adapter sends these values as `tuple_key.user`, `tuple_key.relation`, and `tuple_key.object` to `/stores/{storeId}/check`. It returns `false` on transport errors. Add any deployment authentication headers to the host-owned `WebClient`. Imports: `org.fireflyframework.security.spi.RelationshipPort`, `org.fireflyframework.security.adapter.openfga.OpenFgaRelationshipAdapter`, Spring `Bean`, and WebFlux `WebClient`.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`OpenFgaRelationshipAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-openfga/src/main/java/org/fireflyframework/security/adapter/openfga/OpenFgaRelationshipAdapter.java) | Maps subject/relation/object checks to an OpenFGA store; transport failures return false. |

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
./mvnw -pl :fireflyframework-security-adapter-openfga -am verify
./mvnw -pl :fireflyframework-security-adapter-openfga -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [OpenFgaRelationshipAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-openfga/src/test/java/org/fireflyframework/security/adapter/openfga/OpenFgaRelationshipAdapterIntegrationTest.java).

Some tests use Testcontainers. A working Docker-compatible container runtime is required for those fixtures; inspect their annotations and the build output to distinguish executed tests from environment-dependent skips.

Mock, local-container, and application-context tests do not establish acceptance against a live provider account. Validate the intended provider deployment and credentials separately.

## Test fixture

The `allowsRelatedSubject` test demonstrates one concrete behavior. Excerpt from [OpenFgaRelationshipAdapterIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-openfga/src/test/java/org/fireflyframework/security/adapter/openfga/OpenFgaRelationshipAdapterIntegrationTest.java). The test class supplies its fixtures and imports.

```java
@Test
void allowsRelatedSubject() {
    OpenFgaRelationshipAdapter adapter = new OpenFgaRelationshipAdapter(webClient, storeId);
    StepVerifier.create(adapter.check("user:alice", "viewer", "document:budget"))
            .expectNext(true).verifyComplete();
}
```

## Continue the application

Use the [Security SPI guide](security-spi.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-openfga/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/security-adapter-openfga/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
