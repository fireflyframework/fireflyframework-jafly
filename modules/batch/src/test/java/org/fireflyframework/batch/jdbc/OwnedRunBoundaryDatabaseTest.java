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
import org.fireflyframework.batch.fixture.OwnedRunFixture;
import org.fireflyframework.batch.fixture.PostgresBatchFixture;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.Obligation;
import org.fireflyframework.security.core.policy.EmbeddedPolicyDecisionAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

class OwnedRunBoundaryDatabaseTest extends PostgresBatchFixture {
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
                    "CREATE TABLE owned_run_claim(tenant varchar(100),run_id varchar(100),canonical"
                        + " text NOT NULL,digest varchar(64) NOT NULL,state varchar(20) NOT"
                        + " NULL,PRIMARY KEY(tenant,run_id));CREATE TABLE owned_run_items(tenant"
                        + " varchar(100),run_id varchar(100),item_no integer,PRIMARY"
                        + " KEY(tenant,run_id,item_no),FOREIGN KEY(tenant,run_id) REFERENCES"
                        + " owned_run_claim(tenant,run_id));CREATE TABLE owned_run_receipts(tenant"
                        + " varchar(100),run_id varchar(100),item_no integer,PRIMARY"
                        + " KEY(tenant,run_id,item_no),FOREIGN KEY(tenant,run_id,item_no)"
                        + " REFERENCES owned_run_items(tenant,run_id,item_no));GRANT"
                        + " SELECT,INSERT,UPDATE ON owned_run_claim TO "
                            + role
                            + ";"
                            + "GRANT SELECT,INSERT ON owned_run_items,owned_run_receipts TO "
                            + role);
        }
        return source;
    }

    private OwnedRunFixture.Intent intent(String tenant, String model) {
        return new OwnedRunFixture.Intent(
                tenant,
                "stable-run",
                "book=fictional-book;cutoff=2026-10-22T00:00:00Z;"
                        + "snapshot=fixture-snapshot-1;model="
                        + model
                        + ";policy=fixture-policy-1;currency=fixture-currency-1");
    }

    private OwnedRunFixture recipe(DataSource source, AtomicReference<Mono<Decision>> current) {
        var policy =
                new EmbeddedPolicyDecisionAdapter(
                        List.of((principal, action, resource, context) -> current.get()), true);
        return new OwnedRunFixture(
                source,
                new JdbcTransactionManager(source),
                policy,
                org.fireflyframework.security.api.domain.SecurityPrincipal.builder()
                        .subject("owned-fixture-service")
                        .issuer("owned-fixture-deployment")
                        .tenantId("owned-a")
                        .scopes(java.util.Set.of("owned-run:execute"))
                        .build());
    }

    @Test
    void immutableFullIntentClaimAcceptsExactReplayAndRejectsChangedIntent() throws Exception {
        DataSource source = prepared();
        var recipe = recipe(source, new AtomicReference<>(Mono.just(Decision.permit())));
        var original = intent("owned-a", "fixture-model-1");
        assertThat(recipe.claim(original)).isEqualTo("CREATED");
        assertThat(recipe.claim(original)).isEqualTo("REPLAY");
        assertThatThrownBy(() -> recipe.claim(intent("owned-a", "changed-model")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("intent");
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT canonical FROM owned_run_claim", String.class))
                .isEqualTo(original.canonical());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM owned_run_claim", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM owned_run_items", Integer.class))
                .isZero();
    }

    @Test
    void differentJobIntentCannotUseAnExistingOwnerClaim() throws Exception {
        DataSource source = prepared();
        var recipe = recipe(source, new AtomicReference<>(Mono.just(Decision.permit())));
        recipe.claim(intent("owned-a", "fixture-model-1"));
        try (var context = CheckpointJobFixture.open(source)) {
            assertThatThrownBy(() -> recipe.job(context, intent("owned-a", "changed-model"), false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("intent");
        }
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM owned_run_items", Integer.class))
                .isZero();
    }

    @Test
    void callerRunTenantCannotSelectTheCurrentServiceTenant() throws Exception {
        DataSource source = prepared();
        var recipe = recipe(source, new AtomicReference<>(Mono.just(Decision.permit())));
        assertThatThrownBy(() -> recipe.claim(intent("owned-b", "fixture-model-1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM owned_run_claim", Integer.class))
                .isZero();
    }

    @Test
    void restartUsesCurrentServicePolicyInsteadOfRetainingEarlierPermit() throws Exception {
        DataSource source = prepared();
        var current = new AtomicReference<>(Mono.just(Decision.permit()));
        var original = intent("owned-a", "fixture-model-1");
        var recipe = recipe(source, current);
        recipe.claim(original);
        var parameters =
                new JobParametersBuilder()
                        .addString("tenant", original.tenant(), true)
                        .addString("run", original.run(), true)
                        .addString("intent", original.digest(), true)
                        .toJobParameters();
        long failedId;
        try (var first = CheckpointJobFixture.open(source)) {
            var repository = first.getBean(JobRepository.class);
            var job = recipe.job(first, original, true);
            var failed =
                    CheckpointJobFixture.await(
                            repository,
                            first.getBean(JobOperator.class).start(job, parameters).getId());
            failedId = failed.getId();
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        }
        assertThat(
                        new JdbcTemplate(source)
                                .queryForList(
                                        "SELECT item_no FROM owned_run_items ORDER BY item_no",
                                        Integer.class))
                .containsExactly(1, 2, 3);
        current.set(Mono.just(Decision.deny("current fixture policy denied")));
        var restartedRecipe = recipe(source, current);
        assertThatThrownBy(() -> restartedRecipe.claim(original))
                .isInstanceOf(IllegalStateException.class);
        try (var second = CheckpointJobFixture.open(source)) {
            var repository = second.getBean(JobRepository.class);
            restartedRecipe.job(second, original, false);
            var failed =
                    CheckpointJobFixture.await(
                            repository,
                            second.getBean(JobOperator.class)
                                    .restart(repository.getJobExecution(failedId))
                                    .getId());
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        }
        assertThat(
                        new JdbcTemplate(source)
                                .queryForList(
                                        "SELECT item_no FROM owned_run_items ORDER BY item_no",
                                        Integer.class))
                .containsExactly(1, 2, 3);
    }

    private static java.util.stream.Stream<Mono<Decision>> invalidCurrentDecisions() {
        return java.util.stream.Stream.of(
                Mono.<Decision>empty(),
                Mono.<Decision>error(new IllegalStateException("owned policy failure")),
                Mono.just(
                        Decision.permit(List.of(Obligation.of("fixture-unfulfilled", Map.of())))));
    }

    @ParameterizedTest
    @MethodSource("invalidCurrentDecisions")
    void emptyFailedAndUnfulfilledObligationDecisionsCannotProduceItems(Mono<Decision> result)
            throws Exception {
        DataSource source = prepared();
        var current = new AtomicReference<>(Mono.just(Decision.permit()));
        var recipe = recipe(source, current);
        var intent = intent("owned-a", "fixture-model-1");
        recipe.claim(intent);
        current.set(result);
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var job = recipe.job(context, intent, false);
            var failed =
                    CheckpointJobFixture.await(
                            repository,
                            context.getBean(JobOperator.class)
                                    .start(
                                            job,
                                            new JobParametersBuilder()
                                                    .addString("tenant", intent.tenant(), true)
                                                    .addString("run", intent.run(), true)
                                                    .addString("intent", intent.digest(), true)
                                                    .toJobParameters())
                                    .getId());
            assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        }
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM owned_run_items", Integer.class))
                .isZero();
    }

    @Test
    void nativeCompletedMetadataCannotFinalizeOwnerRunWithoutStoredAcknowledgments()
            throws Exception {
        DataSource source = prepared();
        var recipe = recipe(source, new AtomicReference<>(Mono.just(Decision.permit())));
        var intent = intent("owned-a", "fixture-model-1");
        recipe.claim(intent);
        try (var context = CheckpointJobFixture.open(source)) {
            var repository = context.getBean(JobRepository.class);
            var job = recipe.job(context, intent, false);
            var completed =
                    CheckpointJobFixture.await(
                            repository,
                            context.getBean(JobOperator.class)
                                    .start(
                                            job,
                                            new JobParametersBuilder()
                                                    .addString("tenant", intent.tenant(), true)
                                                    .addString("run", intent.run(), true)
                                                    .addString("intent", intent.digest(), true)
                                                    .toJobParameters())
                                    .getId());
            assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        }
        assertThat(recipe.completeOnlyWithStoredAcknowledgments(intent)).isFalse();
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject("SELECT state FROM owned_run_claim", String.class))
                .isEqualTo("OPEN");
    }
}
