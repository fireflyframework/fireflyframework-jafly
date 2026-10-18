![Jafly. Plugin API](../../../docs/site/assets/modules/plugin-api.svg)

# Plugin API

Define plugins, extension points, extension registration, and lifecycle contracts without the host implementation.

Implement Plugin and the host extension interfaces; describe the plugin with the model/annotation contracts. Hosts use PluginManager to load, start, stop, and unload plugins and ExtensionRegistry to locate contributed extensions.

[Module guide](../../../docs/site/modules/plugin-api.md) · [Jafly README](../../../README.md) · [Module catalog](../../../docs/site/modules/index.md)

## In the onboarding service

Define a host extension contract for an onboarding check and implement it in an application plugin. Stable plugin IDs identify lifecycle/dependency relationships; they are separate from applicant IDs.

## Installation

[Installation and version availability](../../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>plugin-api</artifactId>
    <version>26.10.17-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Plugin authors implement `org.fireflyframework.plugin.api.Plugin`; the annotation with the same simple name lives in `org.fireflyframework.plugin.annotation`. Hosts operate the API through `PluginManager`:

```java
PluginDescriptor descriptor = manager.loadPlugin(MyPlugin.class);
manager.startPlugin(descriptor.getId());
// Use the host's registered extension points while the plugin is started.
manager.stopPlugin(descriptor.getId());
manager.unloadPlugin(descriptor.getId());
```

`MyPlugin` is the application plugin implementation, not a framework class. The host extension interfaces and registration contract are described in the [plugin platform guide](../../../docs/site/modules/plugins.md). Keep plugin metadata IDs stable because the manager uses them for lifecycle and dependency resolution.

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`Plugin`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/api/Plugin.java) | Defines a plugin lifecycle and its metadata; the annotation with the same simple name labels plugin classes. |
| [`PluginManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/api/PluginManager.java) | Main interface for managing plugins. |
| [`ExtensionRegistry`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/api/ExtensionRegistry.java) | Registry for managing extension points and their implementations. |
| [`PluginDescriptor`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/model/PluginDescriptor.java) | Complete descriptor of a plugin including its metadata and current state. |
| [`PluginMetadata`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/model/PluginMetadata.java) | Metadata information about a plugin. |

## Continue the application

Use the [Plugin Core guide](../../../docs/site/modules/plugin-core.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :plugin-api -am verify
./mvnw -pl :plugin-api -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../../docs/site/modules/plugin-api.md).
