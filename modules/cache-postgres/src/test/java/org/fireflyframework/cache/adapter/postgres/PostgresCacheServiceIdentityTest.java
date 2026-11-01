package org.fireflyframework.cache.adapter.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.factory.PostgresCacheHelper;
import org.fireflyframework.cache.properties.CacheProperties;
import org.fireflyframework.cache.serialization.CacheSerializer;
import org.fireflyframework.cache.serialization.JsonCacheSerializerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresCacheServiceIdentityTest {
    final ConnectionFactory factory=mock(ConnectionFactory.class);
    final Connection connection=mock(Connection.class);
    final Statement statement=mock(Statement.class,RETURNS_SELF);
    final Result result=mock(Result.class);
    final AtomicReference<String> physical=new AtomicReference<>();
    final AtomicReference<String> fullKey=new AtomicReference<>();
    final CacheSerializer serializer=new JsonCacheSerializerImpl(new ObjectMapper());
    @SuppressWarnings({"unchecked","rawtypes"})
    PostgresCacheServiceIdentityTest() {
        doReturn(Mono.just(connection)).when(factory).create();when(connection.close()).thenReturn(Mono.empty());
        when(connection.createStatement(anyString())).thenReturn(statement);
        when(statement.bind(eq("$1"),any())).thenAnswer(call->{physical.set(call.getArgument(1));return statement;});
        when(statement.bind(eq("$2"),any())).thenAnswer(call->{fullKey.set(call.getArgument(1));return statement;});
        doReturn(Flux.just(result)).when(statement).execute();when(result.getRowsUpdated()).thenReturn(Mono.just(0L));
        when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());
    }
    PostgresCacheAdapterImpl helper(String service,String logical,String feature) {
        var properties=new CacheProperties();properties.getPostgres().setKeyPrefix(service);
        return (PostgresCacheAdapterImpl)PostgresCacheHelper.createPostgresCacheAdapter(logical,feature,Duration.ofMinutes(1),
                factory,properties,new ObjectMapper());
    }
    String capture(PostgresCacheAdapterImpl cache) {cache.clear().block();return physical.get();}
    @Test void fixedFeaturePrefixCannotOverrideDifferentServiceNamespaces() {
        assertThat(capture(helper("service-a","logical","fixed-feature")))
                .isNotEqualTo(capture(helper("service-b","logical","fixed-feature")));
    }
    @Test void differentFeaturesWithinOneServiceRemainSeparate() {
        assertThat(capture(helper("service","logical","feature-a")))
                .isNotEqualTo(capture(helper("service","logical","feature-b")));
    }
    @Test void fixedFeatureCannotSatisfyAMissingServiceNamespace() {
        assertThatThrownBy(()->helper("","logical","fixed-feature")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }
    @Test void nullFeatureFallsBackToTheServicePrefix() {
        assertThat(capture(helper("service","logical",null))).isEqualTo(capture(helper("service","logical","service")));
    }
    @Test void blankFeatureUsesTheLegacyKeyFormatWithoutReplacingTheServiceNamespace() {
        var empty=helper("service","logical","");String emptyPhysical=capture(empty);empty.get("key").block();
        assertThat(fullKey.get()).isEqualTo("cache:logical:key");
        assertThat(capture(helper("service","logical","   "))).isEqualTo(emptyPhysical);
        assertThat(capture(helper("other-service","logical",""))).isNotEqualTo(emptyPhysical);
    }
    @Test void retainedDirectConstructorAndHelperAgreeWhenServiceAndFeatureCoincide() {
        var direct=new PostgresCacheAdapterImpl("logical",factory,PostgresCacheConfig.builder().keyPrefix("service").build(),serializer);
        assertThat(capture(direct)).isEqualTo(capture(helper("service","logical","service")));
    }
    @Test void explicitNamespaceOverloadAndHelperHaveTheSameIdentity() throws Exception {
        var constructor=PostgresCacheAdapterImpl.class.getConstructor(String.class,ConnectionFactory.class,
                PostgresCacheConfig.class,CacheSerializer.class,String.class);
        var direct=constructor.newInstance("logical",factory,PostgresCacheConfig.builder().keyPrefix("feature").build(),serializer,"service");
        assertThat(capture(direct)).isEqualTo(capture(helper("service","logical","feature")));
    }
    @ParameterizedTest @CsvSource(delimiter='|',value={        "service|logical|feature|v2:e8cec84e6f0a0be9f2ca4ccec676063bcdb21850f77261e9f3e6dacd59a0b098",
        "a:b|c|d|v2:a30e4b6c0495c13c04afc1b97a1f400b1a086ef922d94cd5f52b624c5ff58355",
        "a|b:c|d|v2:466be9d4a365be5ac1247ff7ece882807be1a3d0a6b5a389ee28f17c43ddada9",
        "服务😀|logical:ñ|feature%_\\|v2:83d9f1e54618a1d63305e0fac548502830da5330fc83f0ea28f977a58ce2f33a"})
    void exactV2TripleMatchesIndependentUtf8GoldenValues(String service,String logical,String feature,String expected) {
        assertThat(capture(helper(service,logical,feature))).isEqualTo(expected);
    }
    static java.util.stream.Stream<String> malformed() {
        char high=(char)0xD800,low=(char)0xDC00;
        return java.util.stream.Stream.of(String.valueOf(high),String.valueOf(low),"a"+high,high+"a",low+"a");
    }
    @ParameterizedTest @MethodSource("malformed")
    void malformedServiceCannotHashAsLiteralReplacementCharacters(String service) {
        assertThat(capture(helper("?","logical","feature"))).isNotBlank();
        clearInvocations(factory);
        assertThatThrownBy(()->helper(service,"logical","feature")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @MethodSource("malformed")
    void malformedLogicalNameOrFeatureIsRejectedBeforeDatabaseUse(String invalid) {
        assertThatThrownBy(()->helper("service",invalid,"feature")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->helper("service","logical",invalid)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @MethodSource("malformed")
    void malformedIdentifiersAreRejectedBeforeUtf8ByteCounting(String invalid) {
        assertThatThrownBy(()->PostgresCacheConfig.quoteIdentifier(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
}
