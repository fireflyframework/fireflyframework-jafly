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

package org.fireflyframework.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import org.fireflyframework.client.ClientType;
import org.fireflyframework.client.SoapClient;
import org.fireflyframework.client.exception.ServiceClientException;
import org.fireflyframework.client.exception.SoapFaultException;
import org.fireflyframework.client.exception.WsdlParsingException;
import org.fireflyframework.resilience.CircuitBreakerManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.endpoint.dynamic.JaxWsDynamicClientFactory;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.handler.WSHandlerConstants;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.io.FileInputStream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Dispatch;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.soap.SOAPBinding;
import jakarta.xml.ws.soap.SOAPFaultException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOAP implementation of ServiceClient using Apache CXF and JAX-WS.
 * 
 * <p>This implementation provides a modern reactive API over traditional SOAP services
 * with the following features:
 * <ul>
 *   <li>Automatic WSDL parsing and service discovery</li>
 *   <li>Dynamic operation invocation without generated stubs</li>
 *   <li>WS-Security username token authentication</li>
 *   <li>MTOM/XOP for efficient binary transfers</li>
 *   <li>Circuit breaker and retry support</li>
 *   <li>Reactive Mono/Flux API</li>
 *   <li>Comprehensive error handling and SOAP fault mapping</li>
 * </ul>
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@Slf4j
public class SoapServiceClientImpl implements SoapClient {

    private final String serviceName;
    private final String wsdlUrl;
    private final QName serviceQName;
    private final QName portQName;
    private final Duration timeout;
    private final String username;
    private final String password;
    private final boolean mtomEnabled;
    private final Map<String, Object> properties;
    private final Map<String, String> customHeaders;
    private final CircuitBreakerManager circuitBreakerManager;
    private final String endpointAddress;
    private final boolean validateSchema;
    private final boolean enableMessageLogging;

    private Client dynamicClient;
    private volatile boolean initialized = false;

    /**
     * Creates a new SOAP service client implementation.
     */
    public SoapServiceClientImpl(
            String serviceName,
            String wsdlUrl,
            QName serviceQName,
            QName portQName,
            Duration timeout,
            String username,
            String password,
            boolean mtomEnabled,
            Map<String, Object> properties,
            Map<String, String> customHeaders,
            CircuitBreakerManager circuitBreakerManager,
            String endpointAddress,
            boolean validateSchema) {

        this.serviceName = serviceName;
        this.wsdlUrl = wsdlUrl;
        this.serviceQName = serviceQName;
        this.portQName = portQName;
        this.timeout = timeout;
        this.username = username;
        this.password = password;
        this.mtomEnabled = mtomEnabled;
        this.properties = new HashMap<>(properties);
        this.customHeaders = new HashMap<>(customHeaders);
        this.circuitBreakerManager = circuitBreakerManager;
        this.endpointAddress = endpointAddress;
        this.validateSchema = validateSchema;
        this.enableMessageLogging = Boolean.parseBoolean(
            System.getProperty("soap.message.logging.enabled", "false"));

        initializeService();
    }

    /**
     * Initializes the SOAP service from WSDL using Apache CXF Dynamic Client.
     */
    private void initializeService() {
        try {
            log.info("Initializing SOAP service '{}' from WSDL: {}", serviceName, wsdlUrl);

            // Use Apache CXF Dynamic Client Factory for dynamic invocation
            JaxWsDynamicClientFactory factory = JaxWsDynamicClientFactory.newInstance();
            dynamicClient = factory.createClient(wsdlUrl);

            // Configure the client
            configureClient();

            initialized = true;
            log.info("SOAP service '{}' initialized successfully", serviceName);

        } catch (Exception e) {
            log.error("Failed to initialize SOAP service '{}': {}", serviceName, e.getMessage(), e);
            throw new WsdlParsingException("Failed to initialize SOAP service from WSDL: " + wsdlUrl, e);
        }
    }

