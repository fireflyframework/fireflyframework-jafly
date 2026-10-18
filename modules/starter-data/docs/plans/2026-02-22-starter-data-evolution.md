# Starter-Data Evolution: Bug Fixes, New Features & GenAI Bridge

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix 11 identified bugs, add 12 best-in-class features, and create a GenAI bridge package for native integration between `fireflyframework-starter-data` (Java) and `fireflyframework-genai` (Python).

**Architecture:** The starter follows hexagonal architecture with Template Method pattern. All changes must preserve the existing port/adapter structure, maintain backward compatibility via `@ConditionalOnMissingBean`, and follow the established reactive (`Mono`/`Flux`) patterns. New features add new packages without modifying existing public APIs.

**Tech Stack:** Spring Boot 3 WebFlux, Reactor, Resilience4j, Micrometer, Jackson, Lombok, JUnit 5 + Mockito + StepVerifier. GenAI bridge: Python 3.13+, pydantic-ai, httpx, uv.

---

## Phase 1: Bug Fixes

### Task 1: Fix DataSizeCalculator Locale Bug

**Files:**
- Modify: `src/main/java/org/fireflyframework/data/util/DataSizeCalculator.java:144`
- Test: `src/test/java/org/fireflyframework/data/util/DataSizeCalculatorTest.java`

**Step 1: Write the failing test**

```java
@Test
void formatSize_shouldUseEnglishLocale_forDecimalSeparator() {
    // 1536 bytes = 1.5 KB
    String formatted = DataSizeCalculator.formatSize(1536);
    assertThat(formatted).isEqualTo("1.5 KB");
    assertThat(formatted).doesNotContain(",");
}
```

**Step 2: Run test to verify it fails**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=DataSizeCalculatorTest#formatSize_shouldUseEnglishLocale_forDecimalSeparator -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — currently produces "1,5 KB" due to Spanish locale

**Step 3: Fix the locale**

In `DataSizeCalculator.java` line 144, change:
```java
// FROM:
return String.format(Locale.of("es", "ES"), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
// TO:
return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
```

**Step 4: Run test to verify it passes**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=DataSizeCalculatorTest`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/org/fireflyframework/data/util/DataSizeCalculator.java src/test/java/org/fireflyframework/data/util/DataSizeCalculatorTest.java
git commit -m "fix: use Locale.US in DataSizeCalculator.formatSize() to prevent comma decimal separator"
```

---

### Task 2: Fix Cache Eviction — Add evictByPrefix to CacheAdapter

This is the **highest severity** bug. All `evictTenant()`, `evictProvider()`, `evictByOperation()` methods call `cacheAdapter.clear()` which wipes all tenants' caches.

**Files:**
- Modify: `fireflyframework-cache/src/main/java/org/fireflyframework/cache/core/CacheAdapter.java`
- Modify: all CacheAdapter implementations (Caffeine, Redis, etc.)
- Test: `fireflyframework-cache/src/test/java/org/fireflyframework/cache/core/` (adapter tests)

**Step 1: Add `evictByPrefix` to `CacheAdapter` interface**

After line 122 (`Mono<Void> clear();`), add:

```java
/**
 * Evicts all cache entries whose keys start with the given prefix.
 *
 * @param keyPrefix the key prefix to match
 * @return a Mono containing the number of evicted entries
 */
default Mono<Long> evictByPrefix(String keyPrefix) {
    // Default implementation: scan keys and evict matching ones
    return this.<String>keys()
        .flatMap(allKeys -> {
            var matching = allKeys.stream()
                .filter(k -> k.toString().startsWith(keyPrefix))
                .toList();
            if (matching.isEmpty()) {
                return Mono.just(0L);
            }
            return Flux.fromIterable(matching)
                .flatMap(this::evict)
                .filter(Boolean::booleanValue)
                .count();
        });
}
```

**Step 2: Write tests for the default implementation**

```java
@Test
void evictByPrefix_shouldOnlyEvictMatchingKeys() {
    // Setup: put keys with different prefixes
    adapter.put("enrichment:tenant-1:providerA:type1:hash1", "value1").block();
    adapter.put("enrichment:tenant-1:providerA:type2:hash2", "value2").block();
    adapter.put("enrichment:tenant-2:providerB:type1:hash3", "value3").block();
    adapter.put("operation:tenant-1:providerA:op1:hash4", "value4").block();

    // Act: evict only tenant-1 enrichment entries
    Long evicted = adapter.evictByPrefix("enrichment:tenant-1:").block();

    // Assert
    assertThat(evicted).isEqualTo(2);
    assertThat(adapter.exists("enrichment:tenant-2:providerB:type1:hash3").block()).isTrue();
    assertThat(adapter.exists("operation:tenant-1:providerA:op1:hash4").block()).isTrue();
}
```

**Step 3: Run tests**

Run: `cd fireflyframework-cache && mvn test`
Expected: PASS

**Step 4: Commit**

```bash
git add fireflyframework-cache/
git commit -m "feat(cache): add evictByPrefix default method to CacheAdapter for pattern-based eviction"
```

---

### Task 3: Fix EnrichmentCacheService to use evictByPrefix

**Files:**
- Modify: `src/main/java/org/fireflyframework/data/cache/EnrichmentCacheService.java:206-249`
- Modify: `src/main/java/org/fireflyframework/data/cache/OperationCacheService.java:188-265`
- Test: `src/test/java/org/fireflyframework/data/cache/EnrichmentCacheServiceTest.java`
- Test: `src/test/java/org/fireflyframework/data/cache/OperationCacheServiceTest.java`

**Step 1: Write failing tests for targeted eviction**

```java
@Test
void evictTenant_shouldNotClearEntireCache() {
    // This test verifies evictTenant uses prefix-based eviction
    when(cacheAdapter.evictByPrefix("enrichment:tenant-1:"))
        .thenReturn(Mono.just(2L));

    StepVerifier.create(cacheService.evictTenant("tenant-1"))
        .verifyComplete();

    // Verify evictByPrefix was called instead of clear()
    verify(cacheAdapter).evictByPrefix("enrichment:tenant-1:");
    verify(cacheAdapter, never()).clear();
}
```

**Step 2: Run test to verify it fails**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=EnrichmentCacheServiceTest#evictTenant_shouldNotClearEntireCache`
Expected: FAIL — currently calls `clear()` instead

**Step 3: Fix EnrichmentCacheService.evictTenant**

Replace lines 206-223:
```java
public Mono<Void> evictTenant(String tenantId) {
    if (!cacheEnabled) {
        return Mono.empty();
    }

    String prefix = keyGenerator.generateTenantPattern(tenantId).replace("*", "");
    log.info("Evicting cache entries for tenant: {} (prefix: {})", tenantId, prefix);

    return cacheAdapter.evictByPrefix(prefix)
        .doOnNext(count -> log.info("Evicted {} cache entries for tenant: {}", count, tenantId))
        .then()
        .onErrorResume(e -> {
            log.warn("Error evicting cache for tenant {}: {}", tenantId, e.getMessage());
            return Mono.empty();
        });
}
```

Apply identical pattern to `evictProvider()` (lines 232-249).

**Step 4: Fix OperationCacheService.evictByTenant/evictByProvider/evictByOperation**

Apply the same `evictByPrefix` pattern to all three methods in `OperationCacheService.java` (lines 188-265). Each method already computes the correct pattern string — just strip the trailing `*` and call `evictByPrefix` instead of `clear()`.

**Step 5: Run all cache tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest="EnrichmentCacheServiceTest,OperationCacheServiceTest"`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/org/fireflyframework/data/cache/ src/test/java/org/fireflyframework/data/cache/
git commit -m "fix(cache): use prefix-based eviction instead of full cache clear for tenant/provider eviction"
```

