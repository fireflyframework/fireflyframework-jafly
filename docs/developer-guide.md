# Firefly Framework Rule Engine AST System Deep Dive

This comprehensive guide provides an exhaustive exploration of the Firefly Framework Rule Engine's Abstract Syntax Tree (AST) system architecture. It covers every aspect of how the AST system works, from lexical analysis to rule evaluation, and provides detailed instructions for extending the system with new functionality.

## Table of Contents

- [AST System Overview](#ast-system-overview)
- [Architecture Components](#architecture-components)
- [Lexical Analysis System](#lexical-analysis-system)
- [AST Node Hierarchy](#ast-node-hierarchy)
- [Parsing Pipeline](#parsing-pipeline)
- [Visitor Pattern Implementation](#visitor-pattern-implementation)
- [Evaluation Engine](#evaluation-engine)
- [Validation System](#validation-system)
- [Error Handling](#error-handling)
- [Performance Optimizations](#performance-optimizations)
- [Functions vs Operators: Architecture and Implementation](#functions-vs-operators-architecture-and-implementation)
- [Extending the AST System](#extending-the-ast-system)
- [Best Practices](#best-practices)

## AST System Overview

### 🧠 **What is an Abstract Syntax Tree (AST)?**

An **Abstract Syntax Tree (AST)** is a tree representation of the syntactic structure of source code. Each node in the tree represents a construct occurring in the programming language. The Firefly Framework Rule Engine uses ASTs to represent business rules in a structured, type-safe manner.

**Why AST over String-Based Evaluation?**

Traditional rule engines often use string-based evaluation where rules are parsed and executed as text. This approach has several limitations:

1. **Runtime Errors**: Syntax errors are only discovered during execution
2. **Poor Performance**: Text parsing happens repeatedly during evaluation
3. **Limited Type Safety**: No compile-time type checking
4. **Difficult Optimization**: Hard to optimize string-based expressions
5. **Complex Debugging**: Error locations are hard to track in text

**The AST Approach Solves These Problems:**

The Firefly Framework Rule Engine transforms YAML-based business rules into structured, type-safe AST nodes that provide:

### 🎯 **Core Benefits**

- **Type Safety**: All operations are type-checked at parse time, preventing runtime type errors
- **Performance**: AST nodes are optimized for fast evaluation and can be cached
- **Extensibility**: New operators and functions can be added without modifying existing code
- **Debugging**: Rich source location information enables precise error reporting
- **Validation**: Comprehensive semantic validation occurs before execution
- **Optimization**: AST structure enables compile-time optimizations like constant folding

### 🏗️ **Compiler Design Principles**

The AST system follows established compiler design principles:

1. **Separation of Concerns**: Lexical analysis, parsing, semantic analysis, and execution are separate phases
2. **Visitor Pattern**: Operations on AST nodes are implemented as visitors, enabling extensibility
3. **Type System**: Strong typing prevents many classes of runtime errors
4. **Error Recovery**: The system can continue processing after encountering errors
5. **Optimization**: Multiple passes over the AST enable various optimizations

### 🏗️ **System Architecture**

The AST system follows a multi-layered architecture that separates concerns and enables clean extensibility:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Input Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ YAML Rules  │  │ REST API    │  │ Input Data Map          │  │
│  │ Definition  │  │ Request     │  │ (Runtime Variables)     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Lexical Analysis Layer                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Lexer     │  │ Token       │  │ TokenType Enumeration   │  │
│  │ (Scanner)   │  │ Stream      │  │ (200+ token types)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Parsing Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ YAML Parser │  │ DSL Parser  │  │ Specialized Parsers     │  │
│  │             │  │ (Main)      │  │ • ExpressionParser      │  │
│  │             │  │             │  │ • ConditionParser       │  │
│  │             │  │             │  │ • ActionParser          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     AST Model Layer                             │ 
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Expression  │  │ Condition   │  │ Action Nodes            │  │
│  │ Nodes       │  │ Nodes       │  │ • SetAction             │  │
│  │ • Binary    │  │ • Comparison│  │ • CalculateAction       │  │
│  │ • Unary     │  │ • Logical   │  │ • FunctionCallAction    │  │
│  │ • Literal   │  │ • Expression│  │ • ConditionalAction     │  │
│  │ • Variable  │  │             │  │ • CircuitBreakerAction  │  │
│  │ • Function  │  │             │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Visitor Pattern Layer                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Expression  │  │ Action      │  │ Validation & Analysis   │  │
│  │ Evaluator   │  │ Executor    │  │ • ValidationVisitor     │  │
│  │             │  │             │  │ • VariableCollector     │  │
│  │             │  │             │  │ • TypeChecker           │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Execution Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ Evaluation  │  │ Evaluation  │  │ Support Systems         │  │
│  │ Engine      │  │ Context     │  │ • AST Cache Service     │  │
│  │             │  │ • Variables │  │ • Error Handling        │  │
│  │             │  │ • Constants │  │ • Audit Trail           │  │
│  │             │  │ • State     │  │ • Performance Metrics   │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 🔄 **Processing Flow: From YAML to Execution**

Understanding the complete processing pipeline is crucial for working with the AST system. Here's how a YAML rule transforms into executable code:

#### **Phase 1: YAML Parsing**
```yaml
# Input: YAML Rule Definition
name: "Credit Assessment"
inputs: [creditScore, income]
when:
  - creditScore greater_than MIN_CREDIT_SCORE
then:
  - set approved to true
```

The system first parses this YAML into a Java Map structure using Jackson's YAML parser. This is a standard structural parsing step that doesn't understand the DSL semantics yet.

#### **Phase 2: Lexical Analysis (Tokenization)**
The DSL expressions within the YAML (like `creditScore greater_than MIN_CREDIT_SCORE`) are broken down into tokens:

```
[IDENTIFIER: "creditScore"] [GREATER_THAN: "greater_than"] [IDENTIFIER: "MIN_CREDIT_SCORE"]
```

**Why Tokenization Matters**: This step converts raw text into meaningful symbols that the parser can understand. Each token carries type information and source location for error reporting.

#### **Phase 3: Syntax Parsing (AST Construction)**
Tokens are assembled into AST nodes using **recursive descent parsing**:

```
ComparisonCondition
├── left: VariableExpression("creditScore")
├── operator: GREATER_THAN
└── right: VariableExpression("MIN_CREDIT_SCORE")
```

**Recursive Descent Parsing**: This is a top-down parsing technique where each grammar rule is implemented as a method. The parser starts with high-level constructs and recursively breaks them down into smaller components.

#### **Phase 4: Semantic Validation**
The AST is validated for semantic correctness:
- Type compatibility (can't compare string to number)
- Variable existence
- Function signature validation
- Operator compatibility

#### **Phase 5: Variable Collection**
The system traverses the AST to collect all variable references, enabling efficient constant loading from the database.

#### **Phase 6: AST Caching (Optional)**
For performance, parsed ASTs can be cached using a hash of the original YAML content.

#### **Phase 7: Evaluation**
The AST is executed using the **Visitor Pattern** with an evaluation context containing variables and state.

#### **Phase 8: Result Generation**
Outputs are collected and formatted into the final evaluation result.

## Architecture Components

### 📦 **Core Package Structure**

The AST system is organized into logical packages that separate concerns and enable clean extensibility:

```
org.fireflyframework.rules.core.dsl.ast/
├── lexer/                    # Lexical analysis components
│   ├── Lexer.java           # Main lexer implementation
│   ├── Token.java           # Token representation
│   ├── TokenType.java       # Token type enumeration (200+ types)
│   └── LexerException.java  # Lexer-specific exceptions
├── parser/                   # Parsing components
│   ├── DSLParser.java       # Main DSL parser coordinator
│   ├── ExpressionParser.java # Expression parsing (arithmetic, logical)
│   ├── ConditionParser.java # Condition parsing (comparisons, validations)
│   ├── ActionParser.java    # Action parsing (assignments, calculations)
│   ├── ASTRulesDSLParser.java # YAML to AST converter
│   └── BaseParser.java      # Common parsing utilities
├── expression/              # Expression AST nodes
│   ├── Expression.java      # Base expression class
│   ├── BinaryExpression.java # Binary operations (+, -, *, /, %, **, comparisons)
│   ├── UnaryExpression.java # Unary operations (-, !, validation ops)
│   ├── LiteralExpression.java # Literal values (numbers, strings, booleans, arrays)
│   ├── VariableExpression.java # Variable references with property/index access
│   ├── FunctionCallExpression.java # Function calls with parameters
│   ├── JsonPathExpression.java # JSON path queries (visitor support; emitted by builders, not the parser)
│   ├── RestCallExpression.java # REST API calls (visitor support; emitted by builders, not the parser)
│   ├── BinaryOperator.java  # Binary operator enumeration
│   ├── UnaryOperator.java   # Unary operator enumeration
│   └── ExpressionType.java  # Expression type enumeration
├── condition/               # Condition AST nodes
│   ├── Condition.java       # Base condition class
│   ├── ComparisonCondition.java # Comparison operations (>, <, ==, between, in_list, etc.)
│   ├── LogicalCondition.java # Logical operations (AND, OR, NOT)
│   ├── ExpressionCondition.java # Expression-based conditions
│   └── ComparisonOperator.java # Comparison operator enumeration
├── action/                  # Action AST nodes
│   ├── Action.java          # Base action class
│   ├── SetAction.java       # Variable assignment (`set var to value`)
│   ├── CalculateAction.java # Pure-math arithmetic (`calculate var as expr`)
│   ├── RunAction.java       # Function-call assignment (`run var as fn(...)`)
│   ├── FunctionCallAction.java # Standalone function execution (`call fn with [...]`)
│   ├── ConditionalAction.java # Inline if-then-else as an action
│   ├── ArithmeticAction.java # `add`/`subtract`/`multiply`/`divide` X to/from/by var
│   ├── ListAction.java      # List operations (append, prepend, remove)
│   ├── CircuitBreakerAction.java # Early termination with a structured message
│   ├── ForEachAction.java   # `forEach item in items: ...`
│   ├── WhileAction.java     # `while condition: ...`
│   └── DoWhileAction.java   # `do: ... while condition`
├── function/                # Extension point for user-defined functions
│   ├── RuleFunction.java    # Functional interface: `Object apply(Object[] args)`
│   └── CustomFunctionRegistry.java # @Component holding registered functions
├── visitor/                 # Visitor pattern implementations
│   ├── EvaluationContext.java # Execution context and state
│   ├── ExpressionEvaluator.java # Expression evaluation visitor
│   ├── ActionExecutor.java  # Action execution visitor
│   ├── ValidationVisitor.java # AST validation visitor
│   ├── VariableReferenceCollector.java # Variable extraction visitor
│   └── ValidationError.java # Validation error representation
├── model/                   # AST model classes
│   ├── ASTRulesDSL.java     # Complete rule model
│   └── SourceLocation.java  # Source position tracking
├── evaluation/              # Evaluation engine
│   └── ASTRulesEvaluationEngine.java # Main evaluation orchestrator
├── exception/               # Exception classes
│   ├── ASTException.java    # General AST exceptions
│   ├── LexerException.java  # Lexer exceptions
│   └── ParserException.java # Parser exceptions
├── ASTNode.java            # Base AST node class
└── ASTVisitor.java         # Visitor interface definition
```

### 🎯 **Key Design Principles**

1. **Separation of Concerns**: Each component has a single, well-defined responsibility
2. **Visitor Pattern**: Operations are separated from AST structure for extensibility
3. **Type Safety**: Strong typing throughout the system prevents runtime errors
4. **Immutability**: AST nodes are immutable after construction for thread safety
5. **Error Recovery**: Comprehensive error handling with detailed diagnostics
6. **Performance**: Optimized for high-throughput rule evaluation scenarios
7. **Extensibility**: New operators, functions, and node types can be added easily
8. **Debugging Support**: Rich source location tracking for error reporting

## Lexical Analysis System

### 🧠 **Understanding Lexical Analysis**

**Lexical Analysis** (also called tokenization or scanning) is the first phase of any compiler or interpreter. It converts a sequence of characters into a sequence of tokens - meaningful symbols that the parser can understand.

**Why is Lexical Analysis Important?**

1. **Abstraction**: Converts raw text into meaningful symbols
2. **Error Detection**: Catches invalid characters early
3. **Simplification**: Parser works with tokens instead of individual characters
4. **Performance**: Tokenization can be optimized independently
5. **Source Tracking**: Each token knows its location for error reporting

### 🔍 **Finite State Machine Approach**

The Firefly Framework Rule Engine uses a **Finite State Machine (FSM)** approach for lexical analysis. This is a proven technique used in most programming language compilers.

**What is a Finite State Machine?**

An FSM is a computational model with:
- A finite number of states
- Transitions between states based on input
- One start state and one or more accepting states

**Example: Recognizing Numbers**
```
State 1 (Start) --[digit]--> State 2 (Integer)
State 2 (Integer) --[digit]--> State 2 (Integer)
State 2 (Integer) --[.]--> State 3 (Decimal)
State 3 (Decimal) --[digit]--> State 4 (Float)
State 4 (Float) --[digit]--> State 4 (Float)
```

### 🏗️ **Lexer Architecture**

The `Lexer` class implements this FSM approach:

```java
public class Lexer {
    private final String source;           // Input text to tokenize
    private final List<Token> tokens;      // Output token stream
    private int start = 0;                 // Start of current lexeme
    private int current = 0;               // Current character position
    private int line = 1;                  // Current line number (for error reporting)
    private int column = 1;                // Current column number (for error reporting)

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;               // Mark start of new token
            scanToken();                   // Process one token
        }
        tokens.add(Token.of(TokenType.EOF, "", getCurrentLocation()));
        return tokens;
    }
}
```

**Key Concepts:**

1. **Lexeme**: The actual text that forms a token (e.g., "123", "greater_than")
2. **Token**: The categorized lexeme with type information
3. **Lookahead**: Examining future characters without consuming them
4. **Backtracking**: Returning to a previous position when a path fails

### 🏷️ **Token Classification System**

**What is a Token?**

A token is a categorized piece of text that represents a meaningful unit in the language. Each token has:
- **Type**: What kind of token it is (NUMBER, IDENTIFIER, OPERATOR, etc.)
- **Lexeme**: The actual text that was matched
- **Location**: Where it appears in the source (line, column)
- **Value**: The interpreted value (for literals)

**Token Categories in Firefly DSL:**

The `TokenType` enum organizes over 200 token types into logical categories:

```java
public enum TokenType {
    // 1. LITERALS - Represent constant values
    NUMBER("number", TokenCategory.LITERAL),        // 123, 45.67, 1.23e-4
    STRING("string", TokenCategory.LITERAL),        // "hello", 'world'
    BOOLEAN("boolean", TokenCategory.LITERAL),      // true, false
    NULL("null", TokenCategory.LITERAL),            // null

    // 2. IDENTIFIERS - Variable and function names
    IDENTIFIER("identifier", TokenCategory.IDENTIFIER), // creditScore, calculateRisk

    // 3. OPERATORS - Operations between values
    // Arithmetic operators
    PLUS("+", TokenCategory.OPERATOR),
    MINUS("-", TokenCategory.OPERATOR),
    MULTIPLY("*", TokenCategory.OPERATOR),
    DIVIDE("/", TokenCategory.OPERATOR),

    // Comparison operators
    EQUALS("==", TokenCategory.OPERATOR),
    NOT_EQUALS("!=", TokenCategory.OPERATOR),
    GREATER_THAN(">", TokenCategory.OPERATOR),
    LESS_THAN("<", TokenCategory.OPERATOR),

    // 4. KEYWORDS - Reserved words with special meaning
    SET("set", TokenCategory.KEYWORD),
    CALCULATE("calculate", TokenCategory.KEYWORD),
    IF("if", TokenCategory.KEYWORD),
    THEN("then", TokenCategory.KEYWORD),

    // 5. PUNCTUATION - Structural elements
    LPAREN("(", TokenCategory.PUNCTUATION),
    RPAREN(")", TokenCategory.PUNCTUATION),
    COMMA(",", TokenCategory.PUNCTUATION),
}
```

**Why Categorize Tokens?**

1. **Parser Simplification**: Parser can make decisions based on token categories
2. **Error Messages**: Better error messages ("expected operator, got literal")
3. **Syntax Highlighting**: IDEs can color-code based on categories
4. **Validation**: Can check for valid token sequences

#### **Complete Token Categories**

1. **Literals**: `NUMBER`, `STRING`, `BOOLEAN`, `NULL`
2. **Identifiers**: `IDENTIFIER` (variable names, function names)
3. **Operators**:
   - Arithmetic: `PLUS`, `MINUS`, `MULTIPLY`, `DIVIDE`, `MODULO`, `POWER`
   - Comparison: `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `LESS_THAN`, etc.
   - Logical: `AND`, `OR`, `NOT`
   - Validation: `IS_POSITIVE`, `IS_EMAIL`, `IS_PHONE`, `IS_CREDIT_SCORE`, etc.
4. **Keywords**: `SET`, `CALCULATE`, `IF`, `THEN`, `ELSE`, `WHEN`, `CALL`, etc.
5. **Punctuation**: `LPAREN`, `RPAREN`, `COMMA`, `DOT`, `COLON`, etc.
6. **Special**: `EOF`, `NEWLINE`, `WHITESPACE`

### 🔧 **Token Recognition Strategies**

The lexer employs different strategies for recognizing different types of tokens. Understanding these strategies helps when adding new token types.

#### **1. Single Character Tokens (Simplest Case)**

For punctuation and simple operators, recognition is straightforward:

```java
private void scanToken() {
    char c = advance();

    switch (c) {
        case '(' -> addToken(TokenType.LPAREN);
        case ')' -> addToken(TokenType.RPAREN);
        case '+' -> addToken(TokenType.PLUS);
        case '-' -> addToken(TokenType.MINUS);
        case ',' -> addToken(TokenType.COMMA);
    }
}
```

**Concept**: Direct character-to-token mapping with no ambiguity.

#### **2. Multi-Character Operators (Lookahead Required)**

Some operators require examining the next character to determine the correct token:

```java
case '=' -> {
    if (match('=')) {
        addToken(TokenType.EQUALS);    // == (equality)
    } else {
        addToken(TokenType.ASSIGN);    // = (assignment)
    }
}
case '!' -> {
    if (match('=')) {
        addToken(TokenType.NOT_EQUALS); // != (not equal)
    } else {
        addToken(TokenType.NOT);        // ! (logical not)
    }
}
```

**Concept**: **Lookahead** - examining future characters without consuming them to make decisions.

#### **3. Keywords vs Identifiers (Context-Sensitive Recognition)**

This is the most complex recognition strategy. The lexer must:

1. **Scan the entire word**: Read all alphanumeric characters
2. **Check keyword tables**: See if it's a reserved word
3. **Handle multi-word operators**: Like "greater_than" or "age_at_least"
4. **Default to identifier**: If not a keyword, treat as variable/function name

```java
private void identifier() {
    // Step 1: Scan entire alphanumeric sequence
    while (isAlphaNumeric(peek())) {
        advance();
    }

    String text = source.substring(start, current);

    // Step 2: Check for multi-word tokens (age_at_least, is_positive, etc.)
    if (isMultiWordToken(text)) {
        scanMultiWordToken(text);  // Special handling for complex operators
        return;
    }

    // Step 3: Check keyword tables
    TokenType type = TokenType.getKeyword(text);      // Reserved words (set, if, then)
    if (type == null) {
        type = TokenType.getOperator(text);           // Operator words (and, or, not)
    }

    // Step 4: Default to identifier
    if (type == null) {
        if ("true".equals(text) || "false".equals(text)) {
            addToken(TokenType.BOOLEAN, Boolean.parseBoolean(text));
        } else if ("null".equals(text)) {
            addToken(TokenType.NULL, null);
        } else {
            type = TokenType.IDENTIFIER;              // Variable or function name
            addToken(type);
        }
    } else {
        addToken(type);
    }
}
```

**Key Concepts:**

- **Maximal Munch**: Always consume the longest possible token
- **Keyword Priority**: Keywords take precedence over identifiers
- **Context Sensitivity**: The same text might be different tokens in different contexts

#### **4. Multi-Word Tokens (Complex Operator Recognition)**

The DSL supports complex operators like `age_at_least`, `is_credit_score`, `not_between`. These require special handling because they span multiple words.

**The Challenge**: When the lexer sees "age", it doesn't know if it's:
- A variable named "age"
- The start of "age_at_least" operator

**The Solution**: **Lookahead with Backtracking**

```java
private boolean isMultiWordToken(String firstWord) {
    return switch (firstWord) {
        case "age" -> checkForAgeOperators();        // age_at_least, age_less_than
        case "is" -> checkForValidationOperators();  // is_positive, is_email, etc.
        case "not" -> checkForNegationOperators();   // not_between, not_in
        case "greater" -> checkForComparison();      // greater_than
        case "less" -> checkForComparison();         // less_than
        default -> false;
    };
}

private boolean checkForAgeOperators() {
    // Look ahead to see if we have "_at_least" or "_less_than"
    int savedPosition = current;

    if (match('_') && matchWord("at") && match('_') && matchWord("least")) {
        addToken(TokenType.AGE_AT_LEAST);
        return true;
    }

    // Backtrack if pattern doesn't match
    current = savedPosition;

    if (match('_') && matchWord("less") && match('_') && matchWord("than")) {
        addToken(TokenType.AGE_LESS_THAN);
        return true;
    }

    // Not a multi-word token, backtrack completely
    current = savedPosition;
    return false;
}
```

**Key Concepts:**
- **Lookahead**: Examining future characters
- **Backtracking**: Returning to previous position if pattern fails
- **Greedy Matching**: Always try to match the longest possible token

### 📍 **Source Location Tracking**

**Why Track Source Locations?**

When a rule fails during evaluation, developers need to know exactly where in the original YAML the problem occurred. This is crucial for debugging complex rules.

```java
public class SourceLocation {
    private final int line;           // Line number (1-based)
    private final int column;         // Column number (1-based)
    private final int startPosition;  // Absolute character position
    private final int endPosition;    // End of token position

    @Override
    public String toString() {
        return String.format("line %d, column %d", line, column);
    }
}
```

**How Location Tracking Works:**

1. **Character Counting**: Lexer tracks current line and column
2. **Token Association**: Each token stores its source location
3. **AST Propagation**: Location information flows up to AST nodes
4. **Error Reporting**: Errors include precise location information

**Example Error Message:**
```
Type mismatch: Cannot compare string to number at line 5, column 12
  - creditScore greater_than "high"
                            ^
```

### 🎯 **Lexer Features**

1. **Error Recovery**: Continues parsing after encountering invalid characters
2. **Unicode Support**: Handles UTF-8 encoded input correctly
3. **String Literals**: Supports both double and single quoted strings with escape sequences
4. **Number Parsing**: Handles integers, decimals, and scientific notation
5. **Comment Support**: Ignores comments (though not used in current DSL)
6. **Whitespace Handling**: Properly handles spaces, tabs, and newlines
7. **Multi-Word Operators**: Recognizes complex operators like `age_at_least 18`

## AST Node Hierarchy

### 🧠 **Understanding AST Nodes**

**What is an AST Node?**

An AST node represents a single construct in the programming language. In the Firefly Framework Rule Engine, every piece of a rule (expressions, conditions, actions) is represented by a specific type of AST node.

**Why Use a Hierarchy?**

The node hierarchy provides several benefits:

1. **Type Safety**: Each node type has specific properties and behaviors
2. **Polymorphism**: Different node types can be treated uniformly through base classes
3. **Extensibility**: New node types can be added without modifying existing code
4. **Visitor Pattern Support**: Operations can be defined separately from node structure

### 🌳 **Base AST Node Design**

All AST nodes inherit from the abstract `ASTNode` class, which establishes the fundamental contract:

```java
public abstract class ASTNode {
    private SourceLocation location;    // Where this node appears in source

    /**
     * Accept method for the visitor pattern
     * This is the key to extensibility - new operations can be added
     * by creating new visitors without modifying node classes
     */
    public abstract <T> T accept(ASTVisitor<T> visitor);

    /**
     * Get a string representation for debugging
     * Essential for troubleshooting and development
     */
    public abstract String toDebugString();

    /**
     * Get the type name of this AST node
     * Useful for reflection and debugging
     */
    public String getNodeType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Check if this node has location information
     */
    public boolean hasLocation() {
        return location != null;
    }
}
```

**Key Design Decisions:**

1. **Abstract Base Class**: Forces all nodes to implement required methods
2. **Location Tracking**: Every node knows where it came from in the source
3. **Visitor Pattern**: The `accept` method enables the visitor pattern
4. **Debugging Support**: Every node can describe itself for debugging
5. **Immutability**: Nodes are immutable after construction (thread-safe)

### 📊 **Complete Node Hierarchy**

```
ASTNode (abstract base)
├── Expression (abstract)
│   ├── LiteralExpression           # numbers, strings, booleans, null, array literals
│   ├── VariableExpression          # variable refs with optional property path / index access
│   ├── BinaryExpression            # +, -, *, /, %, **, comparisons, and/or, contains, starts_with, etc.
│   ├── UnaryExpression             # -, +, NOT, EXISTS, IS_NULL, IS_EMAIL, IS_POSITIVE, etc.
│   ├── FunctionCallExpression      # math, string, date, list, financial, validation, REST, JSON funcs
│   ├── JsonPathExpression          # visitor-supported; emitted by builders, not the lexer/parser
│   └── RestCallExpression          # visitor-supported; emitted by builders, not the lexer/parser
├── Condition (abstract)
│   ├── ComparisonCondition         # `>=`, `at_least`, `between ... and ...`, `in_list [...]`, `is_email`, etc.
│   ├── LogicalCondition            # AND / OR / NOT composition
│   └── ExpressionCondition         # wraps any boolean-valued Expression
└── Action (abstract)
    ├── SetAction                   # `set var to value`
    ├── CalculateAction             # `calculate var as <pure-math-expr>`
    ├── RunAction                   # `run var as <function-or-rest-expr>`
    ├── FunctionCallAction          # `call fn with [args]`
    ├── ConditionalAction           # `if cond then actions [else actions]`
    ├── ArithmeticAction            # `add X to var`, `subtract X from var`, `multiply var by X`, `divide var by X`
    ├── ListAction                  # `append X to list`, `prepend X to list`, `remove X from list`
    ├── ForEachAction               # `forEach item[, index] in items: actions`
    ├── WhileAction                 # `while cond: actions`
    ├── DoWhileAction               # `do: actions while cond`
    └── CircuitBreakerAction        # `circuit_breaker "MESSAGE"` -- early termination
```

> **Note:** The DSL was simplified in 2026-05 to remove orphan AST classes that were never produced by the parser. The compound-assignment family (`+=`, `-=`, etc.) and the multi-operand `ArithmeticExpression` n-ary form are no longer present. Use `add`/`subtract`/`multiply`/`divide` arithmetic actions or `calculate`/`run` with `+`/`-`/`*`/`/` for the same outcomes.

### 🔢 **Expression Nodes: Representing Computable Values**

**What is an Expression?**

An expression is any construct that can be evaluated to produce a value. Examples:
- `creditScore` (variable reference)
- `42` (literal value)
- `income + bonus` (arithmetic operation)
- `calculateRisk(creditScore, income)` (function call)

**Expression Base Class Design:**

```java
public abstract class Expression extends ASTNode {

    public Expression(SourceLocation location) {
        super(location);
    }

    /**
     * Get the expected result type of this expression
     * This enables compile-time type checking
     */
    public abstract ExpressionType getExpressionType();

    /**
     * Check if this expression is a constant value
     * Enables compile-time optimizations like constant folding
     */
    public boolean isConstant() {
        return false;  // Most expressions are not constant
    }

    /**
     * Check if this expression references any variables
     * Used for dependency analysis and constant loading
     */
    public abstract boolean hasVariableReferences();
}
```

**Key Concepts:**

1. **Type System**: Every expression has a known type (NUMBER, STRING, BOOLEAN, etc.)
2. **Constant Folding**: Constant expressions can be evaluated at parse time
3. **Dependency Analysis**: Knowing variable references helps optimize evaluation
4. **Polymorphism**: All expressions can be treated uniformly despite different implementations

**Expression Type System:**

```java
public enum ExpressionType {
    NUMBER,     // 42, 3.14, -17
    STRING,     // "hello", 'world'
    BOOLEAN,    // true, false
    LIST,       // [1, 2, 3]
    OBJECT,     // Complex objects
    UNKNOWN     // Type cannot be determined
}
```

**Why Type Information Matters:**

1. **Error Prevention**: Catch type mismatches at parse time
2. **Optimization**: Type-specific optimizations
3. **Code Generation**: Different types may need different evaluation strategies
4. **IDE Support**: Better autocomplete and error highlighting

#### **Binary Expressions: Two-Operand Operations**

**What is a Binary Expression?**

A binary expression represents an operation between two operands. The structure is always:
`left_operand operator right_operand`

Examples:
- `income + bonus` (arithmetic)
- `creditScore > 700` (comparison)
- `isActive AND isVerified` (logical)

**Binary Expression Design:**

```java
public class BinaryExpression extends Expression {
    private final Expression left;        // Left operand (can be any expression)
    private final BinaryOperator operator; // The operation to perform
    private final Expression right;       // Right operand (can be any expression)

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitBinaryExpression(this);
    }

    @Override
    public ExpressionType getExpressionType() {
        // The result type is determined by the operator
        return operator.getResultType();
    }

    @Override
    public boolean hasVariableReferences() {
        // Has references if either operand has references
        return left.hasVariableReferences() || right.hasVariableReferences();
    }

    @Override
    public boolean isConstant() {
        // Constant only if both operands are constant
        return left.isConstant() && right.isConstant();
    }
}
```

**Key Design Principles:**

1. **Recursive Structure**: Operands are expressions themselves, enabling complex nesting
2. **Operator Determines Type**: The operator defines what type the result will be
3. **Compositional**: Binary expressions can be combined to form larger expressions
4. **Immutable**: Once created, the expression cannot be modified

**Example AST Structure:**

For the expression `(income + bonus) > threshold`:

```
BinaryExpression (>)
├── left: BinaryExpression (+)
│   ├── left: VariableExpression("income")
│   ├── operator: ADD
│   └── right: VariableExpression("bonus")
├── operator: GREATER_THAN
└── right: VariableExpression("threshold")
```

#### **Binary Operators: The Heart of Expression Evaluation**

**What is Operator Precedence?**

Operator precedence determines the order of operations in complex expressions. Without precedence rules, `2 + 3 * 4` could be interpreted as either:
- `(2 + 3) * 4 = 20` (left-to-right)
- `2 + (3 * 4) = 14` (multiplication first)

Mathematical convention says multiplication has higher precedence, so the second interpretation is correct.

**Binary Operator Design:**

```java
public enum BinaryOperator {
    // Arithmetic operators (highest precedence: 6-8)
    POWER("^", ExpressionType.NUMBER, 8),        // 2^3 = 8
    MULTIPLY("*", ExpressionType.NUMBER, 7),     // 3*4 = 12
    DIVIDE("/", ExpressionType.NUMBER, 7),       // 8/2 = 4
    MODULO("%", ExpressionType.NUMBER, 7),       // 7%3 = 1
    ADD("+", ExpressionType.NUMBER, 6),          // 2+3 = 5
    SUBTRACT("-", ExpressionType.NUMBER, 6),     // 5-2 = 3

    // Comparison operators (medium precedence: 4-5)
    LESS_THAN("<", ExpressionType.BOOLEAN, 5),
    GREATER_THAN(">", ExpressionType.BOOLEAN, 5),
    LESS_THAN_OR_EQUAL("<=", ExpressionType.BOOLEAN, 5),
    GREATER_THAN_OR_EQUAL(">=", ExpressionType.BOOLEAN, 5),
    EQUALS("==", ExpressionType.BOOLEAN, 4),
    NOT_EQUALS("!=", ExpressionType.BOOLEAN, 4),

    // Logical operators (lowest precedence: 1-2)
    AND("and", ExpressionType.BOOLEAN, 2),       // Both must be true
    OR("or", ExpressionType.BOOLEAN, 1),         // Either can be true

    // Special operators (context-dependent precedence)
    BETWEEN("between", ExpressionType.BOOLEAN, 4),     // value between min and max
    IN("in", ExpressionType.BOOLEAN, 4),               // value in [list]
    CONTAINS("contains", ExpressionType.BOOLEAN, 4),   // string contains substring
    STARTS_WITH("starts_with", ExpressionType.BOOLEAN, 4),
    ENDS_WITH("ends_with", ExpressionType.BOOLEAN, 4),

    // Domain-specific operators
    AGE_AT_LEAST("age_at_least", ExpressionType.BOOLEAN, 4),
    AGE_LESS_THAN("age_less_than", ExpressionType.BOOLEAN, 4);

    private final String symbol;           // How it appears in source code
    private final ExpressionType resultType; // What type it produces
    private final int precedence;          // Order of operations (higher = first)
}
```

**Precedence Rules in Action:**

Expression: `income + bonus * 0.1 > threshold`

Parsing order (by precedence):
1. `bonus * 0.1` (precedence 7)
2. `income + (result)` (precedence 6)
3. `(result) > threshold` (precedence 5)

**Why This Design Works:**

1. **Mathematical Intuition**: Follows standard mathematical precedence
2. **Unambiguous Parsing**: Clear rules for complex expressions
3. **Extensible**: New operators can be added with appropriate precedence
4. **Type Safety**: Each operator declares its result type

#### **Unary Expressions**

Unary expressions handle operations on a single operand, including validation operators:

````java
@Data
@EqualsAndHashCode(callSuper = true)
public class UnaryExpression extends Expression {
    private final UnaryOperator operator;
    private final Expression operand;

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitUnaryExpression(this);
    }

    @Override
    public ExpressionType getExpressionType() {
        return operator.getResultType();
    }
}
````

#### **Unary Operators**

The `UnaryOperator` enum includes arithmetic, logical, and validation operators:

````java
public enum UnaryOperator {
    // Arithmetic operators
    NEGATE("-", ExpressionType.NUMBER),
    POSITIVE("+", ExpressionType.NUMBER),

    // Logical operators
    NOT("not", ExpressionType.BOOLEAN),

    // Existence operators
    IS_NULL("is_null", ExpressionType.BOOLEAN),
    IS_NOT_NULL("is_not_null", ExpressionType.BOOLEAN),

    // Type checking operators
    IS_NUMBER("is_number", ExpressionType.BOOLEAN),
    IS_STRING("is_string", ExpressionType.BOOLEAN),
    IS_BOOLEAN("is_boolean", ExpressionType.BOOLEAN),
    IS_LIST("is_list", ExpressionType.BOOLEAN),

    // Validation operators
    IS_POSITIVE("is_positive", ExpressionType.BOOLEAN),
    IS_NEGATIVE("is_negative", ExpressionType.BOOLEAN),
    IS_ZERO("is_zero", ExpressionType.BOOLEAN),
    IS_EMPTY("is_empty", ExpressionType.BOOLEAN),
    IS_NOT_EMPTY("is_not_empty", ExpressionType.BOOLEAN),
    IS_NUMERIC("is_numeric", ExpressionType.BOOLEAN),
    IS_EMAIL("is_email", ExpressionType.BOOLEAN),
    IS_PHONE("is_phone", ExpressionType.BOOLEAN),
    IS_DATE("is_date", ExpressionType.BOOLEAN),
    IS_PERCENTAGE("is_percentage", ExpressionType.BOOLEAN),
    IS_CURRENCY("is_currency", ExpressionType.BOOLEAN),
    IS_CREDIT_SCORE("is_credit_score", ExpressionType.BOOLEAN),
    IS_SSN("is_ssn", ExpressionType.BOOLEAN),
    IS_ACCOUNT_NUMBER("is_account_number", ExpressionType.BOOLEAN),
    IS_ROUTING_NUMBER("is_routing_number", ExpressionType.BOOLEAN),
    IS_BUSINESS_DAY("is_business_day", ExpressionType.BOOLEAN),
    IS_WEEKEND("is_weekend", ExpressionType.BOOLEAN);
}
````

### 🔍 **Condition Nodes**

Condition nodes represent boolean expressions used in `when` clauses and conditional actions:

````java
public abstract class Condition extends ASTNode {

    public Condition(SourceLocation location) {
        super(location);
    }

    /**
     * Check if this condition references any variables
     */
    public abstract boolean hasVariableReferences();

    /**
     * Get the complexity score of this condition
     */
    public abstract int getComplexityScore();
}
````

#### **Comparison Conditions**

Handle comparisons between expressions using various operators:

````java
@Data
@EqualsAndHashCode(callSuper = true)
public class ComparisonCondition extends Condition {
    private final Expression left;
    private final ComparisonOperator operator;
    private final Expression right;  // Optional for unary operators
    private final Expression third;  // For BETWEEN operator

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitComparisonCondition(this);
    }

    @Override
    public boolean hasVariableReferences() {
        boolean hasRefs = left.hasVariableReferences();
        if (right != null) hasRefs |= right.hasVariableReferences();
        if (third != null) hasRefs |= third.hasVariableReferences();
        return hasRefs;
    }
}
````

#### **Comparison Operators**

The `ComparisonOperator` enum defines all comparison and validation operations:

````java
public enum ComparisonOperator {
    // Basic comparison operators
    EQUALS("equals", "=="),
    NOT_EQUALS("not_equals", "!="),
    LESS_THAN("less_than", "<"),
    GREATER_THAN("greater_than", ">"),
    LESS_THAN_OR_EQUAL("less_than_or_equal", "<="),
    GREATER_THAN_OR_EQUAL("greater_than_or_equal", ">="),

    // String operators
    CONTAINS("contains"),
    NOT_CONTAINS("not_contains"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    MATCHES("matches"),
    NOT_MATCHES("not_matches"),

    // Special operators
    BETWEEN("between"),
    NOT_BETWEEN("not_between"),
    IN("in"),
    NOT_IN("not_in"),

    // Validation operators (same as UnaryOperator but in condition context)
    IS_POSITIVE("is_positive"),
    IS_NEGATIVE("is_negative"),
    IS_EMAIL("is_email"),
    IS_PHONE("is_phone"),
    IS_CREDIT_SCORE("is_credit_score"),
    // ... all validation operators
}
````

### ⚡ **Action Nodes**

Action nodes represent operations that modify the evaluation context or perform side effects:

````java
public abstract class Action extends ASTNode {

    public Action(SourceLocation location) {
        super(location);
    }

    /**
     * Check if this action references any variables
     */
    public abstract boolean hasVariableReferences();

    /**
     * Get the variables that this action modifies
     */
    public abstract Set<String> getModifiedVariables();
}
````

#### **Set Actions**

The most common action type for variable assignment:

````java
@Data
@EqualsAndHashCode(callSuper = true)
public class SetAction extends Action {
    private final String variableName;
    private final Expression value;

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitSetAction(this);
    }

    @Override
    public boolean hasVariableReferences() {
        return value.hasVariableReferences();
    }

    @Override
    public Set<String> getModifiedVariables() {
        return Set.of(variableName);
    }
}
````

#### **Calculate Actions**

For arithmetic calculations with result assignment:

````java
@Data
@EqualsAndHashCode(callSuper = true)
public class CalculateAction extends Action {
    private final String resultVariable;
    private final Expression expression;

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCalculateAction(this);
    }

    @Override
    public String toDebugString() {
        return String.format("CalculateAction(result=%s, expr=%s)",
                           resultVariable, expression.toDebugString());
    }
}
````

## Parsing Pipeline

The parsing pipeline transforms tokens into AST nodes through a series of specialized parsers that work together to handle the complete DSL grammar.

### 🔄 **Parser Architecture**

The parsing system uses a **recursive descent parser** architecture with separate parsers for different language constructs:

```
DSLParser (Main Coordinator)
├── ExpressionParser (Arithmetic, logical, function calls)
├── ConditionParser (Comparisons, validations, logical conditions)
├── ActionParser (Assignments, calculations, function calls)
└── BaseParser (Common parsing utilities)
```

### 🎯 **Main DSL Parser**

The `DSLParser` class coordinates the parsing process and delegates to specialized parsers:

````java
@Component
public class DSLParser extends BaseParser {

    public Expression parseExpression(String expressionStr) {
        if (expressionStr == null || expressionStr.trim().isEmpty()) {
            throw new ParserException("Expression cannot be empty");
        }

        List<Token> tokens = lexer.tokenize(expressionStr);
        ExpressionParser parser = new ExpressionParser(tokens);
        return parser.parseExpression();
    }

    public Condition parseCondition(String conditionStr) {
        List<Token> tokens = lexer.tokenize(conditionStr);
        ConditionParser parser = new ConditionParser(tokens);
        return parser.parseCondition();
    }

    public Action parseAction(String actionStr) {
        List<Token> tokens = lexer.tokenize(actionStr);
        ActionParser parser = new ActionParser(tokens);
        return parser.parseAction();
    }
}
````

### 📊 **Expression Parser**

The `ExpressionParser` handles arithmetic, logical, and function call expressions using operator precedence parsing:

````java
public class ExpressionParser extends BaseParser {

    public Expression parseExpression() {
        return logicalOr();
    }

    private Expression logicalOr() {
        Expression expr = logicalAnd();

        while (match(TokenType.OR)) {
            BinaryOperator operator = BinaryOperator.OR;
            Expression right = logicalAnd();
            expr = new BinaryExpression(null, expr, operator, right);
        }

        return expr;
    }

    private Expression logicalAnd() {
        Expression expr = equality();

        while (match(TokenType.AND)) {
            BinaryOperator operator = BinaryOperator.AND;
            Expression right = equality();
            expr = new BinaryExpression(null, expr, operator, right);
        }

        return expr;
    }

    private Expression equality() {
        Expression expr = comparison();

        while (matchAny(TokenType.EQUALS, TokenType.NOT_EQUALS)) {
            BinaryOperator operator = mapTokenToBinaryOperator(previous().getType());
            Expression right = comparison();
            expr = new BinaryExpression(null, expr, operator, right);
        }

        return expr;
    }
}
````

### 🔍 **Condition Parser**

The `ConditionParser` specializes in parsing comparison and logical conditions:

````java
public class ConditionParser extends BaseParser {

    public Condition parseCondition() {
        return logicalOr();
    }

    private Condition logicalOr() {
        Condition condition = logicalAnd();

        while (match(TokenType.OR)) {
            Condition right = logicalAnd();
            condition = new LogicalCondition(null, condition, LogicalOperator.OR, right);
        }

        return condition;
    }

    private Condition comparison() {
        Expression left = expressionParser.parseExpressionWithoutLogical();

        if (matchValidationOperator()) {
            ComparisonOperator operator = mapTokenToComparisonOperator(previous().getType());
            return new ComparisonCondition(null, left, operator, null, null);
        }

        if (match(TokenType.BETWEEN)) {
            Expression min = expressionParser.parseExpressionWithoutLogical();
            consume(TokenType.AND, "Expected 'and' in between expression");
            Expression max = expressionParser.parseExpressionWithoutLogical();
            return new ComparisonCondition(null, left, ComparisonOperator.BETWEEN, min, max);
        }

        // Handle other comparison operators...
    }
}
````

### ⚡ **Action Parser**

The `ActionParser` handles all action types including assignments and function calls:

````java
public class ActionParser extends BaseParser {

    public Action parseAction() {
        if (match(TokenType.SET)) {
            return parseSetAction();
        }

        if (match(TokenType.CALCULATE)) {
            return parseCalculateAction();
        }

        if (match(TokenType.CALL)) {
            return parseFunctionCallAction();
        }

        if (match(TokenType.IF)) {
            return parseConditionalAction();
        }

        // Arithmetic actions are emitted by `add`/`subtract`/`multiply`/`divide` keywords
        // (parseArithmeticAction), not by `=` / `+=` operators -- the latter are not part
        // of the action DSL.

        throw error("Expected action statement");
    }

    private SetAction parseSetAction() {
        String variableName = consume(TokenType.IDENTIFIER, "Expected variable name").getLexeme();
        consume(TokenType.TO, "Expected 'to' after variable name");
        Expression value = expressionParser.parseExpression();
        return new SetAction(null, variableName, value);
    }
}
````

## Visitor Pattern Implementation

### 🧠 **Understanding the Visitor Pattern**

**What is the Visitor Pattern?**

The Visitor Pattern is a behavioral design pattern that allows you to define new operations on a set of objects without modifying their classes. It's particularly useful when you have a stable object structure (like an AST) but want to add new operations frequently.

**The Problem It Solves:**

Imagine you have an AST with many node types (BinaryExpression, VariableExpression, SetAction, etc.). You want to perform different operations on these nodes:
- Evaluate expressions
- Validate syntax
- Optimize the AST
- Generate code
- Collect statistics

**Without Visitor Pattern (Bad Approach):**

```java
// This approach violates the Open/Closed Principle
public abstract class ASTNode {
    public abstract Object evaluate(EvaluationContext context);
    public abstract List<ValidationError> validate();
    public abstract ASTNode optimize();
    public abstract String generateCode();
    // Adding new operations requires modifying ALL node classes!
}
```

**Problems with this approach:**
1. **Violates Open/Closed Principle**: Adding new operations requires modifying existing classes
2. **Mixed Responsibilities**: Node classes contain both structure and operations
3. **Difficult Maintenance**: Changes to one operation affect all node classes
4. **Poor Extensibility**: Third-party developers can't add operations

**With Visitor Pattern (Good Approach):**

```java
// Node classes only contain structure
public abstract class ASTNode {
    public abstract <T> T accept(ASTVisitor<T> visitor);
}

// Operations are separate visitor classes
public interface ASTVisitor<T> {
    T visitBinaryExpression(BinaryExpression node);
    T visitVariableExpression(VariableExpression node);
    // ... one method for each node type
}
```

**Benefits of this approach:**
1. **Follows Open/Closed Principle**: New operations can be added without modifying existing code
2. **Separation of Concerns**: Node structure is separate from operations
3. **Easy Extensibility**: New visitors can be added by anyone
4. **Type Safety**: Compile-time checking ensures all node types are handled

### 🎯 **ASTVisitor Interface Design**

The `ASTVisitor` interface is the heart of the visitor pattern implementation:

```java
public interface ASTVisitor<T> {

    // Expression visitors - handle value computation
    T visitBinaryExpression(BinaryExpression node);             // a + b, a > b, a and b, etc.
    T visitUnaryExpression(UnaryExpression node);               // -a, !a, is_positive(a)
    T visitVariableExpression(VariableExpression node);         // creditScore, user.profile.name
    T visitLiteralExpression(LiteralExpression node);           // 42, "hello", true, [1,2,3]
    T visitFunctionCallExpression(FunctionCallExpression node); // max(a, b), if_else(...), coalesce(...)
    T visitJsonPathExpression(JsonPathExpression node);         // structural node; emitted by builders
    T visitRestCallExpression(RestCallExpression node);         // structural node; emitted by builders

    // Condition visitors - handle boolean logic
    T visitComparisonCondition(ComparisonCondition node);       // a > b, a between x and y, etc.
    T visitLogicalCondition(LogicalCondition node);             // a AND b, a OR b, NOT a
    T visitExpressionCondition(ExpressionCondition node);       // any Expression as a condition

    // Action visitors - handle state changes
    T visitSetAction(SetAction node);                           // set var to value
    T visitCalculateAction(CalculateAction node);               // calculate var as expr (pure-math only)
    T visitRunAction(RunAction node);                           // run var as fn(...) / json_get(...) / rest_*(...)
    T visitFunctionCallAction(FunctionCallAction node);         // call fn with [args]
    T visitConditionalAction(ConditionalAction node);           // if cond then ... else ...
    T visitArithmeticAction(ArithmeticAction node);             // add/subtract/multiply/divide ... to/from/by var
    T visitListAction(ListAction node);                         // append/prepend/remove ... to/from list
    T visitCircuitBreakerAction(CircuitBreakerAction node);     // circuit_breaker "MESSAGE"
    T visitForEachAction(ForEachAction node);                   // forEach item[, index] in items: actions
    T visitWhileAction(WhileAction node);                       // while cond: actions
    T visitDoWhileAction(DoWhileAction node);                   // do: actions while cond
}
```

**Key Design Decisions:**

1. **Generic Return Type**: `<T>` allows different visitors to return different types
2. **One Method Per Node**: Ensures all node types are explicitly handled
3. **Descriptive Names**: Method names clearly indicate which node type they handle
4. **Logical Grouping**: Methods are grouped by node category (expressions, conditions, actions)

### 🧮 **Expression Evaluator: Bringing AST to Life**

**What is the Expression Evaluator?**

The `ExpressionEvaluator` is a concrete implementation of the `ASTVisitor` interface that traverses an AST and computes the actual values of expressions. It's the component that transforms the abstract syntax tree into concrete results.

**How the Visitor Pattern Works in Practice:**

```java
public class ExpressionEvaluator implements ASTVisitor<Object> {

    private final EvaluationContext context;  // Contains variables and state
    private final RestCallService restCallService;  // For REST API calls
    private final JsonPathService jsonPathService;  // For JSON path queries

    @Override
    public Object visitBinaryExpression(BinaryExpression node) {
        // Step 1: Recursively evaluate left operand
        Object leftValue = node.getLeft().accept(this);

        // Step 2: Recursively evaluate right operand
        Object rightValue = node.getRight().accept(this);

        // Step 3: Apply the operator to the values
        return evaluateOperation(node.getOperator().getSymbol(), leftValue, rightValue);
    }

    @Override
    public Object visitUnaryExpression(UnaryExpression node) {
        // Step 1: Recursively evaluate the operand
        Object operandValue = node.getOperand().accept(this);

        // Step 2: Apply the unary operator
        return switch (node.getOperator()) {
            case NEGATE -> negateValue(operandValue);           // -value
            case NOT -> !isTruthy(operandValue);                // !value
            case IS_POSITIVE -> isPositive(operandValue);       // value is_positive
            case IS_EMAIL -> isValidEmail(operandValue);        // value is_email
            case IS_PHONE -> isValidPhone(operandValue);        // value is_phone
            case IS_CREDIT_SCORE -> isValidCreditScore(operandValue); // value is_credit_score
            default -> throw new ASTException("Unknown unary operator: " + node.getOperator());
        };
    }

    @Override
    public Object visitVariableExpression(VariableExpression node) {
        // Look up variable value in the evaluation context
        return context.getVariable(node.getVariableName());
    }

    @Override
    public Object visitLiteralExpression(LiteralExpression node) {
        // Literals evaluate to themselves
        return node.getValue();
    }

    @Override
    public Object visitFunctionCallExpression(FunctionCallExpression node) {
        // Step 1: Evaluate all arguments
        Object[] args = node.hasArguments() ?
            node.getArguments().stream().map(arg -> arg.accept(this)).toArray() :
            new Object[0];

        // Step 2: Call the function with evaluated arguments
        return callFunction(node.getFunctionName(), args);
    }
}
```

**Key Concepts Demonstrated:**

1. **Recursive Evaluation**: Each visitor method calls `accept(this)` on child nodes
2. **Bottom-Up Evaluation**: Leaf nodes (literals, variables) are evaluated first
3. **Type Handling**: The evaluator handles type conversions and validations
4. **Context Dependency**: Variable values come from the evaluation context
5. **Error Handling**: Invalid operations throw descriptive exceptions

**Example Evaluation Flow:**

For the expression `(income + bonus) * 0.1`:

```
1. visitBinaryExpression(* operator)
   ├── 2. visitBinaryExpression(+ operator)
   │   ├── 3. visitVariableExpression("income") → 50000
   │   └── 4. visitVariableExpression("bonus") → 5000
   │   └── Result: 55000
   └── 5. visitLiteralExpression(0.1) → 0.1
   └── Final Result: 5500.0
```

**Why This Design is Powerful:**

1. **Composability**: Complex expressions are built from simple ones
2. **Reusability**: The same evaluator works for any expression structure
3. **Extensibility**: New expression types just need new visitor methods
4. **Testability**: Each visitor method can be tested independently

### ⚡ **Action Executor: Modifying System State**

**What is the Action Executor?**

The `ActionExecutor` is another concrete visitor that executes actions - operations that modify the system state rather than just computing values. While expressions are "pure" (they don't change anything), actions have "side effects" (they modify variables, call external services, etc.).

**Key Difference from Expression Evaluator:**

- **Expression Evaluator**: Returns values (`ASTVisitor<Object>`)
- **Action Executor**: Performs side effects (`ASTVisitor<Void>`)

**Action Executor Design:**

```java
public class ActionExecutor implements ASTVisitor<Void> {

    private final EvaluationContext context;           // State to modify
    private final ExpressionEvaluator expressionEvaluator; // For evaluating expressions within actions

    @Override
    public Void visitSetAction(SetAction node) {
        // Step 1: Evaluate the value expression
        Object value = node.getValue().accept(expressionEvaluator);

        // Step 2: Store the value in the context
        context.setComputedVariable(node.getVariableName(), value);

        // Step 3: Log for debugging
        log.debug("Set {} = {}", node.getVariableName(), value);

        // Actions return null (they're about side effects, not values)
        return null;
    }

    @Override
    public Void visitCalculateAction(CalculateAction node) {
        // Step 1: Evaluate the calculation expression
        Object result = node.getExpression().accept(expressionEvaluator);

        // Step 2: Store the result
        context.setComputedVariable(node.getResultVariable(), result);

        log.debug("Calculated {} = {}", node.getResultVariable(), result);
        return null;
    }

    @Override
    public Void visitArithmeticAction(ArithmeticAction node) {
        // Equivalent of an "in-place" compound assignment, expressed via dedicated keywords
        // in the DSL (`add X to var`, `subtract X from var`, etc.). Both operands must be
        // numeric; non-numeric operands raise IllegalArgumentException so authoring bugs
        // surface immediately rather than silently no-op'ing.
        Object value = node.getValue().accept(expressionEvaluator);
        Object current = context.getVariable(node.getVariableName());

        if (!(current instanceof Number) || !(value instanceof Number)) {
            throw new IllegalArgumentException(
                "Arithmetic action requires numeric operands");
        }

        BigDecimal currentNum = toBigDecimal(current);
        BigDecimal valueNum = toBigDecimal(value);
        BigDecimal result = switch (node.getOperation()) {
            case ADD      -> currentNum.add(valueNum);
            case SUBTRACT -> currentNum.subtract(valueNum);
            case MULTIPLY -> currentNum.multiply(valueNum);
            case DIVIDE   -> {
                if (valueNum.signum() == 0) {
                    throw new ArithmeticException("Division by zero in arithmetic action");
                }
                yield currentNum.divide(valueNum, 10, RoundingMode.HALF_UP);
            }
        };
        context.setComputedVariable(node.getVariableName(), result);
        return null;
    }

    @Override
    public Void visitConditionalAction(ConditionalAction node) {
        // Step 1: Evaluate the condition
        boolean conditionResult = (Boolean) node.getCondition().accept(
            new ConditionEvaluator(context, expressionEvaluator)
        );

        // Step 2: Choose which actions to execute
        List<Action> actionsToExecute = conditionResult ?
            node.getThenActions() : node.getElseActions();

        // Step 3: Execute the chosen actions
        for (Action action : actionsToExecute) {
            action.accept(this);  // Recursive execution
        }

        return null;
    }
}
```

**Key Concepts:**

1. **Side Effects**: Actions modify the evaluation context
2. **Expression Delegation**: Actions use the expression evaluator for value computation
3. **Recursive Execution**: Conditional actions can contain other actions
4. **Type Handling**: Different assignment operators handle types differently
5. **Error Handling**: Invalid operations throw descriptive exceptions

**Example Action Execution:**

For the action sequence:
```yaml
then:
  - set base_score to 600
  - calculate adjusted_score as base_score + (income / 1000)
  - set final_score to adjusted_score
```

Execution flow:
1. `visitSetAction`: Sets `base_score = 600`
2. `visitCalculateAction`: Evaluates `600 + (75000 / 1000) = 675`, sets `adjusted_score = 675`
3. `visitSetAction`: Sets `final_score = 675`

### 🎯 **Evaluation Context: The State Container**

**What is the Evaluation Context?**

The `EvaluationContext` is the central state container during rule evaluation. It holds all variables, tracks execution state, and provides the runtime environment for rule execution. Think of it as the "memory" of the rule engine during evaluation.

**Why Do We Need Variable Scoping?**

In a rule engine, variables can come from different sources with different priorities:

1. **Input Variables**: Provided by the API caller (e.g., `creditScore`, `income`)
2. **System Constants**: Loaded from database (e.g., `MIN_CREDIT_SCORE`, `MAX_LOAN_AMOUNT`)
3. **Computed Variables**: Calculated during rule execution (e.g., `risk_score`, `approval_status`)

**The Variable Resolution Problem:**

What happens if the same variable name exists in multiple scopes? For example:
- Input: `score = 750` (from API)
- Computed: `score = 800` (calculated during rule)

Which value should `score` resolve to? The evaluation context implements a **priority-based resolution system**.

**Evaluation Context Design:**

```java
public class EvaluationContext {

    // Metadata
    private String ruleName;        // Name of the rule being evaluated
    private String operationId;     // Unique ID for this evaluation
    private long startTime;         // When evaluation started

    // Variable storage with different scopes
    private Map<String, Object> inputVariables;     // Priority 2: From API request
    private Map<String, Object> systemConstants;    // Priority 3: From database
    private Map<String, Object> computedVariables;  // Priority 1: Calculated during execution

    // Execution tracking
    private Map<String, Object> executionMetrics;   // Performance and statistics
    private List<String> executionLog;              // Debug trace of operations
    private boolean debugMode;                      // Whether to collect debug info

    /**
     * Variable resolution with priority: Computed > Input > Constants
     * This implements the "shadowing" concept from programming languages
     */
    public Object getVariable(String name) {
        // Priority 1: Computed variables (highest priority)
        if (computedVariables.containsKey(name)) {
            return computedVariables.get(name);
        }

        // Priority 2: Input variables (medium priority)
        if (inputVariables.containsKey(name)) {
            return inputVariables.get(name);
        }

        // Priority 3: System constants (lowest priority)
        return systemConstants.get(name);
    }

    /**
     * Set a computed variable (creates or updates)
     */
    public void setComputedVariable(String name, Object value) {
        computedVariables.put(name, value);

        // Optional debug logging
        if (debugMode) {
            executionLog.add(String.format("Set %s = %s", name, value));
        }
    }

    /**
     * Check if a variable exists in any scope
     */
    public boolean hasVariable(String name) {
        return computedVariables.containsKey(name) ||
               inputVariables.containsKey(name) ||
               systemConstants.containsKey(name);
    }

    /**
     * Get all variables from a specific scope
     */
    public Map<String, Object> getComputedVariables() {
        return new HashMap<>(computedVariables);  // Defensive copy
    }

    /**
     * Clear computed variables (useful for testing)
     */
    public void clearComputedVariables() {
        computedVariables.clear();
    }
}
```

**Key Design Principles:**

1. **Priority-Based Resolution**: Computed variables shadow input variables and constants
2. **Thread Safety**: Uses `ConcurrentHashMap` for thread-safe access
3. **Debug Support**: Optional logging of all variable changes
4. **Immutable Snapshots**: Getter methods return defensive copies
5. **Performance Tracking**: Built-in metrics collection

**Example Variable Resolution:**

```java
// Setup context
context.setInputVariable("score", 750);           // From API
context.setSystemConstant("MIN_SCORE", 600);      // From database
context.setComputedVariable("score", 800);        // Calculated

// Resolution
context.getVariable("score");      // Returns 800 (computed shadows input)
context.getVariable("MIN_SCORE");  // Returns 600 (only in constants)
context.getVariable("unknown");    // Returns null (doesn't exist)
```

**Why This Design Works:**

1. **Predictable Behavior**: Clear priority rules prevent confusion
2. **Flexibility**: Rules can override input values when needed
3. **Performance**: Hash map lookups are O(1)
4. **Debugging**: Full trace of variable changes available
5. **Isolation**: Each evaluation gets its own context

## Evaluation Engine

### 🧠 **Understanding the Evaluation Engine**

**What is the Evaluation Engine?**

The `ASTRulesEvaluationEngine` is the orchestrator that coordinates the entire rule evaluation process. It's like the conductor of an orchestra, ensuring all components work together harmoniously to evaluate a rule.

**The Evaluation Process:**

Rule evaluation follows a well-defined pipeline that ensures correctness, performance, and debuggability:

```
Input (YAML + Data) → Parse → Validate → Load Constants → Evaluate → Execute → Result
```

### 🚀 **Main Evaluation Engine Architecture**

```java
@Component
@Slf4j
public class ASTRulesEvaluationEngine {

    // Dependencies injected by Spring
    private final ASTRulesDSLParser astParser;        // Converts YAML to AST
    private final ConstantService constantService;     // Loads system constants
    private final RestCallService restCallService;    // For REST API calls
    private final JsonPathService jsonPathService;    // For JSON path queries

    /**
     * Reactive evaluation (non-blocking)
     * Returns a Mono for integration with reactive systems
     */
    public Mono<ASTRulesEvaluationResult> evaluateRulesReactive(
            String rulesDefinition,
            Map<String, Object> inputData) {

        return Mono.fromCallable(() -> evaluateRules(rulesDefinition, inputData))
                .onErrorMap(e -> new ASTException("Rule evaluation failed: " + e.getMessage()));
    }

    /**
     * Synchronous evaluation (blocking)
     * Main evaluation method that orchestrates the entire process
     */
    public ASTRulesEvaluationResult evaluateRules(
            String rulesDefinition,
            Map<String, Object> inputData) {

        // Setup tracking
        long startTime = System.currentTimeMillis();
        String operationId = UUID.randomUUID().toString();

        try {
            // PHASE 1: PARSING
            // Convert YAML string to structured AST
            ASTRulesDSL rulesDSL = astParser.parseRules(rulesDefinition);

            // PHASE 2: CONTEXT CREATION
            // Set up the evaluation environment
            EvaluationContext context = createEvaluationContext(rulesDSL, inputData, operationId);

            // PHASE 3: CONSTANT LOADING
            // Load system constants from database
            loadSystemConstants(context, rulesDSL);

            // PHASE 4: VISITOR CREATION
            // Create the visitors that will traverse the AST
            ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(
                context, restCallService, jsonPathService);
            ActionExecutor actionExecutor = new ActionExecutor(
                context, restCallService, jsonPathService);

            // PHASE 5: CONDITION EVALUATION
            // Evaluate the 'when' conditions to determine rule outcome
            boolean conditionResult = evaluateConditions(rulesDSL.getConditions(), expressionEvaluator);

            // PHASE 6: ACTION EXECUTION
            // Execute either 'then' or 'else' actions based on condition result
            List<Action> actionsToExecute = conditionResult ?
                rulesDSL.getThenActions() : rulesDSL.getElseActions();

            executeActions(actionsToExecute, actionExecutor);

            // PHASE 7: RESULT GENERATION
            // Package up the results for return to caller
            return buildEvaluationResult(context, conditionResult, startTime);

        } catch (Exception e) {
            // Error handling with detailed logging
            log.error("Rule evaluation failed for operation {}", operationId, e);
            return buildErrorResult(e, operationId, startTime);
        }
    }
}
```

### 🔍 **Detailed Phase Analysis**

#### **Phase 1: Parsing**
```java
ASTRulesDSL rulesDSL = astParser.parseRules(rulesDefinition);
```

**What happens:**
- YAML is parsed into a Map structure
- DSL expressions are tokenized
- AST nodes are constructed
- Syntax validation occurs

**Why it's important:**
- Catches syntax errors early
- Creates optimized AST structure
- Enables caching for performance

#### **Phase 2: Context Creation**
```java
EvaluationContext context = createEvaluationContext(rulesDSL, inputData, operationId);
```

**What happens:**
- Input variables are stored in context
- Metadata (rule name, operation ID) is set
- Debug mode is configured
- Performance tracking is initialized

#### **Phase 3: Constant Loading**
```java
loadSystemConstants(context, rulesDSL);
```

**What happens:**
- AST is scanned for variable references
- Required constants are loaded from database
- Constants are stored in context with lowest priority

**Why it's optimized:**
- Only loads constants that are actually used
- Batch loads multiple constants in one query
- Caches constants for subsequent evaluations

#### **Phase 4: Visitor Creation**
```java
ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(context, ...);
ActionExecutor actionExecutor = new ActionExecutor(context, ...);
```

**What happens:**
- Visitors are configured with evaluation context
- External services are injected for REST calls and JSON path queries
- Visitors are ready to traverse the AST

#### **Phase 5: Condition Evaluation**
```java
boolean conditionResult = evaluateConditions(rulesDSL.getConditions(), expressionEvaluator);

private boolean evaluateConditions(List<Condition> conditions, ExpressionEvaluator evaluator) {
    if (conditions.isEmpty()) {
        return true;  // No conditions means rule always applies
    }

    // All conditions must be true (implicit AND)
    for (Condition condition : conditions) {
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(
            evaluator.getContext(), evaluator);
        Boolean result = (Boolean) condition.accept(conditionEvaluator);

        if (!Boolean.TRUE.equals(result)) {
            return false;  // Short-circuit on first false condition
        }
    }

    return true;
}
```

**Key concepts:**
- **Short-circuit evaluation**: Stops at first false condition
- **Implicit AND**: All conditions must be true
- **Null safety**: Handles null results gracefully

#### **Phase 6: Action Execution**
```java
List<Action> actionsToExecute = conditionResult ?
    rulesDSL.getThenActions() : rulesDSL.getElseActions();

executeActions(actionsToExecute, actionExecutor);
```

**What happens:**
- Chooses action list based on condition result
- Executes actions sequentially
- Each action can modify the evaluation context
- Side effects (variable assignments, external calls) occur here

#### **Phase 7: Result Generation**
```java
return buildEvaluationResult(context, conditionResult, startTime);
```

**What happens:**
- Collects all computed variables as outputs
- Calculates performance metrics
- Packages debug information if enabled
- Creates immutable result object

## Validation System

The validation system ensures that AST structures are semantically correct before evaluation. It uses the visitor pattern to traverse the AST and collect validation errors.

### 🔍 **YAML DSL Validator**

The main validator coordinates validation across multiple dimensions:

````java
@Component
@Slf4j
@RequiredArgsConstructor
public class YamlDslValidator implements ASTVisitor<List<ValidationError>> {

    private final ASTRulesDSLParser astParser;

    public ValidationResult validate(String yamlContent) {
        List<ValidationError> allErrors = new ArrayList<>();
        List<ValidationWarning> allWarnings = new ArrayList<>();

        try {
            // 1. Parse YAML structure
            Map<String, Object> yamlMap = parseYamlStructure(yamlContent);

            // 2. Validate YAML structure
            validateYamlStructure(yamlMap, allErrors);

            // 3. Parse to AST
            ASTRulesDSL astModel = astParser.parseRules(yamlContent);

            // 4. Validate AST semantics
            ValidationVisitor astValidator = new ValidationVisitor();
            List<ValidationError> astErrors = astModel.accept(astValidator);
            allErrors.addAll(astErrors);

            // 5. Validate variable references
            validateVariableReferences(astModel, allErrors);

            // 6. Validate type compatibility
            validateTypeCompatibility(astModel, allErrors);

            // 7. Generate warnings for best practices
            generateBestPracticeWarnings(astModel, allWarnings);

        } catch (Exception e) {
            allErrors.add(new ValidationError(
                "Failed to parse YAML: " + e.getMessage(),
                "PARSE_ERROR",
                "unknown"
            ));
        }

        return ValidationResult.builder()
            .status(allErrors.isEmpty() ? ValidationStatus.VALID : ValidationStatus.ERROR)
            .issues(ValidationIssues.builder()
                .errors(allErrors)
                .warnings(allWarnings)
                .build())
            .build();
    }
}
````

### 🎯 **Validation Visitor**

The `ValidationVisitor` performs semantic validation of AST nodes:

````java
public class ValidationVisitor implements ASTVisitor<List<ValidationError>> {

    private final Set<String> declaredVariables = new HashSet<>();
    private final Set<String> referencedVariables = new HashSet<>();

    @Override
    public List<ValidationError> visitBinaryExpression(BinaryExpression node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate operands
        errors.addAll(node.getLeft().accept(this));
        errors.addAll(node.getRight().accept(this));

        // Validate operator compatibility
        ExpressionType leftType = node.getLeft().getExpressionType();
        ExpressionType rightType = node.getRight().getExpressionType();

        if (!isOperatorCompatible(node.getOperator(), leftType, rightType)) {
            errors.add(new ValidationError(
                String.format("Operator %s is not compatible with types %s and %s",
                    node.getOperator().getSymbol(), leftType, rightType),
                "TYPE_MISMATCH",
                node.getLocationString()
            ));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitUnaryExpression(UnaryExpression node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate operand
        errors.addAll(node.getOperand().accept(this));

        // Validate operator compatibility
        ExpressionType operandType = node.getOperand().getExpressionType();

        if (!isUnaryOperatorCompatible(node.getOperator(), operandType)) {
            errors.add(new ValidationError(
                String.format("Unary operator %s is not compatible with type %s",
                    node.getOperator().getSymbol(), operandType),
                "TYPE_MISMATCH",
                node.getLocationString()
            ));
        }

        return errors;
    }

    @Override
    public List<ValidationError> visitVariableExpression(VariableExpression node) {
        referencedVariables.add(node.getVariableName());

        // Variable existence will be validated later
        return new ArrayList<>();
    }

    @Override
    public List<ValidationError> visitSetAction(SetAction node) {
        List<ValidationError> errors = new ArrayList<>();

        // Validate value expression
        errors.addAll(node.getValue().accept(this));

        // Track declared variable
        declaredVariables.add(node.getVariableName());

        return errors;
    }

    private boolean isOperatorCompatible(BinaryOperator operator, ExpressionType left, ExpressionType right) {
        return switch (operator) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO, POWER ->
                left == ExpressionType.NUMBER && right == ExpressionType.NUMBER;
            case EQUALS, NOT_EQUALS -> true; // Any types can be compared for equality
            case LESS_THAN, GREATER_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL ->
                (left == ExpressionType.NUMBER && right == ExpressionType.NUMBER) ||
                (left == ExpressionType.STRING && right == ExpressionType.STRING);
            case AND, OR ->
                left == ExpressionType.BOOLEAN && right == ExpressionType.BOOLEAN;
            case CONTAINS, STARTS_WITH, ENDS_WITH ->
                left == ExpressionType.STRING && right == ExpressionType.STRING;
            default -> false;
        };
    }
}
````

## Error Handling

The AST system provides comprehensive error handling with detailed diagnostics and recovery mechanisms.

### 🚨 **Exception Hierarchy**

```
Exception
└── RuntimeException
    └── ASTException (base for all AST-related exceptions)
        ├── LexerException (lexical analysis errors)
        ├── ParserException (parsing errors)
        ├── ValidationException (validation errors)
        └── EvaluationException (evaluation errors)
```

### 🎯 **AST Exception**

The base exception class for all AST-related errors:

````java
public class ASTException extends RuntimeException {

    private final String errorCode;
    private final SourceLocation location;
    private final List<String> suggestions;

    public ASTException(String message) {
        super(message);
        this.errorCode = "AST_ERROR";
        this.location = null;
        this.suggestions = new ArrayList<>();
    }

    public ASTException(String message, String errorCode, SourceLocation location) {
        super(message);
        this.errorCode = errorCode;
        this.location = location;
        this.suggestions = new ArrayList<>();
    }

    public ASTException(String message, String errorCode, SourceLocation location, List<String> suggestions) {
        super(message);
        this.errorCode = errorCode;
        this.location = location;
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());

        if (location != null) {
            sb.append(" at ").append(location);
        }

        if (!suggestions.isEmpty()) {
            sb.append("\nSuggestions:");
            for (String suggestion : suggestions) {
                sb.append("\n  - ").append(suggestion);
            }
        }

        return sb.toString();
    }
}
````

### 🔧 **Error Recovery**

The parser includes error recovery mechanisms to continue parsing after encountering errors:

```java
protected ParserException error(String message, String errorCode, List<String> suggestions) {
    Token current = isAtEnd() ? previous() : peek();
    SourceLocation location = current.getLocation();

    // Log the error for debugging
    log.error("Parser error at {}: {}", location, message);

    // Attempt to synchronize to a known good state
    synchronize();

    return new ParserException(message, errorCode, location, suggestions);
}

private void synchronize() {
    advance();

    while (!isAtEnd()) {
        if (previous().getType() == TokenType.SEMICOLON) return;

        switch (peek().getType()) {
            case SET, CALCULATE, IF, WHEN -> {
                return;
            }
        }

        advance();
    }
}
```

## Performance Optimizations

The AST system includes several performance optimizations for high-throughput scenarios:

### 🚀 **AST Caching**

Parsed AST trees can be cached to avoid re-parsing identical YAML content:

````java
public ASTRulesDSL parseRules(String rulesDefinition) {
    try {
        // Check cache first if caching is enabled
        if (cacheService != null) {
            String cacheKey = cacheService.generateCacheKey(rulesDefinition);
            Optional<ASTRulesDSL> cachedAST = cacheService.getCachedAST(cacheKey);

            if (cachedAST.isPresent()) {
                JsonLogger.info(log, "AST cache hit for rules definition");
                return cachedAST.get();
            }

            // Cache miss - parse and cache the result
            ASTRulesDSL parsedAST = parseRulesInternal(rulesDefinition);
            cacheService.cacheAST(cacheKey, parsedAST);
            return parsedAST;
        }

        return parseRulesInternal(rulesDefinition);
    } catch (Exception e) {
        throw new ASTException("Failed to parse rules definition: " + e.getMessage());
    }
}
````

### ⚡ **Optimization Strategies**

1. **Constant Folding**: Evaluate constant expressions at parse time
2. **Variable Reference Optimization**: Pre-collect variable references for efficient constant loading
3. **Expression Type Inference**: Determine expression types early for optimization
4. **Operator Precedence**: Use precedence-based parsing for efficient expression evaluation
5. **Memory Pool**: Reuse AST node instances where possible
6. **Lazy Evaluation**: Defer expensive operations until needed

### 📊 **Performance Metrics**

The evaluation engine tracks performance metrics:

```java
private void recordPerformanceMetrics(EvaluationContext context, long startTime) {
    long duration = System.currentTimeMillis() - startTime;

    context.getExecutionMetrics().put("evaluation_duration_ms", duration);
    context.getExecutionMetrics().put("ast_nodes_evaluated", nodeCount);
    context.getExecutionMetrics().put("variables_resolved", variableCount);
    context.getExecutionMetrics().put("conditions_evaluated", conditionCount);
    context.getExecutionMetrics().put("actions_executed", actionCount);
}
```

## Functions vs Operators: Architecture and Implementation

This section provides a comprehensive understanding of the architectural distinction between functions and operators in the Firefly Framework Rule Engine, along with practical tutorials for implementing both.

### 🧠 **Fundamental Architectural Distinction**

The Firefly Framework Rule Engine makes a clear architectural distinction between **operators** and **functions** based on their purpose, complexity, and implementation requirements.

#### **🔧 Operators: Core Language Constructs**

**Operators** are fundamental language constructs that form the backbone of the DSL grammar. They are:

- **Purpose**: Core comparisons and logical operations
- **Syntax**: Infix notation (`creditScore greater_than 650`)
- **Implementation**: Defined in lexer tokens and handled by specialized parsers
- **Characteristics**:
  - Fixed arity (usually binary: left operator right)
  - Simple, predictable behavior
  - Core to the DSL grammar structure
  - Optimized for performance
  - Type-safe at parse time

**Examples of Operators:**
```yaml
# Comparison operators
- creditScore greater_than 650
- income less_than_or_equal 100000
- status equals "ACTIVE"

# Logical operators
- isActive and isVerified
- creditScore greater_than 650 or income greater_than 50000

# Validation operators
- email is_email
- phoneNumber is_phone
- creditScore is_credit_score
```

#### **⚡ Functions: Complex Operations with Side Effects**

**Functions** are complex operations that may have side effects, variable parameters, or require external services. They are:

- **Purpose**: Complex operations, calculations, and external integrations
- **Syntax**: Function call notation (`rest_get(url, headers, timeout)`)
- **Implementation**: Handled in the ExpressionEvaluator visitor
- **Characteristics**:
  - Variable arity (0 to many parameters)
  - May have side effects (REST calls, logging, database access)
  - Can access injected services
  - Extensible without changing core grammar
  - Runtime parameter validation

**Examples of Functions:**
```yaml
# REST API calls
- run api_data as rest_get("https://api.example.com/credit", headers, 30)
- run user_info as rest_post("https://api.example.com/users", requestBody)

# JSON operations
- run user_name as json_get(api_data, "user.name")
- run credit_history as json_get(api_data, "credit.history[0]")

# Mathematical functions
- run max_score as max(score1, score2, score3)
- calculate loan_payment as calculate_loan_payment(principal, rate, term)

# String functions
- run formatted_amount as format_currency(amount)
- calculate full_name as concat(firstName, " ", lastName)
```

### 🏗️ **Why This Architecture Matters**

#### **1. Separation of Concerns**

```java
// Operators: Handled in lexer and parser
public enum TokenType {
    GREATER_THAN("greater_than", TokenCategory.OPERATOR),
    IS_EMAIL("is_email", TokenCategory.OPERATOR),
    BETWEEN("between", TokenCategory.OPERATOR)
}

// Functions: Handled in expression evaluator
@Override
public Object visitFunctionCallExpression(FunctionCallExpression node) {
    return switch (node.getFunctionName()) {
        case "rest_get" -> restGet(node.getArguments());
        case "json_get" -> jsonGet(node.getArguments());
        case "max" -> max(node.getArguments());
        default -> throw new EvaluationException("Unknown function: " + node.getFunctionName());
    };
}
```

#### **2. Parameter Flexibility**

**Operators** have fixed parameter patterns:
```yaml
# Binary operators: left operator right
creditScore greater_than 650
income between 30000 and 80000

# Unary operators: operand operator
email is_email
```

**Functions** support variable parameters:
```yaml
# Variable number of parameters
- run max_value as max(score1)                    # 1 parameter
- run max_value as max(score1, score2)            # 2 parameters
- run max_value as max(score1, score2, score3)    # 3 parameters

# Optional parameters with defaults
- run api_data as rest_get(url)                   # Uses default timeout
- run api_data as rest_get(url, headers)          # Uses default timeout
- run api_data as rest_get(url, headers, 30)      # Custom timeout
```

#### **3. Service Integration**

**Functions** can access injected Spring services:
```java
@Component
public class ExpressionEvaluator implements ASTVisitor<Object> {

    @Autowired
    private RestCallService restCallService;

    @Autowired
    private JsonPathService jsonPathService;

    @Autowired
    private ConstantService constantService;

    private Object restGet(Object[] args) {
        // Access to injected services for complex operations
        if (restCallService == null) {
            return createErrorResponse("RestCallService not available");
        }

        try {
            String url = (String) args[0];
            Map<String, String> headers = args.length > 1 ? (Map<String, String>) args[1] : null;
            Integer timeout = args.length > 2 ? (Integer) args[2] : 30;

            return restCallService.get(url, headers, timeout).block();
        } catch (Exception e) {
            log.error("REST GET failed for URL: {}", args[0], e);
            return createErrorResponse("REST GET failed: " + e.getMessage());
        }
    }
}
```

### 📚 **Tutorial: Creating a New Function**

Let's walk through creating a new function called `calculate_credit_risk` that combines multiple credit factors.

#### **Step 1: Define the Function Logic**

Add the function to the `ExpressionEvaluator` class:

```java
@Override
public Object visitFunctionCallExpression(FunctionCallExpression node) {
    String functionName = node.getFunctionName();
    Object[] args = evaluateArguments(node.getArguments());

    return switch (functionName) {
        // Existing functions...
        case "calculate_credit_risk" -> calculateCreditRisk(args);
        default -> throw new EvaluationException("Unknown function: " + functionName);
    };
}

private Object calculateCreditRisk(Object[] args) {
    // Validate parameters
    if (args.length < 3) {
        log.warn("calculate_credit_risk requires at least 3 arguments: creditScore, income, debtToIncomeRatio");
        return null;
    }

    try {
        // Extract and validate parameters
        Number creditScore = convertToNumber(args[0]);
        Number income = convertToNumber(args[1]);
        Number debtToIncomeRatio = convertToNumber(args[2]);

        if (creditScore == null || income == null || debtToIncomeRatio == null) {
            return createErrorResponse("All parameters must be numeric");
        }

        // Optional parameters with defaults
        Number employmentYears = args.length > 3 ? convertToNumber(args[3]) : 2.0;
        Boolean hasCollateral = args.length > 4 ? convertToBoolean(args[4]) : false;

        // Calculate risk score (0-100, lower is better)
        double riskScore = calculateRiskScore(
            creditScore.doubleValue(),
            income.doubleValue(),
            debtToIncomeRatio.doubleValue(),
            employmentYears.doubleValue(),
            hasCollateral
        );

        // Return structured result
        Map<String, Object> result = new HashMap<>();
        result.put("riskScore", riskScore);
        result.put("riskLevel", getRiskLevel(riskScore));
        result.put("recommendation", getRecommendation(riskScore));

        return result;

    } catch (Exception e) {
        log.error("Error calculating credit risk", e);
        return createErrorResponse("Credit risk calculation failed: " + e.getMessage());
    }
}

private double calculateRiskScore(double creditScore, double income, double debtToIncomeRatio,
                                 double employmentYears, boolean hasCollateral) {
    // Credit score factor (0-40 points, lower credit score = higher risk)
    double creditFactor = Math.max(0, (850 - creditScore) / 850 * 40);

    // Income factor (0-20 points, lower income = higher risk)
    double incomeFactor = income < 30000 ? 20 :
                         income < 50000 ? 15 :
                         income < 75000 ? 10 : 5;

    // Debt-to-income factor (0-25 points)
    double debtFactor = debtToIncomeRatio > 0.5 ? 25 :
                       debtToIncomeRatio > 0.4 ? 20 :
                       debtToIncomeRatio > 0.3 ? 15 :
                       debtToIncomeRatio > 0.2 ? 10 : 5;

    // Employment factor (0-10 points)
    double employmentFactor = employmentYears < 1 ? 10 :
                             employmentYears < 2 ? 7 :
                             employmentYears < 5 ? 5 : 2;

    // Collateral factor (-5 points if has collateral)
    double collateralFactor = hasCollateral ? -5 : 0;

    return Math.max(0, Math.min(100, creditFactor + incomeFactor + debtFactor + employmentFactor + collateralFactor));
}

private String getRiskLevel(double riskScore) {
    if (riskScore <= 20) return "LOW";
    if (riskScore <= 40) return "MODERATE";
    if (riskScore <= 60) return "HIGH";
    return "VERY_HIGH";
}

private String getRecommendation(double riskScore) {
    if (riskScore <= 20) return "APPROVE";
    if (riskScore <= 40) return "APPROVE_WITH_CONDITIONS";
    if (riskScore <= 60) return "MANUAL_REVIEW";
    return "DECLINE";
}
```

#### **Step 2: Add Function Documentation**

Update the YAML DSL reference to document the new function:

```yaml
# In docs/yaml-dsl-reference.md

## Mathematical and Financial Functions

### calculate_credit_risk
Calculates a comprehensive credit risk score based on multiple financial factors.

**Syntax:**
```yaml
calculate_credit_risk(creditScore, income, debtToIncomeRatio [, employmentYears] [, hasCollateral])
```

**Parameters:**
- `creditScore` (required): Credit score (300-850)
- `income` (required): Annual income in dollars
- `debtToIncomeRatio` (required): Debt-to-income ratio (0.0-1.0)
- `employmentYears` (optional): Years of employment, defaults to 2.0
- `hasCollateral` (optional): Whether loan has collateral, defaults to false

**Returns:**
Object with properties:
- `riskScore`: Numeric risk score (0-100, lower is better)
- `riskLevel`: String risk level (LOW, MODERATE, HIGH, VERY_HIGH)
- `recommendation`: String recommendation (APPROVE, APPROVE_WITH_CONDITIONS, MANUAL_REVIEW, DECLINE)

**Examples:**
```yaml
# Basic usage
- calculate risk_assessment as calculate_credit_risk(creditScore, income, debtRatio)

# With optional parameters
- calculate risk_assessment as calculate_credit_risk(creditScore, income, debtRatio, employmentYears, true)

# Using the result
- set approved to risk_assessment.recommendation equals "APPROVE"
- set risk_level to risk_assessment.riskLevel
```
```

#### **Step 3: Add Comprehensive Tests**

Create tests for the new function:

```java
@Test
void testCalculateCreditRiskFunction() {
    // Test basic functionality
    String yaml = """
        name: "Credit Risk Test"
        inputs: [creditScore, income, debtRatio]
        then:
          - calculate risk_result as calculate_credit_risk(creditScore, income, debtRatio)
          - set risk_score to risk_result.riskScore
          - set risk_level to risk_result.riskLevel
          - set recommendation to risk_result.recommendation
        """;

    Map<String, Object> inputs = Map.of(
        "creditScore", 750,
        "income", 60000,
        "debtRatio", 0.3
    );

    EvaluationResult result = evaluateRule(yaml, inputs);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutputs()).containsKey("risk_score");
    assertThat(result.getOutputs()).containsKey("risk_level");
    assertThat(result.getOutputs()).containsKey("recommendation");

    // Verify reasonable risk score for good credit profile
    Double riskScore = (Double) result.getOutputs().get("risk_score");
    assertThat(riskScore).isBetween(0.0, 30.0); // Should be low risk
}

@Test
void testCalculateCreditRiskWithOptionalParameters() {
    String yaml = """
        name: "Credit Risk with Optional Params"
        inputs: [creditScore, income, debtRatio, employmentYears, hasCollateral]
        then:
          - calculate risk_result as calculate_credit_risk(creditScore, income, debtRatio, employmentYears, hasCollateral)
          - set final_score to risk_result.riskScore
        """;

    Map<String, Object> inputs = Map.of(
        "creditScore", 650,
        "income", 45000,
        "debtRatio", 0.45,
        "employmentYears", 5,
        "hasCollateral", true
    );

    EvaluationResult result = evaluateRule(yaml, inputs);

    assertThat(result.isSuccess()).isTrue();
    Double riskScore = (Double) result.getOutputs().get("final_score");
    assertThat(riskScore).isNotNull();
    assertThat(riskScore).isBetween(0.0, 100.0);
}

@Test
void testCalculateCreditRiskErrorHandling() {
    String yaml = """
        name: "Credit Risk Error Test"
        inputs: [creditScore]
        then:
          - calculate risk_result as calculate_credit_risk(creditScore)
        """;

    Map<String, Object> inputs = Map.of("creditScore", 750);

    EvaluationResult result = evaluateRule(yaml, inputs);

    // Should handle missing parameters gracefully
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutputs().get("risk_result")).isNull();
}
```

#### **Step 4: Usage Examples**

Here's how the new function would be used in real rules:

```yaml
name: "Loan Application Assessment"
inputs: [creditScore, annualIncome, monthlyDebt, employmentYears, hasCollateral]

constants:
  MIN_CREDIT_SCORE: 600
  MAX_DEBT_RATIO: 0.5

then:
  # Calculate debt-to-income ratio
  - calculate monthly_income as annualIncome / 12
  - calculate debt_to_income_ratio as monthlyDebt / monthly_income

  # Calculate comprehensive risk assessment
  - calculate risk_assessment as calculate_credit_risk(
      creditScore,
      annualIncome,
      debt_to_income_ratio,
      employmentYears,
      hasCollateral
    )

  # Extract risk components
  - set risk_score to risk_assessment.riskScore
  - set risk_level to risk_assessment.riskLevel
  - set recommendation to risk_assessment.recommendation

  # Make approval decision
  - set approved to recommendation equals "APPROVE"
  - set conditional_approval to recommendation equals "APPROVE_WITH_CONDITIONS"
  - set requires_review to recommendation equals "MANUAL_REVIEW"

  # Set loan terms based on risk
  - if risk_level equals "LOW" then
      - set interest_rate to 3.5
      - set loan_term_months to 360
  - if risk_level equals "MODERATE" then
      - set interest_rate to 4.2
      - set loan_term_months to 300
  - if risk_level equals "HIGH" then
      - set interest_rate to 5.8
      - set loan_term_months to 240
```

### 📚 **Tutorial: Creating a New Operator**

Now let's create a new operator called `credit_score_range` that checks if a credit score falls within a specific range category.

#### **Step 1: Add Token Type**

Add the new operator to the `TokenType` enum:

```java
public enum TokenType {
    // ... existing tokens ...

    // Credit scoring operators
    CREDIT_SCORE_RANGE("credit_score_range", TokenCategory.OPERATOR),

    // ... rest of tokens ...
}
```

#### **Step 2: Add Lexer Recognition**

Update the lexer to recognize the new operator:

```java
private boolean isMultiWordToken(String firstWord) {
    return switch (firstWord) {
        case "credit" -> checkForCreditOperators();
        // ... existing cases ...
        default -> false;
    };
}

private boolean checkForCreditOperators() {
    int savedPosition = current;

    if (match('_') && matchWord("score") && match('_') && matchWord("range")) {
        addToken(TokenType.CREDIT_SCORE_RANGE);
        return true;
    }

    // Backtrack if pattern doesn't match
    current = savedPosition;
    return false;
}
```

#### **Step 3: Add Comparison Operator**

Add the operator to the `ComparisonOperator` enum:

```java
public enum ComparisonOperator {
    // ... existing operators ...

    CREDIT_SCORE_RANGE("credit_score_range"),

    // ... rest of operators ...
}
```

#### **Step 4: Add Parser Support**

Update the condition parser to handle the new operator:

```java
private Condition comparison() {
    Expression left = expressionParser.parseExpressionWithoutLogical();

    if (match(TokenType.CREDIT_SCORE_RANGE)) {
        String range = consume(TokenType.STRING, "Expected credit score range").getLexeme();
        return new ComparisonCondition(null, left, ComparisonOperator.CREDIT_SCORE_RANGE,
                                     new LiteralExpression(null, range), null);
    }

    // ... handle other operators ...
}
```

#### **Step 5: Add Evaluation Logic**

Add the evaluation logic to the condition evaluator:

```java
@Override
public Object visitComparisonCondition(ComparisonCondition node) {
    Object leftValue = node.getLeft().accept(this);

    return switch (node.getOperator()) {
        case CREDIT_SCORE_RANGE -> evaluateCreditScoreRange(leftValue, node.getRight());
        // ... other operators ...
    };
}

private Boolean evaluateCreditScoreRange(Object creditScoreObj, Expression rangeExpr) {
    try {
        Number creditScore = convertToNumber(creditScoreObj);
        if (creditScore == null) {
            log.warn("Credit score must be numeric for credit_score_range operator");
            return false;
        }

        String range = (String) rangeExpr.accept(this);
        if (range == null) {
            log.warn("Credit score range must be specified");
            return false;
        }

        int score = creditScore.intValue();

        return switch (range.toLowerCase()) {
            case "poor" -> score >= 300 && score <= 579;
            case "fair" -> score >= 580 && score <= 669;
            case "good" -> score >= 670 && score <= 739;
            case "very_good" -> score >= 740 && score <= 799;
            case "excellent" -> score >= 800 && score <= 850;
            default -> {
                log.warn("Unknown credit score range: {}. Valid ranges: poor, fair, good, very_good, excellent", range);
                yield false;
            }
        };

    } catch (Exception e) {
        log.error("Error evaluating credit score range", e);
        return false;
    }
}
```

#### **Step 6: Add Documentation and Tests**

Document the new operator:

```yaml
## Credit Score Operators

### credit_score_range
Checks if a credit score falls within a standard credit score range category.

**Syntax:**
```yaml
creditScore credit_score_range "range_name"
```

**Valid Ranges:**
- `"poor"`: 300-579
- `"fair"`: 580-669
- `"good"`: 670-739
- `"very_good"`: 740-799
- `"excellent"`: 800-850

**Examples:**
```yaml
when:
  - creditScore credit_score_range "good"
  - applicantScore credit_score_range "excellent"
```
```

Add comprehensive tests:

```java
@Test
void testCreditScoreRangeOperator() {
    String yaml = """
        name: "Credit Score Range Test"
        inputs: [creditScore]
        when:
          - creditScore credit_score_range "good"
        then:
          - set qualified to true
        """;

    // Test good credit score
    Map<String, Object> inputs = Map.of("creditScore", 720);
    EvaluationResult result = evaluateRule(yaml, inputs);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutputs().get("qualified")).isEqualTo(true);

    // Test poor credit score
    inputs = Map.of("creditScore", 550);
    result = evaluateRule(yaml, inputs);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getOutputs()).doesNotContainKey("qualified"); // Condition failed
}

@Test
void testAllCreditScoreRanges() {
    Map<String, Integer> testCases = Map.of(
        "poor", 500,
        "fair", 620,
        "good", 700,
        "very_good", 760,
        "excellent", 820
    );

    for (Map.Entry<String, Integer> testCase : testCases.entrySet()) {
        String yaml = String.format("""
            name: "Credit Range Test"
            inputs: [score]
            when:
              - score credit_score_range "%s"
            then:
              - set range_matched to true
            """, testCase.getKey());

        Map<String, Object> inputs = Map.of("score", testCase.getValue());
        EvaluationResult result = evaluateRule(yaml, inputs);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputs().get("range_matched"))
            .as("Score %d should match range %s", testCase.getValue(), testCase.getKey())
            .isEqualTo(true);
    }
}
```

### 🎯 **When to Choose Functions vs Operators**

#### **Choose Functions When:**

1. **Variable Parameters**: Need 0 to many parameters
   ```yaml
   # Functions can handle variable parameters elegantly
   - run max_value as max(score1, score2, score3, score4)
   - run api_data as rest_get(url, headers, timeout)
   ```

2. **Side Effects**: Operation involves external systems
   ```yaml
   # REST calls, database access, logging
   - run credit_report as rest_get("https://credit-api.com/report", headers)
   - calculate audit_log as log_event("CREDIT_CHECK", userId, timestamp)
   ```

3. **Complex Logic**: Multi-step calculations or business logic
   ```yaml
   # Complex financial calculations
   - calculate loan_payment as calculate_loan_payment(principal, rate, term, type)
   - calculate risk_score as calculate_credit_risk(score, income, debt, employment)
   ```

4. **Service Integration**: Need access to Spring services
   ```java
   // Functions can access injected services
   @Autowired private RestCallService restCallService;
   @Autowired private DatabaseService databaseService;
   ```

#### **Choose Operators When:**

1. **Core Language Constructs**: Fundamental comparisons and logic
   ```yaml
   # Basic comparisons that are core to the language
   - creditScore greater_than 650
   - status equals "ACTIVE"
   - income between 30000 and 80000
   ```

2. **Fixed Arity**: Always the same number of operands
   ```yaml
   # Binary operators: left operator right
   - value1 greater_than value2
   - text contains "substring"
   ```

3. **High Performance**: Need optimized evaluation
   ```java
   // Operators are optimized in the parser and evaluator
   // No function call overhead
   ```

4. **Grammar Integration**: Part of the core DSL syntax
   ```yaml
   # Operators integrate naturally with the grammar
   when:
     - condition1 and condition2 or condition3
   ```

### 🚀 **Best Practices**

#### **For Functions:**

1. **Parameter Validation**: Always validate parameters
   ```java
   private Object myFunction(Object[] args) {
       if (args.length < 2) {
           log.warn("myFunction requires at least 2 arguments");
           return null;
       }
       // ... validation logic
   }
   ```

2. **Error Handling**: Graceful error handling with logging
   ```java
   try {
       // Function logic
   } catch (Exception e) {
       log.error("Function failed", e);
       return createErrorResponse("Function failed: " + e.getMessage());
   }
   ```

3. **Documentation**: Comprehensive documentation with examples
4. **Testing**: Test all parameter combinations and error cases

#### **For Operators:**

1. **Precedence**: Carefully consider operator precedence
2. **Type Safety**: Ensure type compatibility at parse time
3. **Performance**: Optimize for high-frequency evaluation
4. **Consistency**: Follow existing operator patterns

This comprehensive guide provides everything needed to understand and extend the function and operator systems in the Firefly Framework Rule Engine. The clear architectural separation enables both powerful functionality and maintainable code.

## Extending the AST System

This section provides comprehensive guidance for extending the AST system with new functionality.

### 🔧 **Adding New Operators**

#### **Step 1: Define the Operator**

Add your operator to the appropriate enum:

```java
// In BinaryOperator.java for binary operators
CUSTOM_OPERATOR("custom_op", ExpressionType.BOOLEAN, 4),

// In UnaryOperator.java for unary operators
IS_CUSTOM("is_custom", ExpressionType.BOOLEAN),

// In ComparisonOperator.java for comparison operators
CUSTOM_COMPARISON("custom_comparison"),
```

#### **Step 2: Add Token Type**

Add the token type to `TokenType.java`:

```java
// In TokenType.java
CUSTOM_OPERATOR("custom_op", TokenCategory.OPERATOR),
```

#### **Step 3: Update Lexer**

Add recognition in the lexer if needed:

```java
// In Lexer.java keywords map
static {
    // ... existing keywords
    KEYWORDS.put("custom_op", TokenType.CUSTOM_OPERATOR);
}
```

#### **Step 4: Implement Evaluation Logic**

Add evaluation logic to `ExpressionEvaluator.java`:

```java
// In ExpressionEvaluator.java
private Object evaluateOperation(String operator, Object left, Object right) {
    return switch (operator.toLowerCase()) {
        // ... existing operators
        case "custom_op" -> evaluateCustomOperator(left, right);
        default -> throw new ASTException("Unknown operator: " + operator);
    };
}

private Object evaluateCustomOperator(Object left, Object right) {
    // Type checking
    if (left == null || right == null) {
        return false;
    }

    // Implement your custom logic
    if (left instanceof Number leftNum && right instanceof Number rightNum) {
        return performCustomNumericOperation(leftNum, rightNum);
    }

    throw new ASTException("Custom operator requires numeric operands");
}
```

#### **Step 5: Add Validation**

Update validation logic in `ValidationVisitor.java`:

```java
private boolean isOperatorCompatible(BinaryOperator operator, ExpressionType left, ExpressionType right) {
    return switch (operator) {
        // ... existing cases
        case CUSTOM_OPERATOR ->
            left == ExpressionType.NUMBER && right == ExpressionType.NUMBER;
        default -> false;
    };
}
```

#### **Step 6: Add Tests**

Create comprehensive tests:

```java
@Test
@DisplayName("Should evaluate custom operator correctly")
void testCustomOperator() {
    String yaml = """
        name: "Test Custom Operator"
        inputs: [value1, value2]
        when:
          - value1 custom_op value2
        then:
          - set result to true
        else:
          - set result to false
        output:
          result: boolean
        """;

    Map<String, Object> inputData = Map.of(
        "value1", 10,
        "value2", 5
    );

    ASTRulesEvaluationResult result = evaluationEngine.evaluateRules(yaml, inputData);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getConditionResult()).isTrue();
    assertThat(result.getOutputData().get("result")).isEqualTo(true);
}
```

### 🔧 **Adding New Functions**

#### **Step 1: Implement Function Logic**

Add your function to `ExpressionEvaluator.java`:

```java
@Override
public Object visitFunctionCallExpression(FunctionCallExpression node) {
    String functionName = node.getFunctionName();
    Object[] args = node.hasArguments() ?
        node.getArguments().stream().map(arg -> arg.accept(this)).toArray() :
        new Object[0];

    return switch (functionName.toLowerCase()) {
        // ... existing functions
        case "custom_function" -> customFunction(args);
        case "advanced_calculation" -> advancedCalculation(args);
        default -> {
            log.warn("Unknown function: {}", functionName);
            yield null;
        }
    };
}

private Object customFunction(Object[] args) {
    // Validate argument count
    if (args.length != 2) {
        throw new ASTException("custom_function requires exactly 2 arguments, got " + args.length);
    }

    // Type validation
    if (!(args[0] instanceof Number) || !(args[1] instanceof Number)) {
        throw new ASTException("custom_function requires numeric arguments");
    }

    double arg1 = ((Number) args[0]).doubleValue();
    double arg2 = ((Number) args[1]).doubleValue();

    // Your custom logic
    return Math.pow(arg1, 2) + Math.sqrt(arg2);
}
```

#### **Step 2: Add Function to Action Executor**

If your function can be used in actions:

```java
// In ActionExecutor.java
@Override
public Void visitFunctionCallAction(FunctionCallAction node) {
    String functionName = node.getFunctionName();
    Object[] args = evaluateArguments(node.getArguments());

    Object result = switch (functionName.toLowerCase()) {
        // ... existing functions
        case "custom_function" -> expressionEvaluator.customFunction(args);
        default -> throw new ASTException("Unknown function: " + functionName);
    };

    // Store result if needed
    if (node.getResultVariable() != null) {
        context.setComputedVariable(node.getResultVariable(), result);
    }

    return null;
}
```

#### **Step 3: Add Validation**

Update validation to recognize your function:

```java
// In ValidationVisitor.java
private static final Set<String> KNOWN_FUNCTIONS = Set.of(
    "abs", "round", "ceil", "floor", "max", "min",
    "length", "substring", "upper", "lower",
    "custom_function", "advanced_calculation"  // Add your functions
);

@Override
public List<ValidationError> visitFunctionCallExpression(FunctionCallExpression node) {
    List<ValidationError> errors = new ArrayList<>();

    // Validate function exists
    if (!KNOWN_FUNCTIONS.contains(node.getFunctionName().toLowerCase())) {
        errors.add(new ValidationError(
            "Unknown function: " + node.getFunctionName(),
            "UNKNOWN_FUNCTION",
            node.getLocationString()
        ));
    }

    // Validate arguments
    if (node.hasArguments()) {
        for (Expression arg : node.getArguments()) {
            errors.addAll(arg.accept(this));
        }
    }

    return errors;
}
```

### 🔧 **Adding New AST Node Types**

#### **Step 1: Create the Node Class**

```java
// Create new file: CustomExpression.java
package org.fireflyframework.rules.core.dsl.ast.expression;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomExpression extends Expression {

    private final String customProperty;
    private final Expression innerExpression;
    private final List<Expression> parameters;

    public CustomExpression(SourceLocation location, String customProperty,
                          Expression innerExpression, List<Expression> parameters) {
        super(location);
        this.customProperty = customProperty;
        this.innerExpression = innerExpression;
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        return visitor.visitCustomExpression(this);
    }

    @Override
    public ExpressionType getExpressionType() {
        // Determine based on custom property
        return switch (customProperty.toLowerCase()) {
            case "numeric_transform" -> ExpressionType.NUMBER;
            case "boolean_check" -> ExpressionType.BOOLEAN;
            default -> ExpressionType.UNKNOWN;
        };
    }

    @Override
    public boolean hasVariableReferences() {
        boolean hasRefs = innerExpression.hasVariableReferences();
        for (Expression param : parameters) {
            hasRefs |= param.hasVariableReferences();
        }
        return hasRefs;
    }

    @Override
    public String toDebugString() {
        return String.format("CustomExpression(property=%s, inner=%s, params=%s)",
                           customProperty, innerExpression.toDebugString(),
                           parameters.stream().map(Expression::toDebugString).toList());
    }
}
```

#### **Step 2: Update ASTVisitor Interface**

```java
// In ASTVisitor.java
public interface ASTVisitor<T> {
    // ... existing methods
    T visitCustomExpression(CustomExpression node);
}
```

#### **Step 3: Implement Visitor Methods**

```java
// In ExpressionEvaluator.java
@Override
public Object visitCustomExpression(CustomExpression node) {
    // Evaluate inner expression and parameters
    Object innerValue = node.getInnerExpression().accept(this);
    Object[] paramValues = node.getParameters().stream()
        .map(param -> param.accept(this))
        .toArray();

    // Apply custom logic based on property
    return switch (node.getCustomProperty().toLowerCase()) {
        case "numeric_transform" -> applyNumericTransform(innerValue, paramValues);
        case "boolean_check" -> applyBooleanCheck(innerValue, paramValues);
        default -> throw new ASTException("Unknown custom property: " + node.getCustomProperty());
    };
}
```

## Best Practices

### 🎯 **Development Guidelines**

#### **1. Type Safety**
- Always validate argument types in functions and operators
- Use proper type conversion with error handling
- Provide clear error messages for type mismatches
- Leverage the `ExpressionType` enum for type checking

#### **2. Performance Considerations**
- Avoid expensive operations in frequently called functions
- Consider caching for complex calculations
- Use lazy evaluation where appropriate
- Profile performance with realistic data volumes

#### **3. Error Handling**
- Throw `ASTException` for AST-related errors
- Include context information in error messages
- Provide suggestions for fixing errors when possible
- Use proper error codes for categorization

#### **4. Testing Strategy**
- Write comprehensive unit tests for all extensions
- Include edge cases and error scenarios
- Test integration with existing AST components
- Verify validation rules work correctly
- Use property-based testing for complex operators

#### **5. Documentation**
- Document all new operators, functions, and node types
- Provide examples in YAML DSL format
- Include performance characteristics and limitations
- Update the YAML DSL reference documentation

#### **6. Code Organization**
- Follow the existing package structure
- Use consistent naming conventions
- Implement proper equals/hashCode for AST nodes
- Maintain immutability of AST nodes after construction

### 🔧 **Extension Checklist**

When adding new functionality to the AST system, use this checklist:

- [ ] **Token Type**: Added to `TokenType.java` if needed
- [ ] **Lexer**: Updated to recognize new tokens
- [ ] **Parser**: Updated to parse new syntax
- [ ] **AST Node**: Created new node class if needed
- [ ] **Visitor Interface**: Updated with new visit methods
- [ ] **Expression Evaluator**: Implemented evaluation logic
- [ ] **Action Executor**: Implemented execution logic if applicable
- [ ] **Validation**: Added validation rules
- [ ] **Error Handling**: Proper exception handling
- [ ] **Tests**: Comprehensive test coverage
- [ ] **Documentation**: Updated documentation
- [ ] **Performance**: Considered performance implications

### 📚 **Resources**

- **Source Code**: `fireflyframework-rule-engine-core/src/main/java/com/firefly/rules/core/dsl/ast/`
- **Tests**: `fireflyframework-rule-engine-core/src/test/java/com/firefly/rules/core/dsl/`
- **Documentation**: `docs/yaml-dsl-reference.md`
- **Examples**: `docs/my-first-rule-design.md`

This comprehensive guide provides everything needed to understand, use, and extend the Firefly Framework Rule Engine's AST system. The modular architecture and visitor pattern make it straightforward to add new functionality while maintaining type safety, performance, and reliability.
```