    /**
     * Configures the dynamic client with timeout, authentication, and other settings.
     */
    private void configureClient() {
        // Set endpoint address if overridden
        if (endpointAddress != null && !endpointAddress.isEmpty()) {
            dynamicClient.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointAddress);
            log.debug("Overriding endpoint address to: {}", endpointAddress);
        }

        // Configure timeout
        dynamicClient.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", timeout.toMillis());
        dynamicClient.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", timeout.toMillis());

        // Add custom properties
        dynamicClient.getRequestContext().putAll(properties);

        // Configure WS-Security if credentials provided
        if (username != null && password != null) {
            configureWsSecurity();
        }

        // Configure MTOM if enabled
        if (mtomEnabled) {
            dynamicClient.getRequestContext().put("jakarta.xml.ws.soap.http.soapaction.use", true);
            dynamicClient.getRequestContext().put("jakarta.xml.ws.soap.http.soapaction.uri", "");
        }

        // Configure HTTP conduit for additional settings
        configureHttpConduit();
    }

    /**
     * Configures WS-Security username token authentication.
     */
    private void configureWsSecurity() {
        try {
            Map<String, Object> outProps = new HashMap<>();
            outProps.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
            outProps.put(WSHandlerConstants.USER, username);
            outProps.put(WSHandlerConstants.PASSWORD_TYPE, WSConstants.PW_TEXT);
            outProps.put(WSHandlerConstants.PW_CALLBACK_REF,
                (javax.security.auth.callback.CallbackHandler) callbacks -> {
                    for (javax.security.auth.callback.Callback callback : callbacks) {
                        if (callback instanceof org.apache.wss4j.common.ext.WSPasswordCallback) {
                            org.apache.wss4j.common.ext.WSPasswordCallback pc =
                                (org.apache.wss4j.common.ext.WSPasswordCallback) callback;
                            pc.setPassword(password);
                        }
                    }
                });

            WSS4JOutInterceptor wssOut = new WSS4JOutInterceptor(outProps);
            dynamicClient.getOutInterceptors().add(wssOut);

            log.debug("WS-Security configured for user: {}", username);
        } catch (Exception e) {
            log.warn("Failed to configure WS-Security: {}", e.getMessage());
        }
    }

    /**
     * Configures HTTP conduit for connection pooling and custom headers.
     */
    private void configureHttpConduit() {
        try {
            HTTPConduit httpConduit = (HTTPConduit) dynamicClient.getConduit();

            HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
            httpClientPolicy.setConnectionTimeout(timeout.toMillis());
            httpClientPolicy.setReceiveTimeout(timeout.toMillis());
            httpClientPolicy.setAllowChunking(false);

            // Force HTTP/1.1 for better compatibility
            httpClientPolicy.setVersion("1.1");

            // Connection pooling settings
            httpClientPolicy.setMaxRetransmits(3);
            httpClientPolicy.setAutoRedirect(true);

            // Connection reuse and keep-alive
            httpClientPolicy.setConnection(org.apache.cxf.transports.http.configuration.ConnectionType.KEEP_ALIVE);

            // Browser compatibility mode for better interoperability
            httpClientPolicy.setBrowserType("Mozilla/5.0");

            httpConduit.setClient(httpClientPolicy);

            // Add custom headers using CXF 4.x API
            if (!customHeaders.isEmpty()) {
                customHeaders.forEach((key, value) ->
                    dynamicClient.getRequestContext().put(org.apache.cxf.message.Message.PROTOCOL_HEADERS + "." + key,
                                                   java.util.Collections.singletonList(value)));
            }

            log.debug("HTTP conduit configured with connection pooling for service '{}'", serviceName);

            // Add logging interceptors if enabled
            if (enableMessageLogging) {
                configureMessageLogging();
            }

            // Configure SSL/TLS if needed
            configureSsl(httpConduit);

        } catch (Exception e) {
            log.warn("Failed to configure HTTP conduit: {}", e.getMessage());
        }
    }

    /**
     * Configures SOAP message logging interceptors for debugging.
     */
    @SuppressWarnings("deprecation")
    private void configureMessageLogging() {
        try {
            // Add logging interceptors for request/response
            LoggingInInterceptor loggingIn = new LoggingInInterceptor();
            loggingIn.setPrettyLogging(true);

            LoggingOutInterceptor loggingOut = new LoggingOutInterceptor();
            loggingOut.setPrettyLogging(true);

            dynamicClient.getInInterceptors().add(loggingIn);
            dynamicClient.getOutInterceptors().add(loggingOut);

            log.info("SOAP message logging enabled for service '{}'", serviceName);

        } catch (Exception e) {
            log.warn("Failed to configure message logging: {}", e.getMessage());
        }
    }

    /**
     * Configures SSL/TLS settings for HTTPS connections.
     */
    private void configureSsl(HTTPConduit httpConduit) {
        String trustStorePath = (String) properties.get("ssl.trustStore.path");
        String trustStorePassword = (String) properties.get("ssl.trustStore.password");
        String keyStorePath = (String) properties.get("ssl.keyStore.path");
        String keyStorePassword = (String) properties.get("ssl.keyStore.password");
        boolean disableSslVerification = "true".equals(properties.get("ssl.verification.disabled"));

        if (trustStorePath == null && keyStorePath == null && !disableSslVerification) {
            return; // No SSL configuration needed
        }

        try {
            TLSClientParameters tlsParams = new TLSClientParameters();

            // Disable SSL verification if requested (NOT recommended for production)
            if (disableSslVerification) {
                tlsParams.setTrustManagers(new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
                });
                tlsParams.setDisableCNCheck(true);
                log.warn("SSL verification disabled for service '{}' - NOT recommended for production!",
                        serviceName);
            } else {
                // Configure trust store
                if (trustStorePath != null) {
                    KeyStore trustStore = KeyStore.getInstance("JKS");
                    try (FileInputStream fis = new FileInputStream(trustStorePath)) {
                        trustStore.load(fis, trustStorePassword != null ?
                                       trustStorePassword.toCharArray() : null);
                    }

                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                    tlsParams.setTrustManagers(tmf.getTrustManagers());

                    log.debug("Trust store configured for service '{}'", serviceName);
                }

                // Configure key store for client authentication
                if (keyStorePath != null) {
                    KeyStore keyStore = KeyStore.getInstance("JKS");
                    try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                        keyStore.load(fis, keyStorePassword != null ?
                                     keyStorePassword.toCharArray() : null);
                    }

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(keyStore, keyStorePassword != null ?
                            keyStorePassword.toCharArray() : null);
                    tlsParams.setKeyManagers(kmf.getKeyManagers());

                    log.debug("Key store configured for service '{}'", serviceName);
                }
            }

            httpConduit.setTlsClientParameters(tlsParams);
            log.info("SSL/TLS configured for service '{}'", serviceName);

        } catch (Exception e) {
            log.error("Failed to configure SSL/TLS for service '{}': {}",
                     serviceName, e.getMessage(), e);
            throw new WsdlParsingException("Failed to configure SSL/TLS", e);
        }
    }

    /**
     * Parses WSDL and returns list of available operations.
     */
    private List<String> parseWsdlOperations() {
        if (dynamicClient == null) {
            log.warn("SOAP service is not initialized, cannot parse operations");
            return java.util.Collections.emptyList();
        }

        try {
            // Get the endpoint from the dynamic client
            Endpoint endpoint = dynamicClient.getEndpoint();
            if (endpoint == null) {
                log.warn("No endpoint available for SOAP service '{}'", serviceName);
                return java.util.Collections.emptyList();
            }

            // Get operation names from the endpoint's binding
            org.apache.cxf.service.model.ServiceInfo serviceInfo = endpoint.getEndpointInfo().getService();
            if (serviceInfo == null) {
                return java.util.Collections.emptyList();
            }

            java.util.List<String> operations = new java.util.ArrayList<>();
            for (org.apache.cxf.service.model.BindingOperationInfo bindingOp :
                 endpoint.getBinding().getBindingInfo().getOperations()) {
                operations.add(bindingOp.getName().getLocalPart());
            }

            log.debug("Found {} operations for SOAP service '{}'", operations.size(), serviceName);
            return operations;

        } catch (Exception e) {
            log.error("Failed to parse WSDL operations for service '{}': {}",
                     serviceName, e.getMessage(), e);
            return java.util.Collections.emptyList();
        }
    }

    // ========================================
    // SOAP-Specific Methods
    // ========================================

    public OperationBuilder invoke(String operationName) {
        return new SoapOperationBuilder(operationName);
    }

    public <Req, Res> Mono<Res> invokeAsync(String operationName, Req request, Class<Res> responseType) {
        return invokeOperation(operationName, request, responseType);
    }

    public <Req, Res> Mono<Res> invokeAsync(String operationName, Req request, TypeReference<Res> typeReference) {
        // For now, we'll use the Class-based method
        // In a full implementation, we'd handle TypeReference properly
        log.warn("TypeReference support for SOAP is limited. Using Class-based invocation.");
        return Mono.error(new UnsupportedOperationException(
            "TypeReference support for SOAP requires additional implementation. Use Class-based invokeAsync instead."));
    }

    public List<String> getOperations() {
        return parseWsdlOperations();
    }

    public <P> P getPort(Class<P> portType) {
        if (dynamicClient == null) {
            throw new IllegalStateException("SOAP service is not initialized");
        }
        // This would require access to the Service object
        // For now, throw an exception indicating this needs the raw Service
        throw new UnsupportedOperationException(
            "getPort() requires direct Service access. This feature will be implemented in a future version.");
    }

    public String getWsdlUrl() {
        return wsdlUrl;
    }

    public QName getServiceQName() {
        return serviceQName;
    }

    public QName getPortQName() {
        return portQName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getBaseUrl() {
        return endpointAddress != null ? endpointAddress : wsdlUrl;
    }

    public boolean isReady() {
        return initialized && dynamicClient != null;
    }

    public Mono<Void> healthCheck() {
        return Mono.<Void>fromCallable(() -> {
            if (!isReady()) {
                throw new ServiceClientException("SOAP service is not initialized");
            }

            // Verify WSDL is still accessible
            try {
                URL wsdlURL = java.net.URI.create(wsdlUrl).toURL();
                java.net.HttpURLConnection connection =
                    (java.net.HttpURLConnection) wsdlURL.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout((int) timeout.toMillis());
                connection.setReadTimeout((int) timeout.toMillis());

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    throw new ServiceClientException(
                        "WSDL health check failed with HTTP " + responseCode);
                }

                log.debug("SOAP service '{}' health check passed", serviceName);
                return null;

            } catch (Exception e) {
                log.error("SOAP service '{}' health check failed: {}",
                         serviceName, e.getMessage());
                throw new ServiceClientException(
                    "SOAP service health check failed: " + e.getMessage(), e);
            }
        })
        .transform(this::applyCircuitBreakerProtection);
    }

    public ClientType getClientType() {
        return ClientType.SOAP;
    }

    public void shutdown() {
        log.info("Shutting down SOAP service client '{}'", serviceName);
        initialized = false;
        if (dynamicClient != null) {
            dynamicClient.destroy();
        }
    }

    /**
     * Invokes a SOAP operation dynamically using Apache CXF Dynamic Client.
     */
    private <R> Mono<R> invokeOperation(String operationName, Object request, Class<R> responseType) {
        long startTime = System.currentTimeMillis();

        return Mono.fromCallable(() -> {
            if (!initialized) {
                throw new ServiceClientException("SOAP service is not initialized");
            }

            try {
                log.debug("Invoking SOAP operation '{}' on service '{}'", operationName, serviceName);

                // Extract parameters from request object if it's a JAXB object
                Object[] params = extractParameters(request);

                // Invoke the operation using dynamic client
                Object[] results = dynamicClient.invoke(operationName, params);

                // Handle response
                R typedResult = null;
                if (results != null && results.length > 0) {
                    Object result = results[0];
                    if (responseType != null && result != null) {
                        // For primitive types and their wrappers, return directly
                        if (responseType.isPrimitive() ||
                            responseType == Integer.class || responseType == Long.class ||
                            responseType == Double.class || responseType == Float.class ||
                            responseType == Boolean.class || responseType == String.class) {
                            typedResult = (R) result;
                        }
                        // If the result is already the correct type, cast it
                        else if (responseType.isInstance(result)) {
                            typedResult = responseType.cast(result);
                        }
                        // Otherwise, try to convert it
                        else {
                            typedResult = convertResponse(result, responseType);
                        }
                    } else {
                        typedResult = (R) result;
                    }
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("SOAP operation '{}' on service '{}' completed successfully in {}ms",
                        operationName, serviceName, duration);

                return typedResult;

            } catch (SOAPFaultException e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("SOAP operation '{}' on service '{}' failed with SOAP fault after {}ms: {}",
                         operationName, serviceName, duration, e.getMessage());
                throw mapSoapFault(e);
            } catch (org.apache.cxf.binding.soap.SoapFault e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("SOAP operation '{}' on service '{}' failed with SOAP fault after {}ms: {}",
                         operationName, serviceName, duration, e.getMessage());
                throw mapCxfSoapFault(e);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("SOAP operation '{}' on service '{}' failed after {}ms: {}",
                         operationName, serviceName, duration, e.getMessage(), e);
                throw new ServiceClientException("Failed to invoke SOAP operation: " + operationName, e);
            }
        })
        .doOnError(error -> {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SOAP operation '{}' on service '{}' error after {}ms",
                     operationName, serviceName, duration, error);
        })
        .transform(this::applyCircuitBreakerProtection);
    }

    /**
     * Extracts parameters from a request object.
     * If the request is a JAXB object, extracts field values.
     * Otherwise, returns the request as a single-element array.
     */
    private Object[] extractParameters(Object request) {
        if (request == null) {
            return new Object[0];
        }

        // Check if it's a JAXB object by looking for XmlRootElement annotation
        if (request.getClass().isAnnotationPresent(jakarta.xml.bind.annotation.XmlRootElement.class)) {
            // Extract field values using reflection
            java.util.List<Object> params = new java.util.ArrayList<>();
            for (java.lang.reflect.Field field : request.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(jakarta.xml.bind.annotation.XmlElement.class)) {
                    try {
                        field.setAccessible(true);
                        params.add(field.get(request));
                    } catch (IllegalAccessException e) {
                        log.warn("Failed to extract field '{}' from request: {}", field.getName(), e.getMessage());
                    }
                }
            }
            return params.toArray();
        }

        // For non-JAXB objects, return as single parameter
        return new Object[]{request};
    }

    /**
     * Converts a dynamic response object to the target type using reflection.
     */
    private <R> R convertResponse(Object source, Class<R> targetType) {
        try {
            // Create instance of target type
            R target = targetType.getDeclaredConstructor().newInstance();

            // Copy fields from source to target
            for (java.lang.reflect.Field targetField : targetType.getDeclaredFields()) {
                if (targetField.isAnnotationPresent(jakarta.xml.bind.annotation.XmlElement.class)) {
                    jakarta.xml.bind.annotation.XmlElement xmlElement =
                        targetField.getAnnotation(jakarta.xml.bind.annotation.XmlElement.class);
                    String fieldName = xmlElement.name();

                    // Find matching field in source
                    try {
                        java.lang.reflect.Field sourceField = source.getClass().getDeclaredField(fieldName);
                        sourceField.setAccessible(true);
                        targetField.setAccessible(true);

                        Object value = sourceField.get(source);
                        targetField.set(target, value);
                    } catch (NoSuchFieldException e) {
                        // Try with the actual field name
                        try {
                            java.lang.reflect.Field sourceField = source.getClass().getDeclaredField(targetField.getName());
                            sourceField.setAccessible(true);
                            targetField.setAccessible(true);

                            Object value = sourceField.get(source);
                            targetField.set(target, value);
                        } catch (NoSuchFieldException ex) {
                            log.debug("Field '{}' not found in source object", fieldName);
                        }
                    }
                }
            }

            return target;
        } catch (Exception e) {
            log.error("Failed to convert response to type {}: {}", targetType.getName(), e.getMessage());
            throw new ServiceClientException("Failed to convert SOAP response", e);
        }
    }

    /**
     * Maps a SOAP fault to a SoapFaultException.
     */
    private SoapFaultException mapSoapFault(SOAPFaultException soapFault) {
        SOAPFault fault = soapFault.getFault();

        QName faultCode = fault.getFaultCodeAsQName();
        String faultString = fault.getFaultString();
        String faultActor = fault.getFaultActor();
        String faultDetail = fault.getDetail() != null ? fault.getDetail().toString() : null;

        return new SoapFaultException(faultCode, faultString, faultActor, faultDetail, soapFault);
    }

    /**
     * Maps a CXF SOAP fault to a SoapFaultException.
     */
    private SoapFaultException mapCxfSoapFault(org.apache.cxf.binding.soap.SoapFault soapFault) {
        QName faultCode = soapFault.getFaultCode();
        String faultString = soapFault.getMessage();
        String faultActor = soapFault.getRole();
        String faultDetail = soapFault.getDetail() != null ? soapFault.getDetail().toString() : null;

        return new SoapFaultException(faultCode, faultString, faultActor, faultDetail, soapFault);
    }

    /**
     * Applies circuit breaker protection if configured.
     */
    private <T> Mono<T> applyCircuitBreakerProtection(Mono<T> mono) {
        if (circuitBreakerManager != null) {
            return circuitBreakerManager.executeWithCircuitBreaker(serviceName, () -> mono);
        }
        return mono;
    }

    /**
     * SOAP-specific operation builder implementation.
     */
    private class SoapOperationBuilder implements SoapClient.OperationBuilder {
        private final String operationName;
        private final Map<String, Object> parameters = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private Duration requestTimeout;

        public SoapOperationBuilder(String operationName) {
            this.operationName = operationName;
            this.requestTimeout = timeout;
        }

        @Override
        public OperationBuilder withParameter(String name, Object value) {
            this.parameters.put(name, value);
            return this;
        }

        @Override
        public OperationBuilder withParameters(Map<String, Object> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }

        @Override
        public OperationBuilder withHeader(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        @Override
        public OperationBuilder withHeaders(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        @Override
        public OperationBuilder withTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        @Override
        public <R> Mono<R> execute(Class<R> responseType) {
            log.debug("Executing SOAP operation '{}' on service '{}' with {} parameters",
                     operationName, serviceName, parameters.size());

            // Build request object from parameters
            Object request = buildRequestFromParameters();
            return invokeOperation(operationName, request, responseType);
        }

        @Override
        public <R> Mono<R> execute(TypeReference<R> typeReference) {
            log.warn("TypeReference support for SOAP is limited");
            return Mono.error(new UnsupportedOperationException(
                "TypeReference support for SOAP requires additional implementation. Use Class-based execute instead."));
        }

        /**
         * Builds a request object from the parameters map.
         */
        private Object buildRequestFromParameters() {
            if (parameters.isEmpty()) {
                return null;
            }
            // For simple cases, return the parameters map
            // In a full implementation, this would create proper JAXB objects
            return parameters;
        }
    }
}
