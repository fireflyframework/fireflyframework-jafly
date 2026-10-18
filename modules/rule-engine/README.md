![Jafly. Rule Engine Library](../../docs/site/assets/modules/rule-engine.svg)

# Rule Engine Library

Group the YAML rule evaluator, public DTOs, persistence models, HTTP service, and generated SDK.

Use rule-engine-core for embedded evaluation or rule-engine-web for the service API. Decide whether rules are supplied as YAML in a request or loaded from stored definitions, because their service and audit requirements differ.

[Module guide](../../docs/site/modules/rule-engine.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

A product may keep changeable onboarding rules in YAML instead of hard-coding every check in a handler. Choose embedded evaluation or the rule HTTP service, then define input data and validation for those rules.

## Choose a component

| Related component | Why it belongs nearby |
| --- | --- |
| [Rule Engine Core](../../docs/site/modules/rule-engine-core.md) | Parse YAML rule DSL into an AST, evaluate conditions/actions, and provide rule, constant, batch, and audit services. |
| [Rule Engine Interfaces](../../docs/site/modules/rule-engine-interfaces.md) | Share request/response DTOs, rule definitions, validation results, and value/result enums for the rule engine. |
| [Rule Engine Web](../../docs/site/modules/rule-engine-web.md) | Expose HTTP APIs for evaluation, stored rule definitions, constants, batch operations, validation, and audit trails. |
| [Rule Engine SDK](../../docs/site/modules/rule-engine-sdk.md) | Generate a reactive Java WebClient SDK for the rule engine HTTP API. |
| [Rule Engine Models](../../docs/site/modules/rule-engine-models.md) | Persist rule definitions, constants, and audit trails with reactive repositories. |

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

This artifact has `pom` packaging and groups child projects. Select a concrete child JAR for the application classpath.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-rule-engine-core</artifactId>
    <version>26.10.18</version>
</dependency>
```

## Quick start

The injection examples use Lombok `@RequiredArgsConstructor`. Add its provided dependency and annotation processor as described in [Constructor injection](../../docs/site/maven.md#constructor-injection).

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

### Embedded library usage

The fastest path: inject `ASTRulesEvaluationEngine` and evaluate a YAML rule string directly. No database required.

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DecisionService {

    private final RulesEvaluationService evaluationService;



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

This POM has no runtime configuration namespace. Configure the selected child libraries in the application that uses them.

## Continue the application

Use the [Rule Engine Core guide](../../docs/site/modules/rule-engine-core.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-rule-engine,:fireflyframework-rule-engine-core,:fireflyframework-rule-engine-interfaces,:fireflyframework-rule-engine-models,:fireflyframework-rule-engine-sdk,:fireflyframework-rule-engine-web -am verify
./mvnw -pl :fireflyframework-rule-engine,:fireflyframework-rule-engine-core,:fireflyframework-rule-engine-interfaces,:fireflyframework-rule-engine-models,:fireflyframework-rule-engine-sdk,:fireflyframework-rule-engine-web -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/rule-engine.md).
