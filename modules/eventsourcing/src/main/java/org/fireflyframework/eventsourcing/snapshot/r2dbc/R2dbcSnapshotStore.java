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

package org.fireflyframework.eventsourcing.snapshot.r2dbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.eventsourcing.snapshot.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R2DBC-based implementation of {@link SnapshotStore}.
 *
 * <p>Uses {@link DatabaseClient} for reactive, non-blocking snapshot persistence
 * against the {@code snapshots} table created by the V2 migration.</p>
 *
 * <p>Snapshots are serialized as JSON with an embedded class name wrapper so that
 * concrete {@link Snapshot} subtypes can be deserialized polymorphically without
 * requiring {@code @JsonTypeInfo} on the Snapshot interface.</p>
 */
@Slf4j
public class R2dbcSnapshotStore implements SnapshotStore {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public R2dbcSnapshotStore(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
        log.info("R2dbcSnapshotStore initialized");
    }

    @Override
    public Mono<Void> saveSnapshot(Snapshot snapshot) {
        String snapshotData;
        try {
            snapshotData = serializeSnapshot(snapshot);
        } catch (JsonProcessingException e) {
            return Mono.error(new SnapshotException("Failed to serialize snapshot for aggregate "
                    + snapshot.getAggregateId(), e));
        }

        String sql = """
                INSERT INTO snapshots (aggregate_id, aggregate_type, aggregate_version, snapshot_data, created_at)
                VALUES (:aggregateId, :aggregateType, :aggregateVersion, :snapshotData, :createdAt)
                ON CONFLICT (aggregate_id, aggregate_type, aggregate_version)
                DO UPDATE SET snapshot_data = :snapshotData, updated_at = NOW()
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", snapshot.getAggregateId())
                .bind("aggregateType", snapshot.getSnapshotType())
                .bind("aggregateVersion", snapshot.getVersion())
                .bind("snapshotData", snapshotData)
                .bind("createdAt", snapshot.getCreatedAt())
                .fetch()
                .rowsUpdated()
                .doOnSuccess(rows -> log.debug("Saved snapshot for aggregate {} at version {}",
                        snapshot.getAggregateId(), snapshot.getVersion()))
                .then();
    }

    @Override
    public Mono<Snapshot> loadLatestSnapshot(UUID aggregateId, String snapshotType) {
        String sql = """
                SELECT aggregate_id, aggregate_type, aggregate_version, snapshot_data, created_at
                FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                ORDER BY aggregate_version DESC
                LIMIT 1
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .map((row, metadata) -> deserializeSnapshot(
                        row.get("snapshot_data", String.class),
                        row.get("aggregate_id", UUID.class),
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_version", Long.class),
                        row.get("created_at", Instant.class)))
                .one()
                .doOnNext(s -> log.debug("Loaded latest snapshot for aggregate {} at version {}",
                        aggregateId, s.getVersion()));
    }

    @Override
    public Mono<Snapshot> loadSnapshotAtOrBeforeVersion(UUID aggregateId, String snapshotType, long maxVersion) {
        String sql = """
                SELECT aggregate_id, aggregate_type, aggregate_version, snapshot_data, created_at
                FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                  AND aggregate_version <= :maxVersion
                ORDER BY aggregate_version DESC
                LIMIT 1
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .bind("maxVersion", maxVersion)
                .map((row, metadata) -> deserializeSnapshot(
                        row.get("snapshot_data", String.class),
                        row.get("aggregate_id", UUID.class),
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_version", Long.class),
                        row.get("created_at", Instant.class)))
                .one();
    }

    @Override
    public Mono<Snapshot> loadSnapshotAtVersion(UUID aggregateId, String snapshotType, long version) {
        String sql = """
                SELECT aggregate_id, aggregate_type, aggregate_version, snapshot_data, created_at
                FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                  AND aggregate_version = :version
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .bind("version", version)
                .map((row, metadata) -> deserializeSnapshot(
                        row.get("snapshot_data", String.class),
                        row.get("aggregate_id", UUID.class),
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_version", Long.class),
                        row.get("created_at", Instant.class)))
                .one();
    }

