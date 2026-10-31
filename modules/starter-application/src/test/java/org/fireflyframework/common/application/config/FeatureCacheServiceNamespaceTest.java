package org.fireflyframework.common.application.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.r2dbc.spi.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fireflyframework.cache.config.CacheAutoConfiguration;
import org.fireflyframework.cache.config.PostgresCacheAutoConfiguration;
import org.fireflyframework.cache.core.CacheType;
import org.fireflyframework.cache.factory.CacheManagerFactory;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.cache.properties.CacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FeatureCacheServiceNamespaceTest {
    static class TechnicalStorage {
        @Bean AtomicReference<String> namespaceBinding() {return new AtomicReference<>();}
        @Bean AtomicInteger factoryCreations() {return new AtomicInteger();}
        @Bean(name="fireflyCachePostgresConnectionFactory",defaultCandidate=false) @Lazy
        @SuppressWarnings({"rawtypes","unchecked"})
        ConnectionFactory factory(AtomicReference<String> namespaceBinding,AtomicInteger factoryCreations) {
            factoryCreations.incrementAndGet();var f=mock(ConnectionFactory.class);var c=mock(Connection.class);
            var s=mock(Statement.class,RETURNS_SELF);var r=mock(Result.class);
            when(s.bind(eq("$1"),any())).thenAnswer(call -> {namespaceBinding.set(call.getArgument(1));return s;});
            when(r.getRowsUpdated()).thenReturn(Mono.just(0L));
            doReturn(Mono.just(c)).when(f).create();when(c.close()).thenReturn(Mono.empty());
            when(c.createStatement(anyString())).thenReturn(s);doReturn(Flux.just(r)).when(s).execute();
            when(r.map(any(BiFunction.class))).thenReturn(Flux.empty());return f;
        }
    }
    final ApplicationContextRunner context=new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class,PostgresCacheAutoConfiguration.class))
            .withUserConfiguration(TechnicalStorage.class);

    FireflyCacheManager feature(CacheManagerFactory factory) {return new ConfigCacheAutoConfiguration().configCacheManager(factory);}
    String namespace(String service,boolean useFeature) {
        var captured=new AtomicReference<String>();
        context.withPropertyValues("spring.application.name="+service).run(application -> {
            assertThat(application).hasNotFailed();
            var manager=useFeature?feature(application.getBean(CacheManagerFactory.class))
                    :application.getBean("fireflyCacheManager",FireflyCacheManager.class);
            manager.clear().block(java.time.Duration.ofSeconds(2));
            captured.set(application.getBean(AtomicReference.class).get().toString());
        });return captured.get();
    }
    @Test void actualFeatureFactoryKeepsServiceNamespacesSeparate() {
        assertThat(namespace("service-a",true)).isNotEqualTo(namespace("service-b",true));
    }
    @Test void explicitCaffeineNeverCreatesPostgresOrValidatesItsMalformedNamespace() {
        context.withPropertyValues("spring.application.name=service-a","firefly.cache.default-cache-type=CAFFEINE",
                "firefly.cache.postgres.key-prefix="+String.valueOf((char)0xD800),
                "firefly.cache.postgres.database=configured","firefly.cache.postgres.username=configured")
                .run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(feature(application.getBean(CacheManagerFactory.class)).getCacheType()).isEqualTo(CacheType.CAFFEINE);
                    assertThat(application.getBean(AtomicInteger.class).get()).isZero();
                });
    }
    @Test void defaultPostgresWithoutItsResourceCannotSilentlyBecomeALocalFeatureCache() {
        var properties=new CacheProperties();properties.getPostgres().setKeyPrefix("service-a");
        var factory=new CacheManagerFactory(properties,new ObjectMapper(),null,null,null,null);
        assertThatThrownBy(() -> feature(factory)).isInstanceOf(IllegalStateException.class).hasMessageContaining("POSTGRES");
    }
    @Test void explicitGlobalAutoRetainsAvailablePostgresSelection() {
        context.withPropertyValues("spring.application.name=service-a","firefly.cache.default-cache-type=AUTO")
                .run(application -> {assertThat(application).hasNotFailed();
                    assertThat(feature(application.getBean(CacheManagerFactory.class)).getCacheType()).isEqualTo(CacheType.POSTGRES);});
    }
    @Test void defaultManagerControlKeepsServiceNamespacesSeparate() {
        assertThat(namespace("service-a",false)).isNotEqualTo(namespace("service-b",false));
    }

    @Test void explicitlyRegisteredTechnicalFixtureDoesNotLeakIntoRealPackageScanning() {
        var scanner=new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
        assertThat(scanner.findCandidateComponents(getClass().getPackageName()))
                .extracting(org.springframework.beans.factory.config.BeanDefinition::getBeanClassName)
                .doesNotContain(TechnicalStorage.class.getName());
    }
}
