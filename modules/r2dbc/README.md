![Jafly. R2DBC](../../docs/site/assets/modules/r2dbc.svg)

# R2DBC

Build reactive repository filtering and pagination on Spring Data R2DBC.

Configure the host R2DBC connection and use the library repository/filter contracts with your entity and filter DTO. Database migrations and entity schemas remain application responsibilities.

[Module guide](../../docs/site/modules/r2dbc.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

The tutorial saves applications in a process-local map. An application-owned R2DBC repository can replace that storage when submitted applications must survive a restart; configure the database and schema before changing the handlers.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

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

### Optional UTC auditing

For `LocalDateTime`, `OffsetDateTime`, and `Instant` audit fields, expose `new org.fireflyframework.core.auditing.UtcOffsetDateTimeProviderImpl(Clock.systemUTC())` as a `DateTimeProvider` bean and select its name with `@EnableR2dbcAuditing(dateTimeProviderRef = "yourBeanName")`. The injected clock is normalized to UTC. Spring Data retains create/update, immutable entity, and auditing flag behavior. This helper is opt-in; it does not enable auditing or register a bean automatically. Legacy `Date` and `Long` audit fields are unsupported by this provider.

### Opt-in bounded root transactions

The source tree provides `BoundedRootTransaction` and `impl.DefaultBoundedRootTransactionImpl` for an application-owned R2DBC transaction with a finite operation budget. Use a source build containing these classes until the corresponding release is published. This API does not register beans or change the default transaction manager.

Build one `OwnedLeaseConnectionFactoryImpl` around the application connection factory, then construct the actual `DatabaseClient`, customized `R2dbcEntityTemplate`, Spring Data repositories, and `R2dbcTransactionManager` with that same wrapper. `BoundedR2dbcResources.verify` checks the factory/client/template/manager identities; preserve the host converter, callbacks, and repository configuration. The helper refuses an ambient transaction rather than joining another owner's work. It owns individual acquired leases and never closes the application pool.

Capture the monotonic anchor before obtaining an original observation. Pass its original observation time, absolute expiration, explicit maximum duration, clock, and the same monotonic supplier to `R2dbcOperationBudget.observed`. Execute with a `PROPAGATION_REQUIRED` transaction definition and return one result; other propagation modes are refused. Compose an already observed dependency limit with `original.intersect(inherited)` before passing the budget to `execute`, so connection acquisition and transaction begin use the earlier bound. Use `RootTransactionScope.narrow` when a later native observation or dependency imposes an earlier bound. Original and narrowed budgets must share the identical `Clock` object and monotonic supplier. Budget checks include acquisition, transaction completion, and final cleanup; an expired budget does not grant a fresh timeout. Configure rollback and close limits separately with `CleanupPolicy`.

`RootCompletion` exposes a value only after successful bounded completion. `RootTransactionException` exposes payload-free `TransactionFacts` and `CleanupFact`: commit acknowledgment and completed transaction boundary are separate observations. Timeout, cancellation, or cleanup failure can leave the business outcome uncertain. The application must recover through its authoritative action/receipt protocol. These technical facts establish no authentication, authorization, financial finality, or proof that no business effect occurred.

Run the module and its reactor dependencies with `./mvnw -B -ntp -pl :fireflyframework-r2dbc -am verify`. The transaction tests exercise actual Spring transaction infrastructure with controlled R2DBC connections; native PostgreSQL and application journeys require separate integration tests.

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

## Configuration

This module declares no `@ConfigurationProperties` class of its own. Use the constructor/port contracts above and the configuration of the host or related core module.

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`FilterUtils`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/filters/FilterUtils.java) | Defines `defaults`, `initializeTemplate`, `createFilter`, `GenericFilter` operations. |
| [`PaginationResponse`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/queries/PaginationResponse.java) | A generic class representing a paginated response, typically used for returning a portion of data along with pagination details in API responses. |
| [`R2dbcAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/config/R2dbcAutoConfiguration.java) | Defines `R2dbcAutoConfiguration`, `initialize` operations. |
| [`R2dbcTransactionAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/config/R2dbcTransactionAutoConfiguration.java) | Configuration class for managing R2DBC transactions in a reactive application. |
| [`SwaggerAutoConfiguration`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/r2dbc/src/main/java/org/fireflyframework/core/config/SwaggerAutoConfiguration.java) | Defines `filterParameterCustomizer` operations. |

## Continue the application

Use the [Event Sourcing guide](../../docs/site/modules/eventsourcing.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-r2dbc -am verify
./mvnw -pl :fireflyframework-r2dbc -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/r2dbc.md).
