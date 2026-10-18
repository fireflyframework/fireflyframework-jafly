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

package org.fireflyframework.client.builder;

import org.fireflyframework.client.SoapClient;
import org.fireflyframework.client.exception.WsdlParsingException;
import org.fireflyframework.client.impl.SoapServiceClientImpl;
import org.fireflyframework.resilience.CircuitBreakerManager;
import lombok.extern.slf4j.Slf4j;

import javax.xml.namespace.QName;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for SOAP service clients with best-in-class developer experience.
 * 
 * <p>This builder simplifies SOAP client creation by providing:
 * <ul>
 *   <li>Automatic WSDL parsing and service discovery</li>
 *   <li>Fluent API for configuration</li>
 *   <li>Support for WS-Security and authentication</li>
 *   <li>MTOM/XOP attachment handling</li>
 *   <li>Reactive API over traditional SOAP</li>
 *   <li>Built-in resilience patterns</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Simple SOAP client from WSDL
 * ServiceClient client = ServiceClient.soap("weather-service")
 *     .wsdlUrl("http://www.webservicex.net/globalweather.asmx?WSDL")
 *     .build();
 *
 * // SOAP client with authentication
 * ServiceClient client = ServiceClient.soap("payment-service")
 *     .wsdlUrl("https://payment.example.com/service?wsdl")
 *     .username("api-user")
 *     .password("secret")
 *     .timeout(Duration.ofSeconds(30))
 *     .build();
 *
 * // SOAP client with custom namespace and service
 * ServiceClient client = ServiceClient.soap("custom-service")
 *     .wsdlUrl("http://example.com/service?wsdl")
 *     .serviceName(new QName("http://example.com/", "CustomService"))
 *     .portName(new QName("http://example.com/", "CustomPort"))
 *     .build();
 * }</pre>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class SoapClientBuilder {

    private final String serviceName;
    private String wsdlUrl;
    private QName serviceQName;
    private QName portQName;
    private Duration timeout = Duration.ofSeconds(30);
    private String username;
    private String password;
    private boolean mtomEnabled = false;
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, String> customHeaders = new HashMap<>();
    private CircuitBreakerManager circuitBreakerManager;
    private String endpointAddress;
    private boolean validateSchema = true;
    private String trustStorePath;
    private String trustStorePassword;
    private String keyStorePath;
    private String keyStorePassword;
    private boolean disableSslVerification = false;

    /**
     * Creates a new SOAP client builder.
     *
     * @param serviceName the logical service name for metrics and logging
     */
    public SoapClientBuilder(String serviceName) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be null or empty");
        }
        this.serviceName = serviceName.trim();
        log.debug("Created SOAP client builder for service '{}'", this.serviceName);
    }

    /**
     * Sets the WSDL URL for service discovery.
     *
     * <p>The WSDL will be parsed to discover available operations, message formats,
     * and endpoint addresses. This is the primary way to configure a SOAP client.
     *
     * <p>Supports WSDL URLs with authentication parameters:
     * <pre>{@code
     * // WSDL with embedded credentials
     * .wsdlUrl("https://secure.example.com/service.asmx?WSDL&user=username&password=pass")
     *
     * // Standard WSDL URL
     * .wsdlUrl("http://example.com/service?wsdl")
     * }</pre>
     *
     * <p>If the WSDL URL contains 'user' and 'password' query parameters, they will be
     * automatically extracted and used for authentication unless explicitly overridden.
     *
     * @param wsdlUrl the WSDL URL (can be HTTP, HTTPS, or file:// URL)
     * @return this builder
     */
    public SoapClientBuilder wsdlUrl(String wsdlUrl) {
        if (wsdlUrl == null || wsdlUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("WSDL URL cannot be null or empty");
        }
        this.wsdlUrl = wsdlUrl.trim();

        // Extract credentials from WSDL URL if present
        extractCredentialsFromWsdlUrl();

        return this;
    }

    /**
     * Sets the service QName for WSDL service selection.
     * 
     * <p>Use this when the WSDL defines multiple services and you need to
     * specify which one to use. If not set, the first service in the WSDL
     * will be used.
     *
     * @param serviceQName the service qualified name
     * @return this builder
     */
    public SoapClientBuilder serviceName(QName serviceQName) {
        this.serviceQName = serviceQName;
        return this;
    }

    /**
     * Sets the port QName for WSDL port selection.
     * 
     * <p>Use this when the service defines multiple ports and you need to
     * specify which one to use. If not set, the first port will be used.
     *
     * @param portQName the port qualified name
     * @return this builder
     */
    public SoapClientBuilder portName(QName portQName) {
        this.portQName = portQName;
        return this;
    }

    /**
     * Sets the request timeout.
     *
     * @param timeout the timeout duration
     * @return this builder
     */
    public SoapClientBuilder timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the username for WS-Security authentication.
     *
     * @param username the username
     * @return this builder
     */
    public SoapClientBuilder username(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets the password for WS-Security authentication.
     *
     * @param password the password
     * @return this builder
     */
    public SoapClientBuilder password(String password) {
        this.password = password;
        return this;
    }

    /**
     * Sets both username and password for authentication.
     *
     * @param username the username
     * @param password the password
     * @return this builder
     */
    public SoapClientBuilder credentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Enables MTOM (Message Transmission Optimization Mechanism) for efficient
     * binary data transfer.
     *
     * @return this builder
     */
    public SoapClientBuilder enableMtom() {
        this.mtomEnabled = true;
        return this;
    }

    /**
     * Disables MTOM (enabled by default for large payloads).
     *
     * @return this builder
     */
    public SoapClientBuilder disableMtom() {
        this.mtomEnabled = false;
        return this;
    }

    /**
     * Adds a custom SOAP property.
     *
     * @param key the property key
     * @param value the property value
     * @return this builder
     */
    public SoapClientBuilder property(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }

    /**
     * Adds a custom HTTP header to all requests.
     *
     * @param name the header name
     * @param value the header value
     * @return this builder
     */
    public SoapClientBuilder header(String name, String value) {
        this.customHeaders.put(name, value);
        return this;
    }

    /**
     * Sets the circuit breaker manager for resilience.
     *
     * @param circuitBreakerManager the circuit breaker manager
     * @return this builder
     */
    public SoapClientBuilder circuitBreakerManager(CircuitBreakerManager circuitBreakerManager) {
        this.circuitBreakerManager = circuitBreakerManager;
        return this;
    }

    /**
     * Overrides the endpoint address from the WSDL.
     * 
     * <p>Use this to point to a different environment (dev, staging, prod)
     * while using the same WSDL.
     *
     * @param endpointAddress the endpoint address
     * @return this builder
     */
    public SoapClientBuilder endpointAddress(String endpointAddress) {
        this.endpointAddress = endpointAddress;
        return this;
    }

    /**
     * Enables XML schema validation for requests and responses.
     *
     * @return this builder
     */
    public SoapClientBuilder enableSchemaValidation() {
        this.validateSchema = true;
        return this;
    }

    /**
     * Disables XML schema validation (useful for development).
     *
     * @return this builder
     */
    public SoapClientBuilder disableSchemaValidation() {
        this.validateSchema = false;
        return this;
    }

    /**
     * Sets the trust store for SSL/TLS connections.
     *
     * @param trustStorePath path to the trust store file
     * @param trustStorePassword trust store password
     * @return this builder
     */
    public SoapClientBuilder trustStore(String trustStorePath, String trustStorePassword) {
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    /**
     * Sets the key store for SSL/TLS client authentication.
     *
     * @param keyStorePath path to the key store file
     * @param keyStorePassword key store password
     * @return this builder
     */
    public SoapClientBuilder keyStore(String keyStorePath, String keyStorePassword) {
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    /**
     * Disables SSL certificate verification (NOT recommended for production).
     *
     * <p>Use this only for development/testing with self-signed certificates.
     *
     * @return this builder
     */
    public SoapClientBuilder disableSslVerification() {
        this.disableSslVerification = true;
        log.warn("SSL verification disabled - NOT recommended for production!");
        return this;
    }

    /**
     * Builds the SOAP service client.
     *
     * @return a configured SOAP service client
     * @throws WsdlParsingException if WSDL parsing fails
     */
    public SoapClient build() {
        validateConfiguration();

        log.info("Building SOAP service client for service '{}' with WSDL '{}'",
                serviceName, wsdlUrl);

        // Add SSL configuration to properties
        if (trustStorePath != null) {
            properties.put("ssl.trustStore.path", trustStorePath);
            properties.put("ssl.trustStore.password", trustStorePassword);
        }
        if (keyStorePath != null) {
            properties.put("ssl.keyStore.path", keyStorePath);
            properties.put("ssl.keyStore.password", keyStorePassword);
        }
        if (disableSslVerification) {
            properties.put("ssl.verification.disabled", "true");
        }

        return new SoapServiceClientImpl(
            serviceName,
            wsdlUrl,
            serviceQName,
            portQName,
            timeout,
            username,
            password,
            mtomEnabled,
            properties,
            customHeaders,
            circuitBreakerManager,
            endpointAddress,
            validateSchema
        );
    }

    /**
     * Validates the builder configuration.
     */
    private void validateConfiguration() {
        if (wsdlUrl == null || wsdlUrl.isEmpty()) {
            throw new IllegalStateException("WSDL URL must be set");
        }

        // Validate WSDL URL format
        try {
            java.net.URI.create(wsdlUrl).toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WSDL URL: " + wsdlUrl, e);
        }

        log.debug("SOAP client configuration validated for service '{}'", serviceName);
    }

    /**
     * Extracts username and password from WSDL URL query parameters.
     *
     * <p>Supports URLs like:
     * https://secure.example.com/service.asmx?WSDL&user=username&password=pass
     * https://api.example.com/soap?wsdl&username=user&pwd=secret
     *
     * <p>Common parameter names checked:
     * - user, username, userName, login
     * - password, pass, pwd, passwd
     */
    private void extractCredentialsFromWsdlUrl() {
        if (wsdlUrl == null || !wsdlUrl.contains("?")) {
            return;
        }

        try {
            String queryString = wsdlUrl.substring(wsdlUrl.indexOf('?') + 1);
            String[] params = queryString.split("&");

            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length != 2) {
                    continue;
                }

                String key = keyValue[0].toLowerCase();
                String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");

                // Check for username parameters
                if ((key.equals("user") || key.equals("username") ||
                     key.equals("username") || key.equals("login")) &&
                    this.username == null) {
                    this.username = value;
                    log.debug("Extracted username from WSDL URL for service '{}'", serviceName);
                }

                // Check for password parameters
                if ((key.equals("password") || key.equals("pass") ||
                     key.equals("pwd") || key.equals("passwd")) &&
                    this.password == null) {
                    this.password = value;
                    log.debug("Extracted password from WSDL URL for service '{}'", serviceName);
                }
            }

            // Clean WSDL URL by removing credentials for security
            if (this.username != null || this.password != null) {
                this.wsdlUrl = cleanWsdlUrl(wsdlUrl);
                log.debug("Cleaned credentials from WSDL URL for service '{}'", serviceName);
            }

        } catch (Exception e) {
            log.warn("Failed to extract credentials from WSDL URL: {}", e.getMessage());
        }
    }

    /**
     * Removes sensitive credentials from WSDL URL for logging and security.
     */
    private String cleanWsdlUrl(String url) {
        if (url == null || !url.contains("?")) {
            return url;
        }

        try {
            String baseUrl = url.substring(0, url.indexOf('?'));
            String queryString = url.substring(url.indexOf('?') + 1);
            String[] params = queryString.split("&");

            StringBuilder cleanQuery = new StringBuilder();
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length != 2) {
                    continue;
                }

                String key = keyValue[0].toLowerCase();

                // Skip credential parameters but keep WSDL and other parameters
                if (key.equals("user") || key.equals("username") ||
                    key.equals("login") || key.equals("password") ||
                    key.equals("pass") || key.equals("pwd") || key.equals("passwd") ||
                    key.equals("username")) {
                    continue;
                }

                if (cleanQuery.length() > 0) {
                    cleanQuery.append("&");
                }
                cleanQuery.append(param);
            }

            // Always preserve the base URL structure
            if (cleanQuery.length() > 0) {
                return baseUrl + "?" + cleanQuery;
            } else {
                // If all params were credentials, check if original had WSDL
                if (url.toLowerCase().contains("wsdl")) {
                    return baseUrl + "?WSDL";
                }
                return baseUrl;
            }

        } catch (Exception e) {
            log.warn("Failed to clean WSDL URL: {}", e.getMessage());
            return url;
        }
    }
}

