# Firefly Framework Rule Engine - Quick Start Guide

**Get started with the Firefly Framework Rule Engine in 15 minutes**

*A beginner-friendly introduction to writing your first rules*

---

## What You'll Learn

- How to write a basic rule in 5 minutes
- Essential DSL syntax for common scenarios
- Step-by-step examples with explanations
- When to use simple vs. complex syntax

---

## Your First Rule in 5 Minutes

Let's create a simple credit approval rule:

```yaml
name: "Basic Credit Check"
description: "Approve loans for customers with good credit"

inputs:
  - creditScore
  - annualIncome

when:
  - creditScore at_least 650
  - annualIncome greater_than 40000

then:
  - set decision to "APPROVED"
  - set reason to "Meets minimum requirements"

else:
  - set decision to "DECLINED"
  - set reason to "Credit score or income too low"

output:
  decision: text
  reason: text
```

**That's it!** You've written your first rule. Let's break it down:

### Required Sections

Every rule needs these four sections:

1. **`name`** - A human-readable identifier
2. **`description`** - What the rule does
3. **`inputs`** - Variables that come from your application
4. **`output`** - Variables the rule will return

### Logic Sections

Choose **one** of these approaches:

- **Simple Logic**: `when` + `then` + `else` (recommended for beginners)
- **Complex Logic**: `conditions` blocks (for advanced scenarios)
- **Multiple Rules**: `rules` array (for sequential processing)

---

## Essential Syntax Patterns

### 1. Basic Comparisons

```yaml
when:
  - creditScore at_least 650        # Greater than or equal
  - age less_than 65                # Less than
  - status equals "ACTIVE"          # Equals
  - type not_equals "SUSPENDED"     # Not equals
```

### 2. Setting Variables

```yaml
then:
  - set approval_status to "APPROVED"
  - set risk_level to "LOW"
  - set processing_date to "2025-01-15"
```

### 3. Simple Calculations

```yaml
then:
  - calculate monthly_income as annualIncome / 12
  - calculate debt_ratio as monthlyDebt / monthly_income
  - calculate total_score as creditScore + incomeScore
```

### 4. Basic Conditions

```yaml
when:
  - age between 18 and 65
  - status in_list ["ACTIVE", "PENDING"]
  - email contains "@company.com"
  - exists guarantorInfo
```

---

## Common Beginner Examples

### Example 1: Age Verification

```yaml
name: "Age Verification"
description: "Check if customer meets age requirements"

inputs:
  - customerAge

when:
  - customerAge at_least 18
  - customerAge at_most 65

then:
  - set eligible to true
  - set message to "Age requirements met"

else:
  - set eligible to false
  - set message to "Must be between 18 and 65 years old"

output:
  eligible: boolean
  message: text
```

### Example 2: Income-Based Pricing

```yaml
name: "Pricing Tier Assignment"
description: "Assign pricing tier based on annual income"

inputs:
  - annualIncome

when:
  - annualIncome greater_than 0

then:
  - if annualIncome at_least 100000 then set tier to "PREMIUM"
  - if annualIncome at_least 50000 then set tier to "STANDARD"
  - if annualIncome less_than 50000 then set tier to "BASIC"

  - if tier equals "PREMIUM" then set discount to 15
  - if tier equals "STANDARD" then set discount to 10
  - if tier equals "BASIC" then set discount to 5

else:
  - set tier to "INVALID"
  - set discount to 0

output:
  tier: text
  discount: number
```

### Example 3: Multi-Factor Validation

```yaml
name: "Account Validation"
description: "Validate customer account information"

inputs:
  - email
  - phone
  - creditScore

when:
  - email is_email
  - phone is_phone
  - creditScore is_credit_score

then:
  - set valid to true
  - set issues to []

else:
  - set valid to false
  - set issues to []
  - if not email is_email then append "Invalid email format" to issues
  - if not phone is_phone then append "Invalid phone format" to issues
  - if not creditScore is_credit_score then append "Invalid credit score" to issues

output:
  valid: boolean
  issues: list
```

### Example 4: Processing Lists with forEach

```yaml
name: "Transaction Summary"
description: "Calculate total and average from a list of transactions"

inputs:
  - transactions  # List of transaction amounts

when:
  - exists transactions

then:
  - set total to 0
  - set count to 0

  # Sum all transactions
  - forEach amount in transactions: calculate total as total + amount

  # Count transactions
  - forEach amount in transactions: add 1 to count

  # Calculate average
  - if count greater_than 0 then calculate average as total / count
  - if count equals 0 then set average to 0

else:
  - set total to 0
  - set count to 0
  - set average to 0

output:
  total: number
  count: number
  average: number
```

