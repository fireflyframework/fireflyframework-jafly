package org.fireflyframework.plugin.integration;

import org.fireflyframework.plugin.annotation.Extension;
import org.fireflyframework.plugin.annotation.ExtensionPoint;
import org.fireflyframework.plugin.api.Plugin;
import org.fireflyframework.plugin.api.PluginException;
import org.fireflyframework.plugin.api.PluginManager;
import org.fireflyframework.plugin.core.DefaultPluginManager;
import org.fireflyframework.plugin.model.PluginDescriptor;
import org.fireflyframework.plugin.model.PluginMetadata;
import org.fireflyframework.plugin.model.PluginState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the complete plugin system workflow.
 * This simulates a real-world scenario with payment processing plugins.
 */
class PluginSystemIntegrationTest {
    
    private PluginManager pluginManager;
    
    // Extension Point: Payment Processor
    @ExtensionPoint(
        id = "org.fireflyframework.payment.processor",
        description = "Extension point for payment processing"
    )
    interface PaymentProcessor {
        boolean supportsPaymentMethod(String method);
        String processPayment(BigDecimal amount, String currency, String method);
        int getPriority();
    }
    
    // Base Plugin: Core Payment System
    public static class CorePaymentPlugin implements Plugin {
        private final PluginMetadata metadata;
        
        public CorePaymentPlugin() {
            this.metadata = PluginMetadata.builder()
                .id("core-payment")
                .name("Core Payment System")
                .version("1.0.0")
                .description("Core payment processing system")
                .build();
        }
        
        @Override
        public PluginMetadata getMetadata() {
            return metadata;
        }
        
        @Override
        public void initialize() {
            System.out.println("Initializing Core Payment System");
        }
        
        @Override
        public void start() {
            System.out.println("Starting Core Payment System");
        }
        
        @Override
        public void stop() {
            System.out.println("Stopping Core Payment System");
        }
        
        @Override
        public void destroy() {
            System.out.println("Destroying Core Payment System");
        }
    }
    
    // Plugin: Credit Card Processor
    public static class CreditCardPlugin implements Plugin {
        private final PluginMetadata metadata;
        
        public CreditCardPlugin() {
            this.metadata = PluginMetadata.builder()
                .id("credit-card-processor")
                .name("Credit Card Processor")
                .version("1.0.0")
                .dependencies(Set.of("core-payment"))
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
        
        @Extension(
            extensionPointId = "org.fireflyframework.payment.processor",
            priority = 100,
            description = "Processes credit card payments"
        )
        public static class CreditCardProcessor implements PaymentProcessor {
            @Override
            public boolean supportsPaymentMethod(String method) {
                return "CREDIT_CARD".equals(method);
            }
            
            @Override
            public String processPayment(BigDecimal amount, String currency, String method) {
                return "CC-TXN-" + amount + "-" + currency;
            }
            
            @Override
            public int getPriority() {
                return 100;
            }
        }
    }
    
    // Plugin: Bank Transfer Processor
    public static class BankTransferPlugin implements Plugin {
        private final PluginMetadata metadata;
        
        public BankTransferPlugin() {
            this.metadata = PluginMetadata.builder()
                .id("bank-transfer-processor")
                .name("Bank Transfer Processor")
                .version("1.0.0")
                .dependencies(Set.of("core-payment"))
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
        
        @Extension(
            extensionPointId = "org.fireflyframework.payment.processor",
            priority = 50,
            description = "Processes bank transfers"
        )
        public static class BankTransferProcessor implements PaymentProcessor {
            @Override
            public boolean supportsPaymentMethod(String method) {
                return "BANK_TRANSFER".equals(method);
            }
            
            @Override
            public String processPayment(BigDecimal amount, String currency, String method) {
                return "BT-TXN-" + amount + "-" + currency;
            }
            
            @Override
            public int getPriority() {
                return 50;
            }
        }
    }
    
    @BeforeEach
    void setUp() throws PluginException {
        pluginManager = new DefaultPluginManager();
        pluginManager.initialize();
    }
    
