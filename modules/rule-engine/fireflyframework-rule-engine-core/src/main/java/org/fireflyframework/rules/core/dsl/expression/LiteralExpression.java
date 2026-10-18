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

package org.fireflyframework.rules.core.dsl.expression;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Represents a literal value in an expression.
 * Examples: 42, "hello", true, null, [1, 2, 3]
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LiteralExpression extends Expression {
    
    /**
     * The literal value
     */
    private Object value;
    
    /**
     * The type of the literal value
     */
    private LiteralType literalType;
    
    public LiteralExpression(SourceLocation location, Object value) {
        super(location);
        this.value = value;
        this.literalType = determineLiteralType(value);
    }

    public LiteralExpression(Object value, LiteralType literalType) {
        super(null);
        this.value = value;
        this.literalType = literalType;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitLiteralExpression(this);
    }
    
    @Override
    public ExpressionType getExpressionType() {
        return switch (literalType) {
            case STRING -> ExpressionType.STRING;
            case NUMBER -> ExpressionType.NUMBER;
            case BOOLEAN -> ExpressionType.BOOLEAN;
            case LIST -> ExpressionType.LIST;
            case NULL -> ExpressionType.ANY;
        };
    }
    
    @Override
    public boolean isConstant() {
        return true; // Literals are always constant
    }
    
    @Override
    public boolean hasVariableReferences() {
        return false; // Literals never reference variables
    }
    
    @Override
    public String toDebugString() {
        if (value == null) {
            return "null";
        }
        
        return switch (literalType) {
            case STRING -> "\"" + value.toString() + "\"";
            case NUMBER, BOOLEAN -> value.toString();
            case LIST -> value.toString();
            case NULL -> "null";
        };
    }
    
    /**
     * Determine the literal type from the value
     */
    private static LiteralType determineLiteralType(Object value) {
        if (value == null) {
            return LiteralType.NULL;
        }
        
        if (value instanceof String) {
            return LiteralType.STRING;
        }
        
        if (value instanceof Number) {
            return LiteralType.NUMBER;
        }
        
        if (value instanceof Boolean) {
            return LiteralType.BOOLEAN;
        }
        
        if (value instanceof List) {
            return LiteralType.LIST;
        }
        
        // Default to string for unknown types
        return LiteralType.STRING;
    }
    
    /**
     * Get the value as a specific type with type checking
     */
    public String getStringValue() {
        if (literalType != LiteralType.STRING) {
            throw new IllegalStateException("Literal is not a string: " + literalType);
        }
        return (String) value;
    }
    
    public Number getNumberValue() {
        if (literalType != LiteralType.NUMBER) {
            throw new IllegalStateException("Literal is not a number: " + literalType);
        }
        return (Number) value;
    }
    
    public Boolean getBooleanValue() {
        if (literalType != LiteralType.BOOLEAN) {
            throw new IllegalStateException("Literal is not a boolean: " + literalType);
        }
        return (Boolean) value;
    }
    
    @SuppressWarnings("unchecked")
    public List<Object> getListValue() {
        if (literalType != LiteralType.LIST) {
            throw new IllegalStateException("Literal is not a list: " + literalType);
        }
        return (List<Object>) value;
    }
    
    /**
     * Check if this literal represents a null value
     */
    public boolean isNull() {
        return literalType == LiteralType.NULL;
    }
    
    /**
     * Create a string literal
     */
    public static LiteralExpression string(String value) {
        return new LiteralExpression(null, value);
    }
    
    /**
     * Create a number literal
     */
    public static LiteralExpression number(Number value) {
        return new LiteralExpression(null, value);
    }
    
    /**
     * Create a boolean literal
     */
    public static LiteralExpression bool(Boolean value) {
        return new LiteralExpression(null, value);
    }
    
    /**
     * Create a null literal
     */
    public static LiteralExpression nullValue() {
        return new LiteralExpression((SourceLocation) null, (Object) null);
    }
}


