package org.fireflyframework.cache.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.util.function.BiFunction;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.cache.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresCacheNamespaceBindingTest {
    static class ExternalTechnicalFactory {
        @Bean(name="fireflyCachePostgresConnectionFactory",defaultCandidate=false)
        @SuppressWarnings({"unchecked","rawtypes"})
        ConnectionFactory cacheFactory() {
            var factory=mock(ConnectionFactory.class);var connection=mock(Connection.class);
            var statement=mock(Statement.class);var result=mock(Result.class);
            doReturn(Mono.just(connection)).when(factory).create();when(connection.close()).thenReturn(Mono.empty());
            when(connection.createStatement(anyString())).thenReturn(statement);doReturn(Flux.just(result)).when(statement).execute();
            when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());return factory;
        }
    }
    final ApplicationContextRunner context=new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class,PostgresCacheAutoConfiguration.class))
            .withUserConfiguration(ExternalTechnicalFactory.class);

    @Test void applicationNameProvidesTheNamespaceOnlyWhenExplicitPrefixIsMissing() {
        context.withPropertyValues("spring.application.name=service-a").run(application -> {
            assertThat(application).hasNotFailed();assertThat(application).hasSingleBean(FireflyCacheManager.class);
            assertThat(application.getBean(CacheProperties.class).getPostgres().getKeyPrefix()).isEqualTo("service-a");
        });
    }
    @Test void explicitNamespaceOverridesTheApplicationName() {
        context.withPropertyValues("spring.application.name=service-a","firefly.cache.postgres.key-prefix=explicit-service")
                .run(application -> {assertThat(application).hasNotFailed();assertThat(application.getBean(CacheProperties.class)
                        .getPostgres().getKeyPrefix()).isEqualTo("explicit-service");});
    }
    @Test void explicitlyBlankPrefixCanUseTheApplicationNameFallback() {
        context.withPropertyValues("spring.application.name=service-a","firefly.cache.postgres.key-prefix=")
                .run(application -> {assertThat(application).hasNotFailed();assertThat(application.getBean(CacheProperties.class)
                        .getPostgres().getKeyPrefix()).isEqualTo("service-a");});
    }
    @Test void selectedPostgresCannotStartWithoutAnyNamespace() {
        context.run(application -> assertThat(application).hasFailed());
    }
    @Test void explicitlyLocalCacheStillNeedsNoApplicationNameOrPostgresConfiguration() {
        context.withPropertyValues("firefly.cache.default-cache-type=CAFFEINE")
                .run(application -> assertThat(application).hasNotFailed());
    }
}
