package org.fireflyframework.cache.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import org.fireflyframework.cache.core.*;
import org.fireflyframework.cache.exception.CacheException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PrimaryOnlyCacheManagerTest {
    final CacheAdapter primary=mock(CacheAdapter.class);
    PrimaryOnlyCacheManagerTest() { when(primary.operationPolicy()).thenReturn(CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS); }
    FireflyCacheManager manager() { return new FireflyCacheManager(primary,null,CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS); }
    List<Mono<?>> dataOperations(FireflyCacheManager manager) {
        return List.of(manager.get("key"),manager.get("key",String.class),manager.put("key","value"),
                manager.put("key","value",Duration.ofSeconds(1)),manager.putIfAbsent("key","value"),
                manager.putIfAbsent("key","value",Duration.ofSeconds(1)),manager.evict("key"),manager.clear(),
                manager.exists("key"),manager.keys(),manager.size(),manager.evictByPrefix("prefix"),manager.getStats());
    }
    void answers(Mono<?> answer) {
        doReturn(answer).when(primary).get("key");doReturn(answer).when(primary).get("key",String.class);
        doReturn(answer).when(primary).put("key","value");doReturn(answer).when(primary).put(eq("key"),eq("value"),any(Duration.class));
        doReturn(answer).when(primary).putIfAbsent("key","value");doReturn(answer).when(primary).putIfAbsent(eq("key"),eq("value"),any(Duration.class));
        doReturn(answer).when(primary).evict("key");doReturn(answer).when(primary).clear();doReturn(answer).when(primary).exists("key");
        doReturn(answer).when(primary).keys();doReturn(answer).when(primary).size();doReturn(answer).when(primary).evictByPrefix("prefix");doReturn(answer).when(primary).getStats();
    }
    @Test void everyDataErrorPropagatesWithoutAvailabilityOrFallbackSelection() {
        var failure=new IllegalStateException("controlled backend failure");answers(Mono.error(failure));
        for(var operation:dataOperations(manager()))StepVerifier.create(operation).expectErrorSatisfies(error->assertThat(error).isSameAs(failure)).verify();
        verify(primary,never()).isAvailable();
    }
    @Test void requiredResultsCannotCompleteEmpty() {
        answers(Mono.empty());var manager=manager();
        for(var operation:List.of(manager.get("key"),manager.get("key",String.class),manager.putIfAbsent("key","value"),
                manager.putIfAbsent("key","value",Duration.ofSeconds(1)),manager.evict("key"),manager.exists("key"),
                manager.keys(),manager.size(),manager.evictByPrefix("prefix"),manager.getStats()))
            StepVerifier.create(operation).expectError(CacheException.class).verify();
    }
    @Test void missingPublishersCannotBecomeSuccessfulCompletions() {
        for(var operation:dataOperations(manager()))StepVerifier.create(operation).expectError(CacheException.class).verify();
    }
    @Test void unsupportedResultShapesCannotBePresentedAsSuccess() {
        answers(Mono.just("unsupported-result"));
        for(var operation:dataOperations(manager()))StepVerifier.create(operation).expectError(CacheException.class).verify();
    }
    @Test void synchronousAdapterFailureIsAReactiveFailure() {
        var failure=new IllegalStateException("controlled assembly failure");when(primary.get("key")).thenThrow(failure);
        assertThatCode(()->manager().get("key")).doesNotThrowAnyException();
        StepVerifier.create(manager().get("key")).expectErrorSatisfies(error->assertThat(error).isSameAs(failure)).verify();
    }
    @Test void successfulMissAndNegativeResultRetainTheirMeaning() {
        doReturn(Mono.just(Optional.empty())).when(primary).get("key");doReturn(Mono.just(false)).when(primary).exists("key");
        StepVerifier.create(manager().get("key")).expectNext(Optional.empty()).verifyComplete();
        StepVerifier.create(manager().exists("key")).expectNext(false).verifyComplete();
    }
    @Test void successfulVoidCompletionRemainsSupported() {
        doReturn(Mono.empty()).when(primary).put("key","value");doReturn(Mono.empty()).when(primary).clear();
        StepVerifier.create(manager().put("key","value")).verifyComplete();StepVerifier.create(manager().clear()).verifyComplete();
    }
    @Test void unavailableMetadataCannotReselectThePinnedPrimary() {
        when(primary.isAvailable()).thenReturn(false);doReturn(Mono.just(Optional.of("value"))).when(primary).get("key");
        StepVerifier.create(manager().get("key")).expectNext(Optional.of("value")).verifyComplete();verify(primary,never()).isAvailable();
    }
    @Test void negativeSizeOrEvictionCountRefuses() {
        when(primary.size()).thenReturn(Mono.just(-1L));when(primary.evictByPrefix("prefix")).thenReturn(Mono.just(-1L));
        StepVerifier.create(manager().size()).expectError(CacheException.class).verify();
        StepVerifier.create(manager().evictByPrefix("prefix")).expectError(CacheException.class).verify();
    }
    @Test void unknownAdapterAndAnyFallbackRefuseStrictConstruction() {
        var legacy=mock(CacheAdapter.class);when(legacy.operationPolicy()).thenReturn(CacheOperationPolicy.LEGACY);
        assertThatThrownBy(()->new FireflyCacheManager(legacy,null,CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FireflyCacheManager(primary,legacy,CacheOperationPolicy.PRIMARY_ONLY_PROPAGATE_ERRORS)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void closeFailurePropagatesAndClosedInstanceRefusesDataOperations() {
        var failure=new IllegalStateException("controlled close failure");doThrow(failure).when(primary).close();var manager=manager();
        assertThatThrownBy(manager::close).isSameAs(failure);StepVerifier.create(manager.get("key")).expectError(IllegalStateException.class).verify();
    }
    @Test void missingHealthResultCannotPretendHealthy() {
        StepVerifier.create(manager().getHealth()).expectError(CacheException.class).verify();
        when(primary.getHealth()).thenReturn(Mono.empty());StepVerifier.create(manager().getHealth()).expectError(CacheException.class).verify();
    }
    @Test void legacyManagerKeepsExistingFailureRecovery() {
        answers(Mono.error(new IllegalStateException("controlled legacy failure")));var legacy=new FireflyCacheManager(primary);
        when(primary.isAvailable()).thenReturn(true);StepVerifier.create(legacy.get("key")).expectNext(Optional.empty()).verifyComplete();
        StepVerifier.create(legacy.put("key","value")).verifyComplete();assertThat(legacy.operationPolicy()).isEqualTo(CacheOperationPolicy.LEGACY);
    }
}
