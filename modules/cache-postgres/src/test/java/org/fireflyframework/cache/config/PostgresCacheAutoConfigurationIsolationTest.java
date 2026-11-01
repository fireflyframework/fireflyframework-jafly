package org.fireflyframework.cache.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.r2dbc.spi.ConnectionFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.cache.manager.FireflyCacheManagerImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

class PostgresCacheAutoConfigurationIsolationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class,
                    PostgresCacheAutoConfiguration.class))
            .withPropertyValues("firefly.cache.postgres.enabled=true");

    static class ExternalCacheFactory {
        @Bean
        AtomicInteger cacheFactoryLookups() { return new AtomicInteger(); }

        @Bean("fireflyCachePostgresConnectionFactory")
        @Lazy
        ConnectionFactory cacheFactory(AtomicInteger cacheFactoryLookups) {
            cacheFactoryLookups.incrementAndGet();
            return mock(ConnectionFactory.class);
        }
    }

    @Test
    void explicitCaffeineDoesNotResolveAnExternalNamedPostgresFactory() {
        context.withUserConfiguration(ExternalCacheFactory.class)
                .withPropertyValues("firefly.cache.default-cache-type=CAFFEINE")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).hasSingleBean(FireflyCacheManagerImpl.class);
                    assertThat(application.getBean(AtomicInteger.class).get()).isZero();
                });
    }

    @Test
    void explicitCaffeineDoesNotValidateOrCreateTheOwnedPostgresPool() {
        context.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.postgres.database=technical", "firefly.cache.postgres.username=cache",
                        "firefly.cache.postgres.port=-1")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application.getBeanFactory().containsSingleton("fireflyCachePostgresConnectionFactory"))
                            .isFalse();
                });
    }

    @Test
    void ownedCacheFactoryIsLazyAndNotABusinessDefaultCandidate() {
        context.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.postgres.database=technical", "firefly.cache.postgres.username=cache")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    var bean = (AbstractBeanDefinition) application.getBeanFactory()
                            .getBeanDefinition("fireflyCachePostgresConnectionFactory");
                    assertThat(bean.isLazyInit()).isTrue();
                    assertThat(bean.isDefaultCandidate()).isFalse();
                });
    }

    @Test
    void autoWithoutPostgresConfigurationCanSelectAnAvailableLocalProvider() {
        context.withPropertyValues("firefly.cache.default-cache-type=AUTO")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application.getBean(FireflyCacheManagerImpl.class).getCacheType())
                            .isEqualTo(org.fireflyframework.cache.core.CacheType.CAFFEINE);
                    assertThat(application).doesNotHaveBean("fireflyCachePostgresConnectionFactory");
                });
    }

    @Test
    void disabledCacheDoesNotRegisterAnOwnedPostgresPool() {
        context.withPropertyValues("firefly.cache.enabled=false",
                        "firefly.cache.postgres.database=technical", "firefly.cache.postgres.username=cache")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application).doesNotHaveBean("fireflyCachePostgresConnectionFactory");
                });
    }

    @Test
    void selectedOwnedPoolRejectsNegativeMinimumInsteadOfClamping() {
        context.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.postgres.database=technical", "firefly.cache.postgres.username=cache",
                        "firefly.cache.postgres.min-pool-size=-1")
                .run(application -> assertThatThrownBy(() -> application.getBean("fireflyCachePostgresConnectionFactory"))
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void selectedOwnedPoolRejectsMinimumAboveMaximum() {
        context.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.postgres.database=technical", "firefly.cache.postgres.username=cache",
                        "firefly.cache.postgres.min-pool-size=3", "firefly.cache.postgres.max-pool-size=2")
                .run(application -> assertThatThrownBy(() -> application.getBean("fireflyCachePostgresConnectionFactory"))
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void selectedOwnedPoolHasAnAcquisitionDeadlineAndIsDisposedAtContextShutdown() {
        var actual = new java.util.concurrent.atomic.AtomicReference<io.r2dbc.pool.ConnectionPool>();
        context.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE",
                        "firefly.cache.postgres.database=technical", "firefly.cache.postgres.username=cache",
                        "firefly.cache.postgres.min-pool-size=0", "firefly.cache.postgres.acquire-timeout=PT2S")
                .run(application -> {
                    var pool = (io.r2dbc.pool.ConnectionPool) application.getBean("fireflyCachePostgresConnectionFactory");
                    actual.set(pool);
                    assertThat(org.springframework.test.util.ReflectionTestUtils.getField(pool, "maxAcquireTime"))
                            .isEqualTo(java.time.Duration.ofSeconds(2));
                    assertThat(pool.isDisposed()).isFalse();
                });
        assertThat(actual.get().isDisposed()).isTrue();
    }

    @Test void explicitTechnicalFixturesCannotOverrideResourcesThroughComponentScanning() {
        var scanner=new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents(getClass().getPackageName()))
                .extracting(org.springframework.beans.factory.config.BeanDefinition::getBeanClassName)
                .doesNotContain(ExternalCacheFactory.class.getName(),
                        PostgresCacheNamespaceBindingTest.ExternalTechnicalFactory.class.getName());
    }
}
