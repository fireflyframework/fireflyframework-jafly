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


package org.fireflyframework.validators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TaxIdValidator}.
 */
class TaxIdValidatorTest {

    private TaxIdValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TaxIdValidator();
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyInput() {
        assertThat(validator.isValid("", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12-3456789",     // US EIN
            "123-45-6789",    // US SSN as TIN
            "ABCD123456ABC",  // Mexico RFC
            "20-12345678-6",  // Argentina CUIT
            "12345678Z",      // Spain NIF
            "12.345.678/0001-95", // Brazil CNPJ
            "1234567890"      // UK UTR
    })
    void shouldReturnTrueForValidTaxIds(String taxId) {
        assertThat(validator.isValid(taxId, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12-345678",      // US EIN too short
            "123-45-678",     // US SSN too short
            "ABCD12345ABC",   // Mexico RFC too short
            "20-1234567-9",   // Argentina CUIT too short
            "1234567Z",       // Spain NIF too short
            "12.345.678/0001-9", // Brazil CNPJ too short
            "123456789"       // UK UTR too short
    })
    void shouldReturnFalseForInvalidTaxIds(String taxId) {
        assertThat(validator.isValid(taxId, null)).isFalse();
    }

    @Test
    void shouldValidateUSTaxId() {
        // Valid US EIN
        assertThat(validator.isValid("12-3456789", null)).isTrue();

        // Valid US SSN as TIN
        assertThat(validator.isValid("123-45-6789", null)).isTrue();

        // Invalid US tax IDs
        assertThat(validator.isValid("12-345678", null)).isFalse();
        assertThat(validator.isValid("123-45-678", null)).isFalse();
    }

    @Test
    void shouldValidateArgentinaCUIT() {
        // Valid Argentina CUIT
        assertThat(validator.isValid("20-12345678-6", null)).isTrue();

        // Invalid Argentina CUIT
        assertThat(validator.isValid("20-1234567-9", null)).isFalse();
        assertThat(validator.isValid("20-12345678-0", null)).isFalse(); // Invalid check digit
    }

    @Test
    void shouldValidateSpainNIF() {
        // Valid Spain NIF
        assertThat(validator.isValid("12345678Z", null)).isTrue();

        // Invalid Spain NIF
        assertThat(validator.isValid("1234567Z", null)).isFalse();
        assertThat(validator.isValid("12345678A", null)).isFalse(); // Invalid check letter
    }

    @Test
    void shouldValidateBrazilCNPJ() {
        // Valid Brazil CNPJ
        assertThat(validator.isValid("12.345.678/0001-95", null)).isTrue();

        // Invalid Brazil CNPJ
        assertThat(validator.isValid("12.345.678/0001-9", null)).isFalse();
        assertThat(validator.isValid("12.345.678/0001-00", null)).isFalse(); // Invalid check digit
    }

    @Test
    void shouldDetectCountry() {
        assertThat(validator.detectCountry("12-3456789")).isEqualTo("US");
        assertThat(validator.detectCountry("123-45-6789")).isEqualTo("US");
        assertThat(validator.detectCountry("20-12345678-6")).isEqualTo("AR");
        assertThat(validator.detectCountry("12345678Z")).isEqualTo("ES");
        assertThat(validator.detectCountry("12.345.678/0001-95")).isEqualTo("BR");
        assertThat(validator.detectCountry("1234567890")).isEqualTo("GB");

        // Invalid or unsupported tax ID
        assertThat(validator.detectCountry("invalid-tax-id")).isNull();
    }

    @Test
    void shouldFormatTaxId() {
        // US EIN
        assertThat(validator.format("123456789", "US")).isEqualTo("12-3456789");

        // Argentina CUIT
        assertThat(validator.format("20123456789", "AR")).isEqualTo("20-12345678-9");

        // Brazil CNPJ
        assertThat(validator.format("12345678000195", "BR")).isEqualTo("12.345.678/0001-95");

        // Invalid inputs
        assertThat(validator.format(null, "US")).isNull();
        assertThat(validator.format("", "US")).isNull();
        assertThat(validator.format("123456789", null)).isNull();
        assertThat(validator.format("123456789", "")).isNull();
        assertThat(validator.format("123", "US")).isNull(); // Too short
    }
}
