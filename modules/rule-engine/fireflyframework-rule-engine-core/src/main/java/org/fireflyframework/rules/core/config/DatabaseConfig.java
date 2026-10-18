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

package org.fireflyframework.rules.core.config;

import org.springframework.context.event.EventListener;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Advanced database configuration for the Firefly Rule Engine.
 * Provides optimized R2DBC connection pool settings for high-load scenarios.
 */
@Configuration
@EnableScheduling
@Slf4j
public class DatabaseConfig {

    @Value("${spring.r2dbc.url}")
    private String r2dbcUrl;

    @Value("${spring.r2dbc.username}")
    private String username;

    @Value("${spring.r2dbc.password}")
    private String password;

    @Value("${spring.r2dbc.pool.initial-size:10}")
    private int initialSize;

    @Value("${spring.r2dbc.pool.max-size:50}")
    private int maxSize;

    @Value("${spring.r2dbc.pool.min-idle:5}")
    private int minIdle;

    @Value("${spring.r2dbc.pool.max-idle-time:15m}")
    private Duration maxIdleTime;

    @Value("${spring.r2dbc.pool.max-acquire-time:60s}")
    private Duration maxAcquireTime;

    @Value("${spring.r2dbc.pool.max-life-time:30m}")
    private Duration maxLifeTime;

    @Value("${spring.r2dbc.pool.max-create-connection-time:10s}")
    private Duration maxCreateConnectionTime;

    @Value("${spring.r2dbc.pool.acquire-retry:3}")
    private int acquireRetry;

    /**
     * Primary connection factory with optimized connection pool.
     */
    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        // Parse R2DBC URL to extract connection details
        String cleanUrl = r2dbcUrl.replace("r2dbc:postgresql://", "");
        String[] urlParts = cleanUrl.split("/");
        String[] hostPort = urlParts[0].split(":");
        
        String host = hostPort[0];
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 5432;
        String database = urlParts[1].split("\\?")[0];

        // Create PostgreSQL connection configuration with optimizations
        PostgresqlConnectionConfiguration connectionConfig = PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .database(database)
                .username(username)
                .password(password)
                // Performance optimizations
                .preparedStatementCacheQueries(256)  // Cache prepared statements
                .tcpKeepAlive(true)                   // Enable TCP keep-alive
                .tcpNoDelay(true)                     // Disable Nagle's algorithm for lower latency
                .connectTimeout(Duration.ofSeconds(10))
                .statementTimeout(Duration.ofSeconds(30))
                .lockWaitTimeout(Duration.ofSeconds(10))
                // SSL configuration (if needed)
                // SSL disabled for development - configure appropriately for production
                .build();

        // Create connection factory
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(connectionConfig);

        // Create connection pool configuration
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
                .initialSize(initialSize)
                .maxSize(maxSize)
                .minIdle(minIdle)
                .maxIdleTime(maxIdleTime)
                .maxAcquireTime(maxAcquireTime)
                .maxLifeTime(maxLifeTime)
                .maxCreateConnectionTime(maxCreateConnectionTime)
                .acquireRetry(acquireRetry)
                .validationQuery("SELECT 1")
                // Validation depth removed - not available in current version
                .name("firefly-rules-pool")
                .registerJmx(true)  // Enable JMX monitoring
                .build();

        ConnectionPool connectionPool = new ConnectionPool(poolConfig);
        
        log.info("Initialized R2DBC connection pool with settings: " +
                "initialSize={}, maxSize={}, minIdle={}, maxIdleTime={}, maxAcquireTime={}",
                initialSize, maxSize, minIdle, maxIdleTime, maxAcquireTime);

