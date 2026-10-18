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
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for JVM metrics.
 * <p>
 * This class provides comprehensive JVM-related metrics including:
 * - Memory usage (heap, non-heap, memory pools)
 * - Garbage collection metrics
 * - Thread metrics
 * - Class loader metrics
 * - System metrics (CPU, uptime)
 * - JVM heap pressure monitoring
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "firefly.actuator.metrics.jvm", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
public class JvmMetricsConfig {

    /**
     * Provides JVM memory metrics.
     * Includes heap, non-heap, and memory pool metrics.
     *
     * @return JVM memory metrics binder
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Provides JVM garbage collection metrics.
     * Includes GC pause times and counts for all garbage collectors.
     *
     * @return JVM GC metrics binder
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Provides JVM thread metrics.
     * Includes thread counts, states, and deadlock detection.
     *
     * @return JVM thread metrics binder
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * Provides JVM class loader metrics.
     * Includes loaded and unloaded class counts.
     *
     * @return JVM class loader metrics binder
     */
    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    /**
     * Provides JVM heap pressure metrics.
     * Monitors memory allocation rates and pressure.
     *
     * @return JVM heap pressure metrics binder
     */
    @Bean
    public JvmHeapPressureMetrics jvmHeapPressureMetrics() {
        return new JvmHeapPressureMetrics();
    }

    /**
     * Provides system processor metrics.
     * Includes CPU usage and available processors.
     *
     * @return processor metrics binder
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Provides system uptime metrics.
     * Tracks application and system uptime.
     *
     * @return uptime metrics binder
     */
    @Bean
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }
}