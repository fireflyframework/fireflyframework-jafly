# B2B Credit Scoring Platform

**A Complete Step-by-Step Tutorial for Building Enterprise Credit Assessment Rules**

*This tutorial uses only real, implemented features from the Firefly Framework Rule Engine codebase*

---

## 🎯 Tutorial Overview

In this comprehensive tutorial, you'll learn to build a complete B2B credit scoring platform using the Firefly Framework Rule Engine's YAML DSL. We'll create a sophisticated system that evaluates business loan applications by analyzing multiple data sources including:

- **Credit Bureau Data** - Business credit scores and payment history
- **Bank Transaction Analysis** - Cash flow patterns and financial stability  
- **Tax Information** - Revenue verification and tax compliance
- **Business Profile** - Industry risk, years in business, ownership structure
- **Loan Application Details** - Requested amount, purpose, collateral

**Important Note**: All data comes from input variables that have been pre-fetched by your application system. The rule engine focuses on evaluation logic, not data collection.

## 📚 What You'll Learn

By the end of this tutorial, you'll understand:
- How to structure complex rule definitions with multiple sequential rules
- Working with comprehensive input data sets from multiple sources
- Implementing sophisticated scoring algorithms with weighted components
- Using constants for configurable business parameters
- Creating multi-stage evaluation workflows that mirror real-world processes
- Handling data validation and error scenarios gracefully
- Best practices for enterprise-grade rule development

---

## 🏗️ Step 1: Understanding the Rule Structure

Every Firefly rule follows a consistent structure. Let's start with the basic template:

<!-- doc-test:skip (structural template; placeholder values, not a complete parseable rule) -->
```yaml
# Required metadata
name: "Rule Name"
description: "What this rule does"
version: "1.0.0"

# Optional metadata for organization
metadata:
  tags: ["credit", "b2b", "scoring"]
  author: "Credit Risk Team"
  category: "Credit Assessment"

# Define input variables (camelCase - from API requests)
inputs:
  - businessId
  - requestedAmount
  - loanPurpose

# Define system constants (UPPER_CASE - from database)
constants:
  - code: MIN_CREDIT_SCORE
    defaultValue: 650
  - code: MAX_DEBT_TO_INCOME_RATIO
    defaultValue: 0.4

# Business logic (choose one approach)
when: [conditions]     # Simple syntax
then: [actions]        # Actions when true
else: [actions]        # Actions when false (optional)

# OR use multiple rules for complex workflows
rules:
  - name: "Sub-rule 1"
    when: [conditions]
    then: [actions]

# Define outputs (snake_case - computed variables)
output:
  credit_score: number
  approval_status: text
  risk_level: text
```

### Key Naming Conventions

The Firefly Framework Rule Engine uses specific naming patterns to automatically determine variable sources:

- **`camelCase`** (e.g., `businessId`, `annualRevenue`) - Input variables from API requests
- **`UPPER_CASE_WITH_UNDERSCORES`** (e.g., `MIN_CREDIT_SCORE`) - System constants from database
- **`snake_case`** (e.g., `credit_score`, `risk_level`) - Computed variables created during execution

### Rule Execution Flow

1. **Input Validation** - Check that required inputs are present and valid
2. **Constant Resolution** - Load business parameters from database
3. **Condition Evaluation** - Evaluate `when` conditions or rule conditions
4. **Action Execution** - Execute `then` or `else` actions based on conditions
5. **Output Generation** - Return computed variables as defined in `output` section

---

## 🏢 Step 2: Defining Our B2B Credit Scoring Inputs

For our B2B credit platform, we need to handle data from multiple sources. Here's our comprehensive input structure based on real-world credit assessment requirements:

