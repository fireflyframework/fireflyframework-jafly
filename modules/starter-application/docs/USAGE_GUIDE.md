# Resolve application context for a review operation

A reviewer-facing onboarding endpoint can resolve the authenticated subject and tenant configuration before calling a domain operation. Keep those values tied to the validated security context.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

The current package is `org.fireflyframework.common.application`. Use `ContextResolver` and `ConfigResolver` with the controller bases when their contracts fit the service. The starter has its own `Secure` annotation and security aspect; it is distinct from the security-api annotation/method-policy binding. Follow the matching component contract rather than mixing the two annotation types.

## Configuration and API

- [Component guide](../../../docs/site/modules/starter-application.md) for dependency selection, wiring, configuration, and public contracts.
- [Resource-server JWT validation](../../../docs/site/modules/security-resource-server.md) for the authenticated HTTP boundary.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.
