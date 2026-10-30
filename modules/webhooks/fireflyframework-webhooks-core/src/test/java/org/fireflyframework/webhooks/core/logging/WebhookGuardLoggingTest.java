/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.webhooks.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.fireflyframework.kernel.logging.LogFields;
import org.fireflyframework.webhooks.core.config.WebhookSecurityProperties;
import org.fireflyframework.webhooks.core.ratelimit.WebhookRateLimitService;
import org.fireflyframework.webhooks.core.resilience.ResilientWebhookProcessingService;
import org.fireflyframework.webhooks.core.services.WebhookProcessingService;
import org.fireflyframework.webhooks.core.validation.WebhookValidator;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookGuardLoggingTest {
    private static final String LABEL = "sandbox\r\nlabel\u000b\f\u0085\u2028\u2029";
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final Map<Logger, Level> levels = new HashMap<>();
    private final Map<Logger, Boolean> additive = new HashMap<>();

    @BeforeEach void capture() {
        events.start();
        for (Class<?> type : List.of(WebhookValidator.class, WebhookRateLimitService.class, ResilientWebhookProcessingService.class)) {
            var logger = (Logger) LoggerFactory.getLogger(type);
            levels.put(logger, logger.getLevel()); additive.put(logger, logger.isAdditive());
            logger.setLevel(Level.DEBUG); logger.setAdditive(false); logger.addAppender(events);
        }
    }

    @AfterEach void restore() {
        for (var logger : levels.keySet()) {
            logger.detachAppender(events); logger.setLevel(levels.get(logger)); logger.setAdditive(additive.get(logger));
        }
        events.stop();
    }

    @Test void invalidProviderKeepsBadRequestAndEscapesOnlyDiagnostic() {
        var properties = new WebhookSecurityProperties();
        var failure = assertThrows(ResponseStatusException.class, () -> new WebhookValidator(properties).validateProviderName(LABEL));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertEquals("Invalid provider name format. Must match pattern: " + properties.getProviderNamePattern(), failure.getReason());
        diagnostic("Invalid provider name format: {}", Level.WARN, "Invalid provider name format: " + LogFields.text(LABEL));
        singleLine();
    }

    @Test void deniedIpPreservesRawProviderLookupHeaderAndForbiddenResponse() {
        var properties = whitelist(LABEL, List.of("127.0.0.1"));
        var request = request("X-Forwarded-For", "sandbox\naddress");
        var failure = assertThrows(ResponseStatusException.class, () -> new WebhookValidator(properties).validateIpWhitelist(LABEL, request));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode()); assertEquals("Source IP not authorized for this provider", failure.getReason());
        assertEquals("sandbox\naddress", request.getHeaders().getFirst("X-Forwarded-For"));
        assertEquals(List.of("127.0.0.1"), properties.getIpWhitelist().get(LABEL));
        diagnostic("IP {} not whitelisted for provider {}", Level.WARN, "IP sandbox\\naddress not whitelisted for provider " + LogFields.text(LABEL));
        singleLine();
    }

    @Test void acceptedIpKeepsRawComparisonAndEscapesDebugArguments() {
        String ip = "sandbox\naddress";
        var properties = whitelist(LABEL, List.of(ip)); var request = request("X-Forwarded-For", ip);
        assertDoesNotThrow(() -> new WebhookValidator(properties).validateIpWhitelist(LABEL, request));
        assertEquals(ip, request.getHeaders().getFirst("X-Forwarded-For"));
        diagnostic("IP whitelist validation passed: {} for provider {}", Level.DEBUG,
                "IP whitelist validation passed: sandbox\\naddress for provider " + LogFields.text(LABEL));
        singleLine();
    }

    @Test void malformedCidrAndLibraryMessageRemainARefusalWithoutMultilineDiagnostics() {
        String cidr = "sandbox\nnetwork/24"; var properties = whitelist("sandbox", List.of(cidr));
        var failure = assertThrows(ResponseStatusException.class,
                () -> new WebhookValidator(properties).validateIpWhitelist("sandbox", request("X-Forwarded-For", "127.0.0.1")));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode()); assertEquals(cidr, properties.getIpWhitelist().get("sandbox").getFirst());
        var warning = event("Invalid IP or CIDR notation: allowedIp={}, error={}");
        assertEquals(Level.WARN, warning.getLevel()); assertTrue(warning.getFormattedMessage().contains(LogFields.text(cidr)));
        assertNull(warning.getThrowableProxy()); singleLine();
    }

    @Test void contentTypeAndConfiguredListEscapeOnlyLogRendering() {
        String type = "text/sandbox\nlabel"; var properties = new WebhookSecurityProperties();
        var allowed = List.of("application/sandbox\nconfig"); properties.setAllowedContentTypes(allowed);
        var failure = assertThrows(ResponseStatusException.class,
                () -> new WebhookValidator(properties).validateContentType(request("Content-Type", type)));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, failure.getStatusCode());
        assertEquals("Content-Type must be one of: " + String.join(", ", allowed), failure.getReason()); assertSame(allowed, properties.getAllowedContentTypes());
        diagnostic("Invalid Content-Type: {}. Allowed types: {}", Level.WARN,
                "Invalid Content-Type: " + LogFields.text(type) + ". Allowed types: " + LogFields.values(allowed)); singleLine();
    }

    @Test void providerRateLimitKeepsRawKeyOperationAndOriginalReason() {
        var registry = rateRegistry(); var service = new WebhookRateLimitService(registry); Object value = new Object();
        assertSame(value, service.executeWithRateLimit(LABEL, Mono.just(value)).block());
        var failure = assertThrows(ResponseStatusException.class, () -> service.executeWithRateLimit(LABEL, Mono.just(value)).block());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.getStatusCode());
        assertEquals("Rate limit exceeded for provider: " + LABEL + ". Please retry after some time.", failure.getReason());
        assertEquals("webhook-provider-" + LABEL, registry.getAllRateLimiters().iterator().next().getName());
        diagnostic("Rate limit exceeded for provider: {}", Level.WARN, "Rate limit exceeded for provider: " + LogFields.text(LABEL)); singleLine();
    }

    @Test void ipRateLimitKeepsRawKeyAndTooManyRequests() {
        var registry = rateRegistry(); var service = new WebhookRateLimitService(registry); Object value = new Object();
        assertSame(value, service.executeWithIpRateLimit(LABEL, Mono.just(value)).block());
        var failure = assertThrows(ResponseStatusException.class, () -> service.executeWithIpRateLimit(LABEL, Mono.just(value)).block());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, failure.getStatusCode());
        assertEquals("Rate limit exceeded for your IP address. Please retry after some time.", failure.getReason());
        assertEquals("webhook-ip-" + LABEL, registry.getAllRateLimiters().iterator().next().getName());
        diagnostic("Rate limit exceeded for IP: {}", Level.WARN, "Rate limit exceeded for IP: " + LogFields.text(LABEL)); singleLine();
    }

    @Test void resilienceProviderDiagnosticPreservesDelegateDtoPayloadAndResponse() {
        var dto = dto(LABEL); var payload = dto.getPayload(); var response = WebhookResponseDTO.success(dto.getEventId(), LABEL);
        var delegate = mock(WebhookProcessingService.class); when(delegate.processWebhook(dto)).thenReturn(Mono.just(response));
        assertSame(response, resilient(delegate).processWebhook(dto).block()); verify(delegate).processWebhook(same(dto));
        assertEquals(LABEL, dto.getProviderName()); assertSame(payload, dto.getPayload());
        diagnostic("Processing webhook with resilience patterns: eventId={}, provider={}", Level.DEBUG,
                "Processing webhook with resilience patterns: eventId=" + dto.getEventId() + ", provider=" + LogFields.text(LABEL)); singleLine();
    }

    @Test void genericFailurePreservesThrowableIdentityAndEscapesFormattedMessage() {
        var dto = dto("sandbox"); var original = new IllegalStateException(LABEL);
        var delegate = mock(WebhookProcessingService.class); when(delegate.processWebhook(dto)).thenReturn(Mono.error(original));
        assertSame(original, assertThrows(IllegalStateException.class, () -> resilient(delegate).processWebhook(dto).block()));
        var error = event("Error in resilient webhook processing: {}");
        assertEquals(Level.ERROR, error.getLevel()); assertSame(original, ((ThrowableProxy) error.getThrowableProxy()).getThrowable());
        assertEquals("Error in resilient webhook processing: " + LogFields.text(LABEL), error.getFormattedMessage());
        assertEquals(LABEL, original.getMessage()); singleLine();
    }

    @Test void timeoutKeepsGatewayTimeoutAndExistingReason() {
        var dto = dto("sandbox"); var delegate = mock(WebhookProcessingService.class);
        when(delegate.processWebhook(dto)).thenReturn(Mono.error(new TimeoutException(LABEL)));
        var failure = assertThrows(ResponseStatusException.class, () -> resilient(delegate).processWebhook(dto).block());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, failure.getStatusCode());
        assertEquals("Webhook processing timed out. The message broker may be slow or unavailable.", failure.getReason());
        diagnostic("Webhook processing timed out: {}", Level.ERROR, "Webhook processing timed out: " + LogFields.text(LABEL)); singleLine();
    }

    @Test void circuitFailureKeepsServiceUnavailableAndEscapesLibraryMessage() {
        var dto = dto("sandbox"); var delegate = mock(WebhookProcessingService.class);
        var original = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("sandbox\nbreaker"));
        when(delegate.processWebhook(dto)).thenReturn(Mono.error(original));
        var failure = assertThrows(ResponseStatusException.class, () -> resilient(delegate).processWebhook(dto).block());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
        assertEquals("Webhook processing is temporarily unavailable. The message broker is experiencing issues. Please retry later.", failure.getReason());
        diagnostic("Circuit breaker is OPEN - rejecting webhook: {}", Level.ERROR,
                "Circuit breaker is OPEN - rejecting webhook: " + LogFields.text(original.getMessage())); singleLine();
    }

    @Test void ordinaryAndNullValuesKeepExistingTextAndErrorIdentity() {
        var dto = dto(null); var original = new IllegalArgumentException();
        var delegate = mock(WebhookProcessingService.class); when(delegate.processWebhook(dto)).thenReturn(Mono.error(original));
        assertSame(original, assertThrows(IllegalArgumentException.class, () -> resilient(delegate).processWebhook(dto).block()));
        assertEquals("Error in resilient webhook processing: null", event("Error in resilient webhook processing: {}").getFormattedMessage());
        diagnostic("Processing webhook with resilience patterns: eventId={}, provider={}", Level.DEBUG,
                "Processing webhook with resilience patterns: eventId=" + dto.getEventId() + ", provider=null");
        assertDoesNotThrow(() -> new WebhookValidator(new WebhookSecurityProperties()).validateProviderName("sandbox")); singleLine();
    }

    @Test void disabledLoggingLeavesValidationRateLimitAndDelegateBehaviorUnchanged() {
        levels.keySet().forEach(logger -> logger.setLevel(Level.OFF));
        assertThrows(ResponseStatusException.class, () -> new WebhookValidator(new WebhookSecurityProperties()).validateProviderName(LABEL));
        Object value = new Object(); assertSame(value, new WebhookRateLimitService(rateRegistry()).executeWithRateLimit(LABEL, Mono.just(value)).block());
        var dto = dto(LABEL); var response = WebhookResponseDTO.success(dto.getEventId(), LABEL);
        var delegate = mock(WebhookProcessingService.class); when(delegate.processWebhook(dto)).thenReturn(Mono.just(response));
        assertSame(response, resilient(delegate).processWebhook(dto).block()); verify(delegate).processWebhook(same(dto)); assertTrue(events.list.isEmpty());
    }

    private static WebhookSecurityProperties whitelist(String provider, List<String> ips) {
        var properties = new WebhookSecurityProperties(); properties.setEnableIpWhitelist(true); properties.setIpWhitelist(Map.of(provider, ips)); return properties;
    }
    private static ServerHttpRequest request(String name, String value) {
        var headers = new HttpHeaders(); headers.add(name, value); var request = mock(ServerHttpRequest.class); when(request.getHeaders()).thenReturn(headers); return request;
    }
    private static RateLimiterRegistry rateRegistry() {
        return RateLimiterRegistry.of(RateLimiterConfig.custom().limitForPeriod(1).limitRefreshPeriod(Duration.ofHours(1)).timeoutDuration(Duration.ZERO).build());
    }
    private static ResilientWebhookProcessingService resilient(WebhookProcessingService delegate) {
        return new ResilientWebhookProcessingService(delegate, CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults());
    }
    private static WebhookEventDTO dto(String provider) {
        return WebhookEventDTO.builder().eventId(UUID.randomUUID()).providerName(provider).payload(JsonNodeFactory.instance.objectNode().put("kind", "sandbox")).build();
    }
    private ILoggingEvent event(String template) {
        return events.list.stream().filter(value -> template.equals(value.getMessage())).findFirst().orElseThrow();
    }
    private void diagnostic(String template, Level level, String formatted) {
        var actual = event(template); assertEquals(level, actual.getLevel()); assertEquals(formatted, actual.getFormattedMessage());
    }
    private void singleLine() {
        for (var event : events.list) for (String separator : List.of("\r", "\n", "\u000b", "\f", "\u0085", "\u2028", "\u2029"))
            assertFalse(event.getFormattedMessage().contains(separator), "Formatted diagnostic must retain a single line");
    }
}
