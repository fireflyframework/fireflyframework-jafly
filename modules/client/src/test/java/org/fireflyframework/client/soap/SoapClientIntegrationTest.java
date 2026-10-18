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

package org.fireflyframework.client.soap;

import org.fireflyframework.client.ClientType;
import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.SoapClient;
import org.fireflyframework.client.exception.SoapFaultException;
import org.fireflyframework.client.soap.model.CalculatorRequest;
import org.fireflyframework.client.soap.model.CalculatorResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.xml.namespace.QName;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for SOAP client with real mock WSDL service.
 *
 * <p>Uses WireMock to simulate a SOAP service with WSDL.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@DisplayName("SOAP Client Integration Tests")
class SoapClientIntegrationTest {

    private static WireMockServer wireMockServer;
    private static String baseUrl;
    private static String wsdlUrl;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        baseUrl = "http://localhost:" + wireMockServer.port();
        wsdlUrl = baseUrl + "/calculator.asmx?WSDL";

        // Setup WSDL response
        setupWsdlMock();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetMappings();
        setupWsdlMock();
    }

    private static void setupWsdlMock() {
        String wsdlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <wsdl:definitions xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                              xmlns:tns="http://tempuri.org/"
                              xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                              targetNamespace="http://tempuri.org/">
                <wsdl:types>
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                               targetNamespace="http://tempuri.org/"
                               elementFormDefault="unqualified">
                        <xs:element name="Add">
                            <xs:complexType>
                                <xs:sequence>
                                    <xs:element name="intA" type="xs:int"/>
                                    <xs:element name="intB" type="xs:int"/>
                                </xs:sequence>
                            </xs:complexType>
                        </xs:element>
                        <xs:element name="AddResponse">
                            <xs:complexType>
                                <xs:sequence>
                                    <xs:element name="AddResult" type="xs:int"/>
                                </xs:sequence>
                            </xs:complexType>
                        </xs:element>
                    </xs:schema>
                </wsdl:types>
                <wsdl:message name="AddSoapIn">
                    <wsdl:part name="parameters" element="tns:Add"/>
                </wsdl:message>
                <wsdl:message name="AddSoapOut">
                    <wsdl:part name="parameters" element="tns:AddResponse"/>
                </wsdl:message>
                <wsdl:portType name="CalculatorSoap">
                    <wsdl:operation name="Add">
                        <wsdl:input message="tns:AddSoapIn"/>
                        <wsdl:output message="tns:AddSoapOut"/>
                    </wsdl:operation>
                </wsdl:portType>
                <wsdl:binding name="CalculatorSoap" type="tns:CalculatorSoap">
                    <soap:binding transport="http://schemas.xmlsoap.org/soap/http"/>
                    <wsdl:operation name="Add">
                        <soap:operation soapAction="http://tempuri.org/Add" style="document"/>
                        <wsdl:input>
                            <soap:body use="literal"/>
                        </wsdl:input>
                        <wsdl:output>
                            <soap:body use="literal"/>
                        </wsdl:output>
                    </wsdl:operation>
                </wsdl:binding>
                <wsdl:service name="Calculator">
                    <wsdl:port name="CalculatorSoap" binding="tns:CalculatorSoap">
                        <soap:address location="%s/calculator.asmx"/>
                    </wsdl:port>
                </wsdl:service>
            </wsdl:definitions>
            """.formatted(baseUrl);

        wireMockServer.stubFor(get(urlEqualTo("/calculator.asmx?WSDL"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml; charset=utf-8")
                .withBody(wsdlContent)));

        // Stub WSDL with credentials (for testing credential extraction)
        wireMockServer.stubFor(get(urlMatching("/calculator\\.asmx\\?WSDL&user=.*&password=.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml; charset=utf-8")
                .withBody(wsdlContent)));

        // Also stub HEAD requests for health checks
        wireMockServer.stubFor(head(urlEqualTo("/calculator.asmx?WSDL"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml; charset=utf-8")));

        wireMockServer.stubFor(head(urlMatching("/calculator\\.asmx\\?WSDL&user=.*&password=.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml; charset=utf-8")));

        // Stub the service endpoint (for dynamic client validation)
        wireMockServer.stubFor(get(urlEqualTo("/calculator.asmx"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html")
                .withBody("<html><body>SOAP Service</body></html>")));
    }

    @Test
    @DisplayName("Should create SOAP client from WSDL")
    void shouldCreateSoapClientFromWsdl() {
        // Given: A WSDL URL
        // When: Creating a SOAP client
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .timeout(Duration.ofSeconds(10))
            .build();

        // Then: The client should be configured correctly
        assertThat(client).isNotNull();
        assertThat(client.getClientType()).isEqualTo(ClientType.SOAP);
        assertThat(client.getServiceName()).isEqualTo("calculator-service");
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should invoke SOAP operation successfully")
    void shouldInvokeSoapOperationSuccessfully() {
        // Given: A mock SOAP response (AddResult has no namespace due to elementFormDefault="unqualified")
        String soapResponse = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <ns:AddResponse xmlns:ns="http://tempuri.org/">
                        <AddResult>8</AddResult>
                    </ns:AddResponse>
                </soap:Body>
            </soap:Envelope>
            """;

        wireMockServer.stubFor(post(urlEqualTo("/calculator.asmx"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml; charset=utf-8")
                .withBody(soapResponse)));

        // When: Creating a SOAP client and invoking an operation
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .build();

        CalculatorRequest request = new CalculatorRequest(5, 3);

        // The dynamic client returns primitive types directly (Integer in this case)
        Mono<Integer> response = client.invokeAsync("Add", request, Integer.class);

        // Then: The response should be correct
        StepVerifier.create(response)
            .assertNext(result -> {
                assertThat(result).isNotNull();
                assertThat(result).isEqualTo(8);
            })
            .verifyComplete();

        client.shutdown();
    }

    @Test
    @DisplayName("Should handle SOAP fault correctly")
    void shouldHandleSoapFaultCorrectly() {
        // Given: A mock SOAP fault response
        String soapFault = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <soap:Fault>
                        <faultcode>soap:Client</faultcode>
                        <faultstring>Invalid input parameters</faultstring>
                        <detail>
                            <error>Both numbers must be positive</error>
                        </detail>
                    </soap:Fault>
                </soap:Body>
            </soap:Envelope>
            """;

        wireMockServer.stubFor(post(urlEqualTo("/calculator.asmx"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "text/xml; charset=utf-8")
                .withBody(soapFault)));

        // When: Invoking an operation that returns a fault
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .build();

        CalculatorRequest request = new CalculatorRequest(-5, 3);

        Mono<Integer> response = client.invokeAsync("Add", request, Integer.class);

        // Then: Should receive a SOAP fault exception
        StepVerifier.create(response)
            .expectErrorMatches(error ->
                error instanceof SoapFaultException &&
                error.getMessage().contains("Invalid input parameters"))
            .verify();

        client.shutdown();
    }

    @Test
    @DisplayName("Should create SOAP client with custom namespace")
    void shouldCreateSoapClientWithCustomNamespace() {
        // Given: Custom service and port QNames
        QName serviceQName = new QName("http://tempuri.org/", "Calculator");
        QName portQName = new QName("http://tempuri.org/", "CalculatorSoap");

        // When: Creating a SOAP client with custom namespaces
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .serviceName(serviceQName)
            .portName(portQName)
            .build();

        // Then: The client should be created successfully
        assertThat(client).isNotNull();
        assertThat(client.getClientType()).isEqualTo(ClientType.SOAP);

        client.shutdown();
    }

    @Test
    @DisplayName("Should create SOAP client with MTOM enabled")
    void shouldCreateSoapClientWithMtomEnabled() {
        // Given: MTOM configuration
        // When: Creating a SOAP client with MTOM enabled
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .enableMtom()
            .timeout(Duration.ofSeconds(30))
            .build();

        // Then: The client should be created successfully
        assertThat(client).isNotNull();
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should create SOAP client with endpoint override")
    void shouldCreateSoapClientWithEndpointOverride() {
        // Given: A different endpoint address
        String customEndpoint = baseUrl + "/custom-endpoint";

        // When: Creating a SOAP client with endpoint override
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .endpointAddress(customEndpoint)
            .build();

        // Then: The client should use the custom endpoint
        assertThat(client).isNotNull();
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should create SOAP client with custom headers")
    void shouldCreateSoapClientWithCustomHeaders() {
        // Given: Custom HTTP headers
        // When: Creating a SOAP client with custom headers
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .header("X-API-Key", "test-api-key")
            .header("X-Client-Version", "1.0.0")
            .build();

        // Then: The client should be created successfully
        assertThat(client).isNotNull();
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should perform health check on SOAP service")
    void shouldPerformHealthCheckOnSoapService() {
        // Given: A SOAP client
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .build();

        // When: Performing a health check
        Mono<Void> healthCheck = client.healthCheck();

        // Then: The health check should succeed
        StepVerifier.create(healthCheck)
            .verifyComplete();

        client.shutdown();
    }

    @Test
    @DisplayName("Should manage SOAP client lifecycle correctly")
    void shouldManageSoapClientLifecycleCorrectly() {
        // Given: A SOAP client
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .build();

        // When: Checking if ready
        // Then: Should be ready
        assertThat(client.isReady()).isTrue();

        // When: Performing health check
        Mono<Void> healthCheck = client.healthCheck();

        // Then: Health check should succeed
        StepVerifier.create(healthCheck).verifyComplete();

        // When: Shutting down
        client.shutdown();

        // Then: Should not be ready anymore
        assertThat(client.isReady()).isFalse();
    }

    @Test
    @DisplayName("Should create SOAP client with WS-Security credentials")
    void shouldCreateSoapClientWithWsSecurityCredentials() {
        // Given: WS-Security credentials
        // When: Creating a SOAP client with credentials
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .username("test-user")
            .password("test-password")
            .build();

        // Then: The client should be created successfully
        assertThat(client).isNotNull();
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should create SOAP client with WSDL URL containing credentials")
    void shouldCreateSoapClientWithWsdlUrlContainingCredentials() {
        // Given: A WSDL URL with embedded credentials
        String wsdlWithCreds = wsdlUrl + "&user=testuser&password=testpass";

        // When: Creating a SOAP client
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlWithCreds)
            .build();

        // Then: The client should extract credentials and be created successfully
        assertThat(client).isNotNull();
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should create SOAP client with complete configuration")
    void shouldCreateSoapClientWithCompleteConfiguration() {
        // Given: Complete SOAP client configuration
        QName serviceQName = new QName("http://tempuri.org/", "Calculator");
        QName portQName = new QName("http://tempuri.org/", "CalculatorSoap");

        // When: Creating a fully configured SOAP client
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .serviceName(serviceQName)
            .portName(portQName)
            .timeout(Duration.ofSeconds(30))
            .username("test-user")
            .password("test-password")
            .enableMtom()
            .header("X-API-Key", "api-key-123")
            .header("X-Tenant-ID", "tenant-456")
            .property("custom.property", "value")
            .build();

        // Then: The client should be created successfully
        assertThat(client).isNotNull();
        assertThat(client.getClientType()).isEqualTo(ClientType.SOAP);
        assertThat(client.getServiceName()).isEqualTo("calculator-service");
        assertThat(client.isReady()).isTrue();

        client.shutdown();
    }

    @Test
    @DisplayName("Should verify SOAP request contains correct SOAP action")
    void shouldVerifySoapRequestContainsCorrectSoapAction() {
        // Given: A mock SOAP response
        String soapResponse = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                <soap:Body>
                    <ns:AddResponse xmlns:ns="http://tempuri.org/">
                        <AddResult>15</AddResult>
                    </ns:AddResponse>
                </soap:Body>
            </soap:Envelope>
            """;

        wireMockServer.stubFor(post(urlEqualTo("/calculator.asmx"))
            .withHeader("SOAPAction", equalTo("\"http://tempuri.org/Add\""))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml; charset=utf-8")
                .withBody(soapResponse)));

        // When: Invoking a SOAP operation
        SoapClient client = ServiceClient.soap("calculator-service")
            .wsdlUrl(wsdlUrl)
            .build();

        CalculatorRequest request = new CalculatorRequest(10, 5);

        Mono<Integer> response = client.invokeAsync("Add", request, Integer.class);

        // Then: The request should include the correct SOAP action
        StepVerifier.create(response)
            .assertNext(result -> {
                assertThat(result).isNotNull();
                assertThat(result).isEqualTo(15);
            })
            .verifyComplete();

        // Verify the SOAP action header was sent
        wireMockServer.verify(postRequestedFor(urlEqualTo("/calculator.asmx"))
            .withHeader("SOAPAction", equalTo("\"http://tempuri.org/Add\"")));

        client.shutdown();
    }
}
