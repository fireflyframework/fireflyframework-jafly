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
 * Unit tests for {@link BicValidator}.
 */
class BicValidatorTest {

    private BicValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BicValidator();
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValidBic(null)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyInput() {
        assertThat(validator.isValidBic("")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DEUTDEFF",     // Deutsche Bank (8 characters)
            "DEUTDEFFXXX",  // Deutsche Bank with branch code
            "BARCGB22",     // Barclays Bank UK
            "MIDLGB2L",     // HSBC UK
            "BNPAFRPP",     // BNP Paribas France
            "CHASUS33",     // JPMorgan Chase
            "COBADEFF",     // Commerzbank
            "SOGEFRPP",     // Société Générale
            "UNCRIT2B912",  // UniCredit with branch code
            "BOFAUS3N"      // Bank of America
    })
    void shouldReturnTrueForValidBics(String bic) {
        assertThat(validator.isValidBic(bic)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DEUT1EFF",     // Invalid bank code (must be letters only)
            "DEUTDEFFF",    // Invalid length (9 characters)
            "DEUTDEFFXX",   // Invalid branch code length
            "DEUTDEF",      // Too short
            "DEUTDEFFXXXX", // Too long
            "1EUTDEFF",     // Bank code must start with letter
            "DEUTD2FF",     // Country code must be letters only
            "DEUTDEFF XX"   // Contains space
    })
    void shouldReturnFalseForInvalidBics(String bic) {
        assertThat(validator.isValidBic(bic)).isFalse();
    }

    @Test
    void shouldHandleBicWithSpaces() {
        assertThat(validator.isValidBic("DEUT DEFF")).isTrue();
    }

    @Test
    void shouldHandleBicWithLowercase() {
        assertThat(validator.isValidBic("deutdeff")).isTrue();
    }

    @Test
    void shouldExtractBankCode() {
        assertThat(validator.getBankCode("DEUTDEFF")).isEqualTo("DEUT");
        assertThat(validator.getBankCode("DEUTDEFFXXX")).isEqualTo("DEUT");
        assertThat(validator.getBankCode("BARCGB22")).isEqualTo("BARC");
    }

    @Test
    void shouldReturnNullBankCodeForInvalidBic() {
        assertThat(validator.getBankCode("INVALID")).isNull();
    }

    @Test
    void shouldExtractCountryCode() {
        assertThat(validator.getCountryCode("DEUTDEFF")).isEqualTo("DE");
        assertThat(validator.getCountryCode("BARCGB22")).isEqualTo("GB");
    }

    @Test
    void shouldReturnNullCountryCodeForInvalidBic() {
        assertThat(validator.getCountryCode("INVALID")).isNull();
    }

    @Test
    void shouldExtractLocationCode() {
        assertThat(validator.getLocationCode("DEUTDEFF")).isEqualTo("FF");
        assertThat(validator.getLocationCode("BARCGB22")).isEqualTo("22");
    }

    @Test
    void shouldReturnNullLocationCodeForInvalidBic() {
        assertThat(validator.getLocationCode("INVALID")).isNull();
    }

    @Test
    void shouldExtractBranchCode() {
        assertThat(validator.getBranchCode("DEUTDEFFXXX")).isEqualTo("XXX");
        assertThat(validator.getBranchCode("UNCRIT2B912")).isEqualTo("912");
    }

    @Test
    void shouldReturnDefaultBranchCodeForEightCharacterBic() {
        assertThat(validator.getBranchCode("DEUTDEFF")).isEqualTo("XXX");
    }

    @Test
    void shouldReturnNullBranchCodeForInvalidBic() {
        assertThat(validator.getBranchCode("INVALID")).isNull();
    }
}
