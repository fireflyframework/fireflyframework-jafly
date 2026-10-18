# Monorepo and Spring Boot migration

The migration combines 63 Java repositories, while preserving all original artifact coordinates, packages and tracked content at the import boundary. `source-manifest.json` records exact source tree/commit identities and all original branches/tags. Subsequent build and Boot 4 edits are separate from history imports. The 347 generated Maven target files tracked by two source repositories are excluded from the active source tree and listed in generated-output-exclusions.json; their exact bytes remain in the import commits and historical refs. The [legacy records archive](https://github.com/fireflyframework/fireflyframework-jafly/releases/tag/legacy%2Fcollaboration-archive%2F20261018T064326Z) provides portable collaboration records and Git bundles for the retired source repositories.

## Scope and release train

The organization inventory contained 93 repositories. The Java migration includes parent, BOM and 61 component repositories. The Java book, CLI, security documentation, website and development skills are downstream ecosystem projects. Other language framework monorepos and standalone Python products are not folded into Java.

`pom.xml` is an aggregation POM. `build/parent/pom.xml` supplies consumer build/dependency management; `build/bom/pom.xml` is independent. The parent does not import its own BOM, avoiding a model-resolution cycle. Framework dependency versions in the public parent use `fireflyframework.version`, so an application version such as `1.0.0` does not accidentally change its framework dependencies. The BOM now includes the previously omitted `fireflyframework-security-bff`.

A single concrete `26.10.18` version identifies development. The version updater validates the monthly CalVer train (`YY.MM.PP`, with `PP` as the release counter) and all input XML before writing, updates framework project/parent/dependency properties, preserves external dependency versions, and ignores generated `target` directories. Existing release versions are never overwritten.

## Boot 4 compatibility

The requested upgrade targets the latest stable releases verified from Maven Central metadata: Spring Boot 4.1.1 and compatible Spring Cloud 2025.1.3. Preview releases (Boot 4.2 milestones and Cloud 2026 milestones) are excluded. The source baseline was Boot 3.5.10 / Cloud 2025.0.1.

Boot 4 uses Spring Framework 7, Spring Security 7 and Jakarta EE 11. This is a major platform upgrade. An application should rebuild and execute its own tests before adopting this train.

- Technology auto-configuration and test APIs now live in focused Boot modules; technology starters and imports are migrated together.
- Security authorization managers implement the Spring Security 7 authorization contract. Authorization-server configuration uses the supported endpoint matcher and authentication rules.
- HTTP header handling follows Spring 7's dedicated header APIs.
- Existing public Jackson 2 types are retained using Boot's `spring-boot-jackson2` compatibility module and the centrally managed Jackson 2 release train. Jackson 3 can coexist; framework codec configuration explicitly preserves Jackson 2 serialization where required.
- Applications using Jackson 2 binding must migrate `spring.jackson.*` settings to `spring.jackson2.*`. For default Boot HTTP codecs/converters, select `spring.http.codecs.preferred-json-mapper=jackson2` and `spring.http.converters.preferred-json-mapper=jackson2` as applicable.
- Tracing OTLP settings use Boot's current `management.opentelemetry.tracing.export.otlp.*` namespace.
- Testcontainers 2 uses renamed Maven module coordinates. Docker is required for tests that exercise real PostgreSQL, Kafka, RabbitMQ and other providers.
- AWS, Azure, Google Cloud, Springdoc and Resilience4j integrations move to their Boot 4 generation; Platform dependencies follow the selected Boot BOM, with explicit security constraints shared by the framework parent and independent BOM.

## Development and history

Use one checkout and one Maven invocation. `mvn -pl :artifact-id -am verify` builds a selected module with dependencies. No internal library should need a separately published GitHub package just to compile the reactor. Module documentation remains local to its original component; old individual repository workflow files are inert under `modules/`.

All original branch and tag objects are preserved with a `legacy/<repository>/` prefix. Original main commit IDs are ancestors of the new main branch. Source branch histories are retained without silently merging outstanding changes. Historical GitHub issues, PRs, reviews, and releases are exported as portable JSON records. Original URLs identify those records but stop resolving after repository retirement. Archive-only tags preserve the final source trees and all referenced PR/review commits; these commits are not silently integrated into active framework code.

## Cutover and rollback

Organization orchestration and CLI setup use the single Jafly repository. Build, version, and publication commands operate at its root; module paths are not separate Git checkouts. The source cutover verifies the replacement CI and live Maven consumers, retires legacy build/release workflows, freezes each source repository, and captures its final refs before retirement.

The [legacy records archive](https://github.com/fireflyframework/fireflyframework-jafly/releases/tag/legacy%2Fcollaboration-archive%2F20261018T064326Z) contains the original repository records, final refs, context-commit inventory, source bundles, checksums, and restore instructions. Immutable `legacy/<repository>/collaboration-archive/…` tags point to archive-only commits whose trees match the final source heads and whose ancestry preserves the original and reviewed commits. These tags supplement the original imported refs.

Follow the [tested source restoration guide](restore-legacy-sources.md) to recover the documented bundles and refs into a new repository. GitHub collaboration records are portable exports; they are not native destination PRs or issues. Old workflow logs and Actions artifact binaries are not included. Historical Maven binaries and their old download URLs are not guaranteed by a source-code archive. New Jafly publication must pass its live association and byte-verification gates independently.

## Primary references

- [Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Cloud compatibility](https://spring.io/projects/spring-cloud/)
- [Maven reactor](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Maven dependency management and BOMs](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