---

### Task 4: Add Job Timeout Enforcement

**Files:**
- Modify: `src/main/java/org/fireflyframework/data/service/AbstractResilientDataJobService.java:144-266`
- Modify: `src/main/java/org/fireflyframework/data/config/JobOrchestrationProperties.java` (add per-stage timeout)
- Test: `src/test/java/org/fireflyframework/data/service/AbstractResilientDataJobServiceTimeoutTest.java` (new)

**Step 1: Write the failing test**

```java
@Test
void executeWithObservability_shouldTimeoutAfterConfiguredDuration() {
    // Configure a 100ms timeout
    when(properties.getDefaultTimeout()).thenReturn(Duration.ofMillis(100));

    // doStartJob takes 500ms — should timeout
    TestService service = new TestService(tracingService, metricsService, resiliencyService, eventPublisher) {
        @Override
        protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
            return Mono.delay(Duration.ofMillis(500))
                .map(v -> JobStageResponse.success(JobStage.START, "exec-1", "done"));
        }
    };

    StepVerifier.create(service.startJob(request))
        .assertNext(response -> {
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getMessage()).contains("timed out");
        })
        .verifyComplete();
}
```

**Step 2: Add timeout to the pipeline**

In `AbstractResilientDataJobService.executeWithObservabilityAndResiliency()`, after applying resiliency (line 173), add:

```java
// Apply timeout if configured
Duration timeout = getJobTimeout(stage);
if (timeout != null && !timeout.isZero()) {
    resilientOperation = resilientOperation
        .timeout(timeout)
        .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
            log.warn("Job stage {} timed out after {} - executionId: {}", stage, timeout, executionId);
            if (metricsService != null) {
                metricsService.recordJobError(stage, "TimeoutException");
            }
            return Mono.just(JobStageResponse.failure(stage, executionId,
                "Job stage " + stage + " timed out after " + timeout));
        });
}
```

Add the timeout resolution method:
```java
protected Duration getJobTimeout(JobStage stage) {
    // Subclasses can override for per-stage timeouts
    return null; // No framework-level timeout by default to maintain backward compat
}
```

**Step 3: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=AbstractResilientDataJobServiceTimeoutTest`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/org/fireflyframework/data/service/AbstractResilientDataJobService.java src/test/
git commit -m "feat(jobs): add configurable timeout enforcement to job stage execution pipeline"
```

---

### Task 5: Fix DataEnricherRegistry Priority Tie-Breaking

**Files:**
- Modify: `src/main/java/org/fireflyframework/data/service/DataEnricherRegistry.java:159-161`
- Test: `src/test/java/org/fireflyframework/data/service/DataEnricherRegistryTest.java`

**Step 1: Write the failing test**

```java
@Test
void getEnricherForType_shouldUseTieBreakerWhenPrioritiesAreEqual() {
    // Two enrichers with same priority — should deterministically pick by provider name
    DataEnricher<?, ?, ?> enricherA = createMockEnricher("ProviderA", "company-data", 50);
    DataEnricher<?, ?, ?> enricherB = createMockEnricher("ProviderB", "company-data", 50);

    DataEnricherRegistry registry = new DataEnricherRegistry(List.of(enricherA, enricherB));

    Optional<DataEnricher<?, ?, ?>> result = registry.getEnricherForType("company-data");
    assertThat(result).isPresent();
    // With equal priority, should be deterministic (alphabetically first provider name)
    assertThat(result.get().getProviderName()).isEqualTo("ProviderA");
}
```

**Step 2: Fix the comparator**

In `DataEnricherRegistry.java` line 159-160, change:
```java
// FROM:
return enrichers.stream()
    .max(Comparator.comparingInt(DataEnricher::getPriority));
// TO:
return enrichers.stream()
    .max(Comparator.comparingInt(DataEnricher<?, ?, ?>::getPriority)
        .thenComparing(e -> e.getProviderName() != null ? e.getProviderName() : "", Comparator.naturalOrder()));
```

Apply same fix to `getEnricherForTypeAndTenant()` (line 288-289).

**Step 3: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=DataEnricherRegistryTest`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/org/fireflyframework/data/service/DataEnricherRegistry.java src/test/
git commit -m "fix(registry): add deterministic tie-breaking by provider name when enricher priorities are equal"
```

---

### Task 6: Add ExceptionHandler for EnrichmentValidationException

**Files:**
- Create: `src/main/java/org/fireflyframework/data/controller/advice/DataExceptionHandler.java`
- Test: `src/test/java/org/fireflyframework/data/controller/advice/DataExceptionHandlerTest.java`

**Step 1: Write the test**

```java
@Test
void handleValidationException_shouldReturn400() {
    EnrichmentValidationException ex = new EnrichmentValidationException(
        "Validation failed", List.of("param 'companyId' is required", "strategy must be ENHANCE or MERGE"));

    DataExceptionHandler handler = new DataExceptionHandler();
    ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsKey("errors");
    assertThat(response.getBody().get("errors")).isInstanceOf(List.class);
}
```

**Step 2: Implement the handler**

```java
package org.fireflyframework.data.controller.advice;

import org.fireflyframework.data.enrichment.EnrichmentRequestValidator.EnrichmentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice(basePackages = "org.fireflyframework.data.controller")
public class DataExceptionHandler {

    @ExceptionHandler(EnrichmentValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(EnrichmentValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "status", 400,
            "error", "Validation Failed",
            "message", ex.getMessage(),
            "errors", ex.getErrors(),
            "timestamp", Instant.now().toString()
        ));
    }
}
```

**Step 3: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=DataExceptionHandlerTest`
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/java/org/fireflyframework/data/controller/advice/ src/test/java/org/fireflyframework/data/controller/advice/
git commit -m "fix(api): add @RestControllerAdvice to return 400 for EnrichmentValidationException instead of 500"
```

---

## Phase 2: Per-Provider Resilience

### Task 7: Create ProviderResiliencyRegistry

**Files:**
- Create: `src/main/java/org/fireflyframework/data/resiliency/ProviderResiliencyConfig.java`
- Create: `src/main/java/org/fireflyframework/data/resiliency/ProviderResiliencyRegistry.java`
- Modify: `src/main/java/org/fireflyframework/data/config/DataEnrichmentProperties.java` (add provider configs)
- Test: `src/test/java/org/fireflyframework/data/resiliency/ProviderResiliencyRegistryTest.java`

**Step 1: Define the per-provider config model**

```java
package org.fireflyframework.data.resiliency;

import lombok.Data;

@Data
public class ProviderResiliencyConfig {
    private boolean circuitBreakerEnabled = true;
    private float circuitBreakerFailureRateThreshold = 50.0f;
    private int circuitBreakerSlidingWindowSize = 10;
    private long circuitBreakerWaitDurationInOpenStateMs = 30000;
    private boolean retryEnabled = true;
    private int retryMaxAttempts = 3;
    private long retryWaitDurationMs = 1000;
    private boolean rateLimiterEnabled = false;
    private int rateLimitForPeriod = 50;
    private long rateLimitRefreshPeriodMs = 1000;
    private boolean bulkheadEnabled = false;
    private int bulkheadMaxConcurrentCalls = 25;
    private long timeoutMs = 30000;
}
```

**Step 2: Create ProviderResiliencyRegistry**

