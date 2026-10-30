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

import static org.assertj.core.api.Assertions.assertThat;

import org.fireflyframework.batch.fixture.PostgresBatchFixture;
import org.fireflyframework.batch.fixture.project.ScannedNativeProviders;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.JobOperatorFactoryBean;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

class DurableBatchBootCoexistenceTest extends PostgresBatchFixture {
    private ApplicationContextRunner enabled(DataSource source) {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                DurableBatchAutoConfiguration.class,
                                DurableJdbcBatchAutoConfiguration.class,
                                BatchAutoConfiguration.class,
                                BatchJobLauncherAutoConfiguration.class))
                .withPropertyValues(
                        "firefly.batch.durable.enabled=true",
                        "spring.batch.job.enabled=false",
                        "firefly.batch.durable.data-source-ref=ownedDataSource",
                        "firefly.batch.durable.transaction-manager-ref=ownedManager",
                        "firefly.batch.durable.task-executor-ref=ownedExecutor")
                .withBean("ownedDataSource", DataSource.class, () -> source)
                .withBean(
                        "ownedManager",
                        JdbcTransactionManager.class,
                        () -> new JdbcTransactionManager(source))
                .withBean(
                        "ownedExecutor",
                        ThreadPoolTaskExecutor.class,
                        PostgresBatchFixture::executor);
    }

    @Test
    void realBootConfigurationCannotCreateResourceFreeRepositoryOrStartupRunner() throws Exception {
        enabled(schema("complete"))
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(JobRepository.class)
                                    .hasSingleBean(JobOperator.class);
                            assertThat(context.getBean(JobRepository.class))
                                    .isNotInstanceOf(ResourcelessJobRepository.class);
                            assertThat(context).doesNotHaveBean(JobLauncherApplicationRunner.class);
                        });
    }

    @Test
    void ordinaryProjectBeansReplaceNativeDefaultsWithoutPrimary() throws Exception {
        assertPlainProject(PlainProject.class);
    }

    @Test
    void plainProjectImportFirstStillReplacesDefaults() throws Exception {
        assertPlainProject(ProjectFirst.class);
    }

    @Test
    void plainProjectImportLastStillReplacesDefaults() throws Exception {
        assertPlainProject(ProjectLast.class);
    }

    @Test
    void actualProjectComponentScanReplacesProvidersWithoutPrimary() throws Exception {
        assertPlainProject(ScannedProject.class);
    }

    private void assertPlainProject(Class<?> project) throws Exception {
        DataSource source = schema("complete");
        enabled(source)
                .withUserConfiguration(project)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(JobRepository.class)
                                    .hasSingleBean(JobOperator.class);
                            assertThat(context.getBean(JobRepository.class))
                                    .isSameAs(context.getBean("projectRepository"));
                            assertThat(context.getBean(JobOperator.class))
                                    .isSameAs(context.getBean("projectOperator"));
                            context.getBean(JobRepository.class)
                                    .createJobInstance(
                                            "project-owned-job",
                                            new JobParametersBuilder()
                                                    .addString("tenant", "owned", true)
                                                    .toJobParameters());
                        });
        assertThat(
                        new org.springframework.jdbc.core.JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void projectJdbcConfigurationBacksOffDefaultGroup() throws Exception {
        enabled(schema("complete"))
                .withUserConfiguration(ProjectJdbc.class)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(JobRepository.class)
                                    .hasSingleBean(JobOperator.class);
                            assertThat(context.getBeansOfType(JdbcDefaultBatchConfiguration.class))
                                    .hasSize(1);
                            assertThat(context.getBean(JdbcDefaultBatchConfiguration.class))
                                    .isInstanceOf(ProjectJdbc.class);
                            context.getBean(JobRepository.class)
                                    .createJobInstance(
                                            "project-config-job",
                                            new JobParametersBuilder().toJobParameters());
                        });
    }

    @Test
    void enabledProjectResourceFreeRepositoryIsRejected() throws Exception {
        enabled(schema("complete"))
                .withUserConfiguration(ResourceFreeProject.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledModuleLeavesProjectResourceFreeLegacyConfigurationAlone() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                DurableBatchAutoConfiguration.class,
                                DurableJdbcBatchAutoConfiguration.class))
                .withUserConfiguration(ResourceFreeProject.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed().hasSingleBean(JobRepository.class);
                            assertThat(context.getBean(JobRepository.class))
                                    .isInstanceOf(ResourcelessJobRepository.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = ScannedNativeProviders.class)
    static class ScannedProject {}

    @Configuration(proxyBeanMethods = false)
    static class PlainProject {
        @Bean
        JobRepository projectRepository(JdbcBatchResources resources) throws Exception {
            var factory = new JdbcJobRepositoryFactoryBean();
            factory.setDataSource(resources.dataSource());
            factory.setTransactionManager(resources.transactionManager());
            factory.setDatabaseType("POSTGRES");
            factory.afterPropertiesSet();
            return factory.getObject();
        }

        @Bean
        JobOperator projectOperator(JobRepository repository, JdbcBatchResources resources)
                throws Exception {
            var factory = new JobOperatorFactoryBean();
            factory.setJobRepository(repository);
            factory.setJobRegistry(new MapJobRegistry());
            factory.setTransactionManager(resources.transactionManager());
            factory.setTaskExecutor(resources.taskExecutor());
            factory.afterPropertiesSet();
            return factory.getObject();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PlainProject.class, DurableBatchAutoConfiguration.class})
    static class ProjectFirst {}

    @Configuration(proxyBeanMethods = false)
    @Import({DurableBatchAutoConfiguration.class, PlainProject.class})
    static class ProjectLast {}

    @Configuration(proxyBeanMethods = false)
    static class ProjectJdbc extends JdbcDefaultBatchConfiguration {
        private final JdbcBatchResources resources;

        ProjectJdbc(JdbcBatchResources resources) {
            this.resources = resources;
        }

        @Override
        protected DataSource getDataSource() {
            return resources.dataSource();
        }

        @Override
        protected PlatformTransactionManager getTransactionManager() {
            return resources.transactionManager();
        }

        @Override
        protected TaskExecutor getTaskExecutor() {
            return resources.taskExecutor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ResourceFreeProject {
        @Bean
        JobRepository projectRepository() {
            return new ResourcelessJobRepository();
        }
    }
}
