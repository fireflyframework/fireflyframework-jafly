![Jafly. Security Vault Adapter](../../docs/site/assets/modules/security-adapter-vault.svg)

# Security Vault Adapter

Read a configured field from HashiCorp Vault KV v2 through SecretsPort.

Construct VaultSecretsAdapter with a Vault WebClient carrying X-Vault-Token, the KV mount, and value field name. GetSecret reads /v1/{mount}/data/{name}; a missing field or transport failure produces an error signal. Supply tokens through the runtime environment, not committed YAML.

[Module guide](../../docs/site/modules/security-adapter-vault.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

Resolve a verification partner's credential through SecretsPort at runtime. Provision the KV v2 secret and Vault access policy; the adapter reads the configured field.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-security-adapter-vault</artifactId>
    <version>26.10.20-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Set `VAULT_ADDR` and `VAULT_TOKEN` in the application's runtime environment. The following application-owned Spring bean uses the adapter's actual three-argument constructor; `secret` is the KV v2 mount and `value` is the field to read. It does not create the Vault mount, policy, or secret.

```java
@Bean
SecretsPort secretsPort() {
    WebClient client = WebClient.builder()
            .baseUrl(System.getenv("VAULT_ADDR"))
            .defaultHeader("X-Vault-Token", System.getenv("VAULT_TOKEN"))
            .build();
    return new VaultSecretsAdapter(client, "secret", "value");
}
```

Inject `SecretsPort` and call `getSecret("payments-api")` as part of a reactive flow. The returned `Mono<String>` contains the configured field; missing secrets/fields and HTTP failures propagate as `IllegalStateException`. Imports: `org.fireflyframework.security.spi.SecretsPort`, `org.fireflyframework.security.adapter.vault.VaultSecretsAdapter`, Spring's `Bean`, and WebFlux `WebClient`. This module provides explicit adapter construction, not a `firefly.vault.*` auto-configuration namespace.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`VaultSecretsAdapter`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/security-adapter-vault/src/main/java/org/fireflyframework/security/adapter/vault/VaultSecretsAdapter.java) | Reads one configured field from Vault KV v2 through SecretsPort; missing fields and HTTP failures propagate as errors. |

## Continue the application

Use the [Client guide](../../docs/site/modules/client.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-security-adapter-vault -am verify
./mvnw -pl :fireflyframework-security-adapter-vault -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/security-adapter-vault.md).
