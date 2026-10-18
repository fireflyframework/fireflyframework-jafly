# Maven parent and BOM

![Maven parent and BOM](assets/banners/maven.svg)

Use the framework parent to adopt its build management, or import the BOM while keeping your existing parent. Both manage the same Jafly dependency versions. Your application keeps its own version.

## Choose the management model

| Capability | Framework parent | Framework BOM |
| --- | --- | --- |
| Framework dependency versions | Yes | Yes |
| Spring dependency management from the framework parent | Yes | Keep your existing platform management |
| Java/compiler and Maven plugin management | Yes | Configure in your own parent/build |
| Compatible with another application parent | Maven permits one parent | Yes |
| Adds runtime libraries automatically | No | No |

## Use the framework parent

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.10.17-SNAPSHOT</version>
    <relativePath/>
</parent>

<groupId>com.example</groupId>
<artifactId>onboarding-service</artifactId>
<version>0.1.0-SNAPSHOT</version>

<dependencies>
    <dependency>
        <groupId>org.fireflyframework</groupId>
        <artifactId>fireflyframework-cqrs</artifactId>
    </dependency>
</dependencies>
```

The managed CQRS dependency uses the framework's `fireflyframework.version` property. The application version `0.1.0-SNAPSHOT` does not change the CQRS dependency version. An empty `relativePath` tells Maven to resolve the consumer parent from the local/remote repository rather than an unrelated adjacent source file.

The parent manages plugin configuration; a plugin in `pluginManagement` executes only when the build or lifecycle activates it. Likewise, a managed dependency becomes a runtime dependency only when an application declares it or receives it transitively.

## Keep your existing parent

Import the framework BOM into your dependency management:

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

Declare the framework dependencies you need without repeating their version. Your existing parent or build must still configure a compatible Spring Boot/Cloud platform and Java 25 compiler. The BOM manages Jafly artifact versions only.

## Resolve the management POMs

Before using the parent or BOM in a separate application, install the source locally or select a verified published version. Use `fireflyframework-parent` or `fireflyframework-bom` for application management. The checkout's root `fireflyframework-jafly` POM aggregates source builds and cannot serve as a consumer parent.

For source builds, follow [installation](installation.md). For application code and tests, continue with [your first service](getting-started.md).

## Diagnose dependency selection

```sh
mvn dependency:tree
mvn help:effective-pom
```

Inspect effective dependency management when an application imports several BOMs. Maven precedence and explicit application versions still apply; importing a BOM does not override every later application decision.

The examples use the development train. Refer to [installation](installation.md) for local installation and [versioning](versioning.md) for verified final versions.

<p class="jafly-source">Source: <a href="https://github.com/fireflyframework/fireflyframework-jafly/blob/main/build/parent/pom.xml">parent POM</a> and <a href="https://github.com/fireflyframework/fireflyframework-jafly/blob/main/build/bom/pom.xml">BOM</a>. Maven's <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html">dependency-management reference</a> explains resolution precedence.</p>
