# Use the internal identity store

Use the internal database adapter when the deployment owns applicant or reviewer identities. Identity records and credentials are separate from the submitted onboarding application.

## Start from the application

[The onboarding tutorial](../../../docs/site/getting-started.md) contains the complete Maven project, controller, command/query handlers, and HTTP tests. Use JDK 25 and the repository Maven wrapper. [Installation and version availability](../../../docs/site/installation.md) explains the current build and published versions.

## Choose the integration

Add `fireflyframework-security-idp-internal-db`, select `firefly.security.idp.provider=internal-db`, and configure `firefly.security.idp.internal-db.jwt` with the deployment secret and issuer. The adapter uses R2DBC and bundled database migrations. The common `/idp` controller exposes the adapter in a reactive host application.

## Configuration and API

- [Component guide](../../../docs/site/modules/security-idp-internal-db.md) for dependency selection, wiring, configuration, and public contracts.
- [Identity API and current /idp routes](../../../docs/site/modules/security-idp.md) for the common adapter/controller contract.
- [Source and tests](..) for the implementation and integration fixtures.

## Check the application

The complete onboarding example runs its own tests from the repository root:

```sh
./mvnw -B -ntp -f docs/examples/getting-started/pom.xml verify
```

That suite checks the tutorial service. Provider/database behavior added later needs its own integration tests and configured dependencies.

## Initial database account

The bundled [V1 migration](../src/main/resources/db/migration/V1__Create_IDP_Tables.sql) seeds an `admin` account. Its comments identify `admin123` as the initial password. Replace or disable the seeded credential before exposing the application, and verify migrations against a disposable PostgreSQL instance first.

Use the corrected [example configuration](../src/main/resources/application-internal-db-example.yml) for the current property hierarchy. Supply `IDP_JWT_SECRET` and `DB_PASSWORD` through the deployment environment. The example contains no usable secret fallback.

## Verify this module

From the Jafly root with Java 25 and Spring Boot 4.1.1:

```sh
./mvnw -B -ntp -pl :fireflyframework-security-idp-internal-db -am verify
```

The adapter and [shared controller](../../security-idp/src/main/java/org/fireflyframework/security/idp/web/IdpController.java) define the current contract. The [API notes](API.md) describe response mapping and the authorization-header normalization the host must verify.
