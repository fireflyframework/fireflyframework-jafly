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

package org.fireflyframework.cache.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.spi.ConnectionFactory;
import org.fireflyframework.cache.adapter.postgres.PostgresCacheConfig;
import org.fireflyframework.cache.properties.CacheProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a lazy technical cache pool, independently of business persistence. */
@AutoConfiguration(after = CacheAutoConfiguration.class)
@ConditionalOnClass(name = {"io.r2dbc.spi.ConnectionFactory", "io.r2dbc.pool.ConnectionPool",
        "io.r2dbc.postgresql.PostgresqlConnectionFactory"})
@ConditionalOnProperty(prefix = "firefly.cache", name = {"enabled", "postgres.enabled"},
        havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(name = "fireflyCachePostgresConnectionFactory")
@Conditional(PostgresCacheAutoConfiguration.ConfiguredPostgres.class)
@EnableConfigurationProperties(CacheProperties.class)
public class PostgresCacheAutoConfiguration {
    static final class ConfiguredPostgres implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String database = context.getEnvironment().getProperty("firefly.cache.postgres.database");
            String username = context.getEnvironment().getProperty("firefly.cache.postgres.username");
            return database != null && !database.isBlank() && username != null && !username.isBlank();
        }
    }

    @Bean(destroyMethod = "close")
    @Lazy
    OwnedPostgresPool fireflyCachePostgresOwnedPool(CacheProperties properties) {
        var cfg = properties.getPostgres();
        requireText(cfg.getHost(), "host");
        requireText(cfg.getDatabase(), "database");
        requireText(cfg.getUsername(), "username");
        PostgresCacheConfig.quoteIdentifier(cfg.getSchema());
        PostgresCacheConfig.quoteIdentifier(cfg.getCacheTable());
        if (cfg.getPort() < 1 || cfg.getPort() > 65535 || cfg.getMinPoolSize() < 0
                || cfg.getMaxPoolSize() < 1 || cfg.getMinPoolSize() > cfg.getMaxPoolSize()) {
            throw new IllegalArgumentException("Invalid PostgreSQL cache port or pool bounds");
        }
        requirePositive(cfg.getConnectTimeout());
        requirePositive(cfg.getAcquireTimeout());
        requirePositive(cfg.getInitializationTimeout());
        requirePositive(cfg.getShutdownTimeout());

        var builder = PostgresqlConnectionConfiguration.builder().host(cfg.getHost()).port(cfg.getPort())
                .database(cfg.getDatabase()).username(cfg.getUsername()).schema(cfg.getSchema())
                .connectTimeout(cfg.getConnectTimeout());
        if (cfg.getPassword() != null) {
            builder.password(cfg.getPassword());
        }
        if (cfg.getSslMode() != null && !cfg.getSslMode().isBlank()) {
            builder.sslMode(SSLMode.fromValue(cfg.getSslMode()));
        }
        if (cfg.getProperties() != null && !cfg.getProperties().isEmpty()) {
            var options = new HashMap<String, String>();
            cfg.getProperties().forEach((name, value) -> {
                if (value != null) options.put(name, value.toString());
            });
            builder.options(options);
        }
        var configuration = ConnectionPoolConfiguration.builder(new PostgresqlConnectionFactory(builder.build()))
                .maxSize(cfg.getMaxPoolSize()).initialSize(cfg.getMinPoolSize()).minIdle(cfg.getMinPoolSize())
                .maxAcquireTime(cfg.getAcquireTimeout()).maxCreateConnectionTime(cfg.getConnectTimeout())
                .maxValidationTime(cfg.getAcquireTimeout()).acquireRetry(0).maxIdleTime(Duration.ofMinutes(5)).build();
        return new OwnedPostgresPool(new ConnectionPool(configuration), cfg.getShutdownTimeout());
    }

    @Bean(name = "fireflyCachePostgresConnectionFactory", destroyMethod = "", defaultCandidate = false)
    @Lazy
    public ConnectionFactory fireflyCachePostgresConnectionFactory(OwnedPostgresPool owner) {
        return owner.pool;
    }

    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("firefly.cache.postgres." + property + " must be configured");
        }
    }

    private static void requirePositive(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("PostgreSQL cache timeouts must be positive");
        }
    }

    static final class OwnedPostgresPool implements AutoCloseable {
        private final ConnectionPool pool;
        private final Duration shutdownTimeout;
        private final AtomicBoolean closed = new AtomicBoolean();

        OwnedPostgresPool(ConnectionPool pool, Duration shutdownTimeout) {
            this.pool = pool;
            this.shutdownTimeout = shutdownTimeout;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                pool.disposeLater().block(shutdownTimeout);
            }
        }
    }
}
