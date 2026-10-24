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
package org.fireflyframework.batch.jdbc;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.builder.TaskletStepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.util.Assert;

/** Native JDBC step-builder entry point. */
public final class JdbcBatchSteps {
    private final JobRepository repository;
    private final DataSourceTransactionManager manager;

    public JdbcBatchSteps(JobRepository repository, DataSourceTransactionManager manager) {
        Assert.notNull(repository, "JobRepository is required");
        Assert.notNull(manager, "JDBC transaction manager is required");
        this.repository = repository;
        this.manager = manager;
    }

    public <I, O> ChunkOrientedStepBuilder<I, O> chunk(String name, int size) {
        Assert.hasText(name, "Step name is required");
        Assert.isTrue(size > 0, "Chunk size must be positive");
        return new StepBuilder(name, repository).<I, O>chunk(size).transactionManager(manager);
    }

    public TaskletStepBuilder tasklet(String name, Tasklet tasklet) {
        Assert.hasText(name, "Step name is required");
        Assert.notNull(tasklet, "Tasklet is required");
        return new StepBuilder(name, repository).tasklet(tasklet, manager);
    }
}
