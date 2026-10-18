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


package org.fireflyframework.core.actuator.config;

import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for database health indicators.
 * <p>
 * This class provides health indicators for database connections and
 * monitors connection pool health, query performance, and database availability.
 */
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "firefly.actuator.health.database", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter({DataSourceAutoConfiguration.class, HealthContributorAutoConfiguration.class})
public class DatabaseHealthConfig {

    /**
     * Provides a comprehensive database health indicator.
     * Monitors database connectivity, connection pool status, and performance.
     *
     * @param dataSources list of available data sources
     * @return database health indicator
     */
    @Bean
    public HealthIndicator databaseHealthIndicator(List<DataSource> dataSources) {
        return () -> {
            Map<String, Object> details = new HashMap<>();
            boolean allHealthy = true;
            
            for (int i = 0; i < dataSources.size(); i++) {
                DataSource dataSource = dataSources.get(i);
                String dataSourceName = "dataSource" + (i == 0 ? "" : "_" + i);
                
                try {
                    Map<String, Object> dataSourceDetails = checkDataSourceHealth(dataSource);
                    details.put(dataSourceName, dataSourceDetails);
                } catch (Exception e) {
                    allHealthy = false;
                    details.put(dataSourceName, Map.of(
                            "status", "DOWN",
                            "error", e.getMessage()
                    ));
                }
            }
            
            if (dataSources.isEmpty()) {
                details.put("message", "No data sources configured");
            }
            
            return allHealthy && !dataSources.isEmpty() 
                    ? org.springframework.boot.actuate.health.Health.up().withDetails(details).build()
                    : org.springframework.boot.actuate.health.Health.down().withDetails(details).build();
        };
    }

    /**
     * Checks the health of a specific data source.
     *
     * @param dataSource the data source to check
     * @return health details map
     * @throws SQLException if database check fails
     */
    private Map<String, Object> checkDataSourceHealth(DataSource dataSource) throws SQLException {
        Map<String, Object> details = new HashMap<>();
        
        long startTime = System.currentTimeMillis();
        
        try (Connection connection = dataSource.getConnection()) {
            // Test basic connectivity
            boolean isValid = connection.isValid(5); // 5 second timeout
            long responseTime = System.currentTimeMillis() - startTime;
            
            details.put("status", isValid ? "UP" : "DOWN");
            details.put("responseTime", responseTime + "ms");
            details.put("database", connection.getMetaData().getDatabaseProductName());
            details.put("version", connection.getMetaData().getDatabaseProductVersion());
            details.put("url", connection.getMetaData().getURL());
            details.put("driver", connection.getMetaData().getDriverName());
            
            // Check connection pool details if available
            checkConnectionPoolHealth(dataSource, details);
            
        } catch (SQLException e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
            details.put("errorCode", e.getErrorCode());
            details.put("sqlState", e.getSQLState());
            throw e;
        }
        
        return details;
    }

    /**
     * Checks connection pool health if the data source supports it.
     *
     * @param dataSource the data source to check
     * @param details the details map to populate
     */
    private void checkConnectionPoolHealth(DataSource dataSource, Map<String, Object> details) {
        try {
            // Check for HikariCP
            if (dataSource.getClass().getName().contains("HikariDataSource")) {
                checkHikariPoolHealth(dataSource, details);
            }
            // Check for Tomcat JDBC Pool
            else if (dataSource.getClass().getName().contains("org.apache.tomcat.jdbc.pool.DataSource")) {
                checkTomcatPoolHealth(dataSource, details);
            }
            // Check for DBCP2
            else if (dataSource.getClass().getName().contains("org.apache.commons.dbcp2.BasicDataSource")) {
                checkDbcp2PoolHealth(dataSource, details);
            }
        } catch (Exception e) {
            // Ignore pool health check errors, basic connectivity is more important
            details.put("poolHealthWarning", "Could not retrieve pool metrics: " + e.getMessage());
        }
    }

    /**
     * Checks HikariCP pool health.
     */
    private void checkHikariPoolHealth(DataSource dataSource, Map<String, Object> details) {
        try {
            // Use reflection to avoid direct dependency
            Object pool = dataSource.getClass().getMethod("getHikariPoolMXBean").invoke(dataSource);
            if (pool != null) {
                Map<String, Object> poolDetails = new HashMap<>();
                poolDetails.put("activeConnections", pool.getClass().getMethod("getActiveConnections").invoke(pool));
                poolDetails.put("idleConnections", pool.getClass().getMethod("getIdleConnections").invoke(pool));
                poolDetails.put("totalConnections", pool.getClass().getMethod("getTotalConnections").invoke(pool));
                poolDetails.put("threadsAwaitingConnection", pool.getClass().getMethod("getThreadsAwaitingConnection").invoke(pool));
                details.put("connectionPool", poolDetails);
            }
        } catch (Exception e) {
            // Silently ignore reflection errors
        }
    }

    /**
     * Checks Tomcat JDBC pool health.
     */
    private void checkTomcatPoolHealth(DataSource dataSource, Map<String, Object> details) {
        try {
            Map<String, Object> poolDetails = new HashMap<>();
            poolDetails.put("active", dataSource.getClass().getMethod("getActive").invoke(dataSource));
            poolDetails.put("idle", dataSource.getClass().getMethod("getIdle").invoke(dataSource));
            poolDetails.put("size", dataSource.getClass().getMethod("getSize").invoke(dataSource));
            details.put("connectionPool", poolDetails);
        } catch (Exception e) {
            // Silently ignore reflection errors
        }
    }

    /**
     * Checks DBCP2 pool health.
     */
    private void checkDbcp2PoolHealth(DataSource dataSource, Map<String, Object> details) {
        try {
            Map<String, Object> poolDetails = new HashMap<>();
            poolDetails.put("numActive", dataSource.getClass().getMethod("getNumActive").invoke(dataSource));
            poolDetails.put("numIdle", dataSource.getClass().getMethod("getNumIdle").invoke(dataSource));
            poolDetails.put("maxTotal", dataSource.getClass().getMethod("getMaxTotal").invoke(dataSource));
            details.put("connectionPool", poolDetails);
        } catch (Exception e) {
            // Silently ignore reflection errors
        }
    }
}