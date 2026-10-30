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

import org.fireflyframework.batch.jdbc.JdbcBatchSteps;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.sql.DataSource;

/** Application-owned synthetic recipe, not a framework financial run authority. */
public final class OwnedRunFixture {
    public record Intent(String tenant, String run, String canonical) {
        public String digest() {
            try {
                return HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PolicyDecisionPort policy;
    private final SecurityPrincipal servicePrincipal;

    public OwnedRunFixture(
            DataSource source,
            DataSourceTransactionManager manager,
            PolicyDecisionPort policy,
            SecurityPrincipal servicePrincipal) {
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new TransactionTemplate(manager);
        this.policy = policy;
        this.servicePrincipal = servicePrincipal;
    }

    public String claim(Intent intent) {
        authorize(intent);
        return transaction.execute(
                status -> {
                    int inserted =
                            jdbc.update(
                                    "INSERT INTO"
                                        + " owned_run_claim(tenant,run_id,canonical,digest,state)"
                                        + " VALUES (?,?,?,?,'OPEN') ON CONFLICT(tenant,run_id) DO"
                                        + " NOTHING",
                                    intent.tenant(),
                                    intent.run(),
                                    intent.canonical(),
                                    intent.digest());
                    var stored =
                            jdbc.queryForMap(
                                    "SELECT canonical,digest FROM owned_run_claim WHERE tenant=?"
                                        + " AND run_id=? FOR UPDATE",
                                    intent.tenant(),
                                    intent.run());
                    match(intent, stored);
                    return inserted == 1 ? "CREATED" : "REPLAY";
                });
    }

    private void authorize(Intent intent) {
        if (servicePrincipal == null
                || !intent.tenant().equals(servicePrincipal.tenantId())
                || !servicePrincipal.hasScope("owned-run:execute")) {
            throw new IllegalStateException("Current owner service tenant or scope denied");
        }
        try {
            var publisher =
                    policy.authorize(
                            servicePrincipal,
                            "owned-run:execute",
                            "owned-run:" + intent.tenant() + ":" + intent.run(),
                            Map.of(
                                    "tenant",
                                    intent.tenant(),
                                    "run",
                                    intent.run(),
                                    "intentDigest",
                                    intent.digest()));
            var decision =
                    publisher == null ? null : publisher.block(java.time.Duration.ofSeconds(1));
            if (decision == null || !decision.granted() || !decision.obligations().isEmpty()) {
                throw new IllegalStateException("Current owner service policy denied");
            }
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Current owner service policy denied", failure);
        }
    }

    private void requireMatchingClaim(Intent intent) {
        match(
                intent,
                jdbc.queryForMap(
                        "SELECT canonical,digest FROM owned_run_claim WHERE tenant=? AND run_id=?",
                        intent.tenant(),
                        intent.run()));
    }

    private static void match(Intent intent, Map<String, Object> stored) {
        if (!intent.canonical().equals(stored.get("canonical"))
                || !intent.digest().equals(stored.get("digest"))) {
            throw new IllegalStateException("Owner run intent conflicts with immutable claim");
        }
    }

    public Job job(AnnotationConfigApplicationContext context, Intent intent, boolean failAtFive)
            throws Exception {
        requireMatchingClaim(intent);
        var repository = context.getBean(JobRepository.class);
        var reader = new CheckpointJobFixture.CursorReader();
        var step =
                context.getBean(JdbcBatchSteps.class)
                        .<Integer, Integer>chunk("owned-run-step", 3)
                        .reader(reader)
                        .writer(
                                items -> {
                                    for (Integer item : items) {
                                        authorize(intent);
                                        requireMatchingClaim(intent);
                                        jdbc.update(
                                                "INSERT INTO owned_run_items(tenant,run_id,item_no)"
                                                    + " VALUES (?,?,?)",
                                                intent.tenant(),
                                                intent.run(),
                                                item);
                                        if (failAtFive && item == 5)
                                            throw new IllegalStateException(
                                                    "Owned recipe fixture failure at five");
                                    }
                                })
                        .build();
        Job job = new JobBuilder("owned-run-job", repository).start(step).build();
        context.getBean(MapJobRegistry.class).register(job);
        return job;
    }

    public boolean completeOnlyWithStoredAcknowledgments(Intent intent) {
        authorize(intent);
        return transaction.execute(
                status -> {
                    requireMatchingClaim(intent);
                    Integer items =
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM owned_run_items WHERE tenant=? AND"
                                        + " run_id=?",
                                    Integer.class,
                                    intent.tenant(),
                                    intent.run());
                    Integer missing =
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM owned_run_items i LEFT JOIN"
                                        + " owned_run_receipts r ON r.tenant=i.tenant AND"
                                        + " r.run_id=i.run_id AND r.item_no=i.item_no WHERE"
                                        + " i.tenant=? AND i.run_id=? AND r.item_no IS NULL",
                                    Integer.class,
                                    intent.tenant(),
                                    intent.run());
                    if (items != 10 || missing != 0) return false;
                    jdbc.update(
                            "UPDATE owned_run_claim SET state='COMPLETED' WHERE tenant=? AND"
                                + " run_id=?",
                            intent.tenant(),
                            intent.run());
                    return true;
                });
    }
}
