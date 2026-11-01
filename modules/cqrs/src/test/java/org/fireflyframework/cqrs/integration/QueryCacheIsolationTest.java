package org.fireflyframework.cqrs.integration;

import org.fireflyframework.cache.config.CacheAutoConfiguration;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.cache.QueryCacheAdapter;
import org.fireflyframework.cqrs.config.CqrsAutoConfiguration;
import org.fireflyframework.cqrs.context.ExecutionContext;
import org.fireflyframework.cqrs.query.DefaultQueryBusImpl;
import org.fireflyframework.cqrs.query.Query;
import org.fireflyframework.cqrs.query.QueryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {CacheAutoConfiguration.class, CqrsAutoConfiguration.class,
        QueryCacheIsolationTest.TestConfig.class})
@TestPropertySource(properties = {"firefly.cache.enabled=true", "firefly.cache.default-cache-type=CAFFEINE"})
class QueryCacheIsolationTest {
    @Configuration
    static class TestConfig { }

    @Autowired DefaultQueryBusImpl bus;
    @Autowired QueryCacheAdapter cache;
    private UnkeyedHandler unkeyed;
    private KeyedHandler keyed;
    private FreshHandler fresh;

    @BeforeEach
    void reset() {
        cache.clear().block();
        unkeyed = new UnkeyedHandler();
        keyed = new KeyedHandler();
        fresh = new FreshHandler();
        bus.registerHandler(unkeyed);
        bus.registerHandler(keyed);
        bus.registerHandler(fresh);
    }

    @Test
    void defaultKeyDoesNotGuessTheCompleteQueryIdentity() {
        assertThat(new Unkeyed("request-a").getCacheKey()).isNull();
        Query<String> metadataOnly = new Query<>() {
            @Override public Map<String, Object> getMetadata() { return Map.of("page", 1); }
        };
        assertThat(metadataOnly.getCacheKey()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void differentInputsNeverShareAnImplicitClassKey(boolean contextual) {
        assertThat(read(new Unkeyed("request-a"), contextual)).isEqualTo("request-a");
        assertThat(read(new Unkeyed("request-b"), contextual)).isEqualTo("request-b");
        assertThat(read(new Unkeyed("request-a"), contextual)).isEqualTo("request-a");
        assertThat(unkeyed.calls).hasValue(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitlyScopedKeysStillCacheAndIsolateInputs(boolean contextual) {
        assertThat(read(new Keyed("request-a"), contextual)).isEqualTo("request-a");
        assertThat(read(new Keyed("request-b"), contextual)).isEqualTo("request-b");
        assertThat(read(new Keyed("request-a"), contextual)).isEqualTo("request-a");
        assertThat(keyed.calls).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authoritativeHandlerDisablesBothCacheReadsAndWrites(boolean contextual) {
        cache.put("fresh:request-a", "stale").block();
        assertThat(read(new Fresh("request-a"), contextual)).isEqualTo("request-a:1");
        assertThat(read(new Fresh("request-a"), contextual)).isEqualTo("request-a:2");
        assertThat(cache.get("fresh:request-a", String.class).block()).isEqualTo("stale");
    }

    private String read(Query<String> query, boolean contextual) {
        return (contextual ? bus.query(query, ExecutionContext.builder().withTenantId("fixture-tenant").build())
                : bus.query(query)).block();
    }

    record Unkeyed(String requestId) implements Query<String> { }
    record Keyed(String requestId) implements Query<String> {
        @Override public String getCacheKey() { return "fixture-tenant:keyed:" + requestId; }
        @Override public Class<String> getResultType() { return String.class; }
    }
    record Fresh(String requestId) implements Query<String> {
        @Override public String getCacheKey() { return "fresh:" + requestId; }
        @Override public Class<String> getResultType() { return String.class; }
    }
    @QueryHandlerComponent
    static class UnkeyedHandler extends QueryHandler<Unkeyed, String> {
        final AtomicInteger calls = new AtomicInteger();
        @Override protected Mono<String> doHandle(Unkeyed query) {
            calls.incrementAndGet();
            return Mono.just(query.requestId());
        }
    }
    @QueryHandlerComponent
    static class KeyedHandler extends QueryHandler<Keyed, String> {
        final AtomicInteger calls = new AtomicInteger();
        @Override protected Mono<String> doHandle(Keyed query) {
            calls.incrementAndGet();
            return Mono.just(query.requestId());
        }
    }
    @QueryHandlerComponent(cacheable = false)
    static class FreshHandler extends QueryHandler<Fresh, String> {
        @Override protected Mono<String> doHandle(Fresh query) {
            return Mono.just(query.requestId() + ":" + calls.incrementAndGet());
        }
        final AtomicInteger calls = new AtomicInteger();
    }
}
