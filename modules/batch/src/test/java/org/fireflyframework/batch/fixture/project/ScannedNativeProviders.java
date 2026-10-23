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
package org.fireflyframework.batch.fixture.project;

import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.JobOperatorFactoryBean;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JdbcJobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class ScannedNativeProviders {
    @Bean
    JobRepository projectRepository(
            @Qualifier("ownedDataSource") DataSource source,
            @Qualifier("ownedManager") PlatformTransactionManager manager)
            throws Exception {
        var factory = new JdbcJobRepositoryFactoryBean();
        factory.setDataSource(source);
        factory.setTransactionManager(manager);
        factory.setDatabaseType("POSTGRES");
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean
    JobOperator projectOperator(
            JobRepository repository,
            @Qualifier("ownedManager") PlatformTransactionManager manager,
            @Qualifier("ownedExecutor") ThreadPoolTaskExecutor executor)
            throws Exception {
        var factory = new JobOperatorFactoryBean();
        factory.setJobRepository(repository);
        factory.setJobRegistry(new MapJobRegistry());
        factory.setTransactionManager(manager);
        factory.setTaskExecutor(executor);
        factory.afterPropertiesSet();
        return factory.getObject();
    }
}
