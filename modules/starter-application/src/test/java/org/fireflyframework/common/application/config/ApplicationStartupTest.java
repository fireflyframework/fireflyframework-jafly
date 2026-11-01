package org.fireflyframework.common.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.manager.FireflyCacheManagerImpl;
import org.fireflyframework.common.application.metadata.ApplicationMetadataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.reactive.ReactiveWebApplicationContext;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(classes = ApplicationStartupTest.Application.class,
        properties = "firefly.cache.default-cache-type=CAFFEINE")
class ApplicationStartupTest {
    private static final AtomicReference<Boolean> metadataInitializationState =
            new AtomicReference<>();

    @Autowired CacheManager cacheManager;

    @Autowired FireflyCacheManagerImpl fireflyCacheManager;

    @Autowired ApplicationContext applicationContext;

    @Test
    void startsReactiveWithTheSpringCacheInfrastructureRequiredByTheApplicationLayer() {
        assertThat(applicationContext).isInstanceOf(ReactiveWebApplicationContext.class);
        assertThat(cacheManager).isNotNull();
        assertThat(fireflyCacheManager.getCacheType()).isEqualTo(CacheType.CAFFEINE);
        assertThat(metadataInitializationState.get()).isFalse();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {
        @Bean
        static BeanPostProcessor metadataInitializationProbe(
                ConfigurableListableBeanFactory factory) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof ApplicationMetadataProvider) {
                        metadataInitializationState.set(
                                factory.containsSingleton("deferredApplicationDependency"));
                    }
                    return bean;
                }
            };
        }

        @Bean
        @Lazy
        Object deferredApplicationDependency() {
            return new Object();
        }
    }
}
