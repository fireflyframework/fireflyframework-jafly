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

import org.fireflyframework.kernel.logging.LogFields;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.ValidationDepth;
import org.fireflyframework.cache.core.CacheAdapter;
import org.fireflyframework.cache.core.CacheOperationPolicy;
import org.fireflyframework.cache.exception.CacheException;
import org.fireflyframework.cache.core.CacheHealth;
import org.fireflyframework.cache.core.CacheStats;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.serialization.CacheSerializer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PostgreSQL-backed reactive cache adapter implemented over R2DBC.
 * <p>
 * Cache entries are stored in a single relational table keyed by
 * {@code (cache_name, cache_key)}. The physical cache name includes an exact
 * service namespace while public metadata retains the logical cache name. Values are serialized with a
 * {@link CacheSerializer} into a JSON {@link String}, encoded as UTF-8 bytes and
 * persisted in a {@code BYTEA} column. Expiry is enforced both lazily on read
 * (rows whose {@code expires_at} is in the past are treated as absent) and at
 * write time using the database clock. Conditional expired-row replacements
 * evaluate expiry and renewed TTL after acquiring the conflicting row lock.
 * <p>
 * All operations run reactively against a Spring-managed
 * {@link ConnectionFactory}; connections are acquired and released per operation
 * via {@link Mono#usingWhen(Publisher, java.util.function.Function, java.util.function.Function)}.
 */
public class PostgresCacheAdapterImpl implements CacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(PostgresCacheAdapterImpl.class);

    private final String cacheName;
    private final String keyPrefix;
    private final String storageCacheName;
    private final ConnectionFactory connectionFactory;
    private final PostgresCacheConfig config;
    private final CacheSerializer serializer;
    private final String qualifiedTable;
    private final CacheOperationPolicy operationPolicy;

    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong putCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong totalLoadTime = new AtomicLong();

    /** Retains direct callers whose explicit prefix supplies both service and feature identity. */
    public PostgresCacheAdapterImpl(String cacheName,
                                ConnectionFactory connectionFactory,
                                PostgresCacheConfig config,
                                CacheSerializer serializer) {
        this(cacheName, connectionFactory, config, serializer, config.getKeyPrefix());
    }

    /** Keeps a factory's service identity independent of a caller's feature prefix. */
    public PostgresCacheAdapterImpl(String cacheName,
                                ConnectionFactory connectionFactory,
                                PostgresCacheConfig config,
                                CacheSerializer serializer,
                                String serviceNamespace) {
        this(cacheName, connectionFactory, config, serializer, serviceNamespace, CacheOperationPolicy.LEGACY);
    }

    public PostgresCacheAdapterImpl(String cacheName,
                                ConnectionFactory connectionFactory,
                                PostgresCacheConfig config,
                                CacheSerializer serializer,
                                String serviceNamespace,
                                CacheOperationPolicy policy) {
        this.operationPolicy = java.util.Objects.requireNonNull(policy, "Cache operation policy required");
        config.validate(cacheName, serviceNamespace);
        String featurePrefix = config.effectiveKeyPrefix(serviceNamespace);
        this.cacheName = cacheName;
        this.storageCacheName = physicalCacheName(serviceNamespace, cacheName, featurePrefix);
        this.connectionFactory = connectionFactory;
        this.config = config;
        this.serializer = serializer;
        this.keyPrefix = buildKeyPrefix(cacheName, featurePrefix);
        this.qualifiedTable = config.qualifiedTable();
    }

    private static String physicalCacheName(String namespace, String logicalName, String featurePrefix) {
        byte[] namespaceBytes = namespace.getBytes(StandardCharsets.UTF_8);
        byte[] nameBytes = logicalName.getBytes(StandardCharsets.UTF_8);
        byte[] featureBytes = featurePrefix.getBytes(StandardCharsets.UTF_8);
        byte[] input = java.nio.ByteBuffer.allocate(16 + namespaceBytes.length + nameBytes.length + featureBytes.length)
                .order(java.nio.ByteOrder.BIG_ENDIAN).putInt(2)
                .putInt(namespaceBytes.length).put(namespaceBytes).putInt(nameBytes.length).put(nameBytes)
                .putInt(featureBytes.length).put(featureBytes).array();
        try {
            return "v2:" + java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(input));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static String literalLikePrefix(String prefix) {
        return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String buildKeyPrefix(String cacheName, String configPrefix) {
        if (configPrefix != null && !configPrefix.trim().isEmpty()) {
            return configPrefix + ":" + cacheName + ":";
        }
        return "cache:" + cacheName + ":";
    }

    private String buildKey(Object key) {
        return keyPrefix + key.toString();
    }

    private String extractOriginalKey(String cacheKey) {
        if (cacheKey.startsWith(keyPrefix)) {
            return cacheKey.substring(keyPrefix.length());
        }
        return cacheKey;
    }

    /**
     * Serializes a value to UTF-8 JSON bytes for storage in the {@code BYTEA} column.
     */
    private byte[] toBytes(Object value) {
        Object serialized = serializer.serialize(value);
        if (primaryOnly() && serialized == null) throw new CacheException("Cache serialization returned no value");
        if (serialized instanceof byte[] bytes) {
            return bytes;
        }
        if (serialized instanceof String str) {
            return str.getBytes(StandardCharsets.UTF_8);
        }
        // Primitives/Numbers are returned as-is by JsonCacheSerializer; stringify them.
        if (primaryOnly() && !(serialized instanceof Number || serialized instanceof Boolean || serialized instanceof Character)) {
            throw new CacheException("Cache serialization returned an unsupported representation");
        }
        return String.valueOf(serialized).getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private <V> V fromBytes(byte[] bytes, Class<V> valueType) {
        String json = new String(bytes, StandardCharsets.UTF_8);
        return serializer.deserialize(json, valueType);
    }

    private byte[] readBytes(Readable row, String column) {
        Object raw = row.get(column);
        if (raw == null) {
            if (primaryOnly()) throw new CacheException("Stored cache bytes are missing");
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        if (raw instanceof java.nio.ByteBuffer buf) {
            byte[] out = new byte[buf.remaining()];
            (primaryOnly() ? buf.duplicate() : buf).get(out);
            return out;
        }
        if (primaryOnly()) throw new CacheException("Unsupported stored cache byte representation");
        return raw.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Runs the supplied work against a freshly acquired connection, ensuring the
     * connection is released afterward.
     */
    private <T> Mono<T> withConnection(java.util.function.Function<Connection, Mono<T>> work) {
        Mono<Connection> acquired = Mono.from(connectionFactory.create());
        if (primaryOnly()) acquired = acquired.switchIfEmpty(Mono.error(new CacheException("Cache connection acquisition completed empty")));
        return Mono.usingWhen(
                acquired,
                work::apply,
                Connection::close,
                (conn, err) -> Mono.from(conn.close()).then(Mono.error(err)),
                Connection::close);
    }

    @Override
    public <K, V> Mono<Optional<V>> get(K key) {
        return get(key, (Class<V>) (Class<?>) Object.class);
    }

    @Override
    public <K, V> Mono<Optional<V>> get(K key, Class<V> valueType) {
        return operation(() -> {
            String fullKey = buildKey(key);
            long start = System.nanoTime();
            requestCount.incrementAndGet();
            String sql = "SELECT value FROM " + qualifiedTable
                    + " WHERE cache_name = $1 AND cache_key = $2"
                    + " AND (expires_at IS NULL OR expires_at > NOW())";
            return withConnection(conn -> {
                Statement st = conn.createStatement(sql)
                        .bind("$1", storageCacheName)
                        .bind("$2", fullKey);
                return results(st)
                        .flatMap(result -> result.map((row, meta) -> readBytes(row, "value")))
                        .next();
            }).map(bytes -> {
                try {
                    V value = fromBytes(bytes, valueType);
                    if (primaryOnly() && (value == null || !valueType.isInstance(value))) {
                        throw new CacheException("Stored cache value does not match the requested type");
                    }
                    hitCount.incrementAndGet();
                    totalLoadTime.addAndGet(System.nanoTime() - start);
                    return Optional.ofNullable(value);
                } catch (Exception e) {
                    if (primaryOnly()) throw new CacheException("Cannot decode cached value", e);
                    log.warn("Failed to deserialize cached value for key present={} in cache '{}': type={}",
                            key != null, LogFields.text(cacheName), LogFields.errorType(e));
                    missCount.incrementAndGet();
                    return Optional.<V>empty();
                }
            }).switchIfEmpty(Mono.fromSupplier(() -> {
                missCount.incrementAndGet();
                return Optional.<V>empty();
            })).onErrorResume(e -> {
                if (primaryOnly()) return Mono.error(e);
                log.warn("Error reading key present={} from cache '{}': type={}", key != null, LogFields.text(cacheName), LogFields.errorType(e));
                missCount.incrementAndGet();
                return Mono.just(Optional.<V>empty());
            });
        });
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value) {
        return put(key, value, config.getDefaultTtl());
    }

    @Override
    public <K, V> Mono<Void> put(K key, V value, Duration ttl) {
        return operation(() -> {
            if (primaryOnly() && value == null) return Mono.error(new CacheException("Non-null cache value required"));
            String fullKey = buildKey(key);
            return Mono.fromCallable(() -> toBytes(value))
                    .flatMap(bytes -> {
                        boolean hasTtl = ttl != null && !ttl.isZero() && !ttl.isNegative();
                        return withConnection(conn -> {
                            Statement st;
                            if (hasTtl) {
                                String insertSql = "INSERT INTO " + qualifiedTable
                                        + " (cache_name, cache_key, value, expires_at, created_at)"
                                        + " VALUES ($1, $2, $3, pg_catalog.clock_timestamp() + ($4 || ' milliseconds')::interval, pg_catalog.clock_timestamp())"
                                        + " ON CONFLICT (cache_name, cache_key) DO UPDATE"
                                        + " SET value = EXCLUDED.value, expires_at = pg_catalog.clock_timestamp() + ($4 || ' milliseconds')::interval, created_at = pg_catalog.clock_timestamp()";
                                st = conn.createStatement(insertSql)
                                        .bind("$1", storageCacheName)
                                        .bind("$2", fullKey)
                                        .bind("$3", bytes)
                                        .bind("$4", String.valueOf(ttl.toMillis()));
                            } else {
                                String insertSql = "INSERT INTO " + qualifiedTable
                                        + " (cache_name, cache_key, value, expires_at, created_at)"
                                        + " VALUES ($1, $2, $3, NULL, pg_catalog.clock_timestamp())"
                                        + " ON CONFLICT (cache_name, cache_key) DO UPDATE"
                                        + " SET value = EXCLUDED.value, expires_at = NULL, created_at = pg_catalog.clock_timestamp()";
                                st = conn.createStatement(insertSql)
                                        .bind("$1", storageCacheName)
                                        .bind("$2", fullKey)
                                        .bind("$3", bytes);
                            }
                            return rowsUpdated(st);
                        }).doOnNext(count -> {
                            if (count != 1) throw new IllegalStateException("PostgreSQL cache put requires exactly one affected row");
                            putCount.incrementAndGet();
                        }).then();
                    });
        });
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value) {
        return putIfAbsent(key, value, config.getDefaultTtl());
    }

    @Override
    public <K, V> Mono<Boolean> putIfAbsent(K key, V value, Duration ttl) {
        return operation(() -> {
            if (primaryOnly() && value == null) return Mono.error(new CacheException("Non-null cache value required"));
            String fullKey = buildKey(key);
            return Mono.fromCallable(() -> toBytes(value))
                    .flatMap(bytes -> {
                        boolean hasTtl = ttl != null && !ttl.isZero() && !ttl.isNegative();
                        String clock = "pg_catalog.clock_timestamp()";
                        String expires = hasTtl ? clock + " + ($4 || ' milliseconds')::interval" : "NULL";
                        String sql = "INSERT INTO " + qualifiedTable + " AS cache_entry"
                                + " (cache_name, cache_key, value, expires_at, created_at)"
                                + " VALUES ($1, $2, $3, " + expires + ", " + clock + ")"
                                + " ON CONFLICT (cache_name, cache_key) DO UPDATE"
                                + " SET value = EXCLUDED.value, expires_at = " + expires + ", created_at = " + clock
                                + " WHERE cache_entry.expires_at IS NOT NULL"
                                + " AND cache_entry.expires_at <= " + clock;
                        return withConnection(conn -> {
                            Statement st = conn.createStatement(sql)
                                    .bind("$1", storageCacheName)
                                    .bind("$2", fullKey)
                                    .bind("$3", bytes);
                            if (hasTtl) {
                                st = st.bind("$4", String.valueOf(ttl.toMillis()));
                            }
                            return rowsUpdated(st);
                        }).map(n -> {
                            if (n != 0 && n != 1) {
                                throw new IllegalStateException("PostgreSQL cache insertion requires zero or one affected row");
                            }
                            if (n == 1) putCount.incrementAndGet();
                            return n == 1;
                        });
                    });
        });
    }

    @Override
    public <K> Mono<Boolean> evict(K key) {
        return operation(() -> {
            String fullKey = buildKey(key);
            String sql = "DELETE FROM " + qualifiedTable + " WHERE cache_name = $1 AND cache_key = $2";
            return withConnection(conn -> rowsUpdated(conn.createStatement(sql)
                    .bind("$1", storageCacheName)
                    .bind("$2", fullKey)))
                    .map(n -> {
                        if (primaryOnly() && n != 0 && n != 1) throw new CacheException("Cache eviction returned an invalid count");
                        boolean removed = n > 0;
                        if (removed) {
                            evictionCount.incrementAndGet();
                        }
                        return removed;
                    })
                    .onErrorResume(e -> {
                        if (primaryOnly()) return Mono.error(e);
                        log.warn("Error evicting key present={} from cache '{}': type={}", key != null, LogFields.text(cacheName), LogFields.errorType(e));
                        return Mono.just(false);
                    });
        });
    }

    @Override
    public Mono<Void> clear() {
        return operation(() -> {
            String sql = "DELETE FROM " + qualifiedTable + " WHERE cache_name = $1";
            return withConnection(conn -> rowsUpdated(conn.createStatement(sql).bind("$1", storageCacheName)))
                    .doOnNext(evictionCount::addAndGet)
                    .then();
        });
    }

    @Override
    public Mono<Long> evictByPrefix(String keyPrefixToMatch) {
        return operation(() -> {
            String fullPrefix = literalLikePrefix(buildKey(keyPrefixToMatch));
            String sql = "DELETE FROM " + qualifiedTable
                    + " WHERE cache_name = $1 AND cache_key LIKE $2 || '%' ESCAPE E'\\\\'";
            return withConnection(conn -> rowsUpdated(conn.createStatement(sql)
                    .bind("$1", storageCacheName)
                    .bind("$2", fullPrefix)))
                    .doOnNext(evictionCount::addAndGet);
        });
    }

    @Override
    public <K> Mono<Boolean> exists(K key) {
        return operation(() -> {
            String fullKey = buildKey(key);
            String sql = "SELECT 1 FROM " + qualifiedTable
                    + " WHERE cache_name = $1 AND cache_key = $2"
                    + " AND (expires_at IS NULL OR expires_at > NOW())";
            return withConnection(conn -> results(conn.createStatement(sql)
                    .bind("$1", storageCacheName)
                    .bind("$2", fullKey))
                    .flatMap(result -> result.map((row, meta) -> Boolean.TRUE))
                    .next())
                    .defaultIfEmpty(false)
                    .onErrorResume(e -> {
                        if (primaryOnly()) return Mono.error(e);
                        log.warn("Error checking existence of key present={} in cache '{}': type={}",
                                key != null, LogFields.text(cacheName), LogFields.errorType(e));
                        return Mono.just(false);
                    });
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K> Mono<Set<K>> keys() {
        return operation(() -> {
            String sql = "SELECT cache_key FROM " + qualifiedTable
                    + " WHERE cache_name = $1 AND (expires_at IS NULL OR expires_at > NOW())";
            return withConnection(conn -> results(conn.createStatement(sql)
                    .bind("$1", storageCacheName))
                    .flatMap(result -> result.map((row, meta) -> (String) row.get("cache_key")))
                    .map(this::extractOriginalKey)
                    .map(k -> (K) k)
                    .collect(HashSet::new, Set::add))
                    .map(set -> (Set<K>) set);
        });
    }

    @Override
    public Mono<Long> size() {
        return operation(() -> {
            String sql = "SELECT COUNT(*) AS cnt FROM " + qualifiedTable
                    + " WHERE cache_name = $1 AND (expires_at IS NULL OR expires_at > NOW())";
            return withConnection(conn -> results(conn.createStatement(sql)
                    .bind("$1", storageCacheName))
                    .flatMap(result -> result.map((row, meta) -> {
                        Number n = (Number) row.get("cnt");
                        if (primaryOnly() && (n == null || n.longValue() < 0)) throw new CacheException("Cache size returned an invalid count");
                        return n == null ? 0L : n.longValue();
                    }))
                    .next())
                    .switchIfEmpty(primaryOnly() ? Mono.error(new CacheException("Cache size completed without a result")) : Mono.empty())
                    .defaultIfEmpty(0L);
        });
    }

    @Override
    public Mono<CacheStats> getStats() {
        return operation(() -> {
            Mono<CacheStats> stats = size().map(entryCount -> CacheStats.builder()
                            .requestCount(requestCount.get())
                            .hitCount(hitCount.get())
                            .missCount(missCount.get())
                            .loadCount(putCount.get())
                            .evictionCount(evictionCount.get())
                            .entryCount(entryCount)
                            .averageLoadTime(hitCount.get() > 0 ? (double) totalLoadTime.get() / hitCount.get() : 0.0)
                            .estimatedSize(0)
                            .capturedAt(Instant.now())
                            .cacheType(CacheType.POSTGRES)
                            .cacheName(cacheName)
                            .build());
            return primaryOnly() ? stats : stats.onErrorReturn(CacheStats.empty(CacheType.POSTGRES, cacheName));
        });
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.POSTGRES;
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public boolean isAvailable() {
        return connectionFactory != null;
    }

    @Override
    public Mono<CacheHealth> getHealth() {
        long start = System.currentTimeMillis();
        return withConnection(conn -> Mono.from(conn.validate(ValidationDepth.REMOTE)))
                .map(valid -> {
                    long responseTime = System.currentTimeMillis() - start;
                    if (Boolean.TRUE.equals(valid)) {
                        return CacheHealth.builder()
                                .status("UP")
                                .cacheType(CacheType.POSTGRES)
                                .cacheName(cacheName)
                                .available(true)
                                .configured(true)
                                .responseTimeMs(responseTime)
                                .lastSuccessfulOperation(Instant.now())
                                .details(Map.of(
                                        "schema", config.getSchema(),
                                        "table", config.getCacheTable(),
                                        "key_prefix", keyPrefix))
                                .build();
                    }
                    return CacheHealth.unhealthy(CacheType.POSTGRES, cacheName,
                            "Connection validation returned false", null);
                })
                .onErrorResume(e -> Mono.just(CacheHealth.unhealthy(CacheType.POSTGRES, cacheName,
                        "Health check failed: " + e.getMessage(), e)));
    }

    @Override
    public void close() {
        // The ConnectionFactory/ConnectionPool is Spring-managed (disposed via the
        // bean destroy method); nothing to close here.
        log.info("Closing PostgreSQL cache adapter '{}'", LogFields.text(cacheName));
    }

    @Override
    public CacheOperationPolicy operationPolicy() {
        return operationPolicy;
    }

    private boolean primaryOnly() {
        return operationPolicy == CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS;
    }

    private <T> Mono<T> operation(java.util.function.Supplier<Mono<T>> work) {
        if (!primaryOnly()) return work.get();
        return Mono.defer(() -> {
            Mono<T> result = work.get();
            return result == null ? Mono.error(new CacheException("Cache operation returned no publisher")) : result;
        });
    }

    private Flux<Result> results(Statement statement) {
        Flux<Result> results = Flux.from(statement.execute());
        return primaryOnly() ? results.single().flux() : results;
    }

    private Mono<Long> rowsUpdated(Statement statement) {
        if (primaryOnly()) {
            return results(statement).single()
                    .flatMap(result -> Flux.from(result.getRowsUpdated()).single())
                    .map(count -> {
                        if (count < 0) throw new CacheException("Cache update returned an invalid count");
                        return count.longValue();
                    });
        }
        return Flux.from(statement.execute())
                .flatMap(result -> Mono.from(result.getRowsUpdated()))
                .map(Number::longValue)
                .reduce(0L, Long::sum);
    }
}
