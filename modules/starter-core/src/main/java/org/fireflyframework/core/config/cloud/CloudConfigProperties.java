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


package org.fireflyframework.core.config.cloud;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Spring Cloud Config.
 * <p>
 * This class defines properties for configuring the Spring Cloud Config client,
 * which enables centralized configuration management for microservices.
 * <p>
 * Properties can be configured in application.yml or application.properties using
 * the prefix "cloud.config".
 */
@Validated
@ConfigurationProperties(prefix = "firefly.cloud.config")
@Getter
@Setter
public class CloudConfigProperties {

    /**
     * Whether to enable Spring Cloud Config client.
     * Disabled by default.
     */
    private boolean enabled = false;

    /**
     * URI of the Spring Cloud Config server.
     */
    private String uri = "http://localhost:8888";

    /**
     * Name of the application to retrieve configuration for.
     * Defaults to the value of spring.application.name if not specified.
     */
    private String name = "";

    /**
     * Profile to use for configuration retrieval.
     * Defaults to the active profiles if not specified.
     */
    private String profile = "";

    /**
     * Label to use for configuration retrieval (e.g., Git branch).
     */
    private String label = "main";

    /**
     * Whether to fail startup if unable to connect to the config server.
     */
    private boolean failFast = false;

    /**
     * Timeout in milliseconds for config server requests.
     */
    private int timeoutMs = 5000;

    /**
     * Whether to retry failed requests to the config server.
     */
    private boolean retry = true;

    /**
     * Maximum number of retries for failed requests.
     */
    private int maxRetries = 6;

    /**
     * Initial retry interval in milliseconds.
     */
    private int initialRetryIntervalMs = 1000;

    /**
     * Maximum retry interval in milliseconds.
     */
    private int maxRetryIntervalMs = 2000;

    /**
     * Multiplier for the retry interval.
     */
    private double retryMultiplier = 1.1;

    /**
     * Whether to enable dynamic refresh of configuration.
     */
    private boolean refreshEnabled = true;
}
