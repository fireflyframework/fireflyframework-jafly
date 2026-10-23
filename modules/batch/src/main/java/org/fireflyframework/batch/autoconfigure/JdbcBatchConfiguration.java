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

import org.springframework.batch.core.configuration.support.JdbcDefaultBatchConfiguration;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class JdbcBatchConfiguration extends JdbcDefaultBatchConfiguration {
    private final JdbcBatchResources resources;

    JdbcBatchConfiguration(JdbcBatchResources resources) {
        this.resources = resources;
    }

    @Bean
    @Override
    @ConditionalOnMissingBean(value = JobRepository.class, name = "jobRepository")
    public JobRepository jobRepository() {
        JdbcBatchSchemaValidator.validate(resources.dataSource());
        return super.jobRepository();
    }

    @Bean
    @Override
    @ConditionalOnMissingBean(value = JobOperator.class, name = "jobOperator")
    public JobOperator jobOperator(JobRepository repository) {
        return super.jobOperator(repository);
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
