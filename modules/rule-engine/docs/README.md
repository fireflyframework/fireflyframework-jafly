# Firefly Framework Rule Engine Documentation

**Complete documentation for the Firefly Framework Rule Engine - part of the Firefly Framework**

*Apache 2.0 Licensed | Production-Ready | AST-Based YAML DSL*

---

## 📚 Documentation Overview

This documentation provides comprehensive guidance for using the Firefly Framework Rule Engine, from quick start tutorials to advanced patterns and complete API references.

### 🎯 Choose Your Learning Path

| Your Goal | Start Here | Time Required |
|-----------|------------|---------------|
| **Learn the basics** | [Quick Start Guide](quick-start-guide.md) | 15 minutes |
| **See real examples** | [Common Patterns Guide](common-patterns-guide.md) | 30 minutes |
| **Set team standards** | [Governance Guidelines](governance-guidelines.md) | 20 minutes |
| **Complete reference** | [YAML DSL Reference](yaml-dsl-reference.md) | Reference |
| **API integration** | [API Documentation](api-documentation.md) | Reference |

---

## 📖 Documentation Structure

### 🚀 Getting Started

#### [Quick Start Guide](quick-start-guide.md)
*Perfect for newcomers - learn the essentials in 15 minutes*

- Your first rule in 5 minutes
- Essential syntax patterns
- Variable naming conventions
- Common beginner examples
- When to use simple vs. complex syntax

**Best for**: New users, proof of concepts, quick prototypes

#### [Common Patterns Guide](common-patterns-guide.md)
*Real-world examples organized by complexity*

- **🟢 Basic Patterns**: Simple approval logic, data validation
- **🟡 Intermediate Patterns**: Multi-factor scoring, sequential processing
- **🔴 Advanced Patterns**: API integration, complex workflows

**Best for**: Finding the right approach for your use case

### 🏛️ Team Management

#### [Governance Guidelines](governance-guidelines.md)
*Establish team standards and best practices*

- Team maturity assessment
- Feature usage guidelines
- Complexity management strategies
- Risk assessment framework
- Organizational standards templates

**Best for**: Technical leads, architects, team coordination

### 📚 Complete References

#### [YAML DSL Reference](yaml-dsl-reference.md)
*Complete syntax documentation - 50+ pages*

- All operators and functions
- Variable naming conventions
- Complex syntax features
- Reserved keywords
- Complete examples

**Best for**: Comprehensive syntax lookup, advanced features

#### [API Documentation](api-documentation.md)
*Complete REST API reference*

- Rules evaluation endpoints
- Rule definition management
- Constants management
- Validation API
- Audit trail API
- Batch processing

**Best for**: API integration, system architecture

---

## 🎯 Quick Navigation

### By Use Case

| I want to... | Go to... |
|--------------|----------|
| Write my first rule | [Quick Start Guide](quick-start-guide.md) |
| Find examples for credit scoring | [Common Patterns Guide](common-patterns-guide.md) |
| Set up team standards | [Governance Guidelines](governance-guidelines.md) |
| Look up a specific operator | [YAML DSL Reference](yaml-dsl-reference.md) |
| Integrate with our API | [API Documentation](api-documentation.md) |
| Understand variable naming | [Quick Start Guide - Variable Naming](quick-start-guide.md#variable-naming-rules) |
| See complex workflow examples | [Common Patterns Guide - Advanced](common-patterns-guide.md#-advanced-patterns) |
| Validate YAML syntax | [API Documentation - Validation](api-documentation.md#validation-api) |

### By Experience Level

| Experience Level | Recommended Reading Order |
|------------------|---------------------------|
| **New to Rule Engines** | Quick Start → Common Patterns (Basic) → Governance Guidelines |
| **Some Experience** | Common Patterns → Governance Guidelines → YAML DSL Reference |
| **Advanced Users** | Governance Guidelines → YAML DSL Reference → API Documentation |

---

## 🔧 Technical Architecture

### Rule Engine Core Features

- **AST-Based Parser**: Strongly-typed Abstract Syntax Tree parsing
- **YAML DSL**: Human-readable domain-specific language
- **Variable Types**: Strict naming conventions (camelCase, snake_case, UPPER_CASE)
- **REST API**: Comprehensive evaluation and management endpoints
- **Audit Trail**: Complete operation tracking and logging
- **Batch Processing**: Concurrent rule evaluation capabilities
- **Circuit Breaker**: Resilience patterns for external service calls

### Supported Operations

| Category | Examples |
|----------|----------|
| **Comparisons** | `at_least`, `greater_than`, `equals`, `between`, `in_list` |
| **Actions** | `set`, `calculate`, `append`, `prepend`, `remove` |
| **Functions** | `json_get`, `rest_get`, `max`, `min`, `format_date` |
| **Validation** | `is_email`, `is_phone`, `is_credit_score`, `exists` |
| **Logic** | `and`, `or`, `not`, `if/then` conditions |

---

## 🚀 Getting Started Checklist

### For Developers
- [ ] Read [Quick Start Guide](quick-start-guide.md)
- [ ] Try the basic examples
- [ ] Review [variable naming conventions](quick-start-guide.md#variable-naming-rules)
- [ ] Explore [Common Patterns](common-patterns-guide.md) for your use case

### For Technical Leads
- [ ] Review [Governance Guidelines](governance-guidelines.md)
- [ ] Assess team maturity level
- [ ] Establish feature usage standards
- [ ] Set up code review guidelines

### For Architects
- [ ] Study [API Documentation](api-documentation.md)
- [ ] Review [YAML DSL Reference](yaml-dsl-reference.md)
- [ ] Plan integration architecture
- [ ] Design audit and monitoring strategy

---

## 📋 Documentation Standards

### Accuracy Guarantee
All documentation is generated from and validated against the actual codebase implementation. Examples are tested and verified to work with the current system.

### Naming Conventions
- **Input Variables**: `camelCase` (from API requests)
- **System Constants**: `UPPER_CASE_WITH_UNDERSCORES` (from database)
- **Computed Variables**: `snake_case` (created during execution)

### Syntax Standards
- All examples use actual implemented operators (`at_least` not `>=`)
- Function calls match real implementation
- Output definitions follow proper YAML structure

---

## 🔗 External Resources

- **GitHub Repository**: [fireflyframework/fireflyframework-rule-engine](https://github.com/fireflyframework/fireflyframework-rule-engine)
- **License**: Apache 2.0
- **Firefly Platform**: [Firefly Framework](https://firefly.software)

---

## 📞 Support & Contribution

### Getting Help
1. Check the appropriate documentation section above
2. Review [Common Patterns](common-patterns-guide.md) for similar use cases
3. Consult [YAML DSL Reference](yaml-dsl-reference.md) for syntax questions

### Contributing
- Documentation improvements welcome
- All examples must be tested against actual implementation
- Follow established naming conventions and syntax standards

---

*Last Updated: September 2025 | Version: 1.0.0*
