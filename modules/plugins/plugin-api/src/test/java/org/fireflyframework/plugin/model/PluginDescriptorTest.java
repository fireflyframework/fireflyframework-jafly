package org.fireflyframework.plugin.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PluginDescriptorTest {
    
    @Test
    void shouldCreatePluginDescriptorWithBuilder() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test-plugin")
            .name("Test")
            .version("1.0.0")
            .build();
        
        Instant loadedAt = Instant.now();
        Instant lastChange = Instant.now();
        
        PluginDescriptor descriptor = PluginDescriptor.builder()
            .metadata(metadata)
            .state(PluginState.INITIALIZED)
            .loadedAt(loadedAt)
            .lastStateChange(lastChange)
            .build();
        
        assertEquals(metadata, descriptor.metadata());
        assertEquals(PluginState.INITIALIZED, descriptor.state());
        assertEquals(loadedAt, descriptor.loadedAt());
        assertEquals(lastChange, descriptor.lastStateChange());
    }
    
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
    
    @Test
    void shouldCreateNewDescriptorWithUpdatedState() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test-plugin")
            .name("Test")
            .version("1.0.0")
            .build();
        
        Instant originalLoadedAt = Instant.now();
        
        PluginDescriptor descriptor = PluginDescriptor.builder()
            .metadata(metadata)
            .state(PluginState.LOADED)
            .loadedAt(originalLoadedAt)
            .build();
        
        // Wait a tiny bit to ensure different timestamp
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        
        PluginDescriptor updated = descriptor.withState(PluginState.STARTED);
        
        assertEquals(PluginState.STARTED, updated.state());
        assertEquals(originalLoadedAt, updated.loadedAt()); // Should preserve
        assertNotEquals(descriptor.lastStateChange(), updated.lastStateChange()); // Should update
    }
    
    @Test
    void shouldUseDefaultStateWhenNotSpecified() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .build();
        
        PluginDescriptor descriptor = PluginDescriptor.builder()
            .metadata(metadata)
            .build();
        
        assertEquals(PluginState.LOADED, descriptor.state());
    }
    
    @Test
    void shouldThrowExceptionWhenMetadataIsNull() {
        assertThrows(NullPointerException.class, () ->
            PluginDescriptor.builder()
                .state(PluginState.LOADED)
                .build()
        );
    }
    
    @Test
    void shouldThrowExceptionWhenStateIsNull() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .build();
        
        assertThrows(NullPointerException.class, () ->
            new PluginDescriptor(metadata, null, Instant.now(), Instant.now())
        );
    }
}
