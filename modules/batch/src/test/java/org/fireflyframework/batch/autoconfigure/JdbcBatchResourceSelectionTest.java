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
import static org.mockito.Mockito.mock;

import io.r2dbc.spi.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.sql.Connection;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

class JdbcBatchResourceSelectionTest {
    private final AtomicInteger connections = new AtomicInteger();
    private final DataSource dataSource =
            new AbstractDataSource() {
                @Override
                public Connection getConnection() {
                    connections.incrementAndGet();
                    throw new AssertionError("Invalid resources must fail before JDBC acquisition");
                }

                @Override
                public Connection getConnection(String username, String password) {
                    return getConnection();
                }
            };

    private ApplicationContextRunner enabled() {
        return new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                DurableBatchAutoConfiguration.class,
                                DurableJdbcBatchAutoConfiguration.class))
                .withPropertyValues(
                        "firefly.batch.durable.enabled=true",
                        "spring.batch.job.enabled=false",
                        "firefly.batch.durable.data-source-ref=ownedDataSource",
                        "firefly.batch.durable.transaction-manager-ref=ownedManager",
                        "firefly.batch.durable.task-executor-ref=ownedExecutor");
    }

    private ThreadPoolTaskExecutor executor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        return executor;
    }

    private ApplicationContextRunner resources(Object source, Object manager, Object executor) {
        return enabled()
                .withBean("ownedDataSource", Object.class, () -> source)
                .withBean("ownedManager", Object.class, () -> manager)
                .withBean("ownedExecutor", Object.class, () -> executor);
    }

    private void rejects(ApplicationContextRunner runner, String reason) {
        runner.run(
                context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(reason);
                    assertThat(connections).hasValue(0);
                });
    }

    @Test
    void namedResourcesMustActuallyExist() {
        rejects(enabled(), "data-source-ref");
    }

    @Test
    void selectedDataSourceMustBeJdbc() {
        rejects(
                resources(
                        "not-a-datasource",
                        new DataSourceTransactionManager(dataSource),
                        executor()),
                "data-source-ref");
    }

    @Test
    void selectedManagerMustNotBeReactive() {
        rejects(
                resources(
                        dataSource,
                        new R2dbcTransactionManager(mock(ConnectionFactory.class)),
                        executor()),
                "transaction-manager-ref");
    }

    @Test
    void managerMustOwnTheExactSelectedDataSource() {
        DataSource other = mock(DataSource.class);
        rejects(
                resources(dataSource, new DataSourceTransactionManager(other), executor()),
                "same DataSource");
    }

    @Test
    void executorCannotRunJobsSynchronouslyOnCaller() {
        rejects(
                resources(
                        dataSource,
                        new DataSourceTransactionManager(dataSource),
                        new SyncTaskExecutor()),
                "task-executor-ref");
    }

    @Test
    void executorCannotHaveAnUnboundedMaximum() {
        var executor = executor();
        executor.setMaxPoolSize(Integer.MAX_VALUE);
        rejects(
                resources(dataSource, new DataSourceTransactionManager(dataSource), executor),
                "core=max");
    }

    @Test
    void executorCannotQueueUnboundedWork() {
        var executor = executor();
        executor.setQueueCapacity(Integer.MAX_VALUE);
        rejects(
                resources(dataSource, new DataSourceTransactionManager(dataSource), executor),
                "queueCapacity=0");
    }

    @Test
    void callerRunsRejectionIsNotAllowed() {
        var executor = executor();
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        rejects(
                resources(dataSource, new DataSourceTransactionManager(dataSource), executor),
                "AbortPolicy");
    }
}
