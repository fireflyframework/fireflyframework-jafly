# Internal database identity provider

Use this adapter when your deployment stores and manages its own user identities in PostgreSQL. The onboarding application record and the identity account are separate records with different lifecycles.

- [Integration](INTEGRATION.md): dependencies, provider selection, database configuration, and migration setup.
- [API](API.md): the Java adapter contract and the common `/idp` controller routes.
- [DTO field mapping](DTO_FIELD_MAPPING.md): names and types used at the adapter boundary.
- [Technical details](technical-details.md): services, storage, and configuration defaults.
- [Historical implementation report](PROJECT_STATUS.md): the October 2025 implementation snapshot.

For a running application, begin with the [tested onboarding service](../../../docs/site/getting-started.md). Its HTTP tests do not exercise the identity provider or a PostgreSQL deployment. Add those integration checks when connecting identity accounts to the application.

Build from the Jafly repository root with Java 25:

```bash
./mvnw -B -ntp -pl :fireflyframework-security-idp-internal-db -am verify
```
