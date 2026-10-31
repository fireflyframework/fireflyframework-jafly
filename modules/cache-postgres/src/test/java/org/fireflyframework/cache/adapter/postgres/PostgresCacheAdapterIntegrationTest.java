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

package org.fireflyframework.cache.adapter.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.serialization.CacheSerializer;
import org.fireflyframework.cache.serialization.JsonCacheSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for {@link PostgresCacheAdapter} against a real PostgreSQL
 * instance provisioned via Testcontainers.
 */
@Testcontainers
class PostgresCacheAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cache")
            .withUsername("cache")
            .withPassword("cache");

    private static final String SCHEMA = "public";
    private static final String TABLE = "firefly_cache_entries";

    private static ConnectionPool pool;
    private PostgresCacheAdapter adapter;

    @BeforeAll
    static void startPool() {
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .schema(SCHEMA)
                .build();
        PostgresqlConnectionFactory delegate = new PostgresqlConnectionFactory(config);
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(delegate)
                .maxSize(5)
                .initialSize(1)
                .build();
        pool = new ConnectionPool(poolConfig);
        createTable();
    }

    @AfterAll
    static void closePool() {
        if (pool != null) {
            pool.dispose();
        }
    }

    private static void createTable() {
        String qualified = "\"" + SCHEMA + "\".\"" + TABLE + "\"";
        String createTable = "CREATE TABLE IF NOT EXISTS " + qualified + " ("
                + "cache_name VARCHAR(255) NOT NULL, "
                + "cache_key VARCHAR(1024) NOT NULL, "
                + "value BYTEA NOT NULL, "
                + "expires_at TIMESTAMPTZ, "
                + "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), "
                + "PRIMARY KEY (cache_name, cache_key))";
        String createIndex = "CREATE INDEX IF NOT EXISTS " + TABLE + "_expires_idx "
                + "ON " + qualified + " (expires_at) WHERE expires_at IS NOT NULL";
        Mono.usingWhen(
                        Mono.from(pool.create()),
                        conn -> Mono.from(conn.createStatement(createTable).execute())
                                .flatMap(r -> Mono.from(r.getRowsUpdated())).then()
                                .then(Mono.from(conn.createStatement(createIndex).execute())
                                        .flatMap(r -> Mono.from(r.getRowsUpdated())).then()),
                        Connection::close)
                .block(Duration.ofSeconds(30));
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        CacheSerializer serializer = new JsonCacheSerializer(objectMapper);
        PostgresCacheConfig config = PostgresCacheConfig.builder()
                .cacheName("itest")
                .keyPrefix("firefly:cache")
                .defaultTtl(Duration.ofMinutes(30))
                .schema(SCHEMA)
                .cacheTable(TABLE)
                .build();
        adapter = new PostgresCacheAdapter("itest", pool, config, serializer);
        // Ensure a clean cache for each test.
        adapter.clear().block(Duration.ofSeconds(10));
    }

    @Test
    void putThenGetRoundTrip() {
        StepVerifier.create(adapter.put("user:1", "Alice"))
                .verifyComplete();

        StepVerifier.create(adapter.<String, String>get("user:1", String.class))
                .assertNext(opt -> assertThat(opt).contains("Alice"))
                .verifyComplete();
    }

    @Test
    void getMissReturnsEmpty() {
        StepVerifier.create(adapter.<String, String>get("absent", String.class))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void putRoundTripWithComplexObject() {
        SampleDto dto = new SampleDto("widget", 42);
        StepVerifier.create(adapter.put("dto:1", dto)).verifyComplete();

        StepVerifier.create(adapter.<String, SampleDto>get("dto:1", SampleDto.class))
                .assertNext(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getName()).isEqualTo("widget");
                    assertThat(opt.get().getCount()).isEqualTo(42);
                })
                .verifyComplete();
    }

    @Test
    void ttlExpiryRemovesEntry() {
        adapter.put("ephemeral", "soon-gone", Duration.ofSeconds(1)).block(Duration.ofSeconds(5));

        // Immediately present.
        Optional<String> present = adapter.<String, String>get("ephemeral", String.class)
                .block(Duration.ofSeconds(5));
        assertThat(present).contains("soon-gone");

        // After TTL elapses the entry is filtered out on read.
        await().atMost(5, TimeUnit.SECONDS).pollInterval(250, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            Optional<String> expired = adapter.<String, String>get("ephemeral", String.class)
                    .block(Duration.ofSeconds(5));
            assertThat(expired).isEmpty();
        });
    }

    @Test
    void evictRemovesEntry() {
        adapter.put("toEvict", "value").block(Duration.ofSeconds(5));

        StepVerifier.create(adapter.evict("toEvict"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(adapter.exists("toEvict"))
                .expectNext(false)
                .verifyComplete();

        // Evicting an absent key returns false.
        StepVerifier.create(adapter.evict("toEvict"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void existsReflectsPresence() {
        StepVerifier.create(adapter.exists("k"))
                .expectNext(false)
                .verifyComplete();

        adapter.put("k", "v").block(Duration.ofSeconds(5));

        StepVerifier.create(adapter.exists("k"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void sizeCountsLiveEntries() {
        adapter.put("a", "1").block(Duration.ofSeconds(5));
        adapter.put("b", "2").block(Duration.ofSeconds(5));
        adapter.put("c", "3").block(Duration.ofSeconds(5));

        StepVerifier.create(adapter.size())
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    void clearRemovesAllEntriesForCache() {
        adapter.put("x", "1").block(Duration.ofSeconds(5));
        adapter.put("y", "2").block(Duration.ofSeconds(5));

        StepVerifier.create(adapter.clear()).verifyComplete();

        StepVerifier.create(adapter.size())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void putIfAbsentOnlyStoresWhenMissing() {
        StepVerifier.create(adapter.putIfAbsent("once", "first"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(adapter.putIfAbsent("once", "second"))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(adapter.<String, String>get("once", String.class))
                .assertNext(opt -> assertThat(opt).contains("first"))
                .verifyComplete();
    }

    @Test
    void evictByPrefixDeletesMatchingKeys() {
        adapter.put("session:1", "a").block(Duration.ofSeconds(5));
        adapter.put("session:2", "b").block(Duration.ofSeconds(5));
        adapter.put("other:1", "c").block(Duration.ofSeconds(5));

        StepVerifier.create(adapter.evictByPrefix("session:"))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(adapter.exists("session:1"))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(adapter.exists("other:1"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void keysReturnsUnprefixedKeys() {
        adapter.put("alpha", "1").block(Duration.ofSeconds(5));
        adapter.put("beta", "2").block(Duration.ofSeconds(5));

        Set<String> keys = adapter.<String>keys().block(Duration.ofSeconds(5));
        assertThat(keys).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void getHealthReportsUp() {
        CacheHealth health = adapter.getHealth().block(Duration.ofSeconds(10));
        assertThat(health).isNotNull();
        assertThat(health.getCacheType()).isEqualTo(CacheType.POSTGRES);
        assertThat(health.isAvailable()).isTrue();
        assertThat(health.getStatus()).isEqualTo("UP");
    }

    @Test
    void getCacheTypeIsPostgres() {
        assertThat(adapter.getCacheType()).isEqualTo(CacheType.POSTGRES);
        assertThat(adapter.getCacheName()).isEqualTo("itest");
        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void statsTrackHitsAndMisses() {
        adapter.put("s", "v").block(Duration.ofSeconds(5));
        adapter.<String, String>get("s", String.class).block(Duration.ofSeconds(5));
        adapter.<String, String>get("missing", String.class).block(Duration.ofSeconds(5));

        var stats = adapter.getStats().block(Duration.ofSeconds(5));
        assertThat(stats).isNotNull();
        assertThat(stats.getHitCount()).isGreaterThanOrEqualTo(1L);
        assertThat(stats.getMissCount()).isGreaterThanOrEqualTo(1L);
        assertThat(stats.getCacheType()).isEqualTo(CacheType.POSTGRES);
    }

    private static final Duration PROTOCOL_WAIT = Duration.ofSeconds(10);

    private static PostgresCacheAdapter scoped(String namespace, String logicalName, io.r2dbc.spi.ConnectionFactory factory) {
        return new PostgresCacheAdapter(logicalName, factory, PostgresCacheConfig.builder()
                .keyPrefix(namespace).schema(SCHEMA).cacheTable(TABLE).build(),
                new JsonCacheSerializer(new ObjectMapper()));
    }

    private static <T> Mono<T> technical(java.util.function.Function<Connection, Mono<T>> work) {
        return Mono.usingWhen(Mono.from(pool.create()), work, Connection::close);
    }

    private static String qualified() { return "\"" + SCHEMA + "\".\"" + TABLE + "\""; }

    @Test
    void expiredEntryCanBeReplacedOnceAcrossIndependentInstances() {
        String namespace = "expired-" + java.util.UUID.randomUUID();
        var first = scoped(namespace, "default", pool);
        var second = scoped(namespace, "default", pool);
        first.put("key", "old", Duration.ofMillis(80)).block(PROTOCOL_WAIT);
        await().atMost(PROTOCOL_WAIT).until(() -> !second.exists("key").block(PROTOCOL_WAIT));
        assertThat(second.putIfAbsent("key", "renewed", Duration.ofSeconds(10)).block(PROTOCOL_WAIT)).isTrue();
        assertThat(first.putIfAbsent("key", "loser", Duration.ofSeconds(10)).block(PROTOCOL_WAIT)).isFalse();
        assertThat(first.get("key", String.class).block(PROTOCOL_WAIT)).contains("renewed");
    }

    @Test
    void concurrentExpiredReplacementHasExactlyOneWinner() {
        String namespace = "race-" + java.util.UUID.randomUUID();
        var cache = scoped(namespace, "default", pool);
        cache.put("key", "old", Duration.ofMillis(80)).block(PROTOCOL_WAIT);
        await().atMost(PROTOCOL_WAIT).until(() -> !cache.exists("key").block(PROTOCOL_WAIT));
        var outcomes = reactor.core.publisher.Flux.range(0, 12)
                .flatMap(index -> scoped(namespace, "default", pool)
                        .putIfAbsent("key", "candidate-" + index, Duration.ofSeconds(10))
                        .map(won -> java.util.Map.entry("candidate-" + index, won)), 12)
                .collectList().block(PROTOCOL_WAIT);
        assertThat(outcomes).hasSize(12);
        var winners = outcomes.stream().filter(java.util.Map.Entry::getValue).map(java.util.Map.Entry::getKey).toList();
        assertThat(winners).hasSize(1);
        assertThat(cache.get("key", String.class).block(PROTOCOL_WAIT)).contains(winners.getFirst());
    }

    @Test
    void nullZeroAndNegativeTtlKeepPermanentEntriesAndCannotReplaceThem() {
        String namespace = "permanent-" + java.util.UUID.randomUUID();
        var cache = scoped(namespace, "default", pool);
        Duration[] durations = {null, Duration.ZERO, Duration.ofSeconds(-1)};
        for (int index = 0; index < durations.length; index++) {
            String key = "key-" + index;
            assertThat(cache.putIfAbsent(key, "permanent", durations[index]).block(PROTOCOL_WAIT)).isTrue();
            assertThat(cache.putIfAbsent(key, "replacement", Duration.ofSeconds(10)).block(PROTOCOL_WAIT)).isFalse();
            assertThat(cache.get(key, String.class).block(PROTOCOL_WAIT)).contains("permanent");
        }
        Long permanent = technical(connection -> Mono.from(connection.createStatement(
                        "SELECT count(*) AS n FROM " + qualified() + " WHERE cache_key LIKE $1 AND expires_at IS NULL")
                .bind("$1", namespace + ":default:%").execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("n", Long.class)))))
                .block(PROTOCOL_WAIT);
        assertThat(permanent).isEqualTo(3L);
    }

    @Test
    void literalPrefixCannotDeleteWildcardLookalikes() {
        var cache = scoped("literal-" + java.util.UUID.randomUUID(), "default", pool);
        cache.put("group%_\\part:one", "target").block(PROTOCOL_WAIT);
        cache.put("groupABpart:one", "lookalike").block(PROTOCOL_WAIT);
        cache.put("group%X\\part:one", "underscore-lookalike").block(PROTOCOL_WAIT);
        assertThat(cache.evictByPrefix("group%_\\part:").block(PROTOCOL_WAIT)).isEqualTo(1L);
        assertThat(cache.exists("group%_\\part:one").block(PROTOCOL_WAIT)).isFalse();
        assertThat(cache.get("groupABpart:one", String.class).block(PROTOCOL_WAIT)).contains("lookalike");
        assertThat(cache.get("group%X\\part:one", String.class).block(PROTOCOL_WAIT)).contains("underscore-lookalike");
    }

    @Test
    void nestedNamespacesKeepClearKeysSizeAndLogicalNamesSeparate() {
        String namespace = "nested-" + java.util.UUID.randomUUID();
        var first = scoped(namespace, "default", pool);
        var second = scoped(namespace + ":default", "default", pool);
        first.put("shared", "first").block(PROTOCOL_WAIT);
        second.put("shared", "second").block(PROTOCOL_WAIT);
        second.put("extra", "extra").block(PROTOCOL_WAIT);
        assertThat(first.<String>keys().block(PROTOCOL_WAIT)).containsExactly("shared");
        assertThat(first.size().block(PROTOCOL_WAIT)).isEqualTo(1L);
        assertThat(second.size().block(PROTOCOL_WAIT)).isEqualTo(2L);
        assertThat(first.getCacheName()).isEqualTo("default");
        first.clear().block(PROTOCOL_WAIT);
        assertThat(first.size().block(PROTOCOL_WAIT)).isZero();
        assertThat(second.<String>keys().block(PROTOCOL_WAIT)).containsExactlyInAnyOrder("shared", "extra");
        assertThat(second.get("shared", String.class).block(PROTOCOL_WAIT)).contains("second");
    }

    @Test
    void legacyPhysicalRowsRemainUntouchedAndCold() {
        String namespace = "legacy-" + java.util.UUID.randomUUID();
        String fullKey = namespace + ":legacy:old";
        String versionOne = "v1:" + "a".repeat(64);
        for (String legacyName : java.util.List.of("legacy", versionOne)) {
            technical(connection -> Mono.from(connection.createStatement("INSERT INTO " + qualified()
                            + " (cache_name,cache_key,value) VALUES ($1,$2,$3)")
                    .bind("$1", legacyName).bind("$2", fullKey).bind("$3", new byte[] {1}).execute())
                    .flatMap(result -> Mono.from(result.getRowsUpdated()))).block(PROTOCOL_WAIT);
        }
        var cache = scoped(namespace, "legacy", pool);
        assertThat(cache.get("old", String.class).block(PROTOCOL_WAIT)).isEmpty();
        cache.put("new", "new").block(PROTOCOL_WAIT);
        cache.clear().block(PROTOCOL_WAIT);
        Long retained = technical(connection -> Mono.from(connection.createStatement("SELECT count(*) AS n FROM "
                        + qualified() + " WHERE cache_name IN ($1,$2) AND cache_key=$3")
                .bind("$1", "legacy").bind("$2", versionOne).bind("$3", fullKey).execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("n", Long.class)))))
                .block(PROTOCOL_WAIT);
        assertThat(retained).isEqualTo(2L);
    }

    @Test
    void applicationNameDefaultsSeparateActualPostgresCacheManagers() {
        String suffix = java.util.UUID.randomUUID().toString();
        var external = new io.r2dbc.spi.ConnectionFactory() {
            public org.reactivestreams.Publisher<? extends Connection> create() { return pool.create(); }
            public io.r2dbc.spi.ConnectionFactoryMetadata getMetadata() { return pool.getMetadata(); }
        };
        var contexts = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.fireflyframework.cache.config.CacheAutoConfiguration.class,
                        org.fireflyframework.cache.config.PostgresCacheAutoConfiguration.class))
                .withBean("fireflyCachePostgresConnectionFactory", io.r2dbc.spi.ConnectionFactory.class, () -> external)
                .withPropertyValues("firefly.cache.postgres.schema=" + SCHEMA);
        contexts.withPropertyValues("spring.application.name=service-a-" + suffix).run(firstContext -> {
            assertThat(firstContext).hasNotFailed();
            var first = firstContext.getBean(org.fireflyframework.cache.manager.FireflyCacheManager.class);
            first.put("shared", "first").block(PROTOCOL_WAIT);
            contexts.withPropertyValues("spring.application.name=service-b-" + suffix).run(secondContext -> {
                assertThat(secondContext).hasNotFailed();
                var second = secondContext.getBean(org.fireflyframework.cache.manager.FireflyCacheManager.class);
                assertThat(second.get("shared", String.class).block(PROTOCOL_WAIT)).isEmpty();
                second.put("shared", "second").block(PROTOCOL_WAIT);
                first.clear().block(PROTOCOL_WAIT);
                assertThat(second.get("shared", String.class).block(PROTOCOL_WAIT)).contains("second");
            });
        });
        assertThat(pool.isDisposed()).isFalse();
    }

    @Test
    void fixedHelperFeaturePrefixCannotJoinTwoServiceCaches() {
        String suffix = java.util.UUID.randomUUID().toString();
        var firstProperties = new org.fireflyframework.cache.properties.CacheProperties();
        firstProperties.getPostgres().setSchema(SCHEMA);
        firstProperties.getPostgres().setKeyPrefix("helper-a-" + suffix);
        var secondProperties = new org.fireflyframework.cache.properties.CacheProperties();
        secondProperties.getPostgres().setSchema(SCHEMA);
        secondProperties.getPostgres().setKeyPrefix("helper-b-" + suffix);
        var first = org.fireflyframework.cache.factory.PostgresCacheHelper.createPostgresCacheAdapter(
                "shared-logical", "fixed-feature", Duration.ofMinutes(1), pool, firstProperties, new ObjectMapper());
        var second = org.fireflyframework.cache.factory.PostgresCacheHelper.createPostgresCacheAdapter(
                "shared-logical", "fixed-feature", Duration.ofMinutes(1), pool, secondProperties, new ObjectMapper());
        first.put("same-key", "first").block(PROTOCOL_WAIT);
        second.put("same-key", "second").block(PROTOCOL_WAIT);
        assertThat(first.get("same-key", String.class).block(PROTOCOL_WAIT)).contains("first");
        assertThat(second.get("same-key", String.class).block(PROTOCOL_WAIT)).contains("second");
        first.clear().block(PROTOCOL_WAIT);
        assertThat(second.get("same-key", String.class).block(PROTOCOL_WAIT)).contains("second");
        assertThat(first.size().block(PROTOCOL_WAIT)).isZero();
        assertThat(second.size().block(PROTOCOL_WAIT)).isEqualTo(1L);
    }

    @Test
    void actualOwnedPoolsShareSameServiceEntriesExpireThemAndCloseWithTheirContexts() {
        String service = "owned-service-" + java.util.UUID.randomUUID();
        var firstPool = new java.util.concurrent.atomic.AtomicReference<ConnectionPool>();
        var secondPool = new java.util.concurrent.atomic.AtomicReference<ConnectionPool>();
        var contexts = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.fireflyframework.cache.config.CacheAutoConfiguration.class,
                        org.fireflyframework.cache.config.PostgresCacheAutoConfiguration.class))
                .withPropertyValues("spring.application.name=" + service,
                        "firefly.cache.postgres.host=" + POSTGRES.getHost(),
                        "firefly.cache.postgres.port=" + POSTGRES.getMappedPort(5432),
                        "firefly.cache.postgres.database=" + POSTGRES.getDatabaseName(),
                        "firefly.cache.postgres.username=" + POSTGRES.getUsername(),
                        "firefly.cache.postgres.password=" + POSTGRES.getPassword(),
                        "firefly.cache.postgres.schema=" + SCHEMA,
                        "firefly.cache.postgres.auto-create-schema=false",
                        "firefly.cache.postgres.min-pool-size=0", "firefly.cache.postgres.max-pool-size=2",
                        "firefly.cache.postgres.connect-timeout=PT2S", "firefly.cache.postgres.acquire-timeout=PT2S",
                        "firefly.cache.postgres.initialization-timeout=PT5S", "firefly.cache.postgres.shutdown-timeout=PT2S");
        contexts.run(firstContext -> {
            assertThat(firstContext).hasNotFailed();
            firstPool.set(firstContext.getBean("fireflyCachePostgresConnectionFactory", ConnectionPool.class));
            var first = firstContext.getBean(org.fireflyframework.cache.manager.FireflyCacheManager.class);
            contexts.run(secondContext -> {
                assertThat(secondContext).hasNotFailed();
                secondPool.set(secondContext.getBean("fireflyCachePostgresConnectionFactory", ConnectionPool.class));
                assertThat(firstPool.get()).isNotSameAs(secondPool.get());
                var second = secondContext.getBean(org.fireflyframework.cache.manager.FireflyCacheManager.class);
                first.put("shared", "cross-instance", Duration.ofSeconds(1)).block(PROTOCOL_WAIT);
                assertThat(second.get("shared", String.class).block(PROTOCOL_WAIT)).contains("cross-instance");
                await().atMost(PROTOCOL_WAIT).until(() -> second.get("shared", String.class).block(PROTOCOL_WAIT).isEmpty());
                assertThat(first.get("shared", String.class).block(PROTOCOL_WAIT)).isEmpty();
            });
            assertThat(secondPool.get().isDisposed()).isTrue();
            assertThat(firstPool.get().isDisposed()).isFalse();
        });
        assertThat(firstPool.get().isDisposed()).isTrue();
        assertThat(secondPool.get().isDisposed()).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void conflictWaitRechecksExpiryAndRenewsTtlUsingPostLockDatabaseClock(boolean conditional) {
        String namespace = "lock-" + java.util.UUID.randomUUID();
        String fullKey = namespace + ":default:key";
        var cache = scoped(namespace, "default", pool);
        cache.put("key", "permanent", Duration.ZERO).block(PROTOCOL_WAIT);
        Connection blocker = Mono.from(pool.create()).block(PROTOCOL_WAIT);
        java.util.concurrent.CompletableFuture<Boolean> contender = null;
        boolean committed = false;
        try {
            Mono.from(blocker.beginTransaction()).block(PROTOCOL_WAIT);
            java.time.OffsetDateTime expires = Mono.from(blocker.createStatement("UPDATE " + qualified()
                            + " SET expires_at=pg_catalog.clock_timestamp()+INTERVAL '500 milliseconds' WHERE cache_key=$1 RETURNING expires_at")
                    .bind("$1", fullKey).execute()).flatMap(result -> Mono.from(result.map((row, metadata) ->
                            row.get("expires_at", java.time.OffsetDateTime.class)))).block(PROTOCOL_WAIT);
            var contenderPid = new java.util.concurrent.atomic.AtomicInteger();
            var observedFactory = new io.r2dbc.spi.ConnectionFactory() {
                public org.reactivestreams.Publisher<? extends Connection> create() {
                    return Mono.from(pool.create()).flatMap(connection -> Mono.from(connection.createStatement(
                                    "SELECT pg_backend_pid() AS pid").execute())
                            .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("pid", Integer.class))))
                            .doOnNext(contenderPid::set).thenReturn(connection)
                            .onErrorResume(error -> Mono.from(connection.close()).then(Mono.error(error))));
                }
                public io.r2dbc.spi.ConnectionFactoryMetadata getMetadata() { return pool.getMetadata(); }
            };
            var writer = scoped(namespace, "default", observedFactory);
            contender = (conditional
                    ? writer.putIfAbsent("key", "renewed-after-wait", Duration.ofSeconds(2))
                    : writer.put("key", "renewed-after-wait", Duration.ofSeconds(2)).thenReturn(true)).toFuture();
            await().atMost(PROTOCOL_WAIT).until(() -> contenderPid.get() > 0);
            await().atMost(PROTOCOL_WAIT).until(() -> Boolean.TRUE.equals(technical(connection -> Mono.from(
                            connection.createStatement("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_stat_activity WHERE pid=$1 AND wait_event_type='Lock') AS blocked")
                                    .bind("$1", contenderPid.get()).execute())
                    .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("blocked", Boolean.class)))))
                    .block(PROTOCOL_WAIT)));
            await().atMost(PROTOCOL_WAIT).until(() -> databaseClock().isAfter(expires.plusSeconds(3)));
            java.time.OffsetDateTime releasedAt = databaseClock();
            Mono.from(blocker.commitTransaction()).block(PROTOCOL_WAIT);
            committed = true;
            assertThat(Mono.fromFuture(contender).block(PROTOCOL_WAIT)).isTrue();
            java.time.OffsetDateTime renewedExpiry = technical(connection -> Mono.from(connection.createStatement(
                            "SELECT expires_at FROM " + qualified() + " WHERE cache_key=$1").bind("$1", fullKey).execute())
                    .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("expires_at", java.time.OffsetDateTime.class)))))
                    .block(PROTOCOL_WAIT);
            assertThat(renewedExpiry).isAfter(releasedAt.plus(Duration.ofMillis(1800)));
            assertThat(cache.get("key", String.class).block(PROTOCOL_WAIT)).contains("renewed-after-wait");
        } finally {
            if (contender != null && !contender.isDone()) contender.cancel(true);
            try {
                if (!committed) Mono.from(blocker.rollbackTransaction()).block(PROTOCOL_WAIT);
            } finally {
                Mono.from(blocker.close()).block(PROTOCOL_WAIT);
            }
        }
    }

    private static java.time.OffsetDateTime databaseClock() {
        return technical(connection -> Mono.from(connection.createStatement("SELECT pg_catalog.clock_timestamp() AS at").execute())
                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("at", java.time.OffsetDateTime.class)))))
                .block(PROTOCOL_WAIT);
    }

    /**
     * Simple serializable DTO used to verify JSON round-trips through BYTEA.
     */
    public static class SampleDto {
        private String name;
        private int count;

        public SampleDto() {
        }

        public SampleDto(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
