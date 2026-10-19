![Jafly. Web](../../docs/site/assets/modules/web.svg)

# Web

Handle WebFlux errors, request metadata, idempotency, CORS, and sensitive-data masking consistently.

Add this library to a reactive web application and tune the individual firefly.web settings. Review allowed origins, body logging, and masking for the application data you actually expose; the source-backed property tables show the current defaults.

[Module guide](../../docs/site/modules/web.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

The onboarding HTTP boundary needs consistent error responses and carefully chosen CORS/logging settings. Decide which applicant fields may appear in logs before enabling request bodies or headers.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-web</artifactId>
    <version>26.10.20</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

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

## Configuration

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

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`GlobalExceptionHandler`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/error/handler/GlobalExceptionHandler.java) | Enhanced global exception handler for enrichment workflows. |
| [`CorsProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/cors/config/CorsProperties.java) | Configuration properties for CORS settings. |
| [`IdempotencyProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/idempotency/config/IdempotencyProperties.java) | Configuration properties for the idempotency module. |
| [`ErrorHandlingProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/error/config/ErrorHandlingProperties.java) | Configuration properties for error handling. |
| [`PiiMaskingProperties`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/web/src/main/java/org/fireflyframework/web/logging/config/PiiMaskingProperties.java) | Configuration properties for PII (Personally Identifiable Information) masking in logs. |

## Continue the application

Use the [Security Resource Server guide](../../docs/site/modules/security-resource-server.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-web -am verify
./mvnw -pl :fireflyframework-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/web.md).
