package org.fireflyframework.cache.factory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.function.BiFunction;
import org.fireflyframework.cache.core.*;
import org.fireflyframework.cache.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.*;

class PostgresPrimaryOnlyWiringTest {
    final CacheProperties properties=new CacheProperties();
    final ConnectionFactory factory=mock(ConnectionFactory.class);
    final Connection connection=mock(Connection.class);
    final Statement statement=mock(Statement.class,RETURNS_SELF);
    final Result result=mock(Result.class);
    @SuppressWarnings({"rawtypes","unchecked"})
    PostgresPrimaryOnlyWiringTest(){properties.getPostgres().setKeyPrefix("service");doReturn(Mono.just(connection)).when(factory).create();when(connection.close()).thenReturn(Mono.empty());when(connection.createStatement(anyString())).thenReturn(statement);doReturn(Flux.just(result)).when(statement).execute();when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());}
    @Test void genuineProviderHelperAdapterChainRetainsStrictPolicyWithoutSmartOrL1(){
        properties.getSmart().setEnabled(true);properties.getCaffeine().setEnabled(true);
        var manager=new CacheManagerFactory(properties,new ObjectMapper(),null,null,null,factory).createCacheManager(
                "policy",CacheType.POSTGRES,"service:policy",Duration.ofMinutes(1),"policy test","test",CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
        assertThat(manager.operationPolicy()).isEqualTo(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
        var primary=(CacheAdapter)ReflectionTestUtils.getField(manager,"primaryCache");
        assertThat(primary).isInstanceOf(org.fireflyframework.cache.adapter.postgres.PostgresCacheAdapterImpl.class);
        assertThat(primary.operationPolicy()).isEqualTo(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
        assertThat(ReflectionTestUtils.getField(manager,"fallbackCache")).isNull();verify(connection).close();
    }
    @Test void oldAndExplicitHelperOverloadsConstructIndependentPolicies(){
        var legacy=PostgresCacheHelper.createPostgresCacheAdapter("legacy","service:legacy",Duration.ofMinutes(1),factory,properties,new ObjectMapper());
        var strict=PostgresCacheHelper.createPostgresCacheAdapter("strict","service:strict",Duration.ofMinutes(1),factory,properties,new ObjectMapper(),CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
        assertThat(legacy.operationPolicy()).isEqualTo(CacheOperationPolicy.LEGACY);assertThat(strict.operationPolicy()).isEqualTo(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
    }
}
