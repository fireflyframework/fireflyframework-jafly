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

package org.fireflyframework.webhooks.core.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Liveness health indicator for Kubernetes liveness probes.
 * <p>
 * This indicator checks if the application is alive and should be restarted if not.
 * It only checks basic application health, not external dependencies.
 * <p>
 * Use this for Kubernetes liveness probes:
 * <pre>
 * livenessProbe:
 *   httpGet:
 *     path: /actuator/health/liveness
 *     port: 8080
 *   initialDelaySeconds: 30
 *   periodSeconds: 10
 * </pre>
 */
@Component("webhookLiveness")
public class WebhookPlatformLivenessIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // Liveness check: just verify the application is running
        // Don't check external dependencies (Kafka, Redis) here
        // If those are down, we don't want to restart the pod
        
        return Health.up()
                .withDetail("status", "Application is alive")
                .build();
    }
}

