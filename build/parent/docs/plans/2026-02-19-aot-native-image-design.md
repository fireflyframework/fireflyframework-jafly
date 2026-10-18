# AOT / Native Image Support in Parent POM

**Date**: 2026-02-19
**Status**: Approved

## Context

The fireflyframework-parent POM does not inherit from `spring-boot-starter-parent` (it uses `spring-boot-dependencies` as a BOM). This means the `native` and `nativeTest` profiles that Spring Boot's starter parent provides are not available to child modules. With 50+ modules in the ecosystem, centralizing native image configuration in the parent POM avoids duplication and inconsistency.

## Decision

Add a `native` Maven profile to the parent POM with `pluginManagement` entries for:

1. **`spring-boot-maven-plugin`** with `process-aot` goal and Paketo Buildpacks image configuration
2. **`native-maven-plugin`** (GraalVM) with `build` goal bound to the `package` phase

## Design

### Version Property

```xml
<native-maven-plugin.version>0.11.4</native-maven-plugin.version>
```

### Native Profile

The profile uses `pluginManagement` so child modules opt-in by declaring the plugins without version or configuration.

**spring-boot-maven-plugin**:
- Adds `process-aot` execution goal for Spring AOT processing
- Configures Paketo Buildpacks: `paketobuildpacks/builder-jammy-tiny` builder with `BP_NATIVE_IMAGE=true`

**native-maven-plugin**:
- GraalVM `org.graalvm.buildtools:native-maven-plugin`
- `build` goal bound to `package` phase for local native compilation

### Usage by Child Modules

Child modules activate native builds by:
1. Activating the profile: `mvn -Pnative package`
2. Declaring the plugins in their own POM (version/config inherited from parent)

Two build paths:
- `mvn -Pnative package` — local GraalVM native executable
- `mvn -Pnative spring-boot:build-image` — Paketo container with native executable

### What Is NOT Included

- No `nativeTest` profile (can be added later)
- No `RuntimeHints` implementations (responsibility of individual modules)
- No changes to existing profiles or plugin configurations
