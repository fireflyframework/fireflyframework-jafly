# Firefly Framework Rule Engine - API Documentation

**Complete REST API Reference for the Firefly Framework Rule Engine**

*Based on actual controller implementations and DTOs*

---

## 📚 Documentation Navigation

**New to the API?** Check out our layered documentation:

- **🚀 [Quick Start Guide](quick-start-guide.md)** - Learn the basics in 15 minutes
- **🎯 [Common Patterns Guide](common-patterns-guide.md)** - Real-world usage examples
- **🏛️ [Governance Guidelines](governance-guidelines.md)** - Team standards and best practices
- **📖 [YAML DSL Reference](yaml-dsl-reference.md)** - Complete syntax documentation
- **📋 This API Reference** - Complete REST API documentation (you are here)

---

## Table of Contents

- [Base URL and Versioning](#base-url-and-versioning)
- [Authentication](#authentication)
- [Error Handling](#error-handling)
- [Rules Evaluation API](#rules-evaluation-api)
  - [Evaluate Rules (Base64)](#evaluate-rules-base64)
  - [Evaluate Rules (Plain YAML)](#evaluate-rules-plain-yaml)
  - [Evaluate Stored Rule by Code](#evaluate-stored-rule-by-code)
  - [Batch Rules Evaluation](#batch-rules-evaluation)
- [Rule Definitions Management API](#rule-definitions-management-api)
- [Constants Management API](#constants-management-api)
- [Validation API](#validation-api)
- [Audit Trail API](#audit-trail-api)
- [Health and Monitoring](#health-and-monitoring)
- [Examples](#examples)

## Base URL and Versioning

### Base URL
```
http://localhost:8080/api
```

### API Versioning
The API uses URL path versioning. Current version is `v1`:
```
http://localhost:8080/api/v1/
```

### Content Type
All API endpoints accept and return JSON unless otherwise specified:
```
Content-Type: application/json
Accept: application/json
```

### OpenAPI/Swagger Documentation
Interactive API documentation is available at:
```
http://localhost:8080/swagger-ui.html
```

OpenAPI specification:
```
http://localhost:8080/v3/api-docs
```

## Authentication

The API currently operates without authentication. All endpoints are publicly accessible.

> **Note**: In production environments, implement appropriate authentication and authorization mechanisms.

## Error Handling

### Standard Error Response

All API errors follow a consistent format:

```json
{
  "timestamp": "2025-01-12T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid rules definition: Missing required field 'name'",
  "path": "/api/v1/rules/evaluate/direct",
  "requestId": "req-123456789"
}
```

### HTTP Status Codes

| Status Code | Description | Usage |
|-------------|-------------|-------|
| `200` | OK | Successful request |
| `201` | Created | Resource created successfully |
| `204` | No Content | Successful deletion |
| `400` | Bad Request | Invalid request data |
| `404` | Not Found | Resource not found |
| `409` | Conflict | Resource already exists |
| `422` | Unprocessable Entity | Validation errors |
| `500` | Internal Server Error | Server error |

### Validation Errors

For validation errors (422), the response includes detailed field-level errors:

```json
{
  "timestamp": "2025-01-12T10:30:00Z",
  "status": 422,
  "error": "Validation Failed",
  "message": "Request validation failed",
  "path": "/api/constants",
  "requestId": "req-123456789",
  "validationErrors": [
    {
      "field": "code",
      "message": "Constant code must start with uppercase letter",
      "rejectedValue": "min_credit_score"
    },
    {
      "field": "valueType",
      "message": "Value type is required",
      "rejectedValue": null
    }
  ]
}
```

## Rules Evaluation API

The Rules Evaluation API provides endpoints for evaluating YAML DSL rules against input data. It supports:

- **Direct evaluation** with base64-encoded YAML content
- **Plain YAML evaluation** with unencoded YAML content
- **Stored rule evaluation** by rule code
- **Batch evaluation** for processing multiple rules concurrently
- Real-time rule execution with comprehensive error handling
- Support for all naming conventions and variable types
- Comprehensive audit trail for all evaluations

### Variable Naming Conventions

The API enforces strict naming conventions:

| Variable Type | Format | Example | Source |
|---------------|--------|---------|---------|
| **Input Variables** | `camelCase` | `creditScore`, `annualIncome` | From API request `inputData` |
| **System Constants** | `UPPER_CASE_WITH_UNDERSCORES` | `MIN_CREDIT_SCORE` | From database constants |
| **Computed Variables** | `snake_case` | `debt_ratio`, `is_eligible` | Created during rule execution |

### Evaluate Rules (Base64)

Evaluate a base64-encoded YAML rules definition against provided input data.

**Endpoint:** `POST /api/v1/rules/evaluate/direct`

#### Request Schema

```json
{
  "rulesDefinitionBase64": "bmFtZTogIkNyZWRpdCBDaGVjayIKZGVzY3JpcHRpb246ICJCYXNpYyBjcmVkaXQgZWxpZ2liaWxpdHkiCgppbnB1dHM6CiAgLSBjcmVkaXRTY29yZQogIC0gYW5udWFsSW5jb21lCgp3aGVuOgogIC0gY3JlZGl0U2NvcmUgYXRfbGVhc3QgTUlOX0NSRURJVF9TQ09SRQogIC0gYW5udWFsSW5jb21lIGF0X2xlYXN0IE1JTl9BTk5VQUxfSU5DT01FCgp0aGVuOgogIC0gc2V0IGlzX2VsaWdpYmxlIHRvIHRydWUKICAtIHNldCBhcHByb3ZhbF90aWVyIHRvICJTVEFOREFSRCIKCmVsc2U6CiAgLSBzZXQgaXNfZWxpZ2libGUgdG8gZmFsc2UKICAtIHNldCBhcHByb3ZhbF90aWVyIHRvICJERUNMSU5FRCIKCm91dHB1dDoKICBpc19lbGlnaWJsZTogYm9vbGVhbgogIGFwcHJvdmFsX3RpZXI6IHRleHQ=",
  "inputData": {
    "creditScore": 780,
    "annualIncome": 75000,
    "employmentYears": 3,
    "existingDebt": 25000
  }
}
```

#### Request Fields

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `rulesDefinitionBase64` | string | No* | Max 100,000 chars | Base64-encoded YAML rules definition |
| `inputData` | object | Yes | Max 1,000 entries, camelCase keys | Input variables for rule evaluation |

*Either `rulesDefinitionBase64` or stored rule code is required.

#### Response Schema

```json
{
  "success": true,
  "conditionResult": true,
  "outputData": {
    "is_eligible": true,
    "approval_tier": "STANDARD",
    "debt_ratio": 0.33,
    "risk_score": 85
  },
  "executionTimeMs": 45,
  "circuitBreakerTriggered": false,
  "circuitBreakerMessage": null,
  "error": null,
  "metadata": {
    "rulesId": "rules-123",
    "rulesName": "Credit Check"
  }
}
```

#### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `success` | boolean | Whether the evaluation completed successfully |
| `conditionResult` | boolean | Result of the rule conditions evaluation |
| `outputData` | object | Output data with computed variables in snake_case |
| `executionTimeMs` | number | Execution time in milliseconds |
| `circuitBreakerTriggered` | boolean | Whether circuit breaker was triggered |
| `circuitBreakerMessage` | string | Circuit breaker message if triggered |
| `error` | string | Error message if evaluation failed |
| `metadata` | object | Additional metadata about the evaluation |

#### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/rules/evaluate/direct \
  -H "Content-Type: application/json" \
  -d '{
    "rulesDefinitionBase64": "bmFtZTogIkNyZWRpdCBDaGVjayIKZGVzY3JpcHRpb246ICJCYXNpYyBjcmVkaXQgZWxpZ2liaWxpdHkiCgppbnB1dHM6CiAgLSBjcmVkaXRTY29yZQogIC0gYW5udWFsSW5jb21lCgp3aGVuOgogIC0gY3JlZGl0U2NvcmUgYXRfbGVhc3QgTUlOX0NSRURJVF9TQ09SRQogIC0gYW5udWFsSW5jb21lIGF0X2xlYXN0IE1JTl9BTk5VQUxfSU5DT01FCgp0aGVuOgogIC0gc2V0IGlzX2VsaWdpYmxlIHRvIHRydWUKICAtIHNldCBhcHByb3ZhbF90aWVyIHRvICJTVEFOREFSRCIKCmVsc2U6CiAgLSBzZXQgaXNfZWxpZ2libGUgdG8gZmFsc2UKICAtIHNldCBhcHByb3ZhbF90aWVyIHRvICJERUNMSU5FRCIKCm91dHB1dDoKICBpc19lbGlnaWJsZTogYm9vbGVhbgogIGFwcHJvdmFsX3RpZXI6IHRleHQ=",
    "inputData": {
      "creditScore": 720,
      "annualIncome": 75000
    },
    "metadata": {
      "requestId": "req-001",
      "source": "loan-application"
    },
    "includeDetails": false,
    "debugMode": false
  }'
```

#### Example Response

```json
{
  "success": true,
  "conditionResult": true,
  "outputData": {
    "is_eligible": true,
    "approval_tier": "STANDARD",
    "debt_to_income": 0.33
  },
  "executionTimeMs": 45,
  "circuitBreakerTriggered": false,
  "circuitBreakerMessage": null,
  "error": null
}
```

### Evaluate Rules (Plain YAML)

Evaluate a plain (non-base64) YAML rules definition against provided input data.

**Endpoint:** `POST /api/v1/rules/evaluate/plain`

#### Request Schema

```json
{
  "yamlContent": "name: \"Credit Scoring Rule\"\ndescription: \"Basic credit assessment for loan applications\"\n\ninputs:\n  - creditScore\n  - annualIncome\n  - employmentYears\n  - existingDebt\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n  - annualIncome at_least 50000\n  - employmentYears at_least 2\n\nthen:\n  - calculate debt_to_income as existingDebt / annualIncome\n  - set is_eligible to true\n  - set approval_tier to \"STANDARD\"\n\nelse:\n  - set is_eligible to false\n  - set approval_tier to \"DECLINED\"\n\noutput:\n  is_eligible: boolean\n  approval_tier: text\n  debt_to_income: number",
  "inputData": {
    "creditScore": 780,
    "annualIncome": 75000,
    "employmentYears": 3,
    "existingDebt": 25000
  }
}
```

#### Request Fields

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `yamlContent` | string | Yes | 10-50,000 chars | Plain YAML DSL rules definition |
| `inputData` | object | Yes | Max 1,000 entries, camelCase keys | Input data for rule evaluation |

#### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/rules/evaluate/plain \
  -H "Content-Type: application/json" \
  -d '{
  "yamlContent": "name: \"Credit Check\"\ndescription: \"Basic credit eligibility\"\n\ninputs:\n  - creditScore\n  - annualIncome\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n  - annualIncome at_least 50000\n\nthen:\n  - set is_eligible to true\n  - set approval_tier to \"STANDARD\"\n\nelse:\n  - set is_eligible to false\n  - set approval_tier to \"DECLINED\"\n\noutput:\n  is_eligible: is_eligible\n  approval_tier: approval_tier",
  "inputData": {
    "creditScore": 720,
    "annualIncome": 75000
  },
  "metadata": {
    "request_id": "req-001",
    "source": "loan-application"
  },
  "includeDetails": false,
  "debugMode": false
}'
```

#### Response Schema

Same as the base64 evaluation response.

### Evaluate Stored Rule by Code

Evaluate a stored rule definition by its code against provided input data.

**Endpoint:** `POST /api/v1/rules/evaluate/by-code`

#### Request Schema

```json
{
  "ruleDefinitionCode": "credit_scoring_v1",
  "inputData": {
    "creditScore": 720,
    "annualIncome": 75000,
    "employmentYears": 3,
    "existingDebt": 25000
  }
}
```

#### Request Fields

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `ruleDefinitionCode` | string | Yes | Pattern: `^[a-zA-Z][a-zA-Z0-9_]*$` | Code of stored rule definition |
| `inputData` | object | Yes | Max 1,000 entries, camelCase keys | Input data for rule evaluation |

#### Response Schema

Same as the standard rule evaluation response.

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `200` | Rules evaluated successfully |
| `400` | Invalid input data or naming convention violations |
| `404` | Rule definition not found or inactive |
| `500` | Internal server error |

### Batch Rules Evaluation

Process multiple rule evaluations concurrently in a single request.

**Endpoint:** `POST /api/v1/rules/batch/evaluate`

#### Request Schema

```json
{
  "evaluationRequests": [
    {
      "requestId": "req-001",
      "ruleDefinitionCode": "credit_scoring_v1",
      "inputData": {
        "creditScore": 750,
        "annualIncome": 80000
      }
    },
    {
      "requestId": "req-002",
      "ruleDefinitionCode": "risk_assessment_v2",
      "inputData": {
        "amount": 50000,
        "customerTier": "GOLD"
      }
    }
  ],
  "concurrencyLimit": 10,
  "timeoutMs": 30000
}
```

#### Request Fields

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `evaluationRequests` | array | Yes | 1-100 items | List of rule evaluation requests |
| `concurrencyLimit` | integer | No | 1-50, default: 10 | Maximum concurrent evaluations |
| `timeoutMs` | integer | No | 1000-300000, default: 30000 | Timeout per evaluation in milliseconds |

#### Single Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `requestId` | string | No | Unique identifier for tracking |
| `ruleDefinitionCode` | string | Yes | Code of stored rule definition |
| `inputData` | object | Yes | Input data with camelCase variables |

#### Response Schema

```json
{
  "batchId": "batch-123e4567-e89b-12d3-a456-426614174000",
  "summary": {
    "totalRequests": 2,
    "successfulEvaluations": 2,
    "failedEvaluations": 0,
    "totalExecutionTimeMs": 125,
    "averageExecutionTimeMs": 62.5
  },
  "results": [
    {
      "requestId": "req-001",
      "ruleDefinitionCode": "credit_scoring_v1",
      "status": "SUCCESS",
      "result": {
        "success": true,
        "conditionResult": true,
        "outputData": {
          "is_eligible": true,
          "approval_tier": "PREMIUM"
        },
        "executionTimeMs": 45
      }
    },
    {
      "requestId": "req-002",
      "ruleDefinitionCode": "risk_assessment_v2",
      "status": "SUCCESS",
      "result": {
        "success": true,
        "conditionResult": true,
        "outputData": {
          "risk_level": "LOW",
          "approval_limit": 75000
        },
        "executionTimeMs": 80
      }
    }
  ]
}
```

#### Batch Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `batchId` | string | Unique identifier for the batch operation |
| `summary` | object | Summary statistics for the batch |
| `results` | array | Individual evaluation results |

#### Evaluation Status Values

| Status | Description |
|--------|-------------|
| `SUCCESS` | Evaluation completed successfully |
| `FAILED` | Evaluation failed due to error |
| `TIMEOUT` | Evaluation exceeded timeout limit |
| `RULE_NOT_FOUND` | Rule definition not found |

## Rule Definitions Management API

The Rule Definitions API allows you to store, manage, and evaluate YAML DSL rule definitions. This enables you to:

- Store validated YAML DSL definitions in the database
- Retrieve stored rule definitions by ID or code
- Update existing rule definitions with versioning
- Delete rule definitions
- Filter and paginate rule definitions
- Validate YAML DSL content before storage
- Manage rule metadata and lifecycle

### Create Rule Definition

Store a new YAML DSL rule definition in the database.

**Endpoint:** `POST /api/v1/rules/definitions`

#### Request Schema

```json
{
  "code": "credit_scoring_v1",
  "name": "Credit Scoring Rule v1",
  "description": "Basic credit scoring rule for loan applications",
  "yamlContent": "name: \"Credit Scoring\"\ndescription: \"Basic credit assessment\"\n\ninputs:\n  - creditScore\n  - annualIncome\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n  - annualIncome at_least MIN_ANNUAL_INCOME\n\nthen:\n  - set is_eligible to true\n  - set approval_tier to \"STANDARD\"\n\nelse:\n  - set is_eligible to false\n  - set approval_tier to \"DECLINED\"\n\noutput:\n  is_eligible: boolean\n  approval_tier: text",
  "version": "1.0.0",
  "isActive": true,
  "tags": "credit,scoring,loan",
  "createdBy": "risk_team"
}
```

#### Request Fields

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `code` | string | Yes | Pattern: `^[a-zA-Z][a-zA-Z0-9_]*$`, 3-100 chars | Unique identifier for the rule definition |
| `name` | string | Yes | 2-255 chars | Human-readable name for the rule definition |
| `description` | string | No | Max 1000 chars | Detailed description of what this rule does |
| `yamlContent` | string | Yes | Max 100,000 chars | The YAML DSL content (validated before storage) |
| `version` | string | No | Pattern: `^\d+\.\d+\.\d+$` | Semantic version identifier (e.g., "1.0.0") |
| `isActive` | boolean | Yes | - | Whether this rule definition is active |
| `tags` | string | No | Max 500 chars | Comma-separated tags for categorization |
| `createdBy` | string | No | Max 255 chars | User who created the rule definition |

#### Response Schema

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "code": "credit_scoring_v1",
  "name": "Credit Scoring Rule v1",
  "description": "Basic credit scoring rule for loan applications",
  "yamlContent": "name: \"Credit Scoring\"...",
  "version": "1.0.0",
  "isActive": true,
  "tags": "credit,scoring,loan",
  "createdBy": "risk_team",
  "updatedBy": "risk_team",
  "createdAt": "2025-01-12T10:30:00Z",
  "updatedAt": "2025-01-12T10:30:00Z"
}
```

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `201` | Rule definition created successfully |
| `400` | Invalid rule definition data or naming convention violations |
| `409` | Rule definition with the same code already exists |
| `422` | YAML DSL validation failed |
| `500` | Internal server error |

#### Example Request

```bash
curl -X POST http://localhost:8080/api/v1/rules/definitions \
  -H "Content-Type: application/json" \
  -d '{
    "code": "credit_scoring_v1",
    "name": "Credit Scoring Rule v1",
    "description": "Basic credit scoring rule for loan applications",
    "yamlContent": "name: \"Credit Scoring\"\ndescription: \"Basic credit assessment\"\n\ninputs:\n  - creditScore\n  - annualIncome\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n  - annualIncome at_least MIN_ANNUAL_INCOME\n\nthen:\n  - set is_eligible to true\n  - set approval_tier to \"STANDARD\"\n\nelse:\n  - set is_eligible to false\n  - set approval_tier to \"DECLINED\"\n\noutput:\n  is_eligible: boolean\n  approval_tier: text",
    "version": "1.0",
    "isActive": true,
    "tags": "credit,scoring,loan",
    "createdBy": "risk_team"
  }'
```

### Update Rule Definition

Update an existing rule definition.

**Endpoint:** `PUT /api/v1/rules/definitions/{id}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Unique identifier of the rule definition |

#### Request/Response Schema

Same as Create Rule Definition.

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `200` | Rule definition updated successfully |
| `400` | Invalid rule definition data |
| `404` | Rule definition not found |
| `422` | YAML DSL validation failed |
| `500` | Internal server error |

### Get Rule Definition by ID

Retrieve a specific rule definition by its UUID.

**Endpoint:** `GET /api/v1/rules/definitions/{id}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Unique identifier of the rule definition |

#### Response Schema

Same as Create Rule Definition response.

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `200` | Rule definition retrieved successfully |
| `404` | Rule definition not found |
| `500` | Internal server error |

### Get Rule Definition by Code

Retrieve a rule definition by its unique code.

**Endpoint:** `GET /api/v1/rules/definitions/by-code/{code}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `code` | string | Unique code of the rule definition |

#### Response Schema

Same as Create Rule Definition response.

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `200` | Rule definition retrieved successfully |
| `404` | Rule definition not found |
| `500` | Internal server error |

### Delete Rule Definition

Delete a rule definition by ID.

**Endpoint:** `DELETE /api/v1/rules/definitions/{id}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Unique identifier of the rule definition |

#### Response

- **Status:** `204 No Content`
- **Body:** Empty

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `204` | Rule definition deleted successfully |
| `404` | Rule definition not found |
| `500` | Internal server error |

### Filter Rule Definitions

Retrieve rule definitions with filtering and pagination.

**Endpoint:** `POST /api/v1/rules/definitions/filter`

#### Request Schema

```json
{
  "filters": {
    "isActive": true,
    "tags": "credit",
    "name": "Credit Scoring"
  },
  "rangeFilters": {
    "ranges": {
      "createdAt": {
        "from": "2025-01-01T00:00:00Z",
        "to": "2025-12-31T23:59:59Z"
      }
    }
  },
  "pagination": {
    "pageNumber": 0,
    "pageSize": 20,
    "sortBy": "createdAt",
    "sortDirection": "DESC"
  },
  "options": {
    "caseInsensitiveStrings": true,
    "includeInheritedFields": false
  }
}
```

#### Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `filters` | object | No | Field-based filters using RuleDefinitionDTO structure |
| `rangeFilters` | object | No | Range filters for date/numeric fields |
| `pagination` | object | Yes | Pagination parameters |
| `options` | object | No | Filter behavior options |

#### Pagination Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `pageNumber` | integer | 0 | Zero-based page number |
| `pageSize` | integer | 10 | Number of items per page |
| `sortBy` | string | "createdAt" | Field to sort by |
| `sortDirection` | string | "DESC" | Sort direction (ASC/DESC) |

#### Response Schema

```json
{
  "content": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "code": "credit_scoring_v1",
      "name": "Credit Scoring Rule v1",
      "description": "Basic credit scoring rule for loan applications",
      "yamlContent": "name: \"Credit Scoring\"...",
      "version": "1.0.0",
      "isActive": true,
      "tags": "credit,scoring,loan",
      "createdBy": "risk_team",
      "updatedBy": "risk_team",
      "createdAt": "2025-01-12T10:30:00Z",
      "updatedAt": "2025-01-12T10:30:00Z"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false
}
```

### Validate Rule Definition

Validate YAML DSL content without storing it.

**Endpoint:** `POST /api/v1/rules/definitions/validate`

#### Request

Raw YAML content as string in request body.

```yaml
name: "Test Rule"
description: "Test validation"

inputs:
  - creditScore

when:
  - creditScore at_least MIN_CREDIT_SCORE

then:
  - set is_eligible to true

output:
  is_eligible: boolean
```

#### Response Schema

```json
{
  "status": "VALID",
  "isValid": true,
  "errors": [],
  "warnings": []
}
```

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `200` | Validation completed successfully |
| `400` | Invalid YAML content |
| `500` | Internal server error |

## Validation API

The Validation API provides comprehensive YAML DSL validation capabilities with detailed feedback and suggestions.

### Validate YAML DSL

Comprehensive validation of YAML DSL content with detailed feedback.

**Endpoint:** `POST /api/v1/validation/yaml`

#### Request Schema

```json
{
  "yamlContent": "name: \"Test Rule\"\ndescription: \"Test\"\n\ninputs:\n  - creditScore\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n\nthen:\n  - set is_eligible to true\n\noutput:\n  is_eligible: boolean",
  "categories": ["SYNTAX", "NAMING", "LOGIC"],
  "minSeverity": "WARNING",
  "includeSuggestions": true,
  "includeMetrics": false
}
```

#### Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `yamlContent` | string | Yes | YAML DSL content to validate (max 100KB) |
| `categories` | array | No | Validation categories to run |
| `minSeverity` | string | No | Minimum severity level (INFO, WARNING, ERROR, CRITICAL) |
| `includeSuggestions` | boolean | No | Include improvement suggestions (default: true) |
| `includeMetrics` | boolean | No | Include performance metrics (default: false) |

#### Validation Categories

| Category | Description |
|----------|-------------|
| `SYNTAX` | YAML syntax and DSL structure |
| `NAMING` | Variable naming conventions |
| `DEPENDENCIES` | Variable dependencies and order |
| `LOGIC` | Business logic and rule semantics |
| `PERFORMANCE` | Performance optimization suggestions |
| `BEST_PRACTICES` | Code quality and maintainability |

#### Response Schema

```json
{
  "status": "WARNING",
  "summary": {
    "totalIssues": 2,
    "criticalErrors": 0,
    "errors": 0,
    "warnings": 2,
    "suggestions": 1,
    "qualityScore": 85.5
  },
  "issues": {
    "syntax": [],
    "naming": [
      {
        "code": "NAMING_001",
        "severity": "WARNING",
        "message": "Variable name should use camelCase",
        "location": {
          "line": 5,
          "column": 3,
          "path": "inputs[0]"
        },
        "suggestion": "Use 'creditScore' instead of 'credit_score'"
      }
    ],
    "logic": [],
    "performance": [],
    "bestPractices": []
  },
  "suggestions": [
    {
      "category": "PERFORMANCE",
      "message": "Consider adding circuit breaker for external calls",
      "priority": "LOW"
    }
  ],
  "metadata": {
    "validatorVersion": "2.1.0",
    "validatedAt": "2025-01-15T10:30:00Z",
    "validationTimeMs": 125,
    "ruleName": "Test Rule"
  }
}
```

## Constants Management API

The Constants API allows management of system constants used in rule expressions. Constants follow `UPPER_CASE_WITH_UNDERSCORES` naming convention and are automatically available in YAML DSL rules.

### Filter Constants

Retrieve constants with filtering and pagination.

**Endpoint:** `POST /api/v1/constants/filter`

#### Request Schema

```json
{
  "filters": {
    "valueType": "NUMBER",
    "required": true,
    "code": "MIN_"
  },
  "rangeFilters": {
    "ranges": {
      "createdAt": {
        "from": "2025-01-01T00:00:00Z",
        "to": "2025-12-31T23:59:59Z"
      }
    }
  },
  "pagination": {
    "pageNumber": 0,
    "pageSize": 20,
    "sortBy": "code",
    "sortDirection": "ASC"
  }
}
```

#### Response Schema

```json
{
  "content": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "code": "MIN_CREDIT_SCORE",
      "value": "650",
      "valueType": "NUMBER",
      "description": "Minimum credit score for loan approval",
      "required": true,
      "createdBy": "system_admin",
      "updatedBy": "system_admin",
      "createdAt": "2025-01-12T10:30:00Z",
      "updatedAt": "2025-01-12T10:30:00Z"
    }
  ],
  "totalElements": 50,
  "totalPages": 3,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false
}
```

### Create Constant

Create a new system constant.

**Endpoint:** `POST /api/v1/constants`

#### Request Schema

```json
{
  "code": "MIN_CREDIT_SCORE",
  "value": "650",
  "valueType": "NUMBER",
  "description": "Minimum credit score required for loan approval",
  "required": true,
  "createdBy": "system_admin"
}
```

#### Request Fields

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| `code` | string | Yes | Pattern: `^[A-Z][A-Z0-9_]*$`, 3-100 chars | Unique constant identifier |
| `value` | string | Yes | Max 1000 chars | String representation of the value |
| `valueType` | string | Yes | Enum: STRING, NUMBER, BOOLEAN, DATE, OBJECT | Data type of the constant |
| `description` | string | No | Max 500 chars | Description of the constant's purpose |
| `required` | boolean | Yes | - | Whether this constant is required for rules |
| `createdBy` | string | No | Max 255 chars | User who created the constant |

#### Response Schema

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "code": "MIN_CREDIT_SCORE",
  "name": "Minimum Credit Score",
  "valueType": "NUMBER",
  "required": true,
  "description": "Minimum credit score required for loan approval",
  "currentValue": 650,
  "createdAt": "2025-01-12T10:30:00Z",
  "updatedAt": "2025-01-12T10:30:00Z"
}
```

### Update Constant

Update an existing constant.

**Endpoint:** `PUT /api/v1/constants/{constantId}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `constantId` | UUID | Unique identifier of the constant |

#### Request/Response Schema

Same as Create Constant.

### Delete Constant

Delete a constant by ID.

**Endpoint:** `DELETE /api/v1/constants/{constantId}`

#### Response

- **Status:** `204 No Content`
- **Body:** Empty

### Get Constant by ID

Retrieve a specific constant by its ID.

**Endpoint:** `GET /api/v1/constants/{constantId}`

#### Response Schema

Same as Create Constant response.

### Get Constant by Code

Retrieve a constant by its unique code.

**Endpoint:** `GET /api/v1/constants/code/{code}`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `code` | string | Unique code of the constant |

#### Response Schema

Same as Create Constant response.

## Audit Trail API

The Audit Trail API provides comprehensive tracking and querying capabilities for all rule engine operations including rule evaluations, definition changes, and system events.

### Query Audit Trail

Retrieve audit trail records with filtering and pagination.

**Endpoint:** `POST /api/v1/audit-trail/filter`

#### Request Schema

```json
{
  "filters": {
    "operationType": "RULE_EVALUATION",
    "entityType": "RULE_DEFINITION",
    "userId": "system_admin",
    "success": true
  },
  "rangeFilters": {
    "ranges": {
      "timestamp": {
        "from": "2025-01-01T00:00:00Z",
        "to": "2025-01-15T23:59:59Z"
      }
    }
  },
  "pagination": {
    "pageNumber": 0,
    "pageSize": 50,
    "sortBy": "timestamp",
    "sortDirection": "DESC"
  }
}
```

#### Request Fields

| Field | Type | Description |
|-------|------|-------------|
| `filters` | object | Field-based filters for audit records |
| `rangeFilters` | object | Date/time range filters |
| `pagination` | object | Pagination and sorting parameters |

#### Operation Types

| Type | Description |
|------|-------------|
| `RULE_EVALUATION` | Rule execution and evaluation |
| `RULE_DEFINITION_CREATE` | Rule definition creation |
| `RULE_DEFINITION_UPDATE` | Rule definition modification |
| `RULE_DEFINITION_DELETE` | Rule definition deletion |
| `CONSTANT_CREATE` | System constant creation |
| `CONSTANT_UPDATE` | System constant modification |
| `VALIDATION` | YAML DSL validation operations |

#### Response Schema

```json
{
  "content": [
    {
      "id": "audit-123e4567-e89b-12d3-a456-426614174000",
      "operationType": "RULE_EVALUATION",
      "entityType": "RULE_DEFINITION",
      "entityId": "rule-456",
      "userId": "system_user",
      "timestamp": "2025-01-15T10:30:00Z",
      "requestData": {
        "ruleCode": "credit_scoring_v1",
        "inputData": {"creditScore": 750}
      },
      "responseData": {
        "success": true,
        "outputData": {"is_eligible": true}
      },
      "executionTimeMs": 45,
      "success": true,
      "errorMessage": null,
      "metadata": {
        "ipAddress": "192.168.1.100",
        "userAgent": "RuleEngine-Client/1.0",
        "sessionId": "session-789"
      }
    }
  ],
  "totalElements": 1250,
  "totalPages": 25,
  "size": 50,
  "number": 0,
  "first": true,
  "last": false
}
```

#### HTTP Status Codes

| Status | Description |
|--------|-------------|
| `200` | Audit trail retrieved successfully |
| `400` | Invalid filter parameters |
| `500` | Internal server error |

## Health and Monitoring

### Health Check

Check the health status of the application.

**Endpoint:** `GET /actuator/health`

#### Response Schema

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 91943821312,
        "threshold": 10485760,
        "exists": true
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### Application Info

Get application information.

**Endpoint:** `GET /actuator/info`

#### Response Schema

```json
{
  "app": {
    "name": "fireflyframework-rule-engine",
    "version": "1.0.0",
    "description": "Rule Engine Core Application"
  },
  "build": {
    "version": "1.0.0",
    "artifact": "fireflyframework-rule-engine-web",
    "name": "fireflyframework-rule-engine-web",
    "group": "org.fireflyframework",
    "time": "2025-01-12T10:30:00.000Z"
  }
}
```

### Metrics

Get application metrics (Prometheus format).

**Endpoint:** `GET /actuator/prometheus`

#### Response

Returns metrics in Prometheus format for monitoring and alerting.



## Examples

### Complete Credit Scoring Example

This example demonstrates a complete credit scoring workflow using the API.

#### 1. Create Required Constants

First, create the constants needed for the rule:

```bash
# Create minimum credit score constant
curl -X POST http://localhost:8080/api/v1/constants \
  -H "Content-Type: application/json" \
  -d '{
    "code": "MIN_CREDIT_SCORE",
    "name": "Minimum Credit Score",
    "valueType": "NUMBER",
    "required": true,
    "description": "Minimum credit score for loan approval",
    "currentValue": 650
  }'

# Create minimum income constant
curl -X POST http://localhost:8080/api/v1/constants \
  -H "Content-Type: application/json" \
  -d '{
    "code": "MIN_ANNUAL_INCOME",
    "name": "Minimum Annual Income",
    "valueType": "NUMBER",
    "required": true,
    "description": "Minimum annual income for loan approval",
    "currentValue": 40000
  }'
```

#### 2. Evaluate Credit Scoring Rule

```bash
curl -X POST http://localhost:8080/api/v1/rules/evaluate/plain \
  -H "Content-Type: application/json" \
  -d '{
    "yamlContent": "name: \"Advanced Credit Scoring\"\ndescription: \"Comprehensive credit assessment with multiple factors\"\n\ninputs:\n  - creditScore\n  - annualIncome\n  - employmentYears\n  - existingDebt\n  - requestedAmount\n\nwhen:\n  - creditScore at_least MIN_CREDIT_SCORE\n  - annualIncome at_least MIN_ANNUAL_INCOME\n  - employmentYears at_least 1\n\nthen:\n  - set basic_eligible to true\n  - calculate debt_to_income as existingDebt / annualIncome\n  - calculate loan_to_income as requestedAmount / annualIncome\n  - set risk_score to 0\n  - if creditScore at_least 750 then add 30 to risk_score\n  - if creditScore between 650 and 749 then add 20 to risk_score\n  - if debt_to_income less_than 0.3 then add 25 to risk_score\n  - if debt_to_income between 0.3 and 0.5 then add 15 to risk_score\n  - if loan_to_income less_than 3 then add 20 to risk_score\n  - if employmentYears at_least 5 then add 10 to risk_score\n  - if risk_score at_least 70 then set decision to \"APPROVED\"\n  - if risk_score between 50 and 69 then set decision to \"CONDITIONAL\"\n  - if risk_score less_than 50 then set decision to \"DECLINED\"\n  - if decision equals \"APPROVED\" then set interest_rate to 3.5\n  - if decision equals \"CONDITIONAL\" then set interest_rate to 4.5\n  - if decision equals \"DECLINED\" then set interest_rate to 0\n\nelse:\n  - set basic_eligible to false\n  - set decision to \"DECLINED\"\n  - set interest_rate to 0\n  - set rejection_reason to \"Does not meet basic requirements\"\n\noutput:\n  decision: decision\n  risk_score: risk_score\n  interest_rate: interest_rate\n  debt_to_income_ratio: debt_to_income\n  loan_to_income_ratio: loan_to_income\n  basic_eligible: basic_eligible\n  rejection_reason: rejection_reason",
    "inputData": {
      "creditScore": 720,
      "annualIncome": 75000,
      "employmentYears": 3,
      "existingDebt": 15000,
      "requestedAmount": 200000
    },
    "metadata": {
      "applicationId": "app-12345",
      "customerId": "cust-67890",
      "loanType": "MORTGAGE",
      "requestSource": "ONLINE_APPLICATION"
    },
    "includeDetails": true,
    "debugMode": false
  }'
```

#### Expected Response

```json
{
  "success": true,
  "conditionResult": true,
  "outputData": {
    "decision": "CONDITIONAL",
    "risk_score": 55,
    "interest_rate": 4.5,
    "debt_to_income_ratio": 0.2,
    "loan_to_income_ratio": 2.67,
    "basic_eligible": true,
    "rejection_reason": null
  },
  "executionTimeMs": 125,
  "circuitBreakerTriggered": false,
  "circuitBreakerMessage": null,
  "error": null
}
```

### Financial Validation and Calculation Example

This example demonstrates the new financial operators and calculation functions:

```bash
curl -X POST http://localhost:8080/api/v1/rules/evaluate/plain \
  -H "Content-Type: application/json" \
  -d '{
    "yamlContent": "name: \"Advanced Financial Assessment\"\ndescription: \"Comprehensive financial validation and calculation using new operators\"\n\ninputs:\n  - creditScore\n  - socialSecurityNumber\n  - accountNumber\n  - routingNumber\n  - birthDate\n  - annualIncome\n  - monthlyDebt\n  - requestedAmount\n  - employmentYears\n\nwhen:\n  - creditScore is_credit_score\n  - socialSecurityNumber is_ssn\n  - accountNumber is_account_number\n  - routingNumber is_routing_number\n  - birthDate age_at_least 18\n  - annualIncome is_positive\n  - requestedAmount is_currency\n\nthen:\n  - call calculate_debt_ratio with [monthlyDebt, annualIncome / 12] and store in debt_to_income\n  - call calculate_loan_payment with [requestedAmount, 0.045, 360] and store in monthly_payment\n  - call calculate_credit_score with [0.95, debt_to_income, employmentYears, 1, 5] and store in calculated_score\n  - call calculate_risk_score with [creditScore, annualIncome, debt_to_income, employmentYears] and store in risk_score\n  - call generate_account_number with [\"LOAN\", 12] and store in loan_account_number\n  - call format_currency with [monthly_payment, \"USD\"] and store in formatted_payment\n  - call format_percentage with [debt_to_income] and store in formatted_dti\n  - if debt_to_income is_percentage and debt_to_income less_than 0.3 then set risk_level to \"LOW\"\n  - if debt_to_income between 0.3 and 0.5 then set risk_level to \"MEDIUM\"\n  - if debt_to_income greater_than 0.5 then set risk_level to \"HIGH\"\n  - set is_eligible to true\n  - call audit_log with [\"FINANCIAL_ASSESSMENT\", \"Comprehensive validation completed\"]\n\nelse:\n  - set is_eligible to false\n  - set risk_level to \"UNQUALIFIED\"\n  - set rejection_reason to \"Failed financial validation\"\n  - call audit_log with [\"FINANCIAL_ASSESSMENT\", \"Validation failed\"]\n\noutput:\n  is_eligible: is_eligible\n  risk_level: risk_level\n  debt_to_income: debt_to_income\n  monthly_payment: monthly_payment\n  calculated_score: calculated_score\n  risk_score: risk_score\n  loan_account_number: loan_account_number\n  formatted_payment: formatted_payment\n  formatted_dti: formatted_dti\n  rejection_reason: rejection_reason",
    "inputData": {
      "creditScore": 750,
      "socialSecurityNumber": "123-45-6789",
      "accountNumber": "12345678901",
      "routingNumber": "021000021",
      "birthDate": "1985-06-15",
      "annualIncome": 85000,
      "monthlyDebt": 2000,
      "requestedAmount": 250000,
      "employmentYears": 5
    },
    "metadata": {
      "applicationId": "app-fin-001",
      "customerId": "cust-12345",
      "loanType": "MORTGAGE"
    }
  }'
```

### AML Risk Assessment Example

```bash
curl -X POST http://localhost:8080/api/v1/rules/evaluate/plain \
  -H "Content-Type: application/json" \
  -d '{
    "yamlContent": "name: \"AML Risk Assessment\"\ndescription: \"Anti-Money Laundering transaction monitoring\"\n\ninputs:\n  - transactionAmount\n  - customerRiskProfile\n  - transactionFreq24h\n  - accountAgeDays\n  - geographicRiskScore\n  - transactionType\n\nwhen:\n  - transactionAmount greater_than AML_THRESHOLD_AMOUNT\n  - customerRiskProfile in_list [\"HIGH\", \"UNKNOWN\"]\n  - transactionFreq24h greater_than MAX_DAILY_TRANSACTIONS\n\nthen:\n  - set aml_risk_score to 85\n  - set requires_manual_review to true\n  - set compliance_flag to \"AML_REVIEW_REQUIRED\"\n  - set review_priority to \"HIGH\"\n\nelse:\n  - calculate base_risk as geographicRiskScore * 0.3\n  - add transactionAmount / 1000 to base_risk\n  - if accountAgeDays less_than 30 then add 10 to base_risk\n  - set aml_risk_score to base_risk\n  - set requires_manual_review to false\n  - if base_risk greater_than 50 then set compliance_flag to \"MONITOR\"\n  - if base_risk less_than_or_equal 50 then set compliance_flag to \"CLEAR\"\n\noutput:\n  aml_risk_score: aml_risk_score\n  requires_manual_review: requires_manual_review\n  compliance_flag: compliance_flag\n  review_priority: review_priority",
    "inputData": {
      "transactionAmount": 15000,
      "customerRiskProfile": "HIGH",
      "transactionFreq24h": 6,
      "accountAgeDays": 45,
      "geographicRiskScore": 7,
      "transactionType": "WIRE_TRANSFER"
    },
    "metadata": {
      "transactionId": "txn-98765",
      "customerId": "cust-54321",
      "channel": "ONLINE_BANKING"
    }
  }'
```
