# Firefly Framework Rule Engine - Common Patterns Guide

**Real-world patterns organized by complexity tier**

*Discover the right features for your use case*

---

## How to Use This Guide

This guide organizes patterns by complexity to help you:
- 🎯 **Find the right approach** for your specific needs
- 📈 **Progress gradually** from simple to advanced features
- 🔍 **Discover capabilities** you might not know about
- ⚖️ **Choose appropriate complexity** for your team's context

### Complexity Tiers

| Tier | When to Use | Team Experience | Rule Maintenance |
|------|-------------|-----------------|------------------|
| **🟢 Basic** | Simple business rules | New to rule engine | Easy to modify |
| **🟡 Intermediate** | Multi-step logic | Some experience | Moderate complexity |
| **🔴 Advanced** | Complex workflows | Experienced team | Requires expertise |

---

## 🟢 Basic Patterns

*Perfect for getting started and handling common business scenarios*

### Pattern: Simple Approval Logic

**Use Case**: Basic yes/no decisions based on thresholds

```yaml
name: "Loan Pre-Approval"
description: "Quick pre-approval based on credit score and income"

inputs:
  - creditScore
  - annualIncome

when:
  - creditScore at_least 650
  - annualIncome at_least 40000

then:
  - set approved to true
  - set message to "Pre-approved for loan"

else:
  - set approved to false
  - set message to "Does not meet minimum requirements"

output:
  approved: boolean
  message: text
```

**Why This Pattern**: Simple, readable, easy to modify thresholds.

### Pattern: Tiered Classification

**Use Case**: Categorizing customers, products, or risk levels

```yaml
name: "Customer Tier Assignment"
description: "Assign customer tier based on account value"

inputs:
  - accountBalance
  - yearsWithBank

when:
  - accountBalance greater_than 0

then:
  - set benefits to []

  # Tier assignment with cascading logic
  - if accountBalance at_least 100000 then set tier to "PLATINUM"
  - if accountBalance at_least 50000 and accountBalance less_than 100000 then set tier to "GOLD"
  - if accountBalance at_least 10000 and accountBalance less_than 50000 then set tier to "SILVER"
  - if accountBalance less_than 10000 then set tier to "BRONZE"

  # Benefits based on tier
  - if tier equals "PLATINUM" then append "Priority Support" to benefits
  - if tier equals "PLATINUM" then append "Fee Waivers" to benefits
  - if tier in_list ["PLATINUM", "GOLD"] then append "Investment Advice" to benefits
  - if tier in_list ["PLATINUM", "GOLD", "SILVER"] then append "Online Banking" to benefits

else:
  - set tier to "INACTIVE"
  - set benefits to []

output:
  tier: text
  benefits: list
```

**Why This Pattern**: Clear tier logic, easy to add new tiers or benefits.

### Pattern: Data Validation

**Use Case**: Validating input data before processing

<!-- doc-test:skip (TODO: example uses unquoted error-message strings with embedded colons that break YAML parsing; rewrite messages without colons or wrap action lines in YAML quotes before re-enabling the guard) -->
```yaml
name: "Application Data Validation"
description: "Validate customer application data"

inputs:
  - email
  - phone
  - ssn
  - birthDate

when:
  - exists email
  - exists phone

then:
  - set errors to []
  - set valid to true

  # Individual field validation
  - if not email is_email then append "Invalid email format" to errors
  - if not phone is_phone then append "Invalid phone number" to errors
  - if not ssn is_ssn then append "Invalid SSN format" to errors
  - if not birthDate is_date then append "Invalid birth date" to errors

  # Update validity based on errors
  - calculate error_count as size(errors)
  - if error_count greater_than 0 then set valid to false

else:
  - set valid to false
  - set errors to ["Missing required fields: email, phone"]

output:
  valid: boolean
  errors: list
```

**Why This Pattern**: Comprehensive validation, clear error reporting.

---

## 🟡 Intermediate Patterns

*For teams ready to handle more sophisticated business logic*

### Pattern: List Processing with forEach

**Use Case**: Processing collections of data, aggregating values, filtering lists

