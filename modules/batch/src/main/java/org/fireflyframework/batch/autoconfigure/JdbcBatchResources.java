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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.sql.DataSource;

record JdbcBatchResources(
        DataSource dataSource,
        DataSourceTransactionManager transactionManager,
        ThreadPoolTaskExecutor taskExecutor) {
    static JdbcBatchResources resolve(DurableBatchProperties properties, BeanFactory beans) {
        DataSource source =
                selected(beans, properties.getDataSourceRef(), DataSource.class, "data-source-ref");
        DataSourceTransactionManager manager =
                selected(
                        beans,
                        properties.getTransactionManagerRef(),
                        DataSourceTransactionManager.class,
                        "transaction-manager-ref");
        if (manager.getDataSource() != source) {
            throw new IllegalStateException(
                    "Durable Batch manager must own the same DataSource selected by"
                            + " data-source-ref");
        }
        ThreadPoolTaskExecutor executor =
                selected(
                        beans,
                        properties.getTaskExecutorRef(),
                        ThreadPoolTaskExecutor.class,
                        "task-executor-ref");
        if (executor.getCorePoolSize() < 1
                || executor.getCorePoolSize() > 32
                || executor.getCorePoolSize() != executor.getMaxPoolSize()) {
            throw new IllegalStateException(
                    "Durable Batch task-executor-ref requires core=max in 1..32");
        }
        if (executor.getQueueCapacity() != 0) {
            throw new IllegalStateException(
                    "Durable Batch task-executor-ref requires queueCapacity=0");
        }
        ThreadPoolExecutor initialized = executor.getThreadPoolExecutor();
        if (initialized.getCorePoolSize() < 1
                || initialized.getCorePoolSize() > 32
                || initialized.getCorePoolSize() != initialized.getMaximumPoolSize()) {
            throw new IllegalStateException(
                    "Durable Batch initialized executor requires core=max in 1..32");
        }
        if (!(initialized.getQueue() instanceof SynchronousQueue)) {
            throw new IllegalStateException(
                    "Durable Batch initialized executor requires a SynchronousQueue");
        }
        if (!(initialized.getRejectedExecutionHandler()
                instanceof ThreadPoolExecutor.AbortPolicy)) {
            throw new IllegalStateException("Durable Batch task-executor-ref requires AbortPolicy");
        }
        return new JdbcBatchResources(source, manager, executor);
    }

    private static <T> T selected(BeanFactory beans, String name, Class<T> type, String property) {
        try {
            return beans.getBean(name, type);
        } catch (BeansException error) {
            throw new IllegalStateException(
                    "firefly.batch.durable."
                            + property
                            + " must select a "
                            + type.getSimpleName()
                            + " bean named '"
                            + name
                            + "'",
                    error);
        }
    }
}
