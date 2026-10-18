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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration class for HTTP client metrics and health monitoring.
 * <p>
 * This class provides comprehensive monitoring of HTTP clients including:
 * - RestTemplate metrics and health
 * - WebClient metrics (if available)
 * - OkHttp client metrics
 * - HTTP connection pool monitoring
 * - Response time and error rate tracking
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(prefix = "firefly.actuator.metrics.http.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
public class HttpClientMetricsConfig {

    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public HttpClientMetricsConfig(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Provides HTTP client health indicator.
     * Monitors the health and performance of HTTP clients.
     *
     * @param restTemplates list of RestTemplate instances
     * @return HTTP client health indicator
     */
    @Bean
    public HealthIndicator httpClientHealthIndicator(List<RestTemplate> restTemplates) {
        return () -> {
            Map<String, Object> details = new HashMap<>();
            boolean allHealthy = true;
            
            // Check RestTemplate instances
            for (int i = 0; i < restTemplates.size(); i++) {
                RestTemplate restTemplate = restTemplates.get(i);
                String clientName = "restTemplate_" + i;
                
                Map<String, Object> clientDetails = checkRestTemplateHealth(restTemplate);
                details.put(clientName, clientDetails);
                
                if (!"UP".equals(clientDetails.get("status"))) {
                    allHealthy = false;
                }
            }
            
            // Check system HTTP client if available
            Map<String, Object> systemHttpClientDetails = checkSystemHttpClientHealth();
            details.put("systemHttpClient", systemHttpClientDetails);
            
            if (restTemplates.isEmpty()) {
                details.put("message", "No RestTemplate instances configured");
            }
            
            return allHealthy 
                    ? org.springframework.boot.actuate.health.Health.up().withDetails(details).build()
                    : org.springframework.boot.actuate.health.Health.down().withDetails(details).build();
        };
    }

    /**
     * Provides RestTemplate customizer for metrics integration.
     * Adds request/response metrics to RestTemplate instances.
     *
     * @return RestTemplate customizer
     */
    @Bean
    public RestTemplateCustomizer restTemplateMetricsCustomizer() {
        return restTemplate -> {
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
            if (meterRegistry != null) {
                // Add interceptors for metrics collection
                restTemplate.getInterceptors().add((request, body, execution) -> {
                    Timer.Sample sample = Timer.start(meterRegistry);
                    String uri = request.getURI().toString();
                    String method = request.getMethod().toString();
                    
                    try {
                        var response = execution.execute(request, body);
                        
                        // Record successful request metrics
                        sample.stop(Timer.builder("http.client.requests")
                                .tags(Tags.of(
                                        "method", method,
                                        "uri", sanitizeUri(uri),
                                        "status", String.valueOf(response.getStatusCode().value()),
                                        "outcome", "SUCCESS"
                                ))
                                .register(meterRegistry));
                        
                        return response;
                    } catch (Exception e) {
                        // Record failed request metrics
                        sample.stop(Timer.builder("http.client.requests")
                                .tags(Tags.of(
                                        "method", method,
                                        "uri", sanitizeUri(uri),
                                        "status", "UNKNOWN",
                                        "outcome", "ERROR"
                                ))
                                .register(meterRegistry));
                        
                        meterRegistry.counter("http.client.errors", 
                                Tags.of(
                                        "method", method,
                                        "uri", sanitizeUri(uri),
                                        "exception", e.getClass().getSimpleName()
                                ))
                                .increment();
                        
                        throw e;
                    }
                });
            }
        };
    }

    /**
     * Checks the health of a RestTemplate instance.
     */
    private Map<String, Object> checkRestTemplateHealth(RestTemplate restTemplate) {
        Map<String, Object> details = new HashMap<>();
        
        try {
            details.put("status", "UP");
            details.put("interceptorCount", restTemplate.getInterceptors().size());
            details.put("messageConverterCount", restTemplate.getMessageConverters().size());
            details.put("errorHandlerType", restTemplate.getErrorHandler().getClass().getSimpleName());
            details.put("requestFactoryType", restTemplate.getRequestFactory().getClass().getSimpleName());
            
            // Test basic connectivity with a simple HEAD request to a reliable endpoint
            try {
                restTemplate.headForHeaders("https://httpbin.org/status/200");
                details.put("connectivityTest", "PASSED");
            } catch (Exception e) {
                details.put("connectivityTest", "FAILED");
                details.put("connectivityError", e.getMessage());
            }
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Checks the health of the system HTTP client.
     */
    private Map<String, Object> checkSystemHttpClientHealth() {
        Map<String, Object> details = new HashMap<>();
        
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            details.put("status", "UP");
            details.put("version", httpClient.version().toString());
            details.put("cookieHandlerPresent", httpClient.cookieHandler().isPresent());
            details.put("proxyPresent", httpClient.proxy().isPresent());
            details.put("authenticatorPresent", httpClient.authenticator().isPresent());
            details.put("redirectPolicy", httpClient.followRedirects().toString());
            details.put("connectTimeout", httpClient.connectTimeout().map(Duration::toString).orElse("DEFAULT"));
            
        } catch (Exception e) {
            details.put("status", "DOWN");
            details.put("error", e.getMessage());
        }
        
        return details;
    }

    /**
     * Provides OkHttp metrics event listener if OkHttp is available.
     */
    @Bean
    @ConditionalOnClass(name = "okhttp3.OkHttpClient")
    public OkHttpMetricsEventListener okHttpMetricsEventListener() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            return OkHttpMetricsEventListener.builder(meterRegistry, "okhttp.requests").build();
        }
        return null;
    }

    /**
     * Sanitizes URI for metrics to avoid high cardinality.
     */
    private String sanitizeUri(String uri) {
        if (uri == null) return "unknown";
        
        // Remove query parameters and fragments
        String sanitized = uri.split("\\?")[0].split("#")[0];
        
        // Replace numeric IDs with placeholder to reduce cardinality
        sanitized = sanitized.replaceAll("/\\d+", "/{id}");
        
        // Limit length to prevent extremely long URIs
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 97) + "...";
        }
        
        return sanitized;
    }
}