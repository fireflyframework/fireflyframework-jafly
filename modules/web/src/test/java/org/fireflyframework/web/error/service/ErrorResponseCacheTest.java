package org.fireflyframework.web.error.service;

import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.models.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErrorResponseCache.
 */
@ExtendWith(MockitoExtension.class)
class ErrorResponseCacheTest {

    @Mock
    private FireflyCacheManager cacheManager;

    private ErrorHandlingProperties properties;
    private ErrorResponseCache errorResponseCache;

    @BeforeEach
    void setUp() {
        properties = new ErrorHandlingProperties();
        properties.setEnableErrorCaching(true);
        properties.setErrorCacheTtlSeconds(60);
        properties.setErrorCacheMaxSize(1000);

        errorResponseCache = new ErrorResponseCache(cacheManager, properties);
    }

    @Test
    void get_WhenCacheHit_ReturnsErrorResponse() {
        // Given
        String errorCode = "RESOURCE_NOT_FOUND";
        int status = 404;
        String path = "/api/users/123";
        
        ErrorResponse cachedResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error("Not Found")
                .message("User not found")
                .code(errorCode)
                .path(path)
                .build();

        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.get(eq(expectedKey), eq(ErrorResponse.class)))
                .thenReturn(Mono.just(Optional.of(cachedResponse)));

        // When & Then
        StepVerifier.create(errorResponseCache.get(errorCode, status, path))
                .expectNext(cachedResponse)
                .verifyComplete();