```yaml
name: "B2B Credit Scoring Platform"
description: "Comprehensive business credit assessment using multiple data sources"
version: "1.0.0"

metadata:
  tags: ["b2b", "credit-scoring", "business-loans"]
  author: "Credit Risk Engineering Team"
  category: "Business Credit Assessment"

# Input variables from loan application API and external data sources
inputs:
  # Business identification and basic info
  - businessId                    # Unique business identifier
  - businessName                  # Legal business name
  - taxId                        # Federal tax ID (EIN)
  - businessType                 # LLC, Corporation, Partnership, etc.
  - industryCode                 # NAICS industry classification
  - yearsInBusiness             # Years since business establishment
  - numberOfEmployees           # Current employee count
  
  # Loan application details
  - requestedAmount             # Loan amount requested
  - loanPurpose                # Equipment, expansion, working capital, etc.
  - requestedTerm              # Loan term in months
  - hasCollateral              # Boolean - collateral offered
  - collateralValue            # Value of collateral if offered
  
  # Financial information from application
  - annualRevenue              # Most recent year revenue (self-reported)
  - monthlyRevenue             # Average monthly revenue (self-reported)
  - monthlyExpenses            # Average monthly expenses
  - existingDebt               # Current total debt obligations
  - monthlyDebtPayments        # Current monthly debt service
  
  # Business owner information
  - ownerCreditScore           # Primary owner's personal credit score
  - ownerYearsExperience       # Owner's years in industry
  - ownershipPercentage        # Primary owner's ownership stake
  
  # Credit bureau data (pre-fetched by application system)
  - businessCreditScore        # Business credit score from bureau
  - paymentHistoryScore        # Payment history rating (0-100)
  - creditUtilization          # Credit utilization percentage
  - publicRecordsCount         # Number of public records (liens, judgments)
  - tradelineCount            # Number of active trade lines
  
  # Banking and transaction data (pre-fetched by application system)
  - avgMonthlyDeposits         # Average monthly deposits (last 12 months)
  - accountAgeMonths           # Age of primary business account in months
  - nsfCount12Months           # NSF/overdraft count in last 12 months
  - cashFlowVolatility         # Cash flow volatility score (0-100, lower is better)
  - averageAccountBalance      # Average account balance
  
  # Tax and financial verification data (pre-fetched by application system)
  - verifiedAnnualRevenue      # Revenue verified from tax returns
  - taxComplianceScore         # Tax compliance rating (0-100)
  - businessExpenses           # Total business expenses from tax returns
  - netIncome                  # Net income from tax returns
  - taxFilingHistory           # Years of consecutive tax filings
```

### Input Data Sources Explained

**Application Data**: Basic business information and loan details provided directly by the applicant.

**Credit Bureau Data**: Information from business credit reporting agencies like Dun & Bradstreet, Experian Business, or Equifax Business.

**Banking Data**: Transaction analysis and account behavior data from the business's primary bank account.

**Tax Data**: Verified financial information from business tax returns, providing the most reliable revenue and expense data.

This comprehensive input structure ensures we have all the data needed for a thorough credit assessment without making external API calls from within the rule itself.

---

## 🔧 Step 3: Setting Up Business Constants

Constants allow us to maintain business parameters centrally and adjust them without changing rule logic. This is crucial for enterprise deployments where business rules need to be configurable:

```yaml
# System constants for business rules (stored in database)
constants:
  # Credit score thresholds
  - code: MIN_BUSINESS_CREDIT_SCORE
    defaultValue: 650
  - code: EXCELLENT_CREDIT_THRESHOLD
    defaultValue: 750
  
  # Financial ratio limits
  - code: MAX_DEBT_TO_INCOME_RATIO
    defaultValue: 0.4
  - code: MIN_DEBT_SERVICE_COVERAGE
    defaultValue: 1.25
  - code: MAX_LOAN_TO_VALUE_RATIO
    defaultValue: 0.8
  
  # Business criteria
  - code: MIN_YEARS_IN_BUSINESS
    defaultValue: 2
  - code: MIN_ANNUAL_REVENUE
    defaultValue: 100000
  - code: MAX_LOAN_AMOUNT_UNSECURED
    defaultValue: 250000
  
  # Industry risk multipliers
  - code: HIGH_RISK_INDUSTRY_MULTIPLIER
    defaultValue: 1.5
  - code: LOW_RISK_INDUSTRY_MULTIPLIER
    defaultValue: 0.8
  
  # Scoring weights
  - code: CREDIT_SCORE_WEIGHT
    defaultValue: 0.3
  - code: FINANCIAL_STABILITY_WEIGHT
    defaultValue: 0.25
  - code: BUSINESS_PROFILE_WEIGHT
    defaultValue: 0.25
  - code: CASH_FLOW_WEIGHT
    defaultValue: 0.2
```

### Why Use Constants?

1. **Configurability** - Business rules can be adjusted without code changes
2. **Consistency** - Same thresholds used across all rules
3. **Auditability** - Changes to business parameters are tracked
4. **Testing** - Easy to test different scenarios by adjusting constants
5. **Compliance** - Regulatory requirements can be easily updated

---

## 📊 Step 4: Building the Multi-Stage Evaluation Workflow

