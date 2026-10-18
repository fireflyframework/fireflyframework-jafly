# Firefly Framework - Plugins

[![CI](https://github.com/fireflyframework/fireflyframework-plugins/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-plugins/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> A lightweight, framework-independent plugin system for the Firefly Framework — declare plugins and extension points with annotations, manage their lifecycle, and resolve load order with dependency-aware topological sorting.

---

## Table of Contents

- [Overview](#overview)
  - [Modules](#modules)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

`fireflyframework-plugins` provides a small, deliberately simple plugin architecture for extending Firefly Framework applications. It lets host applications expose **extension points** (interfaces that plugins can implement) and lets plugins **contribute extensions** to those points, all with explicit lifecycle management (`initialize` → `start` → `stop` → `destroy`) and dependency-ordered loading.

The module is intentionally **non-reactive and framework-independent**. Plugin operations (load, start, stop, unload) are discrete, synchronous state transitions, so the API uses plain Java methods and checked `PluginException`s rather than `Mono`/`Flux` and reactive error channels. The only hard dependency is [`fireflyframework-kernel`](https://github.com/fireflyframework/fireflyframework-kernel) (for the shared `FireflyException` base type) plus SLF4J for logging — meaning the plugin system can be embedded in any Java 21+ application, with or without Spring. This is a complete, simplified reimplementation of the legacy plugin manager (see [DESIGN_RATIONALE.md](DESIGN_RATIONALE.md) for the full rationale and the features that were intentionally removed).

Plugins are loaded explicitly from the classpath via `PluginManager.loadPlugin(Class)` — there is no JAR/Git/hot-deploy loading and no automatic classpath scanning, which keeps the system predictable and secure. Extensions are discovered automatically from `@Extension`-annotated inner classes of each plugin and registered with the `ExtensionRegistry`, ordered by an explicit integer priority.

Within the Firefly Framework, this module sits alongside the other foundational libraries (kernel, observability) and gives services a uniform way to make a subsystem pluggable without reinventing lifecycle and ordering logic. The `plugin-core` module optionally integrates with [`fireflyframework-observability`](https://github.com/fireflyframework/fireflyframework-observability) when present on the classpath.

### Modules

This is a multi-module Maven build. The aggregator POM (`fireflyframework-plugins`, packaging `pom`) inherits from `fireflyframework-parent` and builds two JAR submodules:

| Module | Artifact | Purpose |
|--------|----------|---------|
| **Plugin API** | `plugin-api` | The public contract: the `@Plugin`, `@Extension`, and `@ExtensionPoint` annotations; the `Plugin`, `PluginManager`, and `ExtensionRegistry` interfaces; the `PluginMetadata`, `PluginDescriptor`, and `PluginState` models; and `PluginException`. Depend on this from any module that **defines** plugins or extension points. Its only dependency is `fireflyframework-kernel`. |
| **Plugin Core** | `plugin-core` | The default runtime: `DefaultPluginManager` (lifecycle + extension wiring), `DefaultExtensionRegistry` (thread-safe, priority-ordered extension registry), and `PluginDependencyResolver` (topological-sort load ordering with circular/missing-dependency detection). Depend on this from the **host application** that loads and runs plugins. It has an optional dependency on `fireflyframework-observability`. |

## Features

- **Annotation-driven declaration** — `@Plugin` (id, name, version, description, author, dependencies), `@ExtensionPoint` (id, description), and `@Extension` (extensionPointId, priority, description).
- **Explicit lifecycle** — every plugin implements `initialize()`, `start()`, `stop()`, and `destroy()`; the manager tracks state transitions through the `PluginState` enum (`LOADED → INITIALIZED → STARTED → STOPPED`, plus `FAILED`).
- **`PluginManager`** — load, start, stop, and unload plugins by id; query loaded plugins and their `PluginDescriptor`s; managed init/shutdown of the manager itself. `DefaultPluginManager` is the ready-to-use implementation.
- **`ExtensionRegistry`** — register extension points and contributed extensions, look up the highest-priority extension or all extensions for a point, and unregister. `DefaultExtensionRegistry` is backed by `ConcurrentHashMap` and `CopyOnWriteArrayList` for thread-safe concurrent reads, validates that each extension actually implements its declared point, and keeps extensions sorted by priority (highest first).
- **Dependency resolution** — `PluginDependencyResolver` performs an O(V+E) topological sort over declared plugin dependencies, starting dependencies before dependents and throwing a clear `PluginException` on circular or missing dependencies.
- **Automatic extension wiring** — `DefaultPluginManager` scans a plugin's `@Extension`-annotated inner classes on load and registers/unregisters them with the `ExtensionRegistry` automatically.
- **Immutable, builder-friendly models** — `PluginMetadata` and `PluginDescriptor` are Java `record`s with validating constructors, builders, and copy-with-state helpers (`PluginDescriptor.withState(...)`).
- **Framework-independent & non-reactive** — usable in any Java 21+ app; clear stack traces via checked `PluginException` (which extends the kernel's `FireflyException`).

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x (only if embedding in a Spring application; the library itself does not require Spring)
- Maven 3.9+
- No external runtime services (no broker, database, or cache required)

## Installation

Versions are managed by the Firefly Framework parent/BOM — omit `<version>` when your project inherits `fireflyframework-parent` or imports the Firefly BOM.

If you **define** plugins or extension points, depend on the API:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>plugin-api</artifactId>
    <!-- version managed by fireflyframework-parent / BOM -->
</dependency>
```

If you **host and run** plugins, depend on the core (it transitively brings in the API):

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>plugin-core</artifactId>
    <!-- version managed by fireflyframework-parent / BOM -->
</dependency>
```

To let the parent manage the version, inherit it in your POM:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.08</version>
</parent>
```

## Quick Start

### 1. Define an extension point (in the host)

```java
import org.fireflyframework.plugin.annotation.ExtensionPoint;

@ExtensionPoint(id = "payment-processor", description = "Processes payments")
public interface PaymentProcessor {
    PaymentResult process(PaymentRequest request);
}
```

### 2. Write a plugin that contributes an extension

A plugin is a class with a no-arg constructor that implements `org.fireflyframework.plugin.api.Plugin`. Its extensions are `@Extension`-annotated inner classes that implement an extension-point interface.

```java
import org.fireflyframework.plugin.annotation.Extension;
import org.fireflyframework.plugin.annotation.Plugin;
import org.fireflyframework.plugin.api.PluginException;
import org.fireflyframework.plugin.model.PluginMetadata;

@Plugin(
    id = "stripe-payments",
    name = "Stripe Payments",
    version = "1.0.0",
    description = "Stripe payment processing",
    author = "Acme Corp"
)
public class StripePlugin implements org.fireflyframework.plugin.api.Plugin {

    @Override
    public PluginMetadata getMetadata() {
        return PluginMetadata.builder()
            .id("stripe-payments")
            .name("Stripe Payments")
            .version("1.0.0")
            .build();
    }

    @Override public void initialize() throws PluginException { /* set up clients */ }
    @Override public void start()      throws PluginException { /* begin work */ }
    @Override public void stop()       throws PluginException { /* pause work */ }
    @Override public void destroy()    throws PluginException { /* release resources */ }

    @Extension(extensionPointId = "payment-processor", priority = 100)
    public static class StripePaymentProcessor implements PaymentProcessor {
        @Override
        public PaymentResult process(PaymentRequest request) {
            return /* call Stripe and map the result */ null;
        }
    }
}
```

### 3. Load, run, and use the extension (in the host)

```java
import org.fireflyframework.plugin.api.ExtensionRegistry;
import org.fireflyframework.plugin.api.PluginManager;
import org.fireflyframework.plugin.core.DefaultPluginManager;

PluginManager manager = new DefaultPluginManager();
manager.initialize();

ExtensionRegistry registry = manager.getExtensionRegistry();
// Extension points must be registered before extensions can be added to them:
registry.registerExtensionPoint("payment-processor", PaymentProcessor.class);

// Loading a plugin also initializes it and auto-registers its @Extension classes:
manager.loadPlugin(StripePlugin.class);
manager.startPlugin("stripe-payments");

// Highest-priority extension for the point:
PaymentProcessor processor = registry.<PaymentProcessor>getExtension("payment-processor").orElseThrow();
PaymentResult result = processor.process(request);

// ...or fan out across all contributed extensions (ordered by priority, highest first):
for (PaymentProcessor p : registry.<PaymentProcessor>getExtensions("payment-processor")) {
    // ...
}

manager.shutdown(); // stops, unloads, and destroys all plugins
```

## How It Works

- **Loading** — `loadPlugin(Class)` instantiates the plugin via its no-arg constructor, reads its `PluginMetadata`, records a `PluginDescriptor` in state `LOADED`, calls `initialize()` (moving it to `INITIALIZED`, or `FAILED` on error), then registers its `@Extension` inner classes.
- **Starting** — `startPlugin(id)` first resolves declared dependencies via `PluginDependencyResolver` and starts any not-yet-started dependencies, then calls the plugin's `start()` (→ `STARTED`).
- **Extension lookup** — extensions are validated to implement their point, stored thread-safely, and returned ordered by descending priority. `getExtension` returns the single highest-priority extension; `getExtensions` returns them all.
- **Shutdown** — `unloadPlugin(id)` stops a running plugin, unregisters its extensions, and calls `destroy()`. `shutdown()` does this for every loaded plugin.

## Configuration

This module has **no configuration properties**. It contains no Spring Boot auto-configuration and no `@ConfigurationProperties` classes — you wire a `PluginManager` (typically `DefaultPluginManager`) yourself, as a plain object or as a Spring `@Bean`, and drive it through its API. Plugin metadata (ids, versions, dependencies, extension priorities) is declared in code via annotations or the `PluginMetadata` builder, not in `application.yml`.

## Documentation

- [DESIGN_RATIONALE.md](DESIGN_RATIONALE.md) — why the system is simple and synchronous, and which legacy features were deliberately removed.
- [VERIFICATION.md](VERIFICATION.md) — verification notes for the reimplementation.
- Firefly Framework module catalog and org-wide docs: [github.com/fireflyframework](https://github.com/fireflyframework).

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
