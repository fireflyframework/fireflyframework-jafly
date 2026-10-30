/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.webhooks.web.controllers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.fireflyframework.webhooks.core.metrics.WebhookMetricsService;
import org.fireflyframework.webhooks.core.ratelimit.WebhookRateLimitService;
import org.fireflyframework.webhooks.core.services.WebhookMetadataEnrichmentService;
import org.fireflyframework.webhooks.core.services.WebhookProcessingService;
import org.fireflyframework.webhooks.core.validation.WebhookValidator;
import org.fireflyframework.webhooks.interfaces.dto.WebhookEventDTO;
import org.fireflyframework.webhooks.interfaces.dto.WebhookResponseDTO;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookControllerLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(WebhookController.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level oldLevel;
    private boolean oldAdditive;
    @BeforeEach void capture() {
        oldLevel=logger.getLevel();oldAdditive=logger.isAdditive();
        logger.setLevel(Level.INFO);logger.setAdditive(false);events.start();logger.addAppender(events);
    }
    @AfterEach void restore() {
        logger.detachAppender(events);events.stop();logger.setLevel(oldLevel);logger.setAdditive(oldAdditive);
    }
    @Test void receivedDiagnosticEscapesLineSeparatorsBeforeLocalValidation() {
        String provider="sandbox\r\nprovider\u0085\u2028\u2029";var f=new Fixture();
        var rejected=new ResponseStatusException(HttpStatus.BAD_REQUEST,"sandbox validation");
        doThrow(rejected).when(f.validator).validateRequest(eq(provider),same(f.payload),same(f.request));
        assertSame(rejected,assertThrows(ResponseStatusException.class,()->f.receive(provider)));
        assertEquals(1,events.list.size());assertEquals(Level.INFO,events.list.getFirst().getLevel());
        assertTrue(events.list.getFirst().getFormattedMessage().startsWith("Received webhook from provider: sandbox\\r\\nprovider\\u0085\\u2028\\u2029 eventId: "));
        assertSingleLine();verify(f.metrics).recordWebhookRejected(provider,"sandbox validation");verifyNoInteractions(f.processing);
    }
    @Test void successfulDiagnosticEscapesOnlyLogFieldAndPreservesControllerTransport() {
        String provider="Sandbox\nProvider";var f=new Fixture();var response=f.receive(provider);
        f.assertTransport(provider,response);assertSingleLine();
        assertEquals("Webhook processed successfully: "+f.event.getEventId()+" from provider: Sandbox\\nProvider",events.list.getLast().getFormattedMessage());
        assertEquals(Level.INFO,events.list.getLast().getLevel());verify(f.metrics).recordWebhookPublished(provider);
    }
    @Test void processingErrorKeepsOriginalThrowableAndEscapesProviderDiagnostic() {
        String provider="Sandbox\rProvider";var f=new Fixture();var failure=new IllegalStateException("sandbox\nmessage");f.failure=failure;
        assertSame(failure,assertThrows(IllegalStateException.class,()->f.receive(provider)));
        var event=events.list.getLast();assertEquals(Level.ERROR,event.getLevel());
        assertEquals("Error processing webhook from provider: Sandbox\\rProvider",event.getFormattedMessage());
        assertSame(failure,((ThrowableProxy)event.getThrowableProxy()).getThrowable());assertSingleLine();
        assertSame(f.payload,f.event.getPayload());assertEquals(provider.toLowerCase(),f.event.getProviderName());
        verify(f.metrics).recordWebhookFailed(provider,"IllegalStateException");
    }
    @Test void ordinaryDiagnosticsKeepTextLevelsAcceptedStatusAndBodyIdentity() {
        var f=new Fixture();var response=f.receive("Sandbox");f.assertTransport("Sandbox",response);
        assertEquals("Received webhook from provider: Sandbox eventId: "+f.event.getEventId(),events.list.getFirst().getFormattedMessage());
        assertEquals("Webhook processed successfully: "+f.event.getEventId()+" from provider: Sandbox",events.list.getLast().getFormattedMessage());
        assertTrue(events.list.stream().allMatch(e->e.getLevel()==Level.INFO&&e.getThrowableProxy()==null));
    }
    @Test void validationStatusReasonAndRawProviderAreUnchanged() {
        var f=new Fixture();var failure=new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"sandbox reason");
        doThrow(failure).when(f.validator).validateRequest(eq("sandbox"),same(f.payload),same(f.request));
        var observed=assertThrows(ResponseStatusException.class,()->f.receive("sandbox"));assertSame(failure,observed);assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE,observed.getStatusCode());assertEquals("sandbox reason",observed.getReason());
        verifyNoInteractions(f.processing);verify(f.metrics).recordWebhookRejected("sandbox","sandbox reason");
    }
    @Test void localRateLimitRefusalKeepsStatusAndDoesNotInvokeValidationOrProcessing() {
        var f=new Fixture();var failure=new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"sandbox limit");
        when(f.limits.executeWithRateLimit(eq("sandbox"),any())).thenReturn(Mono.error(failure));
        assertSame(failure,assertThrows(ResponseStatusException.class,()->f.receive("sandbox")));verifyNoInteractions(f.validator,f.processing);assertEquals(1,events.list.size());
    }
    @Test void disabledLoggingDoesNotChangeControllerInputsResponseOrMetrics() {
        logger.setLevel(Level.OFF);var f=new Fixture();var response=f.receive("Sandbox\nProvider");f.assertTransport("Sandbox\nProvider",response);assertTrue(events.list.isEmpty());verify(f.metrics).recordWebhookPublished("Sandbox\nProvider");
    }
    @Test void nullProviderKeepsExistingNullDiagnosticAndFailureBoundary() {
        var f=new Fixture();assertThrows(NullPointerException.class,()->f.receive(null));
        assertTrue(events.list.getFirst().getFormattedMessage().startsWith("Received webhook from provider: null eventId: "));verifyNoInteractions(f.validator,f.processing);
    }
    private void assertSingleLine() {
        for(var event:events.list)for(String separator:List.of("\r","\n","\u0085","\u2028","\u2029"))assertFalse(event.getFormattedMessage().contains(separator));
    }
    private static final class Fixture {
        final WebhookProcessingService processing=mock(WebhookProcessingService.class);
        final WebhookValidator validator=mock(WebhookValidator.class);
        final WebhookMetricsService metrics=mock(WebhookMetricsService.class);
        final WebhookRateLimitService limits=mock(WebhookRateLimitService.class);
        final WebhookMetadataEnrichmentService enrichment=mock(WebhookMetadataEnrichmentService.class);
        final WebhookController controller=new WebhookController(processing,validator,metrics,limits,enrichment);
        final com.fasterxml.jackson.databind.JsonNode payload=JsonNodeFactory.instance.objectNode().put("kind","sandbox");
        final MockServerHttpRequest request=MockServerHttpRequest.post("/owned-local-fixture").header("X-Sandbox","original").header("X-Forwarded-For","192.0.2.10, 192.0.2.11").build();
        final LinkedMultiValueMap<String,String> query=new LinkedMultiValueMap<>();
        final WebhookResponseDTO body=WebhookResponseDTO.builder().status("ACCEPTED").message("sandbox response").metadata(new HashMap<>()).build();
        WebhookEventDTO event;RuntimeException failure;
        Fixture() {
            query.add("sandbox","first");query.add("sandbox","second");
            when(limits.executeWithRateLimit(any(),any())).thenAnswer(inv->inv.getArgument(1));when(limits.executeWithIpRateLimit(any(),any())).thenAnswer(inv->inv.getArgument(1));
            when(processing.processWebhook(any())).thenAnswer(inv->{event=inv.getArgument(0);return failure==null?Mono.just(body):Mono.error(failure);});
        }
        org.springframework.http.ResponseEntity<WebhookResponseDTO> receive(String provider){return controller.receiveWebhook(provider,payload,request,query).block(Duration.ofSeconds(2));}
        void assertTransport(String provider,org.springframework.http.ResponseEntity<WebhookResponseDTO> response) {
            assertEquals(HttpStatus.ACCEPTED,response.getStatusCode());assertSame(body,response.getBody());assertSame(payload,event.getPayload());assertEquals(provider.toLowerCase(),event.getProviderName());assertEquals("original",event.getHeaders().get("X-Sandbox"));assertEquals("first",event.getQueryParams().get("sandbox"));assertEquals("192.0.2.10",event.getSourceIp());assertEquals("POST",event.getHttpMethod());assertNotNull(event.getReceivedAt());assertNotNull(body.getMetadata().get("responseTimeMs"));
            verify(validator).validateRequest(eq(provider),same(payload),same(request));verify(limits).executeWithRateLimit(eq(provider),any());verify(metrics).recordWebhookReceived(provider);
        }
    }
}
