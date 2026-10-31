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
    <version>26.10.22</version>
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

### Opt-in bounded root transactions

The source tree provides `BoundedRootTransaction` and `impl.DefaultBoundedRootTransaction` for an application-owned R2DBC transaction with a finite operation budget. Use a source build containing these classes until the corresponding release is published. This API does not register beans or change the default transaction manager.

Build one `OwnedLeaseConnectionFactory` around the application connection factory, then construct the actual `DatabaseClient`, customized `R2dbcEntityTemplate`, Spring Data repositories, and `R2dbcTransactionManager` with that same wrapper. `BoundedR2dbcResources.verify` checks the factory/client/template/manager identities; preserve the host converter, callbacks, and repository configuration. The helper refuses an ambient transaction rather than joining another owner's work. It owns individual acquired leases and never closes the application pool.

Capture the monotonic anchor before obtaining an original observation. Pass its original observation time, absolute expiration, explicit maximum duration, clock, and the same monotonic supplier to `R2dbcOperationBudget.observed`. Execute with a `PROPAGATION_REQUIRED` transaction definition and return one result; other propagation modes are refused. Compose an already observed dependency limit with `original.intersect(inherited)` before passing the budget to `execute`, so connection acquisition and transaction begin use the earlier bound. Use `RootTransactionScope.narrow` when a later native observation or dependency imposes an earlier bound. Original and narrowed budgets must share the identical `Clock` object and monotonic supplier. Budget checks include acquisition, transaction completion, and final cleanup; an expired budget does not grant a fresh timeout. Configure rollback and close limits separately with `CleanupPolicy`.

`RootCompletion` exposes a value only after successful bounded completion. `RootTransactionException` exposes payload-free `TransactionFacts` and `CleanupFact`: commit acknowledgment and completed transaction boundary are separate observations. Timeout, cancellation, or cleanup failure can leave the business outcome uncertain. The application must recover through its authoritative action/receipt protocol. These technical facts establish no authentication, authorization, financial finality, or proof that no business effect occurred.

Run the module and its reactor dependencies with `./mvnw -B -ntp -pl :fireflyframework-r2dbc -am verify`. The transaction tests exercise actual Spring transaction infrastructure with controlled R2DBC connections; native PostgreSQL and application journeys require separate integration tests.

### Optional completed audit append read

`CompletedAuditAppendService` and `impl.SpringDataCompletedAuditAppendService` verify an append that has already completed inside its still-active original bounded owner root. Construct the service explicitly with the original `BoundedR2dbcResources`, the same `DatabaseClient`, customized `R2dbcEntityTemplate`, transaction manager, `Clock` and monotonic supplier. Its Spring Data repository is built from that retained template; there is no automatic bean, injected arbitrary repository or ambient-transaction fallback.

Call `requireCompleted(scope, contextNonce, provisionalAppend)` with the exact native context nonce supplied through the existing appender's call-local nonce supplier and its actual `ProvisionalAppend`. The provisional record does not expose or create a nonce. The new service requires exactly one typed native row, rejects NULL numeric projections and out-of-range raw nanos, narrows the original scope to the observed native window, recomputes the full canonical record through `AuditRows.Event.record()`, and compares the entire expected record. A mismatch, read error or late result marks that same root rollback-only, even if the caller catches the error. No separate read transaction is started.

The optional `modules/r2dbc/src/main/resources/db/optional/immutable-audit/completed-append.sql` extends an existing reviewed immutable-audit installation. Its read-only function reuses the original private `context_for` and `require_graph`, requires completion and exact nonce/event/sequence/hash/replay context, then loads the actual stored row. Private guard-owned helpers encode that row in the existing Kernel v1 format and reject a mismatched `record_hash` with SQLSTATE `23514` before the final original-context/deadline recheck. Caller parameters select and compare the row; a caller-supplied preimage, verification flag or digest is never attestation. The public signature and returned columns are unchanged.

The codec requires a UTF8 database. Text is exact length-prefixed UTF8 without normalization; UUIDs, sequence, epoch seconds, nanos and optional-value flags use their native binary representations. Previous/evidence hashes remain 64 ASCII hexadecimal characters in the preimage. Token, digest, enum, staff-pair, Java Instant/nanos and bounded actor-text rules follow the unchanged Kernel records. Nonbreaking spaces remain distinct from Java whitespace. PostgreSQL UTF8 rejects malformed scalar encodings and NUL before they can become stored text; the codec does not replace invalid Java UTF16 or infer missing values. PostgreSQL provides [built-in SHA-256 and UTF8 conversion](https://www.postgresql.org/docs/16/functions-binarystring.html), so this optional resource adds no extension.

The helpers have fixed `pg_catalog` search paths and no PUBLIC/writer/reader EXECUTE privilege. Installation leaves no consumer EXECUTE grant and does not reopen completed `scope_matches`, RLS or write authority. A separately reviewed deployment must grant only the public accessor to its explicit restricted consumer and prove the same physical session/full-XID/backend and native ACL behavior. Do not grant private-schema/table access or runtime schema CREATE.

This verifies the selected record's hash when the optional accessor is called; unchanged generic completion does not make every stored record canonically verified. It does not verify every predecessor, authenticate actor claims, prove issuer/native admission, establish external custody/WORM or regulatory compliance, or attest that Java code ran. Future owner pairing must call the protected native check, compare every owner-bound field against its protected admission/effect/anchor, and independently enforce any required pairing obligation. The Java service's own row rehash and full expected-record equality remain mandatory. Existing records, `ProvisionalAppend`, constructors, default SQL and default grants are unchanged.

The source corpus captures 28 actual unchanged Kernel hasher preimages and compares each with an uncaptured genuine JDK SHA-256 result. Pure source tests and lexical SQL controls are separate from PostgreSQL parsing/execution. `AuditCanonicalPostgresIT` is a dormant native fixture requiring a new exact Root-owned cluster/profile proof, matching data directory/system identifier/port/database and separately reviewed one-run authority. It checks byte/hash parity, actual stored-row tampering and malformed hashes, replay refusal and private ACL denial. Its explicit test-only public-accessor opt-in records initial absence and exact grant/revoke; no production/default grant is added. All prior 143 source controls remain active. Native/owner-reactor/artifact acceptance and release remain separate gates.

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

### Conditional database infrastructure

R2DBC utility types can be present transitively in a service that owns no database. Their presence does not require that service to create a connection factory, entity template, or transaction manager. In this source version, database integration activates only when its runtime prerequisites exist:

| Available infrastructure | Framework behavior |
| --- | --- |
| No R2DBC/transaction runtime classes | Database auto-configuration backs off before loading its dependent beans |
| Classes present, no entity template or connection factory | No database template initialization or transaction-manager creation |
| Application or Boot provides `R2dbcEntityTemplate` | Initialize `FilterUtils` after Boot's data R2DBC auto-configuration |
| One candidate `ConnectionFactory`, no existing reactive transaction manager | Provide `R2dbcTransactionManager` if Boot has not already supplied one |
| Existing `ReactiveTransactionManager` | Preserve the application's selected manager |
| Several connection factories without a primary candidate | Do not choose an arbitrary database; configure the intended transaction boundary explicitly |

This conditional behavior concerns framework infrastructure. It does not disable Spring Boot's own database auto-configuration selected by a data starter. A service that includes that starter must still configure its database or deliberately exclude the unused Boot data configuration. Database-free services should depend only on the facilities they actually use.

`FilterUtils` uses a process-wide template reference. Applications with multiple independently selected databases must define the supported filtering/database boundary explicitly; these conditions do not introduce multitenant database routing.

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
