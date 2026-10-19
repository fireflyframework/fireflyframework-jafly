![Jafly. R2DBC](../assets/modules/r2dbc.svg)

# R2DBC

Build reactive repository filtering and pagination on Spring Data R2DBC.

Configure the host R2DBC connection and use the library repository/filter contracts with your entity and filter DTO. Database migrations and entity schemas remain application responsibilities.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-r2dbc` | `jar` | [modules/r2dbc](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/r2dbc) |

Part of [Domain and data](index.md#domain-and-data).

## In the onboarding service

The tutorial saves applications in a process-local map. An application-owned R2DBC repository can replace that storage when submitted applications must survive a restart; configure the database and schema before changing the handlers.

## Add the component

[Installation and version availability](../installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-r2dbc</artifactId>
    <version>26.10.21-SNAPSHOT</version>
</dependency>
```

## Quick start

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

### 1. Configure the connection

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: app
    password: secret
  flyway:
    url: jdbc:postgresql://localhost:5432/mydb   # Flyway uses a JDBC URL for migrations
    user: app
    password: secret
```

### 2. Define an entity, a filter DTO, and a paginated endpoint

```java
@Table("products")
public record Product(@Id Long id, String name, String category, BigDecimal price) {}

// Filter DTO: field names match entity columns
@Data
public class ProductFilter {
    private String name;          // -> LIKE '%name%'
    private String category;      // -> LIKE '%category%'
    @FilterableId
    private Long supplierId;      // *Id is opt-in via @FilterableId -> exact match
}

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    @GetMapping
    public Mono<PaginationResponse<Product>> list(@ParameterObject FilterRequest<ProductFilter> request) {
        return FilterUtils
                .createFilter(Product.class, Function.identity())   // entity class + entity->DTO mapper
                .filter(request);                                   // returns Mono<PaginationResponse<Product>>
    }
}
```

A request such as:

```
GET /products?filters.category=tools&rangeFilters.ranges[price].from=10&rangeFilters.ranges[price].to=50&pagination.pageNumber=0&pagination.pageSize=20&pagination.sortBy=price&pagination.sortDirection=ASC
```

produces a `LIKE` filter on `category`, a `BETWEEN` filter on `price`, sorted/paged results, and a `PaginationResponse` with `content`, `totalElements`, `totalPages`, and `currentPage`. The same `filters.*`, `rangeFilters.*`, `pagination.*`, and `options.*` parameters appear automatically in the Swagger UI for this endpoint. No manual annotations required.

### 3. Case-insensitive and inherited-field options

```java
FilterUtils.FilterOptions options = FilterUtils.FilterOptions.builder()
        .caseInsensitiveStrings(true)     // LIKE on lower-cased values
        .includeInheritedFields(true)     // also reflect over superclass fields
        .build();

FilterUtils.createFilter(Product.class, Function.identity(), options).filter(request);
```

## Configuration and wiring

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

Property-based wiring conditions found in this module:

- `prefix = "firefly.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true`. [R2dbcObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/observability/R2dbcObservabilityAutoConfiguration.java).
- `prefix = "firefly.observability.health", name = "enabled", havingValue = "true", matchIfMissing = true`. [R2dbcObservabilityAutoConfiguration.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/observability/R2dbcObservabilityAutoConfiguration.java).

## Public API and extension points