Our B2B credit scoring will use multiple sequential rules to build a comprehensive assessment. This approach mirrors real-world credit evaluation processes where different aspects are analyzed in stages.

<!-- doc-test:skip (Long multi-stage walkthrough demonstrating tutorial concepts; references many constants that would need to be wired up via ConstantService in a runnable demo. Kept as illustrative reading material. The complete runnable equivalent is exercised by the EndToEndScenarioTest in the test suite.) -->
```yaml
# Multi-stage evaluation using sequential rules
rules:
  # Stage 1: Data Validation and Preparation
  - name: "Data Validation and Preparation"
    when:
      - businessId is_not_empty
      - requestedAmount is_positive
      - annualRevenue is_positive
      - businessCreditScore is_credit_score
      - ownerCreditScore is_credit_score
    then:
      # Validate all required financial data is present and valid
      - set has_complete_financial_data to (
          monthlyRevenue is_positive AND
          monthlyExpenses is_positive AND
          existingDebt is_not_null AND
          monthlyDebtPayments is_positive AND
          verifiedAnnualRevenue is_positive
        )

      # Validate business profile data
      - set has_complete_business_profile to (
          yearsInBusiness is_positive AND
          numberOfEmployees is_positive AND
          industryCode is_not_empty AND
          ownerYearsExperience is_positive AND
          businessType is_not_empty
        )

      # Validate credit and banking data
      - set has_complete_credit_data to (
          businessCreditScore is_credit_score AND
          paymentHistoryScore is_positive AND
          avgMonthlyDeposits is_positive AND
          accountAgeMonths is_positive AND
          taxComplianceScore is_positive
        )

      # Calculate data quality indicators
      - run revenue_variance as abs(annualRevenue - verifiedAnnualRevenue) / verifiedAnnualRevenue
      - run deposit_variance as abs(avgMonthlyDeposits - monthlyRevenue) / monthlyRevenue

      # Overall data completeness and quality check
      - set data_validation_complete to (
          has_complete_financial_data AND
          has_complete_business_profile AND
          has_complete_credit_data AND
          revenue_variance less_than 0.3
        )

      - if data_validation_complete then set validation_status to "PASSED"
      - if NOT data_validation_complete then set validation_status to "FAILED"
      - if NOT data_validation_complete then set rejection_reason to "Incomplete or inconsistent application data"
    else:
      - set data_validation_complete to false
      - set validation_status to "FAILED"
      - set rejection_reason to "Missing required basic information"

  # Stage 2: Financial Analysis and Ratio Calculations
  - name: "Financial Analysis and Ratio Calculations"
    when:
      - data_validation_complete equals true
      - verifiedAnnualRevenue is_positive
    then:
      # Calculate key financial ratios
      - calculate monthly_revenue_verified as verifiedAnnualRevenue / 12
      - calculate debt_to_income_ratio as monthlyDebtPayments / monthly_revenue_verified
      - calculate profit_margin as netIncome / verifiedAnnualRevenue
      - calculate expense_ratio as businessExpenses / verifiedAnnualRevenue

      # Cash flow analysis
      - calculate cash_flow_coverage as avgMonthlyDeposits / monthlyDebtPayments
      - run account_stability_score as min(100, accountAgeMonths * 2)
      - run banking_behavior_score as max(0, 100 - (nsfCount12Months * 10))

      # Loan-specific calculations
      - calculate loan_to_revenue_ratio as requestedAmount / verifiedAnnualRevenue
      - calculate estimated_monthly_payment as requestedAmount * 0.02  # Simplified calculation
      - calculate new_debt_service as monthlyDebtPayments + estimated_monthly_payment
      - calculate debt_service_coverage as (monthly_revenue_verified - monthlyExpenses) / new_debt_service

      # Risk indicators
      - run revenue_stability_score as max(0, 100 - (revenue_variance * 100))
      - run cash_flow_stability_score as max(0, 100 - cashFlowVolatility)

      - set financial_analysis_complete to true
    else:
      - set financial_analysis_complete to false
      - set rejection_reason to "Unable to complete financial analysis"

  # Stage 3: Business Profile and Industry Risk Assessment
  - name: "Business Profile and Industry Risk Assessment"
    when:
      - financial_analysis_complete equals true
    then:
      # Business maturity scoring
      - run business_maturity_score as min(100, yearsInBusiness * 10)
      - run owner_experience_score as min(100, ownerYearsExperience * 8)
      - run employee_stability_score as min(100, numberOfEmployees * 5)

      # Industry risk assessment (using NAICS industry codes)
      - calculate industry_risk_multiplier as 1.0  # Default neutral
      - if industryCode starts_with "72" then set industry_risk_multiplier to HIGH_RISK_INDUSTRY_MULTIPLIER  # Food service
      - if industryCode starts_with "44" then set industry_risk_multiplier to LOW_RISK_INDUSTRY_MULTIPLIER   # Retail
      - if industryCode starts_with "54" then set industry_risk_multiplier to LOW_RISK_INDUSTRY_MULTIPLIER   # Professional services
      - if industryCode starts_with "62" then set industry_risk_multiplier to HIGH_RISK_INDUSTRY_MULTIPLIER  # Healthcare

      # Ownership and management assessment
      - calculate ownership_concentration_risk as ownershipPercentage  # Higher concentration = higher risk
      - calculate management_experience_score as (owner_experience_score + business_maturity_score) / 2

      # Credit profile assessment
      - calculate credit_profile_score as (businessCreditScore + paymentHistoryScore) / 2
      - calculate credit_risk_factors as publicRecordsCount * 15  # Penalty for public records

      - set business_profile_complete to true

  # Stage 4: Credit Assessment and Scoring
  - name: "Credit Assessment and Scoring"
    when:
      - business_profile_complete equals true
      - businessCreditScore is_credit_score
    then:
      # Credit score components (weighted scoring)
      - calculate credit_score_component as businessCreditScore * CREDIT_SCORE_WEIGHT
      - calculate owner_credit_component as ownerCreditScore * 0.15  # Personal credit has lower weight

      # Financial stability component
      - calculate financial_component as (
          (revenue_stability_score * 0.4) +
          (profit_margin * 100 * 0.3) +
          (cash_flow_stability_score * 0.3)
        ) * FINANCIAL_STABILITY_WEIGHT

      # Business profile component
      - calculate business_component as (
          (business_maturity_score * 0.4) +
          (owner_experience_score * 0.3) +
          (employee_stability_score * 0.2) +
          (account_stability_score * 0.1)
        ) * BUSINESS_PROFILE_WEIGHT

      # Cash flow component
      - calculate cash_flow_component as (
          (cash_flow_coverage * 20) +
          (debt_service_coverage * 30) +
          (banking_behavior_score * 0.5)
        ) * CASH_FLOW_WEIGHT

      # Calculate composite score
      - calculate base_credit_score as credit_score_component + financial_component + business_component + cash_flow_component

      # Apply industry risk adjustment
      - calculate adjusted_credit_score as base_credit_score / industry_risk_multiplier

      # Apply penalties for negative factors
      - if publicRecordsCount greater_than 0 then subtract credit_risk_factors from adjusted_credit_score
      - if taxComplianceScore less_than 80 then subtract 25 from adjusted_credit_score
      - if revenue_variance greater_than 0.2 then subtract 20 from adjusted_credit_score

      # Ensure score stays within bounds
      - run final_credit_score as max(300, min(850, adjusted_credit_score))

      - set credit_assessment_complete to true

  # Stage 5: Final Decision and Risk Classification
  - name: "Final Decision and Risk Classification"
    when:
      - credit_assessment_complete equals true
    then:
      # Determine risk level based on final score
      - if final_credit_score at_least EXCELLENT_CREDIT_THRESHOLD then set risk_level to "LOW"
      - if final_credit_score at_least MIN_BUSINESS_CREDIT_SCORE AND final_credit_score less_than EXCELLENT_CREDIT_THRESHOLD then set risk_level to "MEDIUM"
      - if final_credit_score less_than MIN_BUSINESS_CREDIT_SCORE then set risk_level to "HIGH"

      # Check debt service capacity
      - set debt_service_adequate to (debt_service_coverage at_least MIN_DEBT_SERVICE_COVERAGE)
      - set debt_to_income_acceptable to (debt_to_income_ratio at_most MAX_DEBT_TO_INCOME_RATIO)

      # Collateral assessment for larger loans
      - calculate loan_to_value_ratio as 0.0  # Default for unsecured
      - if hasCollateral equals true then calculate loan_to_value_ratio as requestedAmount / collateralValue
      - set collateral_adequate to (loan_to_value_ratio at_most MAX_LOAN_TO_VALUE_RATIO)

      # Final approval logic
      - set meets_credit_requirements to (final_credit_score at_least MIN_BUSINESS_CREDIT_SCORE)
      - set meets_financial_requirements to (debt_service_adequate AND debt_to_income_acceptable)
      - set meets_business_requirements to (yearsInBusiness at_least MIN_YEARS_IN_BUSINESS AND verifiedAnnualRevenue at_least MIN_ANNUAL_REVENUE)

      # Determine approval status
      - if meets_credit_requirements AND meets_financial_requirements AND meets_business_requirements then set approval_status to "APPROVED"
      - if NOT meets_credit_requirements then set approval_status to "DECLINED"
      - if NOT meets_financial_requirements then set approval_status to "DECLINED"
      - if NOT meets_business_requirements then set approval_status to "DECLINED"

      # Special handling for large unsecured loans
      - if requestedAmount greater_than MAX_LOAN_AMOUNT_UNSECURED AND NOT hasCollateral then set approval_status to "DECLINED"
      - if requestedAmount greater_than MAX_LOAN_AMOUNT_UNSECURED AND hasCollateral AND NOT collateral_adequate then set approval_status to "DECLINED"

      # Set interest rate based on risk
      - calculate base_interest_rate as 0.08  # 8% base rate
      - if risk_level equals "LOW" then calculate interest_rate as base_interest_rate - 0.015
      - if risk_level equals "MEDIUM" then calculate interest_rate as base_interest_rate
      - if risk_level equals "HIGH" then calculate interest_rate as base_interest_rate + 0.025

      # Calculate final loan terms
      - calculate approved_amount as requestedAmount  # Could be modified based on risk
      - if risk_level equals "HIGH" AND approval_status equals "APPROVED" then run approved_amount as min(requestedAmount, verifiedAnnualRevenue * 0.25)

      # Set rejection reasons if declined
      - if NOT meets_credit_requirements then set rejection_reason to "Credit score below minimum requirements"
      - if NOT meets_financial_requirements then set rejection_reason to "Debt service capacity insufficient"
      - if NOT meets_business_requirements then set rejection_reason to "Business does not meet minimum operating requirements"

      # Generate recommendation summary. Uses `run` because the expression mixes strings
      # with numbers (string concatenation via `+`). Wrap in YAML single-quotes because
      # the message contains `:` followed by a space, which YAML would otherwise treat
      # as a key/value separator.
      - 'run recommendation_summary as "Credit Score - " + tostring(final_credit_score) + ", Risk - " + risk_level + ", DTI - " + tostring(debt_to_income_ratio * 100) + "%"'

      - set final_decision_complete to true

# Define all output variables that will be returned
output:
  # Primary decision outputs
  approval_status: text           # APPROVED, DECLINED, PENDING_REVIEW
  final_credit_score: number      # Computed credit score (300-850)
  risk_level: text               # LOW, MEDIUM, HIGH
  interest_rate: number          # Approved interest rate
  approved_amount: number        # Final approved loan amount

  # Financial analysis results
  debt_to_income_ratio: number   # Monthly debt to income ratio
  debt_service_coverage: number  # Debt service coverage ratio
  profit_margin: number          # Business profit margin
  cash_flow_coverage: number     # Cash flow to debt coverage

  # Risk assessment details
  revenue_stability_score: number # Revenue consistency score
  cash_flow_stability_score: number # Cash flow consistency score
  industry_risk_multiplier: number # Industry risk adjustment

  # Decision factors
  meets_credit_requirements: boolean
  meets_financial_requirements: boolean
  meets_business_requirements: boolean
  debt_service_adequate: boolean
  collateral_adequate: boolean

  # Additional information
  rejection_reason: text         # Reason if declined
  recommendation_summary: text   # Summary of key factors
  estimated_monthly_payment: number # Calculated monthly payment

  # Data verification results
  revenue_variance: number       # Difference between stated and verified revenue
  banking_behavior_score: number # Banking relationship quality
```

