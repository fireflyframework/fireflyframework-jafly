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
 * Unit tests for {@link CreditCardValidator}.
 */
class CreditCardValidatorTest {

    private CreditCardValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CreditCardValidator();
    }

    @Test
    void shouldReturnFalseForNullInput() {
        assertThat(validator.isValidCreditCard(null)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyInput() {
        assertThat(validator.isValidCreditCard("")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4111111111111111",    // Visa
            "4012888888881881",    // Visa
            "4222222222222",       // Visa (13 digits)
            "5555555555554444",    // MasterCard
            "5105105105105100",    // MasterCard
            "378282246310005",     // American Express
            "371449635398431",     // American Express
            "6011111111111117",    // Discover
            "6011000990139424",    // Discover
            "3530111333300000",    // JCB
            "3566002020360505",    // JCB
            "30569309025904",      // Diners Club
            "38520000023237"       // Diners Club
    })
    void shouldReturnTrueForValidCardNumbers(String cardNumber) {
        assertThat(validator.isValidCreditCard(cardNumber)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4111111111111112",    // Invalid Visa (wrong check digit)
            "5555555555554443",    // Invalid MasterCard (wrong check digit)
            "378282246310006",     // Invalid American Express (wrong check digit)
            "6011111111111118",    // Invalid Discover (wrong check digit)
            "1234567890123456",    // Invalid card type
            "41111111",            // Too short
            "41111111111111111111", // Too long
            "4111-1111-1111-111a", // Contains non-digit
            "0000000000000000"     // All zeros
    })
    void shouldReturnFalseForInvalidCardNumbers(String cardNumber) {
        assertThat(validator.isValidCreditCard(cardNumber)).isFalse();
    }

    @Test
    void shouldHandleCardNumberWithSpaces() {
        assertThat(validator.isValidCreditCard("4111 1111 1111 1111")).isTrue();
    }

    @Test
    void shouldHandleCardNumberWithDashes() {
        assertThat(validator.isValidCreditCard("4111-1111-1111-1111")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "4111111111111111, VISA",
            "4012888888881881, VISA",
            "4222222222222, VISA",
            "5555555555554444, MASTERCARD",
            "5105105105105100, MASTERCARD",
            "378282246310005, AMERICAN_EXPRESS",
            "371449635398431, AMERICAN_EXPRESS",
            "6011111111111117, DISCOVER",
            "6011000990139424, DISCOVER",
            "3530111333300000, JCB",
            "3566002020360505, JCB",
            "30569309025904, DINERS_CLUB",
            "38520000023237, DINERS_CLUB",
            "1234567890123456, UNKNOWN"
    })
    void shouldIdentifyCorrectCardType(String cardNumber, CreditCardValidator.CardType expectedType) {
        assertThat(validator.getCardType(cardNumber)).isEqualTo(expectedType);
    }

    @Test
    void shouldReturnUnknownCardTypeForNullInput() {
        assertThat(validator.getCardType(null)).isEqualTo(CreditCardValidator.CardType.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownCardTypeForEmptyInput() {
        assertThat(validator.getCardType("")).isEqualTo(CreditCardValidator.CardType.UNKNOWN);
    }

    @Test
    void shouldMaskCardNumberCorrectly() {
        assertThat(validator.maskCardNumber("4111111111111111")).isEqualTo("************1111");
        assertThat(validator.maskCardNumber("378282246310005")).isEqualTo("***********0005");
        assertThat(validator.maskCardNumber("4222222222222")).isEqualTo("*********2222");
    }

    @Test
    void shouldHandleSpacesAndDashesWhenMasking() {
        assertThat(validator.maskCardNumber("4111 1111 1111 1111")).isEqualTo("************1111");
        assertThat(validator.maskCardNumber("4111-1111-1111-1111")).isEqualTo("************1111");
    }

    @Test
    void shouldReturnNullWhenMaskingNullInput() {
        assertThat(validator.maskCardNumber(null)).isNull();
    }

    @Test
    void shouldReturnNullWhenMaskingEmptyInput() {
        assertThat(validator.maskCardNumber("")).isNull();
    }

    @Test
    void shouldReturnNullWhenMaskingTooShortInput() {
        assertThat(validator.maskCardNumber("123")).isNull();
    }
}
