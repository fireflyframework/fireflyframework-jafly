package org.fireflyframework.cache.adapter.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.serialization.JsonCacheSerializerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresCacheNamespaceTest {
    final ConnectionFactory factory=mock(ConnectionFactory.class);
    final Connection connection=mock(Connection.class);
    final Statement statement=mock(Statement.class,RETURNS_SELF);
    final Result result=mock(Result.class);
    final Row row=mock(Row.class);
    final List<Object> boundNamespaces=new ArrayList<>();

    @SuppressWarnings({"unchecked","rawtypes"})
    PostgresCacheNamespaceTest() {
        doReturn(Mono.just(connection)).when(factory).create();when(connection.close()).thenReturn(Mono.empty());
        when(connection.createStatement(anyString())).thenReturn(statement);
        when(statement.bind(eq("$1"),any())).thenAnswer(call -> { boundNamespaces.add(call.getArgument(1));return statement; });
        doReturn(Flux.just(result)).when(statement).execute();when(result.getRowsUpdated()).thenReturn(Mono.just(1L));
        when(row.get("cache_key")).thenReturn("service:logical:visible");when(row.get("cnt")).thenReturn(1L);
        when(row.get("value")).thenReturn("\"value\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(result.map(any(BiFunction.class))).thenAnswer(call -> Flux.just(((BiFunction)call.getArgument(0))
                .apply(row,mock(RowMetadata.class))));
    }

    PostgresCacheAdapterImpl adapter(String namespace,String logical) {
        return new PostgresCacheAdapterImpl(logical,factory,PostgresCacheConfig.builder().keyPrefix(namespace).build(),
                new JsonCacheSerializerImpl(new ObjectMapper()));
    }

    @ParameterizedTest @CsvSource(delimiter='|',value={
        "service|logical|v2:161fe517a9798c0b568c5f4c393f30dc87cdabef45e9a3f19c74e937cb88c4bc",
        "a:b|c|v2:df863f0f4ce7b84d6527341fc21f476d10f2b270c5fc9a7012506fa0b60e9dad",
        "a|b:c|v2:4ec7ad5cd3dec58c6d7edf668d9a85a52caf559638764ddabac361ffbd89bfa0",
        "服务😀|logical:ñ|v2:61782f3c7b6a89b090647559aca9f8f5dbca9eb85503b0244e91486902dd30bc"})
    void physicalNamespaceMatchesIndependentVersionedUtf8GoldenCorpus(String namespace,String logical,String expected) {
        adapter(namespace,logical).clear().block();
        assertThat(boundNamespaces).containsExactly(expected);assertThat(expected.length()).isLessThanOrEqualTo(255);
    }

    @ParameterizedTest @NullSource @ValueSource(strings={""," "})
    void directPostgresAdapterRequiresAnExplicitNamespace(String prefix) {
        assertThatThrownBy(() -> adapter(prefix,"logical")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }

    @Test void allKeyAndBulkOperationsUseTheSameExactNamespaceAndExposeLogicalMetadata() {
        var cache=adapter("service","logical");
        cache.get("visible",String.class).block();cache.put("visible","value").block();
        cache.putIfAbsent("visible","value").block();cache.evict("visible").block();
        cache.evictByPrefix("vis").block();cache.exists("visible").block();
        assertThat(cache.<String>keys().block()).containsExactly("visible");
        assertThat(cache.size().block()).isEqualTo(1L);cache.clear().block();
        assertThat(boundNamespaces).hasSize(9).allMatch("v2:161fe517a9798c0b568c5f4c393f30dc87cdabef45e9a3f19c74e937cb88c4bc"::equals);
        assertThat(cache.getCacheName()).isEqualTo("logical");
    }

    @Test void nestedExplicitPrefixesCannotSharePhysicalCacheNames() {
        adapter("service","logical").clear().block();adapter("service:logical","logical").clear().block();
        assertThat(boundNamespaces).hasSize(2).doesNotHaveDuplicates();
    }
}
