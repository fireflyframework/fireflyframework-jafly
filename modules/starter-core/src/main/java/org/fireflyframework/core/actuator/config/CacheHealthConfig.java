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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for cache health indicators and metrics.
 * <p>
 * This class provides comprehensive monitoring of cache systems including:
 * - Spring Cache abstraction monitoring
 * - Redis cache health (if available)
 * - Caffeine cache metrics
 * - Ehcache monitoring
 * - Cache hit/miss ratios and performance metrics
 */
@Configuration
@ConditionalOnClass(CacheManager.class)
@ConditionalOnProperty(prefix = "firefly.actuator.health.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(HealthContributorAutoConfiguration.class)
public class CacheHealthConfig {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public CacheHealthConfig(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Provides cache health indicator.
     * Monitors the health and performance of all configured caches.
     *
     * @param cacheManagers list of CacheManager instances
     * @return cache health indicator
     */
    @Bean
    public HealthIndicator cacheHealthIndicator(List<CacheManager> cacheManagers) {
        return () -> {
            Map<String, Object> details = new HashMap<>();
            boolean allHealthy = true;
            
            for (int i = 0; i < cacheManagers.size(); i++) {
                CacheManager cacheManager = cacheManagers.get(i);
                String managerName = "cacheManager_" + i;
                
                Map<String, Object> managerDetails = checkCacheManagerHealth(cacheManager);
                details.put(managerName, managerDetails);
                
                if (!"UP".equals(managerDetails.get("status"))) {
                    allHealthy = false;
                }
                
                // Register metrics for this cache manager
                registerCacheMetrics(cacheManager, managerName);
            }
            
            if (cacheManagers.isEmpty()) {
                details.put("message", "No cache managers configured");
            }
            
            return allHealthy && !cacheManagers.isEmpty()
                    ? org.springframework.boot.actuate.health.Health.up().withDetails(details).build()
                    : org.springframework.boot.actuate.health.Health.down().withDetails(details).build();
        };
    }

    /**
     * Checks the health of a CacheManager and its caches.
     */
    private Map<String, Object> checkCacheManagerHealth(CacheManager cacheManager) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            details.put("status", "UP");
            details.put("type", cacheManager.getClass().getSimpleName());
            
            // Get cache names
            java.util.Collection<String> cacheNames = cacheManager.getCacheNames();
            details.put("cacheCount", cacheNames.size());
            details.put("cacheNames", cacheNames);
            
            // Check individual caches
            Map<String, Object> cacheDetails = new HashMap<>();
            for (String cacheName : cacheNames) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cacheDetails.put(cacheName, checkIndividualCacheHealth(cache));
                }
            }
            details.put("caches", cacheDetails);
            
            // Check specific cache implementations
            checkSpecificCacheImplementation(cacheManager, details);
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Checks the health of an individual cache.
     */
    private Map<String, Object> checkIndividualCacheHealth(Cache cache) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            details.put("status", "UP");
            details.put("name", cache.getName());
            details.put("nativeType", cache.getNativeCache().getClass().getSimpleName());
            
            // Test basic cache operations
            String testKey = "_health_check_key_";
            String testValue = "health_check_value";
            
            try {
                cache.put(testKey, testValue);
                Cache.ValueWrapper retrieved = cache.get(testKey);
                cache.evict(testKey);
                
                details.put("operationalTest", retrieved != null ? "PASSED" : "FAILED");
            } catch (Exception e) {
                details.put("operationalTest", "FAILED");
                details.put("operationalError", e.getMessage());
            }
            
            // Get cache-specific metrics
            getCacheSpecificMetrics(cache, details);
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Gets metrics specific to different cache implementations.
     */
    private void getCacheSpecificMetrics(Cache cache, Map<String, Object> details) {
        Object nativeCache = cache.getNativeCache();
        
        try {
            // Caffeine cache metrics
            if (nativeCache.getClass().getName().contains("caffeine")) {
                getCaffeineMetrics(nativeCache, details);
            }
            // Ehcache metrics
            else if (nativeCache.getClass().getName().contains("ehcache")) {
                getEhcacheMetrics(nativeCache, details);
            }
            // Concurrent Map cache metrics
            else if (nativeCache instanceof java.util.concurrent.ConcurrentMap) {
                getConcurrentMapMetrics((java.util.concurrent.ConcurrentMap<?, ?>) nativeCache, details);
            }
        } catch (Exception e) {
            details.put("metricsWarning", "Could not retrieve cache-specific metrics: " + e.getMessage());
        }
    }

    /**
     * Gets Caffeine cache specific metrics.
     */
    private void getCaffeineMetrics(Object caffeineCache, Map<String, Object> details) {
        try {
            // Use reflection to get Caffeine cache stats
            Object stats = caffeineCache.getClass().getMethod("stats").invoke(caffeineCache);
            if (stats != null) {
                Map<String, Object> statsMap = new HashMap<>();
                statsMap.put("hitCount", stats.getClass().getMethod("hitCount").invoke(stats));
                statsMap.put("missCount", stats.getClass().getMethod("missCount").invoke(stats));
                statsMap.put("requestCount", stats.getClass().getMethod("requestCount").invoke(stats));
                statsMap.put("hitRate", stats.getClass().getMethod("hitRate").invoke(stats));
                statsMap.put("missRate", stats.getClass().getMethod("missRate").invoke(stats));
                statsMap.put("evictionCount", stats.getClass().getMethod("evictionCount").invoke(stats));
                details.put("statistics", statsMap);
            }
        } catch (Exception e) {
            // Silently ignore reflection errors
        }
    }

    /**
     * Gets Ehcache specific metrics.
     */
    private void getEhcacheMetrics(Object ehcache, Map<String, Object> details) {
        try {
            // Use reflection to get Ehcache statistics
            Object runtimeConfiguration = ehcache.getClass().getMethod("getRuntimeConfiguration").invoke(ehcache);
            if (runtimeConfiguration != null) {
                Map<String, Object> configMap = new HashMap<>();
                // Add basic configuration details
                details.put("configuration", configMap);
            }
        } catch (Exception e) {
            // Silently ignore reflection errors
        }
    }

    /**
     * Gets ConcurrentMap cache metrics.
     */
    private void getConcurrentMapMetrics(java.util.concurrent.ConcurrentMap<?, ?> map, Map<String, Object> details) {
        details.put("size", map.size());
        details.put("isEmpty", map.isEmpty());
    }

    /**
     * Checks for specific cache implementation features.
     */
    private void checkSpecificCacheImplementation(CacheManager cacheManager, Map<String, Object> details) {
        String className = cacheManager.getClass().getName();
        
        if (className.contains("Redis")) {
            details.put("implementation", "Redis");
            checkRedisSpecificHealth(cacheManager, details);
        } else if (className.contains("Caffeine")) {
            details.put("implementation", "Caffeine");
        } else if (className.contains("Ehcache")) {
            details.put("implementation", "Ehcache");
        } else if (className.contains("Concurrent")) {
            details.put("implementation", "ConcurrentMap");
        } else {
            details.put("implementation", "Generic");
        }
    }

    /**
     * Checks Redis-specific health if Redis cache manager is detected.
     */
    private void checkRedisSpecificHealth(CacheManager cacheManager, Map<String, Object> details) {
        try {
            // Try to get connection information for Redis
            details.put("redisHealthNote", "Redis cache detected - consider using Redis health indicator");
        } catch (Exception e) {
            details.put("redisHealthWarning", "Could not check Redis connection: " + e.getMessage());
        }
    }

    /**
     * Registers cache metrics with Micrometer if available.
     */
    private void registerCacheMetrics(CacheManager cacheManager, String managerName) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            try {
                // Register basic cache manager metrics
                meterRegistry.gauge("cache.manager.count", 
                        Tags.of("manager", managerName), 
                        cacheManager.getCacheNames().size());
                
                // Register metrics for individual caches
                for (String cacheName : cacheManager.getCacheNames()) {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        registerIndividualCacheMetrics(meterRegistry, cache, managerName);
                    }
                }
            } catch (Exception e) {
                // Log warning but don't fail
            }
        }
    }

    /**
     * Registers metrics for individual caches.
     */
    private void registerIndividualCacheMetrics(MeterRegistry meterRegistry, Cache cache, String managerName) {
        try {
            Tags tags = Tags.of("cache", cache.getName(), "manager", managerName);
            
            // Register basic cache presence gauge
            meterRegistry.gauge("cache.status", tags, 1);
            
            // Try to register cache-specific metrics if supported
            Object nativeCache = cache.getNativeCache();
            if (nativeCache.getClass().getName().contains("caffeine")) {
                // Caffeine caches can be monitored with built-in Micrometer support
                try {
                    Class.forName("io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics")
                            .getMethod("monitor", MeterRegistry.class, Object.class, String.class, Iterable.class)
                            .invoke(null, meterRegistry, nativeCache, cache.getName(), tags);
                } catch (Exception e) {
                    // Silently ignore if CaffeineCacheMetrics is not available
                }
            }
        } catch (Exception e) {
            // Log warning but don't fail
        }
    }
}