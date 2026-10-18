# Notify an onboarding partner

Use outbound callbacks when a partner must receive an application-status event over HTTP. Operate the callback web service separately from the onboarding service, or compose its core services in a host application.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

The setup order is: authorize the destination domain, configure the callback destination and event types, configure the inbound event subscription, then publish a matching event. Inspect execution records to distinguish a saved configuration from a successful HTTP delivery. The callback web application needs its database/migrations and messaging configuration; credentials and endpoints come from the deployment.

## Configuration and API

- [Component guide](../../../docs/site/modules/callbacks.md) for dependency selection, wiring, configuration, and public contracts.
- [Callback web service](../../../docs/site/modules/callbacks-web.md) for service configuration and controller sources.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.
