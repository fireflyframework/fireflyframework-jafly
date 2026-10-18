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

package org.fireflyframework.rules.core.dsl.action;

import org.fireflyframework.rules.core.dsl.ASTVisitor;
import org.fireflyframework.rules.core.dsl.SourceLocation;
import org.fireflyframework.rules.core.dsl.expression.Expression;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a run action that executes a function, REST call, or JSON operation and stores the result.
 * This action is used for non-mathematical operations that produce a result.
 * 
 * Examples: 
 *   - run user_data as rest_get("https://api.example.com/users/123")
 *   - run api_response as rest_post("https://api.example.com/data", requestBody)
 *   - run user_name as json_get(user_data, "name")
 *   - run max_value as max(value1, value2, value3)
 *   - run formatted as format_currency(amount)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RunAction extends Action {
    
    /**
     * Name of the variable to store the result
     */
    private String resultVariable;
    
    /**
     * Expression to execute (typically a function call, REST call, or JSON operation)
     */
    private Expression expression;
    
    public RunAction(SourceLocation location, String resultVariable, Expression expression) {
        super(location);
        this.resultVariable = resultVariable;
        this.expression = expression;
    }
    
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitRunAction(this);
    }
    
    @Override
    public boolean hasVariableReferences() {
        return expression.hasVariableReferences();
    }
    
    @Override
    public String toDebugString() {
        return String.format("run %s as %s", resultVariable, expression.toDebugString());
    }
}

