# 🏛️ Application Layer Architecture

> Complete architectural guide for building Application Layer microservices with lib-common-application

---

## 📖 Table of Contents

### 🎯 Core Concepts
1. [🏭 Understanding the Three-Layer Architecture](#understanding-the-three-layer-architecture)
   - Application Layer (THIS LIBRARY)
   - Domain Layer
   - Infrastructure/Platform Layer
   - When to Use Application Layer
2. [📋 Overview](#overview)
   - What is Application Layer?
   - Key Responsibilities
   - Core Components

### 🛠️ Architecture & Design
3. [🎭 Architectural Patterns](#architectural-patterns)
   - Application Service Pattern
   - Context Object Pattern
   - Strategy Pattern
   - Decorator Pattern
   - Registry Pattern
4. [📏 Layer Responsibilities](#layer-responsibilities)
   - Application Layer Duties
   - Domain Layer Duties
   - Platform Layer Duties
5. [🧩 Component Design](#component-design)
   - ApplicationExecutionContext
   - AppMetadata
   - AppContext
   - AppConfig
   - AppSecurityContext
   - Abstract Services
   - Resolvers

### 🔄 Integration & Flow
6. [🔀 Data Flow](#data-flow)
   - Request Processing Flow
   - Context Resolution Flow
   - Security Authorization Flow
7. [🔌 Integration Points](#integration-points)
   - Domain Service Integration
   - Platform Service Integration
   - Security Center Integration

### 📝 Documentation
8. [📝 Design Decisions](#design-decisions)
   - Architecture Decision Records (ADRs)
   - Key Trade-offs
   - Best Practices

---

## Understanding the Three-Layer Architecture

Firefly implements a clean, layered architecture with three distinct types of microservices, each serving a specific purpose in the overall system architecture.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CHANNELS & APPLICATIONS                         │
│                    (Web, Mobile, APIs, Third-party)                      │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  │ HTTP/REST/GraphQL
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER MICROSERVICES                       │
│                    (lib-common-application)                             │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  Business Process Orchestration Microservices                      │ │
│  │  - customer-application-onboarding                                 │ │
│  │  - lending-application-loan-origination                            │ │
│  │  - payment-application-transfer                                    │ │
│  │                                                                    │ │
│  │  Responsibilities:                                                 │ │
│  │  • Expose REST/GraphQL APIs to channels                            │ │
│  │  • Orchestrate multi-step business processes                       │ │
│  │  • Coordinate multiple domain services                             │ │
│  │  • Manage application context (party, contract, product)           │ │
│  │  • Handle security & authorization                                 │ │
│  │  • Manage tenant configuration & feature flags                     │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  │ Uses ApplicationExecutionContext
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER MICROSERVICES                          │
│                      (lib-common-domain)                                │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  Domain-Driven Design Microservices                                │ │
│  │  - customer-domain-people                                          │ │
│  │  - lending-domain-loan                                             │ │
│  │  - payment-domain-account                                          │ │
│  │                                                                    │ │
│  │  Responsibilities:                                                 │ │
│  │  • Implement business logic & rules                                │ │
│  │  • Maintain domain invariants                                      │ │
│  │  • Handle aggregates, entities, value objects                      │ │
│  │  • Publish domain events                                           │ │
│  │  • Single-domain operations only                                   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  │ Uses Repositories & Platform Services
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                 INFRASTRUCTURE/PLATFORM MICROSERVICES                   │
│                    (lib-common-core / No specific lib)                  │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  Platform & Infrastructure Services                                │ │
│  │  - common-platform-customer-mgmt                                   │ │
│  │  - common-platform-contract-mgmt                                   │ │
│  │  - common-platform-product-mgmt                                    │ │
│  │  - common-platform-config-mgmt                                     │ │
│  │                                                                    │ │
│  │  Responsibilities:                                                 │ │
│  │  • Data persistence (repositories)                                 │ │
│  │  • Master data management                                          │ │
│  │  • External system integration                                     │ │
│  │  • Cross-cutting platform services                                 │ │
│  │  • Provide SDKs for upper layers                                   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### The Three Layers Explained

#### 1. Application Layer (THIS LIBRARY)

Purpose: Business process orchestration and API exposure to channels

Library: `lib-common-application`

Microservice Examples:
- `customer-application-onboarding` - Complete customer onboarding process
- `lending-application-loan-origination` - End-to-end loan origination workflow
- `payment-application-transfer` - Fund transfer orchestration
- `account-application-opening` - Account opening process

What This Layer Does:
1. Exposes APIs to channels (web, mobile, third-party applications)
2. Orchestrates business processes across multiple domains
3. Manages application context (partyId, contractId, productId, tenantId)
4. Handles security & authorization via @Secure annotations
5. Coordinates domain services to complete multi-step workflows
6. Manages tenant configuration and feature flags
7. Implements use cases that span multiple domains

Key Components from lib-common-application:
- `ApplicationExecutionContext` - Complete context for the request
- `AppMetadata` - Request metadata (requestId, correlationId, tracing)
- `AppContext` - Business context (partyId, contractId, productId, roles)
- `AppConfig` - Tenant configuration (providers, feature flags, settings)
- `AppSecurityContext` - Security requirements and authorization
- `@Secure` - Declarative security annotation
- `AbstractApplicationService` - Base for application services

#### 2. Domain Layer

Purpose: Implement business logic and domain rules

Library: `lib-common-domain`

Microservice Examples:
- `customer-domain-people` - Customer/person domain logic
- `lending-domain-loan` - Loan domain logic
- `payment-domain-account` - Account domain logic
- `contract-domain-agreement` - Contract domain logic

What This Layer Does:
1. Implements business logic specific to a domain
2. Maintains domain invariants and business rules
3. Manages aggregates, entities, value objects (DDD patterns)
4. Publishes domain events for async communication
5. Provides domain services for single-domain operations
6. Enforces business rules within the domain boundary

Does NOT:
- Expose REST APIs to external channels
- Orchestrate multiple domains
- Make HTTP calls to other domains
- Manage application-level context

#### 3. Infrastructure/Platform Layer

Purpose: Data persistence, master data, platform services

Library: `lib-common-core` (or no specific library)

Microservice Examples:
- `common-platform-customer-mgmt` - Customer master data management
- `common-platform-contract-mgmt` - Contract master data management
- `common-platform-product-mgmt` - Product catalog management
- `common-platform-config-mgmt` - Tenant configuration management
- `common-platform-reference-master-data` - Reference data

What This Layer Does:
1. Data persistence via repositories (databases)
2. Master data management (customer, product, contract data)
3. External system integration (third-party APIs)
4. Cross-cutting platform services (config, reference data)
5. Provides SDKs for application and domain layers

### When to Use Application Layer

#### ✅ Use Application Layer Microservices When:

1. Exposing APIs to channels (web, mobile, third-party)
2. Orchestrating multiple domains (customer + contract + account)
3. Implementing complex workflows (loan origination, onboarding)
4. Managing application-level security (API authentication, authorization)
5. Handling multi-tenant requirements (tenant config, feature flags)
6. Coordinating transactions across multiple domains
7. Implementing use cases that span business domains

#### ❌ Don't Use Application Layer When:

1. Single-domain operations → Use domain layer
2. Internal domain logic → Use domain layer
3. Data persistence only → Use infrastructure/platform layer
4. Background jobs without API exposure → Use domain layer
5. Simple CRUD operations → Can be domain or platform layer

### Key Principles

1. Application layer = API + Orchestration
   - Exposes REST/GraphQL to external consumers
   - Orchestrates multiple domain services
   - Manages complete application context

2. Domain layer = Business Logic
   - Focused on single domain
   - Receives simplified context (AppContext)
   - Publishes domain events

3. Platform layer = Infrastructure
   - Data persistence
   - Master data management
   - Provides SDKs to upper layers

4. Context flows down, Events flow up
   - Application → Domain: Pass AppContext
   - Domain → Application: Publish domain events
   - Never: Domain → Domain direct HTTP calls

5. Security at Application Layer
   - @Secure annotations on controllers
   - ApplicationExecutionContext includes security
   - Domain layer trusts the context

## Overview

The Application Layer is a critical architectural layer in the Firefly platform that sits between the Presentation Layer (REST controllers, GraphQL resolvers) and the Domain Layer (business logic). It serves as the orchestration and coordination layer for business processes.

### Architectural Position

```
┌─────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                         │
│  (Controllers, GraphQL Resolvers, Message Handlers)             │
│  - HTTP Request Handling                                        │
│  - Input Validation                                             │
│  - Response Formatting                                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ ServerWebExchange / Request
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                          │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐      │
│  │   Context    │  │   Security    │  │  Configuration   │      │
│  │  Resolution  │  │ Authorization │  │   Management     │      │
│  └──────────────┘  └───────────────┘  └──────────────────┘      │
│                                                                 │
│  - Business Process Orchestration                               │
│  - Multi-step Workflow Coordination                             │
│  - Cross-Domain Service Integration                             │
│  - Transaction Coordination                                     │
│  - Context Management (Party, Contract, Product, Tenant)        │
│  - Security & Authorization                                     │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ ApplicationExecutionContext
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Domain Layer                             │
│  (Aggregates, Entities, Value Objects, Domain Services)         │
│  - Business Logic                                               │
│  - Business Rules Enforcement                                   │
│  - Domain Invariants                                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                         │
│  (Repositories, External Services, Message Brokers)            │
└─────────────────────────────────────────────────────────────────┘
```

## Architectural Patterns

### 1. Application Service Pattern

Purpose: Encapsulate business process orchestration and cross-domain coordination.

Implementation: `AbstractApplicationService`

The Application Service pattern ensures that:
- Complex business processes are coordinated at the application layer
- Domain services remain focused on single-domain operations
- Transaction boundaries are clearly defined
- Context is properly managed throughout the request lifecycle

Example:
```java
public Mono<Transfer> transferFunds(ServerWebExchange exchange, TransferRequest request) {
    return resolveExecutionContext(exchange)              // 1. Context Resolution
        .flatMap(ctx -> validateContext(ctx, true, true)) // 2. Context Validation
        .flatMap(ctx -> requirePermission(ctx, "TRANSFER_FUNDS")) // 3. Authorization
        .flatMap(ctx -> {
            // 4. Business Process Orchestration
            return accountDomainService.debit(ctx, request.getFromAccount())
                .then(accountDomainService.credit(ctx, request.getToAccount()))
                .then(transactionDomainService.recordTransfer(ctx, request));
        });
}
```

### 2. Context Object Pattern

Purpose: Carry all necessary contextual information throughout the request lifecycle.

Implementation: `ApplicationExecutionContext`, `AppContext`, `AppMetadata`, `AppConfig`

The Context Object pattern provides:
- Immutability: All context objects are immutable (@Value with Lombok)
- Type Safety: Strong typing for all context attributes
- Completeness: All necessary information in one place
- Traceability: Request tracking through correlation IDs
- Audit Trail: Complete context for logging and auditing

Context Composition:
```
ApplicationExecutionContext
├── AppMetadata (Technical Context)
│   ├── requestId: UUID
│   ├── correlationId: UUID
│   ├── timestamp: Instant
│   ├── traceId: String
│   └── clientIp: String
├── AppContext (Business Context)
│   ├── partyId: UUID ← from customer-mgmt
│   ├── contractId: UUID ← from contract-mgmt
│   ├── productId: UUID ← from product-mgmt
│   ├── roles: Set<String>
│   ├── permissions: Set<String>
│   └── tenantId: UUID
├── AppConfig (Configuration Context)
│   ├── tenantId: UUID ← from config-mgmt
│   ├── providers: Map<String, ProviderConfig>
│   ├── featureFlags: Map<String, Boolean>
│   └── settings: Map<String, String>
└── AppSecurityContext (Security Context)
    ├── endpoint: String
    ├── requiredRoles: Set<String>
    ├── requiredPermissions: Set<String>
    └── authorized: boolean
```

### 3. Strategy Pattern for Context Resolution

Purpose: Support multiple strategies for resolving context information based on authentication mechanism or request source.

Implementation: `ContextResolver` interface with `AbstractContextResolver` base

Different applications may have different ways to extract context:
- JWT tokens
- Session cookies
- API keys
- Request headers
- Path parameters

The Strategy pattern allows each microservice to implement its own resolution logic while maintaining a consistent interface.

```java
@Component
@Order(1) // Higher priority
public class JwtContextResolver extends AbstractContextResolver {
    
    @Override
    public Mono<UUID> resolvePartyId(ServerWebExchange exchange) {
        return extractFromJwt(exchange, "sub")
            .map(UUID::fromString);
    }
    
    @Override
    public boolean supports(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().containsKey("Authorization");
    }
}

@Component
@Order(2) // Lower priority fallback
public class HeaderContextResolver extends AbstractContextResolver {
    
    @Override
    public Mono<UUID> resolvePartyId(ServerWebExchange exchange) {
        return extractUUID(exchange, "partyId", "X-Party-Id");
    }
}
```

### 4. Decorator Pattern for Security

Purpose: Add security concerns transparently without modifying business logic.

Implementation: `@Secure` annotation with `SecurityAspect` AOP interceptor

The Decorator pattern (via AOP) allows:
- Separation of Concerns: Security logic is separated from business logic
- Declarative Security: Clear, self-documenting security requirements
- Cross-cutting Concerns: Security is applied consistently across all endpoints
- Non-invasive: Business logic remains clean and focused

```java
@Aspect
public class SecurityAspect {
    
    @Around("@annotation(secure)")
    public Object secureMethod(ProceedingJoinPoint joinPoint, Secure secure) {
        // Security logic wraps the business method
        return authorizeAndProceed(joinPoint, secure);
    }
}
```

### 5. Registry Pattern for Endpoint Security

Purpose: Provide a centralized, programmatic way to register and query security requirements.

Implementation: `EndpointSecurityRegistry`

Complements the annotation-based approach with:
- Dynamic Registration: Register security rules at runtime
- Centralized Configuration: All security rules in one place
- Queryable: Security rules can be inspected programmatically
- Integration with External Systems: Can be populated from SecurityCenter

```java
registry.registerEndpoint(
    "/api/v1/accounts/{id}/transfer",
    "POST",
    EndpointSecurity.builder()
        .roles(Set.of("ACCOUNT_OWNER"))
        .permissions(Set.of("TRANSFER_FUNDS"))
        .build()
);
```

### 6. Template Method Pattern for Resolvers

Purpose: Define the skeleton of context/config resolution while allowing subclasses to customize specific steps.

Implementation: `AbstractContextResolver`, `AbstractConfigResolver`

The Template Method pattern provides:
- Consistent Structure: All resolvers follow the same flow
- Reusable Logic: Common logic (caching, logging) in base class
- Customization Points: Subclasses override only what they need
- Error Handling: Centralized error handling and retry logic

```java
public abstract class AbstractContextResolver {
    
    // Template method
    public Mono<AppContext> resolveContext(ServerWebExchange exchange, AppMetadata metadata) {
        return Mono.zip(
                resolvePartyId(exchange),
                resolveTenantId(exchange),
                resolveContractId(exchange),
                resolveProductId(exchange)
        ).flatMap(tuple -> enrichContext(...)); // Common flow
    }
    
    // Customization points
    protected abstract Mono<UUID> resolvePartyId(ServerWebExchange exchange);
    protected abstract Mono<UUID> resolveTenantId(ServerWebExchange exchange);
}
```

### 7. Facade Pattern for Application Services

Purpose: Provide a simplified interface to complex domain operations.

Implementation: `AbstractApplicationService`

The Facade pattern simplifies:
- Complex Subsystems: Hides complexity of multiple domain services
- Workflow Orchestration: Coordinates multiple domain operations
- Transaction Management: Manages transaction boundaries
- Error Translation: Converts domain exceptions to application-level exceptions

## Layer Responsibilities

### What the Application Layer DOES:

1. Context Management
   - Resolve party, contract, product, and tenant information
   - Enrich context with roles and permissions
   - Maintain context throughout request lifecycle
   - Cache context for performance

2. Security & Authorization
   - Enforce endpoint-level security
   - Check roles and permissions
   - Integrate with SecurityCenter for complex authorization
   - Audit access attempts

3. Business Process Orchestration
   - Coordinate multiple domain services
   - Manage multi-step workflows
   - Handle compensating transactions
   - Coordinate saga patterns

4. Configuration Management
   - Resolve tenant-specific configuration
   - Manage provider configurations
   - Handle feature flags
   - Cache configuration data

5. Cross-cutting Concerns
   - Logging and tracing
   - Metrics and monitoring
   - Error handling and translation
   - Request/response transformation

### What the Application Layer DOES NOT DO:

1. Business Logic: Domain logic belongs in the Domain Layer
2. Data Persistence: Repositories belong in the Infrastructure Layer
3. HTTP Handling: Request parsing belongs in the Presentation Layer
4. UI Logic: View concerns belong in the Presentation Layer

## Component Design

### Core Components

#### 1. ApplicationExecutionContext

Purpose: The main context object that carries all necessary information throughout the request lifecycle.

Design Decisions:
- Immutable: Uses Lombok @Value for immutability
- Builder Pattern: Easy construction with fluent API
- Composition: Aggregates specialized context objects
- With Methods: Lombok @With for creating modified copies

Rationale: Immutability ensures thread safety in reactive environments and prevents accidental modifications that could lead to bugs.

#### 2. ContextResolver

Purpose: Abstract the strategy for resolving context information.

Design Decisions:
- Interface-based: Allows multiple implementations
- Strategy Pattern: Different resolvers for different auth mechanisms
- Priority Support: Higher priority resolvers are tried first
- Supports Method: Resolvers can declare which requests they handle

Rationale: Different microservices may have different authentication mechanisms. The resolver abstraction allows each service to implement its own logic.

#### 3. SecurityAuthorizationService

Purpose: Centralize authorization logic and SecurityCenter integration.

Design Decisions:
- Abstract Base Class: Provides default behavior
- Template Method: Subclasses can override specific steps
- SecurityCenter Integration: Designed for external authorization service
- Evaluation Results: Detailed audit trail of authorization decisions

Rationale: Authorization logic is complex and may require integration with external services. The abstract base provides a consistent framework while allowing customization.

#### 4. SecurityAspect (AOP)

Purpose: Intercept and process @Secure annotations.

Design Decisions:
- Aspect-Oriented Programming: Non-invasive security
- Method and Class Level: Security can be applied at both levels
- Context Extraction: Automatically extracts ApplicationExecutionContext from method arguments
- Reactive Support: Works with Mono/Flux return types

Rationale: AOP allows security concerns to be separated from business logic, making code cleaner and more maintainable.

## Data Flow

### Request Flow (Successful Path)

```
1. HTTP Request
   └─> Controller receives request
       │
2. Context Resolution
   └─> SecurityAspect intercepts @Secure method
       └─> AbstractApplicationService.resolveExecutionContext()
           ├─> Extract AppMetadata from request
           ├─> ContextResolver.resolveContext()
           │   ├─> resolvePartyId() ← from JWT/header
           │   ├─> resolveTenantId() ← from JWT/subdomain
           │   ├─> resolveContractId() ← from path/header
           │   ├─> resolveProductId() ← from path/header
           │   └─> enrichContext()
           │       ├─> resolveRoles() ← customer-mgmt SDK
           │       └─> resolvePermissions() ← contract-mgmt SDK
           │
           └─> ConfigResolver.resolveConfig()
               └─> fetchConfigFromPlatform() ← config-mgmt SDK
                   ├─> Get tenant configuration
                   ├─> Get provider configs
                   └─> Get feature flags
       
3. Security Check
   └─> SecurityAuthorizationService.authorize()
       ├─> Build AppSecurityContext from @Secure annotation
       ├─> Check required roles against AppContext.roles
       ├─> Check required permissions against AppContext.permissions
       └─> Optional: Call SecurityCenter for complex policies
       
4. Authorization Decision
   ├─> If DENIED: Throw AccessDeniedException → 403 Forbidden
   └─> If GRANTED: Proceed to business logic
   
5. Business Logic Execution
   └─> ApplicationService orchestrates domain services
       ├─> Domain operations with ApplicationExecutionContext
       ├─> Cross-domain coordination
       └─> Transaction management
       
6. Response
   └─> Return result to controller
       └─> HTTP Response
```

### Context Resolution Flow (Detailed)

```
ServerWebExchange
    │
    ├─> Extract Headers
    │   ├─> Authorization (JWT)
    │   ├─> X-Tenant-Id
    │   ├─> X-Trace-Id
    │   └─> X-Party-Id
    │
    ├─> Extract Path Variables
    │   ├─> contractId
    │   └─> productId
    │
    └─> ContextResolver Strategy Selection
        │
        ├─> Check each resolver.supports(exchange)
        ├─> Sort by priority
        └─> Use first supporting resolver
            │
            ├─> resolvePartyId()
            │   └─> customer-mgmt-sdk.getParty(partyId)
            │
            ├─> resolveTenantId()
            │   └─> Extract from JWT or subdomain
            │
            ├─> resolveContractId()
            │   └─> Extract from path or header
            │
            ├─> resolveProductId()
            │   └─> Extract from path or header
            │
            └─> enrichContext()
                ├─> customer-mgmt-sdk.getPartyRoles(partyId, contractId)
                │   └─> Set<String> roles
                │
                └─> contract-mgmt-sdk.getPartyPermissions(partyId, contractId, productId)
                    └─> Set<String> permissions
```

## Integration Points

### 1. Customer Management Platform

SDK: `common-platform-customer-mgmt-sdk`

Purpose: Resolve party (customer) information and roles

Integration Points:
- `AbstractContextResolver.resolveRoles()`
- Get party details
- Get party roles in contract
- Validate party status

Data Retrieved:
- Party roles: `Set<String>`
- Party status: `ACTIVE | SUSPENDED | CLOSED`
- Party type: `INDIVIDUAL | CORPORATE`

### 2. Contract Management Platform

SDK: `common-platform-contract-mgmt-sdk`

Purpose: Resolve contract information and permissions

Integration Points:
- `AbstractContextResolver.resolvePermissions()`
- Get contract details
- Get party permissions in contract
- Validate contract status

Data Retrieved:
- Contract permissions: `Set<String>`
- Contract status: `ACTIVE | TERMINATED | SUSPENDED`
- Contract parties and their roles

### 3. Configuration Management Platform

SDK: `common-platform-config-mgmt-sdk`

Purpose: Resolve tenant configuration and provider settings

Integration Points:
- `AbstractConfigResolver.fetchConfigFromPlatform()`
- Get tenant configuration
- Get provider configurations
- Get feature flags

Data Retrieved:
- Tenant configuration
- Provider configurations (Payment gateways, KYC providers, etc.)
- Feature flags
- Tenant-specific settings

### 4. Security Center

SDK: To be defined (Firefly SecurityCenter Client)

Purpose: Complex authorization decisions and policy evaluation

Integration Points:
- `AbstractSecurityAuthorizationService.authorizeWithSecurityCenter()`
- Evaluate authorization policies
- Complex permission checks
- Attribute-Based Access Control (ABAC)

Data Sent:
- Party ID
- Contract ID
- Product ID
- Requested endpoint
- HTTP method
- Current roles and permissions

Data Received:
- Authorization decision: `GRANTED | DENIED`
- Reason for decision
- Policy that was evaluated
- Evaluation details for audit

## Design Decisions

### ADR-001: Immutable Context Objects

Status: Accepted

Context: Context objects are passed through multiple layers and potentially across thread boundaries in reactive applications.

Decision: All context objects are immutable using Lombok @Value.

Consequences:
- ✅ Thread-safe by design
- ✅ No defensive copying needed
- ✅ Clear data flow (no hidden mutations)
- ✅ Easier to reason about
- ⚠️ Must create new instances for modifications (using @With)

### ADR-002: Reactive First Design

Status: Accepted

Context: Firefly is built on Spring WebFlux for non-blocking, reactive operations.

Decision: All resolvers and services return Mono/Flux instead of blocking calls.

Consequences:
- ✅ Non-blocking I/O
- ✅ Better resource utilization
- ✅ Backpressure support
- ✅ Composable operations
- ⚠️ Learning curve for developers
- ⚠️ More complex error handling

### ADR-003: Two-Way Security Configuration

Status: Accepted

Context: Different teams may prefer different approaches to security configuration.

Decision: Support both annotation-based (@Secure) and programmatic (EndpointSecurityRegistry) security configuration.

Consequences:
- ✅ Flexibility for different use cases
- ✅ Annotations for most cases (declarative)
- ✅ Registry for dynamic security (programmatic)
- ⚠️ Need to check both sources
- ⚠️ Potential for conflicts (annotation takes precedence)

### ADR-004: Context Resolution as Strategy

Status: Accepted

Context: Different microservices use different authentication mechanisms (JWT, API keys, sessions).

Decision: Use Strategy pattern with multiple ContextResolver implementations.

Consequences:
- ✅ Flexibility per microservice
- ✅ Easy to add new strategies
- ✅ Clean separation of concerns
- ⚠️ Need to configure priority
- ⚠️ Must implement for each service

### ADR-005: Caching at Resolver Level

Status: Accepted

Context: External SDK calls (customer-mgmt, config-mgmt) can be expensive.

Decision: Implement caching in abstract resolvers with configurable TTL.

Consequences:
- ✅ Reduced latency
- ✅ Lower load on platform services
- ✅ Better performance
- ⚠️ Cache invalidation complexity
- ⚠️ Potential stale data
- ⚠️ Memory overhead

### ADR-006: AOP for Security

Status: Accepted

Context: Security checks should not clutter business logic.

Decision: Use AspectJ AOP to intercept @Secure annotations.

Consequences:
- ✅ Clean separation of concerns
- ✅ Non-invasive to business logic
- ✅ Consistent security enforcement
- ✅ Easy to test security independently
- ⚠️ "Magic" behavior (not immediately visible)
- ⚠️ Requires understanding of AOP

### ADR-007: SecurityCenter Integration Points

Status: Accepted

Context: Some authorization decisions require complex policy evaluation beyond simple role/permission checks.

Decision: Provide integration points with Firefly SecurityCenter as TODO placeholders.

Consequences:
- ✅ Future-proof for complex authorization
- ✅ Supports Attribute-Based Access Control (ABAC)
- ✅ Centralized policy management
- ✅ Audit trail of authorization decisions
- ⚠️ Network latency for remote calls
- ⚠️ Dependency on external service
- ⚠️ Need to implement circuit breakers

### ADR-008: No Implementation Logic in Library

Status: Accepted

Context: This library is a framework that will be used by multiple microservices with different requirements.

Decision: Provide abstract classes with TODO placeholders instead of concrete implementations.

Consequences:
- ✅ Maximum flexibility for implementers
- ✅ No assumptions about specific SDKs
- ✅ Clear integration points
- ✅ Each microservice can customize
- ⚠️ More work for first-time users
- ⚠️ Need good documentation

### ADR-009: ApplicationExecutionContext as Parameter

Status: Accepted

Context: Context needs to flow through all layers (application → domain → infrastructure).

Decision: Pass ApplicationExecutionContext as explicit parameter to all service methods.

Consequences:
- ✅ Explicit context passing (no ThreadLocal magic)
- ✅ Works in reactive environments
- ✅ Clear dependencies
- ✅ Easier to test
- ⚠️ Method signatures have one more parameter
- ⚠️ Must pass through all layers

### ADR-010: Builder Pattern for Context Objects

Status: Accepted

Context: Context objects have many optional fields.

Decision: Use Lombok @Builder for all context objects.

Consequences:
- ✅ Fluent, readable API
- ✅ Optional fields handled naturally
- ✅ Type-safe construction
- ✅ Works well with @With for copies
- ⚠️ Lombok dependency
- ⚠️ Generated code can be hard to debug

## Best Practices

### 1. Always Resolve Full Context

Always resolve the complete ApplicationExecutionContext at the start of the request:

```java
public Mono<Result> performOperation(ServerWebExchange exchange) {
    return resolveExecutionContext(exchange)
        .flatMap(context -> doOperation(context));
}
```

### 2. Validate Context Early

Validate that required context components are present before proceeding:

```java
return resolveExecutionContext(exchange)
    .flatMap(context -> validateContext(context, requireContract=true, requireProduct=true))
    .flatMap(context -> doOperation(context));
```

### 3. Use Feature Flags

Check feature flags before executing new or experimental features:

```java
return isFeatureEnabled(context, "NEW_TRANSFER_FLOW")
    .flatMap(enabled -> enabled 
        ? newTransferFlow(context, request)
        : legacyTransferFlow(context, request));
```

### 4. Log Security Decisions

Always log authorization decisions for audit:

```java
log.info("Authorization decision: {} for party: {} on endpoint: {}",
    authorized ? "GRANTED" : "DENIED",
    context.getPartyId(),
    endpoint);
```

### 5. Handle Errors Gracefully

Translate domain exceptions to appropriate application-level exceptions:

```java
.onErrorResume(DomainException.class, e -> {
    log.error("Domain error", e);
    return Mono.error(new ApplicationException("Operation failed", e));
});
```

### 6. Use Provider Configuration

Always use provider configuration instead of hard-coding:

```java
return getProviderConfig(context, "PAYMENT_GATEWAY")
    .flatMap(config -> paymentService.process(config, request));
```

### 7. Implement Circuit Breakers

Protect external SDK calls with circuit breakers:

```java
@CircuitBreaker(name = "customer-mgmt")
protected Mono<Set<String>> resolveRoles(AppContext context) {
    return customerManagementClient.getPartyRoles(...);
}
```

## Conclusion

The Application Layer architecture provides a robust, scalable foundation for business process orchestration in the Firefly platform. It successfully separates concerns, enables multi-tenancy, provides flexible security, and integrates cleanly with platform services while maintaining clean, testable code.
