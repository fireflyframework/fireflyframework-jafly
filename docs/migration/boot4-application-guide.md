# Move to the Jafly monorepo and Boot 4

![Move to the Jafly monorepo and Boot 4](../site/assets/banners/migration.svg)

This migration combines the Java repositories and upgrades the application platform. Maven artifact IDs and `org.fireflyframework` packages remain intact; the Spring platform change requires application validation.

## What changes

| Area | Source baseline | Current migration train |
| --- | --- | --- |
| Repository | Separate parent, BOM, and component repositories | `fireflyframework-jafly` |
| Spring Boot | 3.5.10 | 4.1.1 |
| Spring Cloud | 2025.0.1 | 2025.1.3 |
| Java | Framework source already targeted Java 25 | Java 25 |
| Framework versions | Independent repository workflows | One framework version train |
| Maven coordinates | `org.fireflyframework` artifacts | Preserved |
| Framework package names | `org.fireflyframework.*` | Preserved |

The table describes the checked-in migration target. It does not claim that an unpublished snapshot is available from Maven Central.

## Update repository tooling

1. Check out `fireflyframework/fireflyframework-jafly`.
2. Build from the root with the checked-in Maven wrapper.
3. Use `-pl :artifact-id -am` for focused builds instead of cloning a dependency graph of separate Java repositories.
4. Point custom scripts and documentation links to `build/parent`, `build/bom`, and `modules/<component>`.
5. Use the root CI/release workflows. Imported nested workflow files are historical and do not run from module directories.

The original repository's main commit remains in the ancestry of the monorepo. Source branches/tags are retained as namespaced legacy refs. Existing issue, PR, and release records remain authoritative at their original GitHub URLs.

## Validate the Boot 4 application boundary

Spring APIs. Boot 4 adopts Spring Framework 7, Spring Security 7, and Jakarta EE 11. Recompile application customizations, security configuration, HTTP integrations, and test support against the selected platform.

Focused Boot modules. Auto-configuration and test APIs moved into technology-specific modules. Update imports and dependencies together; adding a dependency alone does not fix a moved Java package.

Jackson compatibility. The migration preserves existing public Jackson 2 types through Boot's `spring-boot-jackson2` compatibility module and Boot-managed Jackson 2 versions. Jackson 3 can coexist. Do not mix independently pinned Jackson versions into the managed train.

For Jackson 2 configuration, review these Boot 4 settings where applicable:

```properties
spring.http.codecs.preferred-json-mapper=jackson2
spring.http.converters.preferred-json-mapper=jackson2
```

Move Jackson 2 binding settings from `spring.jackson.*` to `spring.jackson2.*`. Validate actual HTTP bodies and configured codecs in the application; a successful compile cannot establish serialization compatibility.

Observability. Tracing OTLP configuration uses `management.opentelemetry.tracing.export.otlp.*`. Review deployment configuration and collector connectivity.

Testcontainers. Version 2 changes Maven module names. Keep Docker available and execute provider-backed tests against the infrastructure your application uses.

Provider integrations. Cloud SDK starters, Springdoc, resilience, and security integrations move with the Boot 4 generation. Verify authentication, connection configuration, and failure handling against your actual providers.

## Suggested application verification

- Compile with JDK 25 and inspect the effective dependency tree.
- Run unit and integration tests, including serialization and security behavior.
- Start the application with its real deployment configuration in a test environment.
- Exercise database migrations, event contracts, authorization policies, provider callbacks, and observability endpoints used by that application.
- Verify the selected framework version's registry publication before replacing local/source resolution.

## Provenance and rollback

The repository retains the [source manifest](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/docs/migration/source-manifest.json), original source commit ancestry, and legacy refs. Original artifacts and release records are not overwritten. See the complete [migration record](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/docs/migration/README.md) for import boundaries, generated-output exclusions, and cutover evidence.

## Primary references

- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Cloud release-train compatibility](https://spring.io/projects/spring-cloud/)
- [Maven's multi-module reactor](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
