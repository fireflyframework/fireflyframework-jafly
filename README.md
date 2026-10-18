# jafly — Firefly Framework for Java

Java application architecture on **Spring Boot 4.1.1**, Spring Cloud 2025.1.3, Spring WebFlux and Java 25.

This repository consolidates the 63 Java framework repositories into one Maven reactor and one release train. Existing `org.fireflyframework` artifact names and Java packages are preserved. Python, PHP, Agentic, Go, Rust, .NET and frontend frameworks retain their own repositories.

## Build

Requirements: JDK 25, Maven 3.9+, and Docker for container-backed integration tests.

```sh
python3 -m unittest discover -s tests -v
mvn -B -ntp clean verify
```

Build a module together with its prerequisites:

```sh
mvn -B -ntp -pl :fireflyframework-web -am verify
```

Run `mvn install` before building a standalone example under `modules/client/examples/complete-example`. That example deliberately keeps its own Spring Boot parent and is outside the default framework reactor. Agentic bridge samples remain in the reactor and are not published.

## Repository structure

```text
pom.xml                    Build aggregation; not a consumer parent
build/parent/pom.xml       Published fireflyframework-parent
build/bom/pom.xml          Published fireflyframework-bom
modules/                   61 framework source repositories
  kernel/                  Shared exceptions and foundational abstractions
  cache*/                  Cache core and providers
  security*/               Security API, SPI, engines and adapters
  eda*/                    Messaging core and transports
  ecm*/                    Content management and adapters
  notifications*/          Notification core and transports
  starter-*/               Core, domain, data and application starters
  agentic-bridge/          Java integration with the Python Agentic framework
scripts/                   Versioning and release verification
tests/                    Monorepo and release contract tests
docs/migration/            Original source SHAs, history map and migration evidence
```

Each imported module retains its README, documentation, resources, tests, examples and license. Multi-module components retain their internal layout. The root `.github/workflows` directory owns all active Java CI/CD; nested imported workflow files are historical and are not executed by GitHub Actions.

## Use from applications

Choose either the framework parent or the BOM. The parent supplies build and dependency management; the independent BOM works with an existing application parent. Keep your application's version independent of the framework version.

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.10.17-SNAPSHOT</version>
    <relativePath/>
</parent>
```

Or import the BOM:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.fireflyframework</groupId>
            <artifactId>fireflyframework-bom</artifactId>
            <version>26.10.17-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare existing artifacts without repeating their version:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-starter-domain</artifactId>
</dependency>
```

`26.10.17-SNAPSHOT` is the migration development train. Install it locally with `mvn install`; use only a version whose publication has been verified when configuring a registry consumer. Existing published `26.07.06` coordinates remain immutable.

## Version and release

All framework artifacts use a single padded CalVer version (`YY.MM.DD`, or `YY.MM.DD-SNAPSHOT` during development). Change the train with:

```sh
python3 scripts/set_version.py 26.10.17
python3 -m unittest discover -s tests -v
mvn -B -ntp clean verify
```

A stable release must use the matching `vYY.MM.DD` tag. Publishing is centralized in the root release workflow and fails on build, signing, deployment or exact publication verification errors. Do not run the old individual-repository release workflows. See [migration and compatibility](docs/migration/README.md) before upgrading a Boot 3 application.

## History

[Source provenance](docs/migration/source-manifest.json) maps all 63 repositories to destination paths, original commit IDs, import commits, branches and tags. Imports preserve original commit ancestry. Historical refs are namespaced `legacy/<source-repository>/<original-ref>` to avoid collisions between the old repositories' identical release tags. Original GitHub issue, pull request and release records retain their source URLs.