```yaml
name: "Transaction Analysis"
description: "Analyze a list of transactions to calculate totals and identify patterns"

inputs:
  - transactions  # List of transaction amounts

when:
  - exists transactions

then:
  - set total to 0
  - set count to 0
  - set largeTransactionCount to 0
  - set averageAmount to 0

  # Sum all transactions
  - forEach amount in transactions: calculate total as total + amount

  # Count transactions
  - forEach amount in transactions: add 1 to count

  # Count large transactions (over 1000)
  - forEach amount in transactions: if amount greater_than 1000 then add 1 to largeTransactionCount

  # Calculate average
  - if count greater_than 0 then calculate averageAmount as total / count

  # Determine transaction pattern
  - if largeTransactionCount greater_than count / 2 then set pattern to "HIGH_VALUE"
  - if largeTransactionCount greater_than 0 and largeTransactionCount at_most count / 2 then set pattern to "MIXED"
  - if largeTransactionCount equals 0 then set pattern to "STANDARD"

else:
  - set total to 0
  - set count to 0
  - set pattern to "NO_DATA"

output:
  total: number
  count: number
  averageAmount: number
  largeTransactionCount: number
  pattern: text
```

**Why This Pattern**: Efficient list processing, multiple aggregations in one pass, clear business logic.

**Advanced forEach Example - Building Filtered Lists:**

```yaml
name: "Score Filtering and Categorization"
description: "Filter and categorize test scores"

inputs:
  - scores  # List of student scores

when:
  - exists scores

then:
  - set passingScores to []
  - set failingScores to []
  - set excellentCount to 0
  - set passCount to 0
  - set failCount to 0

  # Categorize each score
  - forEach score in scores: if score at_least 90 then add 1 to excellentCount
  - forEach score in scores: if score at_least 70 and score less_than 90 then add 1 to passCount
  - forEach score in scores: if score less_than 70 then add 1 to failCount

  # Build filtered lists
  - forEach score in scores: if score at_least 70 then append score to passingScores
  - forEach score in scores: if score less_than 70 then append score to failingScores

  # Calculate statistics
  - calculate totalStudents as excellentCount + passCount + failCount
  - if totalStudents greater_than 0 then calculate passRate as (excellentCount + passCount) / totalStudents * 100

else:
  - set passRate to 0
  - set totalStudents to 0

output:
  passingScores: list
  failingScores: list
  excellentCount: number
  passCount: number
  failCount: number
  passRate: number
  totalStudents: number
```

**Why This Pattern**: Demonstrates filtering, categorization, and statistical analysis on lists.

**forEach with Index Example:**

```yaml
name: "Position-Based Processing"
description: "Process items with position-dependent logic"

inputs:
  - items  # List of items to process

when:
  - exists items

then:
  - set weightedSum to 0
  - set processedItems to []

  # Calculate weighted sum (later items have higher weight)
  - forEach item, index in items: calculate weightedSum as weightedSum + (item * (index + 1))

  # Process with position awareness
  - forEach item, position in items: if position equals 0 then append item * 2 to processedItems
  - forEach item, position in items: if position greater_than 0 then append item to processedItems

output:
  weightedSum: number
  processedItems: list
```

**Why This Pattern**: Shows how to use index for position-dependent calculations and logic.

### Pattern: Conditional Loops with while

**Use Case**: Iterating until a condition is met, accumulating values, retry logic

```yaml
name: "Accumulate Until Threshold"
description: "Add values until reaching a target threshold"

inputs:
  - baseValue
  - threshold

when:
  - exists baseValue
  - exists threshold

then:
  - set total to 0
  - set counter to 0

  # Accumulate until threshold is reached
  - while total less_than threshold: calculate total as total + baseValue; add 1 to counter

  # Determine if threshold was reached
  - if total at_least threshold then set status to "THRESHOLD_REACHED"
  - if total less_than threshold then set status to "BELOW_THRESHOLD"

output:
  total: number
  counter: number
  status: string
```

**Why This Pattern**: Useful for accumulation, counting, and iterative calculations with dynamic stopping conditions.

**Advanced while Example - Fibonacci Sequence:**

