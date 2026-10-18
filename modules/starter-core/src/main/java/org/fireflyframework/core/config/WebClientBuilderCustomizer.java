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

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Customizer for WebClient.Builder that applies advanced configuration options.
 * <p>
 * This class applies the following configurations to WebClient.Builder:
 * <ul>
 *   <li>SSL/TLS settings</li>
 *   <li>Proxy settings</li>
 *   <li>Connection pooling</li>
 *   <li>HTTP/2 support</li>
 *   <li>Timeout settings</li>
 *   <li>Codec configuration</li>
 * </ul>
 * <p>
 * The configurations are applied based on the properties defined in {@link WebClientProperties}.
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firefly.webclient.enabled", havingValue = "true", matchIfMissing = true)
public class WebClientBuilderCustomizer {

    private final WebClientProperties properties;

    /**
     * Customizes a WebClient.Builder with advanced configuration options.
     *
     * @param builder the WebClient.Builder to customize
     * @return the customized WebClient.Builder
     */
    public WebClient.Builder customize(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create();

        // Apply connection pooling if enabled
        if (properties.getConnectionPool().isEnabled()) {
            httpClient = HttpClient.create(createConnectionProvider());
        }

        // Apply timeout settings
        httpClient = httpClient
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs())
                .responseTimeout(properties.getReadTimeout());

        // Apply SSL/TLS settings if enabled
        if (properties.getSsl().isEnabled()) {
            try {
                httpClient = httpClient.secure(configureSsl());
            } catch (Exception e) {
                log.error("Failed to configure SSL for WebClient", e);
            }
        }

        // Apply proxy settings if enabled
        if (properties.getProxy().isEnabled()) {
            httpClient = httpClient.proxy(configureProxy());
        }

        // Apply HTTP/2 settings if enabled
        if (properties.getHttp2().isEnabled()) {
            httpClient = httpClient.protocol(configureHttp2());
        }

        // Create and set the client HTTP connector
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        builder.clientConnector(connector);

        // Apply codec configuration if enabled
        if (properties.getCodec().isEnabled()) {
            builder.codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(properties.getCodec().getMaxInMemorySize());
                if (properties.getCodec().isEnableLoggingFormData()) {
                    configurer.defaultCodecs().enableLoggingRequestDetails(true);
                }
            });
        }

        return builder;
    }

    /**
     * Creates a connection provider based on the connection pool properties.
     *
     * @return the connection provider
     */
    private ConnectionProvider createConnectionProvider() {
        WebClientProperties.ConnectionPool pool = properties.getConnectionPool();

        return ConnectionProvider.builder("webclient-connection-pool")
                .maxConnections(pool.getMaxConnections())
                .pendingAcquireMaxCount(pool.getMaxPendingAcquires())
                .maxIdleTime(Duration.ofMillis(pool.getMaxIdleTimeMs()))
                .maxLifeTime(Duration.ofMillis(pool.getMaxLifeTimeMs()))
                .metrics(pool.isMetricsEnabled())
                .build();
    }

    /**
     * Configures SSL/TLS settings.
     *
     * @return a consumer that configures SSL/TLS settings
     * @throws Exception if an error occurs while configuring SSL/TLS
     */
    private Consumer<reactor.netty.tcp.SslProvider.SslContextSpec> configureSsl() throws Exception {
        WebClientProperties.Ssl ssl = properties.getSsl();

        if (ssl.isUseDefaultSslContext()) {
            return sslContextSpec -> sslContextSpec.sslContext(createDefaultSslContext());
        } else {
            return sslContextSpec -> {
                try {
                    sslContextSpec.sslContext(createCustomSslContext());
                } catch (Exception e) {
                    log.error("Failed to create custom SSL context", e);
                    throw new RuntimeException("Failed to create custom SSL context", e);
                }
            };
        }
    }

    /**
     * Creates a default SSL context.
     *
     * @return the default SSL context
     */
    private SslContext createDefaultSslContext() {
        try {
            return SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create default SSL context", e);
            throw new RuntimeException("Failed to create default SSL context", e);
        }
    }

    /**
     * Creates a custom SSL context based on the SSL properties.
     *
     * @return the custom SSL context
     * @throws KeyStoreException if an error occurs with the key store
     * @throws IOException if an I/O error occurs
     * @throws CertificateException if an error occurs with the certificate
     * @throws NoSuchAlgorithmException if the algorithm is not available
     * @throws UnrecoverableKeyException if the key cannot be recovered
     */
    private SslContext createCustomSslContext() throws KeyStoreException, IOException, CertificateException,
            NoSuchAlgorithmException, UnrecoverableKeyException {
        WebClientProperties.Ssl ssl = properties.getSsl();
        SslContextBuilder builder = SslContextBuilder.forClient();

        // Configure trust store if provided
        if (!ssl.getTrustStorePath().isEmpty()) {
            KeyStore trustStore = KeyStore.getInstance(ssl.getTrustStoreType());
            try (FileInputStream trustStoreFile = new FileInputStream(ssl.getTrustStorePath())) {
                trustStore.load(trustStoreFile, ssl.getTrustStorePassword().toCharArray());
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            builder.trustManager(trustManagerFactory);
        }

        // Configure key store if provided
        if (!ssl.getKeyStorePath().isEmpty()) {
            KeyStore keyStore = KeyStore.getInstance(ssl.getKeyStoreType());
            try (FileInputStream keyStoreFile = new FileInputStream(ssl.getKeyStorePath())) {
                keyStore.load(keyStoreFile, ssl.getKeyStorePassword().toCharArray());
            }
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, ssl.getKeyStorePassword().toCharArray());
            builder.keyManager(keyManagerFactory);
        }

        // Configure protocols if provided
        if (ssl.getEnabledProtocols() != null && !ssl.getEnabledProtocols().isEmpty()) {
            builder.protocols(ssl.getEnabledProtocols().toArray(new String[0]));
        }

        // Configure cipher suites if provided
        if (ssl.getEnabledCipherSuites() != null && !ssl.getEnabledCipherSuites().isEmpty()) {
            builder.ciphers(ssl.getEnabledCipherSuites());
        }

        return builder.build();
    }

    /**
     * Configures proxy settings.
     *
     * @return a consumer that configures proxy settings
     */
    private Consumer<ProxyProvider.TypeSpec> configureProxy() {
        WebClientProperties.Proxy proxy = properties.getProxy();

        return typeSpec -> {
            ProxyProvider.Proxy proxyType = ProxyProvider.Proxy.valueOf(proxy.getType());

            // Configure proxy with host and port
            ProxyProvider.AddressSpec addressSpec = typeSpec.type(proxyType);
            addressSpec.host(proxy.getHost())
                      .port(proxy.getPort());
        };
    }

    /**
     * Configures HTTP/2 settings.
     *
     * @return the HTTP protocol configuration
     */
    private reactor.netty.http.HttpProtocol[] configureHttp2() {
        return new reactor.netty.http.HttpProtocol[] {
                reactor.netty.http.HttpProtocol.HTTP11,
                reactor.netty.http.HttpProtocol.H2
        };
    }
}
