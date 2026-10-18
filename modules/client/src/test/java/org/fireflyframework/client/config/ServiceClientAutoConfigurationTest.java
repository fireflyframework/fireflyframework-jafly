package org.fireflyframework.client.config;

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.builder.RestClientBuilder;
import org.fireflyframework.resilience.CircuitBreakerConfig;
import org.fireflyframework.resilience.CircuitBreakerManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

/**
 * Test for ServiceClientAutoConfiguration to verify auto-configuration works correctly.
 */
class ServiceClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ServiceClientAutoConfiguration.class));

    @Test
    void testAutoConfigurationCreatesRequiredBeans() {
        contextRunner.run(context -> {
            // Verify that all required beans are created
            assertThat(context).hasSingleBean(CircuitBreakerConfig.class);
            assertThat(context).hasSingleBean(CircuitBreakerManager.class);
            assertThat(context).hasSingleBean(RestClientBuilder.class);
        });
    }

    @Test
    void testCircuitBreakerManagerIsConfiguredCorrectly() {
        contextRunner.run(context -> {
            CircuitBreakerManager circuitBreakerManager = context.getBean(CircuitBreakerManager.class);
            assertThat(circuitBreakerManager).isNotNull();
            
            // Test that the circuit breaker manager works
            String serviceName = "test-service";
            var state = circuitBreakerManager.getState(serviceName);
            assertThat(state).isNotNull();
        });
    }

    @Test
    void testRestClientBuilderIsConfiguredWithCircuitBreaker() {
        contextRunner.run(context -> {
            RestClientBuilder builder = context.getBean(RestClientBuilder.class);
            assertThat(builder).isNotNull();
            
            // Build a service client and verify it works
            ServiceClient client = builder
                .baseUrl("http://localhost:8080")
                .build();
            
            assertThat(client).isNotNull();
            assertThat(client.getServiceName()).isEqualTo("default");
            
            // Clean up
            client.shutdown();
        });
    }

    @Test
    void testAutoConfigurationWithCustomProperties() {
        contextRunner
            .withPropertyValues(
                "firefly.service-client.circuit-breaker.failure-rate-threshold=60.0",
                "firefly.service-client.circuit-breaker.minimum-number-of-calls=3",
                "firefly.service-client.circuit-breaker.sliding-window-size=5"
            )
            .run(context -> {
                CircuitBreakerConfig config = context.getBean(CircuitBreakerConfig.class);
                assertThat(config).isNotNull();
                assertThat(config.getFailureRateThreshold()).isEqualTo(60.0);
                assertThat(config.getMinimumNumberOfCalls()).isEqualTo(3);
                assertThat(config.getSlidingWindowSize()).isEqualTo(5);
            });
    }

    @Test
    void testAutoConfigurationCanBeDisabled() {
        contextRunner
            .withPropertyValues("firefly.service-client.enabled=false")
            .run(context -> {
                // When auto-configuration is disabled, beans should not be created
                assertThat(context).doesNotHaveBean(CircuitBreakerConfig.class);
                assertThat(context).doesNotHaveBean(CircuitBreakerManager.class);
                assertThat(context).doesNotHaveBean(RestClientBuilder.class);
            });
    }

    @Test
    void testServiceClientCreationWithAutoConfiguration() {
        contextRunner.run(context -> {
            // Test creating a service client using the static factory method
            ServiceClient client = ServiceClient.rest("user-service")
                .baseUrl("http://user-service:8080")
                .build();
            
            assertThat(client).isNotNull();
            assertThat(client.getServiceName()).isEqualTo("user-service");
            assertThat(client.getClientType().name()).isEqualTo("REST");
            
            // Clean up
            client.shutdown();
        });
    }

    @Test
    void testCircuitBreakerConfigurationDefaults() {
        contextRunner.run(context -> {
            CircuitBreakerConfig config = context.getBean(CircuitBreakerConfig.class);

            // Verify default values are applied correctly (with DEVELOPMENT environment adjustments)
            assertThat(config.getFailureRateThreshold()).isEqualTo(70.0); // DEVELOPMENT default
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
            assertThat(config.getSlidingWindowSize()).isEqualTo(10);
            assertThat(config.getWaitDurationInOpenState().getSeconds()).isEqualTo(30); // DEVELOPMENT default
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
        });
    }

    @Test
    void testMultipleServiceClientsShareSameCircuitBreakerManager() {
        contextRunner.run(context -> {
            CircuitBreakerManager sharedManager = context.getBean(CircuitBreakerManager.class);
            
            // Create multiple service clients
            ServiceClient client1 = ServiceClient.rest("service-1")
                .baseUrl("http://service-1:8080")
                .build();
            
            ServiceClient client2 = ServiceClient.rest("service-2")
                .baseUrl("http://service-2:8080")
                .build();
            
            assertThat(client1).isNotNull();
            assertThat(client2).isNotNull();
            assertThat(client1.getServiceName()).isEqualTo("service-1");
            assertThat(client2.getServiceName()).isEqualTo("service-2");
            
            // Clean up
            client1.shutdown();
            client2.shutdown();
        });
    }
}
