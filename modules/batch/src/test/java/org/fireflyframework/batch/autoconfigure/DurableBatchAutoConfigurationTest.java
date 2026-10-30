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

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DurableBatchAutoConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    DurableBatchAutoConfiguration.class,
                                    DurableJdbcBatchAutoConfiguration.class));

    @Test
    void absentModeCreatesNoBatchInfrastructure() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(JobRepository.class)
                            .doesNotHaveBean(JobOperator.class);
                    assertThat(context).doesNotHaveBean(javax.sql.DataSource.class);
                });
    }

    @Test
    void falseModeIgnoresIncompleteDurableSettings() {
        runner.withPropertyValues(
                        "firefly.batch.durable.enabled=false",
                        "firefly.batch.durable.data-source-ref=not-present")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .doesNotHaveBean(JobRepository.class)
                                    .doesNotHaveBean(JobOperator.class);
                        });
    }

    @Test
    void disabledModeToleratesMissingBatchClasses() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.batch"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void disabledModeToleratesMissingJdbcClasses() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void explicitModeCannotSilentlyIgnoreMissingJdbcClasses() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
                .withPropertyValues(
                        "firefly.batch.durable.enabled=true", "spring.batch.job.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitModeCannotSilentlyIgnoreMissingBatchClasses() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.batch"))
                .withPropertyValues(
                        "firefly.batch.durable.enabled=true", "spring.batch.job.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitModeRequiresDataSourceReference() {
        runner.withPropertyValues(
                        "firefly.batch.durable.enabled=true", "spring.batch.job.enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasStackTraceContaining("data-source-ref");
                        });
    }

    @Test
    void explicitModeRequiresStartupRunnerToBeDisabled() {
        runner.withPropertyValues(
                        "firefly.batch.durable.enabled=true",
                        "firefly.batch.durable.data-source-ref=ownedDataSource",
                        "firefly.batch.durable.transaction-manager-ref=ownedManager",
                        "firefly.batch.durable.task-executor-ref=ownedExecutor")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasStackTraceContaining("spring.batch.job.enabled=false");
                        });
    }
}
