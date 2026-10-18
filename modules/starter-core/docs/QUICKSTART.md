# Add shared service infrastructure

Use the core starter when the onboarding service needs the included HTTP-client and operational infrastructure. The first-service tutorial uses a smaller dependency set; adopting a starter is a separate dependency decision.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

The starter provides `WebClientTemplate` and its configured `WebClient`. Configure downstream header forwarding deliberately so applicant data and inbound credentials reach only authorized destinations. Messaging annotations belong to the EDA component, not to the removed `core.messaging.annotation` package; use the EDA guide when adding events.

## Configuration and API

- [Component guide](../../../docs/site/modules/starter-core.md) for dependency selection, wiring, configuration, and public contracts.
- [EDA publisher and transport choices](../../../docs/site/modules/eda.md) when the service publishes events.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.
