# Call a verification partner

The onboarding service can call a document-verification partner after accepting an application. Choose the protocol used by that partner and keep its endpoint, credentials, and retry policy in application configuration.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

Use `RestClient` for HTTP, `GrpcClient<T>` with the generated service stub for gRPC, or `SoapClient` with the partner WSDL for SOAP. These types are in `org.fireflyframework.client`; helper classes use their protocol packages such as `org.fireflyframework.client.graphql` and `org.fireflyframework.client.oauth2`. The builder entry point is `ServiceClient`. Spring wiring and environment defaults are described in the component guide.

## Configuration and API

- [Component guide](../../../docs/site/modules/client.md) for dependency selection, wiring, configuration, and public contracts.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.
