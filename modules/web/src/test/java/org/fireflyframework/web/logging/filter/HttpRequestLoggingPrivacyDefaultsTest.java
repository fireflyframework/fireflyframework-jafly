/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.web.logging.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.fireflyframework.web.logging.config.HttpRequestLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestLoggingPrivacyDefaultsTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingWebFilter.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level priorLevel;
    private boolean priorAdditive;

    @BeforeEach
    void capture() {
        priorLevel = logger.getLevel();
        priorAdditive = logger.isAdditive();
        events.start();
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(events);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(events);
        logger.setLevel(priorLevel);
        logger.setAdditive(priorAdditive);
        events.stop();
    }

    @Test
    void defaultLoggingOmitsQueryHeadersAndBodiesWithoutChangingTraffic() {
        var properties = new HttpRequestLoggingProperties();
        exchange(properties, "private-query-value", "private-header-value", "private-request-body", "private-response-body");

        assertThat(events.list).hasSize(2);
        String output = output();
        assertThat(output).contains("HTTP_REQUEST", "HTTP_RESPONSE", "/api/resource", "POST");
        assertThat(output).doesNotContain("private-query-value", "private-header-value", "private-request-body", "private-response-body");
        assertThat(properties.isIncludeHeaders()).isFalse();
        assertThat(properties.isIncludeQueryParams()).isFalse();
        assertThat(properties.isIncludeRequestBody()).isFalse();
        assertThat(properties.isIncludeResponseBody()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("bodyLoggingModes")
    void diagnosticBodyLoggingNeverConsumesTheBusinessPayload(boolean requestEnabled, boolean responseEnabled) {
        var properties = new HttpRequestLoggingProperties();
        properties.setIncludeHeaders(false);
        properties.setIncludeQueryParams(false);
        properties.setIncludeRequestBody(requestEnabled);
        properties.setIncludeResponseBody(responseEnabled);
        properties.setMaxBodySize(7);

        exchange(properties, "query", "header", "request-payload-longer-than-the-log-limit", "response-payload-longer-than-the-log-limit");

        assertThat(events.list).hasSize(2 + (requestEnabled ? 1 : 0) + (responseEnabled ? 1 : 0));
        if (requestEnabled || responseEnabled) {
            if (requestEnabled) assertThat(output()).contains("HTTP_REQUEST_BODY");
            if (responseEnabled) assertThat(output()).contains("HTTP_RESPONSE_BODY");
            assertThat(output()).doesNotContain("request-payload-longer-than-the-log-limit", "response-payload-longer-than-the-log-limit");
        }
    }

    @Test
    void explicitMetadataOptInStillWorks() {
        var properties = new HttpRequestLoggingProperties();
        properties.setIncludeHeaders(true);
        properties.setIncludeQueryParams(true);

        exchange(properties, "diagnostic-query-value", "diagnostic-header-value", "request-body", "response-body");

        assertThat(output()).contains("diagnostic-query-value", "diagnostic-header-value");
        assertThat(output()).doesNotContain("synthetic-secret-value");
    }

    static Stream<Arguments> bodyLoggingModes() {
        return Stream.of(Arguments.of(false, false), Arguments.of(true, true),
                Arguments.of(true, false), Arguments.of(false, true));
    }

    private void exchange(HttpRequestLoggingProperties properties, String query, String header, String requestBody, String responseBody) {
        var request = MockServerHttpRequest.post("/api/resource?diagnostic=" + query)
                .header("X-Diagnostic-Label", header)
                .header("Authorization", "Bearer synthetic-secret-value").body(requestBody);
        var exchange = MockServerWebExchange.from(request);
        var received = new AtomicReference<String>();
        var filter = new HttpRequestLoggingWebFilter(properties, Optional.empty());

        filter.filter(exchange, actual -> DataBufferUtils.join(actual.getRequest().getBody()).flatMap(buffer -> {
            received.set(buffer.toString(StandardCharsets.UTF_8));
            DataBufferUtils.release(buffer);
            return actual.getResponse().writeWith(Mono.just(actual.getResponse().bufferFactory()
                    .wrap(responseBody.getBytes(StandardCharsets.UTF_8))));
        })).block();

        assertThat(received.get()).isEqualTo(requestBody);
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo(responseBody);
    }

    private String output() {
        return events.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + right);
    }
}
