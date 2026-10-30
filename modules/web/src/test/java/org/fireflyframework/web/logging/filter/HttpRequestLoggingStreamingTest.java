/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.web.logging.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.PooledByteBufAllocator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.fireflyframework.web.logging.config.HttpRequestLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class HttpRequestLoggingStreamingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(HttpRequestLoggingWebFilter.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final NettyDataBufferFactory buffers = new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
    private final List<NettyDataBuffer> allocated = new ArrayList<>();
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
        for (NettyDataBuffer buffer : allocated) {
            while (buffer.getNativeBuffer().refCnt() > 0) DataBufferUtils.release(buffer);
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void firstFrameIsDeliveredBeforeCompletionAndCancellationRemainsVisible(boolean response) {
        var buffer = pooled("first");
        var cancelled = new AtomicBoolean();
        var sink = new Drain(false);
        Flux<DataBuffer> body = Flux.<DataBuffer>concat(Mono.just(buffer), Mono.never())
                .doOnCancel(() -> cancelled.set(true));
        Disposable subscription = transfer(response, true, body, sink).subscribe();
        try {
            assertThat(sink.received).containsExactly(buffer);
            assertThat(sink.text()).isEqualTo("first");
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        } finally {
            subscription.dispose();
        }
        assertThat(cancelled).isTrue();
        assertThat(bodyEvents()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("directions")
    void oneBufferDemandDoesNotBecomeUnboundedPrefetch(boolean response) {
        var requested = new ArrayList<Long>();
        var buffer = pooled("one");
        Flux<DataBuffer> body = Flux.<DataBuffer>concat(Mono.just(buffer), Mono.never())
                .doOnRequest(requested::add);
        var sink = new Drain(true);
        Disposable subscription = transfer(response, true, body, sink).subscribe();
        try {
            assertThat(requested).containsExactly(1L);
            assertThat(sink.received).containsExactly(buffer);
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        } finally {
            subscription.dispose();
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void originalErrorAndPooledOwnershipMatchBodyLoggingOff(boolean response) {
        RuntimeException failure = new IllegalStateException("synthetic-upstream-failure");
        for (boolean enabled : new boolean[] {false, true}) {
            events.list.clear();
            var buffer = pooled("before-error");
            var sink = new Drain(false);
            var observed = new AtomicReference<Throwable>();
            transfer(response, enabled, Flux.concat(Mono.just(buffer), Flux.error(failure)), sink)
                    .onErrorResume(error -> { observed.set(error); return Mono.empty(); }).block();
            assertThat(observed.get()).isSameAs(failure);
            assertThat(sink.received).containsExactly(buffer);
            assertThat(sink.text()).isEqualTo("before-error");
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void cancellationAndPooledOwnershipMatchBodyLoggingOff(boolean response) {
        for (boolean enabled : new boolean[] {false, true}) {
            events.list.clear();
            var buffer = pooled("before-cancel");
            var sink = new Drain(false);
            var cancelled = new AtomicBoolean();
            Disposable subscription = transfer(response, enabled,
                    Flux.<DataBuffer>concat(Mono.just(buffer), Mono.never())
                            .doOnCancel(() -> cancelled.set(true)), sink).subscribe();
            subscription.dispose();
            assertThat(cancelled).isTrue();
            assertThat(sink.received).containsExactly(buffer);
            assertThat(sink.text()).isEqualTo("before-cancel");
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("directions")
    void subscriptionsHaveIndependentPrefixBudgetsAndNeverReplaceBuffers(boolean response) {
        var sink = new Drain(false);
        Flux<DataBuffer> body = Flux.defer(() -> Flux.just(pooled("!abc", 1), pooled("def")));
        var properties = properties(true, true, 4);
        var filter = new HttpRequestLoggingWebFilter(properties, Optional.empty());
        ServerWebExchange exchange = exchange(response ? Flux.empty() : body, sink);
        Mono<Void> operation = filter.filter(exchange, actual -> response
                ? actual.getResponse().writeWith(body) : sink.consume(actual.getRequest().getBody()));
        operation.repeat(1).blockLast();

        assertThat(sink.text()).isEqualTo("abcdefabcdef");
        assertThat(sink.received).hasSize(4);
        assertThat(sink.positions).containsExactly(1, 0, 1, 0);
        assertThat(bodyValues()).containsExactly("abcd", "abcd");
        assertThat(sink.received).allSatisfy(buffer ->
                assertThat(((NettyDataBuffer) buffer).getNativeBuffer().refCnt()).isZero());
    }

    @ParameterizedTest
    @MethodSource("directions")
    void fragmentedUtf8IsDecodedAfterTheBytePrefixWithoutChangingTraffic(boolean response) {
        byte[] wire = "α😀Z".getBytes(StandardCharsets.UTF_8);
        var source = new ArrayList<DataBuffer>();
        for (byte value : wire) source.add(pooled(new byte[] {value}));
        var sink = new Drain(false);
        transfer(response, properties(true, true, 6), Flux.fromIterable(source), sink).block();

        assertThat(sink.bytes.toByteArray()).isEqualTo(wire);
        assertThat(sink.received).containsExactlyElementsOf(source);
        assertThat(bodyValues()).containsExactly("α😀");
    }

    @ParameterizedTest
    @MethodSource("directions")
    void zeroBudgetDoesNotLogABodyOrChangeTraffic(boolean response) {
        var buffer = pooled("all-business-bytes");
        var sink = new Drain(false);
        transfer(response, properties(true, true, 0), Flux.just(buffer), sink).block();
        assertThat(sink.received).containsExactly(buffer);
        assertThat(sink.text()).isEqualTo("all-business-bytes");
        assertThat(bodyEvents()).isEmpty();
        assertThat(buffer.getNativeBuffer().refCnt()).isZero();
    }

    @ParameterizedTest
    @MethodSource("directions")
    void copyingStopsAtTheConfiguredByteBudget(boolean response) {
        var reads = new AtomicInteger();
        var buffer = spy(pooled("!abcdefghij", 1));
        doAnswer(invocation -> {
            reads.incrementAndGet();
            return invocation.callRealMethod();
        }).when(buffer).getByte(anyInt());
        var sink = new Drain(false);
        transfer(response, properties(true, true, 4), Flux.just(buffer), sink).block();
        assertThat(reads.get()).isEqualTo(4);
        assertThat(sink.text()).isEqualTo("abcdefghij");
        assertThat(sink.received).containsExactly(buffer);
        assertThat(sink.positions).containsExactly(1);
        assertThat(bodyValues()).containsExactly("abcd");
        assertThat(buffer.getNativeBuffer().refCnt()).isZero();
    }

    @ParameterizedTest
    @MethodSource("directions")
    void aFailedDiagnosticCopyCannotReplaceBusinessSuccessOrError(boolean response) {
        for (boolean sourceFails : new boolean[] {false, true}) {
            events.list.clear();
            var buffer = spy(pooled("business-bytes"));
            doThrow(new IllegalStateException("diagnostic-read-unavailable")).when(buffer).getByte(anyInt());
            var failure = new IllegalStateException("original-business-error");
            var observed = new AtomicReference<Throwable>();
            var sink = new Drain(false);
            transfer(response, true, Flux.concat(Mono.just(buffer), sourceFails ? Flux.error(failure) : Flux.empty()), sink)
                    .onErrorResume(error -> { observed.set(error); return Mono.empty(); }).block();
            assertThat(observed.get()).isSameAs(sourceFails ? failure : null);
            assertThat(sink.text()).isEqualTo("business-bytes");
            assertThat(sink.received).containsExactly(buffer);
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        }
    }

    @Test
    void flushGroupsStaySeparateAndShareOneResponsePrefixBudget() {
        var sink = new Drain(false);
        var first = pooled("abc");
        var second = pooled("def");
        var filter = new HttpRequestLoggingWebFilter(properties(false, true, 5), Optional.empty());
        filter.filter(exchange(Flux.empty(), sink), actual -> actual.getResponse().writeAndFlushWith(
                Flux.just(Flux.just(first), Flux.just(second)))).block();

        assertThat(sink.flushWrites).isOne();
        assertThat(sink.plainWrites).isZero();
        assertThat(sink.groups).containsExactly(List.of(first), List.of(second));
        assertThat(sink.text()).isEqualTo("abcdef");
        assertThat(bodyValues()).containsExactly("abcde");
        assertThat(first.getNativeBuffer().refCnt()).isZero();
        assertThat(second.getNativeBuffer().refCnt()).isZero();
    }

    @Test
    void nonterminalFlushGroupDeliversFirstFrameAndPreservesCancellation() {
        var sink = new Drain(false);
        var buffer = pooled("event");
        var cancelled = new AtomicBoolean();
        var filter = new HttpRequestLoggingWebFilter(properties(false, true, 5), Optional.empty());
        Disposable subscription = filter.filter(exchange(Flux.empty(), sink), actual -> actual.getResponse()
                .writeAndFlushWith(Flux.just(Flux.<DataBuffer>concat(Mono.just(buffer), Mono.never())
                        .doOnCancel(() -> cancelled.set(true))))).subscribe();
        try {
            assertThat(sink.text()).isEqualTo("event");
            assertThat(sink.flushWrites).isOne();
            assertThat(sink.plainWrites).isZero();
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        } finally {
            subscription.dispose();
        }
        assertThat(cancelled).isTrue();
        assertThat(bodyEvents()).isEmpty();
    }

    @Test
    void flushFailurePreservesOriginalErrorWithoutLoggingAnIncompletePrefix() {
        var sink = new Drain(false);
        var buffer = pooled("partial");
        var failure = new IllegalStateException("flush-source-error");
        var observed = new AtomicReference<Throwable>();
        var filter = new HttpRequestLoggingWebFilter(properties(false, true, 7), Optional.empty());
        filter.filter(exchange(Flux.empty(), sink), actual -> actual.getResponse().writeAndFlushWith(
                Flux.just(Flux.concat(Mono.just(buffer), Flux.error(failure)))))
                .onErrorResume(error -> { observed.set(error); return Mono.empty(); }).block();
        assertThat(observed.get()).isSameAs(failure);
        assertThat(sink.received).containsExactly(buffer);
        assertThat(buffer.getNativeBuffer().refCnt()).isZero();
        assertThat(bodyEvents()).isEmpty();
    }

    @Test
    void transportFailureAfterConsumingTheBodyDoesNotLogACompletedResponseBody() {
        for (boolean flush : new boolean[] {false, true}) {
            events.list.clear();
            var sink = new Drain(false);
            sink.terminalFailure = new IllegalStateException("transport-failure");
            var buffer = pooled("written-prefix");
            var observed = new AtomicReference<Throwable>();
            var filter = new HttpRequestLoggingWebFilter(properties(false, true, 7), Optional.empty());
            filter.filter(exchange(Flux.empty(), sink), actual -> flush
                    ? actual.getResponse().writeAndFlushWith(Flux.just(Flux.just(buffer)))
                    : actual.getResponse().writeWith(Flux.just(buffer)))
                    .onErrorResume(error -> { observed.set(error); return Mono.empty(); }).block();
            assertThat(observed.get()).isSameAs(sink.terminalFailure);
            assertThat(sink.received).containsExactly(buffer);
            assertThat(buffer.getNativeBuffer().refCnt()).isZero();
            assertThat(bodyEvents()).isEmpty();
        }
    }

    static Stream<Arguments> directions() {
        return Stream.of(Arguments.of(false), Arguments.of(true));
    }

    private HttpRequestLoggingProperties properties(boolean request, boolean response, int cap) {
        var result = new HttpRequestLoggingProperties();
        result.setIncludeRequestBody(request);
        result.setIncludeResponseBody(response);
        result.setMaxBodySize(cap);
        return result;
    }

    private Mono<Void> transfer(boolean response, boolean enabled, Flux<DataBuffer> body, Drain sink) {
        return transfer(response, properties(enabled, enabled, 1024), body, sink);
    }

    private Mono<Void> transfer(boolean response, HttpRequestLoggingProperties properties,
                                Flux<DataBuffer> body, Drain sink) {
        var filter = new HttpRequestLoggingWebFilter(properties, Optional.empty());
        return filter.filter(exchange(response ? Flux.empty() : body, sink), actual -> response
                ? actual.getResponse().writeWith(body) : sink.consume(actual.getRequest().getBody()));
    }

    private ServerWebExchange exchange(Flux<DataBuffer> body, Drain sink) {
        return new ServerWebExchangeDecorator(MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/stream").body(body))) {
            @Override
            public ServerHttpResponse getResponse() { return sink; }
        };
    }

    private NettyDataBuffer pooled(String text) { return pooled(text, 0); }

    private NettyDataBuffer pooled(String text, int position) {
        NettyDataBuffer buffer = pooled(text.getBytes(StandardCharsets.UTF_8));
        buffer.readPosition(position);
        return buffer;
    }

    private NettyDataBuffer pooled(byte[] bytes) {
        NettyDataBuffer buffer = buffers.allocateBuffer(bytes.length);
        buffer.write(bytes);
        allocated.add(buffer);
        return buffer;
    }

    private List<String> bodyEvents() {
        return events.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(value -> value.contains("HTTP_REQUEST_BODY") || value.contains("HTTP_RESPONSE_BODY")).toList();
    }

    private List<String> bodyValues() {
        return bodyEvents().stream().map(value -> {
            try { return new ObjectMapper().readTree(value).get("body").asText(); }
            catch (Exception error) { throw new AssertionError(error); }
        }).toList();
    }

    // The test transport consumes and releases buffers; the observer never owns them.
    private final class Drain extends ServerHttpResponseDecorator {
        private final boolean one;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final List<DataBuffer> received = new ArrayList<>();
        private final List<Integer> positions = new ArrayList<>();
        private final List<List<DataBuffer>> groups = new ArrayList<>();
        private int plainWrites;
        private int flushWrites;
        private RuntimeException terminalFailure;

        private Drain(boolean one) {
            super(new MockServerHttpResponse(buffers));
            this.one = one;
        }

        private Mono<Void> consume(Publisher<? extends DataBuffer> body) {
            Flux<? extends DataBuffer> stream = Flux.from(body);
            if (one) stream = stream.take(1);
            return stream.doOnNext(this::accept).then();
        }

        private void accept(DataBuffer buffer) {
            received.add(buffer);
            positions.add(buffer.readPosition());
            try {
                try (var views = buffer.readableByteBuffers()) {
                    while (views.hasNext()) {
                        var view = views.next();
                        while (view.hasRemaining()) bytes.write(view.get());
                    }
                }
            } finally {
                DataBufferUtils.release(buffer);
            }
        }

        private String text() { return bytes.toString(StandardCharsets.UTF_8); }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            plainWrites++;
            return consume(body).then(Mono.defer(() -> terminalFailure == null
                    ? Mono.empty() : Mono.error(terminalFailure)));
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            flushWrites++;
            return Flux.from(body).concatMap(group -> {
                var receivedGroup = new ArrayList<DataBuffer>();
                groups.add(receivedGroup);
                return consume(Flux.from(group).doOnNext(receivedGroup::add));
            }).then(Mono.defer(() -> terminalFailure == null
                    ? Mono.empty() : Mono.error(terminalFailure)));
        }
    }
}