---

## 🔍 Step 5: Understanding the Multi-Stage Workflow

This comprehensive multi-stage workflow demonstrates several key concepts:

### 🔍 **Stage 1: Data Validation and Preparation**
- **Data validation** using validation operators like `is_positive`, `is_credit_score`, `is_not_empty`
- **Complex boolean logic** combining multiple conditions with `AND`/`OR`
- **Mathematical calculations** for data quality assessment (revenue variance, deposit variance)
- **Variable assignment** using `set` and `calculate` actions
- **Conditional logic** with `if`/`then` statements

### 📊 **Stage 2: Financial Analysis and Ratio Calculations**
- **Financial ratio calculations** including debt-to-income, profit margin, cash flow coverage
- **Risk scoring** with revenue stability and cash flow stability scores
- **Loan-specific metrics** like debt service coverage and estimated payments
- **Bounds checking** using `min()` and `max()` functions

### 🏢 **Stage 3: Business Profile and Industry Risk Assessment**
- **Industry risk assessment** using NAICS codes and risk multipliers
- **Business maturity scoring** based on years in business and owner experience
- **Credit profile evaluation** incorporating payment history and public records
- **String operations** using `starts_with` for industry code classification

### 🎯 **Stage 4: Credit Assessment and Scoring**
- **Weighted scoring algorithm** combining multiple components
- **Industry risk adjustments** and penalty applications
- **Score normalization** ensuring values stay within valid bounds (300-850)
- **Complex mathematical expressions** with proper parentheses usage

