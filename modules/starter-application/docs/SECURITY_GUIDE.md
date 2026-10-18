# Security Guide - Application Layer

## Overview

This guide explains how security and authorization work in Firefly Application Layer microservices, including:

1. How `contractId` and `productId` are **always** extracted from URL paths
2. How SecurityCenter integration works via `FireflySessionManager`
3. How to use `@Secure` annotations
4. Complete authorization flow from request to response

---

## Table of Contents

1. [Security Architecture](#1-security-architecture)
2. [Path-Based Context Resolution](#2-path-based-context-resolution)
3. [SecurityCenter Integration](#3-securitycenter-integration)
4. [Security Configuration Approaches](#4-security-configuration-approaches)
   - 4.1. [Declarative Security: @Secure Annotation](#41-declarative-security-secure-annotation)
   - 4.2. [Explicit Security: EndpointSecurityRegistry](#42-explicit-security-endpointsecurityregistry)
   - 4.3. [Configuration Priority Flow](#43-configuration-priority-flow)
5. [Complete Authorization Flow](#5-complete-authorization-flow)
6. [Base Controllers for Path Structure](#6-base-controllers-for-path-structure)
   - 6.1. [AbstractContractController](#61-abstractcontractcontroller)
   - 6.2. [AbstractProductController](#62-abstractproductcontroller)
7. [Examples](#7-examples)

---

## 1. Security Architecture

### The Security Stack

```
┌─────────────────────────────────────────────────────────┐
│  1. @Secure Annotation on Controller Method             │
│     @Secure(roles="ACCOUNT_HOLDER", permissions="TRANSFER")
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  2. SecurityAspect (AOP Interceptor)                    │
│     - Intercepts method call                            │
│     - Extracts security requirements                    │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  3. Context Resolution                                  │
│     - partyId: From JWT token                           │
│     - contractId: From path (/contracts/{contractId})   │
│     - productId: From path (/products/{productId})      │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  4. SecurityCenter via FireflySessionManager            │
│     sessionManager.authorize(                           │
│         partyId,                                        │
│         contractId,                                     │
│         productId,                                      │
│         requiredRoles,                                  │
│         requiredPermissions                             │
│     )                                                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  5. Authorization Decision                              │
│     ✅ GRANTED → Proceed to business logic              │
│     ❌ DENIED  → Return 403 Forbidden                   │
└─────────────────────────────────────────────────────────┘
```

### Key Principles

1. **Authentication**: JWT token validates WHO the user is (partyId)
2. **Context**: Path parameters specify WHAT they want to access (contractId, productId)
3. **Authorization**: SecurityCenter validates IF they can do it (roles, permissions)

---

## 2. Path-Based Context Resolution

### The Golden Rule

> **contractId and productId MUST ALWAYS come from URL path parameters**

This is a fundamental architectural decision that ensures:
- **Explicit intent**: Every request clearly states which contract/product it targets
- **RESTful design**: Resources are properly identified in URLs
- **Security clarity**: Authorization knows exactly what's being accessed
- **Audit trail**: Logs show which resources were accessed

### URL Structure Standard

All Application Layer endpoints MUST follow this pattern:

```
/api/v1/contracts/{contractId}/...
/api/v1/contracts/{contractId}/products/{productId}/...
```

### Examples of Correct URL Patterns

```
✅ GET    /api/v1/contracts/{contractId}/balance
✅ POST   /api/v1/contracts/{contractId}/accounts/{accountId}/transfer
✅ GET    /api/v1/contracts/{contractId}/products/{productId}/details
✅ PUT    /api/v1/contracts/{contractId}/products/{productId}/beneficiaries
✅ DELETE /api/v1/contracts/{contractId}/products/{productId}/cards/{cardId}
```

### ❌ Incorrect Patterns (Never Do This)

```
❌ /api/v1/accounts/{accountId}/transfer
   → Missing contractId in path!

❌ /api/v1/transfer?contractId={contractId}
   → contractId in query param, not path!

❌ /api/v1/balance
   → No contract specified at all!
```

### How Context is Resolved

```java
@Component
public class MyContextResolver extends AbstractContextResolver {
    
    @Override
    public Mono<UUID> resolvePartyId(ServerWebExchange exchange) {
        // ✅ From JWT token (authenticated user)
        return extractPartyIdFromJwt(exchange);
    }
    
    @Override
    public Mono<UUID> resolveContractId(ServerWebExchange exchange) {
        // ✅ From path parameter
        return extractFromPathVariable(exchange, "contractId");
    }
    
    @Override
    public Mono<UUID> resolveProductId(ServerWebExchange exchange) {
        // ✅ From path parameter (may be absent)
        return extractFromPathVariable(exchange, "productId");
    }
    
    @Override
    public Mono<UUID> resolveTenantId(ServerWebExchange exchange) {
        // ✅ From JWT or subdomain
        return extractTenantIdFromJwt(exchange);
    }
}
```

---

## 3. SecurityCenter Integration

### What is SecurityCenter?

SecurityCenter (`firefly-security-center`) is the centralized service that:

1. **Manages Party-Contract relationships**: Which parties have access to which contracts
2. **Manages Contract-Product associations**: Which products belong to which contracts
3. **Resolves roles**: What roles does a party have on a contract (ACCOUNT_HOLDER, OWNER, etc.)
4. **Resolves permissions**: What can a party do based on their roles and product rules
5. **Authorizes operations**: Final decision on whether an operation is allowed

### FireflySessionManager

The `FireflySessionManager` interface (provided by `firefly-security-center-sdk`) is the bridge between your application and SecurityCenter:

```java
// This interface comes from firefly-security-center-sdk
public interface FireflySessionManager {
    
    /**
     * Main authorization method
     */
    Mono<AuthorizationResult> authorize(
        UUID partyId,           // From JWT token
        UUID contractId,        // From path parameter
        UUID productId,         // From path parameter
        Set<String> requiredRoles,      // From @Secure annotation
        Set<String> requiredPermissions // From @Secure annotation
    );
    
    // Other helper methods...
}
```

### How Authorization Works

When you use `@Secure(roles="ACCOUNT_HOLDER", permissions="TRANSFER_FUNDS")`:

1. **Library extracts context**:
   - `partyId`: `12345678-1234-1234-1234-123456789012` (from JWT)
   - `contractId`: `abcd1234-5678-90ab-cdef-123456789abc` (from path `/contracts/{contractId}/...`)
   - `productId`: `prod9876-5432-10fe-dcba-987654321fed` (from path if present)

2. **Library calls SecurityCenter**:
   ```java
   sessionManager.authorize(
       partyId: 12345678-1234-1234-1234-123456789012,
       contractId: abcd1234-5678-90ab-cdef-123456789abc,
       productId: prod9876-5432-10fe-dcba-987654321fed,
       requiredRoles: ["ACCOUNT_HOLDER"],
       requiredPermissions: ["TRANSFER_FUNDS"]
   )
   ```

3. **SecurityCenter validates**:
   - ✅ Does party `12345678...` have access to contract `abcd1234...`?
   - ✅ Does contract `abcd1234...` belong to product `prod9876...`?
   - ✅ Does party `12345678...` have role `ACCOUNT_HOLDER` on contract `abcd1234...`?
   - ✅ Does role `ACCOUNT_HOLDER` grant permission `TRANSFER_FUNDS` for product `prod9876...`?

4. **SecurityCenter returns decision**:
   ```java
   AuthorizationResult {
       granted: true,
       partyId: 12345678-1234-1234-1234-123456789012,
       contractId: abcd1234-5678-90ab-cdef-123456789abc,
       productId: prod9876-5432-10fe-dcba-987654321fed,
       grantedRoles: ["ACCOUNT_HOLDER", "AUTHORIZED_USER"],
       grantedPermissions: ["TRANSFER_FUNDS", "VIEW_BALANCE", "VIEW_TRANSACTIONS"]
   }
   ```

5. **Library makes decision**:
   - If `granted = true`: ✅ Proceed to business logic
   - If `granted = false`: ❌ Throw `AccessDeniedException` → 403 Forbidden

---

## 4. Security Configuration Approaches

### Two Ways to Configure Security

The Application Layer provides **two complementary approaches** to configure endpoint security:

1. **Declarative (Annotations)**: Using `@Secure` on controller methods - **Simple and readable**
2. **Explicit (Registry)**: Using `EndpointSecurityRegistry` - **Flexible and dynamic**

**Priority Rule**: ⚠️ **Explicit configuration (registry) ALWAYS takes precedence over declarative configuration (annotations).**

This dual approach allows you to:
- Use annotations for standard, static security requirements
- Use registry for runtime-configurable, feature-flagged, or environment-specific security
- Override annotation-based security temporarily without code changes

---

### 4.1. Declarative Security: @Secure Annotation

#### Basic Usage

### Basic Usage

```java
@RestController
@RequestMapping("/api/v1/contracts/{contractId}")
public class AccountController {
    
    // Require role ACCOUNT_HOLDER
    @GetMapping("/balance")
    @Secure(roles = "ACCOUNT_HOLDER")
    public Mono<BalanceResponse> getBalance(
            @PathVariable UUID contractId,
            ServerWebExchange exchange) {
        
        return accountService.getBalance(exchange, contractId);
    }
    
    // Require permission TRANSFER_FUNDS
    @PostMapping("/accounts/{accountId}/transfer")
    @Secure(permissions = "TRANSFER_FUNDS")
    public Mono<TransferResponse> transfer(
            @PathVariable UUID contractId,
            @PathVariable UUID accountId,
            @RequestBody TransferRequest request,
            ServerWebExchange exchange) {
        
        return accountService.transfer(exchange, contractId, accountId, request);
    }
    
    // Require BOTH role AND permission
    @DeleteMapping("/products/{productId}/cards/{cardId}")
    @Secure(roles = "ACCOUNT_OWNER", permissions = "MANAGE_CARDS")
    public Mono<Void> deleteCard(
            @PathVariable UUID contractId,
            @PathVariable UUID productId,
            @PathVariable UUID cardId,
            ServerWebExchange exchange) {
        
        return cardService.deleteCard(exchange, contractId, productId, cardId);
    }
}
```

### @Secure Annotation Reference

```java
@Secure(
    roles = {"ROLE1", "ROLE2"},              // Any of these roles (OR logic)
    permissions = {"PERM1", "PERM2"},        // Any of these permissions (OR logic)
    expression = "hasRole('ADMIN')",         // SpEL expression (advanced)
    allowAnonymous = false                   // Allow unauthenticated access
)
```

### Class-Level Security

Apply security to ALL methods in a controller:

```java
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/admin")
@Secure(roles = "ADMIN")  // ← All methods require ADMIN role
public class AdminController {
    
    @GetMapping("/settings")
    public Mono<Settings> getSettings(...) {
        // Automatically protected by ADMIN role
    }
    
    @PutMapping("/settings")
    @Secure(permissions = "MODIFY_SETTINGS")  // ← Additional permission check
    public Mono<Settings> updateSettings(...) {
        // Requires BOTH: ADMIN role (class) + MODIFY_SETTINGS permission (method)
    }
}
```

---

### 4.2. Explicit Security: EndpointSecurityRegistry

#### Why Use Explicit Configuration?

The `EndpointSecurityRegistry` allows you to configure security **programmatically at runtime**, which is useful for:

- **Dynamic security**: Change security rules based on environment variables or feature flags
- **External configuration**: Load security rules from database or configuration service
- **Override annotations**: Temporarily override `@Secure` annotations without modifying code
- **Testing**: Easily adjust security for integration tests
- **A/B testing**: Different security for different user segments

#### Basic Usage

```java
@Configuration
public class SecurityConfiguration {
    
    @Autowired
    private EndpointSecurityRegistry securityRegistry;
    
    @PostConstruct
    public void configureEndpointSecurity() {
        // Register security for specific endpoint
        securityRegistry.registerEndpoint(
            "/api/v1/contracts/{contractId}/accounts",
            "POST",
            EndpointSecurity.builder()
                .roles(Set.of("ACCOUNT_CREATOR"))
                .permissions(Set.of("CREATE_ACCOUNT"))
                .requiresAuthentication(true)
                .build()
        );
        
        // Strict DELETE operation
        securityRegistry.registerEndpoint(
            "/api/v1/contracts/{contractId}/accounts/{accountId}",
            "DELETE",
            EndpointSecurity.builder()
                .roles(Set.of("ACCOUNT_ADMIN", "DELETE_AUTHORIZED"))
                .requireAllRoles(true)  // Must have BOTH roles
                .requiresAuthentication(true)
                .build()
        );
        
        // Public endpoint
        securityRegistry.registerEndpoint(
            "/api/v1/public/rates",
            "GET",
            EndpointSecurity.builder()
                .allowAnonymous(true)
                .requiresAuthentication(false)
                .build()
        );
    }
}
```

#### EndpointSecurity Builder Reference

```java
EndpointSecurity.builder()
    .roles(Set.of("ROLE1", "ROLE2"))              // Required roles (ANY by default)
    .permissions(Set.of("PERM1", "PERM2"))        // Required permissions (ANY by default)
    .requireAllRoles(true)                        // Require ALL roles instead of ANY
    .requireAllPermissions(true)                  // Require ALL permissions instead of ANY
    .allowAnonymous(true)                         // Allow unauthenticated access
    .requiresAuthentication(false)                // Requires authentication
    .expression("custom SpEL expression")         // Custom expression (future)
    .attributes(Map.of("key", "value"))           // Custom metadata
    .build();
```

#### Dynamic Configuration Example

```java
@Component
public class DynamicSecurityConfigurator {
    
    @Autowired
    private EndpointSecurityRegistry securityRegistry;
    
    @Autowired
    private FeatureFlagService featureFlags;
    
    @PostConstruct
    public void configureSecurity() {
        if (featureFlags.isEnabled("STRICT_TRANSACTION_SECURITY")) {
            // Feature flag ON: require additional permission
            securityRegistry.registerEndpoint(
                "/api/v1/contracts/{contractId}/products/{productId}/transactions",
                "POST",
                EndpointSecurity.builder()
                    .roles(Set.of("ACCOUNT_HOLDER"))
                    .permissions(Set.of("TRANSFER_FUNDS", "AUDIT_APPROVED"))
                    .requireAllPermissions(true)
                    .build()
            );
        } else {
            // Feature flag OFF: standard permission
            securityRegistry.registerEndpoint(
                "/api/v1/contracts/{contractId}/products/{productId}/transactions",
                "POST",
                EndpointSecurity.builder()
                    .roles(Set.of("ACCOUNT_HOLDER"))
                    .permissions(Set.of("TRANSFER_FUNDS"))
                    .build()
            );
        }
    }
}
```

#### Overriding Annotation-Based Security

```java
// Controller with annotation
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/accounts")
public class AccountController {
    
    @PostMapping
    @Secure(roles = "ACCOUNT_CREATOR")  // Default: only ACCOUNT_CREATOR
    public Mono<AccountDto> createAccount(
            @PathVariable UUID contractId,
            @RequestBody CreateAccountRequest request,
            ServerWebExchange exchange) {
        return accountService.createAccount(exchange, contractId, request);
    }
}

// Configuration that overrides the annotation
@Configuration
public class SecurityOverrideConfig {
    
    @Autowired
    private EndpointSecurityRegistry securityRegistry;
    
    @Value("${security.strict-mode:false}")
    private boolean strictMode;
    
    @PostConstruct
    public void overrideSecurity() {
        if (strictMode) {
            // OVERRIDE annotation: now requires ADMIN instead
            securityRegistry.registerEndpoint(
                "/api/v1/contracts/{contractId}/accounts",
                "POST",
                EndpointSecurity.builder()
                    .roles(Set.of("ACCOUNT_ADMIN"))  // Overrides annotation
                    .permissions(Set.of("CREATE_ACCOUNT", "APPROVE_CREATION"))
                    .requireAllPermissions(true)
                    .build()
            );
            
            log.info("Strict mode enabled: Account creation requires ACCOUNT_ADMIN");
        }
        // If strict mode is OFF, the annotation is used
    }
}
```

#### Registry Management Operations

```java
@Autowired
private EndpointSecurityRegistry securityRegistry;

// Check if endpoint is registered
boolean isRegistered = securityRegistry.isRegistered(
    "/api/v1/contracts/{contractId}/accounts",
    "POST"
);

// Get current configuration
Optional<EndpointSecurity> config = securityRegistry.getEndpointSecurity(
    "/api/v1/contracts/{contractId}/accounts",
    "POST"
);

// Unregister endpoint (falls back to annotation)
securityRegistry.unregisterEndpoint(
    "/api/v1/contracts/{contractId}/accounts",
    "POST"
);

// Clear all registered endpoints
securityRegistry.clear();

// Get all registered endpoints
Map<String, EndpointSecurity> allEndpoints = securityRegistry.getAllEndpoints();
```

---

### 4.3. Configuration Priority Flow

#### Priority Rules

```
Request arrives → SecurityAspect intercepts
                       ↓
        Check EndpointSecurityRegistry
                       ↓
         Explicit config exists?
                ↓              ↓
              YES             NO
                ↓              ↓
     Use Registry Config    Check @Secure annotation
                ↓              ↓
           ENFORCE        Annotation exists?
                              ↓        ↓
                            YES       NO
                              ↓        ↓
                   Use Annotation   DENY (default)
                              ↓
                          ENFORCE
```

#### Priority Example

```java
// 1. Controller with annotation (LOWER PRIORITY)
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/transfers")
public class TransferController {
    
    @PostMapping
    @Secure(roles = "ACCOUNT_HOLDER")  // Will be IGNORED if registry has config
    public Mono<TransferDto> transfer(...) {
        // Implementation
    }
}

// 2. Registry configuration (HIGHER PRIORITY)
@Configuration
public class TransferSecurityConfig {
    
    @PostConstruct
    public void configure() {
        // This OVERRIDES the @Secure annotation above
        securityRegistry.registerEndpoint(
            "/api/v1/contracts/{contractId}/transfers",
            "POST",
            EndpointSecurity.builder()
                .roles(Set.of("ACCOUNT_HOLDER"))
                .permissions(Set.of("TRANSFER_FUNDS", "HIGH_VALUE_TRANSFER"))
                .requireAllPermissions(true)
                .build()
        );
        
        // Now the endpoint requires BOTH permissions,
        // not just the ACCOUNT_HOLDER role from annotation
    }
}
```

#### When to Use Each Approach

| Use Case | Recommended Approach |
|----------|---------------------|
| Standard CRUD operations | **Annotations** - Simple and clear |
| Environment-specific security | **Registry** - Production vs. Development |
| Feature-flagged endpoints | **Registry** - Enable/disable dynamically |
| Temporary security overrides | **Registry** - No code changes needed |
| A/B testing security | **Registry** - Different rules per segment |
| Static, well-known requirements | **Annotations** - Easier to read in code |
| Database-driven security rules | **Registry** - Load from external source |

---

## 5. Complete Authorization Flow

### Step-by-Step Example

Let's trace a complete request through the security system:

#### Request

```http
POST /api/v1/contracts/aaaa-1111-bbbb-2222/accounts/cccc-3333/transfer
Authorization: Bearer eyJhbGc...  (JWT token with partyId = xxxx-9999-yyyy-8888)
Content-Type: application/json

{
  "fromAccount": "cccc-3333",
  "toAccount": "dddd-4444",
  "amount": 100.00
}
```

#### Controller

```java
@RestController
@RequestMapping("/api/v1/contracts/{contractId}")
public class TransferController {
    
    @PostMapping("/accounts/{accountId}/transfer")
    @Secure(roles = "ACCOUNT_HOLDER", permissions = "TRANSFER_FUNDS")
    public Mono<TransferResponse> transfer(
            @PathVariable UUID contractId,  // aaaa-1111-bbbb-2222
            @PathVariable UUID accountId,   // cccc-3333
            @RequestBody TransferRequest request,
            ServerWebExchange exchange) {
        
        return transferService.executeTransfer(exchange, contractId, accountId, request);
    }
}
```

#### Security Flow

1. **AOP Intercepts**:
   - Method: `transfer()`
   - Security requirements: `roles=["ACCOUNT_HOLDER"], permissions=["TRANSFER_FUNDS"]`

2. **Extract Context**:
   ```java
   partyId = extractFromJwt(exchange)      // xxxx-9999-yyyy-8888
   contractId = pathVariable("contractId") // aaaa-1111-bbbb-2222
   productId = null                        // Not in this path
   ```

3. **Call SecurityCenter**:
   ```java
   sessionManager.authorize(
       xxxx-9999-yyyy-8888,              // partyId
       aaaa-1111-bbbb-2222,              // contractId
       null,                             // productId
       ["ACCOUNT_HOLDER"],               // required roles
       ["TRANSFER_FUNDS"]                // required permissions
   )
   ```

4. **SecurityCenter Checks**:
   - ✅ Party `xxxx-9999` has access to contract `aaaa-1111`? **YES**
   - ✅ Party has role `ACCOUNT_HOLDER` on this contract? **YES**
   - ✅ Role `ACCOUNT_HOLDER` grants permission `TRANSFER_FUNDS`? **YES**

5. **Decision**:
   - ✅ **GRANTED** → Proceed to `transferService.executeTransfer()`

6. **Response**:
   ```json
   {
     "transferId": "transfer-5555-6666",
     "status": "COMPLETED",
     "amount": 100.00
   }
   ```

#### If Authorization Fails

If SecurityCenter returns `granted=false`:

```
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "error": "FORBIDDEN",
  "message": "Access denied: Required permission TRANSFER_FUNDS not granted",
  "partyId": "xxxx-9999-yyyy-8888",
  "contractId": "aaaa-1111-bbbb-2222"
}
```

---

## 6. Base Controllers for Path Structure

### Overview

The library provides **optional** abstract base controllers that help enforce clear, consistent path structures for contract and product-scoped endpoints. These controllers:

- Provide validation helper methods
- Enforce explicit `contractId` and `productId` in paths
- Promote consistent API design across microservices
- Include logging utilities for debugging

⚠️ **Important**: These are **optional**. You are NOT required to extend them, but they provide a convenient pattern for ensuring clarity.

---

### 6.1. AbstractContractController

#### Purpose

For endpoints scoped to contracts:
```
/api/v1/contracts/{contractId}/...
```

#### Usage

```java
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/accounts")
@RequiredArgsConstructor
public class AccountController extends AbstractContractController {
    
    private final AccountApplicationService accountService;
    
    @GetMapping
    @Secure(roles = "ACCOUNT_VIEWER")
    public Mono<List<AccountDto>> getAccounts(
            @PathVariable UUID contractId,
            ServerWebExchange exchange) {
        
        validateContractId(contractId);  // From base class
        logContractContext(contractId, "getAccounts");
        
        return accountService.getAccountsByContract(exchange, contractId);
    }
    
    @PostMapping
    @Secure(roles = "ACCOUNT_CREATOR", permissions = "CREATE_ACCOUNT")
    public Mono<AccountDto> createAccount(
            @PathVariable UUID contractId,
            @RequestBody CreateAccountRequest request,
            ServerWebExchange exchange) {
        
        validateContractId(contractId);
        logContractContext(contractId, "createAccount");
        
        return accountService.createAccount(exchange, contractId, request);
    }
}
```

#### Available Methods

| Method | Description |
|--------|-------------|
| `validateContractId(UUID)` | Validates contractId is not null, throws `IllegalArgumentException` if null |
| `logContractContext(UUID, String)` | Logs operation with contractId for debugging |

---

### 6.2. AbstractProductController

#### Purpose

For endpoints scoped to products within contracts:
```
/api/v1/contracts/{contractId}/products/{productId}/...
```

#### Usage

```java
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/products/{productId}/transactions")
@RequiredArgsConstructor
public class TransactionController extends AbstractProductController {
    
    private final TransactionApplicationService transactionService;
    
    @GetMapping
    @Secure(roles = "TRANSACTION_VIEWER")
    public Mono<List<TransactionDto>> getTransactions(
            @PathVariable UUID contractId,
            @PathVariable UUID productId,
            ServerWebExchange exchange) {
        
        validateContext(contractId, productId);  // From base class
        logProductContext(contractId, productId, "getTransactions");
        
        return transactionService.getTransactionsByProduct(exchange, contractId, productId);
    }
    
    @PostMapping
    @Secure(roles = "ACCOUNT_HOLDER", permissions = "TRANSFER_FUNDS")
    public Mono<TransactionDto> createTransaction(
            @PathVariable UUID contractId,
            @PathVariable UUID productId,
            @RequestBody CreateTransactionRequest request,
            ServerWebExchange exchange) {
        
        validateContext(contractId, productId);
        logProductContext(contractId, productId, "createTransaction");
        
        return transactionService.createTransaction(exchange, contractId, productId, request);
    }
}
```

#### Available Methods

| Method | Description |
|--------|-------------|
| `validateContext(UUID, UUID)` | Validates both contractId and productId are not null |
| `validateContractId(UUID)` | Validates only contractId |
| `validateProductId(UUID)` | Validates only productId |
| `logProductContext(UUID, UUID, String)` | Logs operation with both IDs for debugging |

---

### 6.3. Benefits of Using Base Controllers

✅ **Explicit scoping**: Path structure makes it clear this is a contract/product-scoped operation  
✅ **Built-in validation**: Prevents null contractId/productId from reaching business logic  
✅ **Consistent patterns**: All microservices follow the same structure  
✅ **Better debugging**: Logging helpers provide context in logs  
✅ **Clear intent**: Developers immediately understand the resource hierarchy  

---

### 6.4. When NOT to Use Base Controllers

❌ **Public endpoints**: Endpoints that don't require contract context  
❌ **Admin endpoints**: System-level operations not scoped to contracts  
❌ **Health/metrics endpoints**: Infrastructure endpoints  

```java
// These DO NOT need base controllers
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {
    
    @GetMapping("/rates")
    @Secure(allowAnonymous = true)
    public Mono<RatesDto> getPublicRates() {
        return ratesService.getPublicRates();
    }
}

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    
    @GetMapping("/system/status")
    @Secure(roles = "SYSTEM_ADMIN")
    public Mono<SystemStatus> getSystemStatus() {
        return systemService.getStatus();
    }
}
```

---

## 7. Examples

### Example 1: Account Balance (Simple)

```java
@GetMapping("/contracts/{contractId}/balance")
@Secure(roles = "ACCOUNT_HOLDER")
public Mono<BalanceResponse> getBalance(
        @PathVariable UUID contractId,
        ServerWebExchange exchange) {
    
    // SecurityCenter validates:
    // - Does authenticated party have role ACCOUNT_HOLDER on this contract?
    
    return balanceService.getBalance(exchange, contractId);
}
```

### Example 2: Transfer with Product (Complex)

```java
@PostMapping("/contracts/{contractId}/products/{productId}/transfer")
@Secure(roles = "ACCOUNT_HOLDER", permissions = "TRANSFER_FUNDS")
public Mono<TransferResponse> transfer(
        @PathVariable UUID contractId,
        @PathVariable UUID productId,
        @RequestBody TransferRequest request,
        ServerWebExchange exchange) {
    
    // SecurityCenter validates:
    // - Does authenticated party have access to this contract?
    // - Does this contract belong to this product?
    // - Does party have role ACCOUNT_HOLDER?
    // - Does ACCOUNT_HOLDER role grant TRANSFER_FUNDS permission for this product?
    
    return transferService.transfer(exchange, contractId, productId, request);
}
```

### Example 3: Admin Operation

```java
@PutMapping("/contracts/{contractId}/products/{productId}/limits")
@Secure(roles = "ACCOUNT_OWNER", permissions = "MODIFY_LIMITS")
public Mono<LimitsResponse> updateLimits(
        @PathVariable UUID contractId,
        @PathVariable UUID productId,
        @RequestBody LimitsRequest request,
        ServerWebExchange exchange) {
    
    // SecurityCenter validates:
    // - Does party have role ACCOUNT_OWNER (higher privilege than ACCOUNT_HOLDER)?
    // - Does party have permission MODIFY_LIMITS?
    
    return limitsService.updateLimits(exchange, contractId, productId, request);
}
```

---

## 8. Summary

### Key Takeaways

1. **Always use path parameters** for `contractId` and `productId` - Never use query params or request body
2. **Two security configuration methods**:
   - **Annotations** (`@Secure`) - For static, well-known requirements
   - **Registry** (`EndpointSecurityRegistry`) - For dynamic, runtime configuration
3. **Priority rule**: **Registry ALWAYS overrides annotations**
4. **SecurityCenter validates** all authorization decisions via `FireflySessionManager`
5. **Base controllers** are optional but help enforce clear path structures
6. **Trust the library** - it handles all the complexity of context resolution and SecurityCenter integration

---

### Security Configuration Decision Tree

```
Need to configure endpoint security?
    ↓
Is security static and well-known?
    ↓                           ↓
  YES                          NO
    ↓                           ↓
Use @Secure annotation      Is it dynamic/environment-based?
    ↓                           ↓
✅ Simple & readable          YES
                                ↓
                    Use EndpointSecurityRegistry
                                ↓
                ✅ Flexible & runtime-configurable

Need to override annotation temporarily?
    ↓
Use EndpointSecurityRegistry
    ↓
✅ No code changes needed
```

---

### Integration Checklist

When building an Application Layer microservice:

**Path Structure:**
- [ ] Add `@PathVariable UUID contractId` to ALL endpoints (except public/admin)
- [ ] Add `@PathVariable UUID productId` where product-specific
- [ ] Use path pattern: `/api/v1/contracts/{contractId}/...` or `/api/v1/contracts/{contractId}/products/{productId}/...`
- [ ] Optionally extend `AbstractContractController` or `AbstractProductController` for validation helpers

**Security Configuration:**
- [ ] Add `@Secure` annotations with appropriate roles/permissions
- [ ] OR register endpoints in `EndpointSecurityRegistry` for dynamic security
- [ ] Understand that registry config overrides annotations

**Integration:**
- [ ] Pass `ServerWebExchange` to services (needed for context resolution)
- [ ] Implement `ContextResolver` to extract `partyId` from JWT
- [ ] Add `firefly-security-center-sdk` dependency (when available)
- [ ] Configure SecurityCenter URL in `application.yml`

**Testing:**
- [ ] Test with different roles and permissions
- [ ] Test registry override behavior if using explicit configuration
- [ ] Verify path variable validation works correctly

---

### Quick Reference Card

| Feature | How to Use |
|---------|------------|
| **Declarative Security** | `@Secure(roles = "ROLE", permissions = "PERM")` on controller methods |
| **Explicit Security** | `securityRegistry.registerEndpoint(path, method, config)` in `@Configuration` |
| **Contract-Scoped Endpoints** | Extend `AbstractContractController` + `/contracts/{contractId}/...` |
| **Product-Scoped Endpoints** | Extend `AbstractProductController` + `/contracts/{contractId}/products/{productId}/...` |
| **Override Annotations** | Register same endpoint in registry with different config |
| **Check Registration** | `securityRegistry.isRegistered(path, method)` |
| **Get Current Config** | `securityRegistry.getEndpointSecurity(path, method)` |
| **Remove Override** | `securityRegistry.unregisterEndpoint(path, method)` (falls back to annotation) |

---

That's it! The library handles the rest. 🔒

For more information, see:
- [Architecture Guide](ARCHITECTURE.md)
- [Usage Guide](USAGE_GUIDE.md)
- [Main README](../README.md)
