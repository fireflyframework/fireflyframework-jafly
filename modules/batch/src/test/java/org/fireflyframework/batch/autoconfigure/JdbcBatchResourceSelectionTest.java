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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.r2dbc.spi.ConnectionFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.sql.Connection;
import java.util.concurrent.SynchronousQueue;
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

    private JdbcBatchResources selectedInitializedExecutor(ThreadPoolTaskExecutor executor) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("ownedDataSource", dataSource);
        beans.registerSingleton("ownedManager", new DataSourceTransactionManager(dataSource));
        beans.registerSingleton("ownedExecutor", executor);
        var properties = new DurableBatchProperties();
        properties.setDataSourceRef("ownedDataSource");
        properties.setTransactionManagerRef("ownedManager");
        properties.setTaskExecutorRef("ownedExecutor");
        return JdbcBatchResources.resolve(properties, beans);
    }

    private void rejectsInitializedExecutor(ThreadPoolTaskExecutor executor, String reason) {
        try {
            assertThatThrownBy(() -> selectedInitializedExecutor(executor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(reason);
            assertThat(connections).hasValue(0);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void changedCapacityCannotHideInitializedUnboundedQueue() {
        var executor = executor();
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.initialize();
        executor.setQueueCapacity(0);
        assertThat(executor.getQueueCapacity()).isZero();
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                .isEqualTo(Integer.MAX_VALUE);
        rejectsInitializedExecutor(executor, "SynchronousQueue");
    }

    @Test
    void fullBoundedQueueCannotPassAsZeroCapacityHandoff() {
        var executor = executor();
        executor.setQueueCapacity(1);
        executor.initialize();
        executor.getThreadPoolExecutor().getQueue().add(() -> {});
        executor.setQueueCapacity(0);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        rejectsInitializedExecutor(executor, "SynchronousQueue");
    }

    @Test
    void backingMaximumCannotDifferFromFixedConfiguredLimit() {
        var executor = executor();
        executor.initialize();
        executor.getThreadPoolExecutor().setMaximumPoolSize(Integer.MAX_VALUE);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        rejectsInitializedExecutor(executor, "core=max");
    }

    @Test
    void backingCoreCannotBeZeroDespiteConfiguredLimit() {
        var executor = executor();
        executor.initialize();
        executor.getThreadPoolExecutor().setCorePoolSize(0);
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        rejectsInitializedExecutor(executor, "core=max");
    }

    @Test
    void backingFixedPoolCannotExceedThirtyTwoDespiteConfiguredLimit() {
        var executor = executor();
        executor.initialize();
        executor.getThreadPoolExecutor().setMaximumPoolSize(33);
        executor.getThreadPoolExecutor().setCorePoolSize(33);
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        rejectsInitializedExecutor(executor, "core=max");
    }

    @Test
    void backingCallerRunsPolicyIsRejected() {
        var executor = executor();
        executor.initialize();
        executor.getThreadPoolExecutor()
                .setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        rejectsInitializedExecutor(executor, "AbortPolicy");
    }

    @Test
    void actualSingleWorkerSynchronousExecutorIsAccepted() {
        acceptsFixedSynchronousExecutor(1);
    }

    @Test
    void actualThirtyTwoWorkerSynchronousExecutorIsAccepted() {
        acceptsFixedSynchronousExecutor(32);
    }

    private void acceptsFixedSynchronousExecutor(int size) {
        var executor = executor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.initialize();
        try {
            assertThat(executor.getThreadPoolExecutor().getQueue())
                    .isInstanceOf(SynchronousQueue.class);
            assertThat(selectedInitializedExecutor(executor).taskExecutor()).isSameAs(executor);
            assertThat(connections).hasValue(0);
        } finally {
            executor.shutdown();
        }
    }
}