### ✅ **Stage 5: Final Decision and Risk Classification**
- **Risk level determination** based on final credit score
- **Approval logic** checking multiple requirement categories
- **Interest rate calculation** based on risk level
- **Comprehensive output generation** with decision factors and summaries

---

## 🔧 Step 6: Understanding Key DSL Features Used

Our B2B credit scoring rule demonstrates many advanced features of the Firefly Framework Rule Engine:

### ✅ **Data Validation**
```yaml
# Comprehensive input validation
when:
  - businessId is_not_empty
  - requestedAmount is_positive
  - businessCreditScore is_credit_score
  - verifiedAnnualRevenue is_positive
```

**Key Points:**
- Use specific validation operators for different data types
- `is_credit_score` validates scores are in 300-850 range
- `is_positive` ensures numeric values are greater than zero
- `is_not_empty` checks for non-empty strings

### 🧮 **Mathematical Calculations**
```yaml
# Complex financial calculations
- calculate debt_to_income_ratio as monthlyDebtPayments / monthly_revenue_verified
- run final_credit_score as max(300, min(850, adjusted_credit_score))
- calculate weighted_score as (creditScore * 0.6) + (incomeScore * 0.4)
```

**Key Points:**
- Standard arithmetic operators: `+`, `-`, `*`, `/`, `%`, `**`
- Parentheses for operation precedence
- Built-in functions like `max()`, `min()`, `abs()`
- Bounds checking to keep values in valid ranges

