![Jafly. Plugin API](../assets/modules/plugin-api.svg)

# Plugin API

Define plugins, extension points, extension registration, and lifecycle contracts without the host implementation.

Implement Plugin and the host extension interfaces; describe the plugin with the model/annotation contracts. Hosts use PluginManager to load, start, stop, and unload plugins and ExtensionRegistry to locate contributed extensions.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:plugin-api` | `jar` | [modules/plugins/plugin-api](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/plugins/plugin-api) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

Define a host extension contract for an onboarding check and implement it in an application plugin. Stable plugin IDs identify lifecycle/dependency relationships; they are separate from applicant IDs.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>plugin-api</artifactId>
    <version>26.10.19</version>
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

`MyPlugin` is the application plugin implementation, not a framework class. The host extension interfaces and registration contract are described in the [plugin platform guide](plugins.md). Keep plugin metadata IDs stable because the manager uses them for lifecycle and dependency resolution.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`Plugin`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/api/Plugin.java) | Defines a plugin lifecycle and its metadata; the annotation with the same simple name labels plugin classes. |
| [`PluginManager`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/api/PluginManager.java) | Main interface for managing plugins. |
| [`ExtensionRegistry`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/api/ExtensionRegistry.java) | Registry for managing extension points and their implementations. |
| [`PluginDescriptor`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/model/PluginDescriptor.java) | Complete descriptor of a plugin including its metadata and current state. |
| [`PluginMetadata`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/main/java/org/fireflyframework/plugin/model/PluginMetadata.java) | Metadata information about a plugin. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common Plugins](plugins.md) | Group the API and implementation of the framework plugin system. |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :plugin-api -am verify
./mvnw -pl :plugin-api -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [PluginDescriptorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/test/java/org/fireflyframework/plugin/model/PluginDescriptorTest.java).
- [PluginMetadataTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/test/java/org/fireflyframework/plugin/model/PluginMetadataTest.java).

## Test fixture

The `shouldProvideConvenienceMethodsForMetadata` test demonstrates one concrete behavior. Excerpt from [PluginDescriptorTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/src/test/java/org/fireflyframework/plugin/model/PluginDescriptorTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldProvideConvenienceMethodsForMetadata() {
    PluginMetadata metadata = PluginMetadata.builder()
        .id("test-plugin")
        .name("Test Plugin")
        .version("2.0.0")
        .build();

    PluginDescriptor descriptor = PluginDescriptor.builder()
        .metadata(metadata)
        .state(PluginState.STARTED)
        .build();

    assertEquals("test-plugin", descriptor.getId());
    assertEquals("Test Plugin", descriptor.getName());
    assertEquals("2.0.0", descriptor.getVersion());
}
```

## Continue the application

Use the [Plugin Core guide](plugin-core.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-api/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/plugins/plugin-api/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
