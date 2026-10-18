package org.fireflyframework.cqrs.actuator.endpoint;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.command.CommandHandlerRegistry;
import org.fireflyframework.cqrs.command.CommandMetricsService;
import org.fireflyframework.cqrs.query.DefaultQueryBus;
import org.fireflyframework.cqrs.query.QueryBus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Spring Boot Actuator endpoint for exposing comprehensive CQRS framework metrics.
 * 
 * <p>This endpoint provides detailed insights into the CQRS framework's performance,
 * including command and query processing statistics, handler registry information,
 * and system health metrics.
 * 
 * <p>Available endpoints:
 * <ul>
 *   <li><strong>GET /actuator/cqrs</strong> - Complete CQRS metrics overview</li>
 *   <li><strong>GET /actuator/cqrs/commands</strong> - Command processing metrics</li>
 *   <li><strong>GET /actuator/cqrs/queries</strong> - Query processing metrics</li>
 *   <li><strong>GET /actuator/cqrs/handlers</strong> - Handler registry information</li>
 *   <li><strong>GET /actuator/cqrs/health</strong> - CQRS framework health status</li>
 * </ul>
 * 
 * <p>Example response structure:
 * <pre>{@code
 * {
 *   "framework": {
 *     "version": "2025-08",
 *     "uptime": "PT2H30M15S",
 *     "status": "HEALTHY"
 *   },
 *   "commands": {
 *     "total_processed": 1250,
 *     "total_failed": 15,
 *     "success_rate": 98.8,
 *     "avg_processing_time_ms": 45.2,
 *     "by_type": { ... }
 *   },
 *   "queries": {
 *     "total_processed": 3420,
 *     "cache_hit_rate": 85.3,
 *     "avg_processing_time_ms": 12.8
 *   },
 *   "handlers": {
 *     "command_handlers": 12,
 *     "query_handlers": 8,
 *     "registered_types": [ ... ]
 *   }
 * }
 * }</pre>
 * 
 * @author Firefly Common Domain Library
 * @since 1.0.0
 */
@Component
@Endpoint(id = "cqrs")
public class CqrsMetricsEndpoint {

    private static final Logger log = LoggerFactory.getLogger(CqrsMetricsEndpoint.class);

    private final MeterRegistry meterRegistry;
    private final CommandMetricsService commandMetricsService;
    private final CommandHandlerRegistry commandHandlerRegistry;
    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final Instant startupTime;

