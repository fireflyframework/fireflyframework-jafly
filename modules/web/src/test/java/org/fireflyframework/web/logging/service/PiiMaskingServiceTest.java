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


package org.fireflyframework.web.logging.service;

import org.fireflyframework.web.logging.config.PiiMaskingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PII masking functionality.
 * Tests verify that various types of PII data are correctly detected and masked.
 */
@ExtendWith(MockitoExtension.class)
class PiiMaskingServiceTest {

    private PiiMaskingService piiMaskingService;
    private PiiMaskingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PiiMaskingProperties();
        // Use default configuration for most tests
        properties.setEnabled(true);
        properties.setMaskCharacter("*");
        properties.setPreserveLength(true);
        
        piiMaskingService = new PiiMaskingService(properties);
    }

    @Test
    void testEmailMasking() {
        String input = "User email is john.doe@example.com and backup is jane@test.org";
        String result = piiMaskingService.maskPiiData(input);
        
        // Emails should be masked with asterisks
        assertNotEquals(input, result);
        assertFalse(result.contains("john.doe@example.com"));
        assertFalse(result.contains("jane@test.org"));
        assertTrue(result.contains("User email is"));
        assertTrue(result.contains("and backup is"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testPhoneNumberMasking() {
        String input = "Call me at 555-123-4567 or (555) 987-6543";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("555-123-4567"));
        assertFalse(result.contains("(555) 987-6543"));
        assertTrue(result.contains("Call me at"));
        assertTrue(result.contains("or"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testSsnMasking() {
        String input = "SSN: 123-45-6789 for employee";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("123-45-6789"));
        assertTrue(result.contains("SSN:"));
        assertTrue(result.contains("for employee"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testCreditCardMasking() {
        String input = "Card number: 4532-1234-5678-9012";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("4532-1234-5678-9012"));
        assertTrue(result.contains("Card number:"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testIpAddressMasking() {
        String input = "Server IP: 192.168.1.100";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("192.168.1.100"));
        assertTrue(result.contains("Server IP:"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testJwtTokenMasking() {
        String input = "Token: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature"));
        assertTrue(result.contains("Token:"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testApiKeyMasking() {
        String input = "API_KEY=abc123def456ghi789jkl012";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("abc123def456ghi789jkl012"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testMultiplePiiTypes() {
        String input = "User: john@example.com, Phone: 555-1234, Card: 4532-1234-5678-9012";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("john@example.com"));
        assertFalse(result.contains("555-1234"));
        assertFalse(result.contains("4532-1234-5678-9012"));
        assertTrue(result.contains("User:"));
        assertTrue(result.contains("Phone:"));
        assertTrue(result.contains("Card:"));
        
        System.out.println("[DEBUG_LOG] Original: " + input);
        System.out.println("[DEBUG_LOG] Masked: " + result);
    }

    @Test
    void testDisabledMasking() {
        properties.setEnabled(false);
        PiiMaskingService disabledService = new PiiMaskingService(properties);
        
        String input = "Email: john@example.com";
        String result = disabledService.maskPiiData(input);
        
        assertEquals(input, result); // Should return original text when disabled
        
        System.out.println("[DEBUG_LOG] Disabled masking - Original: " + input);
        System.out.println("[DEBUG_LOG] Disabled masking - Result: " + result);
    }

    @Test
    void testPartialRevealMasking() {
        properties.setPreserveLength(false);
        properties.setShowCharacters(2);
        PiiMaskingService partialService = new PiiMaskingService(properties);
        
        String input = "Email: john.doe@example.com";
        String result = partialService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("john.doe@example.com"));
        assertTrue(result.contains("Email:"));
        
        System.out.println("[DEBUG_LOG] Partial reveal - Original: " + input);
        System.out.println("[DEBUG_LOG] Partial reveal - Result: " + result);
    }

    @Test
    void testCustomMaskCharacter() {
        properties.setMaskCharacter("#");
        PiiMaskingService customService = new PiiMaskingService(properties);
        
        String input = "Email: user@domain.com";
        String result = customService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertTrue(result.contains("#"));
        assertFalse(result.contains("*"));
        
        System.out.println("[DEBUG_LOG] Custom mask char - Original: " + input);
        System.out.println("[DEBUG_LOG] Custom mask char - Result: " + result);
    }

    @Test
    void testHeaderMasking() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.token");
        headers.put("Content-Type", "application/json");
        headers.put("User-Email", "user@example.com");
        
        Map<String, String> result = piiMaskingService.maskHeaders(headers);
        
        assertNotNull(result);
        assertFalse(result.get("Authorization").contains("eyJhbGciOiJIUzI1NiJ9.token"));
        assertEquals("application/json", result.get("Content-Type")); // Should remain unchanged
        assertFalse(result.get("User-Email").contains("user@example.com"));
        
        System.out.println("[DEBUG_LOG] Headers masked: " + result);
    }

    @Test
    void testQueryParamMasking() {
        Map<String, String> params = new HashMap<>();
        params.put("email", "test@example.com");
        params.put("phone", "555-1234");
        params.put("search", "normal text");
        
        Map<String, String> result = piiMaskingService.maskQueryParams(params);
        
        assertNotNull(result);
        assertFalse(result.get("email").contains("test@example.com"));
        assertFalse(result.get("phone").contains("555-1234"));
        assertEquals("normal text", result.get("search")); // Should remain unchanged
        
        System.out.println("[DEBUG_LOG] Query params masked: " + result);
    }

    @Test
    void testExceptionMessageMasking() {
        String exceptionMessage = "Database error for user john@example.com with phone 555-1234";
        String result = piiMaskingService.maskExceptionMessage(exceptionMessage);
        
        assertNotEquals(exceptionMessage, result);
        assertFalse(result.contains("john@example.com"));
        assertFalse(result.contains("555-1234"));
        assertTrue(result.contains("Database error for user"));
        
        System.out.println("[DEBUG_LOG] Exception message - Original: " + exceptionMessage);
        System.out.println("[DEBUG_LOG] Exception message - Masked: " + result);
    }

    @Test
    void testNullAndEmptyInputs() {
        assertNull(piiMaskingService.maskPiiData(null));
        assertEquals("", piiMaskingService.maskPiiData(""));
        assertEquals("   ", piiMaskingService.maskPiiData("   "));
        
        assertNull(piiMaskingService.maskHeaders(null));
        assertNull(piiMaskingService.maskQueryParams(null));
        assertNull(piiMaskingService.maskExceptionMessage(null));
        
        System.out.println("[DEBUG_LOG] Null and empty inputs handled correctly");
    }

    @Test
    void testMaskingStats() {
        Map<String, Object> stats = piiMaskingService.getMaskingStats();
        
        assertNotNull(stats);
        assertTrue((Boolean) stats.get("enabled"));
        assertTrue((Integer) stats.get("patternsLoaded") > 0);
        assertEquals("*", stats.get("maskCharacter"));
        assertTrue((Boolean) stats.get("preserveLength"));
        
        System.out.println("[DEBUG_LOG] Masking stats: " + stats);
    }

    @Test
    void testIsReady() {
        assertTrue(piiMaskingService.isReady());
        
        // Test with disabled service
        properties.setEnabled(false);
        PiiMaskingService disabledService = new PiiMaskingService(properties);
        assertFalse(disabledService.isReady());
        
        System.out.println("[DEBUG_LOG] Service readiness checked");
    }

    @Test
    void testCustomPatterns() {
        // Add custom pattern for internal IDs
        Map<String, String> customPatterns = new HashMap<>();
        customPatterns.put("internal-id", "ID-[0-9]{6}");
        properties.setCustomPatterns(customPatterns);
        
        PiiMaskingService customService = new PiiMaskingService(properties);
        
        String input = "Internal reference: ID-123456";
        String result = customService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("ID-123456"));
        assertTrue(result.contains("Internal reference:"));
        
        System.out.println("[DEBUG_LOG] Custom pattern - Original: " + input);
        System.out.println("[DEBUG_LOG] Custom pattern - Masked: " + result);
    }

    @Test
    void testSpanishDniMasking() {
        String input = "Spanish citizen DNI: 12345678Z";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("12345678Z"));
        assertTrue(result.contains("Spanish citizen DNI:"));
        
        System.out.println("[DEBUG_LOG] Spanish DNI - Original: " + input);
        System.out.println("[DEBUG_LOG] Spanish DNI - Masked: " + result);
    }

    @Test
    void testSpanishNieMasking() {
        String input = "Foreign resident NIE: X1234567L and Y7654321M";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("X1234567L"));
        assertFalse(result.contains("Y7654321M"));
        assertTrue(result.contains("Foreign resident NIE:"));
        assertTrue(result.contains("and"));
        
        System.out.println("[DEBUG_LOG] Spanish NIE - Original: " + input);
        System.out.println("[DEBUG_LOG] Spanish NIE - Masked: " + result);
    }

    @Test
    void testFrenchCniMasking() {
        String input = "French CNI number: 12AB34567";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("12AB34567"));
        assertTrue(result.contains("French CNI number:"));
        
        System.out.println("[DEBUG_LOG] French CNI - Original: " + input);
        System.out.println("[DEBUG_LOG] French CNI - Masked: " + result);
    }

    @Test
    void testGermanIdMasking() {
        String input = "German ID: 1234567890 and old format: T12345678";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("1234567890"));
        assertFalse(result.contains("T12345678"));
        assertTrue(result.contains("German ID:"));
        assertTrue(result.contains("and old format:"));
        
        System.out.println("[DEBUG_LOG] German ID - Original: " + input);
        System.out.println("[DEBUG_LOG] German ID - Masked: " + result);
    }

    @Test
    void testItalianCodiceFiscaleMasking() {
        String input = "Italian CF: RSSMRA85M01H501Z";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("RSSMRA85M01H501Z"));
        assertTrue(result.contains("Italian CF:"));
        
        System.out.println("[DEBUG_LOG] Italian CF - Original: " + input);
        System.out.println("[DEBUG_LOG] Italian CF - Masked: " + result);
    }

    @Test
    void testPortugueseCartaoCidadaoMasking() {
        String input = "Portuguese CC: 12345678 9 AB0";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("12345678 9 AB0"));
        assertTrue(result.contains("Portuguese CC:"));
        
        System.out.println("[DEBUG_LOG] Portuguese CC - Original: " + input);
        System.out.println("[DEBUG_LOG] Portuguese CC - Masked: " + result);
    }

    @Test
    void testDutchBsnMasking() {
        String input = "Dutch BSN: 123456789";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("123456789"));
        assertTrue(result.contains("Dutch BSN:"));
        
        System.out.println("[DEBUG_LOG] Dutch BSN - Original: " + input);
        System.out.println("[DEBUG_LOG] Dutch BSN - Masked: " + result);
    }

    @Test
    void testBelgianNrnMasking() {
        String input = "Belgian NRN: 85.07.30-033.84";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("85.07.30-033.84"));
        assertTrue(result.contains("Belgian NRN:"));
        
        System.out.println("[DEBUG_LOG] Belgian NRN - Original: " + input);
        System.out.println("[DEBUG_LOG] Belgian NRN - Masked: " + result);
    }

    @Test
    void testSwissAhvMasking() {
        String input = "Swiss AHV: 756.1234.5678.90";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("756.1234.5678.90"));
        assertTrue(result.contains("Swiss AHV:"));
        
        System.out.println("[DEBUG_LOG] Swiss AHV - Original: " + input);
        System.out.println("[DEBUG_LOG] Swiss AHV - Masked: " + result);
    }

    @Test
    void testSwedishPersonnummerMasking() {
        String input = "Swedish PN: 850730-1234 and full format: 19850730-1234";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("850730-1234"));
        assertFalse(result.contains("19850730-1234"));
        assertTrue(result.contains("Swedish PN:"));
        assertTrue(result.contains("and full format:"));
        
        System.out.println("[DEBUG_LOG] Swedish PN - Original: " + input);
        System.out.println("[DEBUG_LOG] Swedish PN - Masked: " + result);
    }

    @Test
    void testUkNinoMasking() {
        String input = "UK NINO: AB123456C";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("AB123456C"));
        assertTrue(result.contains("UK NINO:"));
        
        System.out.println("[DEBUG_LOG] UK NINO - Original: " + input);
        System.out.println("[DEBUG_LOG] UK NINO - Masked: " + result);
    }

    @Test
    void testMultipleEuropeanIds() {
        String input = "Spanish DNI: 12345678Z, German ID: 1234567890, UK NINO: AB123456C";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("12345678Z"));
        assertFalse(result.contains("1234567890"));
        assertFalse(result.contains("AB123456C"));
        assertTrue(result.contains("Spanish DNI:"));
        assertTrue(result.contains("German ID:"));
        assertTrue(result.contains("UK NINO:"));
        
        System.out.println("[DEBUG_LOG] Multiple European IDs - Original: " + input);
        System.out.println("[DEBUG_LOG] Multiple European IDs - Masked: " + result);
    }

    @Test
    void testEuropeanIdsCaseSensitivity() {
        // Test with lowercase letters which should still be detected since case-sensitive is false by default
        String input = "Spanish NIE: x1234567l and French CNI: 12ab34567";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("x1234567l"));
        assertFalse(result.contains("12ab34567"));
        assertTrue(result.contains("Spanish NIE:"));
        assertTrue(result.contains("and French CNI:"));
        
        System.out.println("[DEBUG_LOG] European IDs Case Sensitivity - Original: " + input);
        System.out.println("[DEBUG_LOG] European IDs Case Sensitivity - Masked: " + result);
    }

    // European Phone Number Tests

    @Test
    void testSpanishPhoneNumberMasking() {
        String input = "Contact: +34 612 345 678 or mobile 681234567";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+34 612 345 678"));
        assertFalse(result.contains("681234567"));
        assertTrue(result.contains("Contact:"));
        assertTrue(result.contains("or mobile"));
        
        System.out.println("[DEBUG_LOG] Spanish Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Spanish Phone - Masked: " + result);
    }

    @Test
    void testFrenchPhoneNumberMasking() {
        String input = "Call France: +33 1 42 68 53 00 or mobile +33 6 12 34 56 78";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+33 1 42 68 53 00"));
        assertFalse(result.contains("+33 6 12 34 56 78"));
        assertTrue(result.contains("Call France:"));
        assertTrue(result.contains("or mobile"));
        
        System.out.println("[DEBUG_LOG] French Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] French Phone - Masked: " + result);
    }

    @Test
    void testGermanPhoneNumberMasking() {
        String input = "Germany office: +49 30 12345678 and mobile +49 151 23456789";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+49 30 12345678"));
        assertFalse(result.contains("+49 151 23456789"));
        assertTrue(result.contains("Germany office:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] German Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] German Phone - Masked: " + result);
    }

    @Test
    void testItalianPhoneNumberMasking() {
        String input = "Italy landline: +39 02 1234567 and mobile +39 333 1234567";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+39 02 1234567"));
        assertFalse(result.contains("+39 333 1234567"));
        assertTrue(result.contains("Italy landline:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Italian Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Italian Phone - Masked: " + result);
    }

    @Test
    void testUkPhoneNumberMasking() {
        String input = "UK numbers: +44 20 7123 4567 and mobile +44 7700 900123";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+44 20 7123 4567"));
        assertFalse(result.contains("+44 7700 900123"));
        assertTrue(result.contains("UK numbers:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] UK Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] UK Phone - Masked: " + result);
    }

    @Test
    void testDutchPhoneNumberMasking() {
        String input = "Netherlands: +31 20 123 4567 and mobile +31 6 1234 5678";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+31 20 123 4567"));
        assertFalse(result.contains("+31 6 1234 5678"));
        assertTrue(result.contains("Netherlands:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Dutch Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Dutch Phone - Masked: " + result);
    }

    @Test
    void testBelgianPhoneNumberMasking() {
        String input = "Belgium office: +32 2 123 45 67 and mobile +32 475 12 34 56";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+32 2 123 45 67"));
        assertFalse(result.contains("+32 475 12 34 56"));
        assertTrue(result.contains("Belgium office:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Belgian Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Belgian Phone - Masked: " + result);
    }

    @Test
    void testSwissPhoneNumberMasking() {
        String input = "Switzerland: +41 44 123 45 67 and mobile +41 76 123 45 67";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+41 44 123 45 67"));
        assertFalse(result.contains("+41 76 123 45 67"));
        assertTrue(result.contains("Switzerland:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Swiss Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Swiss Phone - Masked: " + result);
    }

    @Test
    void testSwedishPhoneNumberMasking() {
        String input = "Sweden numbers: +46 8 123 456 78 and mobile +46 70 123 45 67";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+46 8 123 456 78"));
        assertFalse(result.contains("+46 70 123 45 67"));
        assertTrue(result.contains("Sweden numbers:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Swedish Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Swedish Phone - Masked: " + result);
    }

    @Test
    void testNorwegianPhoneNumberMasking() {
        String input = "Norway contact: +47 22 12 34 56 and mobile +47 900 12 345";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+47 22 12 34 56"));
        assertFalse(result.contains("+47 900 12 345"));
        assertTrue(result.contains("Norway contact:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Norwegian Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Norwegian Phone - Masked: " + result);
    }

    @Test
    void testDanishPhoneNumberMasking() {
        String input = "Denmark office: +45 33 12 34 56 and mobile +45 20 12 34 56";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+45 33 12 34 56"));
        assertFalse(result.contains("+45 20 12 34 56"));
        assertTrue(result.contains("Denmark office:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Danish Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Danish Phone - Masked: " + result);
    }

    @Test
    void testFinnishPhoneNumberMasking() {
        String input = "Finland: +358 9 123 4567 and mobile +358 40 123 4567";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+358 9 123 4567"));
        assertFalse(result.contains("+358 40 123 4567"));
        assertTrue(result.contains("Finland:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Finnish Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Finnish Phone - Masked: " + result);
    }

    @Test
    void testPolishPhoneNumberMasking() {
        String input = "Poland office: +48 22 123 45 67 and mobile +48 501 234 567";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+48 22 123 45 67"));
        assertFalse(result.contains("+48 501 234 567"));
        assertTrue(result.contains("Poland office:"));
        assertTrue(result.contains("and mobile"));
        
        System.out.println("[DEBUG_LOG] Polish Phone - Original: " + input);
        System.out.println("[DEBUG_LOG] Polish Phone - Masked: " + result);
    }

    @Test
    void testMultipleEuropeanPhoneNumbers() {
        String input = "Contacts: Spain +34 612 345 678, Germany +49 151 234567, UK +44 7700 900123";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+34 612 345 678"));
        assertFalse(result.contains("+49 151 234567"));
        assertFalse(result.contains("+44 7700 900123"));
        assertTrue(result.contains("Contacts: Spain"));
        assertTrue(result.contains("Germany"));
        assertTrue(result.contains("UK"));
        
        System.out.println("[DEBUG_LOG] Multiple European Phones - Original: " + input);
        System.out.println("[DEBUG_LOG] Multiple European Phones - Masked: " + result);
    }

    @Test
    void testEuropeanPhoneNumbersWithoutCountryCode() {
        String input = "Local numbers: 612345678 (Spain), 7700900123 (UK), 151234567 (Germany)";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        // Some numbers might be masked by general patterns
        assertTrue(result.contains("Local numbers:"));
        assertTrue(result.contains("(Spain)"));
        assertTrue(result.contains("(UK)"));
        assertTrue(result.contains("(Germany)"));
        
        System.out.println("[DEBUG_LOG] Local European Phones - Original: " + input);
        System.out.println("[DEBUG_LOG] Local European Phones - Masked: " + result);
    }

    @Test
    void testEuropeanPhoneNumbersVariousFormats() {
        String input = "Various formats: +34-612-345-678, +33 6.12.34.56.78, +49(151)234567, +44 7700 900 123";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+34-612-345-678"));
        assertFalse(result.contains("+33 6.12.34.56.78"));
        assertFalse(result.contains("+49(151)234567"));
        assertFalse(result.contains("+44 7700 900 123"));
        assertTrue(result.contains("Various formats:"));
        
        System.out.println("[DEBUG_LOG] Various Format European Phones - Original: " + input);
        System.out.println("[DEBUG_LOG] Various Format European Phones - Masked: " + result);
    }

    @Test 
    void testEuropeanPhoneNumbersWithUSPhone() {
        String input = "Mixed: US +1 555-123-4567, Spain +34 612 345 678, France +33 6 12 34 56 78";
        String result = piiMaskingService.maskPiiData(input);
        
        assertNotEquals(input, result);
        assertFalse(result.contains("+1 555-123-4567"));
        assertFalse(result.contains("+34 612 345 678"));
        assertFalse(result.contains("+33 6 12 34 56 78"));
        assertTrue(result.contains("Mixed: US"));
        assertTrue(result.contains("Spain"));
        assertTrue(result.contains("France"));
        
        System.out.println("[DEBUG_LOG] Mixed US/European Phones - Original: " + input);
        System.out.println("[DEBUG_LOG] Mixed US/European Phones - Masked: " + result);
    }
}