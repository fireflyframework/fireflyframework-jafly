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

package org.fireflyframework.rules.core.dsl.condition;

/**
 * Enumeration of comparison operators.
 */
public enum ComparisonOperator {
    EQUALS("equals", "=="),
    NOT_EQUALS("not_equals", "!="),
    LESS_THAN("less_than", "<"),
    LESS_THAN_OR_EQUAL("less_than_or_equal", "<="),
    GREATER_THAN("greater_than", ">"),
    GREATER_THAN_OR_EQUAL("greater_than_or_equal", ">="),
    GREATER_EQUAL("at_least", ">="),  // Alias
    LESS_EQUAL("at_most", "<="),      // Alias
    CONTAINS("contains"),
    NOT_CONTAINS("not_contains"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    MATCHES("matches"),
    NOT_MATCHES("not_matches"),
    BETWEEN("between"),
    NOT_BETWEEN("not_between"),
    IN("in"),
    NOT_IN("not_in"),
    IN_LIST("in_list"),         // Alias
    NOT_IN_LIST("not_in_list"), // Alias
    EXISTS("exists"),
    NOT_EXISTS("not_exists"),
    IS_NULL("is_null"),
    IS_NOT_NULL("is_not_null"),

    // Basic validation operators
    IS_EMPTY("is_empty"),
    IS_NOT_EMPTY("is_not_empty"),
    IS_NUMERIC("is_numeric"),
    IS_NOT_NUMERIC("is_not_numeric"),
    IS_EMAIL("is_email"),
    IS_PHONE("is_phone"),
    IS_DATE("is_date"),

    // Financial validation operators
    IS_POSITIVE("is_positive"),
    IS_NEGATIVE("is_negative"),
    IS_ZERO("is_zero"),
    IS_PERCENTAGE("is_percentage"),
    IS_CURRENCY("is_currency"),
    IS_CREDIT_SCORE("is_credit_score"),
    IS_SSN("is_ssn"),
    IS_ACCOUNT_NUMBER("is_account_number"),
    IS_ROUTING_NUMBER("is_routing_number"),

    // Date/time validation operators
    IS_BUSINESS_DAY("is_business_day"),
    IS_WEEKEND("is_weekend"),
    AGE_AT_LEAST("age_at_least"),
    AGE_LESS_THAN("age_less_than"),

    // Length operators
    LENGTH_EQUALS("length_equals"),
    LENGTH_GREATER_THAN("length_greater_than"),
    LENGTH_LESS_THAN("length_less_than"),

    // Additional financial operators
    IS_NON_ZERO("is_non_zero");
    
    private final String keyword;
    private final String symbol;
    
    ComparisonOperator(String keyword) {
        this.keyword = keyword;
        this.symbol = null;
    }
    
    ComparisonOperator(String keyword, String symbol) {
        this.keyword = keyword;
        this.symbol = symbol;
    }
    
    public String getKeyword() {
        return keyword;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public static ComparisonOperator fromKeyword(String keyword) {
        for (ComparisonOperator op : values()) {
            if (op.keyword.equals(keyword)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown comparison operator: " + keyword);
    }
    
    public static ComparisonOperator fromSymbol(String symbol) {
        for (ComparisonOperator op : values()) {
            if (symbol.equals(op.symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown comparison operator symbol: " + symbol);
    }
}
