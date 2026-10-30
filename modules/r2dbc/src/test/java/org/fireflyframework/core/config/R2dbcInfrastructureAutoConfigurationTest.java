package org.fireflyframework.core.config;

import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.r2dbc.autoconfigure.DataR2dbcAutoConfiguration;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class R2dbcInfrastructureAutoConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(R2dbcAutoConfiguration.class,
            R2dbcTransactionAutoConfiguration.class));

    @Test
    void domainWithoutDatabaseInfrastructureStartsWithoutDatabaseBeans() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(R2dbcEntityTemplate.class);
            assertThat(application).doesNotHaveBean(ReactiveTransactionManager.class);
        });
    }

    @Test
    void excludedTransactionDependencyDoesNotBreakEmbeddedRuleDomain() {
        context.withClassLoader(new FilteredClassLoader("org.springframework.transaction"))
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).doesNotHaveBean("transactionManager");
            });
    }

    @Test
    void absentR2dbcApiDoesNotLoadDatabaseConfiguration() {
        context.withClassLoader(new FilteredClassLoader("io.r2dbc.spi", "org.springframework.data.r2dbc"))
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).doesNotHaveBean("transactionManager");
            });
    }

    @Test
    void suppliedTemplateCanInitializeWithoutCreatingTransactionManager() {
        context.withBean(R2dbcEntityTemplate.class, () -> mock(R2dbcEntityTemplate.class))
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).hasSingleBean(R2dbcAutoConfiguration.class);
                assertThat(application).doesNotHaveBean(ReactiveTransactionManager.class);
            });
    }

    @Test
    void suppliedConnectionFactoryGetsReactiveTransactionManager() {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        context.withBean(ConnectionFactory.class, () -> factory).run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).hasSingleBean(ReactiveTransactionManager.class);
            assertThat(application.getBean(ReactiveTransactionManager.class))
                .isInstanceOfSatisfying(R2dbcTransactionManager.class,
                    manager -> assertThat(manager.getConnectionFactory()).isSameAs(factory));
        });
    }

    @Test
    void userTransactionManagerIsPreserved() {
        ReactiveTransactionManager custom = mock(ReactiveTransactionManager.class);
        context.withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean("customReactiveTransactions", ReactiveTransactionManager.class, () -> custom)
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).hasSingleBean(ReactiveTransactionManager.class);
                assertThat(application.getBean(ReactiveTransactionManager.class)).isSameAs(custom);
            });
    }

    @Test
    void ambiguousConnectionFactoriesDoNotChooseAnArbitraryDatabase() {
        context.withBean("firstDatabase", ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean("secondDatabase", ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).doesNotHaveBean(ReactiveTransactionManager.class);
            });
    }

    @Test
    void realBootDataConfigurationRunsBeforeFrameworkTemplateInitialization() {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.getMetadata()).thenReturn(() -> "PostgreSQL");
        context.withConfiguration(AutoConfigurations.of(
                org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration.class,
                DataR2dbcAutoConfiguration.class,
                R2dbcTransactionManagerAutoConfiguration.class))
            .withBean(ConnectionFactory.class, () -> factory)
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).hasSingleBean(R2dbcEntityTemplate.class);
                assertThat(application).hasSingleBean(R2dbcAutoConfiguration.class);
                assertThat(application).hasSingleBean(ReactiveTransactionManager.class);
            });
    }
}
