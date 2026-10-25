package org.fireflyframework.eventsourcing.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventStoreTestCredentialsTest {
    private MockEnvironment testProfile() throws Exception {
        var environment = new MockEnvironment();
        var source = new YamlPropertySourceLoader().load("event-store", new ClassPathResource("application.yml"))
                .stream().filter(value -> "test".equals(value.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();
        environment.getPropertySources().addLast(source);
        return environment;
    }

    @Test
    void testProfileHasNoImplicitEmptyCredential() throws Exception {
        var environment = testProfile();
        assertThrows(IllegalArgumentException.class, () -> environment.getProperty("spring.r2dbc.password"));
    }

    @Test
    void explicitDynamicTestCredentialOverridesTheProfileWithoutAnEnvironmentSecret() throws Exception {
        var environment = testProfile();
        String credential = UUID.randomUUID().toString();
        environment.setProperty("spring.r2dbc.password", credential);
        assertEquals(credential, environment.getProperty("spring.r2dbc.password"));
    }

    @Test
    void explicitTestCredentialResolvesWithoutChangingTheInMemoryTarget() throws Exception {
        var environment = testProfile();
        String credential = UUID.randomUUID().toString();
        environment.setProperty("EVENT_STORE_TEST_PASSWORD", credential);
        assertEquals(credential, environment.getProperty("spring.r2dbc.password"));
        assertEquals("r2dbc:h2:mem:///testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", environment.getProperty("spring.r2dbc.url"));
        assertEquals("sa", environment.getProperty("spring.r2dbc.username"));
        assertEquals(Boolean.FALSE, environment.getProperty("spring.flyway.enabled", Boolean.class));
    }
}
