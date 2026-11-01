package org.fireflyframework.cache.adapter.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import org.fireflyframework.cache.core.*;
import org.fireflyframework.cache.serialization.JsonCacheSerializer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;

class PostgresPrimaryOnlyPolicyTest {
    final ConnectionFactory factory=mock(ConnectionFactory.class);
    final Connection connection=mock(Connection.class);
    final Statement statement=mock(Statement.class,RETURNS_SELF);
    final Result result=mock(Result.class);
    final Row row=mock(Row.class);
    @SuppressWarnings({"rawtypes","unchecked"})
    PostgresPrimaryOnlyPolicyTest() {
        doReturn(Mono.just(connection)).when(factory).create();when(connection.close()).thenReturn(Mono.empty());
        when(connection.createStatement(anyString())).thenReturn(statement);doReturn(Flux.just(result)).when(statement).execute();
        when(result.getRowsUpdated()).thenReturn(Mono.just(1L));
        when(row.get("value")).thenReturn("value".getBytes(StandardCharsets.UTF_8));
        when(result.map(any(BiFunction.class))).thenAnswer(call->Flux.just(((BiFunction<Row,RowMetadata,Object>)call.getArgument(0)).apply(row,mock(RowMetadata.class))));
    }
    PostgresCacheAdapter strict() {return new PostgresCacheAdapter("policy",factory,PostgresCacheConfig.builder().keyPrefix("service").build(),new JsonCacheSerializer(new ObjectMapper()),"service",CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);}
    PostgresCacheAdapter legacy() {return new PostgresCacheAdapter("policy",factory,PostgresCacheConfig.builder().keyPrefix("service").build(),new JsonCacheSerializer(new ObjectMapper()));}
    @Test void acquireFailureIsNotAMissFalseOrSuccessfulWrite() {
        doReturn(Mono.error(new IllegalStateException("controlled acquire failure"))).when(factory).create();var cache=strict();
        for(var operation:List.of(cache.get("key",String.class),cache.exists("key"),cache.evict("key"),cache.put("key","value"),cache.clear(),cache.getStats()))
            StepVerifier.create(operation).expectError().verify();
    }
    @Test void emptyAcquisitionCannotImplyMissConflictOrCompletion() {
        doReturn(Mono.empty()).when(factory).create();var cache=strict();
        for(var operation:List.of(cache.get("key",String.class),cache.exists("key"),cache.putIfAbsent("key","value"),cache.put("key","value"),cache.clear()))
            StepVerifier.create(operation).expectError().verify();
    }
    @Test void statementFailureDoesNotBecomeEvictionFalseOrMissingValue() {
        doReturn(Flux.error(new IllegalStateException("controlled statement failure"))).when(statement).execute();var cache=strict();
        StepVerifier.create(cache.get("key",String.class)).expectError().verify();StepVerifier.create(cache.evict("key")).expectError().verify();
        StepVerifier.create(cache.exists("key")).expectError().verify();
    }
    @Test void deserializeFailureIsNotAMiss() {
        when(row.get("value")).thenReturn("{".getBytes(StandardCharsets.UTF_8));
        StepVerifier.create(strict().get("key",Map.class)).expectError().verify();
    }
    @Test void nullDecodeAndUnsupportedReadableTypeRefuse() {
        when(row.get("value")).thenReturn("null".getBytes(StandardCharsets.UTF_8));
        StepVerifier.create(strict().get("key",Map.class)).expectError().verify();
        when(row.get("value")).thenReturn(new Object());StepVerifier.create(strict().get("key",String.class)).expectError().verify();
    }
    @Test void releaseFailurePreventsValueOrVoidSuccess() {
        when(connection.close()).thenReturn(Mono.error(new IllegalStateException("controlled release failure")));
        StepVerifier.create(strict().get("key",String.class)).expectError().verify();StepVerifier.create(strict().put("key","value")).expectError().verify();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @Test void successfulNoRowReadAndExistingEntryRemainDistinctFromFailure() {
        when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());
        StepVerifier.create(strict().get("key",String.class)).expectNext(Optional.empty()).verifyComplete();
        StepVerifier.create(strict().exists("key")).expectNext(false).verifyComplete();
        when(result.getRowsUpdated()).thenReturn(Mono.just(0L));StepVerifier.create(strict().putIfAbsent("key","value")).expectNext(false).verifyComplete();
    }
    @Test void emptyOrInvalidUpdateResultCannotBecomeFalseOrCompletion() {
        when(result.getRowsUpdated()).thenReturn(Mono.empty());
        StepVerifier.create(strict().putIfAbsent("key","value")).expectError().verify();StepVerifier.create(strict().clear()).expectError().verify();
        doReturn(Flux.empty()).when(statement).execute();StepVerifier.create(strict().evict("key")).expectError().verify();
    }
    @Test void absentStatementResultCannotMasqueradeAsASuccessfulRead() {
        doReturn(Flux.empty()).when(statement).execute();var cache=strict();
        for(var operation:List.of(cache.get("key",String.class),cache.exists("key"),cache.keys(),cache.size()))
            StepVerifier.create(operation).expectError().verify();
    }
    @Test void nullWritesRefuseBeforeAnyConnectionAcquisition() {
        StepVerifier.create(strict().put("key",null)).expectError().verify();StepVerifier.create(strict().putIfAbsent("key",null)).expectError().verify();verifyNoInteractions(factory);
    }
    @Test void missingOrUnsupportedSerializedValueRefusesBeforeAcquisition() {
        var serializer=mock(org.fireflyframework.cache.serialization.CacheSerializer.class);
        var cache=new PostgresCacheAdapter("policy",factory,PostgresCacheConfig.builder().keyPrefix("service").build(),serializer,"service",CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
        StepVerifier.create(cache.put("key","value")).expectError().verify();
        when(serializer.serialize("value")).thenReturn(new Object());StepVerifier.create(cache.put("key","value")).expectError().verify();verifyNoInteractions(factory);
    }
    @Test void directStrictAssemblyFailureIsReactive() {
        var cache=strict();assertThatCode(()->cache.get(null,String.class)).doesNotThrowAnyException();
        StepVerifier.create(cache.get(null,String.class)).expectError().verify();
    }
    @Test void independentLegacyInstanceRetainsRecoveryInsteadOfInheritingStrictState() {
        doReturn(Mono.error(new IllegalStateException("controlled same-backend failure"))).when(factory).create();
        assertThat(legacy().operationPolicy()).isEqualTo(CacheOperationPolicy.LEGACY);
        assertThat(strict().operationPolicy()).isEqualTo(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
        StepVerifier.create(legacy().get("key",String.class)).expectNext(Optional.empty()).verifyComplete();
        StepVerifier.create(strict().get("key",String.class)).expectError().verify();
    }
}
