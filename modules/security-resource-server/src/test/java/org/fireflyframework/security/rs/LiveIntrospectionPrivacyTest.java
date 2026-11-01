package org.fireflyframework.security.rs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.fireflyframework.security.api.domain.BearerToken;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.introspection.SpringReactiveOpaqueTokenIntrospector;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveIntrospectionPrivacyTest {
    @Test void nativeCodecDelegationSuppressesAllDecodeEntryPointValueLogsAtTrace() {
        var logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var capture = new ListAppender<ILoggingEvent>(); capture.start();
        var previous = logger.getLevel(); logger.setLevel(Level.TRACE); logger.addAppender(capture);
        try {
            var codec = SpringLiveTokenValidationAdapter.nativeJsonDecoder();
            var type = ResolvableType.forClass(Map.class);
            var factory = DefaultDataBufferFactory.sharedInstance;
            byte[] bytes = "{\"private-key\":\"PRIVATE_PROVIDER_BODY\"}".getBytes(StandardCharsets.UTF_8);
            assertNotNull(codec.decode(factory.wrap(bytes), type, MediaType.APPLICATION_JSON, Map.of()));
            assertNotNull(codec.decodeToMono(Flux.just(factory.wrap(bytes)), type, MediaType.APPLICATION_JSON, Map.of()).block());
            assertEquals(1, codec.decode(Flux.just(factory.wrap(bytes)), type, MediaType.APPLICATION_JSON, Map.of()).collectList().block().size());
            assertFalse(messages(capture).contains("PRIVATE_PROVIDER_BODY"));
            assertFalse(messages(capture).contains("private-key"));
        } finally { logger.detachAppender(capture); logger.setLevel(previous); capture.stop(); }
    }
    @Test void nativeClientAndAdapterNeverExposeCredentialResponseOrErrorCauseInCompleteLogAndStackBytes() {
        var logger = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var capture = new ListAppender<ILoggingEvent>(); capture.start();
        var previous = logger.getLevel(); logger.setLevel(Level.TRACE); logger.addAppender(capture);
        try {
            int successes = 0;
            int failures = 0;
            for (String body : List.of(LiveIntrospectionResponseGuardTest.VALID.substring(0, LiveIntrospectionResponseGuardTest.VALID.length()-1)
                    + ",\"extra\":\"PRIVATE_PROVIDER_BODY\"}", "{\"active\":\"PRIVATE_PROVIDER_ERROR\"}")) {
                var originalStrategies = new java.util.concurrent.atomic.AtomicReference<ExchangeStrategies>();
                var constructed = new java.util.concurrent.atomic.AtomicBoolean();
                org.springframework.http.client.reactive.ClientHttpConnector connector = (method, uri, writer) -> {
                    var request = new org.springframework.mock.http.client.reactive.MockClientHttpRequest(method, uri);
                    return writer.apply(request).then(Mono.defer(request::getBodyAsString)).map(form -> {
                        assertEquals("POST", method.name());
                        assertEquals("https://issuer.invalid/introspect", uri.toString());
                        assertEquals("token=PRIVATE_RAW_TOKEN.x.y", form);
                        assertEquals("Basic " + Base64.getEncoder().encodeToString("PRIVATE_CLIENT:PRIVATE_SECRET".getBytes(StandardCharsets.UTF_8)), request.getHeaders().getFirst("Authorization"));
                        constructed.set(true);
                        var response = new org.springframework.mock.http.client.reactive.MockClientHttpResponse(HttpStatus.OK);
                        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        response.setBody(body);
                        return (org.springframework.http.client.reactive.ClientHttpResponse)response;
                    });
                };
                WebClient.Builder builder = SpringLiveTokenValidationAdapter.ownedWebClientBuilder(connector, "PRIVATE_CLIENT", "PRIVATE_SECRET");
                builder.filter((request, next) -> next.exchange(request).doOnNext(response -> originalStrategies.set(response.strategies())));
                builder.filters(filters -> filters.addFirst((request, next) -> next.exchange(request)
                        .doOnNext(response -> assertSame(originalStrategies.get(), response.strategies()))));
                WebClient webClient = builder.build();
                var adapter = new SpringLiveTokenValidationAdapter(new SpringReactiveOpaqueTokenIntrospector("https://issuer.invalid/introspect", webClient),
                        new LiveActiveJwtDecoderTest().properties(), Clock.fixed(LiveActiveJwtDecoderTest.NOW, ZoneOffset.UTC));
                try {
                    var principal = adapter.validateLive(BearerToken.of("PRIVATE_RAW_TOKEN.x.y")).block();
                    assertNotNull(principal);
                    assertFalse(principal.claims().containsKey("extra"));
                    successes++;
                } catch (RuntimeException error) {
                    var writer = new StringWriter(); error.printStackTrace(new PrintWriter(writer));
                    assertNoSensitiveValues(writer.toString()); assertNull(error.getCause());
                    failures++;
                } finally { adapter.close(); }
                assertTrue(constructed.get());
            }
            assertEquals(1, successes);
            assertEquals(1, failures);
            assertNoSensitiveValues(messages(capture));
        } finally { logger.detachAppender(capture); logger.setLevel(previous); capture.stop(); }
    }
    void assertNoSensitiveValues(String rendered) {
        for (String value : List.of(Base64.getEncoder().encodeToString("PRIVATE_CLIENT:PRIVATE_SECRET".getBytes(StandardCharsets.UTF_8)), "PRIVATE_RAW_TOKEN", "PRIVATE_CLIENT", "PRIVATE_SECRET", "PRIVATE_PROVIDER_BODY", "PRIVATE_PROVIDER_ERROR")) {
            assertFalse(rendered.contains(value), value);
        }
    }
    String messages(ListAppender<ILoggingEvent> capture) {
        var text = new StringBuilder();
        for (var event : capture.list) {
            text.append(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) text.append(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        return text.toString();
    }
}