```java
package org.fireflyframework.data.resiliency;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ProviderResiliencyRegistry {

    private final Map<String, ProviderResiliencyConfig> providerConfigs;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    private final ResiliencyDecoratorService defaultService;

    public ProviderResiliencyRegistry(
            Map<String, ProviderResiliencyConfig> providerConfigs,
            ResiliencyDecoratorService defaultService) {
        this.providerConfigs = providerConfigs != null ? providerConfigs : Map.of();
        this.defaultService = defaultService;
        initializeProviders();
    }

    public <T> Mono<T> decorate(String providerName, Mono<T> operation) {
        if (!providerConfigs.containsKey(providerName)) {
            return defaultService.decorate(operation);
        }

        Mono<T> decorated = operation;
        ProviderResiliencyConfig config = providerConfigs.get(providerName);

        if (config.isBulkheadEnabled()) {
            decorated = decorated.transformDeferred(
                BulkheadOperator.of(bulkheads.get(providerName)));
        }
        if (config.isRateLimiterEnabled()) {
            decorated = decorated.transformDeferred(
                RateLimiterOperator.of(rateLimiters.get(providerName)));
        }
        if (config.isCircuitBreakerEnabled()) {
            decorated = decorated.transformDeferred(
                CircuitBreakerOperator.of(circuitBreakers.get(providerName)));
        }
        if (config.isRetryEnabled()) {
            decorated = decorated.transformDeferred(
                RetryOperator.of(retries.get(providerName)));
        }
        if (config.getTimeoutMs() > 0) {
            decorated = decorated.timeout(Duration.ofMillis(config.getTimeoutMs()));
        }

        return decorated;
    }

    private void initializeProviders() {
        providerConfigs.forEach((name, config) -> {
            log.info("Initializing resiliency for provider: {}", name);
            // Create provider-specific Resilience4j instances
            if (config.isCircuitBreakerEnabled()) {
                circuitBreakers.put(name, CircuitBreaker.of("enricher-" + name,
                    CircuitBreakerConfig.custom()
                        .failureRateThreshold(config.getCircuitBreakerFailureRateThreshold())
                        .slidingWindowSize(config.getCircuitBreakerSlidingWindowSize())
                        .waitDurationInOpenState(Duration.ofMillis(config.getCircuitBreakerWaitDurationInOpenStateMs()))
                        .build()));
            }
            if (config.isRetryEnabled()) {
                retries.put(name, Retry.of("enricher-" + name,
                    RetryConfig.custom()
                        .maxAttempts(config.getRetryMaxAttempts())
                        .waitDuration(Duration.ofMillis(config.getRetryWaitDurationMs()))
                        .build()));
            }
            if (config.isRateLimiterEnabled()) {
                rateLimiters.put(name, RateLimiter.of("enricher-" + name,
                    RateLimiterConfig.custom()
                        .limitForPeriod(config.getRateLimitForPeriod())
                        .limitRefreshPeriod(Duration.ofMillis(config.getRateLimitRefreshPeriodMs()))
                        .build()));
            }
            if (config.isBulkheadEnabled()) {
                bulkheads.put(name, Bulkhead.of("enricher-" + name,
                    BulkheadConfig.custom()
                        .maxConcurrentCalls(config.getBulkheadMaxConcurrentCalls())
                        .build()));
            }
        });
    }
}
```

**Step 3: Add provider config to DataEnrichmentProperties**

Add to `DataEnrichmentProperties`:
```java
private Map<String, ProviderResiliencyConfig> providers = new HashMap<>();
```

Config YAML example:
```yaml
firefly.data.enrichment.providers:
  experian:
    rate-limiter-enabled: true
    rate-limit-for-period: 5
    timeout-ms: 10000
  google-maps:
    rate-limiter-enabled: true
    rate-limit-for-period: 100
    timeout-ms: 3000
```

**Step 4: Wire in DataEnrichmentAutoConfiguration**

Add bean:
```java
@Bean
@ConditionalOnMissingBean
public ProviderResiliencyRegistry providerResiliencyRegistry(
        DataEnrichmentProperties properties,
        ResiliencyDecoratorService defaultService) {
    return new ProviderResiliencyRegistry(properties.getProviders(), defaultService);
}
```

**Step 5: Write tests**

```java
@Test
void decorate_shouldUseProviderSpecificConfig_whenConfigured() {
    Map<String, ProviderResiliencyConfig> configs = Map.of(
        "slow-provider", new ProviderResiliencyConfig() {{ setTimeoutMs(100); setRateLimiterEnabled(true); setRateLimitForPeriod(5); }}
    );
    ProviderResiliencyRegistry registry = new ProviderResiliencyRegistry(configs, defaultService);

    // This should timeout at 100ms, not use global config
    Mono<String> slow = Mono.delay(Duration.ofMillis(500)).map(v -> "done");

    StepVerifier.create(registry.decorate("slow-provider", slow))
        .expectError(java.util.concurrent.TimeoutException.class)
        .verify();
}

@Test
void decorate_shouldFallbackToDefault_whenProviderNotConfigured() {
    ProviderResiliencyRegistry registry = new ProviderResiliencyRegistry(Map.of(), defaultService);
    Mono<String> op = Mono.just("result");

    registry.decorate("unknown-provider", op).block();

    verify(defaultService).decorate(op);
}
```

**Step 6: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=ProviderResiliencyRegistryTest`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/org/fireflyframework/data/resiliency/ src/main/java/org/fireflyframework/data/config/ src/test/
git commit -m "feat(resiliency): add per-provider resilience configuration via ProviderResiliencyRegistry"
```

---

## Phase 3: Data Quality Framework

### Task 8: Create Data Quality Core

**Files:**
- Create: `src/main/java/org/fireflyframework/data/quality/DataQualityRule.java`
- Create: `src/main/java/org/fireflyframework/data/quality/QualityResult.java`
- Create: `src/main/java/org/fireflyframework/data/quality/QualityReport.java`
- Create: `src/main/java/org/fireflyframework/data/quality/QualitySeverity.java`
- Create: `src/main/java/org/fireflyframework/data/quality/QualityStrategy.java`
- Create: `src/main/java/org/fireflyframework/data/quality/DataQualityEngine.java`
- Create: `src/main/java/org/fireflyframework/data/quality/rules/NotNullRule.java`
- Create: `src/main/java/org/fireflyframework/data/quality/rules/PatternRule.java`
- Create: `src/main/java/org/fireflyframework/data/quality/rules/RangeRule.java`
- Create: `src/main/java/org/fireflyframework/data/event/DataQualityEvent.java`
- Test: `src/test/java/org/fireflyframework/data/quality/DataQualityEngineTest.java`

**Step 1: Define the quality contracts**

```java
// QualitySeverity.java
public enum QualitySeverity { INFO, WARNING, CRITICAL }

// QualityStrategy.java
public enum QualityStrategy { FAIL_FAST, COLLECT_ALL }

// QualityResult.java
@Data @Builder
public class QualityResult {
    private final String ruleName;
    private final boolean passed;
    private final QualitySeverity severity;
    private final String message;
    private final String fieldName;
    private final Object actualValue;

    public static QualityResult pass(String ruleName) { ... }
    public static QualityResult fail(String ruleName, QualitySeverity severity, String message) { ... }
}

// DataQualityRule.java — the port
public interface DataQualityRule<T> {
    QualityResult evaluate(T data);
    String getRuleName();
    QualitySeverity getSeverity();
}

// QualityReport.java
@Data @Builder
public class QualityReport {
    private final boolean passed;
    private final int totalRules;
    private final int passedRules;
    private final int failedRules;
    private final List<QualityResult> results;
    private final Instant timestamp;

    public List<QualityResult> getFailures() { ... }
    public List<QualityResult> getBySeverity(QualitySeverity severity) { ... }
}
```

**Step 2: Implement DataQualityEngine**