    @Test
    void shouldDemonstrateCompletePluginSystemWorkflow() throws PluginException {
        // 1. Register extension point
        pluginManager.getExtensionRegistry().registerExtensionPoint(
            "org.fireflyframework.payment.processor",
            PaymentProcessor.class
        );
        
        // 2. Load plugins
        PluginDescriptor coreDesc = pluginManager.loadPlugin(CorePaymentPlugin.class);
        PluginDescriptor ccDesc = pluginManager.loadPlugin(CreditCardPlugin.class);
        PluginDescriptor btDesc = pluginManager.loadPlugin(BankTransferPlugin.class);
        
        assertEquals(PluginState.INITIALIZED, coreDesc.state());
        assertEquals(PluginState.INITIALIZED, ccDesc.state());
        assertEquals(PluginState.INITIALIZED, btDesc.state());
        
        // 3. Start plugins (should respect dependencies)
        pluginManager.startPlugin("credit-card-processor");
        pluginManager.startPlugin("bank-transfer-processor");
        
        // Core payment should be auto-started due to dependencies
        Optional<PluginDescriptor> coreStatus = pluginManager.getPluginDescriptor("core-payment");
        assertTrue(coreStatus.isPresent());
        assertEquals(PluginState.STARTED, coreStatus.get().state());
        
        // 4. Get all payment processors (should be ordered by priority)
        List<PaymentProcessor> processors = pluginManager.getExtensionRegistry()
            .getExtensions("org.fireflyframework.payment.processor");
        
        assertEquals(2, processors.size());
        // Credit card has priority 100, bank transfer has priority 50
        assertEquals(100, processors.get(0).getPriority());
        assertEquals(50, processors.get(1).getPriority());
        
        // 5. Process payments using the extensions
        for (PaymentProcessor processor : processors) {
            if (processor.supportsPaymentMethod("CREDIT_CARD")) {
                String txnId = processor.processPayment(
                    new BigDecimal("100.00"),
                    "USD",
                    "CREDIT_CARD"
                );
                assertTrue(txnId.startsWith("CC-TXN-"));
            }
            
            if (processor.supportsPaymentMethod("BANK_TRANSFER")) {
                String txnId = processor.processPayment(
                    new BigDecimal("500.00"),
                    "EUR",
                    "BANK_TRANSFER"
                );
                assertTrue(txnId.startsWith("BT-TXN-"));
            }
        }
        
        // 6. Get highest priority processor
        Optional<PaymentProcessor> highestPriority = pluginManager.getExtensionRegistry()
            .getExtension("org.fireflyframework.payment.processor");
        
        assertTrue(highestPriority.isPresent());
        assertEquals(100, highestPriority.get().getPriority());
        
        // 7. Verify all plugins are loaded
        List<PluginDescriptor> allPlugins = pluginManager.getAllPlugins();
        assertEquals(3, allPlugins.size());
        
        // 8. Stop and unload plugins
        pluginManager.stopPlugin("credit-card-processor");
        pluginManager.stopPlugin("bank-transfer-processor");
        pluginManager.stopPlugin("core-payment");
        
        pluginManager.unloadPlugin("credit-card-processor");
        pluginManager.unloadPlugin("bank-transfer-processor");
        pluginManager.unloadPlugin("core-payment");
        
        // 9. Verify all plugins are unloaded
        assertTrue(pluginManager.getAllPlugins().isEmpty());
        
        System.out.println("✅ Integration test completed successfully!");
    }
    
    @Test
    void shouldHandlePluginLifecycleCorrectly() throws PluginException {
        // Load and start core plugin
        pluginManager.getExtensionRegistry().registerExtensionPoint(
            "org.fireflyframework.payment.processor",
            PaymentProcessor.class
        );
        
        pluginManager.loadPlugin(CorePaymentPlugin.class);
        pluginManager.loadPlugin(CreditCardPlugin.class);
        
        // Verify initial states
        assertEquals(PluginState.INITIALIZED, 
            pluginManager.getPluginDescriptor("core-payment").get().state());
        assertEquals(PluginState.INITIALIZED, 
            pluginManager.getPluginDescriptor("credit-card-processor").get().state());
        
        // Start dependent plugin (should auto-start core)
        pluginManager.startPlugin("credit-card-processor");
        
        assertEquals(PluginState.STARTED, 
            pluginManager.getPluginDescriptor("core-payment").get().state());
        assertEquals(PluginState.STARTED, 
            pluginManager.getPluginDescriptor("credit-card-processor").get().state());
        
        // Stop plugins
        pluginManager.stopPlugin("credit-card-processor");
        assertEquals(PluginState.STOPPED, 
            pluginManager.getPluginDescriptor("credit-card-processor").get().state());
        
        // Core should still be started (we only stopped dependent plugin)
        assertEquals(PluginState.STARTED, 
            pluginManager.getPluginDescriptor("core-payment").get().state());
    }
    
    @Test
    void shouldHandleShutdownGracefully() throws PluginException {
        pluginManager.getExtensionRegistry().registerExtensionPoint(
            "org.fireflyframework.payment.processor",
            PaymentProcessor.class
        );
        
        pluginManager.loadPlugin(CorePaymentPlugin.class);
        pluginManager.loadPlugin(CreditCardPlugin.class);
        pluginManager.loadPlugin(BankTransferPlugin.class);
        
        pluginManager.startPlugin("core-payment");
        pluginManager.startPlugin("credit-card-processor");
        pluginManager.startPlugin("bank-transfer-processor");
        
        assertEquals(3, pluginManager.getAllPlugins().size());
        
        // Shutdown should stop and unload all plugins
        pluginManager.shutdown();
        
        assertTrue(pluginManager.getAllPlugins().isEmpty());
    }
}
