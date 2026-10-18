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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for Spring Boot Actuator.
 * <p>
 * This class provides configuration properties for customizing Spring Boot Actuator
 * behavior in the application. It allows for fine-grained control over which endpoints
 * are enabled and exposed, as well as configuration for health checks and metrics.
 */
@Validated
@ConfigurationProperties(prefix = "firefly.actuator")
@Getter
@Setter
public class ActuatorProperties {

    /**
     * Endpoints configuration.
     */
    private final Endpoints endpoints = new Endpoints();

    /**
     * Metrics configuration.
     */
    private final Metrics metrics = new Metrics();

    /**
     * Tracing configuration.
     */
    private final Tracing tracing = new Tracing();

    /**
     * Health configuration.
     */
    private final Health health = new Health();

    /**
     * Extended metrics configuration.
     */
    private final ExtendedMetrics extendedMetrics = new ExtendedMetrics();

    /**
     * Endpoints configuration properties.
     */
    @Getter
    @Setter
    public static class Endpoints {
        /**
         * Whether to enable endpoints.
         */
        private boolean enabled = true;

        /**
         * Web exposure configuration.
         */
        private final Web web = new Web();

        /**
         * JMX exposure configuration.
         */
        private final Jmx jmx = new Jmx();

        /**
         * Web exposure configuration properties.
         */
        @Getter
        @Setter
        public static class Web {
            /**
             * Endpoints to expose. Use '*' for all endpoints.
             */
            private String exposure = "*";

            /**
             * Base path for endpoints.
             */
            private String basePath = "/actuator";

            /**
             * Whether to include details in responses.
             */
            private boolean includeDetails = true;
        }

        /**
         * JMX exposure configuration properties.
         */
        @Getter
        @Setter
        public static class Jmx {
            /**
             * Endpoints to expose. Use '*' for all endpoints.
             */
            private String exposure = "*";

            /**
             * Domain for JMX endpoints.
             */
            private String domain = "org.springframework.boot";
        }
    }

    /**
     * Metrics configuration properties.
     */
    @Getter
    @Setter
    public static class Metrics {
        /**
         * Whether to enable metrics.
         */
        private boolean enabled = true;

        /**
         * Tags to add to all metrics.
         */
        private final Map<String, String> tags = new HashMap<>();

        /**
         * Prometheus configuration.
         */
        private final Prometheus prometheus = new Prometheus();

        /**
         * Prometheus configuration properties.
         */
        @Getter
        @Setter
        public static class Prometheus {
            /**
             * Whether to enable Prometheus metrics.
             */
            private boolean enabled = true;

            /**
             * Path for Prometheus metrics endpoint.
             */
            private String path = "/actuator/prometheus";
        }
    }

    /**
     * Tracing configuration properties.
     */
    @Getter
    @Setter
    public static class Tracing {
        /**
         * Whether to enable tracing.
         */
        private boolean enabled = true;

        /**
         * Sampling configuration.
         */
        private final Sampling sampling = new Sampling();

        /**
         * Zipkin configuration.
         */
        private final Zipkin zipkin = new Zipkin();

        /**
         * Propagation configuration.
         */
        private final Propagation propagation = new Propagation();

        /**
         * Sampling configuration properties.
         */
        @Getter
        @Setter
        public static class Sampling {
            /**
             * Probability for sampling traces (0.0 - 1.0).
             */
            private double probability = 0.1;
        }

        /**
         * Zipkin configuration properties.
         */
        @Getter
        @Setter
        public static class Zipkin {
            /**
             * Whether to enable Zipkin tracing.
             */
            private boolean enabled = false;

            /**
             * Base URL for Zipkin server.
             */
            private String baseUrl = "http://localhost:9411";

            /**
             * Service name for Zipkin traces.
             */
            private String serviceName = "${spring.application.name:application}";
        }

        /**
         * Propagation configuration properties.
         */
        @Getter
        @Setter
        public static class Propagation {
            /**
             * Propagation types to use (B3, W3C, etc.).
             */
            private List<String> type = new ArrayList<>(List.of("B3", "W3C"));
        }
    }