```java
@Slf4j
public class DataQualityEngine {

    private final List<DataQualityRule<?>> rules;
    private final ApplicationEventPublisher eventPublisher;

    public DataQualityEngine(List<DataQualityRule<?>> rules,
                              ApplicationEventPublisher eventPublisher) {
        this.rules = rules;
        this.eventPublisher = eventPublisher;
    }

    @SuppressWarnings("unchecked")
    public <T> Mono<QualityReport> evaluate(T data, QualityStrategy strategy) {
        return Mono.fromCallable(() -> {
            List<QualityResult> results = new ArrayList<>();

            for (DataQualityRule<?> rule : rules) {
                DataQualityRule<T> typedRule = (DataQualityRule<T>) rule;
                QualityResult result = typedRule.evaluate(data);
                results.add(result);

                if (strategy == QualityStrategy.FAIL_FAST
                    && !result.isPassed()
                    && result.getSeverity() == QualitySeverity.CRITICAL) {
                    break;
                }
            }

            QualityReport report = QualityReport.builder()
                .passed(results.stream().noneMatch(r -> !r.isPassed() && r.getSeverity() == QualitySeverity.CRITICAL))
                .totalRules(results.size())
                .passedRules((int) results.stream().filter(QualityResult::isPassed).count())
                .failedRules((int) results.stream().filter(r -> !r.isPassed()).count())
                .results(results)
                .timestamp(Instant.now())
                .build();

            if (eventPublisher != null) {
                eventPublisher.publishEvent(new DataQualityEvent(report));
            }

            return report;
        });
    }

    public <T> Mono<QualityReport> evaluate(T data) {
        return evaluate(data, QualityStrategy.COLLECT_ALL);
    }
}
```

**Step 3: Implement built-in rules**

```java
// NotNullRule.java
public class NotNullRule<T> implements DataQualityRule<T> {
    private final String fieldName;
    private final Function<T, Object> fieldExtractor;
    // evaluate: return pass/fail based on null check
}

// PatternRule.java
public class PatternRule<T> implements DataQualityRule<T> {
    private final String fieldName;
    private final Pattern pattern;
    private final Function<T, String> fieldExtractor;
    // evaluate: return pass/fail based on regex match
}

// RangeRule.java
public class RangeRule<T> implements DataQualityRule<T> {
    private final String fieldName;
    private final Comparable<?> min;
    private final Comparable<?> max;
    private final Function<T, Comparable<?>> fieldExtractor;
    // evaluate: return pass/fail based on range check
}
```

**Step 4: Write comprehensive tests**

```java
@Test
void evaluate_collectAll_shouldRunAllRulesEvenOnFailure() {
    DataQualityRule<TestDTO> rule1 = new NotNullRule<>("name", TestDTO::getName);
    DataQualityRule<TestDTO> rule2 = new PatternRule<>("email", Pattern.compile("^[^@]+@[^@]+$"), TestDTO::getEmail);

    DataQualityEngine engine = new DataQualityEngine(List.of(rule1, rule2), null);
    TestDTO invalidDto = new TestDTO(null, "not-an-email");

    StepVerifier.create(engine.evaluate(invalidDto, QualityStrategy.COLLECT_ALL))
        .assertNext(report -> {
            assertThat(report.isPassed()).isFalse();
            assertThat(report.getFailedRules()).isEqualTo(2);
            assertThat(report.getTotalRules()).isEqualTo(2);
        })
        .verifyComplete();
}

@Test
void evaluate_failFast_shouldStopOnFirstCriticalFailure() {
    // rule1 CRITICAL fails → rule2 should NOT execute
    ...
}
```

**Step 5: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=DataQualityEngineTest`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/org/fireflyframework/data/quality/ src/main/java/org/fireflyframework/data/event/DataQualityEvent.java src/test/
git commit -m "feat(quality): add DataQualityEngine with rule-based validation, built-in rules, and event publishing"
```

---

## Phase 4: Data Lineage Tracking

### Task 9: Create Data Lineage Core

**Files:**
- Create: `src/main/java/org/fireflyframework/data/lineage/LineageRecord.java`
- Create: `src/main/java/org/fireflyframework/data/lineage/LineageTracker.java`
- Create: `src/main/java/org/fireflyframework/data/lineage/port/LineageRepository.java`
- Create: `src/main/java/org/fireflyframework/data/lineage/InMemoryLineageTracker.java`
- Create: `src/main/java/org/fireflyframework/data/event/LineageEvent.java`
- Modify: `src/main/java/org/fireflyframework/data/service/DataEnricher.java` (add lineage recording)
- Test: `src/test/java/org/fireflyframework/data/lineage/LineageTrackerTest.java`

**Step 1: Define the lineage model**

```java
// LineageRecord.java
@Data @Builder
public class LineageRecord {
    private final String recordId;
    private final String entityId;
    private final String sourceSystem;
    private final String operation;       // ENRICHMENT, TRANSFORMATION, JOB_COLLECTION
    private final String operatorId;      // enricher name or job name
    private final Instant timestamp;
    private final String inputHash;
    private final String outputHash;
    private final String traceId;
    private final Map<String, Object> metadata;
}
```

**Step 2: Define the LineageTracker interface (port)**

```java
public interface LineageTracker {
    Mono<Void> record(LineageRecord record);
    Flux<LineageRecord> getLineage(String entityId);
    Flux<LineageRecord> getLineageByOperator(String operatorId);
}
```

**Step 3: Implement InMemoryLineageTracker**

```java
@Slf4j
public class InMemoryLineageTracker implements LineageTracker {
    private final Map<String, List<LineageRecord>> lineageByEntity = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> record(LineageRecord record) {
        return Mono.fromRunnable(() -> {
            lineageByEntity.computeIfAbsent(record.getEntityId(), k -> new CopyOnWriteArrayList<>())
                .add(record);
            log.debug("Recorded lineage: entity={}, operator={}, operation={}",
                record.getEntityId(), record.getOperatorId(), record.getOperation());
        });
    }

    @Override
    public Flux<LineageRecord> getLineage(String entityId) {
        return Flux.fromIterable(lineageByEntity.getOrDefault(entityId, List.of()));
    }

    @Override
    public Flux<LineageRecord> getLineageByOperator(String operatorId) {
        return Flux.fromStream(lineageByEntity.values().stream()
            .flatMap(List::stream)
            .filter(r -> operatorId.equals(r.getOperatorId())));
    }
}
```

**Step 4: Integrate into DataEnricher**

Add optional `LineageTracker` to `DataEnricher` constructor (with backward-compatible constructors). In `enrichAndCache()`, after successful enrichment, record lineage:

```java
// After line 227 in DataEnricher.java (after cache put)
if (lineageTracker != null && response.isSuccess()) {
    String entityId = request.paramAsString("entityId", request.getRequestId());
    lineageTracker.record(LineageRecord.builder()
        .recordId(UUID.randomUUID().toString())
        .entityId(entityId)
        .sourceSystem(getProviderName())
        .operation("ENRICHMENT")
        .operatorId(getProviderName())
        .timestamp(Instant.now())
        .inputHash(String.valueOf(request.getParameters().hashCode()))
        .outputHash(String.valueOf(response.getEnrichedData().hashCode()))
        .traceId(TracingContextExtractor.extractTraceId())
        .build()
    ).subscribe();
}
```

**Step 5: Write tests**

```java
@Test
void lineageTracker_shouldRecordEnrichmentLineage() {
    InMemoryLineageTracker tracker = new InMemoryLineageTracker();
    // Create enricher with tracker, enrich, verify lineage recorded

    StepVerifier.create(tracker.getLineage("entity-1"))
        .assertNext(record -> {
            assertThat(record.getOperation()).isEqualTo("ENRICHMENT");
            assertThat(record.getOperatorId()).isEqualTo("TestProvider");
        })
        .verifyComplete();
}
```

