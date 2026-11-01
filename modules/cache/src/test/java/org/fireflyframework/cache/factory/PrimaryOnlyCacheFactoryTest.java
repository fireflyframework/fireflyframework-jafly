package org.fireflyframework.cache.factory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.fireflyframework.cache.core.*;
import org.fireflyframework.cache.properties.CacheProperties;
import org.fireflyframework.cache.spi.CacheProviderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PrimaryOnlyCacheFactoryTest {
    final CacheProperties properties=new CacheProperties();
    CacheManagerFactory factory(CacheProviderFactory... providers) {
        var factory=new CacheManagerFactory(properties,new ObjectMapper(),null,null,null);
        ReflectionTestUtils.setField(factory,"providerFactories",List.of(providers));return factory;
    }
    org.fireflyframework.cache.manager.FireflyCacheManager create(CacheManagerFactory factory,CacheType type) {
        return factory.createCacheManager("policy",type,
                "service:policy",Duration.ofMinutes(1),"primary-only test","test",CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
    }
    static class LegacyProvider implements CacheProviderFactory {
        int calls;
        public CacheType getType(){return CacheType.POSTGRES;}
        public int priority(){return 1;}
        public boolean isAvailable(ProviderContext context){throw new AssertionError("Metadata cannot establish policy capability");}
        public CacheAdapter create(String name,String prefix,Duration ttl,ProviderContext context){calls++;throw new AssertionError("Legacy creation must not be used");}
    }
    static class ExplicitProvider extends LegacyProvider {
        final CacheAdapter adapter=mock(CacheAdapter.class);
        ExplicitProvider(){when(adapter.operationPolicy()).thenReturn(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);when(adapter.getCacheType()).thenReturn(CacheType.POSTGRES);}
        public CacheAdapter create(String name,String prefix,Duration ttl,ProviderContext context,CacheOperationPolicy policy){calls++;return adapter;}
    }
    @Test void smartAndCaffeineCannotWrapAnExplicitPrimaryOnlyAdapter() {
        properties.getSmart().setEnabled(true);properties.getCaffeine().setEnabled(true);var provider=new ExplicitProvider();
        var manager=create(factory(provider),CacheType.POSTGRES);assertThat(ReflectionTestUtils.getField(manager,"primaryCache")).isSameAs(provider.adapter);
        assertThat(ReflectionTestUtils.getField(manager,"fallbackCache")).isNull();assertThat(provider.calls).isEqualTo(1);
        assertThat(manager.operationPolicy()).isEqualTo(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS);
    }
    @Test void automaticAndNoopBackendChoicesRefuse() {
        for(var type:List.of(CacheType.AUTO,CacheType.NOOP))assertThatThrownBy(()->create(factory(new ExplicitProvider()),type)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingOrAmbiguousProviderRefuses() {
        assertThatThrownBy(()->create(factory(),CacheType.POSTGRES)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->create(factory(new ExplicitProvider(),new ExplicitProvider()),CacheType.POSTGRES)).isInstanceOf(IllegalStateException.class);
    }
    @Test void oldProviderDefaultExplicitlyRefusesWithoutLegacyCreation() {
        var provider=new LegacyProvider();assertThatThrownBy(()->create(factory(provider),CacheType.POSTGRES)).isInstanceOf(UnsupportedOperationException.class);assertThat(provider.calls).isZero();
    }
    @Test void nullOrUnconfiguredAdapterCannotBeAccepted() {
        var provider=new ExplicitProvider();when(provider.adapter.operationPolicy()).thenReturn(CacheOperationPolicy.LEGACY);
        assertThatThrownBy(()->create(factory(provider),CacheType.POSTGRES)).isInstanceOf(IllegalArgumentException.class);verify(provider.adapter).close();
        var empty=new ExplicitProvider(){@Override public CacheAdapter create(String n,String p,Duration t,ProviderContext c,CacheOperationPolicy policy){return null;}};
        assertThatThrownBy(()->create(factory(empty),CacheType.POSTGRES)).isInstanceOf(IllegalStateException.class);
    }
    @Test void currentlyUnsupportedBuiltinProviderRefusesExplicitly() {
        assertThatThrownBy(()->create(factory(new org.fireflyframework.cache.spi.providers.CaffeineProvider()),CacheType.CAFFEINE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void providerCannotReturnADifferentBackend() {
        var provider=new ExplicitProvider();when(provider.adapter.getCacheType()).thenReturn(CacheType.CAFFEINE);
        assertThatThrownBy(()->create(factory(provider),CacheType.POSTGRES)).isInstanceOf(IllegalStateException.class);verify(provider.adapter).close();
    }

}
