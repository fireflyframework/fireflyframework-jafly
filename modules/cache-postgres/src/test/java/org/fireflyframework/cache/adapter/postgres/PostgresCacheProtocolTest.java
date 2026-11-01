package org.fireflyframework.cache.adapter.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.*;
import org.fireflyframework.cache.serialization.JsonCacheSerializerImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresCacheProtocolTest {
    final ConnectionFactory factory = mock(ConnectionFactory.class);
    final Connection connection = mock(Connection.class);
    final Statement statement = mock(Statement.class, RETURNS_SELF);
    final Result result = mock(Result.class);
    final Map<String,Object> bindings = new LinkedHashMap<>();
    String sql;

    @SuppressWarnings({"unchecked","rawtypes"})
    PostgresCacheProtocolTest() {
        doReturn(Mono.just(connection)).when(factory).create();
        when(connection.close()).thenReturn(Mono.empty());
        when(connection.createStatement(anyString())).thenAnswer(call -> { sql=call.getArgument(0);bindings.clear();return statement; });
        when(statement.bind(anyString(),any())).thenAnswer(call -> { bindings.put(call.getArgument(0),call.getArgument(1));return statement; });
        doReturn(Flux.just(result)).when(statement).execute();
        when(result.getRowsUpdated()).thenReturn(Mono.just(1L));
    }

    PostgresCacheAdapterImpl adapter() {
        return new PostgresCacheAdapterImpl("logical",factory,PostgresCacheConfig.builder().keyPrefix("service").build(),
                new JsonCacheSerializerImpl(new ObjectMapper()));
    }

    @Test void expiredConflictUsesOneAtomicConditionalUpdateWithPostLockDatabaseClocks() {
        assertThat(adapter().putIfAbsent("key","value",Duration.ofSeconds(2)).block()).isTrue();
        assertThat(sql).startsWith("INSERT INTO \"firefly_cache\".\"firefly_cache_entries\" AS cache_entry");
        assertThat(sql).contains("ON CONFLICT (cache_name, cache_key) DO UPDATE", "value = EXCLUDED.value",
                "WHERE cache_entry.expires_at IS NOT NULL", "cache_entry.expires_at <= pg_catalog.clock_timestamp()");
        String conflict=sql.substring(sql.indexOf("ON CONFLICT"));
        assertThat(conflict).contains("expires_at = pg_catalog.clock_timestamp() +", "created_at = pg_catalog.clock_timestamp()");
        assertThat(conflict).doesNotContain("EXCLUDED.expires_at", "NOW()", "DO NOTHING");
        assertThat(bindings.get("$4")).isEqualTo("2000");
        verify(connection).close();
    }

    static java.util.stream.Stream<Duration> noExpiry() { return java.util.stream.Stream.of(null,Duration.ZERO,Duration.ofMillis(-1)); }
    @ParameterizedTest @MethodSource("noExpiry")
    void nullZeroAndNegativeTtlRemainWithoutExpiryOnInsertAndReplacement(Duration ttl) {
        assertThat(adapter().putIfAbsent("key","value",ttl).block()).isTrue();
        assertThat(sql).contains("$3, NULL", "expires_at = NULL", "cache_entry.expires_at IS NOT NULL");
        assertThat(bindings).doesNotContainKey("$4");
    }

    @ParameterizedTest @ValueSource(longs={-1,2})
    void impossibleAffectedRowCountsCannotAcknowledgeCacheInsertion(long count) {
        when(result.getRowsUpdated()).thenReturn(Mono.just(count));
        assertThatThrownBy(() -> adapter().putIfAbsent("key","value").block()).isInstanceOf(IllegalStateException.class);
    }

    @Test void zeroAffectedRowsMeanExistingLiveEntryAndOneMeansSuccessfulInsertion() {
        when(result.getRowsUpdated()).thenReturn(Mono.just(0L));
        assertThat(adapter().putIfAbsent("key","value").block()).isFalse();
        when(result.getRowsUpdated()).thenReturn(Mono.just(1L));
        assertThat(adapter().putIfAbsent("key","value").block()).isTrue();
    }

    @Test void prefixWildcardsAndEscapeCharactersAreBoundLiterally() {
        adapter().evictByPrefix("customer%_\\folder").block();
        assertThat(sql).contains("cache_key LIKE $2 || '%' ESCAPE E'\\\\'");
        assertThat(bindings.get("$2")).isEqualTo("service:logical:customer\\%\\_\\\\folder");
        assertThat(sql).doesNotContain("customer");
    }

    @Test void ordinaryPutRenewsPositiveTtlWithThePostLockDatabaseClock() {
        adapter().put("key","value",Duration.ofSeconds(2)).block();
        String conflict=sql.substring(sql.indexOf("ON CONFLICT"));
        assertThat(sql).contains("pg_catalog.clock_timestamp()").doesNotContain("NOW()");
        assertThat(conflict).contains("expires_at = pg_catalog.clock_timestamp() +", "created_at = pg_catalog.clock_timestamp()")
                .doesNotContain("EXCLUDED.expires_at");
    }
    static java.util.stream.Stream<Mono<Long>> invalidPutCounts() {
        return java.util.stream.Stream.of(Mono.empty(),Mono.just(-1L),Mono.just(0L),Mono.just(2L));
    }
    @ParameterizedTest @MethodSource("invalidPutCounts")
    void ordinaryPutCannotAcknowledgeMissingOrNonSingleAffectedCounts(Mono<Long> count) {
        when(result.getRowsUpdated()).thenReturn(count);
        assertThatThrownBy(()->adapter().put("key","value").block()).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @MethodSource("noExpiry")
    void ordinaryPutRetainsNullZeroAndNegativeNoExpirySemantics(Duration ttl) {
        adapter().put("key","value",ttl).block();
        assertThat(sql).contains("$3, NULL", "expires_at = NULL");
        assertThat(bindings).doesNotContainKey("$4");
    }
}