```yaml
name: "Generate Fibonacci Sequence"
description: "Generate Fibonacci numbers up to a limit"

inputs:
  - limit  # Maximum value for Fibonacci numbers

when:
  - exists limit

then:
  - set fibonacci to [0, 1]
  - set index to 1
  - set current to 1

  # Generate Fibonacci numbers while under limit
  - while current less_than limit: set prev to fibonacci[index - 1]; set next to prev + current; append next to fibonacci; set current to next; add 1 to index

output:
  fibonacci: list
  count: number
```

**Why This Pattern**: Demonstrates sequence generation with dynamic termination based on calculated values.

### Pattern: Guaranteed Execution with do-while

**Use Case**: Retry logic, validation loops, processing that must happen at least once

```yaml
name: "Retry with Backoff"
description: "Attempt operation with guaranteed first try"

inputs:
  - initialValue
  - maxAttempts

when:
  - exists initialValue

then:
  - set attempts to 0
  - set result to initialValue

  # Always try at least once, then retry if needed
  - do: calculate result as result * 2; add 1 to attempts while attempts less_than maxAttempts and result less_than 100

  # Determine outcome
  - if result at_least 100 then set outcome to "SUCCESS"
  - if result less_than 100 then set outcome to "PARTIAL"

output:
  result: number
  attempts: number
  outcome: string
```

**Why This Pattern**: Ensures at least one execution, perfect for retry logic and validation scenarios.

**do-while Example - Build Until Condition:**

```yaml
name: "Build Sequence Until Target"
description: "Build a sequence with guaranteed initial value"

inputs:
  - multiplier
  - targetSize

when:
  - exists multiplier

then:
  - set sequence to []
  - set current to 1
  - set count to 0

  # Build sequence (always adds at least one value)
  - do: append current to sequence; multiply current by multiplier; add 1 to count while count less_than targetSize

output:
  sequence: list
  finalValue: number
```

**Why This Pattern**: Guarantees initial data population before checking continuation conditions.

### Pattern: Multi-Factor Risk Scoring

**Use Case**: Calculating risk scores from multiple factors

<!-- doc-test:skip (TODO: example uses C-style ternary `? :` which is not in the DSL; rewrite with the `if_else()` function before re-enabling the guard) -->
```yaml
name: "Credit Risk Assessment"
description: "Calculate risk score from multiple financial factors"

inputs:
  - creditScore
  - annualIncome
  - existingDebt
  - employmentYears

when:
  - creditScore greater_than 0
  - annualIncome greater_than 0

then:
  - set factors to []

  # Credit score component (40% weight)
  - if creditScore at_least 750 then set creditComponent to 40
  - if creditScore at_least 700 and creditScore less_than 750 then set creditComponent to 30
  - if creditScore at_least 650 and creditScore less_than 700 then set creditComponent to 20
  - if creditScore less_than 650 then set creditComponent to 10

  # Income component (30% weight)
  - if annualIncome at_least 100000 then set incomeComponent to 30
  - if annualIncome at_least 60000 and annualIncome less_than 100000 then set incomeComponent to 20
  - if annualIncome at_least 40000 and annualIncome less_than 60000 then set incomeComponent to 15
  - if annualIncome less_than 40000 then set incomeComponent to 5
  
  # Debt ratio component (20% weight)
  - calculate debt_ratio as existingDebt / annualIncome
  - if debt_ratio at_most 0.2 then set debtComponent to 20
  - if debt_ratio greater_than 0.2 and debt_ratio at_most 0.4 then set debtComponent to 15
  - if debt_ratio greater_than 0.4 and debt_ratio at_most 0.6 then set debtComponent to 10
  - if debt_ratio greater_than 0.6 then set debtComponent to 5

  # Employment stability (10% weight)
  - if employmentYears at_least 5 then set employmentComponent to 10
  - if employmentYears at_least 2 and employmentYears less_than 5 then set employmentComponent to 7
  - if employmentYears at_least 1 and employmentYears less_than 2 then set employmentComponent to 5
  - if employmentYears less_than 1 then set employmentComponent to 2

  # Calculate final score
  - calculate risk_score as creditComponent + incomeComponent + debtComponent + employmentComponent

  # Determine risk level
  - if risk_score at_least 80 then set risk_level to "LOW"
  - if risk_score at_least 60 and risk_score less_than 80 then set risk_level to "MEDIUM"
  - if risk_score at_least 40 and risk_score less_than 60 then set risk_level to "HIGH"
  - if risk_score less_than 40 then set risk_level to "VERY_HIGH"

  # Document contributing factors
  - calculate credit_factor as "Credit Score: " + tostring(creditScore) + " (Weight: " + tostring(creditComponent) + ")"
  - calculate income_factor as "Annual Income: " + tostring(annualIncome) + " (Weight: " + tostring(incomeComponent) + ")"
  - calculate debt_factor as "Debt Ratio: " + tostring(debt_ratio) + " (Weight: " + tostring(debtComponent) + ")"
  - calculate employment_factor as "Employment Years: " + tostring(employmentYears) + " (Weight: " + tostring(employmentComponent) + ")"

  - append credit_factor to factors
  - append income_factor to factors
  - append debt_factor to factors
  - append employment_factor to factors

else:
  - set risk_score to 0
  - set risk_level to "INVALID"
  - set factors to ["Invalid input data"]

output:
  riskScore: risk_score
  riskLevel: risk_level
  factors: factors
```