        return connectionPool;
    }

    /**
     * Scheduled task to log connection pool statistics.
     * Helps monitor pool performance and identify potential issues.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void logConnectionPoolStatistics() {
        // This would be enhanced with actual pool metrics if available
        // For now, we log the configuration
        log.info("Connection Pool Configuration - Max Size: {}, Initial Size: {}, Min Idle: {}, " +
                "Max Idle Time: {}, Max Acquire Time: {}, Max Life Time: {}",
                maxSize, initialSize, minIdle, maxIdleTime, maxAcquireTime, maxLifeTime);
    }

    /**
     * Configuration properties for database tuning.
     */
    public static class DatabaseProperties {
        // Connection pool settings
        private int initialSize = 10;
        private int maxSize = 50;
        private int minIdle = 5;
        private Duration maxIdleTime = Duration.ofMinutes(15);
        private Duration maxAcquireTime = Duration.ofSeconds(60);
        private Duration maxLifeTime = Duration.ofMinutes(30);
        private Duration maxCreateConnectionTime = Duration.ofSeconds(10);
        private int acquireRetry = 3;

        // PostgreSQL specific settings
        private int preparedStatementCacheQueries = 256;
        private boolean tcpKeepAlive = true;
        private boolean tcpNoDelay = true;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration statementTimeout = Duration.ofSeconds(30);
        private Duration lockWaitTimeout = Duration.ofSeconds(10);

        // Monitoring settings
        private boolean enableJmx = true;
        private boolean enableMetrics = true;
        private Duration metricsInterval = Duration.ofMinutes(1);

        // Getters and setters
        public int getInitialSize() { return initialSize; }
        public void setInitialSize(int initialSize) { this.initialSize = initialSize; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
        public int getMinIdle() { return minIdle; }
        public void setMinIdle(int minIdle) { this.minIdle = minIdle; }
        public Duration getMaxIdleTime() { return maxIdleTime; }
        public void setMaxIdleTime(Duration maxIdleTime) { this.maxIdleTime = maxIdleTime; }
        public Duration getMaxAcquireTime() { return maxAcquireTime; }
        public void setMaxAcquireTime(Duration maxAcquireTime) { this.maxAcquireTime = maxAcquireTime; }
        public Duration getMaxLifeTime() { return maxLifeTime; }
        public void setMaxLifeTime(Duration maxLifeTime) { this.maxLifeTime = maxLifeTime; }
        public Duration getMaxCreateConnectionTime() { return maxCreateConnectionTime; }
        public void setMaxCreateConnectionTime(Duration maxCreateConnectionTime) { this.maxCreateConnectionTime = maxCreateConnectionTime; }
        public int getAcquireRetry() { return acquireRetry; }
        public void setAcquireRetry(int acquireRetry) { this.acquireRetry = acquireRetry; }
        public int getPreparedStatementCacheQueries() { return preparedStatementCacheQueries; }
        public void setPreparedStatementCacheQueries(int preparedStatementCacheQueries) { this.preparedStatementCacheQueries = preparedStatementCacheQueries; }
        public boolean isTcpKeepAlive() { return tcpKeepAlive; }
        public void setTcpKeepAlive(boolean tcpKeepAlive) { this.tcpKeepAlive = tcpKeepAlive; }
        public boolean isTcpNoDelay() { return tcpNoDelay; }
        public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getStatementTimeout() { return statementTimeout; }
        public void setStatementTimeout(Duration statementTimeout) { this.statementTimeout = statementTimeout; }
        public Duration getLockWaitTimeout() { return lockWaitTimeout; }
        public void setLockWaitTimeout(Duration lockWaitTimeout) { this.lockWaitTimeout = lockWaitTimeout; }
        public boolean isEnableJmx() { return enableJmx; }
        public void setEnableJmx(boolean enableJmx) { this.enableJmx = enableJmx; }
        public boolean isEnableMetrics() { return enableMetrics; }
        public void setEnableMetrics(boolean enableMetrics) { this.enableMetrics = enableMetrics; }
        public Duration getMetricsInterval() { return metricsInterval; }
        public void setMetricsInterval(Duration metricsInterval) { this.metricsInterval = metricsInterval; }
    }
}
