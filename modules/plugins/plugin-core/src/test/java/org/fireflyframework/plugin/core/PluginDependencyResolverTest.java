package org.fireflyframework.plugin.core;

import org.fireflyframework.plugin.api.PluginException;
import org.fireflyframework.plugin.model.PluginDescriptor;
import org.fireflyframework.plugin.model.PluginMetadata;
import org.fireflyframework.plugin.model.PluginState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PluginDependencyResolverTest {
    
    private PluginDependencyResolver resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new PluginDependencyResolver();
    }
    
    private PluginDescriptor createDescriptor(String id, String... dependencies) {
        PluginMetadata metadata = PluginMetadata.builder()
            .id(id)
            .name(id + "-name")
            .version("1.0.0")
            .dependencies(Set.of(dependencies))
            .build();
        
        return PluginDescriptor.builder()
            .metadata(metadata)
            .state(PluginState.LOADED)
            .build();
    }
    
    @Test
    void shouldResolveNoDependencies() throws PluginException {
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin1"),
            createDescriptor("plugin2"),
            createDescriptor("plugin3")
        );
        
        List<String> order = resolver.resolveDependencies(plugins);
        
        assertEquals(3, order.size());
        assertTrue(order.contains("plugin1"));
        assertTrue(order.contains("plugin2"));
        assertTrue(order.contains("plugin3"));
    }
    
    @Test
    void shouldResolveLinearDependencies() throws PluginException {
        // plugin3 depends on plugin2, plugin2 depends on plugin1
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin3", "plugin2"),
            createDescriptor("plugin1"),
            createDescriptor("plugin2", "plugin1")
        );
        
        List<String> order = resolver.resolveDependencies(plugins);
        
        assertEquals(3, order.size());
        assertEquals("plugin1", order.get(0)); // No dependencies, first
        assertEquals("plugin2", order.get(1)); // Depends on plugin1
        assertEquals("plugin3", order.get(2)); // Depends on plugin2
    }
    
    @Test
    void shouldResolveComplexDependencies() throws PluginException {
        // plugin4 depends on plugin2 and plugin3
        // plugin2 depends on plugin1
        // plugin3 depends on plugin1
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin4", "plugin2", "plugin3"),
            createDescriptor("plugin2", "plugin1"),
            createDescriptor("plugin3", "plugin1"),
            createDescriptor("plugin1")
        );
        
        List<String> order = resolver.resolveDependencies(plugins);
        
        assertEquals(4, order.size());
        
        // plugin1 must be first
        assertEquals("plugin1", order.get(0));
        
        // plugin2 and plugin3 must come before plugin4
        int plugin2Index = order.indexOf("plugin2");
        int plugin3Index = order.indexOf("plugin3");
        int plugin4Index = order.indexOf("plugin4");
        
        assertTrue(plugin2Index < plugin4Index);
        assertTrue(plugin3Index < plugin4Index);
    }
    
    @Test
    void shouldDetectCircularDependency() {
        // plugin1 depends on plugin2, plugin2 depends on plugin1
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin1", "plugin2"),
            createDescriptor("plugin2", "plugin1")
        );
        
        PluginException exception = assertThrows(PluginException.class, () ->
            resolver.resolveDependencies(plugins)
        );
        
        assertTrue(exception.getMessage().contains("Circular dependency"));
    }
    
    @Test
    void shouldDetectCircularDependencyInChain() {
        // plugin1 -> plugin2 -> plugin3 -> plugin1 (circular)
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin1", "plugin2"),
            createDescriptor("plugin2", "plugin3"),
            createDescriptor("plugin3", "plugin1")
        );
        
        PluginException exception = assertThrows(PluginException.class, () ->
            resolver.resolveDependencies(plugins)
        );
        
        assertTrue(exception.getMessage().contains("Circular dependency"));
    }
    
    @Test
    void shouldDetectMissingDependency() {
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin1", "plugin-missing"),
            createDescriptor("plugin2")
        );
        
        PluginException exception = assertThrows(PluginException.class, () ->
            resolver.resolveDependencies(plugins)
        );
        
        assertTrue(exception.getMessage().contains("depends on"));
        assertTrue(exception.getMessage().contains("not loaded"));
    }
    
    @Test
    void shouldHandleEmptyPluginList() throws PluginException {
        List<PluginDescriptor> plugins = new ArrayList<>();
        
        List<String> order = resolver.resolveDependencies(plugins);
        
        assertTrue(order.isEmpty());
    }
    
    @Test
    void shouldHandleSinglePlugin() throws PluginException {
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin1")
        );
        
        List<String> order = resolver.resolveDependencies(plugins);
        
        assertEquals(1, order.size());
        assertEquals("plugin1", order.get(0));
    }
    
    @Test
    void shouldHandleDiamondDependency() throws PluginException {
        // Diamond: plugin4 depends on plugin2 and plugin3
        // Both plugin2 and plugin3 depend on plugin1
        List<PluginDescriptor> plugins = List.of(
            createDescriptor("plugin1"),
            createDescriptor("plugin2", "plugin1"),
            createDescriptor("plugin3", "plugin1"),
            createDescriptor("plugin4", "plugin2", "plugin3")
        );
        
        List<String> order = resolver.resolveDependencies(plugins);
        
        assertEquals(4, order.size());
        assertEquals("plugin1", order.get(0));
        
        // plugin4 must be last
        assertEquals("plugin4", order.get(3));
        
        // plugin2 and plugin3 must be in middle (order between them doesn't matter)
        assertTrue(order.indexOf("plugin2") > 0 && order.indexOf("plugin2") < 3);
        assertTrue(order.indexOf("plugin3") > 0 && order.indexOf("plugin3") < 3);
    }
}
