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

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Test-owned native consumer; its runtime classpath has no test framework or containers. */
public final class BatchProcessMain {
    public static void main(String[] args) throws Exception {
        var source = new PGSimpleDataSource();
        source.setURL(required("OWNED_BATCH_URL"));
        source.setUser(required("OWNED_BATCH_USER"));
        source.setPassword(required("OWNED_BATCH_PASSWORD"));
        source.setCurrentSchema(required("OWNED_BATCH_SCHEMA"));
        System.out.println(
                "OWNED_MODULE_ORIGIN="
                        + org.fireflyframework.batch.jdbc.JdbcBatchSteps.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation());
        String mode = args[0];
        String run = required("OWNED_BATCH_RUN");
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var operator = context.getBean(JobOperator.class);
            var job =
                    CheckpointJobFixture.job(
                            context,
                            source,
                            run,
                            false,
                            false,
                            () -> {
                                String release = System.getenv("OWNED_BATCH_RELEASE_FILE");
                                if (release != null) {
                                    System.out.println("OWNED_HOLD_BEFORE_FOUR=true");
                                    System.out.flush();
                                    long deadline =
                                            System.nanoTime() + Duration.ofSeconds(60).toNanos();
                                    while (!Files.exists(Path.of(release))) {
                                        if (System.nanoTime() > deadline)
                                            throw new IllegalStateException(
                                                    "Owned barrier timed out");
                                        try {
                                            Thread.sleep(20);
                                        } catch (InterruptedException interrupted) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(
                                                    "Owned barrier interrupted", interrupted);
                                        }
                                    }
                                }
                            });
            if (mode.equals("stop")) {
                var execution = repository.getJobExecution(Long.parseLong(args[1]));
                System.out.println("OWNED_STOP_ACK=" + operator.stop(execution));
                return;
            }
            org.springframework.batch.core.job.JobExecution started;
            if (mode.equals("recover-restart") || mode.equals("restart")) {
                var original = repository.getJobExecution(Long.parseLong(args[1]));
                if (mode.equals("recover-restart")) {
                    operator.recover(original);
                    var observed = repository.getJobExecution(original.getId());
                    if (observed.getStatus() != BatchStatus.FAILED)
                        throw new AssertionError("Recovery was not persisted");
                    System.out.println("OWNED_RECOVERED_STORED_STATUS=" + observed.getStatus());
                    original = observed;
                }
                started = operator.restart(original);
            } else if (mode.equals("start")) {
                String startFile = System.getenv("OWNED_BATCH_START_FILE");
                System.out.println("OWNED_READY_TO_START=true");
                System.out.flush();
                if (startFile != null) {
                    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                    while (!Files.exists(Path.of(startFile))) {
                        if (System.nanoTime() > deadline)
                            throw new IllegalStateException("Owned launch barrier timed out");
                        Thread.sleep(20);
                    }
                }
                started =
                        operator.start(
                                job,
                                new JobParametersBuilder()
                                        .addString("tenant", required("OWNED_BATCH_TENANT"), true)
                                        .addString("run", run, true)
                                        .toJobParameters());
            } else {
                throw new IllegalArgumentException("Unknown owned process mode");
            }
            System.out.println("OWNED_EXECUTION_ID=" + started.getId());
            System.out.flush();
            var completed = CheckpointJobFixture.await(repository, started.getId());
            System.out.println("OWNED_FINAL_STATUS=" + completed.getStatus());
            if (completed.getStatus() != BatchStatus.COMPLETED
                    && completed.getStatus() != BatchStatus.STOPPED) {
                throw new AssertionError("Owned execution failed: " + completed.getStatus());
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank())
            throw new IllegalStateException("Missing owned fixture setting: " + name);
        return value;
    }
}
