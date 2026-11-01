# Core starter API

The core starter configures shared HTTP-client infrastructure. In the onboarding service, use it when a verification-partner call needs the included client behavior. Application request/response models and partner credentials remain in the host service.

## HTTP contracts

| Contract | Use |
| --- | --- |
| [WebClientTemplate](../src/main/java/org/fireflyframework/core/web/client/WebClientTemplate.java) | Reactive GET, GET stream, POST, PUT, and DELETE helpers with request exchange context. |
| [WebClientProperties](../src/main/java/org/fireflyframework/core/config/WebClientProperties.java) | Bind firefly.webclient connection, TLS, proxy, pool, and codec settings. |
| [HeaderPropagationProperties](../src/main/java/org/fireflyframework/core/config/HeaderPropagationProperties.java) | Bind the enabled flag and allowed headers under firefly.webclient.propagation. |
| [TransactionFilterImpl](../src/main/java/org/fireflyframework/core/config/TransactionFilterImpl.java) | Carry the X-Transaction-Id request correlation value. |
| [WebClientAutoConfiguration](../src/main/java/org/fireflyframework/core/config/WebClientAutoConfiguration.java) | Create the configured WebClient infrastructure when its conditions match. |

`WebClientTemplate.get(baseUrl, path, responseType, exchange)` returns a `Mono<T>`; the overload accepting `ParameterizedTypeReference<T>` handles generic response types. `getFlux` returns a `Flux<T>`. Compose those publishers in the service operation rather than blocking a WebFlux request thread.

## Keep transport configuration separate

[EDA](../../../docs/site/modules/eda.md) owns event publisher/listener annotations and transport selection. Its current publisher enum includes APPLICATION_EVENT, KAFKA, RABBITMQ, POSTGRES, NOOP, and AUTO. Broker adapters and connection settings must be supplied for the selected transport.

[The client component](../../../docs/site/modules/client.md) offers explicit REST, SOAP, and gRPC builders. [Orchestration](../../../docs/site/modules/orchestration.md) owns workflow/saga/TCC definitions and recovery behavior. Choose these integrations when the application needs their contracts.

## Setup and tests

- [Core starter guide](../../../docs/site/modules/starter-core.md).
- [Configuration](CONFIGURATION.md).
- [Starter tests](../src/test).
