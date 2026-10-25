package org.fireflyframework.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExampleCredentialConfigurationTest {
    private void optionalCredential(String resource, String prefix, String variable) throws Exception {
        var input = new ClassPathResource(resource);
        var environment = new MockEnvironment();
        for (var source : new YamlPropertySourceLoader().load(resource, input))
            environment.getPropertySources().addLast(source);
        assertEquals(Boolean.FALSE, environment.getProperty(prefix + ".enabled", Boolean.class));
        assertNull(environment.getProperty(prefix + ".password"));
        String text = input.getContentAsString(StandardCharsets.UTF_8);
        String placeholder = "${" + variable + "}";
        assertTrue(text.contains("# password: " + placeholder));
        assertThrows(IllegalArgumentException.class,
                () -> environment.resolveRequiredPlaceholders(placeholder));
        String credential = UUID.randomUUID().toString();
        environment.setProperty(variable, credential);
        assertEquals(credential, environment.resolveRequiredPlaceholders(placeholder));
    }

    @Test
    void disabledRedisExampleHasOnlyExplicitOptInEnvironmentCredentials() throws Exception {
        optionalCredential("application-messaging-example.yml", "messaging.redis", "MESSAGING_REDIS_PASSWORD");
    }

    @Test
    void disabledJmsExampleHasOnlyExplicitOptInEnvironmentCredentials() throws Exception {
        optionalCredential("application-messaging-example.yml", "messaging.jms", "MESSAGING_JMS_PASSWORD");
    }

    @Test
    void disabledProxyExampleHasOnlyExplicitOptInEnvironmentCredentials() throws Exception {
        optionalCredential("application-webclient-example.yml", "webclient.proxy", "WEBCLIENT_PROXY_PASSWORD");
    }
}
