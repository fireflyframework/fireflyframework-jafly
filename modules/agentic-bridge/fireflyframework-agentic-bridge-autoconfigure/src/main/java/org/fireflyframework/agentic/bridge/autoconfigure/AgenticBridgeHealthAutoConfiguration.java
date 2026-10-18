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

import org.fireflyframework.agentic.bridge.AgenticBridge;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires the {@link AgenticBridgeHealthIndicator} when Spring Boot Actuator
 * is on the classpath and the indicator hasn't been disabled via
 * {@code management.health.firefly-agentic-bridge.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.ReactiveHealthIndicator")
public class AgenticBridgeHealthAutoConfiguration {

    @Bean(name = "fireflyAgenticBridgeHealthIndicator")
    @ConditionalOnBean(AgenticBridge.class)
    @ConditionalOnMissingBean(name = "fireflyAgenticBridgeHealthIndicator")
    @ConditionalOnEnabledHealthIndicator("firefly-agentic-bridge")
    public AgenticBridgeHealthIndicator fireflyAgenticBridgeHealthIndicator(AgenticBridge bridge) {
        return new AgenticBridgeHealthIndicator(bridge);
    }
}
