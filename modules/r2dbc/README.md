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
    <version>26.10.17-SNAPSHOT</version>
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

Use the [Event Sourcing guide](../../docs/site/modules/eventsourcing.md) for the next part of this integration. ## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-r2dbc -am verify
./mvnw -pl :fireflyframework-r2dbc -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/r2dbc.md).
