![Jafly. Plugin Core](../../../docs/site/assets/modules/plugin-core.svg)

# Plugin Core

Load plugins, resolve dependencies, and manage extension registration in a host process.

Create DefaultPluginManager for the host and use the plugin-api contracts for lifecycle operations. Inspect dependency-resolution and lifecycle tests before changing load/start order or unloading extensions still used by the host.

[Module guide](../../../docs/site/modules/plugin-core.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

The onboarding host can load an application plugin class and start its contributed checks through the plugin API. Stop/unload it only after the host has finished work that uses its extensions.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>plugin-core</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The host can use the concrete implementation with the API lifecycle operations:

```java
PluginManager manager = new DefaultPluginManager();
manager.initialize();
PluginDescriptor descriptor = manager.loadPlugin(MyPlugin.class);
manager.startPlugin(descriptor.getId());
// Run application work using registered extensions.
manager.shutdown();
```

Import `DefaultPluginManager` from `org.fireflyframework.plugin.core` and the API/model types from `plugin-api`. `MyPlugin` is an application implementation with a no-argument constructor, as required by the loader. The default manager owns an extension registry and dependency resolver; an alternate `ExtensionRegistry` can be supplied through its constructor.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`DefaultPluginManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/main/java/org/fireflyframework/plugin/core/DefaultPluginManager.java) | Default implementation of PluginManager. |
| [`DefaultExtensionRegistry`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/main/java/org/fireflyframework/plugin/core/DefaultExtensionRegistry.java) | Thread-safe implementation of ExtensionRegistry. |
| [`PluginDependencyResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/main/java/org/fireflyframework/plugin/core/PluginDependencyResolver.java) | Resolves plugin dependencies and determines the correct loading order. |

## Continue the application

Use the [Plugins guide](../../../docs/site/modules/plugins.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :plugin-core -am verify
./mvnw -pl :plugin-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/plugin-core.md).