**Key forEach Concepts:**
- Use `forEach item in list: action` to process each element
- The iteration variable (`amount`) is available only within the forEach
- You can use `forEach item, index in list: action` to access the position
- Multiple actions can be separated by semicolons: `forEach x in list: action1; action2`

### Example 5: Conditional Loops with while

```yaml
name: "Accumulate to Target"
description: "Add values until reaching a target amount"

inputs:
  - increment
  - target

when:
  - exists increment
  - exists target

then:
  - set total to 0
  - set iterations to 0

  # Loop until target is reached
  - while total less_than target: calculate total as total + increment; add 1 to iterations

output:
  total: number
  iterations: number
```

**Key while Concepts:**
- Use `while condition: action` to repeat actions while condition is true
- Condition is checked **before** each iteration
- If condition is false initially, the loop never executes
- Maximum 1000 iterations to prevent infinite loops
- Multiple actions separated by semicolons: `while x < 10: action1; action2`

### Example 6: Guaranteed Execution with do-while

```yaml
name: "Process At Least Once"
description: "Execute action at least once, then check condition"

inputs:
  - startValue
  - maxValue

when:
  - exists startValue

then:
  - set current to startValue
  - set count to 0

  # Always executes at least once.
  # Grammar reminder: arithmetic actions are `<keyword> <value> <preposition> <target-variable>`,
  # so "multiply current by 2" is written `multiply 2 by current`.
  - do: multiply 2 by current; add 1 to count while current less_than maxValue

output:
  current: number
  count: number
```

**Key do-while Concepts:**
- Use `do: action while condition` for loops that execute at least once
- Condition is checked **after** each iteration
- Guarantees first execution even if condition is initially false
- Perfect for retry logic and validation scenarios
- Multiple actions separated by semicolons: `do: action1; action2 while condition`

---

## Variable Naming Rules

The rule engine uses naming conventions to understand your data:

| Type | Format | Example | Source |
|------|--------|---------|---------|
| **Input Variables** | `camelCase` | `creditScore`, `annualIncome` | From your API call |
| **System Constants** | `UPPER_CASE` | `MIN_CREDIT_SCORE` | From database |
| **Computed Variables** | `snake_case` | `debt_ratio`, `final_score` | Created by rules |

```yaml
inputs: [creditScore, annualIncome]  # camelCase from API

when:
  - creditScore >= MIN_CREDIT_SCORE  # UPPER_CASE from database

then:
  - calculate debt_ratio as monthlyDebt / annualIncome  # snake_case computed
```

---

## When to Use What

### Use Simple Syntax When:
- ✅ You have straightforward if/then logic
- ✅ You're new to the rule engine
- ✅ Your conditions fit on one line each
- ✅ You need quick, readable rules

### Consider Complex Syntax When:
- 🔄 You need nested conditional logic
- 🔄 You have many interconnected conditions
- 🔄 You need structured decision trees
- 🔄 You're building reusable rule components

### Use Multiple Rules When:
- 🔄 You need sequential processing steps
- 🔄 Later rules depend on earlier rule results
- 🔄 You want to break complex logic into stages
- 🔄 You need different validation phases

---

## Next Steps

Once you're comfortable with these basics:

1. **📖 Explore Patterns**: Check out the [Common Patterns Guide](common-patterns-guide.md) for real-world examples
2. **🏗️ Learn Advanced Features**: See the [Full YAML DSL Reference](yaml-dsl-reference.md) for all capabilities
3. **🎯 Follow Best Practices**: Review the [Governance Guidelines](governance-guidelines.md) for team standards
4. **💡 Try the Tutorial**: Work through the [B2B Credit Scoring Tutorial](b2b-credit-scoring-tutorial.md)

---

## Quick Reference Card

### Essential Operators
```yaml
# Comparisons
>=, <=, >, <, ==, !=
between, not_between
in_list, not_in_list

# String checks
contains, starts_with, ends_with
is_email, is_phone

# Existence
exists, is_null, is_not_null

# Actions
set variable to value
calculate variable as expression
if condition then action
```

### Basic Structure Template
<!-- doc-test:skip (template placeholder; `input1`, `condition1`, etc. are illustrative names, not real DSL) -->
```yaml
name: "Your Rule Name"
description: "What this rule does"

inputs: [input1, input2]
output: {result1: type, result2: type}

when:
  - condition1
  - condition2

then:
  - action1
  - action2

else:
  - fallback_action
```

---

*Ready to write more sophisticated rules? Continue with the [Common Patterns Guide](common-patterns-guide.md) →*
