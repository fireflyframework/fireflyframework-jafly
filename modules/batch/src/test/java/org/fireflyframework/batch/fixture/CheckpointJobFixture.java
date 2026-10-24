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
package org.fireflyframework.batch.fixture;

import org.fireflyframework.batch.autoconfigure.DurableBatchAutoConfiguration;
import org.fireflyframework.batch.autoconfigure.DurableJdbcBatchAutoConfiguration;
import org.fireflyframework.batch.jdbc.JdbcBatchSteps;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

import javax.sql.DataSource;

public final class CheckpointJobFixture {
    private CheckpointJobFixture() {}

    public static AnnotationConfigApplicationContext open(DataSource source) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(
                                "owned-batch",
                                java.util.Map.of(
                                        "firefly.batch.durable.enabled",
                                        "true",
                                        "spring.batch.job.enabled",
                                        "false",
                                        "firefly.batch.durable.data-source-ref",
                                        "ownedDataSource",
                                        "firefly.batch.durable.transaction-manager-ref",
                                        "ownedManager",
                                        "firefly.batch.durable.task-executor-ref",
                                        "ownedExecutor")));
        context.registerBean("ownedDataSource", DataSource.class, () -> source);
        context.registerBean(
                "ownedManager",
                JdbcTransactionManager.class,
                () -> new JdbcTransactionManager(source));
        context.registerBean(
                "ownedExecutor",
                ThreadPoolTaskExecutor.class,
                () -> {
                    var executor = new ThreadPoolTaskExecutor();
                    executor.setCorePoolSize(2);
                    executor.setMaxPoolSize(2);
                    executor.setQueueCapacity(0);
                    executor.setThreadNamePrefix("owned-batch-");
                    return executor;
                });
        context.registerBean("ownedRegistry", MapJobRegistry.class, MapJobRegistry::new);
        context.register(
                DurableBatchAutoConfiguration.class, DurableJdbcBatchAutoConfiguration.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    public static Job job(
            AnnotationConfigApplicationContext context,
            DataSource source,
            String run,
            boolean failAtFive,
            boolean omittedManagerControl)
            throws Exception {
        return job(context, source, run, failAtFive, omittedManagerControl, () -> {});
    }

    public static Job job(
            AnnotationConfigApplicationContext context,
            DataSource source,
            String run,
            boolean failAtFive,
            boolean omittedManagerControl,
            Runnable beforeFour)
            throws Exception {
        var repository = context.getBean(JobRepository.class);
        var reader = new CursorReader(beforeFour);
        var helper = context.getBean(JdbcBatchSteps.class);
        var builder =
                omittedManagerControl
                        ? new StepBuilder("owned-chunk", repository).<Integer, Integer>chunk(3)
                        : helper.<Integer, Integer>chunk("owned-chunk", 3);
        var jdbc = new JdbcTemplate(source);
        var step =
                builder.reader(reader)
                        .writer(
                                items -> {
                                    for (Integer item : items) {
                                        jdbc.update(
                                                "INSERT INTO owned_items(run_id,item_no) VALUES"
                                                    + " (?,?)",
                                                run,
                                                item);
                                        if (failAtFive && item == 5)
                                            throw new IllegalStateException(
                                                    "Owned fixture writer failure at five");
                                    }
                                })
                        .build();
        Job job = new JobBuilder("owned-checkpoint-job", repository).start(step).build();
        context.getBean(MapJobRegistry.class).register(job);
        return job;
    }

    public static JobExecution await(JobRepository repository, long executionId)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            JobExecution current = repository.getJobExecution(executionId);
            if (current != null && !current.isRunning() && current.getEndTime() != null)
                return current;
            Thread.sleep(20);
        }
        throw new AssertionError(
                "Owned execution did not reach a stored terminal state: " + executionId);
    }

    public static final class CursorReader implements ItemStreamReader<Integer> {
        private int cursor;
        private final Runnable beforeFour;

        public CursorReader() {
            this(() -> {});
        }

        public CursorReader(Runnable beforeFour) {
            this.beforeFour = beforeFour;
        }

        @Override
        public void open(ExecutionContext context) {
            cursor = context.getInt("owned.cursor", 0);
        }

        @Override
        public Integer read() {
            if (cursor == 3) beforeFour.run();
            return cursor >= 10 ? null : ++cursor;
        }

        @Override
        public void update(ExecutionContext context) {
            context.putInt("owned.cursor", cursor);
        }
    }
}
