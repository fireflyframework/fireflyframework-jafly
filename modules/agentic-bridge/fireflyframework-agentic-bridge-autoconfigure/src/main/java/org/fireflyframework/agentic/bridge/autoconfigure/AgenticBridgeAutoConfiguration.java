/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.fireflyframework.agentic.bridge.AgenticClient;
import org.fireflyframework.agentic.bridge.internal.DefaultAgenticBridge;
import org.fireflyframework.agentic.bridge.transport.queue.QueueAgenticPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Map;

/**
 * Spring Boot auto-configuration entry point for the agentic bridge.
 *
 * <p>Activated when the {@code firefly.agentic-bridge.enabled} property is
 * {@code true} (the default) and {@link AgenticClient} is on the classpath.</p>
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(AgenticClient.class)
@ConditionalOnProperty(prefix = "firefly.agentic-bridge", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticBridgeProperties.class)
@Import({AgenticBridgeHealthAutoConfiguration.class})
public class AgenticBridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgenticClient fireflyAgenticClient(AgenticBridgeProperties properties,
                                              ObjectProvider<ObjectMapper> objectMapper,
                                              ObjectProvider<MeterRegistry> meterRegistry) {
        return AgenticBridgeFactory.buildClient(
                properties.getPrimary(),
                objectMapper.getIfAvailable(),
                meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AgenticBridge fireflyAgenticBridge(AgenticClient client,
                                              AgenticBridgeProperties properties,
                                              ObjectProvider<ObjectMapper> objectMapper,
                                              ObjectProvider<MeterRegistry> meterRegistry) {
        Map<String, QueueAgenticPublisher> publishers = AgenticBridgeFactory.buildPublishers(
                properties,
                objectMapper.getIfAvailable(),
                meterRegistry.getIfAvailable());
        return new DefaultAgenticBridge(client, publishers);
    }

    /**
     * Optional Spring Actuator health indicator. Loaded only when the
     * Actuator classes are on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.ReactiveHealthIndicator")
    static class HealthMarker { }
}