**Step 6: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=LineageTrackerTest`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/org/fireflyframework/data/lineage/ src/main/java/org/fireflyframework/data/event/LineageEvent.java src/test/
git commit -m "feat(lineage): add data lineage tracking with InMemoryLineageTracker and DataEnricher integration"
```

---

## Phase 5: Enrichment Fallback Chains

### Task 10: Create Fallback Chain Support

**Files:**
- Create: `src/main/java/org/fireflyframework/data/enrichment/EnricherFallback.java` (annotation)
- Create: `src/main/java/org/fireflyframework/data/enrichment/FallbackStrategy.java` (enum)
- Create: `src/main/java/org/fireflyframework/data/enrichment/FallbackEnrichmentExecutor.java`
- Modify: `src/main/java/org/fireflyframework/data/service/DataEnricherRegistry.java` (add fallback chain resolution)
- Test: `src/test/java/org/fireflyframework/data/enrichment/FallbackEnrichmentExecutorTest.java`

**Step 1: Define annotation and enum**

```java
// FallbackStrategy.java
public enum FallbackStrategy {
    ON_ERROR,        // Fallback only on errors
    ON_EMPTY,        // Fallback when result has no enriched fields
    ON_ERROR_OR_EMPTY // Fallback on either condition
}

// EnricherFallback.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnricherFallback {
    String fallbackTo();                               // provider name of fallback enricher
    FallbackStrategy strategy() default FallbackStrategy.ON_ERROR;
    int maxFallbacks() default 3;                      // max chain depth
}
```

**Step 2: Create FallbackEnrichmentExecutor**

```java
@Slf4j
public class FallbackEnrichmentExecutor {

    private final DataEnricherRegistry registry;

    public FallbackEnrichmentExecutor(DataEnricherRegistry registry) {
        this.registry = registry;
    }

    public Mono<EnrichmentResponse> enrichWithFallback(
            DataEnricher<?, ?, ?> primary, EnrichmentRequest request) {

        return primary.enrich(request)
            .flatMap(response -> {
                if (shouldFallback(primary, response)) {
                    return executeFallbackChain(primary, request, response, 0);
                }
                return Mono.just(response);
            });
    }

    private Mono<EnrichmentResponse> executeFallbackChain(
            DataEnricher<?, ?, ?> current, EnrichmentRequest request,
            EnrichmentResponse lastResponse, int depth) {

        EnricherFallback fallbackAnnotation = current.getClass().getAnnotation(EnricherFallback.class);
        if (fallbackAnnotation == null || depth >= fallbackAnnotation.maxFallbacks()) {
            return Mono.just(lastResponse);
        }

        String fallbackProvider = fallbackAnnotation.fallbackTo();
        return registry.getEnricherByProvider(fallbackProvider)
            .map(fallback -> {
                log.info("Falling back from {} to {} (depth {})",
                    current.getProviderName(), fallbackProvider, depth + 1);
                return fallback.enrich(request)
                    .flatMap(fbResponse -> {
                        if (shouldFallback(fallback, fbResponse)) {
                            return executeFallbackChain(fallback, request, fbResponse, depth + 1);
                        }
                        // Add fallback metadata
                        fbResponse.getMetadata().put("fallbackFrom", current.getProviderName());
                        fbResponse.getMetadata().put("fallbackDepth", depth + 1);
                        return Mono.just(fbResponse);
                    });
            })
            .orElseGet(() -> {
                log.warn("Fallback provider {} not found", fallbackProvider);
                return Mono.just(lastResponse);
            });
    }

    private boolean shouldFallback(DataEnricher<?, ?, ?> enricher, EnrichmentResponse response) {
        EnricherFallback annotation = enricher.getClass().getAnnotation(EnricherFallback.class);
        if (annotation == null) return false;

        return switch (annotation.strategy()) {
            case ON_ERROR -> !response.isSuccess();
            case ON_EMPTY -> response.isSuccess() && response.getFieldsEnriched() == 0;
            case ON_ERROR_OR_EMPTY -> !response.isSuccess() || response.getFieldsEnriched() == 0;
        };
    }
}
```

**Step 3: Write tests**

```java
@Test
void enrichWithFallback_shouldFallbackOnError() {
    // Primary enricher fails → fallback enricher succeeds
    when(primaryEnricher.enrich(any())).thenReturn(Mono.just(EnrichmentResponse.failure("type", "primary", "error")));
    when(fallbackEnricher.enrich(any())).thenReturn(Mono.just(EnrichmentResponse.success("type", enrichedData)));
    when(registry.getEnricherByProvider("fallback-provider")).thenReturn(Optional.of(fallbackEnricher));

    StepVerifier.create(executor.enrichWithFallback(primaryEnricher, request))
        .assertNext(response -> {
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMetadata()).containsKey("fallbackFrom");
        })
        .verifyComplete();
}

@Test
void enrichWithFallback_shouldRespectMaxDepth() { ... }

@Test
void enrichWithFallback_shouldNotFallbackOnSuccess() { ... }
```

**Step 4: Run tests**

Run: `cd fireflyframework-starter-data && mvn test -pl . -Dtest=FallbackEnrichmentExecutorTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/org/fireflyframework/data/enrichment/ src/test/
git commit -m "feat(enrichment): add @EnricherFallback annotation and FallbackEnrichmentExecutor for provider failover chains"
```

---

## Phase 6: Data Transformation Pipeline

### Task 11: Create Transformation Abstractions

**Files:**
- Create: `src/main/java/org/fireflyframework/data/transform/DataTransformer.java`
- Create: `src/main/java/org/fireflyframework/data/transform/TransformationChain.java`
- Create: `src/main/java/org/fireflyframework/data/transform/TransformContext.java`
- Create: `src/main/java/org/fireflyframework/data/transform/transformers/FieldMappingTransformer.java`
- Create: `src/main/java/org/fireflyframework/data/transform/transformers/ComputedFieldTransformer.java`
- Test: `src/test/java/org/fireflyframework/data/transform/TransformationChainTest.java`

**Step 1: Define the contracts**

```java
// DataTransformer.java — functional interface
@FunctionalInterface
public interface DataTransformer<S, T> {
    Mono<T> transform(S source, TransformContext context);
}

// TransformContext.java
@Data @Builder
public class TransformContext {
    private final String requestId;
    private final UUID tenantId;
    private final Map<String, Object> metadata;
    private final Instant startTime;
}
```

**Step 2: Implement TransformationChain**

```java
@Slf4j
public class TransformationChain<S, T> {

    private final List<DataTransformer<Object, Object>> transformers = new ArrayList<>();
    private final Class<T> outputType;

    public TransformationChain(Class<T> outputType) {
        this.outputType = outputType;
    }

    @SuppressWarnings("unchecked")
    public <U> TransformationChain<S, T> then(DataTransformer<?, ?> transformer) {
        transformers.add((DataTransformer<Object, Object>) transformer);
        return this;
    }

    @SuppressWarnings("unchecked")
    public Mono<T> execute(S source, TransformContext context) {
        Mono<Object> result = Mono.just((Object) source);

        for (DataTransformer<Object, Object> transformer : transformers) {
            result = result.flatMap(current -> transformer.transform(current, context));
        }

        return result.map(r -> (T) r);
    }

    public static <S, T> TransformationChain<S, T> of(Class<T> outputType) {
        return new TransformationChain<>(outputType);
    }
}
```

**Step 3: Implement built-in transformers**

