package org.fireflyframework.cache.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.manager.FireflyCacheManagerImpl;
import org.fireflyframework.cache.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CacheAutoConfigurationPostgresDefaultTest {
    @EnableConfigurationProperties(CacheProperties.class)
    static class BoundProperties { }

    private final ApplicationContextRunner properties = new ApplicationContextRunner()
            .withUserConfiguration(BoundProperties.class);
    private final ApplicationContextRunner cache = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

    @Test
    void boundDefaultSelectsPostgres() {
        properties.run(context -> assertThat(context.getBean(CacheProperties.class).getDefaultCacheType())
                .isEqualTo(CacheType.POSTGRES));
    }

    @Test
    void postgresIsEnabledByDefaultWithASeparateTechnicalSchemaAndNoAutomaticDdl() {
        properties.run(context -> {
            var postgres = context.getBean(CacheProperties.class).getPostgres();
            assertThat(postgres.isEnabled()).isTrue();
            assertThat(postgres.getSchema()).isEqualTo("firefly_cache");
            assertThat(postgres.isAutoCreateSchema()).isFalse();
        });
    }

    @Test
    void localL1RequiresExplicitOptIn() {
        properties.run(context -> assertThat(context.getBean(CacheProperties.class).getSmart().isEnabled()).isFalse());
    }

    @Test
    void defaultPostgresCannotBecomeAnUnreportedLocalCacheWhenItsFactoryIsMissing() {
        cache.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitlyRequestedPostgresCannotFallBackToCaffeine() {
        cache.withPropertyValues("firefly.cache.default-cache-type=POSTGRES", "firefly.cache.postgres.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitlySelectedCaffeineRemainsUsableWithoutPostgresConfiguration() {
        cache.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FireflyCacheManagerImpl.class).getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(context).doesNotHaveBean("fireflyCachePostgresConnectionFactory");
                });
    }

    @Test
    void disabledCachingCreatesNoManagerOrPostgresFactory() {
        cache.withPropertyValues("firefly.cache.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FireflyCacheManagerImpl.class);
            assertThat(context).doesNotHaveBean("fireflyCachePostgresConnectionFactory");
        });
    }

    @Test void explicitPropertiesFixtureIsNotDiscoveredByFrameworkComponentScanning() {
        var scanner=new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents(getClass().getPackageName()))
                .extracting(org.springframework.beans.factory.config.BeanDefinition::getBeanClassName)
                .doesNotContain(BoundProperties.class.getName());
    }
}
