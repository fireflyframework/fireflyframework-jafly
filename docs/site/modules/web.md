![Jafly. Web](../assets/modules/web.svg)

# Web

Handle WebFlux errors, request metadata, idempotency, CORS, and sensitive-data masking consistently.

Add this library to a reactive web application and tune the individual firefly.web settings. Review allowed origins, body logging, and masking for the application data you actually expose; the source-backed property tables show the current defaults.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-web` | `jar` | [modules/web](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/web) |

Part of [Service operations](index.md#service-operations).

## In the onboarding service

The onboarding HTTP boundary needs consistent error responses and carefully chosen CORS/logging settings. Decide which applicant fields may appear in logs before enabling request bodies or headers.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-web</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

Add the dependency and the web layer is wired automatically. Throw the framework's semantic exceptions from your reactive controllers and the `GlobalExceptionHandler` converts them to standardized RFC 7807 responses:

```java
import lombok.RequiredArgsConstructor;
import org.fireflyframework.web.error.exceptions.ResourceNotFoundException;
import org.fireflyframework.web.error.exceptions.ConflictException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;



    @GetMapping("/{id}")
    public Mono<Order> findById(@PathVariable String id) {
        return orderService.findById(id)
            // -> 404 with an RFC 7807 Problem Details body
            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Order not found: " + id)));
    }

    @PostMapping
    public Mono<Order> create(@Valid @RequestBody OrderRequest request) {
        // Bean Validation failures are auto-converted to 400 responses.
        // ConflictException -> 409, BusinessException -> 422, etc.
        return orderService.create(request)
            .onErrorMap(DuplicateOrderException.class,
                e -> new ConflictException("Order already exists"));
    }
}
```

A typical error response (RFC 7807 Problem Details):

```json
{
  "type": "https://docs.example.com/errors/RESOURCE_NOT_FOUND",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Order not found: 12345",
  "instance": "/api/orders/12345",
  "code": "RESOURCE_NOT_FOUND",
  "timestamp": "2026-05-31T10:15:30Z",
  "correlationId": "b1d3...",
  "traceId": "4f9a..."
}
```

### Idempotent requests

Clients send a unique `X-Idempotency-Key` header on a write request; retries with the same key replay the original cached response instead of re-executing the handler. Opt an endpoint out with `@DisableIdempotency`:

```java
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;

@PostMapping("/payments")
public Mono<Payment> pay(@RequestBody PaymentRequest req) {
    // Send header "X-Idempotency-Key: <uuid>"; a retry returns the same response.
    return paymentService.charge(req);
}

@PostMapping("/heartbeat")
@DisableIdempotency // never deduplicated, header hidden from OpenAPI
public Mono<Void> heartbeat() {
    return monitor.ping();
}
```

## Configuration and wiring

The `firefly.web.conditions-report` namespace is bound by [ConditionsReportProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/conditions/config/ConditionsReportProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.web.conditions-report.enabled` | `boolean` | `false` |
| `firefly.web.conditions-report.show-negative` | `boolean` | `true` |
| `firefly.web.conditions-report.show-details` | `boolean` | `true` |
| `firefly.web.conditions-report.summary-only` | `boolean` | `false` |
| `firefly.web.conditions-report.use-colors` | `boolean` | `true` |

