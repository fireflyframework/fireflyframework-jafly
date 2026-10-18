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
 * Unit tests for {@link NationalIdValidator}.
 */
class NationalIdValidatorTest {

    private NationalIdValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NationalIdValidator();
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
            "12345678Z",      // Spanish DNI
            "X1234567L",      // Spanish NIE
            "123-45-6789",    // US SSN
            "123.456.789-09", // Brazilian CPF
            "AB123456C",      // UK National Insurance Number
            "MXGR890101HDFNNS09", // Mexico RFC
            "12345678",       // Argentina DNI
            "12345678-5"      // Chilean RUT
    })
    void shouldReturnTrueForValidNationalIds(String nationalId) {
        assertThat(validator.isValid(nationalId, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567Z",       // Spanish DNI too short
            "X123456L",       // Spanish NIE too short
            "123-45-678",     // US SSN too short
            "123.456.789-0",  // Brazilian CPF too short
            "AB12345C",       // UK National Insurance Number too short
            "MXGR8901HDFNNS09", // Mexico RFC too short
            "1234567",        // Argentina DNI too short
            "1234567-5"       // Chilean RUT too short
    })
    void shouldReturnFalseForInvalidNationalIds(String nationalId) {
        assertThat(validator.isValid(nationalId, null)).isFalse();
    }

    @Test
    void shouldValidateSpanishId() {
        // Valid Spanish DNI
        assertThat(validator.isValid("12345678Z", null)).isTrue();
        
        // Valid Spanish NIE
        assertThat(validator.isValid("X1234567L", null)).isTrue();
        
        // Invalid Spanish IDs
        assertThat(validator.isValid("1234567Z", null)).isFalse();
        assertThat(validator.isValid("12345678A", null)).isFalse(); // Invalid check letter
        assertThat(validator.isValid("X123456L", null)).isFalse();
        assertThat(validator.isValid("X1234567A", null)).isFalse(); // Invalid check letter
    }

    @Test
    void shouldValidateBrazilianCPF() {
        // Valid Brazilian CPF
        assertThat(validator.isValid("123.456.789-09", null)).isTrue();
        
        // Invalid Brazilian CPF
        assertThat(validator.isValid("123.456.789-0", null)).isFalse();
        assertThat(validator.isValid("123.456.789-00", null)).isFalse(); // Invalid check digit
        assertThat(validator.isValid("111.111.111-11", null)).isFalse(); // All same digits
    }

    @Test
    void shouldValidateUSSSN() {
        // Valid US SSN
        assertThat(validator.isValid("123-45-6789", null)).isTrue();
        
        // Invalid US SSN
        assertThat(validator.isValid("123-45-678", null)).isFalse();
        assertThat(validator.isValid("000-45-6789", null)).isFalse(); // Invalid first group
        assertThat(validator.isValid("123-00-6789", null)).isFalse(); // Invalid second group
        assertThat(validator.isValid("123-45-0000", null)).isFalse(); // Invalid third group
    }

    @Test
    void shouldValidateChileanRUT() {
        // Valid Chilean RUT
        assertThat(validator.isValid("12345678-5", null)).isTrue();
        
        // Invalid Chilean RUT
        assertThat(validator.isValid("1234567-5", null)).isFalse();
        assertThat(validator.isValid("12345678-0", null)).isFalse(); // Invalid check digit
    }

    @Test
    void shouldDetectCountry() {
        assertThat(validator.detectCountry("12345678Z")).isEqualTo("ES");
        assertThat(validator.detectCountry("X1234567L")).isEqualTo("ES");
        assertThat(validator.detectCountry("123-45-6789")).isEqualTo("US");
        assertThat(validator.detectCountry("123.456.789-09")).isEqualTo("BR");
        assertThat(validator.detectCountry("AB123456C")).isEqualTo("GB");
        assertThat(validator.detectCountry("12345678-5")).isEqualTo("CL");
        
        // Invalid or unsupported national ID
        assertThat(validator.detectCountry("invalid-id")).isNull();
    }

    @Test
    void shouldFormatNationalId() {
        // US SSN
        assertThat(validator.format("123456789", "US")).isEqualTo("123-45-6789");
        
        // Brazilian CPF
        assertThat(validator.format("12345678909", "BR")).isEqualTo("123.456.789-09");
        
        // Chilean RUT
        assertThat(validator.format("123456785", "CL")).isEqualTo("12345678-5");
        
        // Invalid inputs
        assertThat(validator.format(null, "US")).isNull();
        assertThat(validator.format("", "US")).isNull();
        assertThat(validator.format("123456789", null)).isNull();
        assertThat(validator.format("123456789", "")).isNull();
        assertThat(validator.format("123", "US")).isNull(); // Too short
    }
}