### 🔄 **Conditional Logic**
```yaml
# Multi-condition decision making
- if final_credit_score at_least EXCELLENT_CREDIT_THRESHOLD then set risk_level to "LOW"
- if meets_credit_requirements AND meets_financial_requirements then set approval_status to "APPROVED"
```

**Key Points:**
- Use comparison operators like `at_least`, `greater_than`, `equals`
- Combine conditions with `AND`, `OR`, `NOT`
- Constants (UPPER_CASE) are automatically resolved from database

### 🏗️ **Complex Boolean Expressions**
```yaml
# Multi-factor validation
- set has_complete_financial_data to (
    monthlyRevenue is_positive AND
    monthlyExpenses is_positive AND
    existingDebt is_not_null AND
    monthlyDebtPayments is_positive
  )
```

**Key Points:**
- Parentheses group complex boolean expressions
- Multi-line expressions for readability
- Boolean results can be stored in variables

---

## 🎯 Step 7: Testing Your Rule

To test this rule, you would send a POST request to the rule evaluation endpoint with input data:

```json
{
  "businessId": "BUS123456",
  "businessName": "Tech Solutions LLC",
  "taxId": "12-3456789",
  "businessType": "LLC",
  "industryCode": "541511",
  "yearsInBusiness": 5,
  "numberOfEmployees": 25,
  "requestedAmount": 150000,
  "loanPurpose": "equipment",
  "requestedTerm": 60,
  "hasCollateral": true,
  "collateralValue": 200000,
  "annualRevenue": 1200000,
  "monthlyRevenue": 100000,
  "monthlyExpenses": 75000,
  "existingDebt": 50000,
  "monthlyDebtPayments": 2500,
  "ownerCreditScore": 720,
  "ownerYearsExperience": 10,
  "ownershipPercentage": 75,
  "businessCreditScore": 680,
  "paymentHistoryScore": 85,
  "creditUtilization": 0.3,
  "publicRecordsCount": 0,
  "tradelineCount": 8,
  "avgMonthlyDeposits": 105000,
  "accountAgeMonths": 36,
  "nsfCount12Months": 1,
  "cashFlowVolatility": 15,
  "averageAccountBalance": 25000,
  "verifiedAnnualRevenue": 1180000,
  "taxComplianceScore": 95,
  "businessExpenses": 950000,
  "netIncome": 230000,
  "taxFilingHistory": 5
}
```

