package org.fireflyframework.core.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Configuration class for managing R2DBC transactions in a reactive application.
 *
 * This class enables Spring's reactive transaction management and defines a bean for
 * the {@link ReactiveTransactionManager} configured with an R2DBC {@link ConnectionFactory}.
 *
 * Key features:
 * - Provides reactive transaction manager support using {@link R2dbcTransactionManager}.
 * - Makes transaction management available for reactive database operations within the application.
 *
 * Annotations:
 * - {@code @AutoConfiguration}: Indicates that this class is a Spring Boot auto-configuration class.
 * - {@code @EnableTransactionManagement}: Enables Spring's annotation-driven transaction management capabilities.
 *
 * Bean Definitions:
 * - {@link ReactiveTransactionManager}: Configured to manage transactions for R2DBC-based reactive data sources.
 */
@AutoConfiguration
@EnableTransactionManagement
public class R2dbcTransactionAutoConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

}