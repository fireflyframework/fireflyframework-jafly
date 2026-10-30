package org.fireflyframework.cache.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.fireflyframework.cache.adapter.postgres.PostgresCacheAdapter;
import org.fireflyframework.cache.adapter.postgres.PostgresCacheConfig;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PostgresCacheStartupTest {
    final CacheProperties properties = new CacheProperties();
    final ConnectionFactory factory = mock(ConnectionFactory.class);
    final Connection connection = mock(Connection.class);
    final Statement statement = mock(Statement.class);
    final Result result = mock(Result.class);
    final List<String> sql = new ArrayList<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    PostgresCacheStartupTest() {
        properties.getPostgres().setKeyPrefix("synthetic-service");
        doReturn(Mono.just(connection)).when(factory).create();
        when(connection.createStatement(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return statement; });
        doReturn(Flux.just(result)).when(statement).execute();
        when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());
        when(result.getRowsUpdated()).thenReturn(Mono.just(0L));
        when(connection.close()).thenReturn(Mono.empty());
    }

    PostgresCacheAdapter create() {
        return (PostgresCacheAdapter) PostgresCacheHelper.createPostgresCacheAdapter(
                "orders", "tenant", Duration.ofMinutes(7), factory, properties, new ObjectMapper());
    }

    @Test void selectedProviderChecksActualTechnicalTableWithoutDdlAndClosesConnection() {
        create();
        assertThat(sql).singleElement().asString().contains("SELECT", "\"firefly_cache\".\"firefly_cache_entries\"", "cache_name", "cache_key", "expires_at");
        assertThat(sql).allMatch(value -> !value.contains("CREATE") && !value.contains("INSERT"));
        verify(connection).close();
    }

    @Test void missingTechnicalTableFailsStartupAndDoesNotLeakDriverMessage() {
        when(connection.createStatement(anyString())).thenThrow(new IllegalStateException("password=private-token"));
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL cache").hasMessageNotContaining("private-token");
        verify(connection).close();
    }

    @Test void noImplicitLocalFallbackOrL1AndPostgresMetadataAreRetained() {
        properties.getPostgres().setCacheName("orders");
        properties.getPostgres().setKeyPrefix("tenant");
        properties.getPostgres().setDefaultTtl(Duration.ofMinutes(7));
        var manager = new CacheManagerFactory(properties, new ObjectMapper(), null, null, null, factory)
                .createDefaultCacheManager("default");
        assertThat(manager.getCacheType()).isEqualTo(CacheType.POSTGRES);
        assertThat(manager.getCacheName()).isEqualTo("orders");
        assertThat(ReflectionTestUtils.getField(manager, "fallbackCache")).isNull();
        var adapter = ReflectionTestUtils.getField(manager, "primaryCache");
        var config = (PostgresCacheConfig) ReflectionTestUtils.getField(adapter, "config");
        assertThat(config.getKeyPrefix()).isEqualTo("tenant");
        assertThat(config.getDefaultTtl()).isEqualTo(Duration.ofMinutes(7));
    }

    @Test void configuredPostgresAutoSelectionPrecedesOtherProviders() {
        properties.setDefaultCacheType(CacheType.AUTO);
        properties.getCaffeine().setEnabled(false);
        var manager = new CacheManagerFactory(properties, new ObjectMapper(), null, null, null, factory)
                .createDefaultCacheManager("default");
        assertThat(manager.getCacheType()).isEqualTo(CacheType.POSTGRES);
    }

    @Test void schemaIdentifierIsQuotedIncludingEmbeddedQuote() {
        properties.getPostgres().setSchema("tenant\"cache");
        create();
        assertThat(sql).singleElement().asString().contains("\"tenant\"\"cache\".");
    }

    @Test void invalidIdentifierIsRejectedBeforeAnyConnection() {
        properties.getPostgres().setSchema("x".repeat(64));
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }

    @Test void oversizeCacheNameIsRejectedBeforeAnyConnection() {
        assertThatThrownBy(() -> PostgresCacheHelper.createPostgresCacheAdapter("x".repeat(256), "", null,
                factory, properties, new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }

    @Test void negativeDefaultTtlIsRejectedBeforeAnyConnection() {
        assertThatThrownBy(() -> PostgresCacheHelper.createPostgresCacheAdapter("orders", "tenant", Duration.ofSeconds(-1),
                factory, properties, new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(factory);
    }

    @Test void explicitSmartOptInIsRetained() {
        properties.getSmart().setEnabled(true);
        var manager = new CacheManagerFactory(properties, new ObjectMapper(), null, null, null, factory)
                .createDefaultCacheManager("default");
        assertThat(ReflectionTestUtils.getField(manager, "primaryCache").getClass().getSimpleName())
                .isEqualTo("SmartCacheAdapter");
    }

    @Test void automaticAdapterSelectionPrefersPostgresToRedis() {
        var postgres = mock(org.fireflyframework.cache.core.CacheAdapter.class);
        var redis = mock(org.fireflyframework.cache.core.CacheAdapter.class);
        when(postgres.isAvailable()).thenReturn(true);
        when(redis.isAvailable()).thenReturn(true);
        when(postgres.getCacheType()).thenReturn(CacheType.POSTGRES);
        when(redis.getCacheType()).thenReturn(CacheType.REDIS);
        when(postgres.getCacheName()).thenReturn("z-postgres");
        when(redis.getCacheName()).thenReturn("a-redis");
        assertThat(new org.fireflyframework.cache.manager.AutoCacheSelectionStrategy()
                .selectCache(List.of(redis, postgres))).contains(postgres);
    }

    @Test void automaticDdlRequiresExplicitOptInAndNeverCreatesRolesOrGrants() {
        properties.getPostgres().setAutoCreateSchema(true);
        create();
        assertThat(sql).hasSize(4);
        assertThat(sql.getFirst()).startsWith("CREATE SCHEMA IF NOT EXISTS");
        assertThat(sql.getLast()).startsWith("SELECT");
        assertThat(sql).noneMatch(value -> value.contains("GRANT") || value.contains("ROLE"));
    }

    @Test void initializationDeadlineClosesTheAcquiredConnection() {
        properties.getPostgres().setInitializationTimeout(Duration.ofMillis(100));
        properties.getPostgres().setShutdownTimeout(Duration.ofMillis(100));
        doReturn(Flux.never()).when(statement).execute();
        assertThatThrownBy(this::create).isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot initialize PostgreSQL cache storage");
        verify(connection).close();
    }
}
