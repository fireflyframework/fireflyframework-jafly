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
 * Unit tests for {@link PhoneNumberValidator}.
 */
class PhoneNumberValidatorTest {

    private PhoneNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PhoneNumberValidator();
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
            "+14155552671",    // US
            "+442071234567",   // UK
            "+34911234567",    // Spain
            "+5511987654321",  // Brazil
            "+525512345678",   // Mexico
            "+61412345678",    // Australia
            "+8613812345678",  // China
            "+919876543210"    // India
    })
    void shouldReturnTrueForValidE164PhoneNumbers(String phoneNumber) {
        assertThat(validator.isValid(phoneNumber, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "14155552671",     // Missing + prefix
            "+1415555267",     // Too short for US
            "+4420712345678",  // Too long for UK
            "+3491123456",     // Too short for Spain
            "+55119876543210", // Too long for Brazil
            "+52551234567",    // Too short for Mexico
            "+6141234567",     // Too short for Australia
            "+861381234567",   // Too short for China
            "+91987654321"     // Too short for India
    })
    void shouldReturnFalseForInvalidE164PhoneNumbers(String phoneNumber) {
        assertThat(validator.isValid(phoneNumber, null)).isFalse();
    }

    @Test
    void shouldValidateUSPhoneNumber() {
        // Valid US phone number in E.164 format
        assertThat(validator.isValid("+14155552671", null)).isTrue();

        // Invalid US phone numbers
        assertThat(validator.isValid("+1415555267", null)).isFalse(); // Too short
    }

    @Test
    void shouldValidateUKPhoneNumber() {
        // Valid UK phone number in E.164 format
        assertThat(validator.isValid("+442071234567", null)).isTrue();

        // Invalid UK phone numbers
        assertThat(validator.isValid("+44207123456", null)).isFalse(); // Too short
        assertThat(validator.isValid("+4420712345678", null)).isFalse(); // Too long
    }

    @Test
    void shouldConvertToE164Format() {
        // US phone number
        assertThat(validator.toE164Format("(415) 555-2671", "US")).isEqualTo("+14155552671");
        assertThat(validator.toE164Format("4155552671", "US")).isEqualTo("+14155552671");

        // UK phone number
        assertThat(validator.toE164Format("020 7123 4567", "GB")).isEqualTo("+442071234567");
        assertThat(validator.toE164Format("2071234567", "GB")).isEqualTo("+442071234567");

        // Already in E.164 format
        assertThat(validator.toE164Format("+14155552671", "US")).isEqualTo("+14155552671");

        // Invalid inputs
        assertThat(validator.toE164Format(null, "US")).isNull();
        assertThat(validator.toE164Format("", "US")).isNull();
        assertThat(validator.toE164Format("4155552671", null)).isNull();
        assertThat(validator.toE164Format("4155552671", "")).isNull();
        assertThat(validator.toE164Format("415", "US")).isNull(); // Too short
    }

    @Test
    void shouldFormatPhoneNumber() {
        // US phone number
        assertThat(validator.format("+14155552671", "US")).isEqualTo("(415) 555-2671");

        // UK phone number
        assertThat(validator.format("+442071234567", "GB")).isEqualTo("0207 1234567");

        // Spain phone number
        assertThat(validator.format("+34911234567", "ES")).isEqualTo("911 234 567");

        // Invalid inputs
        assertThat(validator.format(null, "US")).isNull();
        assertThat(validator.format("", "US")).isNull();
        assertThat(validator.format("+14155552671", null)).isNull();
        assertThat(validator.format("+14155552671", "")).isNull();
    }

    @Test
    void shouldDetectCountry() {
        assertThat(validator.detectCountry("+14155552671")).isEqualTo("US");
        assertThat(validator.detectCountry("+442071234567")).isEqualTo("GB");
        assertThat(validator.detectCountry("+34911234567")).isEqualTo("ES");
        assertThat(validator.detectCountry("+5511987654321")).isEqualTo("BR");

        // Not in E.164 format
        assertThat(validator.detectCountry("4155552671")).isNull();

        // Invalid or unsupported phone number
        assertThat(validator.detectCountry("invalid-phone")).isNull();
    }
}
