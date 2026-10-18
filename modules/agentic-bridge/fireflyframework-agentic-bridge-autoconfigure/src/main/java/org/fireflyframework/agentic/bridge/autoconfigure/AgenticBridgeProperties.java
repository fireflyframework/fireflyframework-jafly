/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot configuration binding for {@code firefly.agentic-bridge.*}.
 *
 * <p>Drives the auto-wiring of the primary {@link org.fireflyframework.agentic.bridge.AgenticClient}
 * along with any number of named queue publishers.</p>
 */
@ConfigurationProperties(prefix = "firefly.agentic-bridge")
public class AgenticBridgeProperties {

    private boolean enabled = true;

    private Client primary = new Client();

    private final Map<String, Publisher> publishers = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Client getPrimary() { return primary; }
    public void setPrimary(Client primary) { this.primary = primary; }

    public Map<String, Publisher> getPublishers() { return publishers; }

    /** Configuration for one bridge {@link org.fireflyframework.agentic.bridge.AgenticClient}. */
    public static class Client {
        private String name = "primary";
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration responseTimeout = Duration.ofSeconds(60);
        private Duration writeTimeout = Duration.ofSeconds(30);
        private boolean compression = true;
        private boolean followRedirects = false;
        private boolean requireTls = false;
        private String userAgent = "fireflyframework-agentic-bridge";
        private int maxInMemorySize = 16 * 1024 * 1024;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private Auth auth = new Auth();
        private Retry retry = new Retry();
        private CircuitBreaker circuitBreaker = new CircuitBreaker();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getResponseTimeout() { return responseTimeout; }
        public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
        public Duration getWriteTimeout() { return writeTimeout; }
        public void setWriteTimeout(Duration writeTimeout) { this.writeTimeout = writeTimeout; }
        public boolean isCompression() { return compression; }
        public void setCompression(boolean compression) { this.compression = compression; }
        public boolean isFollowRedirects() { return followRedirects; }
        public void setFollowRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }
        public boolean isRequireTls() { return requireTls; }
        public void setRequireTls(boolean requireTls) { this.requireTls = requireTls; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
        public int getMaxInMemorySize() { return maxInMemorySize; }
        public void setMaxInMemorySize(int maxInMemorySize) { this.maxInMemorySize = maxInMemorySize; }
        public Map<String, String> getDefaultHeaders() { return defaultHeaders; }
        public Auth getAuth() { return auth; }
        public void setAuth(Auth auth) { this.auth = auth; }
        public Retry getRetry() { return retry; }
        public void setRetry(Retry retry) { this.retry = retry; }
        public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(CircuitBreaker circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    }

    /** Authentication configuration. */
    public static class Auth {
        public enum Type { NONE, API_KEY, BEARER }

        private Type type = Type.NONE;
        private String apiKey;
        private String apiKeyHeader = "X-API-Key";
        private String token;

        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    /** Retry policy configuration. */
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(250);
        private Duration maxBackoff = Duration.ofSeconds(5);
        private boolean jitter = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getInitialBackoff() { return initialBackoff; }
        public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
        public boolean isJitter() { return jitter; }
        public void setJitter(boolean jitter) { this.jitter = jitter; }
    }

    /** Circuit breaker configuration. */
    public static class CircuitBreaker {
        private boolean enabled = false;
        private float failureRateThreshold = 50f;
        private int slidingWindowSize = 100;
        private int minimumNumberOfCalls = 10;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private Duration slowCallDurationThreshold = Duration.ofSeconds(2);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public float getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(float failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }
        public Duration getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) { this.slowCallDurationThreshold = slowCallDurationThreshold; }
    }

    /** Configuration for one named queue publisher. */
    public static class Publisher {
        public enum Type { KAFKA, RABBITMQ, REDIS }

        private Type type;
        private String topic;
        private String exchange = "";
        private String routingKey;
        private String channel;
        private String bootstrapServers;
        private String url;

        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
