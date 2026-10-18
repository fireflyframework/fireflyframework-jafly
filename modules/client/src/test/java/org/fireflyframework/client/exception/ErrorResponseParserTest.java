package org.fireflyframework.client.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for ErrorResponseParser.
 */
@DisplayName("ErrorResponseParser Tests")
class ErrorResponseParserTest {

    @Test
    @DisplayName("Should parse simple error message from JSON")
    void shouldParseSimpleErrorMessageFromJson() {
        // Given
        String json = "{\"error\":\"Something went wrong\"}";

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).isEqualTo("Something went wrong");
    }

    @Test
    @DisplayName("Should parse message field from JSON")
    void shouldParseMessageFieldFromJson() {
        // Given
        String json = "{\"message\":\"Error occurred\"}";

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).isEqualTo("Error occurred");
    }

    @Test
    @DisplayName("Should parse error_description field from JSON")
    void shouldParseErrorDescriptionFieldFromJson() {
        // Given
        String json = "{\"error_description\":\"Detailed error\"}";

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).isEqualTo("Detailed error");
    }

    @Test
    @DisplayName("Should parse detail field from JSON")
    void shouldParseDetailFieldFromJson() {
        // Given
        String json = "{\"detail\":\"Error details\"}";

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).isEqualTo("Error details");
    }

    @Test
    @DisplayName("Should return original JSON if no error field found")
    void shouldReturnOriginalJsonIfNoErrorFieldFound() {
        // Given
        String json = "{\"status\":\"failed\"}";

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).isEqualTo(json);
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully")
    void shouldHandleInvalidJsonGracefully() {
        // Given
        String invalidJson = "not a json";

        // When
        String message = ErrorResponseParser.parseErrorMessage(invalidJson);

        // Then
        assertThat(message).isEqualTo(invalidJson);
    }

    @Test
    @DisplayName("Should handle null input")
    void shouldHandleNullInput() {
        // When
        String message = ErrorResponseParser.parseErrorMessage(null);

        // Then
        assertThat(message).isNull();
    }

    @Test
    @DisplayName("Should handle empty string")
    void shouldHandleEmptyString() {
        // When
        String message = ErrorResponseParser.parseErrorMessage("");

        // Then
        assertThat(message).isNull();
    }

    @Test
    @DisplayName("Should parse Spring Boot validation errors")
    void shouldParseSpringBootValidationErrors() {
        // Given
        String json = """
            {
                "errors": [
                    {
                        "field": "email",
                        "message": "Invalid email format",
                        "code": "email.invalid",
                        "rejectedValue": "not-an-email"
                    },
                    {
                        "field": "age",
                        "message": "Must be at least 18",
                        "code": "age.min"
                    }
                ]
            }
            """;

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(json);

        // Then
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getField()).isEqualTo("email");
        assertThat(errors.get(0).getMessage()).isEqualTo("Invalid email format");
        assertThat(errors.get(0).getCode()).isEqualTo("email.invalid");
        assertThat(errors.get(0).getRejectedValue()).isEqualTo("not-an-email");
        
        assertThat(errors.get(1).getField()).isEqualTo("age");
        assertThat(errors.get(1).getMessage()).isEqualTo("Must be at least 18");
        assertThat(errors.get(1).getCode()).isEqualTo("age.min");
    }

    @Test
    @DisplayName("Should parse RFC 7807 Problem Details validation errors")
    void shouldParseRfc7807ValidationErrors() {
        // Given
        String json = """
            {
                "type": "https://example.com/validation-error",
                "title": "Validation Failed",
                "status": 422,
                "invalid-params": [
                    {
                        "name": "username",
                        "reason": "Username is required"
                    },
                    {
                        "name": "password",
                        "reason": "Password must be at least 8 characters"
                    }
                ]
            }
            """;

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(json);

        // Then
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getField()).isEqualTo("username");
        assertThat(errors.get(0).getMessage()).isEqualTo("Username is required");
        
        assertThat(errors.get(1).getField()).isEqualTo("password");
        assertThat(errors.get(1).getMessage()).isEqualTo("Password must be at least 8 characters");
    }

    @Test
    @DisplayName("Should parse custom validation errors format")
    void shouldParseCustomValidationErrorsFormat() {
        // Given
        String json = """
            {
                "validationErrors": [
                    {
                        "field": "name",
                        "message": "Name is required"
                    }
                ]
            }
            """;

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(json);

        // Then
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("name");
        assertThat(errors.get(0).getMessage()).isEqualTo("Name is required");
    }

    @Test
    @DisplayName("Should return empty list for invalid JSON")
    void shouldReturnEmptyListForInvalidJson() {
        // Given
        String invalidJson = "not a json";

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(invalidJson);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list for null input")
    void shouldReturnEmptyListForNullInput() {
        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(null);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when no validation errors found")
    void shouldReturnEmptyListWhenNoValidationErrorsFound() {
        // Given
        String json = "{\"error\":\"Something went wrong\"}";

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(json);

        // Then
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should handle nested error objects")
    void shouldHandleNestedErrorObjects() {
        // Given
        String json = """
            {
                "error": {
                    "message": "Nested error message"
                }
            }
            """;

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).contains("Nested error message");
    }

    @Test
    @DisplayName("Should prioritize message field over error field")
    void shouldPrioritizeMessageFieldOverErrorField() {
        // Given
        String json = """
            {
                "error": "Secondary error",
                "message": "Primary message"
            }
            """;

        // When
        String message = ErrorResponseParser.parseErrorMessage(json);

        // Then
        assertThat(message).isEqualTo("Primary message");
    }

    @Test
    @DisplayName("Should handle validation errors with missing optional fields")
    void shouldHandleValidationErrorsWithMissingOptionalFields() {
        // Given
        String json = """
            {
                "errors": [
                    {
                        "field": "email",
                        "message": "Invalid email"
                    }
                ]
            }
            """;

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(json);

        // Then
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("email");
        assertThat(errors.get(0).getMessage()).isEqualTo("Invalid email");
        assertThat(errors.get(0).getCode()).isNull();
        assertThat(errors.get(0).getRejectedValue()).isNull();
    }

    @Test
    @DisplayName("Should handle large JSON responses efficiently")
    void shouldHandleLargeJsonResponsesEfficiently() {
        // Given
        StringBuilder largeJson = new StringBuilder("{\"errors\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeJson.append(",");
            largeJson.append(String.format(
                "{\"field\":\"field%d\",\"message\":\"Error %d\"}", i, i
            ));
        }
        largeJson.append("]}");

        // When
        List<ValidationError> errors = ErrorResponseParser.parseValidationErrors(largeJson.toString());

        // Then
        assertThat(errors).hasSize(100);
        assertThat(errors.get(0).getField()).isEqualTo("field0");
        assertThat(errors.get(99).getField()).isEqualTo("field99");
    }
}

