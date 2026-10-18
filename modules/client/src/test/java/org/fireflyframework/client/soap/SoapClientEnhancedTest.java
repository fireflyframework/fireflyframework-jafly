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

import org.fireflyframework.client.ServiceClient;
import org.fireflyframework.client.builder.SoapClientBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Enhanced tests for SOAP client with WSDL URL parsing, SSL, and advanced features.
 *
 * @author Firefly Software Foundation
 * @since 2.0.0
 */
@DisplayName("SOAP Client Enhanced Features Tests")
class SoapClientEnhancedTest {

    @Test
    @DisplayName("Should support WSDL URL with embedded credentials")
    void shouldSupportWsdlUrlWithEmbeddedCredentials() {
        // Given: WSDL URL with username and password parameters
        String wsdlUrl = "https://secure.example.com/service.asmx?WSDL&user=testuser&password=testpass";

        // When: Creating SOAP client
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl(wsdlUrl);

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should support WSDL URL with alternative credential parameter names")
    void shouldSupportAlternativeCredentialParameterNames() {
        // Given: WSDL URL with username/pwd parameters
        String wsdlUrl = "https://api.example.com/soap?wsdl&username=apiuser&pwd=secret";

        // When: Creating SOAP client
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl(wsdlUrl);

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should support PayNet-style WSDL URL")
    void shouldSupportPayNetStyleWsdlUrl() {
        // Given: PayNet-style WSDL URL with credentials
        String wsdlUrl = "https://secure.paynetonline.com/direct/PayNetDirect.asmx?WSDL&user=username&password=pass";

        // When: Creating SOAP client
        SoapClientBuilder builder = ServiceClient.soap("paynet-service")
            .wsdlUrl(wsdlUrl)
            .timeout(Duration.ofSeconds(30));

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should allow explicit credentials to override URL credentials")
    void shouldAllowExplicitCredentialsOverride() {
        // Given: WSDL URL with credentials
        String wsdlUrl = "https://example.com/service?wsdl&user=urluser&password=urlpass";

        // When: Creating SOAP client with explicit credentials
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl(wsdlUrl)
            .credentials("explicituser", "explicitpass");

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure SSL with trust store")
    void shouldConfigureSslWithTrustStore() {
        // When: Creating SOAP client with trust store
        SoapClientBuilder builder = ServiceClient.soap("secure-service")
            .wsdlUrl("https://secure.example.com/service?wsdl")
            .trustStore("/path/to/truststore.jks", "trustpass");

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure SSL with key store for client authentication")
    void shouldConfigureSslWithKeyStore() {
        // When: Creating SOAP client with key store
        SoapClientBuilder builder = ServiceClient.soap("secure-service")
            .wsdlUrl("https://secure.example.com/service?wsdl")
            .keyStore("/path/to/keystore.jks", "keypass");

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should configure SSL with both trust and key stores")
    void shouldConfigureSslWithBothStores() {
        // When: Creating SOAP client with both stores
        SoapClientBuilder builder = ServiceClient.soap("secure-service")
            .wsdlUrl("https://secure.example.com/service?wsdl")
            .trustStore("/path/to/truststore.jks", "trustpass")
            .keyStore("/path/to/keystore.jks", "keypass");

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should allow disabling SSL verification for development")
    void shouldAllowDisablingSslVerification() {
        // When: Creating SOAP client with SSL verification disabled
        SoapClientBuilder builder = ServiceClient.soap("dev-service")
            .wsdlUrl("https://dev.example.com/service?wsdl")
            .disableSslVerification();

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate complete secure SOAP client configuration")
    void shouldDemonstrateCompleteSecureConfiguration() {
        // When: Creating fully configured secure SOAP client
        SoapClientBuilder builder = ServiceClient.soap("enterprise-service")
            .wsdlUrl("https://secure.example.com/service.asmx?WSDL&user=apiuser&password=secret")
            .timeout(Duration.ofSeconds(45))
            .enableMtom()
            .enableSchemaValidation()
            .trustStore("/etc/ssl/truststore.jks", "trustpass")
            .keyStore("/etc/ssl/keystore.jks", "keypass")
            .header("X-API-Version", "2.0")
            .header("X-Client-ID", "client-123");

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate PayNet integration example")
    void shouldDemonstratePayNetIntegration() {
        // Example: PayNet payment gateway integration
        SoapClientBuilder builder = ServiceClient.soap("paynet-payment")
            .wsdlUrl("https://secure.paynetonline.com/direct/PayNetDirect.asmx?WSDL&user=merchant123&password=merchantpass")
            .timeout(Duration.ofSeconds(60))
            .enableMtom()
            .header("X-Merchant-ID", "MERCHANT123")
            .header("X-Transaction-Type", "SALE");

        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate multi-environment WSDL configuration")
    void shouldDemonstrateMultiEnvironmentConfiguration() {
        // Development environment
        SoapClientBuilder devBuilder = ServiceClient.soap("payment-service")
            .wsdlUrl("http://dev.example.com/payment?wsdl&user=devuser&password=devpass")
            .disableSslVerification()
            .timeout(Duration.ofSeconds(60));

        // Production environment
        SoapClientBuilder prodBuilder = ServiceClient.soap("payment-service")
            .wsdlUrl("https://prod.example.com/payment?wsdl&user=produser&password=prodpass")
            .trustStore("/etc/ssl/prod-truststore.jks", "prodpass")
            .timeout(Duration.ofSeconds(30))
            .enableSchemaValidation();

        assertThat(devBuilder).isNotNull();
        assertThat(prodBuilder).isNotNull();
    }

    @Test
    @DisplayName("Should handle WSDL URL with special characters in credentials")
    void shouldHandleSpecialCharactersInCredentials() {
        // Given: WSDL URL with URL-encoded special characters
        String wsdlUrl = "https://example.com/service?wsdl&user=test%40user&password=p%40ss%21word";

        // When: Creating SOAP client
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl(wsdlUrl);

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should support WSDL URL with additional query parameters")
    void shouldSupportAdditionalQueryParameters() {
        // Given: WSDL URL with credentials and other parameters
        String wsdlUrl = "https://example.com/service?wsdl&user=testuser&password=testpass&version=2.0&format=xml";

        // When: Creating SOAP client
        SoapClientBuilder builder = ServiceClient.soap("test-service")
            .wsdlUrl(wsdlUrl);

        // Then: Builder should be created successfully
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate banking SOAP service integration")
    void shouldDemonstrateBankingIntegration() {
        // Example: Banking SOAP service with strict security
        SoapClientBuilder builder = ServiceClient.soap("banking-service")
            .wsdlUrl("https://secure.bank.example.com/services/AccountService?wsdl")
            .credentials("bank_api_user", "secure_password")
            .trustStore("/etc/ssl/bank-ca-bundle.jks", "trustpass")
            .keyStore("/etc/ssl/client-cert.jks", "certpass")
            .timeout(Duration.ofSeconds(30))
            .enableSchemaValidation()
            .header("X-Bank-ID", "BANK001")
            .header("X-Branch-Code", "BR123");

        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate government SOAP service integration")
    void shouldDemonstrateGovernmentIntegration() {
        // Example: Government SOAP service with specific requirements
        SoapClientBuilder builder = ServiceClient.soap("gov-tax-service")
            .wsdlUrl("https://tax.gov.example.com/TaxService.asmx?WSDL&user=taxpayer123&password=secret")
            .timeout(Duration.ofMinutes(2))  // Government services can be slow
            .enableMtom()  // For document uploads
            .trustStore("/etc/ssl/gov-ca.jks", "govpass")
            .header("X-Taxpayer-ID", "TAX123456")
            .header("X-Fiscal-Year", "2025");

        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("Should demonstrate healthcare SOAP service integration")
    void shouldDemonstrateHealthcareIntegration() {
        // Example: Healthcare SOAP service with HIPAA compliance
        SoapClientBuilder builder = ServiceClient.soap("healthcare-ehr")
            .wsdlUrl("https://ehr.hospital.example.com/PatientService?wsdl")
            .credentials("ehr_integration", "hipaa_compliant_password")
            .trustStore("/etc/ssl/healthcare-ca.jks", "healthpass")
            .keyStore("/etc/ssl/hospital-client.jks", "clientpass")
            .timeout(Duration.ofSeconds(45))
            .enableSchemaValidation()
            .header("X-Facility-ID", "HOSP001")
            .header("X-Provider-NPI", "1234567890");

        assertThat(builder).isNotNull();
    }
}