| Type | Contract and responsibility |
| --- | --- |
| [`FilterUtils`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/filters/FilterUtils.java) | Defines `defaults`, `initializeTemplate`, `createFilter`, `GenericFilter` operations. |
| [`PaginationResponse`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/queries/PaginationResponse.java) | A generic class representing a paginated response, typically used for returning a portion of data along with pagination details in API responses. |
| [`R2dbcAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/config/R2dbcAutoConfiguration.java) | Defines `R2dbcAutoConfiguration`, `initialize` operations. |
| [`R2dbcTransactionAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/config/R2dbcTransactionAutoConfiguration.java) | Configuration class for managing R2DBC transactions in a reactive application. |
| [`SwaggerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/config/SwaggerAutoConfiguration.java) | Defines `filterParameterCustomizer` operations. |

## Related components

| Related component | Why it belongs nearby |
| --- | --- |
| [Utils](utils.md) | Render FreeMarker templates and convert rendered content to document formats; mark filter DTO fields. |
| [Observability](observability.md) | Provide a common metrics, tracing, health, and context-propagation foundation for framework components. |

## Build and test

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-r2dbc -am verify
./mvnw -pl :fireflyframework-r2dbc -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Representative test sources:

- [FilterParameterCustomizerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/test/java/org/fireflyframework/core/filters/FilterParameterCustomizerTest.java).
- [FilterUtilsTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/test/java/org/fireflyframework/core/filters/FilterUtilsTest.java).

## Test fixture

The `shouldAddFilterParametersWhenMethodHasFilterRequestWithParameterObject` test demonstrates one concrete behavior. Excerpt from [FilterParameterCustomizerTest.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/test/java/org/fireflyframework/core/filters/FilterParameterCustomizerTest.java). The test class supplies its fixtures and imports.

```java
@Test
void shouldAddFilterParametersWhenMethodHasFilterRequestWithParameterObject() throws Exception {
    // Given
    Method method = TestController.class.getMethod("filterWithParameterObject", FilterRequest.class);
    MethodParameter methodParameter = new MethodParameter(method, 0);
    methodParameter.initParameterNameDiscovery(null);
    when(handlerMethod.getMethodParameters()).thenReturn(new MethodParameter[]{methodParameter});
    Operation operation = new Operation();

    // When
    Operation customizedOperation = customizer.customize(operation, handlerMethod);

    // Then
    assertThat(customizedOperation.getParameters()).isNotNull();
    verifyParameters(customizedOperation.getParameters());
}
```

## Detailed reference

### Configuration

This module relies on the standard Spring `spring.r2dbc.*` and `spring.flyway.*` properties for connectivity and migrations. See the Spring Boot reference for the full set. The only Firefly-specific keys are the observability switches it honors (defined in `fireflyframework-observability` and respected by this module's auto-configuration):

```yaml
firefly:
  observability:
    metrics:
      enabled: true   # default true — register R2dbcMetrics when a MeterRegistry is present
    health:
      enabled: true   # default true — register the R2DBC health indicator
```

| Property | Default | Description |
| --- | --- | --- |
| `firefly.observability.metrics.enabled` | `true` | Registers `R2dbcMetrics` (the `firefly.r2dbc.*` timers/counters) when a Micrometer `MeterRegistry` bean exists. Set `false` to disable. |
| `firefly.observability.health.enabled` | `true` | Registers the R2DBC `HealthIndicator` that probes the database with `SELECT 1`. Set `false` to disable. |

Both beans use `matchIfMissing = true`, so they are active by default and back off cleanly if the host service disables observability or the underlying classes/beans (`MeterRegistry`, `ConnectionFactory`) are absent. The `ReactiveTransactionManager` and the springdoc `FilterParameterCustomizer` are both `@ConditionalOnMissingBean`, so you can override either by declaring your own bean.

This module ships an `application-logging.yml` profile fragment that sets `org.springframework.data.r2dbc` and `io.r2dbc` logging to `INFO`.

### How It Works

The module registers four Spring Boot auto-configurations (see `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`):

| Auto-configuration | Responsibility |
| --- | --- |
| `R2dbcAutoConfiguration` | On startup, hands the application's `R2dbcEntityTemplate` to `FilterUtils.initializeTemplate(...)` so the static filtering engine can run queries. |
| `R2dbcTransactionAutoConfiguration` | Enables `@EnableTransactionManagement` and provides an `R2dbcTransactionManager`-backed `ReactiveTransactionManager`. |
| `SwaggerAutoConfiguration` | Registers the `FilterParameterCustomizer` springdoc `OperationCustomizer`. |
| `R2dbcObservabilityAutoConfiguration` | Wires `R2dbcMetrics` and the R2DBC `HealthIndicator`, gated by `firefly.observability.*`. |

Filtering follows a fixed precedence per field: `NULL` / `NOT NULL` markers first, then ID handling (skip or exact match), then collections/arrays (`IN`), then non-empty strings (`LIKE`), then enums (exact, by `name()`), then exact match for everything else. Reflected fields are cached per class for performance.

### Observability

When a Micrometer `MeterRegistry` is present, `R2dbcMetrics` exposes wrappers you can use to instrument repository methods:

- `firefly.r2dbc.query.duration`. query latency, tagged by `operation` (e.g. select/insert/update/delete)
- `firefly.r2dbc.queries`. query counts, tagged by `operation` and `status`
- `firefly.r2dbc.errors`. failures, tagged by `error.type`

```java
@RequiredArgsConstructor
public class ProductRepository {
    private final R2dbcMetrics metrics;          // auto-configured bean

    public Mono<Product> findById(Long id) {
        return metrics.timedQuery("select", delegate.findById(id));
    }
}
```

The health indicator contributes a `r2dbc` entry to `/actuator/health` reporting the database name, probe result, and latency, flipping to `DEGRADED` above a 500 ms threshold and `DOWN` on timeout/failure.

## Continue the application

Use the [Event Sourcing guide](eventsourcing.md) for the next part of this integration. The runnable submission/query service is in [Getting started](../getting-started.md).

## Source and further reading

- [Maven project and dependency declarations](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/pom.xml).
- [Production source](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/r2dbc/src/main/java).
- [org.springframework.boot.autoconfigure.AutoConfiguration.imports](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [application-logging.yml](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/resources/application-logging.yml).
- [Module catalog](index.md) and [getting started](../getting-started.md).
- [Maven platform](../maven.md) and [architecture](../architecture.md).