**Why This Pattern**: Transparent scoring, weighted factors, detailed audit trail.

### Pattern: Sequential Processing with Dependencies

**Use Case**: Multi-stage processes where later stages depend on earlier results

```yaml
name: "Loan Application Processing"
description: "Multi-stage loan processing with dependencies"

inputs:
  - applicantData
  - loanAmount
  - loanPurpose

rules:
  - name: "Initial Eligibility Check"
    when:
      - exists applicantData
      - loanAmount greater_than 0
      - loanAmount at_most 500000
    then:
      - run applicant_age as json_get(applicantData, "age")
      - run applicant_income as json_get(applicantData, "annualIncome")
      - run applicant_credit as json_get(applicantData, "creditScore")

      - set stage to "ELIGIBILITY_PASSED"
      - set eligibility_age to applicant_age
      - set eligibility_income to applicant_income
      - set eligibility_credit_score to applicant_credit
      - set eligibility_requested_amount to loanAmount
    else:
      - set stage to "ELIGIBILITY_FAILED"
      - set decision to "REJECTED"
      - circuit_breaker "Application does not meet basic eligibility"

  - name: "Risk Assessment"
    when:
      - stage equals "ELIGIBILITY_PASSED"
      - applicant_age at_least 18
      - applicant_income at_least 30000
      - applicant_credit at_least 600
    then:
      - calculate loan_to_income_ratio as loanAmount / applicant_income
      - calculate risk_score as (applicant_credit * 0.6) + ((applicant_income / 1000) * 0.4)

      - if risk_score at_least 500 and loan_to_income_ratio at_most 5 then set risk_level to "LOW"
      - if risk_score at_least 400 and loan_to_income_ratio at_most 7 then set risk_level to "MEDIUM"
      - if risk_score at_least 300 and loan_to_income_ratio at_most 10 then set risk_level to "HIGH"
      - if risk_score less_than 300 or loan_to_income_ratio greater_than 10 then set risk_level to "UNACCEPTABLE"

      - set stage to "RISK_ASSESSED"
      - set risk_score_value to risk_score
      - set risk_level_value to risk_level
      - set loan_to_income_ratio_value to loan_to_income_ratio
    else:
      - set stage to "RISK_FAILED"
      - set decision to "REJECTED"

  - name: "Final Decision"
    when:
      - stage equals "RISK_ASSESSED"
      - risk_level in_list ["LOW", "MEDIUM", "HIGH"]
    then:
      - if risk_level equals "LOW" then set decision to "APPROVED"
      - if risk_level equals "MEDIUM" then set decision to "APPROVED_WITH_CONDITIONS"
      - if risk_level equals "HIGH" then set decision to "MANUAL_REVIEW_REQUIRED"

      - set stage to "COMPLETED"
      - set final_decision_value to decision
      - run processed_at as now()
      - set processing_complete to true
    else:
      - set decision to "REJECTED"
      - set stage to "COMPLETED"

output:
  stage: text
  decision: text
  processing_complete: boolean
  processed_at: text
```

**Why This Pattern**: Clear stage progression, dependency management, comprehensive audit trail.

---

## 🔴 Advanced Patterns

*For experienced teams handling complex business workflows*

### Pattern: External API Integration with Fallbacks

**Use Case**: Enriching data from external services with error handling