    /**
     * Health configuration properties.
     */
    @Getter
    @Setter
    public static class Health {
        /**
         * Whether to show details in health endpoint.
         */
        private String showDetails = "always";

        /**
         * Whether to show components in health endpoint.
         */
        private boolean showComponents = true;

        /**
         * Disk space configuration.
         */
        private final DiskSpace diskSpace = new DiskSpace();

        /**
         * Disk space configuration properties.
         */
        @Getter
        @Setter
        public static class DiskSpace {
            /**
             * Whether to enable disk space health check.
             */
            private boolean enabled = true;

            /**
             * Threshold for disk space health check.
             */
            private String threshold = "10MB";

            /**
             * Path to check for disk space.
             */
            private String path = ".";
        }
    }

    /**
     * Extended metrics configuration properties.
     */
    @Getter
    @Setter
    public static class ExtendedMetrics {
        /**
         * JVM metrics configuration.
         */
        private final Jvm jvm = new Jvm();

        /**
         * Database metrics configuration.
         */
        private final Database database = new Database();

        /**
         * Thread pool metrics configuration.
         */
        private final ThreadPool threadpool = new ThreadPool();

        /**
         * HTTP client metrics configuration.
         */
        private final HttpClient httpClient = new HttpClient();

        /**
         * Cache metrics configuration.
         */
        private final Cache cache = new Cache();

        /**
         * Application metrics configuration.
         */
        private final Application application = new Application();

        /**
         * JVM metrics configuration properties.
         */
        @Getter
        @Setter
        public static class Jvm {
            /**
             * Whether to enable JVM metrics.
             */
            private boolean enabled = true;

            /**
             * Whether to enable memory metrics.
             */
            private boolean memory = true;

            /**
             * Whether to enable GC metrics.
             */
            private boolean gc = true;

            /**
             * Whether to enable thread metrics.
             */
            private boolean threads = true;

            /**
             * Whether to enable class loader metrics.
             */
            private boolean classloader = true;
        }

        /**
         * Database metrics configuration properties.
         */
        @Getter
        @Setter
        public static class Database {
            /**
             * Whether to enable database health indicators.
             */
            private boolean enabled = true;

            /**
             * Connection timeout for health checks (in seconds).
             */
            private int connectionTimeout = 5;

            /**
             * Whether to include connection pool metrics.
             */
            private boolean includePoolMetrics = true;
        }

        /**
         * Thread pool metrics configuration properties.
         */
        @Getter
        @Setter
        public static class ThreadPool {
            /**
             * Whether to enable thread pool metrics.
             */
            private boolean enabled = true;

            /**
             * Whether to include executor service metrics.
             */
            private boolean includeExecutorServices = true;

            /**
             * Whether to include ForkJoinPool metrics.
             */
            private boolean includeForkJoinPool = true;
        }

        /**
         * HTTP client metrics configuration properties.
         */
        @Getter
        @Setter
        public static class HttpClient {
            /**
             * Whether to enable HTTP client metrics.
             */
            private boolean enabled = true;

            /**
             * Whether to perform connectivity tests.
             */
            private boolean connectivityTest = false;

            /**
             * Timeout for connectivity tests (in seconds).
             */
            private int connectivityTimeout = 5;
        }

        /**
         * Cache metrics configuration properties.
         */
        @Getter
        @Setter
        public static class Cache {
            /**
             * Whether to enable cache health indicators.
             */
            private boolean enabled = true;

            /**
             * Whether to perform operational tests on caches.
             */
            private boolean operationalTest = true;

            /**
             * Whether to include cache-specific metrics.
             */
            private boolean includeSpecificMetrics = true;
        }

        /**
         * Application metrics configuration properties.
         */
        @Getter
        @Setter
        public static class Application {
            /**
             * Whether to enable application metrics.
             */
            private boolean enabled = true;

            /**
             * Whether to track startup phases.
             */
            private boolean startupPhases = true;

            /**
             * Whether to include enhanced info contributor.
             */
            private boolean enhancedInfo = true;
        }
    }
}