        verify(cacheManager).get(expectedKey, ErrorResponse.class);
    }

    @Test
    void get_WhenCacheMiss_ReturnsEmpty() {
        // Given
        String errorCode = "RESOURCE_NOT_FOUND";
        int status = 404;
        String path = "/api/users/123";
        
        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.get(eq(expectedKey), eq(ErrorResponse.class)))
                .thenReturn(Mono.just(Optional.empty()));

        // When & Then
        StepVerifier.create(errorResponseCache.get(errorCode, status, path))
                .verifyComplete();

        verify(cacheManager).get(expectedKey, ErrorResponse.class);
    }

    @Test
    void get_WhenCacheError_ReturnsEmpty() {
        // Given
        String errorCode = "RESOURCE_NOT_FOUND";
        int status = 404;
        String path = "/api/users/123";
        
        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.get(eq(expectedKey), eq(ErrorResponse.class)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When & Then
        StepVerifier.create(errorResponseCache.get(errorCode, status, path))
                .verifyComplete();

        verify(cacheManager).get(expectedKey, ErrorResponse.class);
    }

    @Test
    void put_WithValidErrorResponse_CachesSuccessfully() {
        // Given
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(404)
                .error("Not Found")
                .message("User not found")
                .code("RESOURCE_NOT_FOUND")
                .path("/api/users/123")
                .build();

        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.put(eq(expectedKey), eq(errorResponse), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(errorResponseCache.put(errorResponse))
                .verifyComplete();

        verify(cacheManager).put(eq(expectedKey), eq(errorResponse), eq(Duration.ofSeconds(60)));
    }

    @Test
    void put_WithNullErrorResponse_DoesNotCache() {
        // When & Then
        StepVerifier.create(errorResponseCache.put(null))
                .verifyComplete();

        verify(cacheManager, never()).put(anyString(), any(), any(Duration.class));
    }

    @Test
    void put_WithNullErrorCode_DoesNotCache() {
        // Given
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(404)
                .error("Not Found")
                .message("User not found")
                .code(null)  // null code
                .path("/api/users/123")
                .build();

        // When & Then
        StepVerifier.create(errorResponseCache.put(errorResponse))
                .verifyComplete();

        verify(cacheManager, never()).put(anyString(), any(), any(Duration.class));
    }

    @Test
    void put_WhenCacheError_HandlesGracefully() {
        // Given
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(404)
                .error("Not Found")
                .message("User not found")
                .code("RESOURCE_NOT_FOUND")
                .path("/api/users/123")
                .build();

        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.put(eq(expectedKey), eq(errorResponse), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When & Then
        StepVerifier.create(errorResponseCache.put(errorResponse))
                .verifyComplete();

        verify(cacheManager).put(eq(expectedKey), eq(errorResponse), any(Duration.class));
    }

    @Test
    void invalidate_WithValidKey_EvictsSuccessfully() {
        // Given
        String errorCode = "RESOURCE_NOT_FOUND";
        int status = 404;
        String path = "/api/users/123";
        
        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.evict(eq(expectedKey)))
                .thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(errorResponseCache.invalidate(errorCode, status, path))
                .verifyComplete();

        verify(cacheManager).evict(expectedKey);
    }

    @Test
    void invalidate_WhenCacheError_HandlesGracefully() {
        // Given
        String errorCode = "RESOURCE_NOT_FOUND";
        int status = 404;
        String path = "/api/users/123";
        
        String expectedKey = ":error:RESOURCE_NOT_FOUND:404:/api/users/123";
        when(cacheManager.evict(eq(expectedKey)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When & Then
        StepVerifier.create(errorResponseCache.invalidate(errorCode, status, path))
                .verifyComplete();

        verify(cacheManager).evict(expectedKey);
    }

    @Test
    void clear_ClearsAllCachedErrors() {
        // Given
        when(cacheManager.clear())
                .thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(errorResponseCache.clear())
                .verifyComplete();

        verify(cacheManager).clear();
    }

    @Test
    void clear_WhenCacheError_HandlesGracefully() {
        // Given
        when(cacheManager.clear())
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When & Then
        StepVerifier.create(errorResponseCache.clear())
                .verifyComplete();

        verify(cacheManager).clear();
    }

    @Test
    void getStats_ReturnsCorrectStatistics() {
        // Given - simulate some cache hits and misses
        when(cacheManager.get(anyString(), eq(ErrorResponse.class)))
                .thenReturn(Mono.just(Optional.of(ErrorResponse.builder().build())))  // hit
                .thenReturn(Mono.just(Optional.empty()))  // miss
                .thenReturn(Mono.just(Optional.of(ErrorResponse.builder().build())))  // hit
                .thenReturn(Mono.just(Optional.empty()));  // miss

        // When - perform operations
        errorResponseCache.get("CODE1", 404, "/path1").block();
        errorResponseCache.get("CODE2", 404, "/path2").block();
        errorResponseCache.get("CODE3", 404, "/path3").block();
        errorResponseCache.get("CODE4", 404, "/path4").block();

        // Then
        ErrorResponseCache.CacheStats stats = errorResponseCache.getStats();
        assertThat(stats.hitCount()).isEqualTo(2);
        assertThat(stats.missCount()).isEqualTo(2);
        assertThat(stats.hitRate()).isEqualTo(0.5);
    }

    @Test
    void getStats_WithNoOperations_ReturnsZeroStats() {
        // When
        ErrorResponseCache.CacheStats stats = errorResponseCache.getStats();

        // Then
        assertThat(stats.hitCount()).isEqualTo(0);
        assertThat(stats.missCount()).isEqualTo(0);
        assertThat(stats.hitRate()).isEqualTo(0.0);
    }

    @Test
    void clear_ResetsStatistics() {
        // Given - simulate some operations
        when(cacheManager.get(anyString(), eq(ErrorResponse.class)))
                .thenReturn(Mono.just(Optional.of(ErrorResponse.builder().build())));
        when(cacheManager.clear())
                .thenReturn(Mono.empty());

        errorResponseCache.get("CODE1", 404, "/path1").block();
        errorResponseCache.get("CODE2", 404, "/path2").block();

        // When
        errorResponseCache.clear().block();

        // Then
        ErrorResponseCache.CacheStats stats = errorResponseCache.getStats();
        assertThat(stats.hitCount()).isEqualTo(0);
        assertThat(stats.missCount()).isEqualTo(0);
    }

    @Test
    void buildKey_CreatesCorrectFormat() {
        // Given
        String errorCode = "VALIDATION_ERROR";
        int status = 400;
        String path = "/api/users";

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode)
                .status(status)
                .path(path)
                .build();

        when(cacheManager.put(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        errorResponseCache.put(errorResponse).block();

        // Then
        verify(cacheManager).put(eq(":error:VALIDATION_ERROR:400:/api/users"), any(), any());
    }

    @Test
    void buildKey_WithNullPath_HandlesGracefully() {
        // Given
        String errorCode = "VALIDATION_ERROR";
        int status = 400;

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode)
                .status(status)
                .path(null)  // null path
                .build();

        when(cacheManager.put(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        errorResponseCache.put(errorResponse).block();

        // Then
        verify(cacheManager).put(eq(":error:VALIDATION_ERROR:400:"), any(), any());
    }
}