```java
// FieldMappingTransformer.java
public class FieldMappingTransformer implements DataTransformer<Map<String, Object>, Map<String, Object>> {
    private final Map<String, String> fieldMappings; // sourceField -> targetField

    @Override
    public Mono<Map<String, Object>> transform(Map<String, Object> source, TransformContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>(source);
            fieldMappings.forEach((from, to) -> {
                if (result.containsKey(from)) {
                    result.put(to, result.remove(from));
                }
            });
            return result;
        });
    }
}

// ComputedFieldTransformer.java
public class ComputedFieldTransformer<T> implements DataTransformer<T, T> {
    private final String fieldName;
    private final Function<T, Object> computation;
    // Sets computed field on the DTO
}
```

**Step 4: Write tests**

```java
@Test
void chain_shouldApplyTransformersInOrder() {
    TransformationChain<Map<String, Object>, Map<String, Object>> chain = TransformationChain.of(Map.class)
        .then(new FieldMappingTransformer(Map.of("old_name", "newName")))
        .then((source, ctx) -> {
            source.put("computed", source.get("newName") + "-enriched");
            return Mono.just(source);
        });

    Map<String, Object> input = Map.of("old_name", "Firefly", "value", 42);
    TransformContext ctx = TransformContext.builder().requestId("req-1").build();

    StepVerifier.create(chain.execute(input, ctx))
        .assertNext(result -> {
            assertThat(result).containsKey("newName");
            assertThat(result).doesNotContainKey("old_name");
            assertThat(result.get("computed")).isEqualTo("Firefly-enriched");
        })
        .verifyComplete();
}
```

**Step 5: Commit**

```bash
git add src/main/java/org/fireflyframework/data/transform/ src/test/
git commit -m "feat(transform): add composable DataTransformer and TransformationChain for post-enrichment data transformation"
```

---

## Phase 7: Dry-Run / Preview Mode

### Task 12: Add Preview Endpoint for Enrichments

**Files:**
- Create: `src/main/java/org/fireflyframework/data/model/PreviewResponse.java`
- Modify: `src/main/java/org/fireflyframework/data/controller/SmartEnrichmentController.java` (add preview endpoint)
- Test: `src/test/java/org/fireflyframework/data/controller/SmartEnrichmentControllerPreviewTest.java`

**Step 1: Create PreviewResponse model**

```java
@Data @Builder
public class PreviewResponse {
    private final String providerName;
    private final String enrichmentType;
    private final String providerVersion;
    private final int priority;
    private final boolean cached;
    private final String estimatedDuration;
    private final UUID tenantId;
    private final boolean autoSelected;
    private final Map<String, Object> providerCapabilities;
}
```

**Step 2: Add preview endpoint**

In `SmartEnrichmentController`, add:
```java
@PostMapping("/smart/preview")
@Operation(summary = "Preview enrichment without executing")
public Mono<ResponseEntity<PreviewResponse>> preview(@RequestBody EnrichmentApiRequest apiRequest) {
    String type = apiRequest.getType();
    UUID tenantId = apiRequest.getTenantId() != null ? UUID.fromString(apiRequest.getTenantId()) : null;

    Optional<DataEnricher<?, ?, ?>> enricherOpt = tenantId != null
        ? enricherRegistry.getEnricherForTypeAndTenant(type, tenantId)
        : enricherRegistry.getEnricherForType(type);

    if (enricherOpt.isEmpty()) {
        return Mono.just(ResponseEntity.notFound().build());
    }

    DataEnricher<?, ?, ?> enricher = enricherOpt.get();

    // Check if result would be cached
    boolean cached = cacheService != null && cacheService.isCacheEnabled();

    PreviewResponse preview = PreviewResponse.builder()
        .providerName(enricher.getProviderName())
        .enrichmentType(type)
        .providerVersion(enricher.getEnricherVersion())
        .priority(enricher.getPriority())
        .cached(cached)
        .tenantId(enricher.getTenantId())
        .autoSelected(true)
        .build();

    return Mono.just(ResponseEntity.ok(preview));
}
```

**Step 3: Write tests and commit**

```bash
git commit -m "feat(api): add POST /api/v1/enrichment/smart/preview for dry-run enrichment preview"
```

---

## Phase 8: Enrichment Cost Tracking

### Task 13: Add Cost Tracking to Enrichments

**Files:**
- Create: `src/main/java/org/fireflyframework/data/cost/EnrichmentCostTracker.java`
- Create: `src/main/java/org/fireflyframework/data/cost/CostReport.java`
- Create: `src/main/java/org/fireflyframework/data/controller/EnrichmentCostController.java`
- Modify: `src/main/java/org/fireflyframework/data/enrichment/EnricherMetadata.java` (add cost attributes)
- Test: `src/test/java/org/fireflyframework/data/cost/EnrichmentCostTrackerTest.java`

**Step 1: Add cost attributes to @EnricherMetadata**

```java
// Add to EnricherMetadata annotation:
double costPerCall() default 0.0;
String costCurrency() default "USD";
```

**Step 2: Create EnrichmentCostTracker**

```java
@Slf4j
public class EnrichmentCostTracker {

    private final Map<String, AtomicLong> callCountByProvider = new ConcurrentHashMap<>();
    private final Map<String, Double> costPerCallByProvider = new ConcurrentHashMap<>();

    public void registerProvider(String providerName, double costPerCall, String currency) {
        costPerCallByProvider.put(providerName, costPerCall);
        callCountByProvider.putIfAbsent(providerName, new AtomicLong(0));
    }

    public void recordCall(String providerName) {
        callCountByProvider.computeIfAbsent(providerName, k -> new AtomicLong(0)).incrementAndGet();
    }

    public CostReport getReport() { ... }
    public CostReport getReportByTenant(UUID tenantId) { ... }
}
```

**Step 3: Expose via REST**

```java
@RestController
@RequestMapping("/api/v1/enrichment/costs")
public class EnrichmentCostController {
    @GetMapping
    public Mono<CostReport> getCosts(@RequestParam(required = false) String tenantId) { ... }
}
```

**Step 4: Write tests and commit**

```bash
git commit -m "feat(cost): add enrichment cost tracking with per-provider call counting and cost reports"
```

---

## Phase 9: Streaming Enrichment (SSE)

### Task 14: Add SSE Streaming for Batch Enrichments

**Files:**
- Modify: `src/main/java/org/fireflyframework/data/controller/SmartEnrichmentController.java`
- Test: `src/test/java/org/fireflyframework/data/controller/SmartEnrichmentControllerStreamTest.java`

**Step 1: Add streaming endpoint**

```java
@PostMapping(value = "/smart/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "Stream batch enrichment results via SSE")
public Flux<ServerSentEvent<EnrichmentApiResponse>> streamEnrichment(
        @RequestBody List<EnrichmentApiRequest> apiRequests) {

    return Flux.fromIterable(apiRequests)
        .index()
        .flatMap(indexed -> {
            long index = indexed.getT1();
            EnrichmentApiRequest apiRequest = indexed.getT2();
            return processSmartEnrichment(apiRequest)
                .map(response -> ServerSentEvent.<EnrichmentApiResponse>builder()
                    .id(String.valueOf(index))
                    .event("enrichment-result")
                    .data(response)
                    .build());
        }, 10) // parallelism
        .concatWith(Flux.just(ServerSentEvent.<EnrichmentApiResponse>builder()
            .event("complete")
            .build()));
}
```

**Step 2: Write tests and commit**

```bash
git commit -m "feat(api): add SSE streaming endpoint POST /api/v1/enrichment/smart/stream for real-time batch results"
```

---

## Phase 10: Auto-Configuration Wiring

