package org.fireflyframework.common.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.reactive.ReactiveWebApplicationContext;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApplicationStartupTest.Application.class)
class ApplicationStartupTest {

    @Autowired
    CacheManager cacheManager;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void startsReactiveWithTheSpringCacheInfrastructureRequiredByTheApplicationLayer() {
        assertThat(applicationContext).isInstanceOf(ReactiveWebApplicationContext.class);
        assertThat(cacheManager).isNotNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
    }
}
