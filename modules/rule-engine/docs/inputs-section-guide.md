# Understanding the `inputs` Section in YAML DSL

## 🎯 **What Goes in the `inputs` Section**

The `inputs` section should **ONLY** contain variables that your application passes at runtime via API requests. These are dynamic values that change with each rule evaluation.

## 📊 **The Three Variable Types**

### 1. 🔄 **Input Variables (camelCase)** - ✅ **Include in `inputs` section**
- **Source**: Passed via API requests in `inputData` parameter
- **Naming**: `camelCase` (e.g., `creditScore`, `annualIncome`, `employmentYears`)
- **Purpose**: Dynamic runtime values that change with each evaluation
- **Examples**: User data, transaction details, request parameters

### 2. 🏛️ **System Constants (UPPER_CASE_WITH_UNDERSCORES)** - ❌ **Do NOT include in `inputs`**
- **Source**: Stored in database, loaded automatically by the rule engine
- **Naming**: `UPPER_CASE_WITH_UNDERSCORES` (e.g., `MIN_CREDIT_SCORE`, `MAX_LOAN_AMOUNT`)
- **Purpose**: Predefined business rules and thresholds
- **Examples**: Minimum credit scores, maximum loan amounts, risk multipliers

### 3. ⚙️ **Computed Variables (snake_case)** - ❌ **Do NOT include in `inputs`**
- **Source**: Calculated during rule execution in `then` actions
- **Naming**: `snake_case` (e.g., `debt_to_income`, `final_score`, `risk_factor`)
- **Purpose**: Values derived from input variables and constants
- **Examples**: Calculated ratios, risk scores, approval statuses

## 🔄 **Variable Resolution Priority**

When the rule engine looks for a variable, it checks in this order:
1. **Computed Variables** (calculated during rule execution)
2. **Input Variables** (from your API request's `inputData`)
3. **System Constants** (from database)

## 📝 **Complete Example**

### ✅ **Correct Usage:**

```yaml
name: "Smart Credit Assessment"
description: "Demonstrates proper variable usage"

inputs:
  - creditScore          # ✅ camelCase: from API inputData
  - annualIncome         # ✅ camelCase: from API inputData
  - employmentYears      # ✅ camelCase: from API inputData
  - existingDebt         # ✅ camelCase: from API inputData

when:
  - creditScore at_least MIN_CREDIT_SCORE      # MIN_CREDIT_SCORE: database constant
  - annualIncome at_least 40000
  - employmentYears at_least 2

then:
  - calculate debt_to_income as existingDebt / annualIncome    # snake_case computed
  - calculate loan_to_income as requestedAmount / annualIncome # snake_case computed
  - set approval_status to "APPROVED"                         # snake_case computed

output:
  debt_to_income: debt_to_income      # Return computed variable
  approval_status: approval_status    # Return computed variable
```

### 🔗 **Corresponding API Request:**

```json
POST /api/v1/rules/evaluate
{
  "ruleDefinition": "...",
  "inputData": {
    "creditScore": 750,        // ← Matches inputs section
    "annualIncome": 85000,     // ← Matches inputs section
    "employmentYears": 3,      // ← Matches inputs section
    "existingDebt": 25000      // ← Matches inputs section
  }
}
```

### ❌ **Incorrect Usage:**

```yaml
# DON'T DO THIS:
inputs:
  - creditScore              # ✅ Correct: runtime input
  - MIN_CREDIT_SCORE         # ❌ Wrong: database constant
  - debt_to_income           # ❌ Wrong: computed variable
  - ANNUAL_INCOME            # ❌ Wrong: should be annualIncome (camelCase)
```

## 🚨 **Common Mistakes**

1. **Including database constants in inputs**
   ```yaml
   # ❌ Wrong
   inputs:
     - MIN_CREDIT_SCORE    # This comes from database automatically!
   ```

2. **Including computed variables in inputs**
   ```yaml
   # ❌ Wrong
   inputs:
     - debt_to_income      # This is calculated in 'then' actions!
   ```

3. **Wrong naming convention for inputs**
   ```yaml
   # ❌ Wrong
   inputs:
     - CREDIT_SCORE        # Should be creditScore (camelCase)
     - annual_income       # Should be annualIncome (camelCase)
   ```

## ✅ **Best Practices**

1. **Only include runtime values in inputs**
   - Values that come from your API request
   - Values that change with each evaluation
   - User-provided data, transaction details, etc.

2. **Use consistent naming conventions**
   - Input variables: `camelCase`
   - System constants: `UPPER_CASE_WITH_UNDERSCORES`
   - Computed variables: `snake_case`

3. **Let the system handle constants and computed variables**
   - Database constants are loaded automatically
   - Computed variables are created during execution
   - Don't declare them in the inputs section

## 🔍 **Validation**

The rule engine validates naming conventions and will reject rules that:
- Include UPPER_CASE variables in the inputs section
- Include snake_case variables in the inputs section
- Use inconsistent naming patterns

This ensures clear separation between variable types and prevents ambiguity in your rules.
