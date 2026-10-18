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
 * Unit tests for {@link SortCodeValidator}.
 */
class SortCodeValidatorTest {

    private SortCodeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SortCodeValidator();
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValidSortCode(null)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyInput() {
        assertThat(validator.isValidSortCode("")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123456",    // No separators
            "12-34-56",  // With hyphens
            "12 34 56",  // With spaces
            "40-47-84",  // HSBC
            "20-00-00",  // Barclays
            "30-00-00",  // Lloyds
            "60-70-80",  // Example sort code
            "83-91-36"   // Example sort code
    })
    void shouldReturnTrueForValidSortCodes(String sortCode) {
        assertThat(validator.isValidSortCode(sortCode)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",     // Too short
            "1234567",   // Too long
            "12-345-6",  // Wrong format
            "12.34.56",  // Wrong separator
            "ab-cd-ef",  // Non-numeric
            "12--34-56", // Double separator
            "12-34-5a"   // Contains letter
    })
    void shouldReturnFalseForInvalidSortCodes(String sortCode) {
        assertThat(validator.isValidSortCode(sortCode)).isFalse();
    }

    @Test
    void shouldNormalizeSortCode() {
        assertThat(validator.normalize("12-34-56")).isEqualTo("123456");
        assertThat(validator.normalize("12 34 56")).isEqualTo("123456");
        assertThat(validator.normalize("123456")).isEqualTo("123456");
    }

    @Test
    void shouldReturnNullWhenNormalizingInvalidSortCode() {
        assertThat(validator.normalize(null)).isNull();
        assertThat(validator.normalize("")).isNull();
        assertThat(validator.normalize("12345")).isNull();
        assertThat(validator.normalize("12-34-5")).isNull();
    }

    @Test
    void shouldFormatSortCode() {
        assertThat(validator.format("123456")).isEqualTo("12-34-56");
        assertThat(validator.format("12-34-56")).isEqualTo("12-34-56");
        assertThat(validator.format("12 34 56")).isEqualTo("12-34-56");
    }

    @Test
    void shouldReturnNullWhenFormattingInvalidSortCode() {
        assertThat(validator.format(null)).isNull();
        assertThat(validator.format("")).isNull();
        assertThat(validator.format("12345")).isNull();
        assertThat(validator.format("12-34-5")).isNull();
    }

    @Test
    void shouldExtractBankIdentifier() {
        assertThat(validator.getBankIdentifier("123456")).isEqualTo("12");
        assertThat(validator.getBankIdentifier("12-34-56")).isEqualTo("12");
        assertThat(validator.getBankIdentifier("12 34 56")).isEqualTo("12");
    }

    @Test
    void shouldReturnNullWhenExtractingBankIdentifierFromInvalidSortCode() {
        assertThat(validator.getBankIdentifier(null)).isNull();
        assertThat(validator.getBankIdentifier("")).isNull();
        assertThat(validator.getBankIdentifier("12345")).isNull();
    }

    @Test
    void shouldExtractBranchIdentifier() {
        assertThat(validator.getBranchIdentifier("123456")).isEqualTo("3456");
        assertThat(validator.getBranchIdentifier("12-34-56")).isEqualTo("3456");
        assertThat(validator.getBranchIdentifier("12 34 56")).isEqualTo("3456");
    }

    @Test
    void shouldReturnNullWhenExtractingBranchIdentifierFromInvalidSortCode() {
        assertThat(validator.getBranchIdentifier(null)).isNull();
        assertThat(validator.getBranchIdentifier("")).isNull();
        assertThat(validator.getBranchIdentifier("12345")).isNull();
    }
}