    public CqrsMetricsEndpoint(
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) CommandMetricsService commandMetricsService,
            @Autowired(required = false) CommandHandlerRegistry commandHandlerRegistry,
            @Autowired(required = false) CommandBus commandBus,
            @Autowired(required = false) QueryBus queryBus) {
        this.meterRegistry = meterRegistry;
        this.commandMetricsService = commandMetricsService;
        this.commandHandlerRegistry = commandHandlerRegistry;
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.startupTime = Instant.now();
        
        log.info("CQRS Metrics Actuator endpoint initialized");
    }

    /**
     * Returns complete CQRS framework metrics overview.
     */
    @ReadOperation
    public Map<String, Object> cqrsMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        
        metrics.put("framework", getFrameworkInfo());
        metrics.put("commands", getCommandMetrics());
        metrics.put("queries", getQueryMetrics());
        metrics.put("handlers", getHandlerInfo());
        metrics.put("health", getHealthStatus());
        
        return metrics;
    }

    /**
     * Returns the metrics section identified by the path variable.
     *
     * <p>Supported sections: {@code commands}, {@code queries}, {@code handlers},
     * {@code health}. Any other value yields an empty map.
     *
     * <p>Spring Boot Actuator only allows a single {@code @ReadOperation} per
     * predicate (verb + path + media type). All four section dispatchers must
     * therefore live in a single method; the path-variable value is the
     * discriminator instead of the method name.
     */
    @ReadOperation
    public Map<String, Object> bySection(@Selector String section) {
        if (section == null) {
            return Collections.emptyMap();
        }
        return switch (section) {
            case "commands" -> getCommandMetrics();
            case "queries" -> getQueryMetrics();
            case "handlers" -> getHandlerInfo();
            case "health" -> getHealthStatus();
            default -> Collections.emptyMap();
        };
    }

    /**
     * Returns detailed command processing metrics. Plain method (no
     * {@code @ReadOperation}) so tests can call it directly without going
     * through the Actuator infrastructure.
     */
    public Map<String, Object> commands(String section) {
        return "commands".equals(section) ? getCommandMetrics() : Collections.emptyMap();
    }

    /**
     * Returns detailed query processing metrics. See {@link #commands(String)}.
     */
    public Map<String, Object> queries(String section) {
        return "queries".equals(section) ? getQueryMetrics() : Collections.emptyMap();
    }

    /**
     * Returns handler registry information. See {@link #commands(String)}.
     */
    public Map<String, Object> handlers(String section) {
        return "handlers".equals(section) ? getHandlerInfo() : Collections.emptyMap();
    }

    /**
     * Returns CQRS framework health status. See {@link #commands(String)}.
     */
    public Map<String, Object> health(String section) {
        return "health".equals(section) ? getHealthStatus() : Collections.emptyMap();
    }

    private Map<String, Object> getFrameworkInfo() {
        Map<String, Object> framework = new LinkedHashMap<>();
        framework.put("version", "2025-08");
        framework.put("uptime", Duration.between(startupTime, Instant.now()).toString());
        framework.put("startup_time", startupTime.toString());
        framework.put("metrics_enabled", meterRegistry != null);
        framework.put("command_metrics_enabled", commandMetricsService != null && commandMetricsService.isMetricsEnabled());
        return framework;
    }

    private Map<String, Object> getCommandMetrics() {
        Map<String, Object> commands = new LinkedHashMap<>();
        
        if (meterRegistry == null) {
            commands.put("status", "Metrics not available - MeterRegistry not configured");
            return commands;
        }

        // Global command metrics
        Counter processedCounter = meterRegistry.find("firefly.cqrs.command.processed").counter();
        Counter failedCounter = meterRegistry.find("firefly.cqrs.command.failed").counter();
        Counter validationFailedCounter = meterRegistry.find("firefly.cqrs.command.validation.failed").counter();
        Timer processingTimer = meterRegistry.find("firefly.cqrs.command.processing.time").timer();

        double totalProcessed = processedCounter != null ? processedCounter.count() : 0;
        double totalFailed = failedCounter != null ? failedCounter.count() : 0;
        double totalValidationFailed = validationFailedCounter != null ? validationFailedCounter.count() : 0;
        double totalRequests = totalProcessed + totalFailed;

        commands.put("total_processed", (long) totalProcessed);
        commands.put("total_failed", (long) totalFailed);
        commands.put("total_validation_failed", (long) totalValidationFailed);
        commands.put("total_requests", (long) totalRequests);
        commands.put("success_rate", totalRequests > 0 ? (totalProcessed / totalRequests) * 100 : 0);
        commands.put("failure_rate", totalRequests > 0 ? (totalFailed / totalRequests) * 100 : 0);
        commands.put("validation_failure_rate", totalRequests > 0 ? (totalValidationFailed / totalRequests) * 100 : 0);

        if (processingTimer != null) {
            commands.put("avg_processing_time_ms", processingTimer.mean(TimeUnit.MILLISECONDS));
            commands.put("max_processing_time_ms", processingTimer.max(TimeUnit.MILLISECONDS));
            commands.put("total_processing_time_ms", processingTimer.totalTime(TimeUnit.MILLISECONDS));
        }

        // Per-command-type metrics
        commands.put("by_type", getCommandTypeMetrics());

        return commands;
    }

    private Map<String, Object> getQueryMetrics() {
        Map<String, Object> queries = new LinkedHashMap<>();
        
        if (meterRegistry == null) {
            queries.put("status", "Metrics not available - MeterRegistry not configured");
            return queries;
        }

        // Global query metrics
        Counter processedCounter = meterRegistry.find("firefly.cqrs.query.processed").counter();
        Timer processingTimer = meterRegistry.find("firefly.cqrs.query.processing.time").timer();

        double totalProcessed = processedCounter != null ? processedCounter.count() : 0;
        queries.put("total_processed", (long) totalProcessed);

        if (processingTimer != null) {
            queries.put("avg_processing_time_ms", processingTimer.mean(TimeUnit.MILLISECONDS));
            queries.put("max_processing_time_ms", processingTimer.max(TimeUnit.MILLISECONDS));
            queries.put("total_processing_time_ms", processingTimer.totalTime(TimeUnit.MILLISECONDS));
        }

        // Cache metrics (if available)
        queries.put("cache", getCacheMetrics());

        return queries;
    }

    private Map<String, Object> getCommandTypeMetrics() {
        Map<String, Object> byType = new LinkedHashMap<>();
        
        if (meterRegistry == null) {
            return byType;
        }

        // Find all command type metrics
        Set<String> commandTypes = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals("firefly.cqrs.command.type.processed"))
            .map(meter -> meter.getId().getTag("command.type"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (String commandType : commandTypes) {
            Map<String, Object> typeMetrics = new LinkedHashMap<>();
            
            Counter processedCounter = meterRegistry.find("firefly.cqrs.command.type.processed")
                .tag("command.type", commandType).counter();
            Counter failedCounter = meterRegistry.find("firefly.cqrs.command.type.failed")
                .tag("command.type", commandType).counter();
            Timer processingTimer = meterRegistry.find("firefly.cqrs.command.type.processing.time")
                .tag("command.type", commandType).timer();

            if (processedCounter != null) {
                typeMetrics.put("processed", (long) processedCounter.count());
            }
            if (failedCounter != null) {
                typeMetrics.put("failed", (long) failedCounter.count());
            }
            if (processingTimer != null) {
                typeMetrics.put("avg_processing_time_ms", processingTimer.mean(TimeUnit.MILLISECONDS));
                typeMetrics.put("max_processing_time_ms", processingTimer.max(TimeUnit.MILLISECONDS));
            }

            byType.put(commandType, typeMetrics);
        }

        return byType;
    }

    private Map<String, Object> getCacheMetrics() {
        Map<String, Object> cache = new LinkedHashMap<>();
        
        if (meterRegistry == null) {
            cache.put("status", "Cache metrics not available");
            return cache;
        }

        // Look for cache-related metrics
        Counter cacheHits = meterRegistry.find("cache.gets").tag("result", "hit").counter();
        Counter cacheMisses = meterRegistry.find("cache.gets").tag("result", "miss").counter();
        
        if (cacheHits != null && cacheMisses != null) {
            double hits = cacheHits.count();
            double misses = cacheMisses.count();
            double total = hits + misses;
            
            cache.put("hits", (long) hits);
            cache.put("misses", (long) misses);
            cache.put("hit_rate", total > 0 ? (hits / total) * 100 : 0);
        } else {
            cache.put("status", "Cache metrics not available");
        }

        return cache;
    }

    private Map<String, Object> getHandlerInfo() {
        Map<String, Object> handlers = new LinkedHashMap<>();

        if (commandHandlerRegistry != null) {
            // Get command handler information
            Map<String, Object> commandHandlers = new LinkedHashMap<>();

            // Use reflection to get registered handlers count and types
            try {
                java.lang.reflect.Field handlersField = commandHandlerRegistry.getClass().getDeclaredField("handlers");
                handlersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Class<?>, Object> registeredHandlers = (Map<Class<?>, Object>) handlersField.get(commandHandlerRegistry);

                commandHandlers.put("count", registeredHandlers.size());
                commandHandlers.put("registered_types", registeredHandlers.keySet().stream()
                    .map(Class::getSimpleName)
                    .sorted()
                    .collect(Collectors.toList()));

            } catch (Exception e) {
                log.debug("Could not access command handler registry details: {}", e.getMessage());
                commandHandlers.put("count", "Unknown");
                commandHandlers.put("status", "Handler registry details not accessible");
            }

            handlers.put("command_handlers", commandHandlers);
        } else {
            handlers.put("command_handlers", Map.of("status", "CommandHandlerRegistry not available"));
        }

        // Get query handler information
        if (queryBus instanceof DefaultQueryBus) {
            Map<String, Object> queryHandlers = new LinkedHashMap<>();

            try {
                java.lang.reflect.Field handlersField = DefaultQueryBus.class.getDeclaredField("handlers");
                handlersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<Class<?>, Object> registeredHandlers = (Map<Class<?>, Object>) handlersField.get(queryBus);

                queryHandlers.put("count", registeredHandlers.size());
                queryHandlers.put("registered_types", registeredHandlers.keySet().stream()
                    .map(Class::getSimpleName)
                    .sorted()
                    .collect(Collectors.toList()));

            } catch (Exception e) {
                log.debug("Could not access query handler registry details: {}", e.getMessage());
                queryHandlers.put("count", "Unknown");
                queryHandlers.put("status", "Query handler registry details not accessible");
            }

            handlers.put("query_handlers", queryHandlers);
        } else {
            handlers.put("query_handlers", Map.of("status", "DefaultQueryBus not available"));
        }

        return handlers;
    }

    private Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new LinkedHashMap<>();

        // Overall framework health
        boolean isHealthy = true;
        List<String> issues = new ArrayList<>();

        // Check command bus health
        if (commandBus == null) {
            isHealthy = false;
            issues.add("CommandBus not available");
        }

        // Check query bus health
        if (queryBus == null) {
            isHealthy = false;
            issues.add("QueryBus not available");
        }

        // Check metrics health
        if (meterRegistry == null) {
            issues.add("MeterRegistry not available - metrics disabled");
        }

        // Check command metrics service
        if (commandMetricsService == null) {
            issues.add("CommandMetricsService not available");
        } else if (!commandMetricsService.isMetricsEnabled()) {
            issues.add("Command metrics collection disabled");
        }

        health.put("status", isHealthy ? "HEALTHY" : "DEGRADED");
        health.put("components", getComponentHealth());

        if (!issues.isEmpty()) {
            health.put("issues", issues);
        }

        return health;
    }

    private Map<String, Object> getComponentHealth() {
        Map<String, Object> components = new LinkedHashMap<>();

        components.put("command_bus", commandBus != null ? "UP" : "DOWN");
        components.put("query_bus", queryBus != null ? "UP" : "DOWN");
        components.put("command_handler_registry", commandHandlerRegistry != null ? "UP" : "DOWN");
        components.put("meter_registry", meterRegistry != null ? "UP" : "DOWN");
        components.put("command_metrics_service",
            commandMetricsService != null && commandMetricsService.isMetricsEnabled() ? "UP" : "DOWN");

        return components;
    }
}
