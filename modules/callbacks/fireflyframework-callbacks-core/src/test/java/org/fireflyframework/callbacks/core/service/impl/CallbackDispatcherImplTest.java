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

package org.fireflyframework.callbacks.core.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.fireflyframework.callbacks.core.mapper.CallbackExecutionMapper;
import org.fireflyframework.callbacks.core.service.CallbackConfigurationService;
import org.fireflyframework.callbacks.core.service.DomainAuthorizationService;
import org.fireflyframework.callbacks.interfaces.dto.CallbackConfigurationDTO;
import org.fireflyframework.callbacks.interfaces.enums.HttpMethod;
import org.fireflyframework.callbacks.models.entity.CallbackExecution;
import org.fireflyframework.callbacks.models.repository.CallbackExecutionRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CallbackDispatcherImpl.
 */
@ExtendWith(MockitoExtension.class)
class CallbackDispatcherImplTest {

    @Mock
    private CallbackExecutionRepository executionRepository;

    @Mock
    private CallbackExecutionMapper executionMapper;

    @Mock
    private CallbackConfigurationService configurationService;

    @Mock
    private DomainAuthorizationService domainAuthorizationService;

    private CallbackDispatcherImpl dispatcher;
    private ObjectMapper objectMapper;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setupClass() {
        wireMockServer = new WireMockServer(0); // dynamic port to avoid clashes with other local servers
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void tearDownClass() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        dispatcher = new CallbackDispatcherImpl(
                webClientBuilder,
                executionRepository,
                executionMapper,
                configurationService,
                domainAuthorizationService,
                objectMapper
        );

        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Should successfully dispatch callback with HMAC signature")
    void testSuccessfulCallbackWithHMAC() {
        // Setup
        String url = "http://localhost:" + wireMockServer.port() + "/webhook";
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
                .id(UUID.randomUUID())
                .url(url)
                .httpMethod(HttpMethod.POST)
                .signatureEnabled(true)
                .secret("test-secret")
                .maxRetries(3)
                .timeoutMs(5000)
                .build();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("test", "data");

        // Mock domain authorization
        when(domainAuthorizationService.isAuthorized(url)).thenReturn(Mono.just(true));
        when(domainAuthorizationService.recordCallback(anyString(), anyBoolean())).thenReturn(Mono.empty());
        when(configurationService.recordSuccess(any())).thenReturn(Mono.empty());
        when(executionRepository.save(any())).thenAnswer(invocation -> 
            Mono.just(invocation.getArgument(0, CallbackExecution.class)));

        // Setup WireMock
        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        // Execute
        Mono<Void> result = dispatcher.dispatch(
                config,
                "test.event",
                UUID.randomUUID(),
                payload
        );

        // Verify
        StepVerifier.create(result)
                .verifyComplete();

        // Verify callback was called with signature
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("X-Signature", matching(".*")));
    }

    @Test
    @DisplayName("Should fail callback for unauthorized domain")
    void testUnauthorizedDomainFails() {
        // Setup
        String url = "http://unauthorized-domain.com/webhook";
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
                .id(UUID.randomUUID())
                .url(url)
                .httpMethod(HttpMethod.POST)
                .maxRetries(3)
                .build();

        ObjectNode payload = objectMapper.createObjectNode();

        // Mock domain authorization - returns false
        when(domainAuthorizationService.isAuthorized(url)).thenReturn(Mono.just(false));
        when(executionRepository.save(any())).thenAnswer(invocation -> 
            Mono.just(invocation.getArgument(0, CallbackExecution.class)));

        // Execute
        Mono<Void> result = dispatcher.dispatch(
                config,
                "test.event",
                UUID.randomUUID(),
                payload
        );

        // Verify - should error with SecurityException
        StepVerifier.create(result)
                .expectError(SecurityException.class)
                .verify();
    }

    @Test
    @DisplayName("Should retry failed callback")
    void testCallbackRetry() {
        // Setup
        String url = "http://localhost:" + wireMockServer.port() + "/webhook";
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
                .id(UUID.randomUUID())
                .url(url)
                .httpMethod(HttpMethod.POST)
                .maxRetries(3)
                .retryDelayMs(100)
                .timeoutMs(5000)
                .build();

        ObjectNode payload = objectMapper.createObjectNode();

        // Mock domain authorization
        when(domainAuthorizationService.isAuthorized(url)).thenReturn(Mono.just(true));
        when(domainAuthorizationService.recordCallback(anyString(), anyBoolean())).thenReturn(Mono.empty());
        when(configurationService.recordFailure(any())).thenReturn(Mono.empty());
        when(executionRepository.save(any())).thenAnswer(invocation -> 
            Mono.just(invocation.getArgument(0, CallbackExecution.class)));

        // Setup WireMock - first fail, then succeed
        stubFor(post(urlEqualTo("/webhook"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("First Retry"));

        stubFor(post(urlEqualTo("/webhook"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("First Retry")
                .willReturn(aResponse().withStatus(200)));

        // Execute
        Mono<Void> result = dispatcher.dispatch(
                config,
                "test.event",
                UUID.randomUUID(),
                payload
        );

        // Verify - should eventually succeed after retry
        StepVerifier.create(result)
                .verifyComplete();

        // Verify multiple attempts were made (at least 2)
        verify(moreThanOrExactly(2), postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    @DisplayName("Should include custom headers in callback")
    void testCustomHeaders() {
        // Setup
        String url = "http://localhost:" + wireMockServer.port() + "/webhook";
        CallbackConfigurationDTO config = CallbackConfigurationDTO.builder()
                .id(UUID.randomUUID())
                .url(url)
                .httpMethod(HttpMethod.POST)
                .customHeaders(java.util.Map.of(
                    "X-Custom-Header", "custom-value",
                    "X-API-Key", "api-key-123"
                ))
                .maxRetries(1)
                .timeoutMs(5000)
                .build();

        ObjectNode payload = objectMapper.createObjectNode();

        // Mock
        when(domainAuthorizationService.isAuthorized(url)).thenReturn(Mono.just(true));
        when(domainAuthorizationService.recordCallback(anyString(), anyBoolean())).thenReturn(Mono.empty());
        when(configurationService.recordSuccess(any())).thenReturn(Mono.empty());
        when(executionRepository.save(any())).thenAnswer(invocation -> 
            Mono.just(invocation.getArgument(0, CallbackExecution.class)));

        // Setup WireMock
        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        // Execute
        Mono<Void> result = dispatcher.dispatch(
                config,
                "test.event",
                UUID.randomUUID(),
                payload
        );

        // Verify
        StepVerifier.create(result)
                .verifyComplete();

        // Verify custom headers were included
        verify(postRequestedFor(urlEqualTo("/webhook"))
                .withHeader("X-Custom-Header", equalTo("custom-value"))
                .withHeader("X-API-Key", equalTo("api-key-123")));
    }
}
