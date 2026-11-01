package org.fireflyframework.domain.config;

import org.fireflyframework.cache.config.CacheAutoConfiguration;
import org.fireflyframework.cache.config.PostgresCacheAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.util.Properties;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcConnectionDetails;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresCacheArchitectureContextTest {
    @AutoConfigurationPackage
    @ImportAutoConfiguration({CacheAutoConfiguration.class, PostgresCacheAutoConfiguration.class,
            R2dbcAutoConfiguration.class, DataR2dbcAutoConfiguration.class,
            DataR2dbcRepositoriesAutoConfiguration.class, R2dbcTransactionManagerAutoConfiguration.class})
    static class ActualBootPersistenceGraph {
        @Bean(name = "fireflyCachePostgresConnectionFactory", defaultCandidate = false)
        @SuppressWarnings({"rawtypes", "unchecked"})
        ConnectionFactory technicalCacheFactory() {
            var factory = mock(ConnectionFactory.class);
            var connection = mock(Connection.class);
            var statement = mock(Statement.class);
            var result = mock(Result.class);
            when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
            doReturn(Mono.just(connection)).when(factory).create();
            when(connection.createStatement(anyString())).thenReturn(statement);
            doReturn(Flux.just(result)).when(statement).execute();
            when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());
            when(connection.close()).thenReturn(Mono.empty());
            return factory;
        }
    }

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ActualBootPersistenceGraph.class)
            .withPropertyValues("spring.r2dbc.pool.initial-size=0", "spring.sql.init.mode=never",
                    "spring.application.name=synthetic-architecture-service");

    void statelessRole() {
        context.withInitializer(application -> {
            String expected = "org.fireflyframework.domain.config.StatelessDomainEnvironmentPostProcessorImpl";
            boolean registered = false;
            try {
                var resources = getClass().getClassLoader().getResources("META-INF/spring.factories");
                while (resources.hasMoreElements()) {
                    var registration = new Properties();
                    try (var input = resources.nextElement().openStream()) { registration.load(input); }
                    String names = registration.getProperty(EnvironmentPostProcessor.class.getName(), "");
                    registered |= java.util.Arrays.asList(names.split(",")).contains(expected);
                }
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Cannot read starter registration", error);
            }
            assertThat(registered).as("actual starter EnvironmentPostProcessor registration").isTrue();
            new StatelessDomainEnvironmentPostProcessorImpl().postProcessEnvironment(application.getEnvironment(), new SpringApplication());
        }).withPropertyValues("spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application.getBean(org.fireflyframework.cache.manager.FireflyCacheManagerImpl.class).getCacheType())
                            .isEqualTo(org.fireflyframework.cache.core.CacheType.POSTGRES);
                    assertThat(application).doesNotHaveBean("connectionFactory");
                    assertThat(application).doesNotHaveBean(R2dbcEntityTemplate.class);
                    assertThat(application).doesNotHaveBean(ReactiveTransactionManager.class);
                    assertThat(application.getBeanNamesForType(RepositoryFactoryBeanSupport.class, true, false)).isEmpty();
                    assertThat(application.getEnvironment().getProperty("spring.autoconfigure.exclude"))
                            .contains("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration");
                });
    }

    @Test void statelessStarterWithTechnicalCacheDoesNotCreateBusinessPersistence() { statelessRole(); }

    void assertCore(org.springframework.boot.test.context.assertj.AssertableApplicationContext application) {
        assertThat(application).hasNotFailed();
        var business = application.getBean("connectionFactory", ConnectionFactory.class);
        assertThat(business).isNotSameAs(application.getBean("fireflyCachePostgresConnectionFactory"));
        assertThat(application.getBean(R2dbcEntityTemplate.class).getDatabaseClient().getConnectionFactory()).isSameAs(business);
        assertThat(application.getBean(R2dbcTransactionManager.class).getConnectionFactory()).isSameAs(business);
    }

    @Test void coreBusinessUrlRetainsItsOwnFactoryTemplateAndTransactions() {
        context.withPropertyValues("spring.r2dbc.url=r2dbc:postgresql://127.0.0.1:9/business",
                "spring.r2dbc.username=business").run(this::assertCore);
    }

    @Test void coreConnectionDetailsWithoutUrlRetainBusinessPersistence() {
        context.withBean(R2dbcConnectionDetails.class, () -> () -> ConnectionFactoryOptions.parse(
                "r2dbc:postgresql://business@127.0.0.1:9/business")).run(this::assertCore);
    }

    @Test void coreExplicitBusinessFactoryWithoutUrlRetainsBusinessPersistence() {
        var business = mock(ConnectionFactory.class);
        when(business.getMetadata()).thenReturn(() -> "PostgreSQL");
        context.withBean("connectionFactory", ConnectionFactory.class, () -> business).run(this::assertCore);
    }

    @Test void explicitArchitectureGraphIsNotAComponentScanCandidate() {
        var scanner=new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents(getClass().getPackageName()))
                .extracting(org.springframework.beans.factory.config.BeanDefinition::getBeanClassName)
                .doesNotContain(ActualBootPersistenceGraph.class.getName());
    }
}
