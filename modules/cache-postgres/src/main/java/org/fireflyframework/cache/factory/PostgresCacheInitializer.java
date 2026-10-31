package org.fireflyframework.cache.factory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import org.fireflyframework.cache.adapter.postgres.PostgresCacheConfig;
import org.fireflyframework.cache.properties.CacheProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Performs selected-provider startup checks against technical cache storage. */
final class PostgresCacheInitializer {
    private PostgresCacheInitializer() { }

    static void initialize(ConnectionFactory factory, CacheProperties.PostgresConfig properties,
                           PostgresCacheConfig config) {
        requirePositive(properties.getAcquireTimeout());
        requirePositive(properties.getInitializationTimeout());
        requirePositive(properties.getShutdownTimeout());
        String table = config.qualifiedTable();
        try {
            Mono.usingWhen(Mono.from(factory.create()).timeout(properties.getAcquireTimeout()),
                    connection -> provision(connection, properties, config)
                            .then(Mono.defer(() -> Flux.from(connection.createStatement("SELECT cache_name, cache_key, value, expires_at, created_at FROM "
                                            + table + " WHERE FALSE").execute())
                                    .flatMap(result -> result.map((row, metadata) -> true)).then())),
                    connection -> close(connection, properties),
                    (connection, error) -> close(connection, properties),
                    connection -> close(connection, properties))
                    .timeout(properties.getInitializationTimeout())
                    .block(properties.getInitializationTimeout().plus(properties.getShutdownTimeout()));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Cannot initialize PostgreSQL cache storage");
        }
    }

    private static Mono<Void> provision(Connection connection, CacheProperties.PostgresConfig properties,
                                        PostgresCacheConfig config) {
        if (!properties.isAutoCreateSchema()) {
            return Mono.empty();
        }
        String schema = config.getSchema() != null ? config.getSchema() : "firefly_cache";
        String table = config.qualifiedTable();
        // The index name is bounded independently of an explicitly supplied 63-byte table name.
        String index = PostgresCacheConfig.quoteIdentifier("firefly_cache_expiry_"
                + java.util.HexFormat.of().formatHex(sha256(table)).substring(0, 16));
        return execute(connection, "CREATE SCHEMA IF NOT EXISTS " + PostgresCacheConfig.quoteIdentifier(schema))
                .then(execute(connection, "CREATE TABLE IF NOT EXISTS " + table + " ("
                        + "cache_name VARCHAR(255) NOT NULL, cache_key VARCHAR(1024) NOT NULL, value BYTEA NOT NULL, "
                        + "expires_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), "
                        + "PRIMARY KEY (cache_name, cache_key))"))
                .then(execute(connection, "CREATE INDEX IF NOT EXISTS " + index + " ON " + table
                        + " (expires_at) WHERE expires_at IS NOT NULL"));
    }

    private static byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static Mono<Void> execute(Connection connection, String sql) {
        return Mono.defer(() -> Flux.from(connection.createStatement(sql).execute())
                .flatMap(result -> result.getRowsUpdated()).then());
    }

    private static Mono<Void> close(Connection connection, CacheProperties.PostgresConfig properties) {
        return Mono.from(connection.close()).timeout(properties.getShutdownTimeout());
    }

    private static void requirePositive(java.time.Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("PostgreSQL cache timeouts must be positive");
        }
    }
}
