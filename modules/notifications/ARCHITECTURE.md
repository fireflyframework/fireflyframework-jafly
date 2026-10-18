# Hexagonal Architecture - fireflyframework-notifications

## Table of Contents

1. [Introduction](#introduction)
2. [Architecture Overview](#architecture-overview)
3. [Core Principles](#core-principles)
4. [Layer Details](#layer-details)
5. [Dependency Flow](#dependency-flow)
6. [Adding New Providers](#adding-new-providers)
7. [Testing Strategy](#testing-strategy)
8. [Design Patterns](#design-patterns)

## Introduction

The `fireflyframework-notifications` library implements **Hexagonal Architecture** (also known as Ports and Adapters pattern) to provide a clean, maintainable, and testable notification system. This architectural style was introduced by Alistair Cockburn and focuses on separating the core business logic from external dependencies.

### Why Hexagonal Architecture?

Traditional layered architectures often suffer from tight coupling between business logic and infrastructure. Hexagonal architecture solves this by:

- **Isolating the domain**: Business rules don't depend on frameworks or external services
- **Enabling testability**: Core logic can be tested without real infrastructure
- **Facilitating change**: Swap providers (e.g., SendGrid → Resend) without touching business logic
- **Enforcing boundaries**: Clear contracts between layers through interfaces (ports)

## Architecture Overview

```
┌───────────────────────────────────────────────────────────────────────┐
│                         CLIENT APPLICATIONS                           │
│                  (Spring Boot services using this library)            │
└───────────────────────────────┬───────────────────────────────────────┘
                                │
                                │ uses
                                ▼
┌───────────────────────────────────────────────────────────────────────┐
│                        APPLICATION LAYER                              │
│                      (fireflyframework-notifications-core)                         │
│                                                                       │
│    ┌─────────────────────────────────────────────────────────┐      │
│    │  EmailService │ SMSService │ PushService                │      │
│    │  (Service Implementations - Application Logic)          │      │
│    └───────────────────┬─────────────────────────────────────┘      │
│                        │ depends on (via DI)                         │
│                        ▼                                              │
│    ┌─────────────────────────────────────────────────────────┐      │
│    │              DOMAIN LAYER (PORTS)                       │      │
│    │                                                          │      │
│    │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │      │
│    │  │EmailProvider │  │ SMSProvider  │  │ PushProvider │  │      │
│    │  │ (interface)  │  │ (interface)  │  │ (interface)  │  │      │
│    │  └──────────────┘  └──────────────┘  └──────────────┘  │      │
│    │                                                          │      │
│    │  ┌──────────────────────────────────────────────────┐  │      │
│    │  │  DTOs: EmailRequestDTO, SMSRequestDTO, etc.      │  │      │
│    │  └──────────────────────────────────────────────────┘  │      │
│    └─────────────────────────────────────────────────────────┘      │
└───────────────────────────────┬───────────────────────────────────────┘
                                │
                                │ implemented by
                                ▼
┌───────────────────────────────────────────────────────────────────────┐
│                    INFRASTRUCTURE LAYER (ADAPTERS)                    │
│                         (Separate Maven Modules)                      │
│                                                                       │
│  ┌──────────────────────┐  ┌──────────────────────┐                 │
│  │ fireflyframework-notifications-   │  │ fireflyframework-notifications-   │                 │
│  │      sendgrid        │  │      resend          │                 │
│  │                      │  │                      │                 │
│  │ SendGridEmailProvider│  │ ResendEmailProvider  │                 │
│  │   implements         │  │   implements         │                 │
│  │   EmailProvider      │  │   EmailProvider      │                 │
│  └──────────────────────┘  └──────────────────────┘                 │
│                                                                       │
│  ┌──────────────────────┐  ┌──────────────────────┐                 │
│  │ fireflyframework-notifications-   │  │ fireflyframework-notifications-   │                 │
│  │      twilio          │  │      firebase        │                 │
│  │                      │  │                      │                 │
│  │  TwilioSMSProvider   │  │   FcmPushProvider    │                 │
│  │   implements         │  │   implements         │                 │
│  │   SMSProvider        │  │   PushProvider       │                 │
│  └──────────────────────┘  └──────────────────────┘                 │
└───────────────────────────────┬───────────────────────────────────────┘
                                │
                                │ connects to
                                ▼
                    ┌──────────────────────┐
                    │ EXTERNAL SERVICES    │
                    │ (SendGrid, Twilio,   │
                    │  Resend, Firebase)   │
                    └──────────────────────┘
```

## Core Principles

### 1. Dependency Inversion Principle

High-level modules (application services) don't depend on low-level modules (adapters). Both depend on abstractions (ports/interfaces).

```java
// ✅ CORRECT: Service depends on interface (port)
@Service
public class EmailServiceImpl implements EmailService {
    @Autowired
    private EmailProvider emailProvider;  // Interface, not concrete class
    
    @Override
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        return emailProvider.sendEmail(request);
    }
}

// ❌ WRONG: Service depending on concrete adapter
@Service
public class EmailServiceImpl implements EmailService {
    @Autowired
    private SendGridEmailProvider sendGridProvider;  // Concrete class - bad!
}
```

### 2. Port Interface Segregation

Each notification type has its own port interface with a single responsibility:

- `EmailProvider` - Email delivery contract
- `SMSProvider` - SMS delivery contract  
- `PushProvider` - Push notification contract

### 3. Adapter Isolation

Each adapter is a separate Maven module that:
- Implements a port interface
- Contains provider-specific configuration
- Has no knowledge of other adapters
- Can be added/removed independently

### 4. DTO-Based Communication

All communication between layers uses immutable DTOs:
- Request DTOs: `EmailRequestDTO`, `SMSRequestDTO`, `PushNotificationRequest`
- Response DTOs: `EmailResponseDTO`, `SMSResponseDTO`, `PushNotificationResponse`

## Layer Details

### Domain Layer (Ports)

**Location**: `fireflyframework-notifications-core/src/main/java/.../interfaces`

**Responsibilities**:
- Define contracts for notification delivery (port interfaces)
- Define data structures (DTOs)
- No business logic, no infrastructure code

**Key Interfaces**:

```java
public interface EmailProvider {
    Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request);
}

public interface SMSProvider {
    SMSResponseDTO sendSMS(SMSRequestDTO request);
}

public interface PushProvider {
    Mono<PushNotificationResponse> sendPush(PushNotificationRequest request);
}
```

**Characteristics**:
- Pure interfaces with no implementation
- Technology-agnostic
- Stable contracts that rarely change
- No Spring annotations (except for service discovery in impl)

### Application Layer (Services)

**Location**: `fireflyframework-notifications-core/src/main/java/.../core/services`

**Responsibilities**:
- Orchestrate notification delivery
- Implement business rules and validation
- Error handling and logging
- Depend only on port interfaces

**Implementation Pattern**:

```java
@Service
public class EmailServiceImpl implements EmailService {
    
    @Autowired
    private EmailProvider emailProvider;  // Injected by Spring
    
    @Override
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        // Business logic here (validation, logging, etc.)
        return emailProvider.sendEmail(request);
    }
}
```

**Key Points**:
- Services are Spring-managed beans (`@Service`)
- Inject port interfaces via constructor or field injection
- Spring automatically wires the concrete adapter implementation
- Services never know which adapter is being used

### Infrastructure Layer (Adapters)

**Location**: Separate Maven modules (`fireflyframework-notifications-*`)

**Responsibilities**:
- Implement port interfaces
- Handle provider-specific API calls
- Manage authentication and configuration
- Transform DTOs to provider-specific formats

**Adapter Structure** (using SendGrid as example):

```
fireflyframework-notifications-sendgrid/
├── pom.xml                                    # Dependencies (SendGrid SDK, core)
├── README.md                                  # Adapter-specific docs
└── src/main/java/.../providers/sendgrid/
    ├── core/v1/
    │   └── SendGridEmailProvider.java        # Port implementation
    ├── config/v1/
    │   └── SendGridConfig.java               # Spring configuration
    └── properties/v1/
        └── SendGridProperties.java           # Configuration properties
```

**Adapter Implementation Example**:

```java
@Component  // Spring bean
public class SendGridEmailProvider implements EmailProvider {
    
    @Autowired
    private SendGridProperties properties;
    
    @Autowired
    private SendGrid sendGrid;
    
    @Override
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        // Provider-specific implementation
        return Mono.fromCallable(() -> {
            Mail mail = buildMail(request);
            Response response = sendGrid.api(sendGridRequest);
            return EmailResponseDTO.success(extractMessageId(response));
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    private Mail buildMail(EmailRequestDTO request) {
        // Transform DTO to SendGrid-specific format
    }
}
```

## Dependency Flow

### Compile-Time Dependencies

```
Client Application
    ↓ (depends on)
fireflyframework-notifications-core (Application + Domain)
    ↑ (implemented by)
fireflyframework-notifications-sendgrid (Adapter)
fireflyframework-notifications-resend (Adapter)
fireflyframework-notifications-twilio (Adapter)
fireflyframework-notifications-firebase (Adapter)
```

### Runtime Dependencies (Spring DI)

```
1. Spring scans for @Component/@Service beans
2. Finds EmailServiceImpl (needs EmailProvider)
3. Finds SendGridEmailProvider (implements EmailProvider)
4. Injects SendGridEmailProvider into EmailServiceImpl
5. Client code calls EmailService methods
6. Calls are routed to SendGridEmailProvider at runtime
```

### Key Insight

The **core never imports adapters**. Adapters import the core and implement its interfaces. This is the dependency inversion that makes the architecture "hexagonal."

## Adding New Providers

### Step 1: Create New Module

```bash
mvn archetype:generate \
  -DgroupId=org.fireflyframework \
  -DartifactId=fireflyframework-notifications-aws-ses \
  -DarchetypeArtifactId=maven-archetype-quickstart
```

### Step 2: Add Core Dependency

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-notifications-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 3: Implement Port Interface

```java
package org.fireflyframework.notifications.providers.awsses.core.v1;

@Component
public class AwsSesEmailProvider implements EmailProvider {
    
    @Override
    public Mono<EmailResponseDTO> sendEmail(EmailRequestDTO request) {
        // AWS SES implementation
    }
}
```

### Step 4: Add Configuration

```java
@Configuration
@ConditionalOnProperty(prefix = "aws.ses", name = "region")
public class AwsSesConfig {
    
    @Bean
    public SesClient sesClient(AwsSesProperties properties) {
        return SesClient.builder()
            .region(Region.of(properties.getRegion()))
            .build();
    }
}
```

### Step 5: Document Usage

Create `README.md` explaining configuration and usage.

That's it! No changes needed in core or other adapters.

## Testing Strategy

### Unit Testing Services (with Mock Adapters)

```java
@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {
    
    @Mock
    private EmailProvider mockProvider;
    
    @InjectMocks
    private EmailServiceImpl emailService;
    
    @Test
    void shouldSendEmailSuccessfully() {
        // Given
        EmailRequestDTO request = EmailRequestDTO.builder()
            .to(List.of("test@example.com"))
            .subject("Test")
            .text("Hello")
            .build();
        
        when(mockProvider.sendEmail(any()))
            .thenReturn(Mono.just(EmailResponseDTO.success("msg-123")));
        
        // When
        Mono<EmailResponseDTO> result = emailService.sendEmail(request);
        
        // Then
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.isSuccess() && 
                "msg-123".equals(response.getMessageId()))
            .verifyComplete();
    }
}
```

### Integration Testing Adapters

```java
@SpringBootTest
@TestPropertySource(properties = {
    "sendgrid.api-key=test-key"
})
class SendGridEmailProviderIntegrationTest {
    
    @Autowired
    private EmailProvider emailProvider;
    
    @Test
    void shouldSendRealEmail() {
        // Test with real SendGrid API (or mock server)
    }
}
```

### Testing with Multiple Providers

```java
@SpringBootTest
@TestPropertySource(properties = {
    "notifications.email.provider=sendgrid",
    "sendgrid.api-key=${SENDGRID_API_KEY}"
})
class SendGridProviderTest {
    @Autowired EmailService emailService;
    // Tests using SendGrid
}

@SpringBootTest
@TestPropertySource(properties = {
    "notifications.email.provider=resend",
    "resend.api-key=${RESEND_API_KEY}"
})
class ResendProviderTest {
    @Autowired EmailService emailService;
    // Same tests, different provider
}
```

## Design Patterns

### 1. Dependency Injection Pattern

Spring's DI container wires dependencies at runtime:

```java
// Core defines what it needs
@Service
public class EmailServiceImpl {
    private final EmailProvider provider;
    
    @Autowired  // Spring injects the implementation
    public EmailServiceImpl(EmailProvider provider) {
        this.provider = provider;
    }
}
```

### 2. Strategy Pattern

Port interfaces represent strategies for notification delivery. The application service is the context that uses these strategies without knowing their concrete implementations.

### 3. Adapter Pattern

Each infrastructure module is literally an adapter that:
- Adapts the port interface to a specific provider's API
- Translates between DTOs and provider-specific formats

### 4. Factory Pattern (Spring Bean Factory)

Spring acts as a factory that creates and manages adapter instances:

```java
@Configuration
public class SendGridConfig {
    @Bean
    public SendGrid sendGrid(SendGridProperties properties) {
        return new SendGrid(properties.getApiKey());
    }
}
```

### 5. Builder Pattern

DTOs use builders for immutable object construction:

```java
EmailRequestDTO request = EmailRequestDTO.builder()
    .from("sender@example.com")
    .to(List.of("recipient@example.com"))
    .subject("Hello")
    .text("World")
    .build();
```

## Benefits Recap

| Benefit | Description |
|---------|-------------|
| **Testability** | Mock port interfaces to test services without real providers |
| **Flexibility** | Swap providers by changing Maven dependencies and configuration |
| **Maintainability** | Clear boundaries make code easier to understand and modify |
| **Scalability** | Add new providers without modifying existing code (Open/Closed Principle) |
| **Independence** | Core business logic has zero knowledge of infrastructure details |
| **Reusability** | Core and adapters can be reused across multiple projects |

## References

- [Hexagonal Architecture by Alistair Cockburn](https://alistair.cockburn.us/hexagonal-architecture/)
- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Dependency Inversion Principle](https://en.wikipedia.org/wiki/Dependency_inversion_principle)
- [Spring Framework Dependency Injection](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html)
