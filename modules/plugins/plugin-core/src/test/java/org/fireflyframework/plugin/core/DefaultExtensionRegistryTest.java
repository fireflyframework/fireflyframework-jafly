package org.fireflyframework.plugin.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultExtensionRegistryTest {
    
    private DefaultExtensionRegistry registry;
    
    // Test extension point interface
    interface TestExtensionPoint {
        String process();
    }
    
    // Test extension implementations
    static class HighPriorityExtension implements TestExtensionPoint {
        @Override
        public String process() {
            return "high";
        }
    }
    
    static class LowPriorityExtension implements TestExtensionPoint {
        @Override
        public String process() {
            return "low";
        }
    }
    
    static class MediumPriorityExtension implements TestExtensionPoint {
        @Override
        public String process() {
            return "medium";
        }
    }
    
    @BeforeEach
    void setUp() {
        registry = new DefaultExtensionRegistry();
    }
    
    @Test
    void shouldRegisterExtensionPoint() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        assertTrue(registry.hasExtensionPoint("test-point"));
        assertTrue(registry.getExtensionPointIds().contains("test-point"));
    }
    
    @Test
    void shouldRegisterExtensionWithPriority() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        TestExtensionPoint extension = new HighPriorityExtension();
        registry.registerExtension("test-point", extension, 100);
        
        List<TestExtensionPoint> extensions = registry.getExtensions("test-point");
        assertEquals(1, extensions.size());
        assertEquals(extension, extensions.get(0));
    }
    
    @Test
    void shouldOrderExtensionsByPriority() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        TestExtensionPoint low = new LowPriorityExtension();
        TestExtensionPoint high = new HighPriorityExtension();
        TestExtensionPoint medium = new MediumPriorityExtension();
        
        // Register in random order
        registry.registerExtension("test-point", low, 10);
        registry.registerExtension("test-point", high, 100);
        registry.registerExtension("test-point", medium, 50);
        
        List<TestExtensionPoint> extensions = registry.getExtensions("test-point");
        assertEquals(3, extensions.size());
        
        // Should be ordered: high (100), medium (50), low (10)
        assertEquals("high", extensions.get(0).process());
        assertEquals("medium", extensions.get(1).process());
        assertEquals("low", extensions.get(2).process());
    }
    
    @Test
    void shouldGetHighestPriorityExtension() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        registry.registerExtension("test-point", new LowPriorityExtension(), 10);
        registry.registerExtension("test-point", new HighPriorityExtension(), 100);
        registry.registerExtension("test-point", new MediumPriorityExtension(), 50);
        
        Optional<TestExtensionPoint> extension = registry.getExtension("test-point");
        
        assertTrue(extension.isPresent());
        assertEquals("high", extension.get().process());
    }
    
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
    
    @Test
    void shouldReturnEmptyListWhenNoExtensions() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        List<TestExtensionPoint> extensions = registry.getExtensions("test-point");
        assertNotNull(extensions);
        assertTrue(extensions.isEmpty());
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenNoExtensions() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        Optional<TestExtensionPoint> extension = registry.getExtension("test-point");
        assertFalse(extension.isPresent());
    }
    
    @Test
    void shouldThrowExceptionWhenExtensionPointNotRegistered() {
        TestExtensionPoint extension = new HighPriorityExtension();
        
        assertThrows(IllegalArgumentException.class, () ->
            registry.registerExtension("non-existent", extension, 100)
        );
    }
    
    @Test
    void shouldThrowExceptionWhenExtensionDoesNotImplementExtensionPoint() {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        String invalidExtension = "not an extension";
        
        assertThrows(IllegalArgumentException.class, () ->
            registry.registerExtension("test-point", invalidExtension, 100)
        );
    }
    
    @Test
    void shouldHandleMultipleExtensionPoints() {
        interface AnotherExtensionPoint {
            int calculate();
        }
        
        registry.registerExtensionPoint("point1", TestExtensionPoint.class);
        registry.registerExtensionPoint("point2", AnotherExtensionPoint.class);
        
        assertEquals(2, registry.getExtensionPointIds().size());
        assertTrue(registry.hasExtensionPoint("point1"));
        assertTrue(registry.hasExtensionPoint("point2"));
    }
    
    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        registry.registerExtensionPoint("test-point", TestExtensionPoint.class);
        
        // Create multiple threads that register extensions simultaneously
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int priority = i;
            threads[i] = new Thread(() -> {
                TestExtensionPoint ext = new HighPriorityExtension();
                registry.registerExtension("test-point", ext, priority);
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all extensions were registered
        List<TestExtensionPoint> extensions = registry.getExtensions("test-point");
        assertEquals(10, extensions.size());
    }
}
