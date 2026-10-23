/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fireflyframework.batch.autoconfigure;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/** Opt-in infrastructure for explicitly configured durable JDBC jobs. */
@AutoConfiguration(
        beforeName = {
            "org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration",
            "org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration"
        })
@ConditionalOnProperty(prefix = "firefly.batch.durable", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DurableBatchProperties.class)
@Import(DurableBatchAutoConfiguration.JdbcInfrastructure.class)
public class DurableBatchAutoConfiguration {
    @Bean
    static BeanFactoryPostProcessor durableBatchClasspathGuard() {
        return beanFactory -> {
            for (String required :
                    new String[] {
                        "org.springframework.batch.core.repository.JobRepository",
                        "org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration",
                        "org.springframework.jdbc.datasource.DataSourceTransactionManager",
                        "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor"
                    }) {
                if (!ClassUtils.isPresent(required, beanFactory.getBeanClassLoader())) {
                    throw new IllegalStateException("Durable Batch requires " + required);
                }
            }
        };
    }

    @Bean
    InitializingBean durableBatchSettingsGuard(
            DurableBatchProperties properties, Environment environment) {
        return () -> {
            if (!Boolean.FALSE.equals(
                    environment.getProperty("spring.batch.job.enabled", Boolean.class))) {
                throw new IllegalStateException(
                        "Durable Batch requires explicit spring.batch.job.enabled=false");
            }
            requireReference(properties.getDataSourceRef(), "data-source-ref");
            requireReference(properties.getTransactionManagerRef(), "transaction-manager-ref");
            requireReference(properties.getTaskExecutorRef(), "task-executor-ref");
        };
    }

    @Bean
    @DependsOn("durableBatchSettingsGuard")
    JdbcBatchResources durableJdbcBatchResources(
            DurableBatchProperties properties, BeanFactory beans) {
        return JdbcBatchResources.resolve(properties, beans);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
            name =
                    "org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration")
    @Import(JdbcReadiness.class)
    static class JdbcInfrastructure {}

    @Configuration(proxyBeanMethods = false)
    static class JdbcReadiness {
        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
                org.fireflyframework.batch.jdbc.JdbcBatchSteps.class)
        org.fireflyframework.batch.jdbc.JdbcBatchSteps durableJdbcBatchSteps(
                org.springframework.batch.core.repository.JobRepository repository,
                JdbcBatchResources resources) {
            return new org.fireflyframework.batch.jdbc.JdbcBatchSteps(
                    repository, resources.transactionManager());
        }

        @Bean
        SmartInitializingSingleton durableBatchReadiness(
                JdbcBatchResources resources,
                ObjectProvider<org.springframework.batch.core.repository.JobRepository>
                        repositories,
                ObjectProvider<org.springframework.batch.core.launch.JobOperator> operators) {
            return () -> {
                var selected = repositories.stream().toList();
                if (selected.size() != 1 || operators.stream().count() != 1) {
                    throw new IllegalStateException(
                            "Durable Batch requires exactly one JobRepository and JobOperator");
                }
                if (selected.getFirst()
                        instanceof
                        org.springframework.batch.core.repository.support
                                .ResourcelessJobRepository) {
                    throw new IllegalStateException(
                            "Durable Batch cannot use a ResourcelessJobRepository");
                }
                JdbcBatchSchemaValidator.validate(resources.dataSource());
            };
        }
    }

    private static void requireReference(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("firefly.batch.durable." + property + " is required");
        }
    }
}
