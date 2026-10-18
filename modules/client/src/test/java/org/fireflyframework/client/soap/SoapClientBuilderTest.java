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
import org.fireflyframework.client.builder.SoapClientBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SOAP client builder.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@DisplayName("SOAP Client Builder Tests")
class SoapClientBuilderTest {

    @Test
    @DisplayName("Should create SOAP client builder with service name")
    void shouldCreateBuilderWithServiceName() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should reject null service name")
    void shouldRejectNullServiceName() {
        // When/Then
        assertThatThrownBy(() -> ServiceClient.soap(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Service name cannot be null or empty");
    }

    @Test
    @DisplayName("Should reject empty service name")
    void shouldRejectEmptyServiceName() {
        // When/Then
        assertThatThrownBy(() -> ServiceClient.soap(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Service name cannot be null or empty");
    }

    @Test
    @DisplayName("Should configure WSDL URL")
    void shouldConfigureWsdlUrl() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should reject null WSDL URL")
    void shouldRejectNullWsdlUrl() {
        // When/Then
        assertThatThrownBy(() -> 
            ServiceClient.soap("test-service").wsdlUrl(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("WSDL URL cannot be null or empty");
    }

    @Test
    @DisplayName("Should configure service QName")
    void shouldConfigureServiceQName() {
        // Given
        QName serviceQName = new QName("http://example.com/", "TestService");

        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .serviceName(serviceQName);

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure port QName")
    void shouldConfigurePortQName() {
        // Given
        QName portQName = new QName("http://example.com/", "TestPort");

        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .portName(portQName);

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure timeout")
    void shouldConfigureTimeout() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .timeout(Duration.ofSeconds(45));

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should reject negative timeout")
    void shouldRejectNegativeTimeout() {
        // When/Then
        assertThatThrownBy(() -> 
            ServiceClient.soap("test-service").timeout(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Timeout must be positive");
    }

    @Test
    @DisplayName("Should configure credentials")
    void shouldConfigureCredentials() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .credentials("username", "password");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure username and password separately")
    void shouldConfigureUsernameAndPasswordSeparately() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .username("testuser")
            .password("testpass");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should enable MTOM")
    void shouldEnableMtom() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .enableMtom();

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should disable MTOM")
    void shouldDisableMtom() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .disableMtom();

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should add custom properties")
    void shouldAddCustomProperties() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .property("custom.property", "value");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should add custom headers")
    void shouldAddCustomHeaders() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .header("X-Custom-Header", "value");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure endpoint address override")
    void shouldConfigureEndpointAddressOverride() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .endpointAddress("http://production.example.com/service");

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should enable schema validation")
    void shouldEnableSchemaValidation() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .enableSchemaValidation();

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should disable schema validation")
    void shouldDisableSchemaValidation() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl("http://example.com/service?wsdl")
            .disableSchemaValidation();

        // Then
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should fail to build without WSDL URL")
    void shouldFailToBuildWithoutWsdlUrl() {
        // When/Then
        assertThatThrownBy(() -> 
            ServiceClient.soap("test-service").build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WSDL URL must be set");
    }

    @Test
    @DisplayName("Should validate WSDL URL format")
    void shouldValidateWsdlUrlFormat() {
        // When/Then
        assertThatThrownBy(() -> 
            ServiceClient.soap("test-service")
                .wsdlUrl("invalid-url")
                .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid WSDL URL");
    }

    @Test
    @DisplayName("Should demonstrate fluent API")
    void shouldDemonstrateFluentApi() {
        // When
        SoapClientBuilder builder = ServiceClient.soap("payment-service")
            .wsdlUrl("http://example.com/payment?wsdl")
            .serviceName(new QName("http://example.com/", "PaymentService"))
            .portName(new QName("http://example.com/", "PaymentPort"))
            .timeout(Duration.ofSeconds(30))
            .credentials("api-user", "secret")
            .enableMtom()
            .header("X-API-Key", "12345")
            .property("custom.setting", "value")
            .endpointAddress("https://prod.example.com/payment")
            .enableSchemaValidation();

        // Then
        assertThat(builder).isNotNull();
    }
}