```yaml
name: "Customer Data Enrichment with Fallbacks"
description: "Fetch customer data from multiple sources with fallback logic"

inputs:
  - customerId
  - requireCreditCheck

when:
  - exists customerId
  - not customerId is_empty

then:
  - set sources to []
  - set enriched_personal_info to null
  - set enriched_contact_info to null
  - set enriched_credit_info to null

  # Primary data source - Customer API
  - calculate customer_api_url as "https://api.customer-service.com/customers/" + tostring(customerId)
  - run customer_api_response as rest_get(customer_api_url)
  - run customer_api_success as json_exists(customer_api_response, "personalInfo")

  - if customer_api_success equals true then append "CUSTOMER_API" to sources
  - if customer_api_success equals true then run personal_info as json_get(customer_api_response, "personalInfo")
  - if customer_api_success equals true then run contact_info as json_get(customer_api_response, "contactInfo")
  
  # Credit bureau integration (conditional)
  - if requireCreditCheck equals true then calculate credit_api_url as "https://api.credit-bureau.com/reports/" + tostring(customerId)
  - if requireCreditCheck equals true then run credit_response as rest_get(credit_api_url)
  - if requireCreditCheck equals true then run credit_success as json_exists(credit_response, "creditScore")
  - if credit_success equals true then append "CREDIT_BUREAU" to sources
  - if credit_success equals true then run credit_info as json_get(credit_response, "creditData")

  # Fallback to internal database if external APIs fail
  - if customer_api_success equals false then calculate internal_api_url as "https://internal-api.company.com/customers/" + tostring(customerId)
  - if customer_api_success equals false then run internal_data as rest_get(internal_api_url)
  - if customer_api_success equals false then run internal_success as json_exists(internal_data, "basicInfo")
  - if internal_success equals true then append "INTERNAL_DB" to sources
  - if internal_success equals true then run personal_info as json_get(internal_data, "basicInfo")

  # Store enriched data in separate variables
  - if exists personal_info then set enriched_personal_info to personal_info
  - if exists contact_info then set enriched_contact_info to contact_info
  - if exists credit_info then set enriched_credit_info to credit_info

  # Determine data quality
  - calculate source_count as size(sources)
  - if source_count at_least 2 then set data_quality to "HIGH"
  - if source_count equals 1 then set data_quality to "MEDIUM"
  - if source_count equals 0 then set data_quality to "LOW"

  # Store metadata
  - set enrichment_sources to sources
  - set enrichment_quality to data_quality
  - run enrichment_timestamp as now()
  - set enrichment_customer_id to customerId

else:
  - set enriched_personal_info to null
  - set enriched_contact_info to null
  - set enriched_credit_info to null
  - set data_quality to "INVALID"
  - set sources to []

output:
  enriched_personal_info: object
  enriched_contact_info: object
  enriched_credit_info: object
  dataQuality: data_quality
  sources: list
```

**Why This Pattern**: Resilient data integration, multiple fallbacks, quality tracking.

---

## Pattern Selection Guide

### Choose Basic Patterns When:
- ✅ Your team is new to rule engines
- ✅ Rules change frequently and need easy modification
- ✅ Business logic is straightforward
- ✅ You need quick time-to-market

### Choose Intermediate Patterns When:
- 🔄 You need multi-factor decision making
- 🔄 Your team has some rule engine experience
- 🔄 Business logic has moderate complexity
- 🔄 You need audit trails and transparency

### Choose Advanced Patterns When:
- 🔴 You're integrating with external systems
- 🔴 Your team has strong technical expertise
- 🔴 You need sophisticated error handling
- 🔴 Performance and resilience are critical

---

## Next Steps

- **📚 Learn More**: Explore the [Full YAML DSL Reference](yaml-dsl-reference.md) for complete syntax
- **🏛️ Set Standards**: Review [Governance Guidelines](governance-guidelines.md) for team practices
- **🎓 Practice**: Try the [B2B Credit Scoring Tutorial](b2b-credit-scoring-tutorial.md)
- **⚡ Get Started**: Return to [Quick Start Guide](quick-start-guide.md) for basics

---

*Need help choosing the right pattern? Check the [Governance Guidelines](governance-guidelines.md) →*
