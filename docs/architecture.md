# Architecture Documentation

This document provides a comprehensive overview of the Firefly Framework Rule Engine architecture, including the modern AST-based system design, module structure, data flow, and integration patterns.

## Table of Contents

- [System Overview](#system-overview)
- [AST-Based Architecture](#ast-based-architecture)
- [Module Architecture](#module-architecture)
- [AST Processing Pipeline](#ast-processing-pipeline)
- [Data Flow](#data-flow)
- [Variable Resolution](#variable-resolution)
- [Technology Stack](#technology-stack)
- [Design Patterns](#design-patterns)
- [Core Engine Components](#core-engine-components)
- [Validation Architecture](#validation-architecture)
- [Performance Considerations](#performance-considerations)
  - [AST-Based Performance Optimizations](#1-ast-based-performance-optimizations)
  - [Reactive Architecture](#2-reactive-architecture)
  - [Advanced Caching System](#3-advanced-caching-system)
  - [Optimized Connection Pooling](#4-optimized-connection-pooling)
  - [Batch Processing Optimization](#5-batch-processing-optimization)
  - [Memory Management & Optimization](#6-memory-management--optimization)
  - [Performance Monitoring & Metrics](#7-performance-monitoring--metrics)
- [Audit Trail System](#audit-trail-system)
- [Security Architecture](#security-architecture)

## System Overview

The Firefly Framework Rule Engine is built as a reactive, microservice-ready application using Spring Boot 3 and WebFlux. It features a **modern AST-based (Abstract Syntax Tree) architecture** that completely replaces the legacy string-based evaluation system with structured, type-safe rule processing. The system includes comprehensive **audit trail capabilities** for compliance and monitoring, tracking all rule operations and evaluations with detailed metadata.

```mermaid
graph TB
    subgraph "External Systems"
        CLIENT[Client Applications]
        DB[(PostgreSQL Database)]
        MONITOR[Monitoring Systems]
    end

    subgraph "Firefly Framework Rule Engine - AST Architecture"
        subgraph "Web Layer"
            API[REST Controllers]
            VALIDATION_API[Validation Controller]
            SWAGGER[OpenAPI/Swagger]
        end

        subgraph "AST Core Engine"
            AST_PARSER[AST Rules DSL Parser]
            AST_ENGINE[AST Rules Evaluation Engine]
            AST_VALIDATOR[YAML DSL Validator]
            VISITOR_PATTERN[Visitor Pattern Implementation]
        end

        subgraph "AST Visitors"
            EXPR_EVAL[Expression Evaluator]
            ACTION_EXEC[Action Executor]
            VALIDATION_VISITOR[Validation Visitor]
            VAR_COLLECTOR[Variable Reference Collector]
        end

        subgraph "AST Model"
            AST_NODES[AST Node Hierarchy]
            CONDITIONS[Condition Nodes]
            EXPRESSIONS[Expression Nodes]
            ACTIONS[Action Nodes]
        end

        subgraph "Service Layer"
            CONST_SVC[Constant Service]
            RULE_SVC[Rule Definition Service]
            EVAL_SVC[Rules Evaluation Service]
            AUDIT_SVC[Audit Trail Service]
        end

        subgraph "Data Layer"
            REPOS[R2DBC Repositories]
            ENTITIES[JPA Entities]
            AUDIT_REPO[Audit Trail Repository]
        end
    end

    CLIENT --> API
    API --> AST_ENGINE
    AST_ENGINE --> AST_PARSER
    AST_PARSER --> AST_NODES
    AST_ENGINE --> VISITOR_PATTERN
    VISITOR_PATTERN --> EXPR_EVAL
    VISITOR_PATTERN --> ACTION_EXEC
    AST_VALIDATOR --> VALIDATION_VISITOR
    AST_ENGINE --> VAR_COLLECTOR
    VAR_COLLECTOR --> CONST_SVC
    CONST_SVC --> REPOS
    REPOS --> DB
    API --> SWAGGER
    AST_ENGINE --> MONITOR
    EVAL_SVC --> AUDIT_SVC
    RULE_SVC --> AUDIT_SVC
    AUDIT_SVC --> AUDIT_REPO
    AUDIT_REPO --> DB

    style CLIENT fill:#e1f5fe
    style DB fill:#fff3e0
    style AST_ENGINE fill:#e8f5e8
    style VISITOR_PATTERN fill:#f3e5f5
    style AST_NODES fill:#e0f2f1
```

## AST-Based Architecture

The Firefly Framework Rule Engine has been completely modernized with an **Abstract Syntax Tree (AST) based architecture** that provides:

### 🏗️ **Structured Rule Processing**
- **Type-Safe Evaluation**: AST nodes provide compile-time type safety
- **Visitor Pattern**: Clean separation of concerns for different operations
- **Extensible Design**: Easy to add new operators, functions, and validation rules
- **Performance Optimized**: Direct AST traversal eliminates string parsing overhead

### 🔄 **Legacy vs. Modern Architecture**

| Aspect | Legacy (String-Based) | Modern (AST-Based) |
|--------|----------------------|-------------------|
| **Parsing** | String manipulation | Structured AST nodes |
| **Evaluation** | Runtime string parsing | Direct AST traversal |
| **Validation** | Basic syntax checking | Comprehensive semantic validation |
| **Type Safety** | Runtime type checking | Compile-time type safety |
| **Performance** | String parsing overhead | Optimized AST operations |
| **Extensibility** | Monolithic evaluators | Visitor pattern modularity |
| **Audit Trail** | Limited logging | Comprehensive audit tracking |
| **Compliance** | Manual tracking | Automated compliance reporting |

## Module Architecture

The system is organized into five distinct Maven modules, each with specific responsibilities:

### 1. fireflyframework-rule-engine-web
**Purpose**: Web layer and REST API endpoints

**Key Components**:
- `RulesEvaluationController` - Main API for rule evaluation using AST engine
- `RuleDefinitionController` - CRUD operations for YAML DSL rule definitions
- `ValidationController` - Comprehensive YAML DSL validation endpoints
- `ConstantController` - CRUD operations for system constants
- `AuditTrailController` - Audit trail querying and reporting endpoints
- `RuleEngineApplication` - Spring Boot main application
- OpenAPI/Swagger configuration

**Dependencies**:
- `fireflyframework-rule-engine-core`
- `fireflyframework-rule-engine-interfaces`

### 2. fireflyframework-rule-engine-core
**Purpose**: AST-based core business logic and rule evaluation engine

**Key Components**:
- **AST Engine**: `ASTRulesEvaluationEngine` - Pure AST-based rule evaluation orchestrator
- **AST Parser**: `ASTRulesDSLParser` - Converts YAML to structured AST nodes
- **AST Validation**: `YamlDslValidator` - Comprehensive DSL validation using AST
- **AST Visitors**:
  - `ExpressionEvaluator` - Evaluates expressions using visitor pattern
  - `ActionExecutor` - Executes actions using visitor pattern
  - `ValidationVisitor` - Validates AST nodes for semantic correctness
  - `VariableReferenceCollector` - Extracts variable references for constant loading
- **AST Model**: Complete hierarchy of AST node classes
- **Services**: `RuleDefinitionService`, `RulesEvaluationService`, `ConstantService`, `AuditTrailService` - Business logic services
- **Context**: `EvaluationContext` - Maintains state during AST evaluation
- **Audit Integration**: Comprehensive audit trail recording for all operations

**Dependencies**:
- `fireflyframework-rule-engine-interfaces`
- `fireflyframework-rule-engine-models`

### 3. fireflyframework-rule-engine-models
**Purpose**: Data entities and repository interfaces

**Key Components**:
- `Constant` - Entity for system constants
- `RuleDefinition` - Entity for stored YAML DSL rule definitions
- `AuditTrail` - Entity for audit trail records
- `ConstantRepository` - R2DBC repository interface for constants
- `RuleDefinitionRepository` - R2DBC repository interface for rule definitions
- `AuditTrailRepository` - R2DBC repository interface for audit trails
- Database migration scripts (V1__Create_constants_table.sql, V2__Create_rule_definitions_table.sql, V3__Create_audit_trails_table.sql)

**Dependencies**: None (base module)

### 4. fireflyframework-rule-engine-interfaces
**Purpose**: DTOs, service interfaces, and contracts

**Key Components**:
- `RulesEvaluationRequestDTO` - API request structure
- `RulesEvaluationResponseDTO` - API response structure
- `ConstantDTO` - Data transfer object for constants
- Service interfaces and enums

**Dependencies**: None (base module)

### 5. fireflyframework-rule-engine-sdk
**Purpose**: Client SDK for integration (future implementation)

**Key Components**:
- Client libraries for Java applications
- Helper utilities for rule management

## AST Processing Pipeline

The AST-based processing pipeline transforms YAML rules through multiple stages:

```mermaid
graph LR
    A[YAML Input] --> B[YAML Parser]
    B --> C[AST Model Creation]
    C --> D[AST Validation]
    D --> E[Variable Collection]
    E --> F[Constant Loading]
    F --> G[AST Evaluation]
    G --> H[Result Generation]

    subgraph "AST Visitors"
        I[Expression Evaluator]
        J[Action Executor]
        K[Validation Visitor]
        L[Variable Collector]
    end

    D --> K
    E --> L
    G --> I
    G --> J

    style C fill:#e0f2f1
    style G fill:#e8f5e8
    style I fill:#f3e5f5
    style J fill:#f3e5f5
```

### AST Node Hierarchy

The system uses a comprehensive AST node hierarchy:

```
ASTNode (abstract base)
├── Expression
│   ├── LiteralExpression (numbers, strings, booleans)
│   ├── VariableExpression (variable references)
│   ├── BinaryExpression (arithmetic operations)
│   ├── FunctionCallExpression (function calls)
│   └── ListExpression (array literals)
├── Condition
│   ├── ComparisonCondition (>, <, ==, etc.)
│   ├── LogicalCondition (AND, OR, NOT)
│   ├── ListCondition (in, not_in)
│   └── ValidationCondition (is_null, is_empty, etc.)
└── Action
    ├── SetAction (variable assignment)
    ├── CalculateAction (arithmetic calculations)
    ├── FunctionCallAction (function execution)
    └── CircuitBreakerAction (execution control)
```

## Data Flow

### Rule Evaluation Flow

#### AST-Based Direct YAML Evaluation
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant ASTEngine
    participant ASTParser
    participant ASTModel
    participant Context
    participant Database
    participant Visitors
    participant Collectors

    Client->>Controller: POST /api/v1/rules/evaluation/evaluate
    Controller->>ASTEngine: evaluateRulesReactive(yaml, inputData)
    ASTEngine->>ASTParser: parseRules(yaml)
    ASTParser->>ASTModel: convertToASTModel(yamlMap)
    ASTModel-->>ASTEngine: ASTRulesDSL object

    ASTEngine->>Context: createEvaluationContext()
    Context->>Context: setInputVariables(inputData)

    ASTEngine->>Collectors: VariableReferenceCollector.collect(astModel)
    Collectors-->>ASTEngine: Set<String> variableReferences
    ASTEngine->>ASTEngine: filterConstantNames(UPPER_CASE pattern)
    ASTEngine->>Database: loadSystemConstants()
    Database-->>Context: setSystemConstants()

    ASTEngine->>Visitors: ExpressionEvaluator.visit(conditions)
    Visitors->>Context: getValue(variableName)
    Context-->>Visitors: resolved value

    alt Conditions Met
        ASTEngine->>Visitors: ActionExecutor.visit(thenActions)
    else Conditions Not Met
        ASTEngine->>Visitors: ActionExecutor.visit(elseActions)
    end

    Visitors->>Context: setComputedVariable()
    ASTEngine->>ASTEngine: generateOutput()
    ASTEngine-->>Controller: ASTRulesEvaluationResult
    Controller-->>Client: JSON Response
```

#### AST-Based Stored Rule Evaluation
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant RuleService
    participant Database
    participant ASTEngine
    participant ASTParser
    participant Context
    participant Visitors

    Client->>Controller: POST /api/v1/rules/evaluation/by-code
    Controller->>RuleService: evaluateRuleByCode(code, inputData)
    RuleService->>Database: findByCode(code)
    Database-->>RuleService: RuleDefinition entity

    alt Rule Found and Active
        RuleService->>ASTEngine: evaluateRulesReactive(yamlContent, inputData)
        ASTEngine->>ASTParser: parseRules(yamlContent)
        ASTParser-->>ASTEngine: ASTRulesDSL object
        ASTEngine->>Context: createEvaluationContext()
        Context->>Context: setInputVariables(inputData)

        ASTEngine->>Database: loadSystemConstants()
        Database-->>Context: setSystemConstants()

        ASTEngine->>Visitors: ExpressionEvaluator.visit(conditions)
        ASTEngine->>Visitors: ActionExecutor.visit(actions)
        ASTEngine-->>RuleService: ASTRulesEvaluationResult
        RuleService-->>Controller: ASTRulesEvaluationResult
    else Rule Not Found or Inactive
        RuleService-->>Controller: Error Response
    end

    Controller-->>Client: JSON Response
```

#### AST-Based Rule Definition Storage Flow
```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant RuleService
    participant YamlValidator
    participant ASTParser
    participant ValidationVisitor
    participant Database

    Client->>Controller: POST /api/v1/rules/definitions
    Controller->>RuleService: createRuleDefinition(ruleDTO)
    RuleService->>YamlValidator: validate(yamlContent)

    YamlValidator->>YamlValidator: performSyntaxValidation()
    YamlValidator->>ASTParser: parseRules(yamlContent)
    ASTParser-->>YamlValidator: ASTRulesDSL object
    YamlValidator->>YamlValidator: performDSLReferenceValidation()
    YamlValidator->>ValidationVisitor: visit(astNodes)
    ValidationVisitor-->>YamlValidator: List<ValidationError>
    YamlValidator->>YamlValidator: performNamingValidation()
    YamlValidator->>YamlValidator: performOperatorValidation()

    alt Validation Successful
        YamlValidator-->>RuleService: ValidationResult (valid)
        RuleService->>Database: existsByCode(code)

        alt Code Available
            Database-->>RuleService: false
            RuleService->>Database: save(ruleDefinition)
            Database-->>RuleService: Saved entity
            RuleService-->>Controller: RuleDefinitionDTO
        else Code Exists
            Database-->>RuleService: true
            RuleService-->>Controller: Error (duplicate code)
        end
    else Validation Failed
        YamlValidator-->>RuleService: ValidationResult (invalid)
        RuleService-->>Controller: Validation Error
    end

    Controller-->>Client: JSON Response
```

### Variable Resolution Priority & Naming Conventions

The system resolves variables in the following order using **distinct naming patterns**:

1. **Computed Variables** (highest priority - `snake_case`)
   - Created during rule execution with `calculate` or `set` actions
   - Can override input variables
   - Examples: `debt_to_income`, `risk_score`, `final_decision`

2. **Input Variables** (medium priority - `camelCase`)
   - Provided via API request in the `inputData` field
   - Runtime data specific to each evaluation
   - Examples: `creditScore`, `annualIncome`, `employmentYears`

3. **System Constants** (lowest priority - `UPPER_CASE_WITH_UNDERSCORES`)
   - Stored in database and auto-loaded when referenced
   - Follow regex pattern `^[A-Z][A-Z0-9_]*$`
   - Examples: `MIN_CREDIT_SCORE`, `MAX_LOAN_AMOUNT`, `RISK_MULTIPLIER`

```mermaid
graph LR
    A[Variable Reference] --> B{Check Computed}
    B -->|Found| C[Return Computed Value]
    B -->|Not Found| D{Check Input}
    D -->|Found| E[Return Input Value]
    D -->|Not Found| F{Check Constants}
    F -->|Found| G[Return Constant Value]
    F -->|Not Found| H[Return null]
    
    style C fill:#e8f5e8
    style E fill:#e1f5fe
    style G fill:#fff3e0
    style H fill:#ffebee
```

## Variable Resolution

### Constant Auto-Detection

The system automatically detects and loads constants from the database using this logic:

```java
private boolean isConstantName(String name) {
    // Constants must match: ^[A-Z][A-Z0-9_]*$
    return name.matches("^[A-Z][A-Z0-9_]*$");
}
```

**Examples**:
- ✅ `MIN_CREDIT_SCORE` - Auto-loaded from database (UPPER_CASE)
- ✅ `MAX_LOAN_AMOUNT_2024` - Auto-loaded from database (UPPER_CASE)
- ❌ `creditScore` - Treated as input variable (camelCase)
- ❌ `debt_to_income` - Treated as computed variable (snake_case)

### Context Management

The `EvaluationContext` maintains three separate maps:

```java
public class EvaluationContext {
    private Map<String, Object> inputVariables;     // From API request
    private Map<String, Object> systemConstants;    // From database
    private Map<String, Object> computedVariables;  // Calculated during execution
    
    public Object getValue(String name) {
        // Priority: Computed > Input > Constants
        if (computedVariables.containsKey(name)) return computedVariables.get(name);
        if (inputVariables.containsKey(name)) return inputVariables.get(name);
        return systemConstants.get(name);
    }
}
```

## Technology Stack

### Core Technologies
- **Java 25** - Latest LTS version with virtual threads (Java 21+ compatible)
- **Spring Boot 3.x** - Application framework
- **Spring WebFlux** - Reactive web stack
- **R2DBC** - Reactive database connectivity
- **PostgreSQL** - Primary database
- **Maven** - Build and dependency management

### Libraries and Frameworks
- **Jackson** - JSON/YAML processing
- **Lombok** - Code generation
- **MapStruct** - Object mapping
- **OpenAPI 3** - API documentation
- **Reactor** - Reactive programming
- **Flyway** - Database migrations

### Development Tools
- **Docker** - Containerization
- **Swagger UI** - Interactive API documentation
- **Actuator** - Health checks and metrics
- **Prometheus** - Metrics collection

## Design Patterns

### 1. Strategy Pattern
Used in `ConditionEvaluator` for different comparison operators:

```java
switch (operator.toLowerCase()) {
    case "greater_than": return compareGreaterThan(left, right);
    case "equals": return compareEquals(left, right);
    case "in_list": return compareInList(left, right);
    // ... more strategies
}
```

### 2. Builder Pattern
Extensively used for DTOs and domain objects:

```java
RulesEvaluationResult result = RulesEvaluationResult.builder()
    .success(true)
    .conditionResult(conditionResult)
    .outputData(outputData)
    .build();
```

### 3. Template Method Pattern
Used in `ActionExecutor` for different action types:

```java
public void execute(ActionBlock actionBlock, EvaluationContext context) {
    for (Action action : actionBlock.getActions()) {
        executeAction(action, context);  // Template method
    }
}
```

### 4. Factory Pattern
Used in `VariableResolver` for creating different value types:

```java
public Object resolveValue(Object value, EvaluationContext context) {
    if (value instanceof String) return resolveStringValue(value, context);
    if (value instanceof Map) return resolveMapValue(value, context);
    return value;  // Literal value
}
```

### 5. Registry Pattern
Used in both `ConditionEvaluator` and `ActionExecutor` for function registries:

```java
// ConditionEvaluator function registry
switch (functionName.toLowerCase()) {
    case "is_valid_credit_score": return isValidCreditScore(parameters);
    case "is_valid_ssn": return isValidSSN(parameters);
    case "debt_to_income_ratio": return calculateDebtToIncomeRatio(parameters);
    // ... more financial validation functions
}

// ActionExecutor function registry
switch (functionName.toLowerCase()) {
    case "calculate_loan_payment": return calculateLoanPayment(parameters);
    case "calculate_compound_interest": return calculateCompoundInterest(parameters);
    case "format_currency": return formatCurrency(parameters);
    // ... more financial calculation functions
}
```

## Core Engine Components

### AST-Based Evaluation Engine

The `ASTRulesEvaluationEngine` provides pure AST-based rule evaluation with:

**Core Features**:
- **Type-Safe Processing**: Direct AST node traversal eliminates string parsing
- **Visitor Pattern**: Clean separation of evaluation, execution, and validation logic
- **Reactive Support**: Full Mono/Flux integration for non-blocking operations
- **Comprehensive Logging**: Structured JSON logging with operation IDs
- **Error Handling**: Graceful degradation with detailed error reporting

### AST Visitor Pattern Implementation

The system uses the visitor pattern for all AST operations:

#### 1. ExpressionEvaluator Visitor
Evaluates expressions to their values using structured AST traversal:

```java
public class ExpressionEvaluator implements ASTVisitor<Object> {
    @Override
    public Object visitBinaryExpression(BinaryExpression node) {
        // Short-circuit AND/OR before eager evaluation
        if (node.getOperator() == BinaryOperator.AND) {
            return toBoolean(node.getLeft().accept(this)) && toBoolean(node.getRight().accept(this));
        }
        if (node.getOperator() == BinaryOperator.OR) {
            return toBoolean(node.getLeft().accept(this)) || toBoolean(node.getRight().accept(this));
        }
        Object left = node.getLeft().accept(this);
        Object right = node.getRight().accept(this);
        return evaluateOperation(node.getOperator(), left, right);
    }

    @Override
    public Object visitVariableExpression(VariableExpression node) {
        return context.getValue(node.getVariableName());
    }
}
```

#### 2. ActionExecutor Visitor
Executes actions using structured AST traversal:

```java
public class ActionExecutor implements ASTVisitor<Void> {
    @Override
    public Void visitSetAction(SetAction node) {
        Object value = node.getValue().accept(expressionEvaluator);
        context.setComputedVariable(node.getVariableName(), value);
        return null;
    }

    @Override
    public Void visitCalculateAction(CalculateAction node) {
        Object result = node.getExpression().accept(expressionEvaluator);
        context.setComputedVariable(node.getVariableName(), result);
        return null;
    }
}
```

#### 3. ValidationVisitor
Validates AST nodes for semantic correctness:

```java
public class ValidationVisitor implements ASTVisitor<List<ValidationError>> {
    @Override
    public List<ValidationError> visitVariableExpression(VariableExpression node) {
        List<ValidationError> errors = new ArrayList<>();
        if (!availableVariables.contains(node.getVariableName())) {
            errors.add(new ValidationError("Undefined variable: " + node.getVariableName()));
        }
        return errors;
    }
}
```

### Supported Operations

**Comparison Operators** (26 total):
- **Numeric**: `greater_than`, `less_than`, `at_least`, `at_most`, `between`, `equals`, `not_equals`
- **String**: `contains`, `starts_with`, `ends_with`, `matches`, `not_matches`
- **List**: `in_list`, `not_in_list`, `in`, `not_in`
- **Validation**: `is_null`, `is_not_null`, `is_empty`, `is_not_empty`, `is_numeric`
- **Financial**: `is_credit_score`, `is_ssn`, `is_account_number`, `is_routing_number`

**Built-in Functions**:
- **Financial**: `calculate_loan_payment`, `calculate_debt_ratio`, `debt_to_income_ratio`
- **Utility**: `format_currency`, `log`, `audit_log`

### Circuit Breaker Pattern

The AST engine implements circuit breaker functionality through dedicated action nodes:

```java
@Override
public Void visitCircuitBreakerAction(CircuitBreakerAction node) {
    context.setCircuitBreakerTriggered(true);
    context.setCircuitBreakerMessage(node.getMessage());
    return null; // Stop execution
}
```

**Use Cases**:
- High-risk transaction detection
- Fraud prevention
- System overload protection
- Compliance threshold enforcement

## Validation Architecture

The Firefly Framework Rule Engine features a comprehensive **multi-layer validation architecture** that ensures YAML DSL compliance and quality:

### Validation Pipeline

```mermaid
graph TD
    A[YAML Input] --> B[Syntax Validation]
    B --> C{Syntax Valid?}
    C -->|No| D[Return Syntax Errors]
    C -->|Yes| E[AST Parsing]
    E --> F[DSL Reference Validation]
    F --> G[Semantic Validation]
    G --> H[Naming Convention Validation]
    H --> I[Operator/Function Validation]
    I --> J[Best Practices Validation]
    J --> K[Generate Validation Result]

    style B fill:#fff3e0
    style E fill:#e0f2f1
    style G fill:#f3e5f5
    style K fill:#e8f5e8
```

### Validation Layers

#### 1. Syntax Validation
- **YAML Structure**: Validates basic YAML syntax and structure
- **Required Sections**: Ensures presence of mandatory sections (name, description, inputs, output)
- **Data Types**: Validates correct data types for each section

#### 2. AST-Based Semantic Validation
- **Variable References**: Validates all variable references using `ValidationVisitor`
- **Type Compatibility**: Ensures type compatibility in expressions and conditions
- **Function Calls**: Validates function names and parameter counts
- **Circular Dependencies**: Detects circular references in variable calculations

#### 3. DSL Reference Compliance
- **Complete Coverage**: Validates against the full YAML DSL Reference specification
- **Operator Support**: Ensures all operators are documented and supported
- **Function Registry**: Validates against built-in function registry
- **Syntax Patterns**: Enforces documented syntax patterns

#### 4. Naming Convention Validation
- **Input Variables**: Enforces `camelCase` naming (e.g., `creditScore`, `annualIncome`)
- **System Constants**: Validates `UPPER_CASE_WITH_UNDERSCORES` pattern (e.g., `MIN_CREDIT_SCORE`)
- **Computed Variables**: Enforces `snake_case` naming (e.g., `debt_to_income`, `final_score`)

#### 5. Best Practices Validation
- **Performance Optimization**: Suggests improvements for complex expressions
- **Code Quality**: Recommends better variable names and structure
- **Maintainability**: Identifies potential maintenance issues

### Validation Result Structure

```java
public class ValidationResult {
    private ValidationStatus status;           // VALID, WARNING, ERROR, CRITICAL_ERROR
    private ValidationSummary summary;         // Issue counts and quality score (0-100)
    private ValidationIssues issues;          // Categorized issues by type
    private List<ValidationSuggestion> suggestions; // Improvement recommendations
    private ValidationMetadata metadata;      // Validation process metadata
}
```

### Integration Points

- **ValidationController**: REST API endpoints for standalone validation
- **RuleDefinitionService**: Automatic validation before rule storage
- **YamlDslValidator**: Central orchestrator for all validation components
- **AST Visitors**: Deep semantic validation using visitor pattern

## Performance Considerations

The Firefly Framework Rule Engine implements enterprise-grade performance optimizations designed for high-load production environments. These optimizations provide significant performance improvements while maintaining reliability and scalability.

### 1. AST-Based Performance Optimizations
- **Direct AST Traversal**: Eliminates string parsing overhead during evaluation
- **Type-Safe Operations**: Compile-time type checking reduces runtime validation
- **Visitor Pattern Efficiency**: Single-pass AST traversal for multiple operations
- **Structured Memory Layout**: AST nodes provide better memory locality
- **Single-Pass Parsing**: YAML to AST conversion in one pass
- **Lazy Evaluation**: Expressions evaluated only when needed
- **Short-Circuit Logic**: Logical operators stop evaluation early when possible
- **Optimized Visitor Dispatch**: Direct method calls instead of reflection

### 2. Reactive Architecture
- **Non-blocking I/O**: WebFlux for high-throughput processing
- **Reactive Database Access**: R2DBC for non-blocking database operations
- **Parallel Rule Processing**: Concurrent evaluation of multiple rules
- **Backpressure Handling**: Reactive streams manage load automatically

### 3. Advanced Caching System

#### **Dual Cache Provider Architecture**

The system supports both local and distributed caching with configurable providers:

```mermaid
graph TB
    subgraph "Cache Provider Architecture"
        CP[CacheProvider Interface]

        subgraph "Local Caching"
            CAFFEINE[CaffeineCacheProvider]
            CAFFEINE_STATS[Cache Statistics]
        end

        subgraph "Distributed Caching"
            REDIS[RedisCacheProvider]
            REDIS_CLUSTER[Redis Cluster]
        end

        subgraph "Cache Services"
            AST_CACHE[AST Cache Service]
            CONST_CACHE[Constants Cache Service]
            VALIDATION_CACHE[Validation Cache Service]
        end
    end

    CP --> CAFFEINE
    CP --> REDIS
    CAFFEINE --> CAFFEINE_STATS
    REDIS --> REDIS_CLUSTER

    AST_CACHE --> CP
    CONST_CACHE --> CP
    VALIDATION_CACHE --> CP
```

#### **Cache Provider Performance Comparison**

| Metric | Caffeine (Local) | Redis (Distributed) | Performance Gain |
|--------|------------------|---------------------|------------------|
| **Read Operations** | 0.26 ms | 175.12 ms | **664x faster** |
| **Write Operations** | 0.25 ms | 44.28 ms | **180x faster** |
| **Network Overhead** | None | TCP/Redis Protocol | **Zero vs Network** |
| **Memory Usage** | JVM Heap | External Redis | **Local vs Remote** |
| **Persistence** | None | Configurable | **Volatile vs Persistent** |
| **Scalability** | Single JVM | Multi-instance | **Local vs Distributed** |

#### **Cache Configuration**

```yaml
firefly:
  rules:
    cache:
      provider: caffeine  # or 'redis'

      # Caffeine Configuration (Default - High Performance)
      caffeine:
        ast-cache:
          maximum-size: 1000
          expire-after-write: 2h
          expire-after-access: 30m
        constants-cache:
          maximum-size: 500
          expire-after-write: 15m
          expire-after-access: 5m
        validation-cache:
          maximum-size: 200
          expire-after-write: 1h
          expire-after-access: 15m

      # Redis Configuration (Optional - Distributed)
      redis:
        host: localhost
        port: 6379
        password: ${REDIS_PASSWORD:}
        database: 0
        timeout: 5s
        ttl:
          ast-cache: 2h
          constants-cache: 15m
          validation-cache: 1h
```

#### **Cache Usage Patterns**

- **AST Cache**: Stores parsed AST models using SHA-256 hash of YAML content as key
- **Constants Cache**: Caches system constants to avoid repeated database queries
- **Validation Cache**: Caches validation results for frequently validated rules
- **Automatic Eviction**: LRU eviction for Caffeine, TTL-based for Redis
- **Cache Statistics**: Real-time hit/miss rates and performance metrics

### 4. Optimized Connection Pooling

#### **Environment-Specific Pool Configuration**

```yaml
spring:
  r2dbc:
    pool:
      # Production Settings (High Load)
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
      max-acquire-time: 60s
      max-create-connection-time: 30s
      max-life-time: 1800s
      validation-query: SELECT 1
      validation-depth: LOCAL

      # Development Settings (Resource Efficient)
      # initial-size: 5
      # max-size: 10
      # max-idle-time: 15m
      # max-acquire-time: 30s
```

#### **Connection Pool Monitoring**

- **Pool Metrics**: Active, idle, pending connections
- **Performance Tracking**: Acquire time, validation time, creation time
- **Health Checks**: Connection validation and leak detection
- **Resource Management**: Automatic cleanup and lifecycle management

### 5. Batch Processing Optimization

#### **High-Throughput Concurrent Evaluation**

The batch processing system provides enterprise-grade concurrent rule evaluation:

```mermaid
graph TB
    subgraph "Batch Processing Architecture"
        BR[Batch Request] --> BV[Batch Validator]
        BV --> BS[Batch Scheduler]

        subgraph "Concurrent Processing"
            BS --> CE1[Concurrent Evaluator 1]
            BS --> CE2[Concurrent Evaluator 2]
            BS --> CE3[Concurrent Evaluator N]
        end

        subgraph "Rule Evaluation"
            CE1 --> RE1[Rules Evaluation Service]
            CE2 --> RE2[Rules Evaluation Service]
            CE3 --> RE3[Rules Evaluation Service]
        end

        subgraph "Result Aggregation"
            RE1 --> RA[Result Aggregator]
            RE2 --> RA
            RE3 --> RA
        end

        RA --> BR_RESP[Batch Response]
    end
```

#### **Batch Configuration Options**

| Parameter | Description | Default | Range | Impact |
|-----------|-------------|---------|-------|---------|
| `maxConcurrency` | Concurrent evaluations | 10 | 1-50 | **Throughput** |
| `timeoutSeconds` | Batch timeout | 300 | 30-1800 | **Reliability** |
| `failFast` | Stop on first error | false | true/false | **Error Handling** |
| `returnPartialResults` | Return partial success | true | true/false | **Availability** |
| `sortByPriority` | Process by priority | false | true/false | **Ordering** |

#### **Performance Characteristics**

- **Throughput**: Up to 2000 rules/minute with optimal configuration
- **Latency**: Average 45ms per rule evaluation
- **Concurrency**: Configurable from 1-50 concurrent evaluations
- **Error Resilience**: Partial results with detailed error reporting
- **Resource Efficiency**: Reactive streams with backpressure handling

### 6. Memory Management & Optimization

#### **Efficient Memory Usage**
- **Immutable AST Nodes**: Thread-safe and memory-efficient
- **Context Isolation**: Separate evaluation contexts prevent memory leaks
- **Efficient Collections**: ConcurrentHashMap for thread-safe variable storage
- **Minimal Object Creation**: Reuse of visitor instances and evaluation contexts
- **Cache Size Limits**: Configurable maximum cache sizes to prevent OOM

#### **Garbage Collection Optimization**
- **Short-lived Objects**: Most evaluation objects are short-lived
- **Pool Reuse**: Connection and thread pool reuse
- **Weak References**: Cache entries use appropriate reference types
- **Memory Monitoring**: Built-in memory usage tracking and alerts

### 7. Performance Monitoring & Metrics

#### **Real-time Performance Metrics**

```bash
# Cache Performance
curl http://localhost:8080/api/v1/rules/cache/statistics
{
  "astCache": {
    "hitRate": 85.5,
    "missRate": 14.5,
    "evictionCount": 12,
    "averageLoadTime": "2.3ms"
  },
  "constantsCache": {
    "hitRate": 92.1,
    "missRate": 7.9,
    "size": 245
  }
}

# Batch Processing Statistics
curl http://localhost:8080/api/v1/rules/batch/statistics
{
  "performanceMetrics": {
    "totalBatchesProcessed": 1250,
    "averageProcessingTimeMs": 45.2,
    "peakThroughput": "2000 rules/minute",
    "averageConcurrency": 8.5,
    "cacheHitRate": 85.5
  }
}
```

#### **Key Performance Indicators (KPIs)**

- **Cache Hit Rate**: Target >80% for optimal performance
- **Average Response Time**: Target <50ms per rule evaluation
- **Throughput**: Target >1000 rules/minute for batch operations
- **Error Rate**: Target <1% for production workloads
- **Resource Utilization**: CPU <70%, Memory <80% under normal load

## Audit Trail System

The Firefly Framework Rule Engine includes a comprehensive audit trail system that tracks all rule operations for compliance, monitoring, and debugging purposes.

### 🔍 **Audit Trail Features**

#### **1. Comprehensive Operation Tracking**
- **Rule Definition Operations**: Create, update, delete, activate/deactivate
- **Rule Evaluations**: Direct YAML, stored rule by code, plain YAML
- **Validation Operations**: DSL validation requests and results
- **System Operations**: Constant management, configuration changes

#### **2. Detailed Audit Records**
Each audit trail record captures:
- **Operation Metadata**: Type, timestamp, user, IP address, user agent
- **Request/Response Data**: Full request and response payloads as JSON
- **Performance Metrics**: Execution time, status codes, success/failure
- **Business Context**: Rule codes, entity IDs, correlation IDs
- **Error Information**: Detailed error messages and stack traces
- **Custom Metadata**: Extensible metadata for business-specific information

#### **3. Reactive Audit Integration**
The audit system is fully integrated with the reactive architecture:
- **Non-blocking Operations**: All audit operations use reactive patterns
- **Proper Error Handling**: Audit failures don't affect business operations
- **Backpressure Support**: Handles high-volume audit scenarios gracefully

### 📊 **Audit Trail Architecture**

```mermaid
graph TB
    subgraph "Business Operations"
        RULE_OPS[Rule Operations]
        EVAL_OPS[Evaluation Operations]
        VALID_OPS[Validation Operations]
    end

    subgraph "Audit Integration"
        AUDIT_HELPER[Audit Helper]
        AUDIT_SVC[Audit Trail Service]
    end

    subgraph "Audit Storage"
        AUDIT_REPO[Audit Repository]
        AUDIT_DB[(Audit Database)]
    end

    subgraph "Audit Querying"
        AUDIT_CTRL[Audit Controller]
        FILTER_API[Filtering API]
        REPORT_API[Reporting API]
    end

    RULE_OPS --> AUDIT_HELPER
    EVAL_OPS --> AUDIT_HELPER
    VALID_OPS --> AUDIT_HELPER
    AUDIT_HELPER --> AUDIT_SVC
    AUDIT_SVC --> AUDIT_REPO
    AUDIT_REPO --> AUDIT_DB
    AUDIT_CTRL --> AUDIT_SVC
    FILTER_API --> AUDIT_SVC
    REPORT_API --> AUDIT_SVC

    style AUDIT_HELPER fill:#e8f5e8
    style AUDIT_SVC fill:#e1f5fe
    style AUDIT_DB fill:#fff3e0
```

### 🎯 **Audit Event Types**

| Event Type | Description | Tracked Data |
|------------|-------------|--------------|
| `RULE_DEFINITION_CREATE` | New rule definition created | Rule metadata, YAML content, validation results |
| `RULE_DEFINITION_UPDATE` | Existing rule definition updated | Changes made, version info, validation results |
| `RULE_DEFINITION_DELETE` | Rule definition deleted | Rule metadata, deletion reason |
| `RULE_EVALUATION_DIRECT` | Direct YAML evaluation | Input data, evaluation results, performance metrics |
| `RULE_EVALUATION_BY_CODE` | Stored rule evaluation by code | Rule code, input data, results, rule metadata |
| `RULE_EVALUATION_PLAIN` | Plain YAML evaluation | YAML content, input data, results |
| `DSL_VALIDATION` | YAML DSL validation | Validation results, errors, suggestions |

### 🔧 **Audit Trail API**

#### **Query Audit Trails**
```http
POST /api/v1/audit/trails
Content-Type: application/json

{
  "operationType": "RULE_EVALUATION_DIRECT",
  "userId": "john.doe@company.com",
  "startDate": "2025-01-01T00:00:00Z",
  "endDate": "2025-01-31T23:59:59Z",
  "page": 0,
  "size": 20,
  "sortBy": "createdAt",
  "sortDirection": "DESC"
}
```

#### **Get Audit Trail by ID**
```http
GET /api/v1/audit/trails/{auditId}
```

#### **Get Recent Trails for Entity**
```http
GET /api/v1/audit/trails/entity/{entityId}?limit=10
```

### 📈 **Compliance and Reporting**

#### **Regulatory Compliance**
- **SOX Compliance**: Complete audit trail for financial rule changes
- **GDPR Compliance**: User activity tracking with data protection
- **PCI DSS**: Secure audit logging for payment processing rules
- **Basel III**: Risk management rule audit trails

#### **Business Intelligence**
- **Rule Usage Analytics**: Track which rules are evaluated most frequently
- **Performance Monitoring**: Identify slow-performing rules and optimizations
- **Error Analysis**: Pattern detection in rule failures and validation errors
- **User Activity**: Monitor rule management activities by user and department

#### **Operational Insights**
- **System Health**: Monitor rule engine performance and reliability
- **Capacity Planning**: Analyze usage patterns for scaling decisions
- **Security Monitoring**: Detect unusual access patterns or potential security issues
- **Change Management**: Track rule evolution and impact analysis

### 🔒 **Audit Trail Security**

#### **Data Protection**
- **Sensitive Data Masking**: Automatic masking of PII in audit records
- **Encryption at Rest**: Audit data encrypted in database storage
- **Access Control**: Role-based access to audit trail data
- **Retention Policies**: Configurable retention periods for compliance

#### **Integrity Assurance**
- **Immutable Records**: Audit trails cannot be modified after creation
- **Checksums**: Data integrity verification for audit records
- **Audit of Audits**: Meta-auditing for audit system operations
- **Backup and Recovery**: Secure backup strategies for audit data

## Security Architecture

### 1. Input Validation
- YAML DSL validation during parsing
- DTO validation with Bean Validation
- SQL injection prevention with parameterized queries
- Variable name and comment sanitization in Python code generation to prevent code injection
- Regex pattern caching with LRU eviction (64 entries) to prevent ReDoS via unbounded compilation

### 2. SSRF Protection
The `RestCallServiceImpl` includes comprehensive URL validation before executing any outbound HTTP request:
- **Scheme restriction**: Only `http` and `https` schemes are allowed
- **Private IP blocking**: Loopback, link-local, site-local, and any-local addresses are rejected
- **Cloud metadata blocking**: Requests to `169.254.169.254` (cloud metadata endpoint) are blocked
- **Host validation**: URLs without a valid host are rejected

### 3. Cryptographic Security (Python Runtime)
- **No hardcoded keys**: Encryption functions require explicit key configuration; no fallback to default keys
- **Strong key derivation**: PBKDF2 with random salt (`os.urandom(16)`) and 480,000 iterations
- **No weak algorithms**: MD5 hashing is explicitly rejected; only SHA-256 and SHA-512 are supported
- **Timing-safe comparison**: Hash verification uses `hmac.compare_digest()` to prevent timing attacks

### 4. Safe Code Evaluation
- **No unsafe reflection**: `ExpressionEvaluator.getPropertyValue()` uses public getter methods only (`getX` / `isX`); `field.setAccessible(true)` is never called. A missing getter throws `IllegalArgumentException` with the class + property name rather than silently returning null.
- **Division/modulo by zero**: `ExpressionEvaluator` and `ActionExecutor` throw `ArithmeticException` rather than returning null or silently failing.
- **Short-circuit evaluation**: AND/OR operators use lazy evaluation to prevent unnecessary side effects.
- **Thread-safe code generation**: `PythonCodeGenerator` uses `ThreadLocal` for mutable state to ensure safe concurrent compilation.

### 5. Error Handling (Fail-Loud Contract)

The engine is intentionally non-silent. Errors propagate to the rule's `success=false`
result with a precise diagnostic message rather than being swallowed and producing a
plausible-but-wrong output.

| Source of failure                          | Behaviour                                                                |
| ------------------------------------------ | ------------------------------------------------------------------------ |
| Unknown function name                      | `IllegalArgumentException` -> `success=false`                            |
| Non-numeric string in arithmetic           | `IllegalArgumentException` naming the operand                            |
| Bad regex pattern in `matches`             | `IllegalArgumentException` naming the pattern                            |
| Missing bean property                      | `IllegalArgumentException` naming class + property                       |
| Unknown `is_valid` validation type         | `IllegalArgumentException` listing supported types                       |
| Unknown `dateadd`/`datediff` unit          | `IllegalArgumentException` listing supported units                       |
| Action throws mid-execution                | Rule reports `success=false` with action index + debug string + cause    |
| Condition throws mid-evaluation            | Rule reports `success=false` (does not silently flip to the else branch) |
| `circuit_breaker` action triggered         | Rule reports `success=true` with `circuitBreakerTriggered=true`          |
| Required constant missing in database      | `success=false` listing the missing codes                                |
| REST function HTTP failure                 | Structured error map `{success:false, error:true, message:...}` (intentional chain-friendly contract; rules can branch on `response.success`) |
| Cache read failure                         | Treated as cache miss; logged via `doOnError`                            |

Surrounding mechanisms:
- Graceful degradation for missing **optional** constants (with explicit `defaultValue`).
- Circuit breaker pattern for external dependencies.
- Comprehensive audit trail (every evaluation logged with correlation ID).
- Null-safe audit context extraction (defaults to "system" when no web exchange is available).

### 6. Cache Integrity
- Cache invalidation on all CRUD operations (create, update, delete) for rule definitions
- Reactive cache access uses `subscribeOn(Schedulers.boundedElastic())` to prevent blocking the Netty event loop

### 7. Database Security
- R2DBC with prepared statements
- Connection encryption with SSL
- Database user with minimal privileges

### 8. API Security (Future)
- JWT token authentication
- Rate limiting per client
- Request/response encryption

This architecture provides a solid foundation for a scalable, maintainable, and high-performance rule engine suitable for enterprise applications.
