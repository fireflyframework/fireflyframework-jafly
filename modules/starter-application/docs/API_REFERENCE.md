# 📑 API Reference

> **Complete API documentation for lib-common-application**

Comprehensive reference for all classes, methods, annotations, and configurations in the Firefly Application Layer library.

---

## 📖 Table of Contents

### 📦 Core Models
1. [📦 Context Models](#context-models)
   - ApplicationExecutionContext
   - AppMetadata
   - AppContext
   - AppConfig
   - AppSecurityContext

### 🏷️ Annotations
2. [🏷️ Annotations](#annotations)
   - @FireflyApplication
   - @Secure
   - @RequireContext

### 🔍 Resolvers
3. [🔍 Resolvers](#resolvers)
   - AbstractContextResolver
   - AbstractConfigResolver
   - ContextResolver (interface)
   - ConfigResolver (interface)

### ⚙️ Services & Components
4. [⚙️ Services](#services)
   - AbstractApplicationService
   - AbstractSecurityAuthorizationService
   - AbstractSecurityConfiguration

### 🔒 Security
5. [🔒 Security Components](#security-components)
   - EndpointSecurityRegistry
   - SecurityAspect
   - AbstractContractController
   - AbstractProductController

### ⚙️ Configuration
6. [⚙️ Configuration](#configuration)
   - ApplicationLayerProperties
   - Configuration Properties Reference

---

## Context Models

### ApplicationExecutionContext

The main context object that aggregates all contextual information for a request.

```java
public class ApplicationExecutionContext {
    AppMetadata metadata;
    AppContext context;
    AppConfig config;
    AppSecurityContext securityContext;
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getTenantId()` | `UUID` | Gets the tenant ID from config |
| `getPartyId()` | `UUID` | Gets the party ID from context |
| `getContractId()` | `UUID` | Gets the contract ID from context (may be null) |
| `getProductId()` | `UUID` | Gets the product ID from context (may be null) |
| `getRequestId()` | `UUID` | Gets the request ID from metadata |
| `getCorrelationId()` | `UUID` | Gets the correlation ID from metadata |
| `isAuthorized()` | `boolean` | Checks if security context is authorized |
| `hasRole(String role)` | `boolean` | Checks if context has specific role |
| `isFeatureEnabled(String feature)` | `boolean` | Checks if feature flag is enabled |

#### Static Factory Methods

```java
// Create minimal context with only required fields
ApplicationExecutionContext.createMinimal(UUID partyId, UUID tenantId)
```

#### Builder Example

```java
ApplicationExecutionContext context = ApplicationExecutionContext.builder()
    .metadata(appMetadata)
    .context(appContext)
    .config(appConfig)
    .securityContext(securityContext)
    .build();
```

---

### AppMetadata

Technical metadata about the request.

```java
public class AppMetadata {
    UUID requestId;
    UUID correlationId;
    Instant timestamp;
    String sourceSystem;
    String clientIp;
    String userAgent;
    String apiVersion;
    Map<String, String> customHeaders;
    String region;
    String locale;
    String traceId;
    String spanId;
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `hasTracing()` | `boolean` | Checks if tracing information is present |
| `getCustomHeader(String key)` | `String` | Gets a custom header value |

#### Static Factory Methods

```java
// Create with default values (auto-generated IDs, current timestamp)
AppMetadata.createDefault()
```

---

### AppContext

Business context containing party, contract, and product information.

```java
public class AppContext {
    @NotNull UUID partyId;
    UUID contractId;
    UUID productId;
    Set<String> roles;
    Set<String> permissions;
    UUID tenantId;
    Map<String, Object> attributes;
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `hasRole(String role)` | `boolean` | Checks if specific role is present |
| `hasAnyRole(String... roles)` | `boolean` | Checks if any of the specified roles are present |
| `hasAllRoles(String... roles)` | `boolean` | Checks if all specified roles are present |
| `hasPermission(String permission)` | `boolean` | Checks if specific permission is granted |
| `hasContract()` | `boolean` | Checks if contract ID is present |
| `hasProduct()` | `boolean` | Checks if product ID is present |
| `getAttribute(String key)` | `<T> T` | Gets an attribute from context |

---

### AppConfig

Tenant configuration including provider settings and feature flags.

```java
public class AppConfig {
    @NotNull UUID tenantId;
    String tenantName;
    Map<String, ProviderConfig> providers;
    Map<String, Boolean> featureFlags;
    Map<String, String> settings;
    String environment;
    @Builder.Default boolean active = true;
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getProvider(String providerType)` | `Optional<ProviderConfig>` | Gets provider configuration by type |
| `hasProvider(String providerType)` | `boolean` | Checks if provider is configured |
| `isFeatureEnabled(String feature)` | `boolean` | Checks if feature flag is enabled |
| `getSetting(String key)` | `String` | Gets a setting value |
| `getSetting(String key, String defaultValue)` | `String` | Gets a setting with default |

#### Nested Class: ProviderConfig

```java
public static class ProviderConfig {
    @NotNull String providerType;
    String implementation;
    Map<String, Object> properties;
    @Builder.Default boolean enabled = true;
    @Builder.Default int priority = 0;
}
```

**ProviderConfig Methods:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getProperty(String key)` | `<T> T` | Gets a property value |
| `getProperty(String key, T defaultValue)` | `<T> T` | Gets property with default |
| `hasProperty(String key)` | `boolean` | Checks if property exists |

---

### AppSecurityContext

Security context with endpoint requirements and authorization results.

```java
public class AppSecurityContext {
    String endpoint;
    String httpMethod;
    Set<String> requiredRoles;
    Set<String> requiredPermissions;
    boolean authorized;
    String authorizationFailureReason;
    SecurityConfigSource configSource;
    Map<String, Object> securityAttributes;
    @Builder.Default boolean requiresAuthentication = true;
    @Builder.Default boolean allowAnonymous = false;
    SecurityEvaluationResult evaluationResult;
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `hasRequiredRoles()` | `boolean` | Checks if roles are required |
| `hasRequiredPermissions()` | `boolean` | Checks if permissions are required |
| `requiresRole(String role)` | `boolean` | Checks if specific role is required |
| `requiresPermission(String permission)` | `boolean` | Checks if specific permission is required |
| `getSecurityAttribute(String key)` | `<T> T` | Gets a security attribute |

#### Enums

**SecurityConfigSource:**
- `ANNOTATION` - From @Secure annotation
- `EXPLICIT_MAP` - From EndpointSecurityRegistry
- `SECURITY_CENTER` - From SecurityCenter
- `DEFAULT` - From default rules

---

## Annotations

### @Secure

Declarative security annotation for endpoints and controllers.

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Secure {
    String[] roles() default {};
    String[] permissions() default {};
    boolean requireAllRoles() default false;
    boolean requireAllPermissions() default false;
    boolean allowAnonymous() default false;
    boolean requiresAuthentication() default true;
    String expression() default "";
    boolean useSecurityCenter() default true;
    String[] attributes() default {};
    String description() default "";
}
```

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `roles` | `String[]` | `{}` | Required roles (OR logic by default) |
| `permissions` | `String[]` | `{}` | Required permissions (OR logic by default) |
| `requireAllRoles` | `boolean` | `false` | Use AND logic for roles |
| `requireAllPermissions` | `boolean` | `false` | Use AND logic for permissions |
| `allowAnonymous` | `boolean` | `false` | Allow anonymous access |
| `requiresAuthentication` | `boolean` | `true` | Authentication required |
| `expression` | `String` | `""` | Custom SpEL expression |
| `useSecurityCenter` | `boolean` | `true` | Delegate to SecurityCenter |
| `attributes` | `String[]` | `{}` | Custom attributes as key=value pairs |
| `description` | `String` | `""` | Documentation description |

#### Usage Examples

**Class-level security (applies to all methods):**
```java
@RestController
@Secure(roles = {"USER", "ADMIN"})
public class MyController {
    // All methods require USER or ADMIN role
}
```

**Method-level security (overrides class-level):**
```java
@PostMapping("/transfer")
@Secure(
    roles = "ACCOUNT_OWNER",
    permissions = "TRANSFER_FUNDS",
    description = "Transfer funds between accounts"
)
public Mono<Transfer> transfer(@RequestBody TransferRequest request) {
    // Requires ACCOUNT_OWNER role AND TRANSFER_FUNDS permission
}
```

**Anonymous access:**
```java
@GetMapping("/public")
@Secure(allowAnonymous = true)
public Mono<Data> publicEndpoint() {
    // No authentication required
}
```

**Complex authorization with SpEL:**
```java
@PutMapping("/{id}")
@Secure(expression = "context.hasRole('ADMIN') or context.partyId == #id")
public Mono<User> updateUser(@PathVariable UUID id) {
    // ADMIN or owner of the resource
}
```

---

### @RequireContext

Annotation to specify required context components.

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireContext {
    boolean contract() default false;
    boolean product() default false;
    boolean tenantConfig() default true;
    String[] requiredProviders() default {};
    boolean failFast() default true;
}
```

#### Attributes

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `contract` | `boolean` | `false` | Contract ID required |
| `product` | `boolean` | `false` | Product ID required |
| `tenantConfig` | `boolean` | `true` | Tenant config must be loaded |
| `requiredProviders` | `String[]` | `{}` | Required provider types |
| `failFast` | `boolean` | `true` | Fail immediately if not met |

#### Usage Example

```java
@PostMapping("/transfer")
@RequireContext(contract = true, product = true, requiredProviders = {"PAYMENT_GATEWAY"})
public Mono<Transfer> transfer(@RequestBody TransferRequest request) {
    // Validates context before method execution
}
```

---

## Resolvers

### ContextResolver

Interface for resolving application context from requests.

```java
public interface ContextResolver {
    Mono<AppContext> resolveContext(ServerWebExchange exchange, AppMetadata metadata);
    Mono<UUID> resolvePartyId(ServerWebExchange exchange);
    Mono<UUID> resolveContractId(ServerWebExchange exchange);
    Mono<UUID> resolveProductId(ServerWebExchange exchange);
    Mono<UUID> resolveTenantId(ServerWebExchange exchange);
    default boolean supports(ServerWebExchange exchange);
    default int getPriority();
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `resolveContext(exchange, metadata)` | `Mono<AppContext>` | Resolves complete context |
| `resolvePartyId(exchange)` | `Mono<UUID>` | Resolves party ID |
| `resolveContractId(exchange)` | `Mono<UUID>` | Resolves contract ID (optional) |
| `resolveProductId(exchange)` | `Mono<UUID>` | Resolves product ID (optional) |
| `resolveTenantId(exchange)` | `Mono<UUID>` | Resolves tenant ID |
| `supports(exchange)` | `boolean` | Checks if resolver handles request |
| `getPriority()` | `int` | Returns resolver priority (higher first) |

---

### AbstractContextResolver

Abstract base implementation with common functionality.

```java
public abstract class AbstractContextResolver implements ContextResolver
```

#### Protected Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `enrichContext(basicContext, exchange, metadata)` | `Mono<AppContext>` | Enriches context with roles/permissions |
| `resolveRoles(context, exchange)` | `Mono<Set<String>>` | Resolves party roles (override for SDK integration) |
| `resolvePermissions(context, exchange)` | `Mono<Set<String>>` | Resolves permissions (override for SDK integration) |
| `extractUUID(exchange, attrName, headerName)` | `Mono<UUID>` | Extracts UUID from attribute or header |
| `extractUUIDFromPath(exchange, varName)` | `Mono<UUID>` | Extracts UUID from path variable |

#### Implementation Example

```java
@Component
public class MyContextResolver extends AbstractContextResolver {
    
    @Override
    public Mono<UUID> resolvePartyId(ServerWebExchange exchange) {
        return extractFromJWT(exchange, "sub").map(UUID::fromString);
    }
    
    @Override
    public Mono<UUID> resolveTenantId(ServerWebExchange exchange) {
        return extractFromJWT(exchange, "tenantId").map(UUID::fromString);
    }
    
    // ... implement other methods
}
```

---

### ConfigResolver

Interface for resolving tenant configuration.

```java
public interface ConfigResolver {
    Mono<AppConfig> resolveConfig(UUID tenantId);
    Mono<AppConfig> refreshConfig(UUID tenantId);
    boolean isCached(UUID tenantId);
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `resolveConfig(tenantId)` | `Mono<AppConfig>` | Resolves tenant configuration (uses cache) |
| `refreshConfig(tenantId)` | `Mono<AppConfig>` | Forces refresh of cached config |
| `isCached(tenantId)` | `boolean` | Checks if config is in cache |

---

### AbstractConfigResolver

Abstract base with caching support.

```java
public abstract class AbstractConfigResolver implements ConfigResolver
```

#### Protected Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `fetchConfigFromPlatform(tenantId)` | `Mono<AppConfig>` | Fetches config from platform (override for SDK integration) |
| `clearCache()` | `void` | Clears entire config cache |
| `clearCacheForTenant(tenantId)` | `void` | Clears cache for specific tenant |

---

## Services

### SecurityAuthorizationService

Interface for authorization decisions.

```java
public interface SecurityAuthorizationService {
    Mono<AppSecurityContext> authorize(AppContext context, AppSecurityContext securityContext);
    Mono<Boolean> hasRole(AppContext context, String role);
    Mono<Boolean> hasPermission(AppContext context, String permission);
    Mono<Boolean> evaluateExpression(AppContext context, String expression);
}
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `authorize(context, securityContext)` | `Mono<AppSecurityContext>` | Performs authorization check |
| `hasRole(context, role)` | `Mono<Boolean>` | Checks if party has role |
| `hasPermission(context, permission)` | `Mono<Boolean>` | Checks if party has permission |
| `evaluateExpression(context, expression)` | `Mono<Boolean>` | Evaluates SpEL expression |

---

### AbstractSecurityAuthorizationService

Abstract base with default authorization logic.

```java
public abstract class AbstractSecurityAuthorizationService 
    implements SecurityAuthorizationService
```

#### Protected Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `checkRoles(context, securityContext)` | `Mono<Boolean>` | Checks required roles |
| `checkPermissions(context, securityContext)` | `Mono<AppSecurityContext>` | Checks required permissions |
| `authorizeWithSecurityCenter(context, securityContext)` | `Mono<AppSecurityContext>` | Delegates to SecurityCenter (override for integration) |
| `createAuthorizedContext(original)` | `AppSecurityContext` | Creates authorized context |
| `createUnauthorizedContext(original, reason)` | `AppSecurityContext` | Creates unauthorized context |

---

### AbstractApplicationService

Abstract base for application services.

```java
public abstract class AbstractApplicationService {
    protected final ContextResolver contextResolver;
    protected final ConfigResolver configResolver;
    protected final SecurityAuthorizationService authorizationService;
}
```

#### Protected Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `resolveExecutionContext(exchange)` | `Mono<ApplicationExecutionContext>` | Resolves complete execution context |
| `validateContext(context, requireContract, requireProduct)` | `Mono<ApplicationExecutionContext>` | Validates context components |
| `requireRole(context, role)` | `Mono<Void>` | Enforces role requirement |
| `requirePermission(context, permission)` | `Mono<Void>` | Enforces permission requirement |
| `getProviderConfig(context, providerType)` | `Mono<ProviderConfig>` | Gets provider configuration |
| `isFeatureEnabled(context, feature)` | `Mono<Boolean>` | Checks feature flag |
| `extractMetadata(exchange)` | `AppMetadata` | Extracts metadata from request |

#### Constructor

```java
protected AbstractApplicationService(
    ContextResolver contextResolver,
    ConfigResolver configResolver,
    SecurityAuthorizationService authorizationService
)
```

---

### EndpointSecurityRegistry

Registry for programmatic endpoint security configuration.

```java
public class EndpointSecurityRegistry
```

#### Methods

| Method | Return Type | Description |
|--------|-------------|-------------|
| `registerEndpoint(endpoint, httpMethod, security)` | `void` | Registers security for endpoint |
| `getEndpointSecurity(endpoint, httpMethod)` | `Optional<EndpointSecurity>` | Gets security configuration |
| `isRegistered(endpoint, httpMethod)` | `boolean` | Checks if endpoint is registered |
| `unregisterEndpoint(endpoint, httpMethod)` | `void` | Removes endpoint registration |
| `clear()` | `void` | Clears all registrations |
| `getAllEndpoints()` | `Map<String, EndpointSecurity>` | Gets all registered endpoints |

#### Nested Class: EndpointSecurity

```java
public static class EndpointSecurity {
    Set<String> roles;
    Set<String> permissions;
    boolean requireAllRoles;
    boolean requireAllPermissions;
    boolean allowAnonymous;
    boolean requiresAuthentication;
    String expression;
    Map<String, String> attributes;
}
```

**Builder Methods:**
- `builder()` - Creates new builder
- `roles(Set<String>)` - Sets required roles
- `permissions(Set<String>)` - Sets required permissions
- `requireAllRoles(boolean)` - Sets AND/OR logic for roles
- `requireAllPermissions(boolean)` - Sets AND/OR logic for permissions
- `allowAnonymous(boolean)` - Sets anonymous access
- `build()` - Builds EndpointSecurity

---

## Configuration

### ApplicationLayerProperties

Configuration properties class.

```yaml
firefly:
  application:
    security:
      enabled: true
      use-security-center: true
      default-roles: []
      fail-on-missing: false
    context:
      cache-enabled: true
      cache-ttl: 300
      cache-max-size: 1000
    config:
      cache-enabled: true
      cache-ttl: 600
      refresh-on-startup: false
```

#### Property Groups

**Security:**
- `enabled` (boolean): Enable security features
- `use-security-center` (boolean): Delegate to SecurityCenter
- `default-roles` (String[]): Default roles when none configured
- `fail-on-missing` (boolean): Fail on missing security config

**Context:**
- `cache-enabled` (boolean): Enable context caching
- `cache-ttl` (int): Cache TTL in seconds
- `cache-max-size` (int): Maximum cache size

**Config:**
- `cache-enabled` (boolean): Enable config caching
- `cache-ttl` (int): Cache TTL in seconds
- `refresh-on-startup` (boolean): Refresh config on application startup

---

## Error Handling

### Common Exceptions

| Exception | When Thrown | HTTP Status |
|-----------|-------------|-------------|
| `AccessDeniedException` | Authorization fails | 403 Forbidden |
| `IllegalStateException` | Required context missing | 400 Bad Request |
| `AuthenticationException` | Authentication fails | 401 Unauthorized |

### Exception Handling Example

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("Access denied: " + ex.getMessage()));
}
```

---

## Reactive Patterns

### Error Handling

```java
.onErrorResume(Exception.class, e -> {
    log.error("Operation failed", e);
    return Mono.error(new ApplicationException("Operation failed", e));
})
```

### Timeout

```java
.timeout(Duration.ofSeconds(5))
.onErrorReturn(TimeoutException.class, defaultValue)
```

### Retry

```java
.retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
```

### Circuit Breaker

```java
@CircuitBreaker(name = "customer-mgmt", fallbackMethod = "fallback")
protected Mono<Set<String>> resolveRoles(AppContext context) {
    return customerManagementClient.getPartyRoles(...);
}
```

---

## See Also

- [ARCHITECTURE.md](ARCHITECTURE.md) - Architectural patterns and decisions
- [USAGE_GUIDE.md](USAGE_GUIDE.md) - Step-by-step usage instructions
- [EXAMPLES.md](EXAMPLES.md) - Code examples and patterns
