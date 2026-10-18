package org.fireflyframework.security.idp.internaldb.config;

import java.io.IOException;
import org.fireflyframework.security.idp.config.IdpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InternalDbExampleConfigurationTest {
    @Test
    void exampleSelectsTheProviderAndBindsTheDeploymentJwtSecret() {
        new ApplicationContextRunner()
                .withUserConfiguration(BindingConfiguration.class)
                .withPropertyValues("IDP_JWT_SECRET=example-test-secret-with-at-least-32-bytes", "DB_PASSWORD=example-test-only")
                .withInitializer(context -> {
                    try {
                        new YamlPropertySourceLoader().load("internal-db-example",
                                new ClassPathResource("application-internal-db-example.yml"))
                                .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (IOException error) {
                        throw new IllegalStateException(error);
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IdpProperties.class).getProvider()).isEqualTo("internal-db");
                    assertThat(context.getBean(InternalDbProperties.class).getJwt().getSecret())
                            .isEqualTo("example-test-secret-with-at-least-32-bytes");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({IdpProperties.class, InternalDbProperties.class})
    static class BindingConfiguration {
    }
}
