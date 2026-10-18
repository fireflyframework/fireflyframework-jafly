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


package org.fireflyframework.web.error.converter;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.fireflyframework.web.error.exceptions.InvalidRequestException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Converter for JSON processing exceptions.
 * Converts JSON exceptions to InvalidRequestException.
 */
@Component
@ConditionalOnClass(JsonProcessingException.class)
public class JsonExceptionConverter implements ExceptionConverter<JsonProcessingException> {

    /**
     * Creates a new JsonExceptionConverter.
     */
    public JsonExceptionConverter() {
        // Default constructor
    }

    @Override
    public Class<JsonProcessingException> getExceptionType() {
        return JsonProcessingException.class;
    }

    @Override
    public BusinessException convert(JsonProcessingException exception) {
        if (exception instanceof JsonParseException) {
            return InvalidRequestException.withReason("JSON_PARSE_ERROR", "Invalid JSON format: " + exception.getMessage());
        } else if (exception instanceof UnrecognizedPropertyException) {
            UnrecognizedPropertyException ex = (UnrecognizedPropertyException) exception;
            return InvalidRequestException.forField(ex.getPropertyName(), "unknown-property", "Unrecognized field");
        } else if (exception instanceof InvalidFormatException) {
            InvalidFormatException ex = (InvalidFormatException) exception;
            String fieldName = ex.getPath().isEmpty() ? "unknown" : ex.getPath().get(0).getFieldName();
            return InvalidRequestException.forField(fieldName, ex.getValue().toString(), "Invalid format");
        } else if (exception instanceof MismatchedInputException) {
            MismatchedInputException ex = (MismatchedInputException) exception;
            String fieldName = ex.getPath().isEmpty() ? "unknown" : ex.getPath().get(0).getFieldName();
            return InvalidRequestException.forField(fieldName, "invalid-value", "Mismatched input");
        } else if (exception instanceof JsonMappingException) {
            JsonMappingException ex = (JsonMappingException) exception;
            String fieldName = ex.getPath().isEmpty() ? "unknown" : ex.getPath().get(0).getFieldName();
            return InvalidRequestException.forField(fieldName, "mapping-error", "JSON mapping error");
        }

        // Fallback
        return InvalidRequestException.withReason("JSON_ERROR", "JSON processing error: " + exception.getMessage());
    }
}
