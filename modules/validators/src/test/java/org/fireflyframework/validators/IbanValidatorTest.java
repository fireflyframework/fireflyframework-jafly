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
 * Unit tests for {@link IbanValidator}.
 */
class IbanValidatorTest {

    private IbanValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IbanValidator();
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
            "DE89 3704 0044 0532 0130 00", // Germany
            "GB29NWBK60161331926819",      // UK
            "FR14 2004 1010 0505 0001 3M02 606", // France
            "CH93 0076 2011 6238 5295 7",  // Switzerland
            "AT61 1904 3002 3457 3201",    // Austria
            "NL91ABNA0417164300",          // Netherlands
            "BE68539007547034",            // Belgium
            "ES91 2100 0418 4502 0005 1332", // Spain
            "IT60X0542811101000000123456", // Italy
            "SE35 5000 0000 0549 1000 0003" // Sweden
    })
    void shouldReturnTrueForValidIbans(String iban) {
        assertThat(validator.isValid(iban, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DE89 3704 0044 0532 0130", // Too short
            "GB29NWBK6016133192681999", // Too long
            "FR14 2004 1010 0505 0001 3M02 605", // Invalid check digits
            "US12 1234 5678 9012 3456", // Invalid country code
            "DE89 3704 0044 0532 0130 0A", // Invalid character
            "DE00 3704 0044 0532 0130 00", // Invalid check digits
            "IT60X05428111010000001234567" // Too long for Italy
    })
    void shouldReturnFalseForInvalidIbans(String iban) {
        assertThat(validator.isValid(iban, null)).isFalse();
    }

    @Test
    void shouldHandleIbanWithSpaces() {
        assertThat(validator.isValid("DE89 3704 0044 0532 0130 00", null)).isTrue();
    }

    @Test
    void shouldHandleIbanWithLowercase() {
        assertThat(validator.isValid("de89 3704 0044 0532 0130 00", null)).isTrue();
    }
}