    @Override
    public Mono<Boolean> snapshotExists(UUID aggregateId, String snapshotType) {
        String sql = """
                SELECT COUNT(*) as cnt FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .map(row -> row.get("cnt", Long.class) > 0)
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> getLatestSnapshotVersion(UUID aggregateId, String snapshotType) {
        String sql = """
                SELECT COALESCE(MAX(aggregate_version), 0) as max_version FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .map(row -> row.get("max_version", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Void> deleteSnapshot(UUID aggregateId, String snapshotType, long version) {
        String sql = """
                DELETE FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                  AND aggregate_version = :version
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .bind("version", version)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> deleteAllSnapshots(UUID aggregateId, String snapshotType) {
        String sql = """
                DELETE FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Long> deleteSnapshotsOlderThan(Instant olderThan) {
        String sql = "DELETE FROM snapshots WHERE created_at < :olderThan";

        return databaseClient.sql(sql)
                .bind("olderThan", olderThan)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Mono<Long> keepLatestSnapshots(UUID aggregateId, String snapshotType, int keepCount) {
        String sql = """
                DELETE FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                  AND aggregate_version NOT IN (
                      SELECT aggregate_version FROM snapshots
                      WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                      ORDER BY aggregate_version DESC
                      LIMIT :keepCount
                  )
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .bind("keepCount", keepCount)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Flux<Snapshot> listSnapshots(UUID aggregateId, String snapshotType) {
        String sql = """
                SELECT aggregate_id, aggregate_type, aggregate_version, snapshot_data, created_at
                FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                ORDER BY aggregate_version DESC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .map((row, metadata) -> deserializeSnapshot(
                        row.get("snapshot_data", String.class),
                        row.get("aggregate_id", UUID.class),
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_version", Long.class),
                        row.get("created_at", Instant.class)))
                .all();
    }

    @Override
    public Flux<Snapshot> listSnapshots(UUID aggregateId, String snapshotType, long fromVersion, long toVersion) {
        String sql = """
                SELECT aggregate_id, aggregate_type, aggregate_version, snapshot_data, created_at
                FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                  AND aggregate_version >= :fromVersion AND aggregate_version <= :toVersion
                ORDER BY aggregate_version DESC
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .bind("fromVersion", fromVersion)
                .bind("toVersion", toVersion)
                .map((row, metadata) -> deserializeSnapshot(
                        row.get("snapshot_data", String.class),
                        row.get("aggregate_id", UUID.class),
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_version", Long.class),
                        row.get("created_at", Instant.class)))
                .all();
    }

    @Override
    public Mono<Long> countSnapshots(UUID aggregateId, String snapshotType) {
        String sql = """
                SELECT COUNT(*) as cnt FROM snapshots
                WHERE aggregate_id = :aggregateId AND aggregate_type = :aggregateType
                """;

        return databaseClient.sql(sql)
                .bind("aggregateId", aggregateId)
                .bind("aggregateType", snapshotType)
                .map(row -> row.get("cnt", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<SnapshotStatistics> getStatistics() {
        String sql = """
                SELECT
                    COUNT(*) as total_snapshots,
                    COUNT(DISTINCT aggregate_id) as total_aggregates
                FROM snapshots
                """;

        return databaseClient.sql(sql)
                .map(row -> SnapshotStatistics.builder()
                        .totalSnapshots(row.get("total_snapshots", Long.class))
                        .totalAggregatesWithSnapshots(row.get("total_aggregates", Long.class))
                        .totalStorageSizeBytes(0L)
                        .build())
                .one();
    }

    @Override
    public Mono<Boolean> isHealthy() {
        return databaseClient.sql("SELECT 1 FROM snapshots LIMIT 1")
                .fetch()
                .first()
                .map(row -> true)
                .defaultIfEmpty(true)
                .onErrorReturn(false);
    }

    @Override
    public Mono<Void> optimize() {
        return databaseClient.sql("ANALYZE snapshots")
                .fetch()
                .rowsUpdated()
                .doOnSuccess(v -> log.info("Snapshot table optimized"))
                .then();
    }

    // --- Serialization helpers ---

    private String serializeSnapshot(Snapshot snapshot) throws JsonProcessingException {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("@class", snapshot.getClass().getName());
        wrapper.put("state", objectMapper.writeValueAsString(snapshot));
        return objectMapper.writeValueAsString(wrapper);
    }

    private Snapshot deserializeSnapshot(String snapshotData, UUID aggregateId,
                                          String aggregateType, Long version, Instant createdAt) {
        try {
            JsonNode wrapper = objectMapper.readTree(snapshotData);
            if (wrapper.has("@class") && wrapper.has("state")) {
                String className = wrapper.get("@class").asText();
                Class<?> clazz = Class.forName(className);
                return (Snapshot) objectMapper.readValue(wrapper.get("state").asText(), clazz);
            }
            // Fallback: data stored without wrapper — return a generic snapshot
            return new GenericSnapshot(aggregateId, aggregateType, version, createdAt, snapshotData);
        } catch (Exception e) {
            log.warn("Failed to deserialize snapshot for aggregate {}, returning generic snapshot: {}",
                    aggregateId, e.getMessage());
            return new GenericSnapshot(aggregateId, aggregateType, version, createdAt, snapshotData);
        }
    }

    /**
     * Fallback snapshot when the concrete class cannot be resolved.
     */
    private record GenericSnapshot(
            UUID aggregateId,
            String snapshotType,
            long version,
            Instant createdAt,
            String rawData
    ) implements Snapshot {

        @Override
        public UUID getAggregateId() { return aggregateId; }

        @Override
        public String getSnapshotType() { return snapshotType; }

        @Override
        public long getVersion() { return version; }

        @Override
        public Instant getCreatedAt() { return createdAt; }
    }
}