### Task 15: Wire All New Features into Auto-Configuration

**Files:**
- Modify: `src/main/java/org/fireflyframework/data/config/DataEnrichmentAutoConfiguration.java`
- Create: `src/main/java/org/fireflyframework/data/config/DataQualityAutoConfiguration.java`
- Create: `src/main/java/org/fireflyframework/data/config/DataLineageAutoConfiguration.java`
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Step 1: Create DataQualityAutoConfiguration**

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.data.quality", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataQualityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DataQualityEngine dataQualityEngine(
            @Autowired(required = false) List<DataQualityRule<?>> rules,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        return new DataQualityEngine(rules != null ? rules : List.of(), eventPublisher);
    }
}
```

**Step 2: Create DataLineageAutoConfiguration**

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.data.lineage", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DataLineageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LineageTracker lineageTracker() {
        return new InMemoryLineageTracker();
    }
}
```

**Step 3: Wire FallbackEnrichmentExecutor in DataEnrichmentAutoConfiguration**

```java
@Bean
@ConditionalOnMissingBean
public FallbackEnrichmentExecutor fallbackEnrichmentExecutor(DataEnricherRegistry registry) {
    return new FallbackEnrichmentExecutor(registry);
}
```

**Step 4: Register auto-configurations**

Add to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
org.fireflyframework.data.config.DataQualityAutoConfiguration
org.fireflyframework.data.config.DataLineageAutoConfiguration
```

**Step 5: Run full test suite**

Run: `cd fireflyframework-starter-data && mvn test`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/org/fireflyframework/data/config/ src/main/resources/
git commit -m "feat(config): wire DataQuality, DataLineage, FallbackExecutor, ProviderResiliency into auto-configuration"
```

---

## Phase 11: GenAI Bridge Package

### Task 16: Create fireflyframework-genai-data Python Package

**Files (all in a new directory: `fireflyframework-genai-data/`):**
- Create: `pyproject.toml`
- Create: `src/fireflyframework_genai_data/__init__.py`
- Create: `src/fireflyframework_genai_data/client.py` (async HTTP client)
- Create: `src/fireflyframework_genai_data/tools/enrichment_tool.py`
- Create: `src/fireflyframework_genai_data/tools/job_tool.py`
- Create: `src/fireflyframework_genai_data/tools/operations_tool.py`
- Create: `src/fireflyframework_genai_data/tools/toolkit.py`
- Create: `src/fireflyframework_genai_data/steps/enrichment_step.py`
- Create: `src/fireflyframework_genai_data/steps/quality_step.py`
- Create: `src/fireflyframework_genai_data/middleware/lineage.py`
- Create: `src/fireflyframework_genai_data/agents/templates.py`
- Create: `tests/test_enrichment_tool.py`
- Create: `tests/test_job_tool.py`
- Create: `tests/test_enrichment_step.py`

**Step 1: Create pyproject.toml**

```toml
[project]
name = "fireflyframework-genai-data"
version = "26.02.07"
description = "GenAI bridge for fireflyframework-starter-data"
requires-python = ">=3.13"
license = "Apache-2.0"
dependencies = [
    "fireflyframework-genai>=26.02.07",
    "httpx>=0.27.0",
]

[project.entry-points."fireflyframework_genai.tools"]
enrichment_tool = "fireflyframework_genai_data.tools.enrichment_tool:DataEnrichmentTool"
job_tool = "fireflyframework_genai_data.tools.job_tool:DataJobTool"
operations_tool = "fireflyframework_genai_data.tools.operations_tool:DataOperationsTool"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
```

**Step 2: Create the HTTP client**

```python
# client.py
import httpx
from dataclasses import dataclass

@dataclass
class DataStarterClient:
    base_url: str
    timeout: float = 30.0
    _client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(base_url=self.base_url, timeout=self.timeout)
        return self._client

    async def enrich(self, type: str, strategy: str, parameters: dict,
                     tenant_id: str | None = None) -> dict:
        client = await self._get_client()
        payload = {"type": type, "strategy": strategy, "parameters": parameters}
        if tenant_id:
            payload["tenantId"] = tenant_id
        response = await client.post("/api/v1/enrichment/smart", json=payload)
        response.raise_for_status()
        return response.json()

    async def start_job(self, job_type: str, parameters: dict) -> dict:
        client = await self._get_client()
        response = await client.post("/api/v1/jobs/start", json={
            "jobType": job_type, "parameters": parameters
        })
        response.raise_for_status()
        return response.json()

    async def check_job(self, execution_id: str) -> dict:
        client = await self._get_client()
        response = await client.get(f"/api/v1/jobs/{execution_id}/check")
        response.raise_for_status()
        return response.json()

    async def collect_results(self, execution_id: str) -> dict:
        client = await self._get_client()
        response = await client.get(f"/api/v1/jobs/{execution_id}/collect")
        response.raise_for_status()
        return response.json()

    async def list_providers(self, type: str | None = None) -> list[dict]:
        client = await self._get_client()
        params = {}
        if type:
            params["type"] = type
        response = await client.get("/api/v1/enrichment/providers", params=params)
        response.raise_for_status()
        return response.json()

    async def execute_operation(self, type: str, operation_id: str, request: dict) -> dict:
        client = await self._get_client()
        response = await client.post("/api/v1/enrichment/operations/execute", json={
            "type": type, "operationId": operation_id, "request": request
        })
        response.raise_for_status()
        return response.json()

    async def close(self):
        if self._client:
            await self._client.aclose()
```

**Step 3: Create DataEnrichmentTool**

```python
# tools/enrichment_tool.py
from fireflyframework_genai.tools.base import BaseTool, ParameterSpec
from fireflyframework_genai_data.client import DataStarterClient

class DataEnrichmentTool(BaseTool):
    def __init__(self, client: DataStarterClient):
        super().__init__(
            name="data_enrichment",
            description="Enrich data using external providers (credit bureaus, company registries, etc.)",
            parameters=[
                ParameterSpec(name="type", type=str, description="Enrichment type (e.g., 'company-profile', 'credit-report')", required=True),
                ParameterSpec(name="strategy", type=str, description="Strategy: ENHANCE (fill nulls), MERGE (provider wins), REPLACE, RAW", required=True),
                ParameterSpec(name="parameters", type=dict, description="Provider-specific parameters", required=True),
                ParameterSpec(name="tenant_id", type=str, description="Tenant ID for multi-tenant isolation", required=False),
            ]
        )
        self._client = client

    async def _execute(self, *, type: str, strategy: str, parameters: dict,
                       tenant_id: str | None = None) -> dict:
        return await self._client.enrich(type, strategy, parameters, tenant_id)
```

**Step 4: Create DataJobTool**

```python
# tools/job_tool.py
from fireflyframework_genai.tools.base import BaseTool, ParameterSpec

class DataJobTool(BaseTool):
    def __init__(self, client):
        super().__init__(
            name="data_job",
            description="Manage async data processing jobs (start, check status, collect results, stop)",
            parameters=[
                ParameterSpec(name="action", type=str, description="Action: start, check, collect, stop", required=True),
                ParameterSpec(name="job_type", type=str, description="Job type (required for 'start')", required=False),
                ParameterSpec(name="execution_id", type=str, description="Execution ID (required for check/collect/stop)", required=False),
                ParameterSpec(name="parameters", type=dict, description="Job parameters (for 'start')", required=False),
            ]
        )
        self._client = client

    async def _execute(self, *, action: str, job_type: str | None = None,
                       execution_id: str | None = None, parameters: dict | None = None) -> dict:
        match action:
            case "start":
                return await self._client.start_job(job_type, parameters or {})
            case "check":
                return await self._client.check_job(execution_id)
            case "collect":
                return await self._client.collect_results(execution_id)
            case _:
                return {"error": f"Unknown action: {action}"}
```

