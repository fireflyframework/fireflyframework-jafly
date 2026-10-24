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

import static org.assertj.core.api.Assertions.assertThat;

import org.fireflyframework.batch.fixture.CheckpointJobFixture;
import org.fireflyframework.batch.fixture.PostgresBatchFixture;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import javax.sql.DataSource;

class JdbcCheckpointDatabaseTest extends PostgresBatchFixture {
    private DataSource prepared() throws Exception {
        DataSource source = schema("complete");
        String selected;
        try (var connection = source.getConnection()) {
            selected = connection.getSchema();
        }
        try (var connection = owner().getConnection();
                var statement = connection.createStatement()) {
            connection.setSchema(selected);
            statement.execute(
                    "CREATE TABLE owned_items(run_id varchar(100) NOT NULL,item_no integer NOT"
                        + " NULL,PRIMARY KEY(run_id,item_no))");
            statement.execute("GRANT SELECT,INSERT ON owned_items TO " + role);
        }
        return source;
    }

    private List<Integer> rows(DataSource source) {
        return new JdbcTemplate(source)
                .queryForList("SELECT item_no FROM owned_items ORDER BY item_no", Integer.class);
    }

    @Test
    void failedChunkRollsBackBothItemsAndCheckpointThenRestartsInFreshContext() throws Exception {
        DataSource source = prepared();
        var parameters =
                new JobParametersBuilder()
                        .addString("tenant", "owned-tenant", true)
                        .addString("run", "stable-owner-run", true)
                        .toJobParameters();
        long failedId;
        long instanceId;
        try (var first = CheckpointJobFixture.open(source)) {
            var repository = first.getBean(JobRepository.class);
            var job = CheckpointJobFixture.job(first, source, "stable-owner-run", true, false);
            JobExecution failed =
                    CheckpointJobFixture.await(
                            repository,
                            first.getBean(JobOperator.class).start(job, parameters).getId());
            failedId = failed.getId();
            instanceId = failed.getJobInstanceId();
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(rows(source)).containsExactly(1, 2, 3);
            var step = repository.getLastStepExecution(failed.getJobInstance(), "owned-chunk");
            assertThat(step.getExecutionContext().getInt("owned.cursor")).isEqualTo(3);
            assertThat(step.getRollbackCount()).isPositive();
        }
        try (var second = CheckpointJobFixture.open(source)) {
            var repository = second.getBean(JobRepository.class);
            JobExecution original = repository.getJobExecution(failedId);
            assertThat(original.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(
                            repository
                                    .getLastStepExecution(original.getJobInstance(), "owned-chunk")
                                    .getExecutionContext()
                                    .getInt("owned.cursor"))
                    .isEqualTo(3);
            CheckpointJobFixture.job(second, source, "stable-owner-run", false, false);
            JobExecution completed =
                    CheckpointJobFixture.await(
                            repository,
                            second.getBean(JobOperator.class).restart(original).getId());
            assertThat(completed.getId()).isNotEqualTo(failedId);
            assertThat(completed.getJobInstanceId()).isEqualTo(instanceId);
            assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(repository.getJobExecution(failedId).getStatus())
                    .isEqualTo(BatchStatus.FAILED);
            assertThat(repository.getJobExecutions(completed.getJobInstance())).hasSize(2);
            assertThat(
                            repository
                                    .getLastStepExecution(completed.getJobInstance(), "owned-chunk")
                                    .getExecutionContext()
                                    .getInt("owned.cursor"))
                    .isEqualTo(10);
            assertThat(rows(source)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        }
    }

    @Test
    void suppliedTaskletBuilderRollsBackSameResourceWritesOnFailure() throws Exception {
        DataSource source = prepared();
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var helper = context.getBean(JdbcBatchSteps.class);
            var step =
                    helper.tasklet(
                                    "owned-tasklet",
                                    (contribution, chunkContext) -> {
                                        new JdbcTemplate(source)
                                                .update(
                                                        "INSERT INTO owned_items(run_id,item_no)"
                                                            + " VALUES (?,?)",
                                                        "tasklet-run",
                                                        1);
                                        throw new IllegalStateException("Owned tasklet failure");
                                    })
                            .build();
            var job =
                    new org.springframework.batch.core.job.builder.JobBuilder(
                                    "owned-tasklet-job", repository)
                            .start(step)
                            .build();
            context.getBean(
                            org.springframework.batch.core.configuration.support.MapJobRegistry
                                    .class)
                    .register(job);
            var failed =
                    CheckpointJobFixture.await(
                            repository,
                            context.getBean(JobOperator.class)
                                    .start(
                                            job,
                                            new JobParametersBuilder()
                                                    .addString("run", "tasklet-run", true)
                                                    .toJobParameters())
                                    .getId());
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(rows(source)).isEmpty();
            assertThat(
                            repository
                                    .getLastStepExecution(failed.getJobInstance(), "owned-tasklet")
                                    .getRollbackCount())
                    .isPositive();
        }
    }

    @Test
    void nativeOmittedManagerControlLeavesPartialItemsDespiteFailedMetadata() throws Exception {
        DataSource source = prepared();
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var job =
                    CheckpointJobFixture.job(
                            context, source, "omitted-manager-control", true, true);
            var failed =
                    CheckpointJobFixture.await(
                            repository,
                            context.getBean(JobOperator.class)
                                    .start(
                                            job,
                                            new JobParametersBuilder()
                                                    .addString(
                                                            "run", "omitted-manager-control", true)
                                                    .toJobParameters())
                                    .getId());
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(rows(source)).containsExactly(1, 2, 3, 4, 5);
        }
    }
}
