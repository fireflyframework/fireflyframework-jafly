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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration class for application-level metrics and startup monitoring.
 * <p>
 * This class provides comprehensive monitoring of application lifecycle including:
 * - Application startup time metrics
 * - Context refresh timing
 * - Application ready event timing
 * - Runtime information and uptime
 * - Custom application metrics
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.actuator.metrics.application", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
public class ApplicationMetricsConfig {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final Environment environment;
    private final AtomicLong applicationStartTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong contextRefreshTime = new AtomicLong();
    private final AtomicLong applicationReadyTime = new AtomicLong();

    public ApplicationMetricsConfig(ObjectProvider<MeterRegistry> meterRegistryProvider, Environment environment) {
        this.meterRegistryProvider = meterRegistryProvider;
        this.environment = environment;
    }

    /**
     * Provides application startup metrics listener.
     * Records timing of various application lifecycle events.
     *
     * @return startup metrics listener
     */
    @Bean
    public ApplicationListener<ApplicationStartedEvent> applicationStartedListener() {
        return event -> {
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
            if (meterRegistry != null) {
                long startupTime = System.currentTimeMillis() - applicationStartTime.get();
                
                Timer.builder("application.startup.time")
                        .description("Time taken for application to start")
                        .tags(getApplicationTags())
                        .register(meterRegistry)
                        .record(Duration.ofMillis(startupTime));
                
                // Record JVM start time for reference
                long jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
                meterRegistry.gauge("application.jvm.start.time", 
                        getApplicationTags(), 
                        jvmStartTime);
            }
        };
    }

    /**
     * Provides context refresh metrics listener.
     * Records timing of Spring context refresh events.
     *
     * @return context refresh metrics listener
     */
    @Bean
    public ApplicationListener<ContextRefreshedEvent> contextRefreshedListener() {
        return event -> {
            if (contextRefreshTime.compareAndSet(0, System.currentTimeMillis())) {
                MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
                if (meterRegistry != null) {
                    long refreshTime = contextRefreshTime.get() - applicationStartTime.get();
                    
                    Timer.builder("application.context.refresh.time")
                            .description("Time taken for Spring context to refresh")
                            .tags(getApplicationTags())
                            .register(meterRegistry)
                            .record(Duration.ofMillis(refreshTime));
                }
            }
        };
    }

    /**
     * Provides application ready metrics listener.
     * Records timing when application is fully ready to serve requests.
     *
     * @return application ready metrics listener
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyListener() {
        return event -> {
            if (applicationReadyTime.compareAndSet(0, System.currentTimeMillis())) {
                MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
                if (meterRegistry != null) {
                    long readyTime = applicationReadyTime.get() - applicationStartTime.get();
                    
                    Timer.builder("application.ready.time")
                            .description("Total time for application to be ready")
                            .tags(getApplicationTags())
                            .register(meterRegistry)
                            .record(Duration.ofMillis(readyTime));
                    
                    // Register uptime gauge
                    meterRegistry.gauge("application.uptime", 
                            getApplicationTags(), 
                            this, 
                            ApplicationMetricsConfig::getUptimeSeconds);
                    
                    // Register application state gauge
                    meterRegistry.gauge("application.state", 
                            getApplicationTags(), 
                            this, 
                            config -> {
                                // Return different values based on application state
                                if (applicationReadyTime.get() > 0) {
                                    return 3.0; // READY
                                } else if (contextRefreshTime.get() > 0) {
                                    return 2.0; // CONTEXT_REFRESHED
                                } else {
                                    return 1.0; // STARTING
                                }
                            });
                    
                    // Register startup phases metrics
                    registerStartupPhaseMetrics(meterRegistry);
                }
            }
        };
    }

    /**
     * Provides enhanced application info contributor.
     * Adds detailed application runtime and startup information.
     *
     * @return application info contributor
     */
    @Bean
    public InfoContributor enhancedApplicationInfoContributor() {
        return builder -> {
            Map<String, Object> details = new HashMap<>();
            
            // Add startup timing information
            Map<String, Object> startup = new HashMap<>();
            startup.put("startTime", Instant.ofEpochMilli(applicationStartTime.get()).toString());
            
            if (contextRefreshTime.get() > 0) {
                startup.put("contextRefreshTime", contextRefreshTime.get() - applicationStartTime.get() + "ms");
            }
            
            if (applicationReadyTime.get() > 0) {
                startup.put("totalReadyTime", applicationReadyTime.get() - applicationStartTime.get() + "ms");
                startup.put("uptime", getUptimeSeconds() + "s");
            }
            
            details.put("startup", startup);
            
            // Add JVM runtime information
            Map<String, Object> runtime = new HashMap<>();
            runtime.put("jvmStartTime", Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()).toString());
            runtime.put("jvmUptime", ManagementFactory.getRuntimeMXBean().getUptime() + "ms");
            runtime.put("processors", Runtime.getRuntime().availableProcessors());
            runtime.put("maxMemory", Runtime.getRuntime().maxMemory());
            runtime.put("totalMemory", Runtime.getRuntime().totalMemory());
            runtime.put("freeMemory", Runtime.getRuntime().freeMemory());
            
            details.put("runtime", runtime);
            
            // Add application configuration info
            Map<String, Object> config = new HashMap<>();
            config.put("activeProfiles", environment.getActiveProfiles());
            config.put("defaultProfiles", environment.getDefaultProfiles());
            
            details.put("configuration", config);
            
            builder.withDetail("application", details);
        };
    }

    /**
     * Registers detailed startup phase metrics.
     */
    private void registerStartupPhaseMetrics(MeterRegistry meterRegistry) {
        Tags tags = getApplicationTags();
        
        // Context refresh phase
        if (contextRefreshTime.get() > 0) {
            long contextPhaseTime = contextRefreshTime.get() - applicationStartTime.get();
            meterRegistry.gauge("application.startup.phase.context", tags, contextPhaseTime);
        }
        
        // Ready phase (time from context refresh to ready)
        if (applicationReadyTime.get() > 0 && contextRefreshTime.get() > 0) {
            long readyPhaseTime = applicationReadyTime.get() - contextRefreshTime.get();
            meterRegistry.gauge("application.startup.phase.ready", tags, readyPhaseTime);
        }
        
        // Register memory usage at startup
        Runtime runtime = Runtime.getRuntime();
        meterRegistry.gauge("application.startup.memory.used", tags, 
                runtime.totalMemory() - runtime.freeMemory());
        meterRegistry.gauge("application.startup.memory.total", tags, runtime.totalMemory());
        meterRegistry.gauge("application.startup.memory.max", tags, runtime.maxMemory());
    }

    /**
     * Gets common application tags for metrics.
     */
    private Tags getApplicationTags() {
        String applicationName = environment.getProperty("spring.application.name", "application");
        String activeProfile = environment.getProperty("spring.profiles.active", "default");
        
        return Tags.of(
                "application", applicationName,
                "profile", activeProfile
        );
    }

    /**
     * Gets application uptime in seconds.
     */
    private double getUptimeSeconds() {
        if (applicationReadyTime.get() == 0) {
            return 0.0;
        }
        return (System.currentTimeMillis() - applicationReadyTime.get()) / 1000.0;
    }

}