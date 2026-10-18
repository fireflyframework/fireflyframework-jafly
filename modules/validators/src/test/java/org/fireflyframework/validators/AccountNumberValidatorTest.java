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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AccountNumberValidator}.
 */
class AccountNumberValidatorTest {

    private AccountNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AccountNumberValidator();
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValidForCountry(null, "GB")).isFalse();
        assertThat(validator.isValidForCountry("12345678", null)).isFalse();
        assertThat(validator.isValidForCountry(null, null)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyInput() {
        assertThat(validator.isValidForCountry("", "GB")).isFalse();
        assertThat(validator.isValidForCountry("12345678", "")).isFalse();
        assertThat(validator.isValidForCountry("", "")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "12345678, GB",     // UK: 8 digits
            "1234567890, FR",   // France: 10 digits
            "1234567890, DE",   // Germany: 10 digits
            "1234567890, ES",   // Spain: 10 digits
            "123456789012, IT", // Italy: 12 digits
            "1234567890, NL",   // Netherlands: 10 digits
            "123456789012, BE", // Belgium: 12 digits
            "12345678, CH",     // Switzerland: 4-11 digits
            "1234, CH"          // Switzerland: 4-11 digits
    })
    void shouldReturnTrueForValidAccountNumbers(String accountNumber, String countryCode) {
        assertThat(validator.isValidForCountry(accountNumber, countryCode)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "1234567, GB",      // UK: too short
            "123456789, GB",    // UK: too long
            "123456789, FR",    // France: too short
            "12345678901, FR",  // France: too long
            "12345, IT",        // Italy: too short
            "1234567890123, IT", // Italy: too long
            "123, CH",          // Switzerland: too short
            "123456789012, CH", // Switzerland: too long
            "12345678, US",     // Unsupported country
            "ABCDEFGH, GB",     // Non-numeric
            "12345678, XX"      // Invalid country code
    })
    void shouldReturnFalseForInvalidAccountNumbers(String accountNumber, String countryCode) {
        assertThat(validator.isValidForCountry(accountNumber, countryCode)).isFalse();
    }

    @Test
    void shouldHandleAccountNumberWithSpaces() {
        assertThat(validator.isValidForCountry("1234 5678", "GB")).isTrue();
    }

    @Test
    void shouldHandleAccountNumberWithHyphens() {
        assertThat(validator.isValidForCountry("1234-5678", "GB")).isTrue();
    }

    @Test
    void shouldHandleCountryCodeWithLowercase() {
        assertThat(validator.isValidForCountry("12345678", "gb")).isTrue();
    }

    @Test
    void shouldHandleCountryCodeWithSpaces() {
        assertThat(validator.isValidForCountry("12345678", " GB ")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345678",    // No separators
            "1234 5678",   // With spaces
            "1234-5678"    // With hyphens
    })
    void shouldValidateUkAccountNumber(String accountNumber) {
        assertThat(validator.isValidUkAccountNumber(accountNumber)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567",     // Too short
            "123456789",   // Too long
            "ABCDEFGH",    // Non-numeric
            "1234567A"     // Contains letter
    })
    void shouldReturnFalseForInvalidUkAccountNumber(String accountNumber) {
        assertThat(validator.isValidUkAccountNumber(accountNumber)).isFalse();
    }

    @Test
    void shouldNormalizeAccountNumber() {
        assertThat(validator.normalize("1234-5678")).isEqualTo("12345678");
        assertThat(validator.normalize("1234 5678")).isEqualTo("12345678");
        assertThat(validator.normalize("12345678")).isEqualTo("12345678");
    }

    @Test
    void shouldReturnNullWhenNormalizingInvalidInput() {
        assertThat(validator.normalize(null)).isNull();
        assertThat(validator.normalize("")).isNull();
    }

    @Test
    void shouldFormatUkAccountNumber() {
        assertThat(validator.formatUkAccountNumber("12345678")).isEqualTo("12345678");
        assertThat(validator.formatUkAccountNumber("1234 5678")).isEqualTo("12345678");
        assertThat(validator.formatUkAccountNumber("1234-5678")).isEqualTo("12345678");
    }

    @Test
    void shouldReturnNullWhenFormattingInvalidUkAccountNumber() {
        assertThat(validator.formatUkAccountNumber(null)).isNull();
        assertThat(validator.formatUkAccountNumber("")).isNull();
        assertThat(validator.formatUkAccountNumber("1234567")).isNull();
        assertThat(validator.formatUkAccountNumber("123456789")).isNull();
    }

    @Test
    void shouldValidateUkBankAccount() {
        assertThat(validator.isValidUkBankAccount("12-34-56", "12345678")).isTrue();
        assertThat(validator.isValidUkBankAccount("123456", "12345678")).isTrue();
        assertThat(validator.isValidUkBankAccount("12 34 56", "1234 5678")).isTrue();
    }

    @Test
    void shouldReturnFalseForInvalidUkBankAccount() {
        assertThat(validator.isValidUkBankAccount("12-34-5", "12345678")).isFalse(); // Invalid sort code
        assertThat(validator.isValidUkBankAccount("12-34-56", "1234567")).isFalse(); // Invalid account number
        assertThat(validator.isValidUkBankAccount(null, "12345678")).isFalse(); // Null sort code
        assertThat(validator.isValidUkBankAccount("12-34-56", null)).isFalse(); // Null account number
    }

    @Test
    void shouldFormatUkBankAccount() {
        assertThat(validator.formatUkBankAccount("12-34-56", "12345678")).isEqualTo("12-34-56 12345678");
        assertThat(validator.formatUkBankAccount("123456", "12345678")).isEqualTo("12-34-56 12345678");
        assertThat(validator.formatUkBankAccount("12 34 56", "1234 5678")).isEqualTo("12-34-56 12345678");
    }

    @Test
    void shouldReturnNullWhenFormattingInvalidUkBankAccount() {
        assertThat(validator.formatUkBankAccount("12-34-5", "12345678")).isNull(); // Invalid sort code
        assertThat(validator.formatUkBankAccount("12-34-56", "1234567")).isNull(); // Invalid account number
        assertThat(validator.formatUkBankAccount(null, "12345678")).isNull(); // Null sort code
        assertThat(validator.formatUkBankAccount("12-34-56", null)).isNull(); // Null account number
    }
}
