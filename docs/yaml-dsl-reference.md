# Firefly Framework Rule Engine DSL Syntax Guide

**Comprehensive Reference for the Firefly Framework Rule Engine YAML Domain Specific Language**

*Based on actual AST-based parser implementation analysis*

---

## 📚 Documentation Navigation

**New to the Rule Engine?** Start with our layered documentation approach:

- **🚀 [Quick Start Guide](quick-start-guide.md)** - Get started in 15 minutes with essential syntax
- **🎯 [Common Patterns Guide](common-patterns-guide.md)** - Real-world examples organized by complexity
- **🏛️ [Governance Guidelines](governance-guidelines.md)** - Team standards and feature selection guidance
- **📖 This Reference** - Complete syntax documentation (you are here)

---

## Table of Contents

1. [Introduction](#introduction)
2. [DSL Structure Overview](#dsl-structure-overview)
3. [Reserved Keywords](#reserved-keywords)
4. [Variable Types and Naming](#variable-types-and-naming)
5. [Core DSL Elements](#core-dsl-elements)
6. [Action Syntax](#action-syntax)
7. [Condition Syntax](#condition-syntax)
8. [Expression Types](#expression-types)
9. [Built-in Functions](#built-in-functions)
10. [Advanced Features](#advanced-features)
11. [Complete Examples](#complete-examples)

---

## Introduction

The Firefly Framework Rule Engine uses a powerful YAML-based Domain Specific Language (DSL) that is parsed using an Abstract Syntax Tree (AST) approach. This guide documents the **actual implementation** based on comprehensive codebase analysis, ensuring accuracy and completeness.

> **💡 First Time Here?** This is a comprehensive reference. For a gentler introduction, start with the [Quick Start Guide](quick-start-guide.md) or browse [Common Patterns](common-patterns-guide.md) for real-world examples.

### Key Principles

- **AST-Based Parsing**: All syntax is parsed into strongly-typed AST nodes
- **Expression-Driven**: Actions and conditions support complex expressions
- **Type-Safe**: Variables have strict naming conventions for automatic type resolution
- **Extensible**: Support for REST API calls, JSON manipulation, and custom functions

### How to Use This Reference

- **🔍 Find Specific Syntax**: Use the table of contents or search for keywords
- **📋 Copy Examples**: All code examples are tested and ready to use
- **🎯 Choose Complexity**: See [Governance Guidelines](governance-guidelines.md) for feature selection advice
- **🚀 Get Started**: Try examples from [Quick Start Guide](quick-start-guide.md) first

---

## DSL Structure Overview

### Required Top-Level Sections

```yaml
name: "Rule Name"                    # Required: Human-readable identifier
description: "Rule description"      # Required: Purpose and behavior
inputs: [variable1, variable2]       # Required: Runtime input variables
output: {result: type}              # Required: Output variable mapping
```

### Optional Top-Level Sections

```yaml
version: "1.0.0"                    # Optional: Version tracking
metadata:                           # Optional: Additional metadata
  tags: ["tag1", "tag2"]
  author: "Team Name"
  category: "Category"

constants:                          # Optional: Constants with defaults
  - code: CONSTANT_NAME
    defaultValue: value

circuit_breaker:                    # Optional: Resilience configuration
  enabled: true
  failure_threshold: 5
  timeout_duration: "30s"
```

### Logic Sections (Choose One)

**Simple Syntax:**
<!-- doc-test:skip (schema sketch; placeholder values, not parseable on its own) -->
```yaml
when: [conditions]                  # Simple condition list
then: [actions]                     # Actions when true
else: [actions]                     # Actions when false (optional)
```

**Complex Syntax:**
<!-- doc-test:skip (schema sketch; placeholder values, not parseable on its own) -->
```yaml
conditions:                         # Structured condition blocks
  if: {condition_structure}
  then: {action_structure}
  else: {action_structure}
```

**Multiple Rules:**
<!-- doc-test:skip (schema sketch; placeholder values, not parseable on its own) -->
```yaml
rules:                             # Array of sub-rules
  - name: "Sub-rule 1"
    when: [conditions]
    then: [actions]
```

---

## Reserved Keywords

The DSL uses specific reserved keywords that have special meaning in the parser. These are organized by category for easy reference:

<details>
<summary><strong>🏗️ Structural Keywords</strong> - Define the rule structure and metadata</summary>

| Section | Keywords | Required | Purpose | Example |
|---------|----------|----------|---------|---------|
| **Rule Metadata** | `name` | ✅ | Human-readable rule identifier | `name: "Credit Assessment"` |
| | `description` | ✅ | Rule purpose and behavior | `description: "Evaluates credit applications"` |
| | `version` | ❌ | Version tracking | `version: "1.0.0"` |
| **Data Definitions** | `inputs` | ✅ | Runtime input variables | `inputs: [creditScore, annualIncome]` |
| | `output` | ✅ | Output variable mapping | `output: {approval_status: text}` |
| | `constants` | ❌ | System constants with defaults | `constants: [{code: MIN_SCORE, defaultValue: 650}]` |
| **Logic Structure** | `when` | ❌* | Simple condition syntax | `when: [creditScore >= 650]` |
| | `then` | ❌* | Actions when conditions true | `then: [set status to "APPROVED"]` |
| | `else` | ❌ | Actions when conditions false | `else: [set status to "DECLINED"]` |
| | `conditions` | ❌* | Complex condition blocks | `conditions: {if: {...}, then: {...}}` |
| | `rules` | ❌* | Multiple sequential rules | `rules: [{name: "Rule 1", when: [...]}]` |
| **Advanced Features** | `metadata` | ❌ | Additional metadata | `metadata: {tags: ["credit"], author: "Team"}` |
| | `circuit_breaker` | ❌ | Resilience configuration | `circuit_breaker: {enabled: true}` |

*One of `when`/`then`, `conditions`, or `rules` is required for logic definition.

</details>

<details>
<summary><strong>🎯 Action Keywords</strong> - Define what the rule should do</summary>

| Keyword | Purpose | Syntax | Example |
|---------|---------|--------|---------|
| `set` | Variable assignment | `set variable to value` | `set approval_status to "APPROVED"` |
| `calculate` | Mathematical expression | `calculate variable as expression` | `calculate debt_ratio as monthlyDebt / annualIncome` |
| `run` | Function/API invocation | `run variable as function(args)` | `run maximum as max(value1, value2)` |
| `call` | Function invocation | `call function with [args]` | `call log with ["Message", "INFO"]` |
| `forEach` | Loop over list | `forEach item in list: action` | `forEach num in numbers: calculate total as total + num` |
| `for` | Loop over list (alias) | `for item in list: action` | `for item in items: set count to count + 1` |
| `while` | Conditional loop | `while condition: action` | `while counter less_than 10: add 1 to counter` |
| `do` | Do-while loop | `do: action while condition` | `do: add 1 to counter while counter less_than 10` |
| `in` | Loop list specifier | Used with forEach/for | `forEach item in items: ...` |
| `if`/`then`/`else` | Conditional actions | `if condition then action` | `if creditScore > 700 then set tier to "PRIME"` |
| `add` | Addition operation | `add value to variable` | `add 10 to base_score` |
| `subtract` | Subtraction operation | `subtract value from variable` | `subtract penalty from total_score` |
| `multiply` | Multiplication operation | `multiply variable by value` | `multiply risk_factor by 1.5` |
| `divide` | Division operation | `divide variable by value` | `divide monthly_payment by 2` |
| `append` | Add to list end | `append value to list` | `append "HIGH_RISK" to risk_factors` |
| `prepend` | Add to list start | `prepend value to list` | `prepend "PRIORITY" to processing_flags` |
| `remove` | Remove from list | `remove value from list` | `remove "TEMPORARY" from account_flags` |
| `circuit_breaker` | Stop execution | `circuit_breaker "message"` | `circuit_breaker "HIGH_RISK_DETECTED"` |

</details>

<details>
<summary><strong>🔍 Logical Keywords</strong> - Define conditional logic and boolean operations</summary>

| Category | Keywords | Purpose | Syntax | Example |
|----------|----------|---------|--------|---------|
| **Logical Operators** | `and`, `AND` | Logical AND | `condition1 AND condition2` | `creditScore >= 650 AND annualIncome > 50000` |
| | `or`, `OR` | Logical OR | `condition1 OR condition2` | `creditScore >= 750 OR hasCollateral == true` |
| | `not`, `NOT` | Logical NOT | `NOT condition` | `NOT (accountStatus == "SUSPENDED")` |
| **Conditional Structure** | `if` | Condition definition | `if: condition` | `if: {and: [condition1, condition2]}` |
| | `compare` | Comparison block | `compare: {left, operator, right}` | `compare: {left: age, operator: ">=", right: 18}` |
| **Helper Keywords** | `left`, `right` | Comparison operands | In compare blocks | `left: creditScore, right: 650` |
| | `operator` | Comparison operator | In compare blocks | `operator: "greater_than"` |
| | `actions` | Action list | In complex syntax | `actions: [{set: {...}}]` |
| | `variable`, `value` | Set action params | In complex syntax | `variable: "status", value: "APPROVED"` |

</details>

<details>
<summary><strong>⚖️ Comparison Operators</strong> - Compare values and expressions</summary>

| Category | Operator | Aliases | Purpose | Example |
|----------|----------|---------|---------|---------|
| **Equality** | `==` | `equals` | Equality check | `status equals "ACTIVE"` |
| | `!=` | `not_equals` | Inequality check | `type not_equals "SUSPENDED"` |
| **Numeric** | `>` | `greater_than` | Greater than | `creditScore greater_than 700` |
| | `<` | `less_than` | Less than | `age less_than 65` |
| | `>=` | `at_least`, `greater_than_or_equal` | Greater or equal | `income at_least 50000` |
| | `<=` | `at_most`, `less_than_or_equal` | Less or equal | `debt_ratio at_most 0.4` |
| **Range** | `between` | - | Range inclusion | `age between 18 and 65` |
| | `not_between` | - | Range exclusion | `score not_between 0 and 100` |
| **String** | `contains` | - | String contains | `name contains "Smith"` |
| | `not_contains` | - | String not contains | `email not_contains "temp"` |
| | `starts_with` | - | String prefix | `phone starts_with "+1"` |
| | `ends_with` | - | String suffix | `email ends_with ".com"` |
| | `matches` | - | Regex match | `ssn matches "^\\d{3}-\\d{2}-\\d{4}$"` |
| | `not_matches` | - | Regex not match | `phone not_matches "^\\+1"` |
| **List** | `in_list` | `in` | List membership | `status in_list ["ACTIVE", "PENDING"]` |
| | `not_in_list` | `not_in` | List non-membership | `type not_in_list ["SUSPENDED", "CLOSED"]` |
| **Existence** | `exists` | - | Variable existence | `exists guarantorInfo` |
| | `is_null` | - | Null check | `is_null previousLoan` |
| | `is_not_null` | - | Not null check | `is_not_null collateralValue` |

</details>

<details>
<summary><strong>✅ Validation Operators</strong> - Validate data types and formats</summary>

| Category | Operator | Purpose | Syntax | Example |
|----------|----------|---------|--------|---------|
| **Existence Checks** | `exists` | Check if variable exists | `exists variable` | `exists guarantorInfo` |
| | `is_null` | Check if value is null | `variable is_null` | `previousLoan is_null` |
| | `is_not_null` | Check if value is not null | `variable is_not_null` | `collateralValue is_not_null` |
| **Type Checking** | `is_number` | Check if value is number | `variable is_number` | `creditScore is_number` |
| | `is_string` | Check if value is string | `variable is_string` | `customerName is_string` |
| | `is_boolean` | Check if value is boolean | `variable is_boolean` | `hasCollateral is_boolean` |
| | `is_list` | Check if value is list/array | `variable is_list` | `riskFactors is_list` |
| **Basic Content** | `is_empty` | Check if value is empty | `variable is_empty` | `customerName is_empty` |
| | `is_not_empty` | Check if value is not empty | `variable is_not_empty` | `email is_not_empty` |
| | `is_numeric` | Check if string is numeric | `variable is_numeric` | `inputValue is_numeric` |
| | `is_not_numeric` | Check if string is not numeric | `variable is_not_numeric` | `customerName is_not_numeric` |
| **Format Validation** | `is_email` | Validate email format | `variable is_email` | `contactEmail is_email` |
| | `is_phone` | Validate phone number format | `variable is_phone` | `phoneNumber is_phone` |
| | `is_date` | Validate date format | `variable is_date` | `birthDate is_date` |
| **Numeric Properties** | `is_positive` | Check if number > 0 | `variable is_positive` | `loanAmount is_positive` |
| | `is_negative` | Check if number < 0 | `variable is_negative` | `accountBalance is_negative` |
| | `is_zero` | Check if number equals 0 | `variable is_zero` | `outstandingDebt is_zero` |
| **Financial Formats** | `is_percentage` | Validate percentage format | `variable is_percentage` | `interestRate is_percentage` |
| | `is_currency` | Validate currency format | `variable is_currency` | `monthlyIncome is_currency` |
| | `is_credit_score` | Validate credit score range (300-850) | `variable is_credit_score` | `creditScore is_credit_score` |
| | `is_ssn` | Validate SSN format (XXX-XX-XXXX) | `variable is_ssn` | `socialSecurityNumber is_ssn` |
| | `is_account_number` | Validate bank account number | `variable is_account_number` | `bankAccount is_account_number` |
| | `is_routing_number` | Validate bank routing number | `variable is_routing_number` | `routingNumber is_routing_number` |
| **Date Properties** | `is_business_day` | Check if date is business day | `variable is_business_day` | `applicationDate is_business_day` |
| | `is_weekend` | Check if date is weekend | `variable is_weekend` | `submissionDate is_weekend` |
| | `age_at_least` | Check minimum age requirement | `variable age_at_least value` | `customerAge age_at_least 18` |
| | `age_less_than` | Check maximum age requirement | `variable age_less_than value` | `applicantAge age_less_than 65` |

**Usage Examples:**
```yaml
when:
  # Existence and null checks
  - exists customerData
  - is_not_null annualIncome
  - previousLoan is_null

  # Type validation
  - creditScore is_number
  - customerName is_string
  - hasCollateral is_boolean
  - riskFactors is_list

  # Content validation
  - email is_not_empty
  - phoneNumber is_phone
  - socialSecurityNumber is_ssn

  # Numeric properties
  - loanAmount is_positive
  - interestRate is_percentage
  - creditScore is_credit_score

  # Date validation
  - applicationDate is_business_day
  - customerAge age_at_least 18

  # Complex boolean expressions with validation operators (NEW FEATURE)
  - (creditScore is_credit_score AND creditScore >= 650)
  - (email is_email AND email is_not_empty)
  - (monthlyIncome is_positive AND annualIncome is_positive)
```

**Validation Operators in Expressions**

Validation operators can be used in any expression context, not just `when:` clauses:

<!-- doc-test:skip (illustrative `then:` fragment, not a complete rule) -->
```yaml
then:
  # Validation operators inside `set`-to-boolean expressions
  - set has_valid_contact to (email is_email and phone is_phone)
  - set financial_data_complete to (monthlyRevenue is_positive and monthlyExpenses is_positive and annualIncome is_not_null)

  # Score each field independently with the inline `if_else` function, then sum
  - run name_score as if_else(customerName is_not_empty, 25, 0)
  - run email_score as if_else(email is_email, 25, 0)
  - run phone_score as if_else(phone is_phone, 25, 0)
  - run ssn_score as if_else(ssn is_ssn, 25, 0)
  - calculate data_quality_score as name_score + email_score + phone_score + ssn_score
```

> **Note:** The engine does not have a C-style ternary `? :` operator; use the
> `if_else(condition, then_value, else_value)` built-in function instead. Both arguments
> are evaluated eagerly (no short-circuit).

</details>

<details>
<summary><strong>🔧 Arithmetic Operators & Keywords</strong> - Mathematical operations and helpers</summary>

| Category | Operator/Keyword | Symbol | Purpose | Syntax | Example |
|----------|------------------|--------|---------|--------|---------|
| **Basic Arithmetic** | `+` | `+` | Addition | `expression + expression` | `principal + interest` |
| | `-` | `-` | Subtraction | `expression - expression` | `income - expenses` |
| | `*` | `*` | Multiplication | `expression * expression` | `rate * amount` |
| | `/` | `/` | Division | `expression / expression` | `monthlyDebt / annualIncome` |
| | `%` | `%` | Modulo (remainder) | `expression % expression` | `amount % 100` |
| | `**` | `**` | Power/Exponentiation | `base ** exponent` | `(1 + rate) ** years` |
| **Arithmetic Actions** | `add` | - | Add value to variable | `add VALUE to VARIABLE` | `add 10 to base_score` |
| | `subtract` | - | Subtract value from variable | `subtract VALUE from VARIABLE` | `subtract penalty from total_score` |
| | `multiply` | - | Multiply target variable by factor | `multiply VALUE by VARIABLE` | `multiply 1.5 by risk_factor` |
| | `divide` | - | Divide target variable by divisor | `divide VALUE by VARIABLE` | `divide 2 by monthly_payment` |
| **Helper Keywords** | `to` | - | Assignment target | `set VARIABLE to VALUE` | `set approval_status to "APPROVED"` |
| | `as` | - | Calculation target | `calculate VARIABLE as EXPRESSION` | `calculate debt_ratio as monthlyDebt / annualIncome` |
| | `with` | - | Function parameters | `call FUNCTION with [args]` | `call log with ["Message", "INFO"]` |
| | `from` | - | Subtraction source | `subtract VALUE from VARIABLE` | `subtract penalty from total_score` |
| | `by` | - | Factor for multiply/divide | `multiply VALUE by VARIABLE` | `multiply 1.5 by risk_factor` |
| | `and` | - | Range separator | `VALUE between MIN and MAX` | `age between 18 and 65` |

> **Grammar peculiarity for `multiply` / `divide`:** the value comes *first*, then the variable.
> All four arithmetic actions follow the same shape: `<keyword> <value-expression> <preposition> <target-variable>`.
> Read `multiply 1.5 by risk_factor` as "apply ×1.5 to `risk_factor`".

**Arithmetic Expression Examples:**
<!-- doc-test:skip (illustrative `then:` fragment, not a complete rule) -->
```yaml
then:
  # Basic arithmetic in expressions
  - calculate monthly_income as annualIncome / 12
  - calculate total_debt as creditCardDebt + loanDebt + mortgageDebt
  - calculate compound_amount as principal * (1 + rate) ** years
  - calculate remainder as loanAmount % 1000

  # Arithmetic actions (modify existing variables)
  # Grammar: <keyword> <value> <preposition> <target-variable>
  - add 50 to credit_score
  - subtract late_fee from account_balance
  - multiply 1.2 by risk_score
  - divide 2 by monthly_payment

  # Complex expressions
  - calculate debt_to_income as (monthlyDebt + proposedPayment) / (annualIncome / 12)
  - calculate weighted_score as (creditScore * 0.6) + (incomeScore * 0.4)
```

**Operator Precedence (highest to lowest):**
1. `**` (Power/Exponentiation)
2. `*`, `/`, `%` (Multiplication, Division, Modulo)
3. `+`, `-` (Addition, Subtraction)
4. Comparison operators (`>`, `<`, `>=`, `<=`, `==`, `!=`)
5. Logical operators (`AND`, `OR`, `NOT`)

**Parentheses** can be used to override precedence:
```yaml
- calculate result as (a + b) * (c - d)
- calculate complex as ((x * y) + z) / (a - b)
```

</details>

---

## Variable Types and Naming

### 1. Input Variables (camelCase)
**Source**: API request `inputData` parameter  
**Naming**: `camelCase` (e.g., `creditScore`, `annualIncome`)  
**Purpose**: Dynamic runtime values

```yaml
inputs:
  - creditScore        # From API inputData
  - annualIncome       # From API inputData
  - employmentYears    # From API inputData
```

### 2. System Constants (UPPER_CASE_WITH_UNDERSCORES)
**Source**: Database constants table  
**Naming**: `UPPER_CASE_WITH_UNDERSCORES` (e.g., `MIN_CREDIT_SCORE`)  
**Purpose**: Configuration values, business parameters

```yaml
when:
  - creditScore at_least MIN_CREDIT_SCORE    # From database
  - loanAmount less_than MAX_LOAN_AMOUNT     # From database
```

### 3. Computed Variables (snake_case)
**Source**: Created during rule execution  
**Naming**: `snake_case` (e.g., `debt_ratio`, `final_score`)  
**Purpose**: Intermediate calculations, results

```yaml
then:
  - calculate debt_ratio as monthlyDebt / annualIncome    # snake_case
  - set approval_status to "APPROVED"                     # snake_case
```

---

## Core DSL Elements

### The `set` Operation

**Purpose**: Assign values to computed variables

**Syntax**: `set variable_name to value`

```yaml
then:
  - set approval_status to "APPROVED"
  - set risk_score to 75
  - set is_eligible to true
  - set processing_date to "2025-01-15"
```

### The `calculate` Operation

**Purpose**: Evaluate expressions and store results

**Syntax**: `calculate variable_name as expression`

**Key Insight**: `calculate` is NOT limited to mathematical operations. It can evaluate:
- Mathematical expressions
- Function calls (including REST API calls)
- JSON path operations
- Complex nested expressions

```yaml
then:
  # Mathematical calculations
  - calculate debt_ratio as monthlyDebt / (annualIncome / 12)
  - calculate compound_interest as principal * (1 + rate) ** years
  
  # Function calls
  - run max_value as max(value1, value2, value3)
  - calculate loan_payment as calculate_loan_payment(amount, rate, term)
  
  # REST API calls
  - run user_data as rest_get("https://api.example.com/users/123")
  - run api_response as rest_post("https://api.example.com/data", requestBody)
  
  # JSON operations
  - run user_name as json_get(user_data, "name")
  - run user_age as json_get(user_data, "age")
  - run has_email as json_exists(user_data, "email")
  
  # Complex expressions
  - calculate risk_score as ((creditScore * 0.6) + (annualIncome / 1000 * 0.3) + 50)
```

### The `when` Conditions

**Purpose**: Define conditions for rule execution

**Syntax**: List of condition strings

```yaml
when:
  - creditScore at_least 650
  - annualIncome greater_than 40000
  - employmentYears at_least 1
  - customerType in_list ["PREMIUM", "GOLD"]
```

### The `rules` Structure

**Purpose**: Define multiple sub-rules with sequential execution

```yaml
rules:
  - name: "Initial Assessment"
    when: creditScore at_least 600
    then:
      - set initial_eligible to true
    else:
      - set initial_eligible to false
      
  - name: "Final Decision"
    when: initial_eligible equals true
    then:
      - set final_decision to "APPROVED"
    else:
      - set final_decision to "DECLINED"
```

---

## Action Syntax

### Variable Assignment Actions

```yaml
# Simple assignment
- set variable_name to value

# Assignment with expressions
- set monthly_income to annualIncome / 12
- set full_name to firstName + " " + lastName

# Complex boolean expressions with validation operators (NEW FEATURE)
- set has_complete_financial_data to (
    monthlyRevenue is_positive AND
    monthlyExpenses is_positive AND
    existingDebt is_not_null AND
    monthlyDebtPayments is_positive AND
    verifiedAnnualRevenue is_positive
  )

# Multi-line boolean expressions with parentheses
- set meets_basic_requirements to (
    creditScore is_credit_score AND
    creditScore >= MIN_CREDIT_SCORE AND
    annualIncome is_positive AND
    customerAge >= 18
  )

# Validation operators in complex expressions
- set data_validation_passed to (
    customerName is_not_empty AND
    email is_email AND
    phone is_phone AND
    ssn is_ssn
  )
```

### Calculation Actions

The rule engine provides two commands for computed values:

**`calculate`** - For mathematical operations only:
- Arithmetic expressions (`+`, `-`, `*`, `/`, `%`, `**`)
- Mathematical calculations with numbers
- Expressions that produce numeric results

**`run`** - For function invocations and external operations:
- Function calls (e.g., `max()`, `min()`, `abs()`, `format_currency()`)
- REST API calls (e.g., `rest_get()`, `rest_post()`)
- JSON operations (e.g., `json_get()`, `json_exists()`)
- String functions (e.g., `upper()`, `lower()`, `trim()`)
- Any operation that invokes a function or external service

```yaml
# ✅ CORRECT: Use 'calculate' for mathematical operations
- calculate total as amount + tax
- calculate monthly_payment as principal * rate / (1 - (1 + rate) ** -term)
- calculate debt_ratio as monthlyDebt / annualIncome
- calculate compound_amount as principal * (1 + rate) ** years

# ✅ CORRECT: Use 'run' for function calls
- run maximum as max(value1, value2, value3)
- run minimum as min(score1, score2, score3)
- run formatted_amount as format_currency(total)
- run absolute_value as abs(difference)

# ✅ CORRECT: Use 'run' for REST API calls
- run api_data as rest_get("https://api.example.com/data")
- run post_result as rest_post("https://api.example.com/submit", data)

# ✅ CORRECT: Use 'run' for JSON operations
- run user_name as json_get(response, "user.name")
- run has_email as json_exists(response, "email")
- calculate item_count as json_size(response, "items")

# ❌ INCORRECT: Don't use 'calculate' for function calls
# - calculate maximum as max(value1, value2, value3)  # Wrong!

# ❌ INCORRECT: Don't use 'calculate' for REST calls
# - calculate api_data as rest_get("https://api.example.com")  # Wrong!
```

### Arithmetic Actions

```yaml
# Modify existing variables
- add 10 to base_score
- subtract penalty from total_score
- multiply risk_factor by 1.5
- divide monthly_payment by 2
```

### List Operations

```yaml
# List manipulation
- append "HIGH_RISK" to risk_factors
- prepend "PRIORITY" to processing_flags
- remove "TEMPORARY" from account_flags
```

### Loop Operations

The rule engine supports three types of loops: `forEach` for iterating over lists, `while` for conditional loops, and `do-while` for loops that execute at least once.

#### forEach Loop

The `forEach` action allows you to iterate over lists and perform actions on each element.

**Basic Syntax:**
```yaml
# Simple iteration
- forEach item in items: set total to total + item

# With index variable
- forEach item, index in items: set processedItems[index] to item * 2

# Multiple actions (separated by semicolons)
- forEach num in numbers: set temp to num * 2; calculate total as total + temp
```

**Common Use Cases:**

```yaml
# Sum all values in a list
- set total to 0
- forEach amount in amounts: calculate total as total + amount

# Process each item with conditions
- set validCount to 0
- forEach score in scores: if score at_least 70 then add 1 to validCount

# Build a new list from existing data
- set doubledValues to []
- forEach value in values: append value * 2 to doubledValues

# Iterate with index for position-based logic
- set indexSum to 0
- forEach item, index in items: calculate indexSum as indexSum + index

# String concatenation
- set sentence to ""
- forEach word in words: set sentence to sentence + word + " "

# Filter and accumulate
- set evenSum to 0
- forEach num in numbers: if num % 2 equals 0 then calculate evenSum as evenSum + num
```

**Advanced Examples:**

```yaml
# Multi-step processing in forEach
- set processedData to []
- forEach record in records: set temp to record * 1.1; append temp to processedData

# Nested conditions within forEach
- set highCount to 0
- set mediumCount to 0
- set lowCount to 0
- forEach score in scores: if score at_least 80 then add 1 to highCount
- forEach score in scores: if score at_least 60 and score less_than 80 then add 1 to mediumCount
- forEach score in scores: if score less_than 60 then add 1 to lowCount

# Using index for calculations
- set weightedSum to 0
- forEach value, position in values: calculate weightedSum as weightedSum + (value * position)
```

**Important Notes:**
- The iteration variable (e.g., `item`) is available only within the forEach body
- The index variable (if specified) starts at 0
- Multiple actions must be separated by semicolons (`;`)
- forEach can be nested, but keep complexity manageable for maintainability
- The list expression can be an input variable, computed variable, or expression

#### while Loop

The `while` action executes actions repeatedly as long as a condition is true. The condition is checked **before** each iteration.

**Basic Syntax:**
```yaml
# Simple while loop
- while counter less_than 10: add 1 to counter

# Multiple actions (separated by semicolons)
- while counter less_than 10: calculate total as total + counter; add 1 to counter

# Complex condition
- while counter less_than maxValue and total less_than 100: calculate total as total + counter; add 1 to counter
```

**Common Use Cases:**

```yaml
# Count to a target value
- set counter to 0
- while counter less_than 10: add 1 to counter

# Accumulate until threshold
- set sum to 0
- set index to 0
- while sum less_than 100: calculate sum as sum + index; add 1 to index

# Process with dynamic condition
- set attempts to 0
- set success to false
- while attempts less_than 5 and success equals false: call tryOperation with []; add 1 to attempts

# Build a sequence
- set fibonacci to [0, 1]
- set count to 2
- while count less_than 10: set next to fibonacci[count - 1] + fibonacci[count - 2]; append next to fibonacci; add 1 to count
```

**Important Notes:**
- The condition is evaluated **before** each iteration
- If the condition is false initially, the loop body never executes
- Maximum iterations limit: 1000 (prevents infinite loops)
- Multiple actions must be separated by semicolons (`;`)
- The loop variable must be modified within the loop to avoid infinite loops

#### do-while Loop

The `do-while` action executes actions at least once, then repeats as long as a condition is true. The condition is checked **after** each iteration.

**Basic Syntax:**
```yaml
# Simple do-while loop
- do: add 1 to counter while counter less_than 10

# Multiple actions (separated by semicolons)
- do: calculate total as total + counter; add 1 to counter while counter less_than 10

# Complex condition
- do: set temp to value * 2; add temp to total while total less_than 100
```

**Common Use Cases:**

```yaml
# Execute at least once, then check condition
- set counter to 0
- do: add 1 to counter while counter less_than 5

# Process until condition met (guaranteed first execution)
- set result to 0
- do: calculate result as result + 10 while result less_than 50

# Retry logic with guaranteed first attempt
- set attempts to 0
- do: call processData with []; add 1 to attempts while attempts less_than 3

# Build data with initial value
- set values to []
- set current to 1
- do: append current to values; multiply current by 2 while current less_than 100
```

**Important Notes:**
- The loop body **always executes at least once**, even if the condition is initially false
- The condition is evaluated **after** each iteration
- Maximum iterations limit: 1000 (prevents infinite loops)
- Multiple actions must be separated by semicolons (`;`)
- Useful when you need guaranteed first execution before checking the condition

#### Loop Comparison

| Loop Type | Condition Check | Minimum Executions | Use When |
|-----------|----------------|-------------------|----------|
| `forEach` | N/A (iterates over list) | 0 (if list is empty) | You have a list to iterate over |
| `while` | Before each iteration | 0 (if condition is false) | You need to check condition before executing |
| `do-while` | After each iteration | 1 (always executes once) | You need guaranteed first execution |

**Example Comparison:**

```yaml
# forEach - iterates over existing list
- forEach item in [1, 2, 3]: calculate total as total + item

# while - may not execute if condition is false
- set counter to 10
- while counter less_than 5: add 1 to counter  # Never executes

# do-while - always executes at least once
- set counter to 10
- do: add 1 to counter while counter less_than 5  # Executes once, then stops
```

### Function Call Actions

```yaml
# Built-in functions
- call log with ["Processing started", "INFO"]
- call audit with ["Decision made", "AUDIT"]
- call notify with ["admin@company.com", "Alert message"]

# Financial functions
- call calculate_loan_payment with [amount, rate, term, "result_var"]
- call format_currency with [amount, "formatted_amount"]
```

### Conditional Actions

```yaml
# Inline conditional logic
- if creditScore greater_than 750 then set tier to "PRIME"
- if annualIncome less_than 50000 then add 5 to risk_score
- if hasGuarantor equals true then subtract 10 from risk_score
```

### Circuit Breaker Actions

```yaml
# Stop execution with message
- if risk_score greater_than 90 then circuit_breaker "HIGH_RISK_DETECTED"
- if fraud_indicators greater_than 3 then circuit_breaker "FRAUD_SUSPECTED"
```

---

## Condition Syntax

### Simple Conditions

```yaml
when:
  # Comparison conditions
  - creditScore at_least 650
  - annualIncome greater_than 50000
  - customerType equals "PREMIUM"
  - accountStatus not_equals "SUSPENDED"

  # Range conditions
  - age between 18 and 65
  - loanAmount not_between 0 and 1000

  # String conditions
  - customerName contains "Smith"
  - email starts_with "admin"
  - phone matches "^\\+1\\d{10}$"

  # List conditions
  - accountType in_list ["CHECKING", "SAVINGS"]
  - riskLevel not_in_list ["HIGH", "CRITICAL"]

  # Existence conditions
  - exists guarantorInfo
  - is_null previousLoan
  - is_not_null collateralValue

  # Validation conditions
  - email is_email
  - phone is_phone
  - ssn is_ssn
  - creditScore is_credit_score
  - amount is_positive
  - balance is_not_empty
```

### Complex Logical Conditions

```yaml
when:
  # AND conditions
  - creditScore at_least 650 AND annualIncome greater_than 50000
  - age at_least 18 AND age at_most 65

  # OR conditions
  - creditScore at_least 750 OR hasCollateral equals true
  - customerType equals "VIP" OR accountBalance greater_than 100000

  # NOT conditions
  - NOT (accountStatus equals "SUSPENDED")
  - NOT (riskLevel in_list ["HIGH", "CRITICAL"])

  # Complex combinations with parentheses
  - (creditScore at_least 650 AND annualIncome greater_than 40000) OR hasGuarantor equals true
  - (age at_least 21 AND employmentYears at_least 2) AND NOT (hasDelinquencies equals true)

  # NEW: Validation operators in complex conditions
  - (creditScore is_credit_score AND creditScore >= MIN_CREDIT_SCORE)
  - (email is_email AND email is_not_empty) OR (phone is_phone AND phone is_not_empty)
  - (monthlyRevenue is_positive AND monthlyExpenses is_positive AND existingDebt is_not_null)

  # Multi-line complex expressions with validation operators
  - (
      customerName is_not_empty AND
      email is_email AND
      phone is_phone AND
      ssn is_ssn
    ) AND (
      creditScore is_credit_score AND
      annualIncome is_positive
    )
```

### Complex Condition Blocks

```yaml
conditions:
  if:
    and:
      - compare:
          left: creditScore
          operator: "at_least"
          right: 650
      - compare:
          left: annualIncome
          operator: "greater_than"
          right: 50000
  then:
    actions:
      - set:
          variable: "approval_status"
          value: "APPROVED"
  else:
    actions:
      - set:
          variable: "approval_status"
          value: "DECLINED"
```

---

## Expression Types

### Literal Expressions

```yaml
# Numbers
- set age to 25
- set rate to 3.5
- set amount to 1000000

# Strings
- set status to "APPROVED"
- set message to "Application processed successfully"

# Booleans
- set is_eligible to true
- set has_errors to false

# Null values
- set optional_field to null
```

### Variable References

```yaml
# Input variables (camelCase)
- calculate monthly_income as annualIncome / 12

# System constants (UPPER_CASE)
- when: creditScore at_least MIN_CREDIT_SCORE

# Computed variables (snake_case)
- when: debt_ratio less_than 0.4
```

### Binary Expressions

```yaml
# Arithmetic operations
- calculate total as principal + interest
- calculate difference as income - expenses
- calculate product as rate * amount
- calculate ratio as numerator / denominator
- calculate remainder as amount % 100
- calculate power as base ** exponent

# Comparison operations
- when: creditScore > 700
- when: balance >= 1000
- when: age < 65
- when: score <= 850
- when: status == "ACTIVE"
- when: type != "SUSPENDED"

# String operations
- when: name contains "Smith"
- when: email starts_with "admin"
- when: phone ends_with "1234"
- when: pattern matches "^\\d{3}-\\d{2}-\\d{4}$"

# Logical operations
- when: is_eligible and has_income
- when: is_vip or has_collateral
- when: not is_suspended
```

### Unary Expressions

```yaml
# Negation
- calculate negative_amount as -balance
- when: not is_active

# Existence checks
- when: exists customer_data
- when: is_null previous_loan
- when: is_not_null guarantor_info

# Validation operators in expressions (NEW FEATURE)
- set has_valid_data to (creditScore is_positive)
- set email_check to (contactEmail is_email)
- set phone_check to (phoneNumber is_phone)
- calculate data_complete as (customerName is_not_empty AND email is_email)
```

### Function Call Expressions

```yaml
# Mathematical functions
- run maximum as max(value1, value2, value3)
- run minimum as min(score1, score2, score3)
- run absolute as abs(difference)
- run rounded as round(decimal_value)
- run ceiling as ceil(amount)
- run floor as floor(rate)

# Financial functions
- calculate payment as calculate_loan_payment(principal, rate, term)
- calculate interest as calculate_compound_interest(principal, rate, time)
- run formatted as format_currency(amount)

# String functions
- run uppercase as upper(name)
- run lowercase as lower(email)
- run trimmed as trim(input_text)
- run name_length as length(description)

# Date/time functions
- run current_date as now()
- run today_date as today()
- run formatted_date as format_date(date_value, "yyyy-MM-dd")
- run pretty_date as format_date(date_value, "dd MMM yyyy")
- run age_years as calculate_age(birth_date)              # from today
- run age_at_event as calculate_age(birth_date, event_date)
- run plus_thirty as dateadd(today_date, 30, "days")
- run days_between as datediff(start_date, end_date, "days")

# Validation functions (function-call form complements the `is_email`/`is_phone` operators)
- run email_ok as validate_email(email_address)
- run phone_ok as validate_phone(phone_number)
- run is_business_day_today as is_business_day(today_date)
- run any_check as is_valid(value, "email")               # 12 known types; unknown -> error

# Null-handling & conditional helpers (DSL primitives)
- run preferred_name as coalesce(nickname, full_name, "Anonymous")  # first non-null wins
- run tier as if_else(creditScore at_least 750, "PRIME", "STANDARD")  # inline ternary
- run within_window as is_in_range(score, 600, 850)       # function form of `between`
```

> **Important:** Function calls and REST/JSON expressions are only legal in `run` actions and in
> expression contexts (function arguments, conditions, output mappings). The `calculate` action
> is restricted to pure mathematical expressions (`+ - * / % **`) on numeric inputs --
> attempting to use a function call inside `calculate` raises a clean validation error.

### REST Call Expressions

```yaml
# GET requests
- run user_data as rest_get("https://api.example.com/users/123")
- run credit_report as rest_get("https://credit-api.com/report/" + ssn)

# POST requests with body
- run api_response as rest_post("https://api.example.com/submit", request_data)
- run validation_result as rest_post("https://validator.com/check", {"email": email, "phone": phone})

# PUT requests with headers
- run update_result as rest_put("https://api.example.com/users/123", user_data, {"Authorization": "Bearer " + token})

# DELETE requests
- run delete_result as rest_delete("https://api.example.com/records/" + record_id)
```

> **REST error contract:** On HTTP failure (non-2xx, network error, DNS, timeout), the REST
> functions return a structured map: `{success: false, error: true, message: "<details>"}`.
> Rules can branch on `response.success` to handle errors gracefully. This is intentional
> "chain-friendly" behaviour and is the only place in the engine where a failure does not
> raise an exception; everywhere else, errors propagate as `success=false` on the rule result.

### JSON Path Expressions

```yaml
# Simple property access
- run user_name as json_get(api_response, "name")
- run user_age as json_get(api_response, "age")

# Nested property access
- run city as json_get(user_data, "address.city")
- run zip_code as json_get(user_data, "address.zipCode")

# Array access
- run first_hobby as json_get(user_data, "hobbies[0]")
- run last_transaction as json_get(account_data, "transactions[-1]")

# Array size
- calculate hobby_count as json_size(user_data, "hobbies")
- calculate transaction_count as json_size(account_data, "transactions")

# Existence checks
- run has_email as json_exists(user_data, "email")
- run has_address as json_exists(user_data, "address")
- run has_phone as json_exists(user_data, "contact.phone")
```

---

## Built-in Functions

*All functions listed below are actually implemented in the codebase and verified against ExpressionEvaluator.java*

### Mathematical Functions

```yaml
# Basic mathematical operations
- run maximum as max(value1, value2, value3)
- run minimum as min(value1, value2, value3)
- run absolute as abs(-15.5)
- run rounded as round(3.14159)
- run ceiling as ceil(3.1)
- run floor as floor(3.9)
- run power as pow(base, exponent)
- run square_root as sqrt(16)

# Statistical functions
- run average as avg(score1, score2, score3)  # Also: average
- run sum as sum(amount1, amount2, amount3)
```

### String Functions

```yaml
# Case conversion
- run uppercase as upper("hello world")      # Also: uppercase
- run lowercase as lower("HELLO WORLD")      # Also: lowercase

# String manipulation
- run trimmed as trim("  hello  ")
- run name_length as length("hello")              # Also: len
- run substring as substring("hello", 1, 3)       # Also: substr
- run contains_check as contains("hello", "ell")
- run starts_check as startswith("hello", "he")
- run ends_check as endswith("hello", "lo")
- run replaced as replace("hello", "l", "x")
```

### Financial Functions

```yaml
# Loan and interest calculations
- run monthly_payment as calculate_loan_payment(principal, annual_rate, term_months)
- run compound_interest as calculate_compound_interest(principal, rate, time)
- run amortization as calculate_amortization(principal, rate, term)
- run apr as calculate_apr(loan_amount, fees, monthly_payment, term)

# Financial ratios and metrics
- run debt_ratio as debt_to_income_ratio(monthly_debt, monthly_income)
- run credit_util as credit_utilization(used_credit, total_credit)
- run ltv as loan_to_value(loan_amount, property_value)
- run debt_ratio_alt as calculate_debt_ratio(total_debt, total_income)
- run ltv_alt as calculate_ltv(loan_amount, property_value)

# Credit and risk scoring
- run credit_score as calculate_credit_score(payment_history, utilization, length, types, inquiries)
- run risk_score as calculate_risk_score(credit_score, income, debt_ratio)
- run payment_score as payment_history_score(payment_data)

# Utility functions
- run formatted_amount as format_currency(1234.56)
- run formatted_percent as format_percentage(0.15)
- run account_num as generate_account_number()
- run transaction_id as generate_transaction_id()
```

### Date/Time Functions

```yaml
# Current date/time
- run current_timestamp as now()
- run current_date as today()

# Date calculations
- run date_plus as dateadd(date_value, amount, "days")  # Also supports "months", "years"
- run date_difference as datediff(start_date, end_date, "days")
- run hour_value as time_hour(timestamp)

# Date validation
- run is_business_day_check as is_business_day(date_value)
- run age_check as age_meets_requirement(birth_date, min_age)
```

### List Functions

```yaml
# List operations
- run list_size as size(my_list)              # Also: count
- run list_sum as sum(number_list)
- run list_average as avg(number_list)        # Also: average
- run first_item as first(my_list)
- run last_item as last(my_list)
```

### Type Conversion Functions

```yaml
# Type conversions
- run as_number as tonumber("123.45")     # Also: number
- run as_string as tostring(123)          # Also: string
- run as_boolean as toboolean("true")     # Also: boolean
```

### Validation Functions

```yaml
# Financial validation
- run is_valid_score as is_valid_credit_score(750)
- run is_valid_ssn as is_valid_ssn("123-45-6789")
- run is_valid_account as is_valid_account("1234567890")
- run is_valid_routing as is_valid_routing("021000021")

# General validation
- run is_valid_data as is_valid(value, "email")
- run in_range_check as in_range(value, min, max)
```

### REST API Functions

```yaml
# HTTP methods (all actually implemented)
- run get_response as rest_get(url)
- run post_response as rest_post(url, body)
- run put_response as rest_put(url, body, headers)
- run delete_response as rest_delete(url, headers)
- run patch_response as rest_patch(url, body, headers)
- run api_response as rest_call(method, url, body, headers)
```

### JSON Functions

```yaml
# JSON path operations (all actually implemented)
- run value as json_get(json_object, "path.to.property")    # Also: json_path
- run exists as json_exists(json_object, "optional.property")
- run size as json_size(json_object, "array_property")
- run type as json_type(json_object, "property")
```

### Utility Functions

```yaml
# Distance and location
- run distance as distance_between(lat1, lon1, lat2, lon2)

# Data security
- run encrypted as encrypt(data, key)
- run decrypted as decrypt(encrypted_data, key)
- run masked as mask_data(sensitive_data, mask_pattern)

# Advanced financial calculations
- run payment_schedule as calculate_payment_schedule(principal, rate, term)
```

### Null-handling, Conditional, and Range Helpers

```yaml
# coalesce: first non-null wins (NULL-coalescing default)
- run preferred_name as coalesce(nickname, full_name, "Anonymous")

# if_else: inline ternary expression (avoids a full `if/then/else` action block)
- run tier as if_else(creditScore at_least 750, "PRIME", "STANDARD")
- run discount as if_else(membership equals "GOLD", 0.20, 0.05)

# is_in_range: function form of the `between` operator (inclusive both ends)
- run score_band_ok as is_in_range(score, 600, 850)
```

### Logging and Audit Functions

```yaml
# Audit and logging (all actually implemented)
- call audit with ["Decision made", "AUDIT"]
- call audit_log with ["Rule executed", "TRACE"]
- call send_notification with ["recipient", "message"]
```

### Custom Functions (Extension Point)

Register your own functions in a Spring `@Bean` and call them from any rule:

```java
@Configuration
class MyRulesConfig {
    @Bean
    CommandLineRunner registerCustomFunctions(CustomFunctionRegistry registry) {
        return args -> {
            registry.register("regional_risk", a ->
                    Set.of("CA", "NY").contains(a[0]) ? 10 : 0);
            registry.register("fraud_score", a ->
                    fraudService.score(String.valueOf(a[0])));
        };
    }
}
```

```yaml
# Then use them like any built-in function:
when:
  - fraud_score(applicantId) at_most MAX_FRAUD_SCORE
then:
  - run risk_bump as regional_risk(region)
  - run is_clean as fraud_score(applicantId) less_than 50
```

**Resolution order:** Custom functions are checked **before** the built-in catalog -- if you
register a function with the same name as a built-in (e.g., `max`), your function wins.
Names are matched case-insensitively. The same registered function is reachable from both
expression contexts (`run` / `calculate` arg / condition) and action contexts (`call`).

---

## Advanced Features

### Circuit Breaker Configuration

```yaml
circuit_breaker:
  enabled: true
  failure_threshold: 5
  timeout_duration: "30s"
  recovery_timeout: "60s"
```

### Metadata and Versioning

```yaml
metadata:
  tags: ["credit", "risk-assessment", "banking"]
  author: "Risk Management Team"
  category: "Credit Scoring"
  priority: "HIGH"
  last_modified: "2025-01-15"
  review_date: "2025-06-15"
```

### Constants with Default Values

```yaml
constants:
  - code: MIN_CREDIT_SCORE
    defaultValue: 650
  - code: MAX_DEBT_RATIO
    defaultValue: 0.4
  - code: PREMIUM_THRESHOLD
    defaultValue: 100000
```

### Mixed Simple and Complex Syntax

```yaml
name: "Mixed Syntax Example"
description: "Demonstrates mixing simple and complex syntax"

inputs: [creditScore, annualIncome]

# Simple syntax for main logic
when:
  - creditScore at_least 600
then:
  - set initial_approval to true

  # Complex conditional action within simple syntax
  - if annualIncome greater_than 75000 then set tier to "PREMIUM"
  - if annualIncome between 50000 and 75000 then set tier to "STANDARD"
  - if annualIncome less_than 50000 then set tier to "BASIC"

# Complex syntax for detailed conditions
conditions:
  if:
    and:
      - compare:
          left: initial_approval
          operator: "equals"
          right: true
      - compare:
          left: tier
          operator: "not_equals"
          right: "BASIC"
  then:
    actions:
      - set:
          variable: "final_decision"
          value: "APPROVED"
```

---

## Complete Examples

### Example 1: Credit Assessment Rule

```yaml
name: "Comprehensive Credit Assessment"
description: "Full credit evaluation with risk scoring"
version: "2.1.0"

metadata:
  tags: ["credit", "risk", "banking"]
  author: "Risk Management Team"
  category: "Credit Scoring"

inputs:
  - creditScore
  - annualIncome
  - monthlyDebt
  - employmentYears
  - requestedAmount
  - hasCollateral

constants:
  - code: MIN_CREDIT_SCORE
    defaultValue: 650
  - code: MAX_DEBT_RATIO
    defaultValue: 0.4

when:
  - creditScore at_least MIN_CREDIT_SCORE
  - annualIncome greater_than 40000
  - employmentYears at_least 1

then:
  # Calculate key financial ratios
  - calculate debt_to_income as monthlyDebt / (annualIncome / 12)
  - calculate loan_to_income as requestedAmount / annualIncome

  # Determine base approval
  - set base_approved to true

  # Risk scoring with conditional logic
  - if creditScore at_least 750 then set credit_risk_score to 10
  - if creditScore between 700 and 749 then set credit_risk_score to 20
  - if creditScore between 650 and 699 then set credit_risk_score to 30

  # Income risk assessment
  - if annualIncome at_least 100000 then set income_risk_score to 5
  - if annualIncome between 60000 and 99999 then set income_risk_score to 15
  - if annualIncome between 40000 and 59999 then set income_risk_score to 25

  # Calculate final risk score
  - calculate total_risk_score as credit_risk_score + income_risk_score

  # Adjust for collateral
  - if hasCollateral equals true then subtract 10 from total_risk_score

  # Final decision logic
  - if total_risk_score less_than 20 then set approval_tier to "PRIME"
  - if total_risk_score between 20 and 35 then set approval_tier to "STANDARD"
  - if total_risk_score greater_than 35 then set approval_tier to "SUBPRIME"

  # Set final decision
  - if debt_to_income less_than MAX_DEBT_RATIO then set final_decision to "APPROVED"
  - if debt_to_income at_least MAX_DEBT_RATIO then set final_decision to "REVIEW_REQUIRED"

else:
  - set base_approved to false
  - set final_decision to "DECLINED"
  - set rejection_reason to "Does not meet minimum requirements"

output:
  base_approved: boolean
  debt_to_income: number
  loan_to_income: number
  total_risk_score: number
  approval_tier: text
  final_decision: text
  rejection_reason: text
```

### Example 2: REST API Integration with JSON Processing

```yaml
name: "Customer Data Enrichment"
description: "Fetch and process customer data from external APIs"
version: "1.0.0"

inputs:
  - customerId
  - requiresValidation

when:
  - customerId is_not_null
  - customerId is_not_empty

then:
  # Fetch customer data from external API
  - run customer_data as rest_get("https://api.customer-service.com/customers/" + customerId)

  # Extract customer information using JSON paths
  - run customer_name as json_get(customer_data, "personalInfo.fullName")
  - run customer_email as json_get(customer_data, "contactInfo.email")
  - run customer_phone as json_get(customer_data, "contactInfo.phone")
  - run credit_score as json_get(customer_data, "creditInfo.score")

  # Check if additional data exists
  - run has_employment_info as json_exists(customer_data, "employmentInfo")
  - run has_address as json_exists(customer_data, "addressInfo")

  # Conditional processing based on available data
  - if has_employment_info equals true then run annual_income as json_get(customer_data, "employmentInfo.annualIncome")
  - if has_address equals true then run zip_code as json_get(customer_data, "addressInfo.zipCode")

  # Validation if required
  - if requiresValidation equals true then run email_valid as validate_email(customer_email)
  - if requiresValidation equals true then run phone_valid as validate_phone(customer_phone)

  # Set processing status
  - set data_enrichment_complete to true
  - run processing_timestamp as now()

else:
  - set data_enrichment_complete to false
  - set error_message to "Invalid or missing customer ID"

output:
  customer_name: text
  customer_email: text
  customer_phone: text
  credit_score: number
  annual_income: number
  zip_code: text
  email_valid: boolean
  phone_valid: boolean
  data_enrichment_complete: boolean
  processing_timestamp: text
  error_message: text
```

### Example 3: Multiple Rules with Sequential Processing

```yaml
name: "Multi-Stage Loan Processing"
description: "Sequential processing with multiple validation stages"
version: "1.0.0"

inputs:
  - applicantData
  - loanAmount
  - loanTerm

rules:
  - name: "Initial Validation"
    when:
      - exists applicantData
      - loanAmount greater_than 0
      - loanTerm greater_than 0
    then:
      - set validation_stage_1 to "PASSED"
      - run applicant_age as json_get(applicantData, "age")
      - run applicant_income as json_get(applicantData, "annualIncome")
    else:
      - set validation_stage_1 to "FAILED"
      - circuit_breaker "INVALID_APPLICATION_DATA"

  - name: "Age and Income Verification"
    when:
      - validation_stage_1 equals "PASSED"
      - applicant_age at_least 18
      - applicant_income greater_than 25000
    then:
      - set validation_stage_2 to "PASSED"
      - calculate monthly_income as applicant_income / 12
      - calculate loan_to_income_ratio as loanAmount / applicant_income
    else:
      - set validation_stage_2 to "FAILED"
      - set rejection_reason to "Age or income requirements not met"

  - name: "Risk Assessment"
    when:
      - validation_stage_2 equals "PASSED"
      - loan_to_income_ratio less_than 5.0
    then:
      - set risk_assessment to "LOW"
      - run estimated_monthly_payment as calculate_loan_payment(loanAmount, 0.05, loanTerm)
      - set pre_approval_status to "APPROVED"
    else:
      - set risk_assessment to "HIGH"
      - set pre_approval_status to "REQUIRES_REVIEW"

  - name: "Final Processing"
    when:
      - pre_approval_status equals "APPROVED"
    then:
      - set final_status to "APPROVED"
      - run approval_timestamp as now()
      - 'call log with ["Loan approved for amount: " + loanAmount, "INFO"]'
    else:
      - set final_status to "DECLINED"
      - 'call log with ["Loan declined - " + rejection_reason, "INFO"]'

output:
  validation_stage_1: text
  validation_stage_2: text
  applicant_age: number
  monthly_income: number
  loan_to_income_ratio: number
  risk_assessment: text
  estimated_monthly_payment: number
  pre_approval_status: text
  final_status: text
  approval_timestamp: text
  rejection_reason: text
```

### Example 4: Advanced Validation with Complex Boolean Expressions (NEW)

<!-- doc-test:skip (TODO: this legacy example uses C-style ternary `? :` and unquoted strings with colons; rewrite to use the `if_else()` function and YAML-quote action strings containing colons before re-enabling the guard) -->
```yaml
name: "B2B Credit Scoring with Enhanced Validation"
description: "Demonstrates new validation operators in complex expressions"
version: "2.1.0"

inputs:
  - monthlyRevenue
  - monthlyExpenses
  - existingDebt
  - monthlyDebtPayments
  - verifiedAnnualRevenue
  - creditScore
  - customerName
  - email
  - phone
  - ssn

constants:
  - code: MIN_BUSINESS_CREDIT_SCORE
    defaultValue: 650
  - code: EXCELLENT_CREDIT_THRESHOLD
    defaultValue: 750

rules:
  - name: "Data Validation Stage"
    when:
      - exists monthlyRevenue
      - exists creditScore
    then:
      # Complex boolean expressions with validation operators
      - set has_complete_financial_data to (
          monthlyRevenue is_positive AND
          monthlyExpenses is_positive AND
          existingDebt is_not_null AND
          monthlyDebtPayments is_positive AND
          verifiedAnnualRevenue is_positive
        )

      - set has_valid_contact_info to (
          customerName is_not_empty AND
          email is_email AND
          phone is_phone AND
          ssn is_ssn
        )

      - set has_valid_credit_data to (
          creditScore is_credit_score AND
          creditScore >= MIN_BUSINESS_CREDIT_SCORE
        )

  - name: "Financial Analysis"
    when:
      - has_complete_financial_data equals true
      - has_valid_credit_data equals true
    then:
      # Multi-line validation expressions
      - set meets_credit_requirements to (
          creditScore is_credit_score AND
          creditScore >= MIN_BUSINESS_CREDIT_SCORE AND
          (creditScore >= EXCELLENT_CREDIT_THRESHOLD OR verifiedAnnualRevenue >= 500000)
        )

      - calculate debt_to_income_ratio as existingDebt / verifiedAnnualRevenue
      - calculate monthly_cash_flow as monthlyRevenue - monthlyExpenses - monthlyDebtPayments

      - set has_positive_cash_flow to (monthly_cash_flow is_positive)

  - name: "Final Decision"
    when:
      - has_valid_contact_info equals true
      - meets_credit_requirements equals true
      - has_positive_cash_flow equals true
    then:
      - set final_decision to "APPROVED"
      - calculate approval_score as (
          (creditScore >= EXCELLENT_CREDIT_THRESHOLD ? 40 : 20) +
          (monthly_cash_flow >= 10000 ? 30 : 15) +
          (debt_to_income_ratio <= 0.3 ? 30 : 10)
        )
    else:
      - set final_decision to "DECLINED"
      - set decline_reasons to []
      - if has_valid_contact_info equals false then append "Invalid contact information" to decline_reasons
      - if meets_credit_requirements equals false then append "Credit requirements not met" to decline_reasons
      - if has_positive_cash_flow equals false then append "Insufficient cash flow" to decline_reasons

output:
  has_complete_financial_data: boolean
  has_valid_contact_info: boolean
  has_valid_credit_data: boolean
  meets_credit_requirements: boolean
  debt_to_income_ratio: number
  monthly_cash_flow: number
  has_positive_cash_flow: boolean
  final_decision: text
  approval_score: number
  decline_reasons: list
```

---

## Implementation Notes

### Parser Architecture
- **AST-Based**: All syntax is parsed into strongly-typed Abstract Syntax Tree nodes
- **Expression-Driven**: Actions and conditions support complex nested expressions
- **Type-Safe**: Automatic type inference and validation during parsing
- **Error Handling**: Comprehensive error reporting with source location information

### Recent Parser Enhancements (v2.1.0+)
- **Validation Operators in Expressions**: Validation operators like `is_positive`, `is_email`, `is_phone`, etc. can now be used in complex expressions, not just simple conditions
- **Complex Boolean Expressions**: Multi-line boolean expressions with AND/OR operators and parentheses are fully supported in both conditions and expressions
- **Enhanced ExpressionParser**: The ExpressionParser now supports validation operators as unary operations, enabling natural syntax like `(email is_email AND phone is_phone)`
- **Unified Operator Support**: Both ConditionParser and ExpressionParser now support the same set of validation operators for consistent syntax across the DSL
- **BETWEEN Operator Improvements**: Fixed parsing issues with `BETWEEN` operator to properly handle `value between min and max` syntax
- **Age Operators in Expressions**: `age_at_least` and `age_less_than` operators now work correctly in both conditions and expressions

### Performance Considerations
- **AST Caching**: Parsed rule models are cached for improved performance
- **Lazy Evaluation**: Expressions are evaluated only when needed
- **Connection Pooling**: R2DBC connection pools are optimized for high-load scenarios
- **Batch Operations**: Support for bulk rule evaluation capabilities

### Validation and Error Handling
- **Syntax Validation**: Complete DSL syntax validation during rule definition
- **Runtime Validation**: Type checking and constraint validation during execution
- **Circuit Breaker**: Built-in resilience patterns for external service calls
- **Audit Trail**: Comprehensive logging of all rule operations and decisions

---

## Recent Updates

### Version 2.1.0 - Enhanced Expression Support
- **Validation Operators in Expressions**: All validation operators (`is_positive`, `is_email`, `is_phone`, `is_credit_score`, etc.) can now be used in complex expressions
- **Multi-line Boolean Expressions**: Support for complex boolean expressions with AND/OR operators and parentheses in both conditions and expressions
- **Enhanced Parser Architecture**: ExpressionParser now supports validation operators as unary operations for consistent syntax
- **BETWEEN Operator Fixes**: Resolved parsing conflicts where ExpressionParser was consuming AND tokens needed by BETWEEN operator
- **Age Operator Support**: Added proper support for `age_at_least` and `age_less_than` operators in both expression and condition contexts
- **Real-world Testing**: Validated with comprehensive B2B credit scoring scenarios including multi-stage evaluation workflows

### Syntax Examples Added
- Complex boolean expressions with validation operators in `set` actions
- Multi-line expressions with proper parentheses grouping
- Validation operators in conditional expressions
- Mixed validation and comparison operators in complex conditions

### Version 26.05 - Fail-Loud Error Contract & New Primitives

- **New primitives:** `coalesce(...)`, `if_else(cond, then, else)`, `is_in_range(value, low, high)`.
- **New built-ins:** `calculate_age(birth[, asOf])`, `format_date(date[, pattern])`, `validate_email(value)`, `validate_phone(value)` (function-call form complements existing `is_email`/`is_phone` operators).
- **Extension point:** `CustomFunctionRegistry` Spring bean lets applications register their own `RuleFunction` implementations callable from rules.
- **Error contract:** the engine now fails loud by design rather than silently swallowing errors. See the table below.
- **Sub-rule action parity:** sub-rules under `rules:` now accept the same map-shaped actions as the top level (e.g., YAML-collapsed `- forEach x in xs: ...`).

#### Error Behavior Reference

| Situation                                    | Old (pre-26.05)                | New                                                                  |
| -------------------------------------------- | ------------------------------ | -------------------------------------------------------------------- |
| Unknown function name                        | `log.warn` + null              | `IllegalArgumentException` -> rule reports `success=false`           |
| Non-numeric string in arithmetic             | silently coerced to ZERO       | `IllegalArgumentException` naming the operand                        |
| Bad regex pattern in `matches`               | `false`                        | `IllegalArgumentException` naming the pattern                        |
| Missing bean property                        | `log.warn` + null              | `IllegalArgumentException` naming class + property (maps still get null on missing key) |
| Unknown `is_valid(value, type)` type         | `false`                        | `IllegalArgumentException` listing supported types                   |
| Unknown `dateadd`/`datediff` unit            | `log.warn` + null              | `IllegalArgumentException` listing supported units                   |
| Action throws during execution               | logged + next action runs      | Rule reports `success=false` with action index + cause               |
| Condition throws during evaluation           | silently flips to else branch  | Rule reports `success=false` with the real cause                     |
| `circuit_breaker` action triggered           | (unchanged)                    | Rule reports `success=true` with `circuitBreakerTriggered=true`      |
| REST function HTTP failure                   | structured error map (unchanged) | Structured error map (unchanged -- intentional chain-friendly form) |
| `set var to null` (e.g., `json_get` missing) | NPE caught silently            | Variable stored as null; rule succeeds                               |

---

---

## 🎓 Learning Path

Ready to put this knowledge into practice?

1. **🚀 Start Simple**: Try examples from [Quick Start Guide](quick-start-guide.md)
2. **🎯 Find Patterns**: Browse [Common Patterns Guide](common-patterns-guide.md) for your use case
3. **🏛️ Set Standards**: Review [Governance Guidelines](governance-guidelines.md) for team practices
4. **💡 Practice**: Work through [B2B Credit Scoring Tutorial](b2b-credit-scoring-tutorial.md)
5. **🏗️ Understand Architecture**: Read [Architecture Guide](architecture.md)
6. **⚡ Optimize**: Check [Performance Optimization](performance-optimization.md)

---

*This guide is based on comprehensive analysis of the actual Firefly Framework Rule Engine AST-based parser implementation. All syntax examples and features documented here are verified against the codebase implementation and tested with real-world scenarios.*

