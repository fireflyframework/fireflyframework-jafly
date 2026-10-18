package org.fireflyframework.plugin.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PluginMetadataTest {
    
    @Test
    void shouldCreatePluginMetadataWithBuilder() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test-plugin")
            .name("Test Plugin")
            .version("1.0.0")
            .description("Test description")
            .author("Test Author")
            .dependencies(Set.of("dep1", "dep2"))
            .build();
        
        assertEquals("test-plugin", metadata.id());
        assertEquals("Test Plugin", metadata.name());
        assertEquals("1.0.0", metadata.version());
        assertEquals("Test description", metadata.description());
        assertEquals("Test Author", metadata.author());
        assertEquals(2, metadata.dependencies().size());
        assertTrue(metadata.dependencies().contains("dep1"));
        assertTrue(metadata.dependencies().contains("dep2"));
    }
    
    @Test
    void shouldCreatePluginMetadataWithMinimalFields() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("minimal-plugin")
            .name("Minimal")
            .version("1.0.0")
            .build();
        
        assertEquals("minimal-plugin", metadata.id());
        assertEquals("Minimal", metadata.name());
        assertEquals("1.0.0", metadata.version());
        assertEquals("", metadata.description());
        assertEquals("", metadata.author());
        assertTrue(metadata.dependencies().isEmpty());
    }
    
    @Test
    void shouldThrowExceptionWhenIdIsNull() {
        assertThrows(NullPointerException.class, () -> 
            PluginMetadata.builder()
                .name("Test")
                .version("1.0.0")
                .build()
        );
    }
    
    @Test
    void shouldThrowExceptionWhenNameIsNull() {
        assertThrows(NullPointerException.class, () -> 
            PluginMetadata.builder()
                .id("test")
                .version("1.0.0")
                .build()
        );
    }
    
    @Test
    void shouldThrowExceptionWhenVersionIsNull() {
        assertThrows(NullPointerException.class, () -> 
            PluginMetadata.builder()
                .id("test")
                .name("Test")
                .build()
        );
    }
    
    @Test
    void shouldCreateImmutableDependenciesSet() {
        Set<String> deps = Set.of("dep1", "dep2");
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .dependencies(deps)
            .build();
        
        // Verify the set is immutable
        assertThrows(UnsupportedOperationException.class, () -> 
            metadata.dependencies().add("dep3")
        );
    }
    
    @Test
    void shouldHandleNullDependencies() {
        PluginMetadata metadata = PluginMetadata.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .dependencies(null)
            .build();
        
        assertNotNull(metadata.dependencies());
        assertTrue(metadata.dependencies().isEmpty());
    }
    
    @Test
    void shouldBeEqualWhenSameValues() {
        PluginMetadata metadata1 = PluginMetadata.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .build();
        
        PluginMetadata metadata2 = PluginMetadata.builder()
            .id("test")
            .name("Test")
            .version("1.0.0")
            .build();
        
        assertEquals(metadata1, metadata2);
        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }
}
