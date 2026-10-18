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

package org.fireflyframework.webhooks.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for webhook batching/buffering.
 * <p>
 * Batching improves throughput by grouping multiple webhook events
 * and publishing them together to Kafka, reducing network overhead.
 * <p>
 * Environment variables:
 * <pre>
 * FIREFLY_WEBHOOKS_BATCHING_ENABLED=true
 * FIREFLY_WEBHOOKS_BATCHING_MAX_BATCH_SIZE=100
 * FIREFLY_WEBHOOKS_BATCHING_MAX_WAIT_TIME=PT1S
 * FIREFLY_WEBHOOKS_BATCHING_BUFFER_SIZE=1000
 * </pre>
 */
@Configuration
@Validated
@ConfigurationProperties(prefix = "firefly.webhooks.batching")
@Data
public class BatchingProperties {

    /**
     * Enable webhook batching
     */
    private boolean enabled = false;

    /**
     * Maximum number of events in a batch
     */
    private int maxBatchSize = 100;

    /**
     * Maximum time to wait before flushing a batch
     */
    private Duration maxWaitTime = Duration.ofSeconds(1);

    /**
     * Size of the internal buffer for pending events
     */
    private int bufferSize = 1000;

    /**
     * Per-provider batching configuration overrides
     */
    private Map<String, BatchConfig> providers = new HashMap<>();

    /**
     * Get batching configuration for a specific provider.
     * Falls back to defaults if no provider-specific config exists.
     *
     * @param providerName the provider name
     * @return the batch configuration
     */
    public BatchConfig getConfigForProvider(String providerName) {
        return providers.getOrDefault(providerName, createDefaultConfig());
    }

    private BatchConfig createDefaultConfig() {
        BatchConfig config = new BatchConfig();
        config.setMaxBatchSize(maxBatchSize);
        config.setMaxWaitTime(maxWaitTime);
        config.setBufferSize(bufferSize);
        return config;
    }

    /**
     * Per-provider batch configuration
     */
    @Data
    public static class BatchConfig {
        private int maxBatchSize = 100;
        private Duration maxWaitTime = Duration.ofSeconds(1);
        private int bufferSize = 1000;
    }
}

