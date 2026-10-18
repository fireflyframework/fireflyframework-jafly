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

package org.fireflyframework.eda.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Environment post-processor that conditionally excludes Spring Boot's
 * RabbitMQ auto-configuration when the Firefly EDA RabbitMQ transport is not
 * enabled.
 *
 * <p>This prevents Spring Boot from auto-creating connection factories and
 * health indicators for RabbitMQ when the application does not use it,
 * avoiding startup errors like "Rabbit health check failed - Connection refused".
 *
 * <p><strong>Behavior:</strong>
 * <ul>
 *   <li>If {@code spring.rabbitmq.host} is explicitly set, the user has opted
 *       into Spring Boot's native RabbitMQ auto-configuration, so it is left
 *       intact and nothing is excluded.</li>
 *   <li>Otherwise (the hexagonal case) {@code RabbitAutoConfiguration} is
 *       excluded. This covers two situations:
 *       <ul>
 *         <li>RabbitMQ is not used at all - excluding avoids spurious
 *             connection/health failures.</li>
 *         <li>RabbitMQ <em>is</em> used via {@code firefly.eda.*.rabbitmq.*} -
 *             Firefly creates and owns its own connection factories,
 *             {@code RabbitTemplate} and {@code AmqpAdmin}, so Boot's native
 *             beans are not needed and would otherwise conflict. In particular,
 *             once both the publisher-side and consumer-side Firefly connection
 *             factories exist, Boot's {@code rabbitListenerContainerFactory}
 *             can no longer autowire a single {@code ConnectionFactory}
 *             (two candidates) and fails - excluding Boot's auto-config removes
 *             that conflict.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Any existing {@code spring.autoconfigure.exclude} entries are preserved.
 */
public class FireflyEdaRabbitMqEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
    private static final String RABBIT_AUTO_CONFIG = "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> excludes = new ArrayList<>();

        if (shouldExcludeRabbit(environment)) {
            excludes.add(RABBIT_AUTO_CONFIG);
        }

        if (!excludes.isEmpty()) {
            applyExcludes(environment, excludes);
        }
    }

    private boolean shouldExcludeRabbit(ConfigurableEnvironment env) {
        // Respect explicit Spring Boot native configuration: if the user has
        // configured spring.rabbitmq.host they want Boot's native RabbitMQ
        // auto-configuration, so leave it intact.
        if (env.containsProperty("spring.rabbitmq.host")) {
            return false;
        }

        // In every other (hexagonal) case Boot's RabbitAutoConfiguration is
        // excluded. When Firefly EDA RabbitMQ is not in use this avoids spurious
        // connection/health failures; when it IS in use Firefly owns its own
        // connection factories / RabbitTemplate / AmqpAdmin and Boot's native
        // beans would otherwise conflict (notably the rabbitListenerContainerFactory
        // cannot pick between the publisher and consumer ConnectionFactory beans).
        return true;
    }

    private void applyExcludes(ConfigurableEnvironment environment, List<String> newExcludes) {
        // Read existing excludes to preserve them
        String existing = environment.getProperty(EXCLUDE_PROPERTY, "");
        List<String> allExcludes = new ArrayList<>();

        if (!existing.isEmpty()) {
            allExcludes.addAll(
                    Arrays.stream(existing.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList()));
        }

        // Add new excludes (avoiding duplicates)
        for (String exclude : newExcludes) {
            if (!allExcludes.contains(exclude)) {
                allExcludes.add(exclude);
            }
        }

        Map<String, Object> props = new HashMap<>();
        props.put(EXCLUDE_PROPERTY, String.join(",", allExcludes));

        environment.getPropertySources().addFirst(
                new MapPropertySource("fireflyEdaRabbitMqAutoConfigExcludes", props));
    }
}
