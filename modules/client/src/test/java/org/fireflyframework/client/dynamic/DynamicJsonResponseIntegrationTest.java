/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.client.dynamic;

import org.fireflyframework.client.RestClient;
import org.fireflyframework.client.ServiceClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DynamicJsonResponse with RestClient.
 * 
 * This test demonstrates:
 * - Full reactive integration (no .block() calls in production code)
 * - Working with DynamicJsonResponse in Mono/Flux pipelines
 * - Dynamic class generation
 * - Real-world usage patterns
 */
@DisplayName("DynamicJsonResponse Integration Tests")
class DynamicJsonResponseIntegrationTest {

    private static WireMockServer wireMockServer;
    private static String baseUrl;
    private RestClient client;

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        baseUrl = "http://localhost:" + wireMockServer.port();
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        client = ServiceClient.rest("test-service")
            .baseUrl(baseUrl)
            .build();
    }

    @Test
    @DisplayName("Should work reactively without .block() - Simple field access")
    void shouldWorkReactivelyWithoutBlock() {
        // Given: A mock user response
        String jsonResponse = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com",
                "age": 30
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Making a reactive request
        Mono<String> userName = client.get("/users/123", DynamicJsonResponse.class)
            .execute()
            .map(response -> response.getString("name"));

        // Then: Verify using StepVerifier (reactive testing)
        StepVerifier.create(userName)
            .expectNext("John Doe")
            .verifyComplete();
    }

    @Test
    @DisplayName("Should access nested fields reactively")
    void shouldAccessNestedFieldsReactively() {
        // Given
        String jsonResponse = """
            {
                "user": {
                    "profile": {
                        "address": {
                            "city": "New York",
                            "country": "USA"
                        }
                    }
                }
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Access nested field with dot notation
        Mono<String> city = client.get("/users/123", DynamicJsonResponse.class)
            .execute()
            .map(response -> response.getString("user.profile.address.city"));

        // Then
        StepVerifier.create(city)
            .expectNext("New York")
            .verifyComplete();
    }

    @Test
    @DisplayName("Should chain multiple reactive operations")
    void shouldChainMultipleReactiveOperations() {
        // Given
        String jsonResponse = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com",
                "active": true
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Chain multiple operations
        Mono<String> result = client.get("/users/123", DynamicJsonResponse.class)
            .execute()
            .filter(response -> response.getBoolean("active"))
            .map(response -> response.getString("name"))
            .map(String::toUpperCase)
            .defaultIfEmpty("INACTIVE USER");

        // Then
        StepVerifier.create(result)
            .expectNext("JOHN DOE")
            .verifyComplete();
    }

    @Test
    @DisplayName("Should work with arrays using Flux")
    void shouldWorkWithArraysUsingFlux() {
        // Given
        String jsonResponse = """
            {
                "users": [
                    {"id": 1, "name": "Alice"},
                    {"id": 2, "name": "Bob"},
                    {"id": 3, "name": "Charlie"}
                ]
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Convert array to Flux
        Flux<String> userNames = client.get("/users", DynamicJsonResponse.class)
            .execute()
            .flatMapMany(response -> response.toFlux("users"))
            .map(user -> user.getString("name"));

        // Then
        StepVerifier.create(userNames)
            .expectNext("Alice", "Bob", "Charlie")
            .verifyComplete();
    }

    @Test
    @DisplayName("Should work with typed arrays using Flux")
    void shouldWorkWithTypedArraysUsingFlux() {
        // Given
        String jsonResponse = """
            {
                "roles": ["admin", "user", "moderator"]
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123/roles"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Convert typed array to Flux
        Flux<String> roles = client.get("/users/123/roles", DynamicJsonResponse.class)
            .execute()
            .flatMapMany(response -> response.toFlux("roles", String.class));

        // Then
        StepVerifier.create(roles)
            .expectNext("admin", "user", "moderator")
            .verifyComplete();
    }

    @Test
    @DisplayName("Should use DynamicObject for DTO-like access")
    void shouldUseDynamicObjectForDtoLikeAccess() {
        // Given
        String jsonResponse = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com",
                "age": 30,
                "active": true
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Use DynamicObject
        Mono<DynamicObject> dynamicUser = client.get("/users/123", DynamicJsonResponse.class)
            .execute()
            .map(DynamicJsonResponse::toDynamicObject);

        // Then: Verify we can access fields like a DTO
        StepVerifier.create(dynamicUser)
            .assertNext(user -> {
                assertThat(user.getString("name")).isEqualTo("John Doe");
                assertThat(user.getInt("age")).isEqualTo(30);
                assertThat(user.getBoolean("active")).isTrue();
                assertThat(user.has("email")).isTrue();
                assertThat(user.has("nonexistent")).isFalse();
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should generate dynamic class at runtime")
    void shouldGenerateDynamicClassAtRuntime() {
        // Given
        String jsonResponse = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com"
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Generate dynamic class
        Mono<Object> dynamicInstance = client.get("/users/123", DynamicJsonResponse.class)
            .execute()
            .map(response -> response.toDynamicClass("User"));

        // Then: Verify the dynamic class works
        StepVerifier.create(dynamicInstance)
            .assertNext(instance -> {
                assertThat(instance).isNotNull();
                assertThat(instance).isInstanceOf(DynamicClassGenerator.DynamicDTO.class);
                
                DynamicClassGenerator.DynamicDTO dto = (DynamicClassGenerator.DynamicDTO) instance;
                assertThat(dto.get("name")).isEqualTo("John Doe");
                assertThat(dto.get("email")).isEqualTo("john@example.com");
                assertThat(dto.getClassName()).isEqualTo("User");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should handle conditional processing reactively")
    void shouldHandleConditionalProcessingReactively() {
        // Given
        String jsonResponse = """
            {
                "id": 123,
                "name": "John Doe",
                "email": "john@example.com",
                "premium": true
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(jsonResponse)));

        // When: Conditional processing
        Mono<String> message = client.get("/users/123", DynamicJsonResponse.class)
            .execute()
            .flatMap(response -> {
                if (response.getBoolean("premium")) {
                    return Mono.just("Premium user: " + response.getString("name"));
                } else {
                    return Mono.just("Regular user: " + response.getString("name"));
                }
            });

        // Then
        StepVerifier.create(message)
            .expectNext("Premium user: John Doe")
            .verifyComplete();
    }

    @Test
    @DisplayName("Should combine multiple API calls reactively")
    void shouldCombineMultipleApiCallsReactively() {
        // Given: Two different endpoints
        String userResponse = """
            {
                "id": 123,
                "name": "John Doe"
            }
            """;

        String profileResponse = """
            {
                "userId": 123,
                "bio": "Software Developer"
            }
            """;

        wireMockServer.stubFor(get(urlEqualTo("/users/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(userResponse)));

        wireMockServer.stubFor(get(urlEqualTo("/profiles/123"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(profileResponse)));

        // When: Combine two API calls
        Mono<String> combined = Mono.zip(
            client.get("/users/123", DynamicJsonResponse.class).execute(),
            client.get("/profiles/123", DynamicJsonResponse.class).execute()
        ).map(tuple -> {
            String name = tuple.getT1().getString("name");
            String bio = tuple.getT2().getString("bio");
            return name + " - " + bio;
        });

        // Then
        StepVerifier.create(combined)
            .expectNext("John Doe - Software Developer")
            .verifyComplete();
    }
}

