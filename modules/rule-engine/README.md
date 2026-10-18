# Firefly Framework - Rule Engine

[![CI](https://github.com/fireflyframework/fireflyframework-rule-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/fireflyframework/fireflyframework-rule-engine/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

> A reactive, YAML-DSL business rule engine for Spring Boot — author rules as human-readable YAML, compile them to an AST, and evaluate them at runtime with caching, audit trails, and optional Python compilation.

---

## Table of Contents

- [Overview](#overview)
  - [Modules](#modules)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
  - [Embedded library usage](#embedded-library-usage)
  - [Stored rules via the service layer](#stored-rules-via-the-service-layer)
  - [REST API](#rest-api)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

Firefly Framework Rule Engine is a reactive business-rules platform for the [Firefly Framework](https://github.com/fireflyframework). Rules are authored in a purpose-built **YAML DSL** that reads like plain English (`when: [creditScore >= 650]`, `then: [set status to "APPROVED"]`) and are parsed into an **Abstract Syntax Tree (AST)** for fast, repeatable evaluation. The engine externalizes volatile business logic — credit scoring, eligibility, pricing, fee waivers, risk tiers — out of compiled application code so it can change without a redeploy.

The DSL is not a thin `if/else` wrapper. It ships with its own lexer, recursive-descent parser, and visitor-based evaluator supporting comparison/logical/expression conditions, arithmetic and unary expressions, literals and variables, JsonPath extraction, list operations, loop constructs (`while`, `do-while`, `for-each`), function calls, outbound REST calls, and a circuit-breaker action for resilient evaluation. Every evaluation is fully reactive (Project Reactor `Mono`/`Flux`) and non-blocking end to end.

Beyond raw evaluation, the engine provides rule-definition CRUD persisted via R2DBC, a shared **constants** store for values referenced across rules, **batch evaluation** for high-throughput scoring, an **audit trail** capturing every evaluation, **YAML validation** (syntax + naming conventions) exposed as its own endpoint, and **Python code generation** that compiles a rule to an equivalent Python function for offline execution or external-runtime portability. Rule definitions, parsed ASTs, constants, and validation results are cached through the Firefly cache abstraction (Caffeine by default, Redis-pluggable for distributed deployments).

Where it sits in the framework: the engine builds on `fireflyframework-kernel`, `fireflyframework-cache` (caching), `fireflyframework-r2dbc` (persistence), `fireflyframework-validators`/`fireflyframework-utils` (interfaces), and `fireflyframework-web` (REST). It is consumed by domain- and experience-tier microservices that need configurable decisioning. The repository is published as a multi-module build so consumers can embed only the layers they need.

### Modules

This is an aggregator (`pom` packaging) over five published submodules:

| Module | Artifact | Purpose |
| --- | --- | --- |
| Interfaces | `fireflyframework-rule-engine-interfaces` | Public API surface: request/response DTOs (evaluation, CRUD, audit, validation), enums (`ResultType`, `ValueType`), and bean-validation rules. No business logic. |
| Models | `fireflyframework-rule-engine-models` | R2DBC entities (`RuleDefinition`, `Constant`, `AuditTrail`), reactive repositories, and Flyway migrations for the PostgreSQL schema. |
| Core | `fireflyframework-rule-engine-core` | The engine itself: DSL lexer/parser/AST/evaluator, the service layer (evaluation, batch, definitions, constants, audit, JsonPath, REST-call), Python compiler, cache & observability auto-configuration, and MapStruct mappers. |
| Web | `fireflyframework-rule-engine-web` | Spring WebFlux REST controllers exposing evaluation, definitions, constants, batch, validation, audit, and Python-compilation endpoints, with springdoc OpenAPI, actuator, and Prometheus metrics. Also a runnable Spring Boot application. |
| SDK | `fireflyframework-rule-engine-sdk` | Generated OpenAPI client SDK for calling a deployed rule-engine service from other JVM applications. |

## Features

- **YAML DSL** with a dedicated lexer, recursive-descent parser, and `ASTNode`/`ASTVisitor` model — not a template language.
- **Condition types**: comparison, logical (AND/OR), and expression-based conditions; simple (`when`/`then`/`else`) and complex (`conditions: {if, then, else}`) syntax, plus multi-rule sequences.
- **Action types**: `set`, `calculate`, arithmetic, conditional branching, list operations, loops (`while`, `do-while`, `for-each`), function calls, `run`, and a circuit-breaker action.
- **Expression types**: arithmetic, binary, unary, literals, variables, function-call expressions, JsonPath extraction, and outbound REST calls.
- **Reactive end to end** via Project Reactor — `ASTRulesEvaluationEngine.evaluateRulesReactive(...)` returns a `Mono`.
- **Python compilation**: `PythonCodeGenerator` / `PythonCompilationService` emit an equivalent Python function for a rule, with its own caching and REST endpoints.
- **Batch evaluation** (`BatchRulesEvaluationService`) for scoring many input sets in one call, with statistics and health endpoints.
- **Rule-definition CRUD** persisted via R2DBC, addressable by id or by stable `code`.
- **Shared constants** store for values reused across rules, with defaults and CRUD.
- **Audit trail** of every evaluation, queryable by entity, user, or operation type, with cleanup and statistics.
- **YAML validation** endpoint reporting syntax errors and camelCase/naming-convention violations before a rule goes live.
- **Caching** of rule definitions, parsed ASTs, constants, and validation results through the Firefly cache abstraction (Caffeine default; Redis-pluggable).
- **Observability**: `RuleEngineMetrics` (Micrometer), actuator health/info, and Prometheus scrape endpoint.
- **OpenAPI / Swagger UI** for the full REST surface and a generated client SDK.

## Requirements

- Java 21+ (Java 25 recommended)
- Spring Boot 3.x
- Maven 3.9+
- PostgreSQL (for rule-definition, constant, and audit-trail persistence via R2DBC + Flyway)
- A Redis server only if you switch the cache provider from the default Caffeine to Redis for distributed evaluation

## Installation

All versions are managed by the Firefly parent/BOM — omit `<version>` once the parent is inherited (or the BOM imported), and declare only the modules you actually use.

**Embed the engine (DSL + services) in a service:**

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-core</artifactId>
    <!-- version managed by the Firefly parent / BOM -->
</dependency>
```

**Call a deployed rule-engine service from another application:**

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-sdk</artifactId>
    <!-- version managed by the Firefly parent / BOM -->
</dependency>
```

To have versions managed for you, inherit the Firefly parent:

```xml
<parent>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-parent</artifactId>
    <version>26.05.08</version>
</parent>
```

`fireflyframework-rule-engine-core` transitively brings in `fireflyframework-rule-engine-interfaces` and `fireflyframework-rule-engine-models`, so adding it is enough to author and evaluate rules. Add `fireflyframework-rule-engine-web` to expose the REST API.

## Quick Start

### Embedded library usage

The fastest path: inject `ASTRulesEvaluationEngine` and evaluate a YAML rule string directly — no database required.

```yaml
# credit-check.yaml — a rule authored in the YAML DSL
name: "Credit Eligibility"
description: "Decide loan eligibility from credit score and income"
inputs: [creditScore, annualIncome]
output: {approval_status: text, tier: text}

constants:
  - code: MIN_SCORE
    defaultValue: 650

when:
  - creditScore >= MIN_SCORE
  - annualIncome >= 50000
then:
  - set approval_status to "APPROVED"
  - set tier to "premium"
else:
  - set approval_status to "DECLINED"
  - set tier to "standard"
```

```java
@Service
public class CreditCheckService {

    private final ASTRulesEvaluationEngine engine;
    private final String ruleYaml; // load from classpath, DB, or config

    public CreditCheckService(ASTRulesEvaluationEngine engine,
                              @Value("classpath:credit-check.yaml") Resource rule) throws IOException {
        this.engine = engine;
        this.ruleYaml = rule.getContentAsString(StandardCharsets.UTF_8);
    }

    public Mono<ASTRulesEvaluationResult> evaluate(int creditScore, long annualIncome) {
        Map<String, Object> inputs = Map.of(
                "creditScore", creditScore,
                "annualIncome", annualIncome);
        return engine.evaluateRulesReactive(ruleYaml, inputs);
        // result.isSuccess(), result.getOutputData(), result.getExecutionTimeMs()
    }
}
```

### Stored rules via the service layer

Persist rule definitions and evaluate them by stable `code` (each evaluation is audited automatically):

```java
@Service
public class DecisionService {

    private final RulesEvaluationService evaluationService;

    public DecisionService(RulesEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    public Mono<RulesEvaluationResponseDTO> decide(Map<String, Object> inputData,
                                                   ServerWebExchange exchange) {
        RuleEvaluationByCodeRequestDTO request = new RuleEvaluationByCodeRequestDTO();
        request.setRuleDefinitionCode("credit-eligibility");
        request.setInputData(inputData);          // camelCase keys
        request.setIncludeDetails(true);
        return evaluationService.evaluateRuleByCodeWithAudit(request, exchange);
    }
}
```

### REST API

Run `fireflyframework-rule-engine-web` and the full surface is available under `/api/v1` (Swagger UI at `/swagger-ui.html`):

| Area | Base path |
| --- | --- |
| Evaluate (direct / plain YAML / by-code) | `POST /api/v1/rules/evaluate/{direct,plain,by-code}` |
| Rule definitions (CRUD + validate) | `/api/v1/rules/definitions` |
| Batch evaluation | `/api/v1/rules/batch/{evaluate,validate,statistics,health}` |
| Constants | `/api/v1/constants` |
| YAML validation | `/api/v1/validation/{yaml,syntax}` |
| Audit trails | `/api/v1/audit/trails` |
| Python compilation | `/api/v1/python/compile` |

```bash
curl -X POST http://localhost:8080/api/v1/rules/evaluate/by-code \
  -H 'Content-Type: application/json' \
  -d '{
        "ruleDefinitionCode": "credit-eligibility",
        "inputData": { "creditScore": 720, "annualIncome": 84000 },
        "includeDetails": true
      }'
```

## Configuration

The rule engine reads cache settings under the `firefly.rules.cache` prefix and uses standard Spring `spring.r2dbc` / `spring.flyway` properties for persistence. Caching defaults to Caffeine; the dedicated rule-engine cache manager (`RuleEngineCacheAutoConfiguration`) keys entries under `firefly:rules:engine`. The defaults below are the real values from the bundled `application.yaml`:

```yaml
firefly:
  rules:
    cache:
      provider: CAFFEINE          # CAFFEINE (default, local) or REDIS (distributed)
      caffeine:
        ast-cache:                # parsed AST models
          maximum-size: 1000
          expire-after-write: 2h
          expire-after-access: 30m
        constants-cache:
          maximum-size: 500
          expire-after-write: 15m
          expire-after-access: 5m
        rule-definitions-cache:
          maximum-size: 200
          expire-after-write: 10m
          expire-after-access: 3m
        validation-cache:
          maximum-size: 100
          expire-after-write: 5m
          expire-after-access: 2m
      # redis:                    # uncomment to use a distributed cache
      #   host: ${REDIS_HOST:localhost}
      #   port: ${REDIS_PORT:6379}
      #   password: ${REDIS_PASSWORD:}
      #   database: ${REDIS_DATABASE:0}
      #   timeout: 5s

spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 10
      max-size: 50
      min-idle: 5
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

Key properties:

| Property | Default | Description |
| --- | --- | --- |
| `firefly.rules.cache.provider` | `CAFFEINE` | Cache backend for definitions/ASTs/constants/validation. Set to `REDIS` for distributed rule evaluation across instances. |
| `firefly.rules.cache.caffeine.ast-cache.*` | size 1000, write 2h | Sizing/TTL for the parsed-AST cache (the hottest cache). |
| `firefly.rules.cache.caffeine.rule-definitions-cache.*` | size 200, write 10m | Cache of stored rule definitions loaded from the database. |
| `firefly.observability.metrics.enabled` | `true` | Gates registration of `RuleEngineMetrics` (Micrometer). |
| `spring.r2dbc.*` / `spring.flyway.*` | see above | PostgreSQL connection, pool sizing, and migration of the rule/constant/audit schema. |

The web module ships `dev`, `testing`, and `prod` profiles that tune pool sizing and cache capacity. Cache TTLs and pool settings have no hard-coded annotations — they are plain Spring config and can be overridden per environment or via environment variables.

## Documentation

The repository ships an extensive `docs/` set:

- [Quick Start Guide](docs/quick-start-guide.md)
- [Architecture](docs/architecture.md)
- [YAML DSL Reference](docs/yaml-dsl-reference.md)
- [API Documentation](docs/api-documentation.md)
- [Developer Guide](docs/developer-guide.md)
- [Configuration Examples](docs/configuration-examples.md)
- [Common Patterns Guide](docs/common-patterns-guide.md)
- [Inputs Section Guide](docs/inputs-section-guide.md)
- [Python Compilation Complete Guide](docs/python-compilation-complete-guide.md)
- [Performance Optimization](docs/performance-optimization.md)
- [Governance Guidelines](docs/governance-guidelines.md)
- [B2B Credit Scoring Tutorial](docs/b2b-credit-scoring-tutorial.md)

For the framework-wide picture, see the [Firefly Framework organization](https://github.com/fireflyframework) and its module catalog.

## Contributing

Contributions are welcome. Please read the [CONTRIBUTING.md](CONTRIBUTING.md) guide for details on our code of conduct, development process, and how to submit pull requests.

## License

Copyright 2024-2026 Firefly Software Foundation.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
