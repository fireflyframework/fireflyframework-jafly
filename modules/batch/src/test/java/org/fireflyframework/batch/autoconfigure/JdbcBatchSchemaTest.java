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

import org.fireflyframework.batch.fixture.PostgresBatchFixture;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;

class JdbcBatchSchemaTest extends PostgresBatchFixture {
    private ApplicationContextRunner configured(DataSource source) {
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
                        "firefly.batch.durable.task-executor-ref=ownedExecutor")
                .withBean("ownedDataSource", DataSource.class, () -> source)
                .withBean(
                        "ownedManager",
                        JdbcTransactionManager.class,
                        () -> new JdbcTransactionManager(source))
                .withBean(
                        "ownedExecutor",
                        ThreadPoolTaskExecutor.class,
                        PostgresBatchFixture::executor);
    }

    @Test
    void genuineNativeRepositoryPersistsWithoutRuntimeSchemaOwnership() throws Exception {
        DataSource source = schema("complete");
        configured(source)
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(JobRepository.class)
                                    .hasSingleBean(JobOperator.class);
                            JobRepository repository = context.getBean(JobRepository.class);
                            assertThat(repository).isNotInstanceOf(ResourcelessJobRepository.class);
                            var parameters =
                                    new JobParametersBuilder()
                                            .addString("tenant", "owned-tenant", true)
                                            .toJobParameters();
                            var instance = repository.createJobInstance("owned-job", parameters);
                            assertThat(instance.getId()).isPositive();
                            assertThat(repository.getJobInstance("owned-job", parameters).getId())
                                    .isEqualTo(instance.getId());
                            assertThat(
                                            new JdbcTemplate(source)
                                                    .queryForObject(
                                                            "SELECT"
                                                                + " has_schema_privilege(current_user,current_schema(),'CREATE')",
                                                            Boolean.class))
                                    .isFalse();
                        });
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE WHERE"
                                            + " JOB_NAME='owned-job'",
                                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void enabledNativeContextSuppliesJdbcBoundStepBuilderHelper() throws Exception {
        configured(schema("complete"))
                .run(
                        context -> {
                            assertThat(context)
                                    .hasNotFailed()
                                    .hasSingleBean(
                                            org.fireflyframework.batch.jdbc.JdbcBatchSteps.class);
                        });
    }

    @Test
    void missingSchemaCannotBeAcceptedAsDurable() throws Exception {
        configured(schema("empty")).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingNativeColumnFailsBeforeJobCreation() throws Exception {
        DataSource source = schema("missing-column");
        configured(source).run(context -> assertThat(context).hasFailed());
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isZero();
    }

    @Test
    void incompatibleNativeColumnTypeFailsBeforeJobCreation() throws Exception {
        DataSource source = schema("wrong-column-type");
        configured(source).run(context -> assertThat(context).hasFailed());
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isZero();
    }

    @Test
    void readOnlyMetadataGrantCannotBeAcceptedAsDurableWriter() throws Exception {
        configured(schema("read-only")).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingNativeSequenceFailsBeforeJobCreation() throws Exception {
        DataSource source = schema("missing-sequence");
        configured(source).run(context -> assertThat(context).hasFailed());
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isZero();
    }

    @Test
    void inaccessibleSchemaFailsReadiness() throws Exception {
        configured(schema("no-access")).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitNamesIgnoreAnotherPrimaryResource() throws Exception {
        DataSource selected = schema("complete");
        DataSource other = schema("empty");
        configured(selected)
                .withBean(
                        "anotherDataSource",
                        DataSource.class,
                        () -> other,
                        definition -> definition.setPrimary(true))
                .withBean(
                        "anotherManager",
                        JdbcTransactionManager.class,
                        () -> new JdbcTransactionManager(other),
                        definition -> definition.setPrimary(true))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed().hasSingleBean(JobRepository.class);
                            context.getBean(JobRepository.class)
                                    .createJobInstance(
                                            "explicit-selected-job",
                                            new JobParametersBuilder()
                                                    .addString("run", "owned-run", true)
                                                    .toJobParameters());
                        });
        assertThat(
                        new JdbcTemplate(selected)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isEqualTo(1);
    }
}
