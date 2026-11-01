package org.fireflyframework.cache.manager;

import static org.mockito.Mockito.*;
import java.util.Optional;
import org.fireflyframework.cache.core.CacheAdapter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Existing API controls can be compiled against674 and the candidate without new-policy symbols. */
class LegacyCacheOperationCompatibilityTest {
    final CacheAdapter adapter=mock(CacheAdapter.class);
    final FireflyCacheManager manager=new FireflyCacheManager(adapter);
    LegacyCacheOperationCompatibilityTest(){when(adapter.isAvailable()).thenReturn(true);}
    @Test void oldReadFailureRecoveryRemainsAReportedMiss(){doReturn(Mono.error(new IllegalStateException("controlled failure"))).when(adapter).get("key");StepVerifier.create(manager.get("key")).expectNext(Optional.empty()).verifyComplete();}
    @Test void oldWriteFailureRecoveryRemainsCompleted(){doReturn(Mono.error(new IllegalStateException("controlled failure"))).when(adapter).put("key","value");StepVerifier.create(manager.put("key","value")).verifyComplete();}
    @Test void oldEvictionFailureRecoveryRemainsFalse(){when(adapter.evict("key")).thenReturn(Mono.error(new IllegalStateException("controlled failure")));StepVerifier.create(manager.evict("key")).expectNext(false).verifyComplete();}
}
