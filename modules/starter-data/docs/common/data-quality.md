# Data Quality

Data that comes back from external enrichment providers or transformation pipelines is not always correct, complete, or consistent. A field might be null when it should not be, an email address might be malformed, or a numeric score might fall outside an expected range. If you persist or act on bad data without catching these issues, the problems compound downstream and become much harder to trace.

The data quality framework in `fireflyframework-starter-data` gives you a structured way to define validation rules and run them against your data at any point in the pipeline. You express each concern -- "email must not be null," "score must be between 0 and 1" -- as an individual rule with a severity level. The framework's engine evaluates all applicable rules and produces a report telling you exactly what passed, what failed, and how severe each failure is.

A common pattern is to use quality rules as a "gate" between enrichment and persistence: enrich the data, validate it, and only proceed if the critical checks pass. The framework supports both fail-fast evaluation (stop on the first critical failure) and collect-all evaluation (run every rule and report everything). Quality events are also published through Spring's event system, so you can wire up audit logging, alerting, or dashboards without coupling them to your validation logic.

This document provides a comprehensive guide to the data quality framework in `fireflyframework-starter-data`, covering rule definition, engine evaluation, built-in rules, custom rules, and integration with Spring events.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Severity Levels](#severity-levels)
- [Evaluation Strategies](#evaluation-strategies)
- [Built-in Rules](#built-in-rules)
- [Creating Custom Rules](#creating-custom-rules)
- [Using DataQualityEngine Programmatically](#using-dataqualityengine-programmatically)
- [QualityReport and QualityResult](#qualityreport-and-qualityresult)
- [Integration with Spring Events](#integration-with-spring-events)
- [Example: Quality Gate for Enrichment Output](#example-quality-gate-for-enrichment-output)
- [Best Practices](#best-practices)

---

## Overview

The data quality framework provides a rule-based validation system for ensuring data correctness, completeness, and consistency as records flow through enrichment and transformation pipelines. It is built around three core abstractions:

- **DataQualityRule** -- a port interface representing a single validation concern
- **DataQualityEngine** -- an engine that evaluates rules against a data object and produces a report
- **QualityReport** -- an aggregated result summarizing pass/fail counts and individual rule outcomes

The framework follows the hexagonal architecture pattern: rules are ports (abstractions), and built-in rules are adapters (implementations) that can be swapped or extended.

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                  DataQualityEngine                      │
│                                                        │
│   evaluate(data, strategy) -> Mono<QualityReport>      │
│                                                        │
│   ┌──────────────────────────────────────────────┐     │
│   │         DataQualityRule<T> (Port)             │     │
│   │                                              │     │
│   │  evaluate(T data) -> QualityResult           │     │
│   │  getRuleName() -> String                     │     │
│   │  getSeverity() -> QualitySeverity            │     │
│   └──────────┬───────────────────────────────────┘     │
│              │                                         │
│   ┌──────────┴───────────────────────────────────┐     │
│   │           Built-in Adapters                  │     │
│   │  ┌──────────────┐ ┌──────────────┐           │     │
│   │  │ NotNullRule   │ │ PatternRule  │           │     │
│   │  └──────────────┘ └──────────────┘           │     │
│   │  ┌──────────────┐ ┌──────────────┐           │     │
│   │  │  RangeRule    │ │ CustomRule   │           │     │
│   │  └──────────────┘ └──────────────┘           │     │
│   └──────────────────────────────────────────────┘     │
│                                                        │
│   ──── publishes ──> DataQualityEvent                  │
└────────────────────────────────────────────────────────┘
```

**Key classes:**

| Class | Package | Role |
|---|---|---|
| `DataQualityRule<T>` | `o.f.data.quality` | Port interface for a single validation rule |
| `DataQualityEngine` | `o.f.data.quality` | Evaluates rules against data, produces `QualityReport` |
| `QualityResult` | `o.f.data.quality` | Result of evaluating a single rule |
| `QualityReport` | `o.f.data.quality` | Aggregated report with pass/fail statistics |
| `QualitySeverity` | `o.f.data.quality` | Enum: `INFO`, `WARNING`, `CRITICAL` |
| `QualityStrategy` | `o.f.data.quality` | Enum: `FAIL_FAST`, `COLLECT_ALL` |
| `NotNullRule<T>` | `o.f.data.quality.rules` | Validates a field is not null |
| `PatternRule<T>` | `o.f.data.quality.rules` | Validates a string field matches a regex |
| `RangeRule<T>` | `o.f.data.quality.rules` | Validates a comparable field is within a range |
| `DataQualityAutoConfiguration` | `o.f.data.config` | Auto-configures the engine from Spring beans |
| `DataQualityEvent` | `o.f.data.event` | Spring event published after each evaluation |

---

## Configuration

Data quality is enabled by default. The auto-configuration is controlled by a single property:

```yaml
firefly:
  data:
    quality:
      enabled: true   # default: true (enabled even when not explicitly set)
```

When enabled, `DataQualityAutoConfiguration` discovers all `DataQualityRule<?>` beans in the Spring context and wires them into a `DataQualityEngine` bean. If an `ApplicationEventPublisher` is available, quality report events are published automatically.

To disable data quality entirely:

```yaml
firefly:
  data:
    quality:
      enabled: false
```

> **See also:** [Configuration Guide](configuration.md) for the full property reference.

---

## Severity Levels

The `QualitySeverity` enum defines three levels of rule violation impact:

| Severity | Report Behavior | Description |
|---|---|---|
| `INFO` | Report still passes | Informational finding; does not affect the overall validation outcome |
| `WARNING` | Report still passes | A potential issue worth noting; does not cause the report to fail |
| `CRITICAL` | Report fails | A severe violation that causes the `QualityReport.passed` flag to be `false` |

The default severity for a rule is `WARNING` (set in the `DataQualityRule` interface default method). Only `CRITICAL` failures cause the report's `passed` field to be `false`.

---

## Evaluation Strategies

The `QualityStrategy` enum controls how the `DataQualityEngine` processes rules:

### FAIL_FAST

Stops evaluation on the first `CRITICAL` failure. Use this strategy when:

- You need early termination for performance
- Downstream processing depends on critical constraints
- You want to surface the most important failure first

```java
engine.evaluate(data, QualityStrategy.FAIL_FAST)
    .subscribe(report -> {
        // report may contain fewer results than total rules
    });
```

### COLLECT_ALL

Runs every registered rule regardless of failures. Use this strategy when:

- You want a comprehensive quality audit
- You need to display all violations to the user
- You are generating a quality dashboard or report

```java
engine.evaluate(data, QualityStrategy.COLLECT_ALL)
    .subscribe(report -> {
        report.getFailures().forEach(failure ->
            log.warn("Quality issue: {}", failure.getMessage())
        );
    });
```

---

## Built-in Rules

The framework ships three built-in rule implementations in the `o.f.data.quality.rules` package. Each accepts a field name, an extractor function, and an optional severity override.

### NotNullRule

Validates that a field extracted from the data object is not null.

```java
// Default severity (WARNING)
DataQualityRule<Customer> rule = new NotNullRule<>(
    "email",
    Customer::getEmail
);

// With CRITICAL severity
DataQualityRule<Customer> criticalRule = new NotNullRule<>(
    "customerId",
    Customer::getId,
    QualitySeverity.CRITICAL
);
```

**Rule name format:** `not-null:<fieldName>` (e.g., `not-null:email`)

**Failure message:** `"<fieldName> must not be null"`

### PatternRule

Validates that a string field matches a compiled regular expression pattern.

```java
import java.util.regex.Pattern;

// Email format validation
DataQualityRule<Customer> emailRule = new PatternRule<>(
    "email",
    Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$"),
    Customer::getEmail
);

// Phone number with CRITICAL severity
DataQualityRule<Customer> phoneRule = new PatternRule<>(
    "phone",
    Pattern.compile("^\\+?[1-9]\\d{1,14}$"),
    Customer::getPhone,
    QualitySeverity.CRITICAL
);
```

**Rule name format:** `pattern:<fieldName>` (e.g., `pattern:email`)

**Failure message:** `"<fieldName> does not match pattern: <regex>"`

**Note:** If the extracted value is null, the rule fails (null does not match any pattern).

### RangeRule

Validates that a `Comparable` field falls within a minimum/maximum range (inclusive). Either bound can be `null` to make the range open-ended.

```java
// Age between 0 and 150
DataQualityRule<Customer> ageRule = new RangeRule<>(
    "age",
    0, 150,
    customer -> customer.getAge()
);

// Score at least 0.0, no upper bound
DataQualityRule<Metric> scoreRule = new RangeRule<>(
    "score",
    0.0, null,
    Metric::getScore,
    QualitySeverity.CRITICAL
);
```

**Rule name format:** `range:<fieldName>` (e.g., `range:age`)

**Failure message:** `"<fieldName> value <actual> is outside range [<min>, <max>]"`

**Note:** If the extracted value is null, the rule fails with a null-specific message.

---

## Creating Custom Rules

Implement the `DataQualityRule<T>` interface to create domain-specific validation rules.

```java
import org.fireflyframework.data.quality.DataQualityRule;
import org.fireflyframework.data.quality.QualityResult;
import org.fireflyframework.data.quality.QualitySeverity;

public class EmailFormatRule implements DataQualityRule<Customer> {

    @Override
    public QualityResult evaluate(Customer customer) {
        String email = customer.getEmail();
        if (email != null && email.contains("@")) {
            return QualityResult.pass(getRuleName());
        }
        return QualityResult.fail(getRuleName(), getSeverity(), "Invalid email format");
    }

    @Override
    public String getRuleName() {
        return "email-format";
    }

    @Override
    public QualitySeverity getSeverity() {
        return QualitySeverity.CRITICAL;
    }
}
```

### Registering Custom Rules as Spring Beans

Declare your rules as `@Bean` definitions so that `DataQualityAutoConfiguration` discovers them:

```java
@Configuration
public class QualityRulesConfig {

    @Bean
    public DataQualityRule<Customer> emailFormatRule() {
        return new EmailFormatRule();
    }

    @Bean
    public DataQualityRule<Customer> customerIdNotNull() {
        return new NotNullRule<>("customerId", Customer::getId, QualitySeverity.CRITICAL);
    }

    @Bean
    public DataQualityRule<Customer> emailNotNull() {
        return new NotNullRule<>("email", Customer::getEmail);
    }
}
```

---

## Using DataQualityEngine Programmatically

The `DataQualityEngine` can be injected and used directly in any service. The `evaluate` method returns a `Mono<QualityReport>`, making it compatible with reactive pipelines.

### Injecting the Auto-configured Engine

```java
@Service
public class EnrichmentValidator {

    private final DataQualityEngine qualityEngine;

    public EnrichmentValidator(DataQualityEngine qualityEngine) {
        this.qualityEngine = qualityEngine;
    }

    public Mono<Void> validateAndProcess(Customer customer) {
        return qualityEngine.evaluate(customer, QualityStrategy.COLLECT_ALL)
            .flatMap(report -> {
                if (report.isPassed()) {
                    return processEnrichedData(customer);
                } else {
                    log.warn("Quality check failed: {} of {} rules failed",
                        report.getFailedRules(), report.getTotalRules());
                    return Mono.error(new QualityException(report));
                }
            });
    }
}
```

### Creating an Engine Manually

For standalone usage without Spring auto-configuration:

```java
List<DataQualityRule<?>> rules = List.of(
    new NotNullRule<>("name", Customer::getName, QualitySeverity.CRITICAL),
    new PatternRule<>("email",
        Pattern.compile("^.+@.+\\..+$"), Customer::getEmail),
    new RangeRule<>("age", 0, 150, c -> ((Customer) c).getAge())
);

DataQualityEngine engine = new DataQualityEngine(rules);

engine.evaluate(customer, QualityStrategy.COLLECT_ALL)
    .subscribe(report -> System.out.println("Passed: " + report.isPassed()));
```

---

## QualityReport and QualityResult

### QualityResult

The result of evaluating a single rule. Created via factory methods:

```java
// Passing result
QualityResult passed = QualityResult.pass("my-rule");

// Failing result
QualityResult failed = QualityResult.fail("my-rule", QualitySeverity.CRITICAL, "Value is invalid");

// Builder for detailed results
QualityResult detailed = QualityResult.builder()
    .ruleName("my-rule")
    .passed(false)
    .severity(QualitySeverity.WARNING)
    .message("Field exceeds maximum length")
    .fieldName("description")
    .actualValue("very long string...")
    .build();
```

**Fields:**

| Field | Type | Description |
|---|---|---|
| `ruleName` | `String` | Unique identifier of the rule |
| `passed` | `boolean` | Whether the rule passed |
| `severity` | `QualitySeverity` | Severity level of the result |
| `message` | `String` | Human-readable description of the failure |
| `fieldName` | `String` | Name of the field that was validated (optional) |
| `actualValue` | `Object` | The actual value that was evaluated (optional) |

### QualityReport

Aggregated report produced by the engine after evaluating all applicable rules.

**Fields:**

| Field | Type | Description |
|---|---|---|
| `passed` | `boolean` | `true` if no `CRITICAL` failures were found |
| `totalRules` | `int` | Total number of rules evaluated |
| `passedRules` | `int` | Number of rules that passed |
| `failedRules` | `int` | Number of rules that failed |
| `results` | `List<QualityResult>` | All individual rule results |
| `timestamp` | `Instant` | When the evaluation was performed |

**Convenience methods:**

```java
// Get only failures
List<QualityResult> failures = report.getFailures();

// Filter by severity
List<QualityResult> criticals = report.getBySeverity(QualitySeverity.CRITICAL);
List<QualityResult> warnings = report.getBySeverity(QualitySeverity.WARNING);
```

---

## Integration with Spring Events

When the `DataQualityEngine` is configured with an `ApplicationEventPublisher` (which happens automatically via auto-configuration), it publishes a `DataQualityEvent` after every evaluation.

### DataQualityEvent

```java
package org.fireflyframework.data.event;

public class DataQualityEvent {
    private final QualityReport report;
    private final Instant timestamp;
}
```

### Listening for Quality Events

Use Spring's `@EventListener` to react to quality evaluations:

```java
@Component
public class QualityAuditor {

    @EventListener
    public void onQualityCheck(DataQualityEvent event) {
        QualityReport report = event.getReport();

        if (!report.isPassed()) {
            log.error("Data quality check FAILED: {}/{} rules failed",
                report.getFailedRules(), report.getTotalRules());

            report.getFailures().forEach(failure ->
                log.error("  [{}] {} - {}",
                    failure.getSeverity(),
                    failure.getRuleName(),
                    failure.getMessage())
            );
        } else {
            log.info("Data quality check passed: {}/{} rules passed",
                report.getPassedRules(), report.getTotalRules());
        }
    }
}
```

### Use Cases for Event Listeners

- **Audit logging** -- persist quality results for compliance
- **Metrics** -- publish pass/fail counts to Prometheus or Micrometer
- **Alerting** -- send notifications on critical failures
- **Dashboard** -- aggregate quality trends over time

---

## Example: Quality Gate for Enrichment Output

A common pattern is validating enriched data before persisting it:

```java
@Service
public class EnrichmentPipeline {

    private final DataEnricher enricher;
    private final DataQualityEngine qualityEngine;

    public EnrichmentPipeline(DataEnricher enricher, DataQualityEngine qualityEngine) {
        this.enricher = enricher;
        this.qualityEngine = qualityEngine;
    }

    public Mono<Customer> enrichAndValidate(Customer customer) {
        return enricher.enrich(customer)
            .flatMap(enriched ->
                qualityEngine.evaluate(enriched, QualityStrategy.COLLECT_ALL)
                    .flatMap(report -> {
                        if (report.isPassed()) {
                            return Mono.just(enriched);
                        }

                        List<QualityResult> criticals =
                            report.getBySeverity(QualitySeverity.CRITICAL);
                        log.error("Enrichment output failed {} critical quality rules",
                            criticals.size());

                        return Mono.error(new EnrichmentQualityException(
                            "Enrichment produced invalid data", report));
                    })
            );
    }
}
```

**Configuration for the quality gate rules:**

```java
@Configuration
public class EnrichmentQualityRules {

    @Bean
    public DataQualityRule<Customer> enrichedNameNotNull() {
        return new NotNullRule<>("name", Customer::getName, QualitySeverity.CRITICAL);
    }

    @Bean
    public DataQualityRule<Customer> enrichedEmailFormat() {
        return new PatternRule<>(
            "email",
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$"),
            Customer::getEmail,
            QualitySeverity.WARNING
        );
    }

    @Bean
    public DataQualityRule<Customer> enrichedScoreRange() {
        return new RangeRule<>(
            "enrichmentScore",
            0.0, 1.0,
            customer -> customer.getEnrichmentScore(),
            QualitySeverity.CRITICAL
        );
    }
}
```

---

## Best Practices

1. **Set appropriate severity levels.** Reserve `CRITICAL` for fields that absolutely must pass (e.g., primary keys, required references). Use `WARNING` for desirable but non-blocking validations. Use `INFO` for advisory checks.

2. **Use COLLECT_ALL for auditing.** When generating quality reports or dashboards, use `COLLECT_ALL` so you see every issue. Reserve `FAIL_FAST` for hot paths where early termination saves processing time.

3. **Register rules as Spring beans.** Let auto-configuration discover rules automatically rather than constructing the engine manually. This ensures event publishing works and keeps configuration centralized.

4. **Name rules consistently.** The built-in rules use the format `<type>:<fieldName>` (e.g., `not-null:email`, `pattern:phone`). Follow this convention for custom rules so log messages and reports are easy to parse.

5. **Listen for DataQualityEvents.** Use Spring event listeners to decouple quality monitoring from validation logic. This keeps your validation code focused on pass/fail decisions and lets separate components handle alerting and metrics.

6. **Validate after enrichment, not just before.** Enrichment can introduce invalid data (e.g., unexpected null fields from a failed provider). Add a quality gate between enrichment and persistence.

7. **Compose granular rules.** Prefer many small, single-concern rules over monolithic validators. This makes reports more actionable and rules more reusable.

8. **Use QualityResult.builder() for rich failure context.** Include `fieldName` and `actualValue` in failure results so downstream consumers can pinpoint exactly what failed and why.

> **See also:** [Data Lineage](data-lineage.md) | [Data Transformation](data-transformation.md) | [GenAI Bridge](genai-bridge.md)
