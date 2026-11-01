![Jafly. Plugin Core](../assets/modules/plugin-core.svg)

# Plugin Core

Load plugins, resolve dependencies, and manage extension registration in a host process.

Create DefaultPluginManagerImpl for the host and use the plugin-api contracts for lifecycle operations. Inspect dependency-resolution and lifecycle tests before changing load/start order or unloading extensions still used by the host.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:plugin-core` | `jar` | [modules/plugins/plugin-core](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/plugins/plugin-core) |

Part of [Team-wide architecture](index.md#team-wide-architecture).

## In the onboarding service

The onboarding host can load an application plugin class and start its contributed checks through the plugin API. Stop/unload it only after the host has finished work that uses its extensions.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>plugin-core</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

The host can use the concrete implementation with the API lifecycle operations:

```java
PluginManager manager = new DefaultPluginManagerImpl();
manager.initialize();
PluginDescriptor descriptor = manager.loadPlugin(MyPlugin.class);
manager.startPlugin(descriptor.getId());
// Run application work using registered extensions.
manager.shutdown();
```

Import `DefaultPluginManagerImpl` from `org.fireflyframework.plugin.core` and the API/model types from `plugin-api`. `MyPlugin` is an application implementation with a no-argument constructor, as required by the loader. The default manager owns an extension registry and dependency resolver; an alternate `ExtensionRegistry` can be supplied through its constructor.

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`DefaultPluginManagerImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/main/java/org/fireflyframework/plugin/core/DefaultPluginManagerImpl.java) | Default implementation of PluginManager. |
| [`DefaultExtensionRegistryImpl`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/main/java/org/fireflyframework/plugin/core/DefaultExtensionRegistryImpl.java) | Thread-safe implementation of ExtensionRegistry. |
| [`PluginDependencyResolver`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/main/java/org/fireflyframework/plugin/core/PluginDependencyResolver.java) | Resolves plugin dependencies and determines the correct loading order. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Common Plugins](plugins.md) | Group the API and implementation of the framework plugin system. |
| [Plugin API](plugin-api.md) | Define plugins, extension points, extension registration, and lifecycle contracts without the host implementation. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :plugin-core -am verify
./mvnw -pl :plugin-core -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [DefaultExtensionRegistryTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/test/java/org/fireflyframework/plugin/core/DefaultExtensionRegistryTest.java).
- [DefaultPluginManagerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/test/java/org/fireflyframework/plugin/core/DefaultPluginManagerTest.java).
- [PluginDependencyResolverTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/test/java/org/fireflyframework/plugin/core/PluginDependencyResolverTest.java).
- [PluginSystemIntegrationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/test/java/org/fireflyframework/plugin/integration/PluginSystemIntegrationTest.java).

## Test fixture

The `shouldUnregisterExtension` test demonstrates one concrete behavior. Excerpt from [DefaultExtensionRegistryTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/src/test/java/org/fireflyframework/plugin/core/DefaultExtensionRegistryTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldUnregisterExtension() {
    registry.registerExtensionPoint("test-point", TestExtensionPoint.class);

    TestExtensionPoint extension1 = new HighPriorityExtension();
    TestExtensionPoint extension2 = new LowPriorityExtension();

    registry.registerExtension("test-point", extension1, 100);
    registry.registerExtension("test-point", extension2, 10);

    assertEquals(2, registry.getExtensions("test-point").size());

    registry.unregisterExtension("test-point", extension1);

    List<TestExtensionPoint> remaining = registry.getExtensions("test-point");
    assertEquals(1, remaining.size());
    assertEquals(extension2, remaining.get(0));
}
```

## Continue the application

Use the [Plugins guide](plugins.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/plugins/plugin-core/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/plugins/plugin-core/src/main/java).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
