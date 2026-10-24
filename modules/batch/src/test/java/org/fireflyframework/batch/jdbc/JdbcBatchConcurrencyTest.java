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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.fireflyframework.batch.fixture.CheckpointJobFixture;
import org.fireflyframework.batch.fixture.PostgresBatchFixture;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class JdbcBatchConcurrencyTest extends PostgresBatchFixture {
    private JobParameters parameters(String tenant, String run) {
        return new JobParametersBuilder()
                .addString("tenant", tenant, true)
                .addString("run", run, true)
                .toJobParameters();
    }

    @Test
    void runningAndCompletedDuplicateLaunchesCannotExecuteAgain() throws Exception {
        var source = schema("complete");
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var operator = context.getBean(JobOperator.class);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var effects = new java.util.concurrent.atomic.AtomicInteger();
            var helper = context.getBean(JdbcBatchSteps.class);
            var step =
                    helper.tasklet(
                                    "owned-admission-step",
                                    (contribution, chunk) -> {
                                        entered.countDown();
                                        if (!release.await(10, TimeUnit.SECONDS))
                                            throw new IllegalStateException(
                                                    "Owned barrier expired");
                                        effects.incrementAndGet();
                                        return RepeatStatus.FINISHED;
                                    })
                            .build();
            Job job = new JobBuilder("owned-admission-job", repository).start(step).build();
            context.getBean(MapJobRegistry.class).register(job);
            var parameters = parameters("owned-a", "stable-run");
            JobExecution first = operator.start(job, parameters);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> operator.start(job, parameters))
                        .isInstanceOf(
                                org.springframework.batch.core.launch
                                        .JobExecutionAlreadyRunningException.class);
                assertThat(effects.get()).isZero();
            } finally {
                release.countDown();
            }
            assertThat(CheckpointJobFixture.await(repository, first.getId()).getStatus())
                    .isEqualTo(BatchStatus.COMPLETED);
            assertThatThrownBy(() -> operator.start(job, parameters))
                    .isInstanceOf(
                            org.springframework.batch.core.launch
                                    .JobInstanceAlreadyCompleteException.class);
            assertThat(effects.get()).isEqualTo(1);
            assertThat(
                            new JdbcTemplate(source)
                                    .queryForObject(
                                            "SELECT count(*) FROM BATCH_JOB_INSTANCE",
                                            Integer.class))
                    .isEqualTo(1);
            assertThat(repository.getJobExecutions(first.getJobInstance())).hasSize(1);
            var otherTenant =
                    repository.createJobInstance(
                            job.getName(), parameters("owned-b", "stable-run"));
            assertThat(otherTenant.getId()).isNotEqualTo(first.getJobInstanceId());
        }
    }

    @Test
    void fullBoundedExecutorRejectsThirdExecutionWithoutCallerThreadEffects() throws Exception {
        var source = schema("complete");
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var operator = context.getBean(JobOperator.class);
            var entered = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var rejectedEntered = new AtomicBoolean();
            var effects = new java.util.concurrent.atomic.AtomicInteger();
            var helper = context.getBean(JdbcBatchSteps.class);
            var step =
                    helper.tasklet(
                                    "owned-bound-step",
                                    (contribution, chunk) -> {
                                        if (contribution
                                                .getStepExecution()
                                                .getJobParameters()
                                                .getString("run")
                                                .equals("third")) {
                                            rejectedEntered.set(true);
                                        }
                                        entered.countDown();
                                        if (!release.await(10, TimeUnit.SECONDS))
                                            throw new IllegalStateException(
                                                    "Owned barrier expired");
                                        effects.incrementAndGet();
                                        return RepeatStatus.FINISHED;
                                    })
                            .build();
            Job job = new JobBuilder("owned-bound-job", repository).start(step).build();
            context.getBean(MapJobRegistry.class).register(job);
            JobExecution first = operator.start(job, parameters("owned", "first"));
            JobExecution second = operator.start(job, parameters("owned", "second"));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                JobExecution third = operator.start(job, parameters("owned", "third"));
                assertThat(repository.getJobExecution(third.getId()).getStatus())
                        .isEqualTo(BatchStatus.FAILED);
                assertThat(rejectedEntered.get()).isFalse();
                assertThat(effects.get()).isZero();
            } finally {
                release.countDown();
            }
            assertThat(CheckpointJobFixture.await(repository, first.getId()).getStatus())
                    .isEqualTo(BatchStatus.COMPLETED);
            assertThat(CheckpointJobFixture.await(repository, second.getId()).getStatus())
                    .isEqualTo(BatchStatus.COMPLETED);
            assertThat(effects.get()).isEqualTo(2);
            assertThat(rejectedEntered.get()).isFalse();
        }
    }
}