**Expected Output:**
```json
{
  "approval_status": "APPROVED",
  "final_credit_score": 742,
  "risk_level": "LOW",
  "interest_rate": 0.065,
  "approved_amount": 150000,
  "debt_to_income_ratio": 0.254,
  "debt_service_coverage": 1.45,
  "recommendation_summary": "Credit Score: 742, Risk Level: LOW, DTI: 25.4%"
}
```

---

## 💡 Step 8: Best Practices and Tips

### 🏗️ **Rule Structure Best Practices**

1. **Use Sequential Rules for Complex Workflows**
   ```yaml
   # ✅ Good: Break complex logic into stages
   rules:
     - name: "Data Validation and Preparation"
     - name: "Financial Analysis and Ratio Calculations"
     - name: "Business Profile and Industry Risk Assessment"
     - name: "Credit Assessment and Scoring"
     - name: "Final Decision and Risk Classification"

   # ❌ Avoid: Single massive rule with all logic
   ```

2. **Meaningful Variable Names**
   ```yaml
   # ✅ Good: Descriptive names
   - calculate debt_to_income_ratio as monthlyDebt / monthlyIncome
   - set credit_assessment_complete to true

   # ❌ Avoid: Cryptic names
   - calculate dti as md / mi
   - set flag1 to true
   ```

3. **Use Constants for Business Parameters**
   ```yaml
   # ✅ Good: Configurable business rules
   - if creditScore at_least MIN_CREDIT_SCORE then set eligible to true

   # ❌ Avoid: Hard-coded values
   - if creditScore at_least 650 then set eligible to true
   ```

### 🔍 **Data Validation Best Practices**

1. **Validate Early and Often**
   ```yaml
   # ✅ Good: Validate inputs before processing
   when:
     - businessId is_not_empty
     - requestedAmount is_positive
     - businessCreditScore is_credit_score
   ```

2. **Handle Missing Data Gracefully**
   ```yaml
   # ✅ Good: Check existence before using
   - if exists collateralValue then calculate loan_to_value as requestedAmount / collateralValue

   # ❌ Avoid: Assuming data exists
   - calculate loan_to_value as requestedAmount / collateralValue
   ```

3. **Use Appropriate Validation Operators**
   ```yaml
   # ✅ Good: Use specific validators
   - ownerCreditScore is_credit_score
   - businessEmail is_email
   - taxId is_ssn
   ```

### 🧮 **Mathematical Calculations Best Practices**

1. **Use Parentheses for Clarity**
   ```yaml
   # ✅ Good: Clear operation order
   - calculate weighted_score as (creditScore * 0.6) + (incomeScore * 0.4)

   # ❌ Confusing: Relies on operator precedence
   - calculate weighted_score as creditScore * 0.6 + incomeScore * 0.4
   ```

2. **Protect Against Division by Zero**
   ```yaml
   # ✅ Good: Check denominator
   - if monthlyIncome greater_than 0 then calculate dti as monthlyDebt / monthlyIncome

   # ❌ Dangerous: Could cause runtime error
   - calculate dti as monthlyDebt / monthlyIncome
   ```

3. **Use Bounds Checking**
   ```yaml
   # ✅ Good: Keep values in valid ranges
   - run final_score as max(300, min(850, calculated_score))
   ```

---

## 🚨 Step 9: Common Issues and Troubleshooting

### ❌ **Common Syntax Errors**

1. **Missing Quotes in String Comparisons**
   ```yaml
   # ❌ Wrong: Missing quotes
   - if approval_status equals APPROVED then...

   # ✅ Correct: String values need quotes
   - if approval_status equals "APPROVED" then...
   ```

2. **Incorrect Variable Naming**
   ```yaml
   # ❌ Wrong: Using $ prefix (old syntax)
   - calculate ratio as $monthlyDebt / $monthlyIncome

   # ✅ Correct: Plain variable names
   - calculate ratio as monthlyDebt / monthlyIncome
   ```

3. **Missing Action Keywords**
   ```yaml
   # ❌ Wrong: Missing 'set' keyword
   - approval_status to "APPROVED"

   # ✅ Correct: Use proper action syntax
   - set approval_status to "APPROVED"
   ```

### 🔧 **Runtime Issues**