The `firefly.web.cors` namespace is bound by [CorsProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/cors/config/CorsProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.web.cors.enabled` | `boolean` | `true` |
| `firefly.web.cors.path-pattern` | `String` | `"/**"` |
| `firefly.web.cors.allowed-origins` | `List<String>` | `List.of()` |
| `firefly.web.cors.allowed-methods` | `List<String>` | `List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")` |
| `firefly.web.cors.allowed-headers` | `List<String>` | `List.of("*")` |
| `firefly.web.cors.exposed-headers` | `List<String>` | `List.of("X-Transaction-Id")` |
| `firefly.web.cors.allow-credentials` | `boolean` | `false` |
| `firefly.web.cors.max-age` | `long` | `3600` |

The `firefly.web.error-handling` namespace is bound by [ErrorHandlingProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/error/config/ErrorHandlingProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.web.error-handling.include-stack-trace` | `boolean` | `false` |
| `firefly.web.error-handling.include-debug-info` | `boolean` | `false` |
| `firefly.web.error-handling.include-exception-cause` | `boolean` | `false` |
| `firefly.web.error-handling.documentation-base-url` | `String` | `not initialized` |
| `firefly.web.error-handling.help-base-url` | `String` | `not initialized` |
| `firefly.web.error-handling.support-email` | `String` | `not initialized` |
| `firefly.web.error-handling.support-phone` | `String` | `not initialized` |
| `firefly.web.error-handling.instance-id` | `String` | `not initialized` |
| `firefly.web.error-handling.enable-metrics` | `boolean` | `true` |
| `firefly.web.error-handling.enable-distributed-tracing` | `boolean` | `true` |
| `firefly.web.error-handling.mask-sensitive-data` | `boolean` | `true` |
| `firefly.web.error-handling.include-correlation-id` | `boolean` | `true` |
| `firefly.web.error-handling.include-request-id` | `boolean` | `true` |
| `firefly.web.error-handling.include-span-id` | `boolean` | `true` |

The `firefly.web.idempotency` namespace is bound by [IdempotencyProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/idempotency/config/IdempotencyProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.web.idempotency.enabled` | `boolean` | `true` |
| `firefly.web.idempotency.header-name` | `String` | `"X-Idempotency-Key"` |
| `firefly.web.idempotency.cache` | `Cache` | `new Cache()` |

The `firefly.web.http-request-logging` namespace is bound by [HttpRequestLoggingProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/HttpRequestLoggingProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.web.http-request-logging.enabled` | `boolean` | `true` |
| `firefly.web.http-request-logging.include-headers` | `boolean` | `true` |
| `firefly.web.http-request-logging.include-request-body` | `boolean` | `false` |
| `firefly.web.http-request-logging.include-response-body` | `boolean` | `false` |
| `firefly.web.http-request-logging.max-body-size` | `int` | `1024` |
| `firefly.web.http-request-logging.sensitive-headers` | `Set<String>` | `nested/default initializer; see source` |
| `firefly.web.http-request-logging.excluded-paths` | `Set<String>` | `new HashSet<>(Arrays.asList( "/health", "/actuator/**", "/metrics", "/info"))` |
| `firefly.web.http-request-logging.log-level` | `String` | `"INFO"` |
| `firefly.web.http-request-logging.include-query-params` | `boolean` | `true` |

The `firefly.web.pii-masking` namespace is bound by [PiiMaskingProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/PiiMaskingProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.web.pii-masking.enabled` | `boolean` | `true` |
| `firefly.web.pii-masking.mask-character` | `String` | `"*"` |
| `firefly.web.pii-masking.preserve-length` | `boolean` | `true` |
| `firefly.web.pii-masking.show-characters` | `int` | `3` |
| `firefly.web.pii-masking.patterns` | `Map<String, String>` | `initializeDefaultPatterns()` |
| `firefly.web.pii-masking.custom-patterns` | `Map<String, String>` | `new HashMap<>()` |
| `firefly.web.pii-masking.mask-headers` | `boolean` | `true` |
| `firefly.web.pii-masking.mask-bodies` | `boolean` | `true` |
| `firefly.web.pii-masking.mask-query-params` | `boolean` | `true` |
| `firefly.web.pii-masking.mask-exceptions` | `boolean` | `true` |
| `firefly.web.pii-masking.auto-mask-logs` | `boolean` | `true` |
| `firefly.web.pii-masking.enable-stdout-masking` | `boolean` | `true` |
| `firefly.web.pii-masking.case-sensitive` | `boolean` | `false` |

Property-based wiring conditions found in this module:

- `prefix = "firefly.web.conditions-report", name = "enabled", havingValue = "true", matchIfMissing = false`. [ConditionsReportAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/conditions/config/ConditionsReportAutoConfiguration.java).
- `prefix = "firefly.web.cors", name = "enabled", havingValue = "true", matchIfMissing = true`. [CorsAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/cors/config/CorsAutoConfiguration.java).
- `prefix = "firefly.web.error-handling", name = "enable-error-caching", havingValue = "true"`. [ErrorResponseCache.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/error/service/ErrorResponseCache.java).
- `name = "pii-masking.enabled", havingValue = "true", matchIfMissing = true`. [PiiMaskingAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/PiiMaskingAutoConfiguration.java).
- `name = "pii-masking.enable-stdout-masking", havingValue = "true", matchIfMissing = true`. [PiiMaskingAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/PiiMaskingAutoConfiguration.java).
- `name = "pii-masking.auto-mask-logs", havingValue = "true", matchIfMissing = true`. [PiiMaskingLoggingConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/PiiMaskingLoggingConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`GlobalExceptionHandler`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/error/handler/GlobalExceptionHandler.java) | Enhanced global exception handler for enrichment workflows. |
| [`CorsProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/cors/config/CorsProperties.java) | Configuration properties for CORS settings. |
| [`IdempotencyProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/idempotency/config/IdempotencyProperties.java) | Configuration properties for the idempotency module. |
| [`ErrorHandlingProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/error/config/ErrorHandlingProperties.java) | Configuration properties for error handling. |
| [`PiiMaskingProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/PiiMaskingProperties.java) | Configuration properties for PII (Personally Identifiable Information) masking in logs. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Kernel](kernel.md) | Share a consistent exception vocabulary across application, domain, and infrastructure boundaries. |
| [Common Cache Library](cache.md) | Use a common cache API with a local Caffeine default and pluggable distributed providers. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-web -am verify
./mvnw -pl :fireflyframework-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [ConditionsReportAutoConfigurationTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/conditions/config/ConditionsReportAutoConfigurationTest.java).
- [ExceptionMetadataAspectTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/error/aspect/ExceptionMetadataAspectTest.java).
- [ExceptionSuggestionAspectTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/error/aspect/ExceptionSuggestionAspectTest.java).
- [ExceptionConverterServiceTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/error/converter/ExceptionConverterServiceTest.java).
- [ExternalServiceExceptionConverterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/error/converter/ExternalServiceExceptionConverterTest.java).
- [R2dbcExceptionConverterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/error/converter/R2dbcExceptionConverterTest.java).

## Test fixture

The `canHandle_WrappedWebClientResponseException_ReturnsTrue` test demonstrates one concrete behavior. Excerpt from [ExternalServiceExceptionConverterTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/test/java/org/fireflyframework/web/error/converter/ExternalServiceExceptionConverterTest.java). The test class supplies its fixtures and imports.

```java
@Test
void canHandle_WrappedWebClientResponseException_ReturnsTrue() {
    // Arrange — a downstream WebClientResponseException nested inside a wrapper (as the CQRS bus does)
    WebClientResponseException downstream = WebClientResponseException.create(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8
    );
    RuntimeException wrapped = new RuntimeException("Failed to process query", downstream);

    // Act
    boolean result = converter.canHandle(wrapped);

    // Assert
    assertTrue(result);
}
```

## Detailed reference

### Configuration

All properties live under the `firefly.web.*` namespace. The defaults below are production-safe; every subsystem can be toggled independently.

```yaml
firefly:
  web:
    # --- Global error handling (firefly.web.error-handling) ---
    error-handling:
      include-stack-trace: false       # keep false in production
      include-debug-info: false        # request params/headers in response
      include-exception-cause: false
      mask-sensitive-data: true        # uses PiiMaskingService when present
      enable-rfc7807: true             # RFC 7807 Problem Details format
      enable-metrics: true             # Micrometer error metrics
      enable-distributed-tracing: true # OpenTelemetry / Zipkin / Jaeger
      include-correlation-id: true
      include-request-id: true
      include-span-id: true
      include-suggestions: true
      include-documentation: true
      include-help-url: true
      documentation-base-url: ""       # error code appended to this URL
      help-base-url: ""
      support-email: ""
      support-phone: ""
      instance-id: ""                  # e.g. ${spring.application.name}:${random.uuid}
      max-message-length: 500
      max-details-length: 2000
      max-validation-errors: 100
      default-severity: MEDIUM
      default-category: UNKNOWN
      log-all-errors: true
      client-error-log-level: WARN     # log level for 4xx
      server-error-log-level: ERROR    # log level for 5xx
      enable-error-caching: false      # cache responses during error storms
      error-cache-ttl-seconds: 60
      error-cache-max-size: 1000

    # --- Idempotency (firefly.web.idempotency) ---
    idempotency:
      enabled: true                    # matchIfMissing=true (on by default)
      header-name: X-Idempotency-Key
      cache:
        ttl-hours: 24                  # TTL for cached idempotent responses

    # --- PII masking (firefly.web.pii-masking) ---
    pii-masking:
      enabled: true
      mask-character: "*"
      preserve-length: true
      show-characters: 3               # used when preserve-length=false
      mask-headers: true
      mask-bodies: true
      mask-query-params: true
      mask-exceptions: true
      auto-mask-logs: true
      enable-stdout-masking: true      # redact System.out.println output
      case-sensitive: false
      patterns: {}                     # override built-in patterns (60+ defaults)
      custom-patterns: {}              # add org-specific patterns

    # --- HTTP request logging (firefly.web.http-request-logging) ---
    http-request-logging:
      enabled: true
      log-level: INFO
      include-headers: true
      include-request-body: false
      include-response-body: false
      include-query-params: true
      max-body-size: 1024              # bytes
      sensitive-headers: [authorization, x-api-key, x-auth-token, cookie, set-cookie]
      excluded-paths: [/health, /actuator/**, /metrics, /info]

    # --- CORS (firefly.web.cors) — secure by default ---
    cors:
      enabled: true
      path-pattern: "/**"
      allowed-origins: []              # empty = no origins allowed (no CORS headers)
      allowed-methods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
      allowed-headers: ["*"]
      exposed-headers: [X-Transaction-Id]
      allow-credentials: false
      max-age: 3600                    # preflight cache seconds

    # --- Conditions evaluation report (firefly.web.conditions-report) ---
    conditions-report:
      enabled: false                   # disabled by default
      show-negative: true              # show conditions that did not match
      show-details: true
      summary-only: false
      use-colors: true
```

Key notes:

- Idempotency cache provider is configured through `fireflyframework-cache` (`firefly.cache.*`), not here. Idempotency only stores entries under the `:idempotency:` key prefix; the actual provider (Caffeine, Redis, …) and its sizing/TTL come from the cache module.
- CORS is secure by default: with an empty `allowed-origins`, no `Access-Control-Allow-Origin` header is emitted. Populate origins explicitly for browser clients.
- PII masking ships 60+ built-in regex patterns (emails, US/EU phone numbers, EU national identity numbers, SSN, credit cards, IP/MAC addresses, JWTs, API keys, sensitive URLs). Override any via `patterns` or extend with `custom-patterns`.
- The conditions report is off by default; enable it during development to diagnose which Firefly/Spring auto-configurations activated.

### OpenAPI Generation Infrastructure

This module provides reusable infrastructure for automated OpenAPI spec generation and SDK creation at build time. A microservice generates an OpenAPI spec from its controller annotations without loading any production dependencies.

| Component | Purpose |
|-----------|---------|
| `@EnableOpenApiGen` | Meta-annotation combining `@SpringBootConfiguration`, `@EnableWebFlux`, and the required Springdoc / WebFlux / Jackson auto-configurations into one annotation. |
| `AutoMockMissingBeansConfig` | A `BeanDefinitionRegistryPostProcessor` that auto-creates Mockito mocks for any `@Autowired` dependency missing from the context. Active only under the `openapi-gen` Spring profile. |
| `application-openapi-gen.yaml` | Profile config that enables Springdoc and allows bean-definition overriding; auto-discovered from the classpath. |

How it works:

1. A lightweight Spring Boot app (`OpenApiGenApplication`) starts during the Maven build using the test classpath.
2. `@EnableOpenApiGen` imports the minimal auto-configurations needed for Springdoc + WebFlux.
3. `AutoMockMissingBeansConfig` scans `@RestController` beans and registers Mockito mocks for their `@Autowired` dependencies, so controllers load without their real service implementations.
4. Springdoc reads the controller annotations and exposes the spec at `/v3/api-docs.yaml`.
5. The `springdoc-openapi-maven-plugin` fetches the spec and writes it to `target/openapi/openapi.yml`.
6. The SDK module uses `openapi-generator-maven-plugin` to generate typed API clients from the spec.

Usage in a microservice:

```java
// src/test/java — OpenApiGenApplication
@EnableOpenApiGen
@ComponentScan(basePackages = "com.example.module.web.controllers")
public class OpenApiGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApiGenApplication.class, args);
    }
}
```

```xml
<!-- the -web module's pom.xml -->
<properties>
    <openapi.gen.skip>false</openapi.gen.skip>
    <openapi.gen.mainClass>com.example.module.web.openapi.OpenApiGenApplication</openapi.gen.mainClass>
</properties>
```

The Maven profile and plugin configuration are inherited from `fireflyframework-parent`.

## Continue the application

Use the [Security Resource Server guide](security-resource-server.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/web/src/main/java).
- [additional-spring-configuration-metadata.json](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/resources/META-INF/spring/additional-spring-configuration-metadata.json).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-conditions-report-example.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/resources/application-conditions-report-example.yml).
- [application-openapi-gen.yaml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/resources/application-openapi-gen.yaml).
- [application.properties](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/resources/application.properties).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