**Step 5: Create DataToolKit**

```python
# tools/toolkit.py
from fireflyframework_genai.tools.toolkit import ToolKit
from fireflyframework_genai_data.client import DataStarterClient
from fireflyframework_genai_data.tools.enrichment_tool import DataEnrichmentTool
from fireflyframework_genai_data.tools.job_tool import DataJobTool
from fireflyframework_genai_data.tools.operations_tool import DataOperationsTool

class DataToolKit(ToolKit):
    def __init__(self, base_url: str, timeout: float = 30.0):
        client = DataStarterClient(base_url=base_url, timeout=timeout)
        super().__init__(
            name="firefly-data",
            description="Tools for interacting with Firefly Data Services",
            tools=[
                DataEnrichmentTool(client),
                DataJobTool(client),
                DataOperationsTool(client),
            ],
            tags=["data", "enrichment", "jobs"]
        )
```

**Step 6: Create EnrichmentStep for pipelines**

```python
# steps/enrichment_step.py
from fireflyframework_genai.pipeline.steps import StepExecutor
from fireflyframework_genai.pipeline.context import PipelineContext
from fireflyframework_genai_data.client import DataStarterClient

class EnrichmentStep:
    """Pipeline step that enriches data via the data starter."""

    def __init__(self, client: DataStarterClient, enrichment_type: str,
                 strategy: str = "ENHANCE"):
        self._client = client
        self._type = enrichment_type
        self._strategy = strategy

    async def execute(self, context: PipelineContext, inputs: dict) -> dict:
        parameters = inputs.get("input", inputs)
        result = await self._client.enrich(
            type=self._type,
            strategy=self._strategy,
            parameters=parameters if isinstance(parameters, dict) else {"data": parameters}
        )
        return result
```

**Step 7: Create DataLineageMiddleware**

```python
# middleware/lineage.py
import time
from fireflyframework_genai.agents.middleware import MiddlewareContext

class DataLineageMiddleware:
    """Tracks data lineage for every agent run involving data operations."""

    def __init__(self, lineage_store: dict | None = None):
        self._store = lineage_store if lineage_store is not None else {}

    async def before_run(self, context: MiddlewareContext) -> None:
        context.metadata["_lineage_start"] = time.monotonic()

    async def after_run(self, context: MiddlewareContext, result) -> any:
        elapsed = time.monotonic() - context.metadata.get("_lineage_start", 0)
        self._store.setdefault(context.agent_name, []).append({
            "agent": context.agent_name,
            "prompt_hash": hash(str(context.prompt)),
            "duration_ms": round(elapsed * 1000),
            "timestamp": time.time(),
        })
        return result
```

**Step 8: Create agent templates**

```python
# agents/templates.py
from fireflyframework_genai.agents.base import FireflyAgent
from fireflyframework_genai_data.tools.toolkit import DataToolKit

def create_data_analyst_agent(
    base_url: str,
    name: str = "data-analyst",
    model: str | None = None,
    **kwargs
) -> FireflyAgent:
    """Pre-configured agent with data enrichment and job management tools."""
    toolkit = DataToolKit(base_url=base_url)
    return FireflyAgent(
        name=name,
        model=model,
        instructions=[
            "You are a data analyst agent with access to data enrichment and job management tools.",
            "Use the data_enrichment tool to enrich records with external provider data.",
            "Use the data_job tool to manage long-running data processing jobs.",
        ],
        tools=[toolkit],
        tags=["data", "analyst"],
        **kwargs
    )
```

**Step 9: Write tests**

```python
# tests/test_enrichment_tool.py
import pytest
from unittest.mock import AsyncMock, patch
from fireflyframework_genai_data.tools.enrichment_tool import DataEnrichmentTool
from fireflyframework_genai_data.client import DataStarterClient

@pytest.mark.asyncio
async def test_enrichment_tool_calls_client():
    client = AsyncMock(spec=DataStarterClient)
    client.enrich.return_value = {"success": True, "enrichedData": {"name": "Firefly"}}

    tool = DataEnrichmentTool(client)
    result = await tool.execute(type="company-profile", strategy="ENHANCE", parameters={"companyId": "123"})

    assert result["success"] is True
    client.enrich.assert_called_once_with("company-profile", "ENHANCE", {"companyId": "123"}, None)

@pytest.mark.asyncio
async def test_enrichment_tool_passes_tenant_id():
    client = AsyncMock(spec=DataStarterClient)
    client.enrich.return_value = {"success": True}

    tool = DataEnrichmentTool(client)
    await tool.execute(type="credit-report", strategy="MERGE", parameters={}, tenant_id="tenant-1")

    client.enrich.assert_called_once_with("credit-report", "MERGE", {}, "tenant-1")
```

**Step 10: Run tests**

Run: `cd fireflyframework-genai-data && uv run pytest tests/ -v`
Expected: ALL PASS

**Step 11: Commit**

```bash
git add fireflyframework-genai-data/
git commit -m "feat(genai-bridge): create fireflyframework-genai-data package with tools, pipeline steps, middleware, and agent templates"
```

---

## Phase 12: Integration Tests & Final Verification

### Task 17: Write Cross-Feature Integration Tests

**Files:**
- Create: `src/test/java/org/fireflyframework/data/integration/FullStackIntegrationTest.java`
- Create: `src/test/java/org/fireflyframework/data/integration/FallbackChainIntegrationTest.java`
- Create: `src/test/java/org/fireflyframework/data/integration/QualityGateIntegrationTest.java`

**Step 1: Write FullStackIntegrationTest**

Tests the full flow: enrichment with fallback → quality gate → transformation → lineage recording.

```java
@ExtendWith(MockitoExtension.class)
class FullStackIntegrationTest {

    @Test
    void fullFlow_enrichWithFallback_qualityGate_transform_lineage() {
        // 1. Primary enricher fails
        // 2. Fallback enricher succeeds
        // 3. Quality engine validates output
        // 4. Transformation chain normalizes fields
        // 5. Lineage tracker records provenance

        // Verify all pieces work together
    }
}
```

**Step 2: Run full test suite**

Run: `cd fireflyframework-starter-data && mvn test`
Expected: ALL PASS (all existing + all new tests)

**Step 3: Commit**

```bash
git add src/test/java/org/fireflyframework/data/integration/
git commit -m "test: add cross-feature integration tests for fallback chains, quality gates, and lineage tracking"
```

---

## Summary

| Phase | Tasks | Files Created | Files Modified | Tests |
|-------|-------|--------------|----------------|-------|
| 1. Bug Fixes | 1-6 | 1 | 7 | ~15 |
| 2. Per-Provider Resilience | 7 | 3 | 2 | ~5 |
| 3. Data Quality | 8 | 8 | 0 | ~10 |
| 4. Data Lineage | 9 | 5 | 1 | ~5 |
| 5. Fallback Chains | 10 | 3 | 1 | ~5 |
| 6. Transformation Pipeline | 11 | 5 | 0 | ~5 |
| 7. Dry-Run / Preview | 12 | 1 | 1 | ~3 |
| 8. Cost Tracking | 13 | 3 | 1 | ~3 |
| 9. SSE Streaming | 14 | 0 | 1 | ~3 |
| 10. Auto-Config Wiring | 15 | 2 | 2 | ~3 |
| 11. GenAI Bridge | 16 | 12 | 0 | ~8 |
| 12. Integration Tests | 17 | 3 | 0 | ~5 |
| **TOTAL** | **17** | **~46** | **~16** | **~70** |