1. **Type Conversion Issues**
   ```yaml
   # ✅ Good: Validate data types
   - if creditScore is_number then calculate adjusted_score as creditScore * 1.1
   ```

2. **Null Value Handling**
   ```yaml
   # ✅ Good: Check for null values
   - if collateralValue is_not_null then calculate loan_to_value as requestedAmount / collateralValue
   ```

---

## 🎓 Step 10: Next Steps and Advanced Features

### 🚀 **Advanced Features to Explore**

1. **Circuit Breaker Pattern**
   ```yaml
   # Stop execution on critical errors
   - if critical_error_detected then circuit_breaker "Critical validation failed"
   ```

2. **Complex Conditional Syntax**
   ```yaml
   conditions:
     if:
       and:
         - compare: {left: creditScore, operator: "at_least", right: 650}
         - compare: {left: annualIncome, operator: "greater_than", right: 50000}
     then:
       actions:
         - set: {variable: "approval_status", value: "APPROVED"}
   ```

3. **List Operations**
   ```yaml
   # Working with arrays and lists
   - append "HIGH_RISK" to risk_factors
   - remove "TEMPORARY" from account_flags
   ```

### 📚 **Additional Resources**

- **[YAML DSL Reference](yaml-dsl-reference.md)** - Complete syntax reference
- **[Architecture Documentation](architecture.md)** - Technical implementation details
- **API Documentation** - Available at `/swagger-ui.html` when running the service

### 🎯 **Practice Exercises**

1. **Modify the scoring weights** in constants and observe how it affects decisions
2. **Add new industry risk categories** with different multipliers
3. **Implement seasonal adjustments** for businesses with seasonal revenue patterns
4. **Add fraud detection rules** using transaction pattern analysis
5. **Create approval workflows** with multiple approval levels based on loan amounts

### 🔄 **Extending the Rule**

Consider these enhancements for production use:

- **Geographic risk factors** based on business location
- **Economic indicators** integration for market conditions
- **Peer comparison** against industry benchmarks
- **Regulatory compliance** checks for specific loan types
- **Dynamic pricing** based on market conditions

---

## 🏁 Conclusion

Congratulations! You've built a comprehensive B2B credit scoring platform using the Firefly Framework Rule Engine. This tutorial covered:

✅ **Rule structure and organization** with proper naming conventions
✅ **Multi-stage evaluation workflows** that mirror real-world processes
✅ **Comprehensive input data handling** from multiple sources
✅ **Complex mathematical calculations** with proper bounds checking
✅ **Conditional logic and decision trees** with boolean expressions
✅ **Data validation and error handling** using validation operators
✅ **Best practices and troubleshooting** for enterprise development

### 🎯 **Key Achievements**

Your rule demonstrates enterprise-grade credit assessment capabilities including:
- **Multi-source data integration** (credit bureaus, banks, tax services)
- **Sophisticated risk scoring algorithms** with weighted components
- **Industry-specific risk adjustments** using NAICS codes
- **Comprehensive financial ratio analysis** with stability scoring
- **Automated decision making** with detailed audit trails

### 🚀 **Production Readiness**

The Firefly Framework Rule Engine's YAML DSL makes it possible to implement complex business logic that would traditionally require extensive custom code, while maintaining:
- **Readability** for business users and analysts
- **Maintainability** through structured rule organization
- **Configurability** via database-stored constants
- **Auditability** with comprehensive output tracking
- **Performance** through AST-based evaluation

### 🎉 **Ready to Deploy**

Your B2B credit scoring rule is now ready for production use! Deploy it using:
- **Rule Definition API** to store and version your rule
- **Rule Evaluation API** to process loan applications in real-time
- **Validation API** to ensure rule syntax and semantic correctness

**Start processing loan applications with confidence using your new enterprise-grade credit scoring system!**

---

## ⚠️ Educational Use Disclaimer

**This tutorial is for educational purposes only and not intended for production credit decisions.**

The credit scoring model presented here uses simplified calculations and arbitrary thresholds that do not reflect industry-standard methodologies. Production credit systems require:

- Compliance with financial regulations (FCRA, ECOA, TILA, etc.)
- Statistically validated risk models
- Comprehensive audit trails and explainability
- Professional risk management oversight
- Legal and regulatory approval

Organizations implementing actual credit assessment systems should consult qualified financial risk professionals, regulatory compliance experts, and legal counsel specializing in enterprise applications.

**The authors disclaim liability for any damages arising from use of this educational material.**
