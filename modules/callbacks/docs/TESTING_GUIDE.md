# Testing Guide

> **Comprehensive testing strategies and examples for the Firefly Framework Callbacks Library**

## Table of Contents

- [Overview](#overview)
- [Testing Strategy](#testing-strategy)
- [Running Tests](#running-tests)
- [Unit Testing](#unit-testing)
- [Integration Testing](#integration-testing)
- [End-to-End Testing](#end-to-end-testing)
- [Test Coverage](#test-coverage)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Overview

The Firefly Framework Callbacks Library uses a comprehensive testing strategy with three levels of tests:

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test component interactions with real dependencies (using Testcontainers)
3. **End-to-End Tests**: Test complete workflows from Kafka to HTTP callbacks

### Testing Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Test Framework** | JUnit 5 | Test execution and assertions |
| **Reactive Testing** | Reactor Test (StepVerifier) | Testing reactive streams |
| **Mocking** | Mockito | Mocking dependencies |
| **Containers** | Testcontainers | Docker-based integration testing |
| **HTTP Mocking** | WireMock | Mocking external HTTP services |
| **Database** | PostgreSQL (Testcontainers) | Real database for integration tests |
| **Messaging** | Kafka (Testcontainers) | Real Kafka for integration tests |
| **Coverage** | JaCoCo | Code coverage reporting |

## Testing Strategy

### Test Pyramid

```
                    ┌─────────────┐
                    │     E2E     │  ← Few, slow, high confidence
                    │   (5 tests) │
                    └─────────────┘
                  ┌─────────────────┐
                  │  Integration    │  ← Some, medium speed
                  │   (30 tests)    │
                  └─────────────────┘
              ┌───────────────────────┐
              │      Unit Tests       │  ← Many, fast, focused
              │     (100+ tests)      │
              └───────────────────────┘
```

### What to Test at Each Level

**Unit Tests**:
- Service layer business logic
- MapStruct mappers
- Filter utilities
- HMAC signature generation
- Domain extraction logic
- Retry condition evaluation

**Integration Tests**:
- Repository queries with real PostgreSQL
- R2DBC entity mapping
- Database transactions
- Flyway migrations
- Kafka message consumption
- HTTP client interactions

**End-to-End Tests**:
- Complete event flow: Kafka → Processing → HTTP Callback → Database
- Circuit breaker behavior
- Retry logic with exponential backoff
- Domain authorization flow
- HMAC signature verification

## Running Tests

### Run All Tests

```bash
# From project root
mvn test

# With coverage report
mvn clean test jacoco:report
```

### Run Specific Test Class

```bash
mvn test -Dtest=CallbackConfigurationServiceTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=CallbackConfigurationServiceTest#testFilterConfigurations
```

### Run Tests in Specific Module

```bash
# Core module tests
mvn test -pl fireflyframework-callbacks-core

# Web module tests (includes integration tests)
mvn test -pl fireflyframework-callbacks-web
```

### Run Only Integration Tests

```bash
mvn test -Dtest=*IntegrationTest
```

### Run Only End-to-End Tests

```bash
mvn test -Dtest=*EndToEndTest
```

### Skip Tests

```bash
mvn clean install -DskipTests
```

### Run Tests with Debug Logging

```bash
mvn test -Dlogging.level.org.fireflyframework.callbacks=DEBUG
```

## Unit Testing

Unit tests focus on testing individual components in isolation using mocks.

### Example: Service Layer Test

**File**: `CallbackConfigurationServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class CallbackConfigurationServiceTest {

    @Mock
    private CallbackConfigurationRepository repository;

    @Mock
    private CallbackConfigurationMapper mapper;

    @InjectMocks
    private CallbackConfigurationServiceImpl service;

    @Test
    void testFilterConfigurations() {
        // Given
        FilterRequest<CallbackConfigurationDTO> request = FilterRequest.<CallbackConfigurationDTO>builder()
            .page(0)
            .size(10)
            .build();

        CallbackConfiguration entity = new CallbackConfiguration();
        entity.setId(UUID.randomUUID());
        entity.setName("Test Config");

        CallbackConfigurationDTO dto = new CallbackConfigurationDTO();
        dto.setId(entity.getId());
        dto.setName("Test Config");

        when(repository.findAllBy(any(Pageable.class)))
            .thenReturn(Flux.just(entity));
        when(repository.count())
            .thenReturn(Mono.just(1L));
        when(mapper.toDto(entity))
            .thenReturn(dto);

        // When
        Mono<PaginationResponse<CallbackConfigurationDTO>> result = 
            service.filterConfigurations(request);

        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getContent()).hasSize(1);
                assertThat(response.getTotalElements()).isEqualTo(1L);
                assertThat(response.getContent().get(0).getName()).isEqualTo("Test Config");
            })
            .verifyComplete();

        verify(repository).findAllBy(any(Pageable.class));
        verify(repository).count();
        verify(mapper).toDto(entity);
    }

    @Test
    void testCreateConfiguration() {
        // Given
        CallbackConfigurationDTO dto = CallbackConfigurationDTO.builder()
            .name("New Config")
            .url("https://example.com/webhook")
            .httpMethod(HttpMethod.POST)
            .subscribedEventTypes(new String[]{"customer.created"})
            .build();

        CallbackConfiguration entity = new CallbackConfiguration();
        entity.setName("New Config");

        CallbackConfiguration savedEntity = new CallbackConfiguration();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setName("New Config");

        when(mapper.toEntity(dto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(Mono.just(savedEntity));
        when(mapper.toDto(savedEntity)).thenReturn(dto);

        // When
        Mono<CallbackConfigurationDTO> result = service.create(dto);

        // Then
        StepVerifier.create(result)
            .assertNext(created -> {
                assertThat(created.getName()).isEqualTo("New Config");
            })
            .verifyComplete();
    }
}
```

### Example: Mapper Test

**File**: `CallbackConfigurationMapperTest.java`

```java
@SpringBootTest
class CallbackConfigurationMapperTest {

    @Autowired
    private CallbackConfigurationMapper mapper;

    @Test
    void testToDto() {
        // Given
        CallbackConfiguration entity = new CallbackConfiguration();
        entity.setId(UUID.randomUUID());
        entity.setName("Test Config");
        entity.setUrl("https://example.com/webhook");
        entity.setHttpMethod("POST");
        entity.setStatus("ACTIVE");
        entity.setSubscribedEventTypes(new String[]{"customer.created"});

        // When
        CallbackConfigurationDTO dto = mapper.toDto(entity);

        // Then
        assertThat(dto.getId()).isEqualTo(entity.getId());
        assertThat(dto.getName()).isEqualTo(entity.getName());
        assertThat(dto.getUrl()).isEqualTo(entity.getUrl());
        assertThat(dto.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(dto.getStatus()).isEqualTo(CallbackStatus.ACTIVE);
        assertThat(dto.getSubscribedEventTypes()).containsExactly("customer.created");
    }

    @Test
    void testToEntity() {
        // Given
        CallbackConfigurationDTO dto = CallbackConfigurationDTO.builder()
            .name("Test Config")
            .url("https://example.com/webhook")
            .httpMethod(HttpMethod.POST)
            .status(CallbackStatus.ACTIVE)
            .subscribedEventTypes(new String[]{"customer.created"})
            .build();

        // When
        CallbackConfiguration entity = mapper.toEntity(dto);

        // Then
        assertThat(entity.getName()).isEqualTo(dto.getName());
        assertThat(entity.getUrl()).isEqualTo(dto.getUrl());
        assertThat(entity.getHttpMethod()).isEqualTo("POST");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getSubscribedEventTypes()).containsExactly("customer.created");
    }
}
```

### Example: Utility Test

**File**: `FilterUtilsTest.java`

```java
class FilterUtilsTest {

    @Test
    void testCreateFilterWithPagination() {
        // Given
        FilterRequest<CallbackConfigurationDTO> request = FilterRequest.<CallbackConfigurationDTO>builder()
            .page(1)
            .size(20)
            .build();

        // When
        Pageable pageable = FilterUtils.createPageable(request);

        // Then
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void testCreateFilterWithSorting() {
        // Given
        FilterRequest<CallbackConfigurationDTO> request = FilterRequest.<CallbackConfigurationDTO>builder()
            .page(0)
            .size(10)
            .sort(List.of("name,ASC", "createdAt,DESC"))
            .build();

        // When
        Pageable pageable = FilterUtils.createPageable(request);

        // Then
        assertThat(pageable.getSort().isSorted()).isTrue();
        assertThat(pageable.getSort().getOrderFor("name").getDirection())
            .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
            .isEqualTo(Sort.Direction.DESC);
    }
}
```

## Integration Testing

Integration tests use Testcontainers to run real PostgreSQL and Kafka instances in Docker.

### Setup: Base Integration Test Class

**File**: `BaseIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
        .withDatabaseName("callbacks_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database
        registry.add("spring.r2dbc.url", () -> 
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + 
            "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        // Kafka
        registry.add("eda.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

### Example: Repository Integration Test

**File**: `CallbackConfigurationRepositoryIntegrationTest.java`

```java
class CallbackConfigurationRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CallbackConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll().block();
    }

    @Test
    void testSaveAndFindById() {
        // Given
        CallbackConfiguration config = new CallbackConfiguration();
        config.setName("Test Config");
        config.setUrl("https://example.com/webhook");
        config.setHttpMethod("POST");
        config.setStatus("ACTIVE");
        config.setSubscribedEventTypes(new String[]{"customer.created"});
        config.setActive(true);

        // When
        CallbackConfiguration saved = repository.save(config).block();
        CallbackConfiguration found = repository.findById(saved.getId()).block();

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Test Config");
        assertThat(found.getUrl()).isEqualTo("https://example.com/webhook");
    }

    @Test
    void testFindActiveByEventType() {
        // Given
        CallbackConfiguration config1 = createConfig("Config 1", new String[]{"customer.created"});
        CallbackConfiguration config2 = createConfig("Config 2", new String[]{"customer.updated"});
        CallbackConfiguration config3 = createConfig("Config 3", new String[]{"customer.created", "customer.updated"});

        repository.saveAll(List.of(config1, config2, config3)).blockLast();

        // When
        List<CallbackConfiguration> results = repository
            .findActiveByEventType("customer.created")
            .collectList()
            .block();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting("name")
            .containsExactlyInAnyOrder("Config 1", "Config 3");
    }

    private CallbackConfiguration createConfig(String name, String[] eventTypes) {
        CallbackConfiguration config = new CallbackConfiguration();
        config.setName(name);
        config.setUrl("https://example.com/webhook");
        config.setHttpMethod("POST");
        config.setStatus("ACTIVE");
        config.setSubscribedEventTypes(eventTypes);
        config.setActive(true);
        return config;
    }
}
```

### Example: Service Integration Test

**File**: `CallbackConfigurationServiceIntegrationTest.java`

```java
class CallbackConfigurationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CallbackConfigurationService service;

    @Autowired
    private CallbackConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll().block();
    }

    @Test
    void testFilterConfigurationsWithPagination() {
        // Given - Create 25 configurations
        List<CallbackConfiguration> configs = IntStream.range(0, 25)
            .mapToObj(i -> createConfig("Config " + i))
            .collect(Collectors.toList());
        repository.saveAll(configs).blockLast();

        FilterRequest<CallbackConfigurationDTO> request = FilterRequest.<CallbackConfigurationDTO>builder()
            .page(0)
            .size(10)
            .build();

        // When
        PaginationResponse<CallbackConfigurationDTO> response = 
            service.filterConfigurations(request).block();

        // Then
        assertThat(response.getContent()).hasSize(10);
        assertThat(response.getTotalElements()).isEqualTo(25L);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getFirst()).isTrue();
        assertThat(response.getLast()).isFalse();
    }

    @Test
    void testFilterConfigurationsWithSorting() {
        // Given
        CallbackConfiguration config1 = createConfig("Zebra");
        CallbackConfiguration config2 = createConfig("Alpha");
        CallbackConfiguration config3 = createConfig("Beta");
        repository.saveAll(List.of(config1, config2, config3)).blockLast();

        FilterRequest<CallbackConfigurationDTO> request = FilterRequest.<CallbackConfigurationDTO>builder()
            .page(0)
            .size(10)
            .sort(List.of("name,ASC"))
            .build();

        // When
        PaginationResponse<CallbackConfigurationDTO> response = 
            service.filterConfigurations(request).block();

        // Then
        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getContent())
            .extracting("name")
            .containsExactly("Alpha", "Beta", "Zebra");
    }

    private CallbackConfiguration createConfig(String name) {
        CallbackConfiguration config = new CallbackConfiguration();
        config.setName(name);
        config.setUrl("https://example.com/webhook");
        config.setHttpMethod("POST");
        config.setStatus("ACTIVE");
        config.setSubscribedEventTypes(new String[]{"customer.created"});
        config.setActive(true);
        return config;
    }
}
```

## End-to-End Testing

End-to-end tests verify the complete flow from Kafka event to HTTP callback.

### Example: Complete E2E Test

**File**: `CallbackManagementEndToEndTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CallbackManagementEndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
        .withDatabaseName("callbacks_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    static WireMockServer wireMockServer;

    @Autowired
    private EventSubscriptionService subscriptionService;

    @Autowired
    private AuthorizedDomainService domainService;

    @Autowired
    private CallbackConfigurationService configurationService;

    @Autowired
    private CallbackExecutionService executionService;

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> 
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + 
            "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("eda.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    @Order(1)
    void testEndToEndCallbackFlow() throws Exception {
        // 1. Setup WireMock stub
        stubFor(post(urlEqualTo("/webhook"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\":\"received\"}")));

        // 2. Create authorized domain
        AuthorizedDomainDTO domain = AuthorizedDomainDTO.builder()
            .domain("localhost:8089")
            .verified(true)
            .active(true)
            .build();
        AuthorizedDomainDTO createdDomain = domainService.create(domain).block();
        assertThat(createdDomain.getId()).isNotNull();

        // 3. Create event subscription
        EventSubscriptionDTO subscription = EventSubscriptionDTO.builder()
            .name("Test Subscription")
            .messagingSystemType("KAFKA")
            .topicOrQueue("test.events")
            .consumerGroupId("test-consumer")
            .eventTypePatterns(new String[]{"customer.*"})
            .active(true)
            .build();
        EventSubscriptionDTO createdSubscription = subscriptionService.create(subscription).block();
        assertThat(createdSubscription.getId()).isNotNull();

        // 4. Create callback configuration
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
            .name("Test Callback")
            .url("http://localhost:8089/webhook")
            .httpMethod(HttpMethod.POST)
            .subscribedEventTypes(new String[]{"customer.created"})
            .status(CallbackStatus.ACTIVE)
            .signatureEnabled(true)
            .secret("test-secret")
            .maxRetries(3)
            .retryDelayMs(1000)
            .timeoutMs(5000)
            .active(true)
            .build();
        CallbackConfigurationDTO createdConfig = configurationService.create(config).block();
        assertThat(createdConfig.getId()).isNotNull();

        // 5. Publish event to Kafka
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String eventId = UUID.randomUUID().toString();
            String event = String.format("""
                {
                    "eventType": "customer.created",
                    "eventId": "%s",
                    "timestamp": "2025-01-15T10:00:00Z",
                    "payload": {
                        "customerId": "CUST-001",
                        "firstName": "John",
                        "lastName": "Doe"
                    }
                }
                """, eventId);

            ProducerRecord<String, String> record = new ProducerRecord<>("test.events", event);
            producer.send(record).get();
        }

        // 6. Wait for callback execution
        Thread.sleep(3000);

        // 7. Verify WireMock received the request
        verify(postRequestedFor(urlEqualTo("/webhook"))
            .withHeader("X-Event-Type", equalTo("customer.created"))
            .withHeader("X-Firefly-Signature", matching(".*")));

        // 8. Verify execution was recorded
        FilterRequest<CallbackExecutionDTO> executionFilter = FilterRequest.<CallbackExecutionDTO>builder()
            .page(0)
            .size(10)
            .build();
        PaginationResponse<CallbackExecutionDTO> executions = 
            executionService.filterExecutions(executionFilter).block();

        assertThat(executions.getContent()).isNotEmpty();
        CallbackExecutionDTO execution = executions.getContent().get(0);
        assertThat(execution.getEventType()).isEqualTo("customer.created");
        assertThat(execution.getStatus()).isEqualTo(CallbackExecutionStatus.SUCCESS);
        assertThat(execution.getResponseStatusCode()).isEqualTo(200);
    }

    @Test
    @Order(2)
    void testRetryOnFailure() throws Exception {
        // 1. Setup WireMock to fail twice, then succeed
        stubFor(post(urlEqualTo("/webhook-retry"))
            .inScenario("Retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("First Retry"));

        stubFor(post(urlEqualTo("/webhook-retry"))
            .inScenario("Retry")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("Second Retry"));

        stubFor(post(urlEqualTo("/webhook-retry"))
            .inScenario("Retry")
            .whenScenarioStateIs("Second Retry")
            .willReturn(aResponse().withStatus(200)));

        // 2. Create callback configuration
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
            .name("Retry Test Callback")
            .url("http://localhost:8089/webhook-retry")
            .httpMethod(HttpMethod.POST)
            .subscribedEventTypes(new String[]{"customer.updated"})
            .status(CallbackStatus.ACTIVE)
            .maxRetries(3)
            .retryDelayMs(500)
            .active(true)
            .build();
        configurationService.create(config).block();

        // 3. Publish event
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String event = """
                {
                    "eventType": "customer.updated",
                    "eventId": "%s",
                    "payload": {"customerId": "CUST-002"}
                }
                """.formatted(UUID.randomUUID());

            producer.send(new ProducerRecord<>("test.events", event)).get();
        }

        // 4. Wait for retries
        Thread.sleep(5000);

        // 5. Verify 3 attempts were made
        verify(exactly(3), postRequestedFor(urlEqualTo("/webhook-retry")));
    }
}
```

## Test Coverage

### Generate Coverage Report

```bash
mvn clean test jacoco:report
```

### View Coverage Report

Open `target/site/jacoco/index.html` in your browser.

### Coverage Goals

| Component | Target Coverage |
|-----------|----------------|
| Service Layer | > 90% |
| Repository Layer | > 80% |
| Controllers | > 85% |
| Mappers | > 95% |
| Utilities | > 90% |
| Overall | > 85% |

### Check Coverage Threshold

Add to `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Best Practices

### 1. Use StepVerifier for Reactive Tests

```java
// ✅ Good
StepVerifier.create(service.findById(id))
    .assertNext(result -> assertThat(result.getName()).isEqualTo("Test"))
    .verifyComplete();

// ❌ Bad
CallbackConfigurationDTO result = service.findById(id).block();
assertThat(result.getName()).isEqualTo("Test");
```

### 2. Clean Up Test Data

```java
@BeforeEach
void setUp() {
    repository.deleteAll().block();
}
```

### 3. Use Test Builders

```java
// ✅ Good
CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
    .name("Test")
    .url("https://example.com")
    .build();

// ❌ Bad
CallbackConfigurationDTO config = new CallbackConfigurationDTO();
config.setName("Test");
config.setUrl("https://example.com");
```

### 4. Test Edge Cases

```java
@Test
void testFilterWithEmptyResult() {
    // Test empty results
}

@Test
void testFilterWithNullFilters() {
    // Test null handling
}

@Test
void testFilterWithInvalidPage() {
    // Test validation
}
```

### 5. Use Descriptive Test Names

```java
// ✅ Good
@Test
void shouldReturnPaginatedResultsWhenFilteringConfigurations() { }

// ❌ Bad
@Test
void test1() { }
```

## Troubleshooting

### Issue: Testcontainers not starting

**Error**: `Could not find a valid Docker environment`

**Solution**: Ensure Docker is running
```bash
docker ps
```

---

**Error**: `Port 5432 already in use`

**Solution**: Stop local PostgreSQL or use different port
```bash
brew services stop postgresql
```

### Issue: Tests timing out

**Solution**: Increase timeout
```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
void testLongRunningOperation() { }
```

### Issue: Flaky tests

**Causes**:
- Race conditions in async code
- Insufficient wait times
- Shared state between tests

**Solutions**:
```java
// Use StepVerifier with timeout
StepVerifier.create(mono)
    .expectNext(expected)
    .expectComplete()
    .verify(Duration.ofSeconds(5));

// Use Awaitility for polling
await().atMost(5, SECONDS)
    .until(() -> repository.count().block() == 1);
```

---

**For more information, see:**
- [Architecture Documentation](ARCHITECTURE.md)
- [Quickstart Guide](QUICKSTART_GUIDE.md)
- [Main README](../README.md)

---

**© 2025 Firefly Software Foundation. All rights reserved.**

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

