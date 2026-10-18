![Jafly. Security OpenFGA Adapter](../../docs/site/assets/modules/security-adapter-openfga.svg)

# Security OpenFGA Adapter

Check relationship authorization through an OpenFGA store.

Construct OpenFgaRelationshipAdapter with a WebClient and store ID, then call check(subject, relation, object). The adapter posts tuple_key to the store check route and returns false for transport errors. Provision the authorization model and tuples in OpenFGA separately.

[Module guide](../../docs/site/modules/security-adapter-openfga.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Use RelationshipPort to check whether a reviewer is related to a particular application resource. Provision the OpenFGA model and relationship tuples before invoking the check.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-openfga</artifactId>
    <version>26.10.19</version>
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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`OpenFgaRelationshipAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-openfga/src/main/java/org/fireflyframework/security/adapter/openfga/OpenFgaRelationshipAdapter.java) | Maps subject/relation/object checks to an OpenFGA store; transport failures return false. |

## Continue the application

Use the [Security SPI guide](../../docs/site/modules/security-spi.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-adapter-openfga -am verify
./mvnw -pl :fireflyframework-security-adapter-openfga -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-adapter-openfga.md).
