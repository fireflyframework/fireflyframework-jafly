package org.fireflyframework.plugin.core;

import org.fireflyframework.plugin.annotation.Extension;
import org.fireflyframework.plugin.api.Plugin;
import org.fireflyframework.plugin.api.PluginException;
import org.fireflyframework.plugin.model.PluginDescriptor;
import org.fireflyframework.plugin.model.PluginMetadata;
import org.fireflyframework.plugin.model.PluginState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPluginManagerTest {
    
    private DefaultPluginManager pluginManager;
    
    // Test extension point
    interface TestExtensionPoint {
        String execute();
    }
    
    // Simple test plugin
    public static class SimplePlugin implements Plugin {
        private final PluginMetadata metadata;
        private boolean initialized = false;
        private boolean started = false;
        
        public SimplePlugin() {
            this.metadata = PluginMetadata.builder()
                .id("simple-plugin")
                .name("Simple Test Plugin")
                .version("1.0.0")
                .build();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return metadata;
        }
        
        @Override
        public void initialize() throws PluginException {
            initialized = true;
        }
        
        @Override
        public void start() throws PluginException {
            if (!initialized) {
                throw new PluginException("Plugin not initialized");
            }
            started = true;
        }
        
        @Override
        public void stop() throws PluginException {
            started = false;
        }
        
        @Override
        public void destroy() throws PluginException {
            initialized = false;
        }
        
        public boolean isInitialized() {
            return initialized;
        }
        
        public boolean isStarted() {
            return started;
        }
    }
    
    // Plugin with extension
    public static class PluginWithExtension implements Plugin {
        private final PluginMetadata metadata;
        
        public PluginWithExtension() {
            this.metadata = PluginMetadata.builder()
                .id("extension-plugin")
                .name("Plugin with Extension")
                .version("1.0.0")
                .build();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return metadata;
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void destroy() {}
        
        @Extension(extensionPointId = "test-extension-point", priority = 100)
        public static class TestExtension implements TestExtensionPoint {
            @Override
            public String execute() {
                return "extension-result";
            }
        }
    }
    
    // Plugin with dependency
    public static class DependentPlugin implements Plugin {
        private final PluginMetadata metadata;
        
        public DependentPlugin() {
            this.metadata = PluginMetadata.builder()
                .id("dependent-plugin")
                .name("Dependent Plugin")
                .version("1.0.0")
                .dependencies(Set.of("simple-plugin"))
                .build();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return metadata;
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public void start() {}
        
        @Override
        public void stop() {}
        
        @Override
        public void destroy() {}
    }
    
    // Plugin that fails on start
    public static class FailingPlugin implements Plugin {
        private final PluginMetadata metadata;
        
        public FailingPlugin() {
            this.metadata = PluginMetadata.builder()
                .id("failing-plugin")
                .name("Failing Plugin")
                .version("1.0.0")
                .build();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return metadata;
        }
        
        @Override
        public void initialize() {}
        
        @Override
        public void start() throws PluginException {
            throw new PluginException("Intentional failure");
        }
        
        @Override
        public void stop() {}
        
        @Override
        public void destroy() {}
    }
    
    @BeforeEach
    void setUp() throws PluginException {
        pluginManager = new DefaultPluginManager();
        pluginManager.initialize();
    }
    
    @Test
    void shouldLoadPlugin() throws PluginException {
        PluginDescriptor descriptor = pluginManager.loadPlugin(SimplePlugin.class);
        
        assertNotNull(descriptor);
        assertEquals("simple-plugin", descriptor.getId());
        assertEquals("Simple Test Plugin", descriptor.getName());
        assertEquals("1.0.0", descriptor.getVersion());
        assertEquals(PluginState.INITIALIZED, descriptor.state());
    }
    
    @Test
    void shouldNotLoadSamePluginTwice() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        
        assertThrows(PluginException.class, () ->
            pluginManager.loadPlugin(SimplePlugin.class)
        );
    }
    
    @Test
    void shouldStartPlugin() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        pluginManager.startPlugin("simple-plugin");
        
        Optional<PluginDescriptor> descriptor = pluginManager.getPluginDescriptor("simple-plugin");
        assertTrue(descriptor.isPresent());
        assertEquals(PluginState.STARTED, descriptor.get().state());
        
        Optional<Plugin> plugin = pluginManager.getPlugin("simple-plugin");
        assertTrue(plugin.isPresent());
        assertTrue(((SimplePlugin) plugin.get()).isStarted());
    }
    
    @Test
    void shouldStopPlugin() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        pluginManager.startPlugin("simple-plugin");
        pluginManager.stopPlugin("simple-plugin");
        
        Optional<PluginDescriptor> descriptor = pluginManager.getPluginDescriptor("simple-plugin");
        assertTrue(descriptor.isPresent());
        assertEquals(PluginState.STOPPED, descriptor.get().state());
        
        Optional<Plugin> plugin = pluginManager.getPlugin("simple-plugin");
        assertTrue(plugin.isPresent());
        assertFalse(((SimplePlugin) plugin.get()).isStarted());
    }
    
    @Test
    void shouldUnloadPlugin() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        pluginManager.unloadPlugin("simple-plugin");
        
        Optional<PluginDescriptor> descriptor = pluginManager.getPluginDescriptor("simple-plugin");
        assertFalse(descriptor.isPresent());
        
        Optional<Plugin> plugin = pluginManager.getPlugin("simple-plugin");
        assertFalse(plugin.isPresent());
    }
    
    @Test
    void shouldStartPluginWithDependencies() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        pluginManager.loadPlugin(DependentPlugin.class);
        
        // Starting dependent plugin should auto-start simple-plugin
        pluginManager.startPlugin("dependent-plugin");
        
        Optional<PluginDescriptor> simpleDesc = pluginManager.getPluginDescriptor("simple-plugin");
        assertTrue(simpleDesc.isPresent());
        assertEquals(PluginState.STARTED, simpleDesc.get().state());
        
        Optional<PluginDescriptor> depDesc = pluginManager.getPluginDescriptor("dependent-plugin");
        assertTrue(depDesc.isPresent());
        assertEquals(PluginState.STARTED, depDesc.get().state());
    }
    
    @Test
    void shouldGetAllPlugins() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        pluginManager.loadPlugin(DependentPlugin.class);
        
        List<PluginDescriptor> allPlugins = pluginManager.getAllPlugins();
        
        assertEquals(2, allPlugins.size());
    }
    
    @Test
    void shouldHandlePluginFailure() throws PluginException {
        pluginManager.loadPlugin(FailingPlugin.class);
        
        assertThrows(PluginException.class, () ->
            pluginManager.startPlugin("failing-plugin")
        );
        
        Optional<PluginDescriptor> descriptor = pluginManager.getPluginDescriptor("failing-plugin");
        assertTrue(descriptor.isPresent());
        assertEquals(PluginState.FAILED, descriptor.get().state());
    }
    
    @Test
    void shouldProvideExtensionRegistry() {
        assertNotNull(pluginManager.getExtensionRegistry());
    }
    
    @Test
    void shouldShutdownAllPlugins() throws PluginException {
        pluginManager.loadPlugin(SimplePlugin.class);
        pluginManager.loadPlugin(DependentPlugin.class);
        pluginManager.startPlugin("simple-plugin");
        pluginManager.startPlugin("dependent-plugin");
        
        pluginManager.shutdown();
        
        assertTrue(pluginManager.getAllPlugins().isEmpty());
    }
    
    @Test
    void shouldThrowExceptionWhenStartingNonExistentPlugin() {
        assertThrows(PluginException.class, () ->
            pluginManager.startPlugin("non-existent")
        );
    }
    
    @Test
    void shouldThrowExceptionWhenStoppingNonExistentPlugin() {
        assertThrows(PluginException.class, () ->
            pluginManager.stopPlugin("non-existent")
        );
    }
    
    @Test
    void shouldThrowExceptionWhenUnloadingNonExistentPlugin() {
        assertThrows(PluginException.class, () ->
            pluginManager.unloadPlugin("non-existent")
        );
    }
}
