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


package org.fireflyframework.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties class for WebClient.
 *
 * This class is designed to hold properties related to the
 * configuration of WebClient functionality. It is annotated with
 * `@ConfigurationProperties` to enable externalized configuration
 * using a specified prefix (`webclient`). The properties can be
 * defined in external configuration files such as application.yml
 * or application.properties.
 *
 * Key configuration options include:
 * - Header management (skipHeaders)
 * - Timeout settings (connectTimeout, readTimeout, writeTimeout)
 * - SSL/TLS configuration
 * - Proxy settings
 * - Connection pooling
 * - HTTP/2 support
 * - Codec configuration
 *
 * Annotations:
 * - `@Configuration`: Marks this class as a source of bean definitions.
 * - `@ConfigurationProperties`: Indicates the property prefix (`webclient`)
 *   for externalized configuration mapping.
 * - `@Getter`, `@Setter`: Lombok annotations to automatically generate
 *   getter and setter methods for the fields.
 */
@Validated
@ConfigurationProperties(prefix = "firefly.webclient")
@Getter
@Setter
public class WebClientProperties {
    /**
     * Whether to enable WebClient auto-configuration.
     */
    private boolean enabled = true;

    /**
     * Headers that should not be propagated.
     */
    private List<String> skipHeaders;

    /**
     * Connection timeout in milliseconds.
     */
    private int connectTimeoutMs = 5000;

    /**
     * Read timeout in milliseconds.
     */
    private int readTimeoutMs = 10000;

    /**
     * Write timeout in milliseconds.
     */
    private int writeTimeoutMs = 10000;

    /**
     * Maximum size of in-memory buffer in bytes.
     */
    private int maxInMemorySize = 16777216; // 16MB

    /**
     * SSL/TLS configuration.
     */
    private final Ssl ssl = new Ssl();

    /**
     * Proxy configuration.
     */
    private final Proxy proxy = new Proxy();

    /**
     * Connection pool configuration.
     */
    private final ConnectionPool connectionPool = new ConnectionPool();

    /**
     * HTTP/2 configuration.
     */
    private final Http2 http2 = new Http2();

    /**
     * Codec configuration.
     */
    private final Codec codec = new Codec();

    /**
     * SSL/TLS configuration properties.
     */
    @Getter
    @Setter
    public static class Ssl {
        /**
         * Whether to enable SSL/TLS.
         */
        private boolean enabled = false;

        /**
         * Whether to use the default SSL context.
         */
        private boolean useDefaultSslContext = true;

        /**
         * Trust store path.
         */
        private String trustStorePath = "";

        /**
         * Trust store password.
         */
        private String trustStorePassword = "";

        /**
         * Trust store type (JKS, PKCS12, etc.).
         */
        private String trustStoreType = "JKS";

        /**
         * Key store path.
         */
        private String keyStorePath = "";

        /**
         * Key store password.
         */
        private String keyStorePassword = "";

        /**
         * Key store type (JKS, PKCS12, etc.).
         */
        private String keyStoreType = "JKS";

        /**
         * Whether to verify hostname.
         */
        private boolean verifyHostname = true;

        /**
         * Protocols to enable (TLSv1.2, TLSv1.3, etc.).
         */
        private List<String> enabledProtocols;

        /**
         * Cipher suites to enable.
         */
        private List<String> enabledCipherSuites;
    }

    /**
     * Proxy configuration properties.
     */
    @Getter
    @Setter
    public static class Proxy {
        /**
         * Whether to enable proxy.
         */
        private boolean enabled = false;

        /**
         * Proxy type (HTTP, SOCKS4, SOCKS5).
         */
        private String type = "HTTP";

        /**
         * Proxy host.
         */
        private String host = "";

        /**
         * Proxy port.
         */
        private int port = 8080;

        /**
         * Proxy username.
         */
        private String username = "";

        /**
         * Proxy password.
         */
        private String password = "";

        /**
         * Non-proxy hosts (hosts that should bypass the proxy).
         */
        private List<String> nonProxyHosts;
    }

    /**
     * Connection pool configuration properties.
     */
    @Getter
    @Setter
    public static class ConnectionPool {
        /**
         * Whether to enable connection pooling.
         */
        private boolean enabled = true;

        /**
         * Maximum number of connections.
         */
        private int maxConnections = 500;

        /**
         * Maximum number of pending acquires.
         */
        private int maxPendingAcquires = 1000;

        /**
         * Maximum idle time in milliseconds.
         */
        private long maxIdleTimeMs = 30000;

        /**
         * Maximum life time in milliseconds.
         */
        private long maxLifeTimeMs = 60000;

        /**
         * Whether to enable metrics.
         */
        private boolean metricsEnabled = true;
    }

    /**
     * HTTP/2 configuration properties.
     */
    @Getter
    @Setter
    public static class Http2 {
        /**
         * Whether to enable HTTP/2.
         */
        private boolean enabled = true;

        /**
         * Maximum concurrent streams.
         */
        private int maxConcurrentStreams = 100;

        /**
         * Initial window size.
         */
        private int initialWindowSize = 1048576; // 1MB
    }

    /**
     * Codec configuration properties.
     */
    @Getter
    @Setter
    public static class Codec {
        /**
         * Whether to enable custom codec configuration.
         */
        private boolean enabled = false;

        /**
         * Maximum in memory size for codecs.
         */
        private int maxInMemorySize = 16777216; // 16MB

        /**
         * Whether to enable logging of form data.
         */
        private boolean enableLoggingFormData = false;

        /**
         * Jackson configuration properties.
         */
        private final Map<String, String> jacksonProperties = new HashMap<>();
    }

    /**
     * Get connection timeout as Duration.
     *
     * @return connection timeout as Duration
     */
    public Duration getConnectTimeout() {
        return Duration.ofMillis(connectTimeoutMs);
    }

    /**
     * Get read timeout as Duration.
     *
     * @return read timeout as Duration
     */
    public Duration getReadTimeout() {
        return Duration.ofMillis(readTimeoutMs);
    }

    /**
     * Get write timeout as Duration.
     *
     * @return write timeout as Duration
     */
    public Duration getWriteTimeout() {
        return Duration.ofMillis(writeTimeoutMs);
    }
}
