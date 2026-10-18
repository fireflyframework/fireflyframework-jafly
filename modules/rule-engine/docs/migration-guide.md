# Migration Guide

A practical mapping from common rule-engine concepts to the Firefly Rule Engine DSL. The goal is to give you the **shortest path from a rule you've written in another engine to its Firefly equivalent**, and to make the trade-offs explicit so you know whether this engine is the right fit.

## Table of Contents

- [Where Firefly sits in the landscape](#where-firefly-sits-in-the-landscape)
- [Migrating from Drools (DRL)](#migrating-from-drools-drl)
- [Migrating from Easy Rules](#migrating-from-easy-rules)
- [Migrating from a hand-rolled if/else service](#migrating-from-a-hand-rolled-ifelse-service)
- [When Firefly is the wrong tool](#when-firefly-is-the-wrong-tool)

---

## Where Firefly sits in the landscape

Firefly Rule Engine is a **stateless expression-evaluation engine over a single
input map**. Each evaluation is an independent function call: parse a YAML rule,
feed it inputs, get outputs.

This is intentionally a smaller, more focused model than full-blown rule engines:

| Concept                              | Firefly                | Drools (KIE)              | Easy Rules            |
| ------------------------------------ | ---------------------- | -------------------------- | --------------------- |
| Rule format                          | YAML DSL               | DRL (Java-like)            | Java annotations / MVEL |
| State model                          | Stateless per eval     | Stateful KieSession + Working Memory | Stateless `Facts` |
| Rule chaining                        | Sub-rules within one eval + `invoke_rule` for cross-rule composition | Full forward-chaining inference | Priority-ordered list |
| Rule priority / salience             | Per sub-rule `priority: N` (drools-style)        | `salience N`              | `@Priority(N)` |
| Multi-fact joins                     | Not supported          | Native (LHS pattern matching) | Not directly |
| Decision tables                      | DMN-style `decision_table:` block with FIRST / COLLECT / ANY / UNIQUE | Native (DRT, spreadsheets) | Not directly |
| Per-rule timeout                     | Declarative `timeout: 5s` (Reactor `Mono.timeout()`) | KieSession-wide          | Not directly |
| Input defaults                       | Declared in `inputs:` block            | Not directly              | Not directly |
| Persistent fact base                 | Not supported          | KIE working memory         | Not supported         |
| External calls (REST/JSON) in rules  | Built-in (`rest_get`, `json_get`, etc.) | Plugin-based         | Custom actions        |
| Custom function extensions           | Spring `@Bean` registration | Imports + globals     | `RuleListener`        |
| Audit trail                          | Built-in               | Plugin                     | Listener interface    |
| Hot reload                           | DB-backed; replace rule definition | KieScanner       | App reload            |

If your rules are **"score / decide / classify based on this one payload"**, Firefly
is purpose-built. If your rules are **"find applications where the spouse's credit
history triggers something on the primary applicant's file"**, you want Drools.

---

## Migrating from Drools (DRL)

### Side-by-side: a credit-score-driven approval

**Drools (DRL):**

```drl
rule "Approve high credit"
when
    $a : Application(creditScore >= 700, annualIncome >= 50000)
then
    $a.setApprovalStatus("APPROVED");
    $a.setTier("STANDARD");
    update($a);
end

rule "Premium tier"
when
    $a : Application(creditScore >= 800, annualIncome >= 100000)
then
    $a.setTier("PRIME");
    update($a);
end
```

**Firefly (YAML):**

```yaml
name: "Application Approval"
inputs:
  creditScore: "number"
  annualIncome: "number"

when:
  - creditScore at_least 700
  - annualIncome at_least 50000

then:
  - set approval_status to "APPROVED"
  - run tier as if_else(creditScore at_least 800 and annualIncome at_least 100000, "PRIME", "STANDARD")

else:
  - set approval_status to "DECLINED"

output:
  approval_status: text
  tier: text
```

### Conceptual mapping

| Drools                                | Firefly                                                |
| ------------------------------------- | ------------------------------------------------------ |
| `KieSession` + `insert(fact)`         | Pass a `Map<String, Object>` of inputs                 |
| `rule "X" when ... then ... end`      | A `rules:` entry, or a top-level `when/then/else`      |
| LHS pattern matching `$a : Application(field op value)` | `when:` list of conditions on input variables |
| `update($a)`                          | Implicit -- variables in `EvaluationContext` are mutable for the rest of this evaluation |
| Salience / priority                   | Order of entries in the `rules:` list                  |
| `agendaGroup`, `ruleflow-group`       | Sub-rules within one evaluation; chained by output → input |
| Forward chaining (a `then` triggers another rule's `when`) | **Not supported** -- run multiple evaluations or use sub-rules with explicit variables |
| `accumulate` over multiple facts      | **Not supported** -- pass an already-aggregated value as an input |
| `query` / backward chaining           | **Not supported**                                      |
| Decision tables (DRT)                 | Express as `if/then/else` chains or many `rules:`      |
| Globals (`global Logger logger`)      | Constants (`UPPER_CASE`, loaded from DB)               |
| Imports / `function` blocks           | Spring beans implementing `RuleFunction` and registered with `CustomFunctionRegistry` |
| `kmodule.xml`                         | YAML rule stored as a `RuleDefinition` row in the database |

### Patterns that map cleanly

- **Single-fact decisions** -- direct translation.
- **Tiered scoring** with `if/then/else` chains -- direct translation.
- **Decision tables** -- DRL `RuleTable` blocks map to Firefly `decision_table:` with `FIRST`, `COLLECT`, `ANY`, or `UNIQUE` hit policies. See [yaml-dsl-reference.md](yaml-dsl-reference.md#decision-tables-dmn-style).
- **Salience / priority** -- DRL `salience` corresponds to Firefly's per-sub-rule `priority: N`. Higher priority evaluates first; ties preserve declaration order.
- **Rule composition** -- DRL `rule "X" extends "Y"` and modify-then-fire chains map to `invoke_rule("other_rule", "k1", v1, "k2", v2)` which returns the nested rule's output map.
- **External data calls** -- Drools requires plugin work; Firefly has `rest_get`, `json_get`, etc. built-in.
- **Per-rule budgets** -- where DRL relies on `KieSession`-level timeouts, Firefly accepts a `timeout: 5s` directive on each rule.

### Patterns that don't map

- **Cross-fact joins** -- no equivalent. Pre-compute the join into a single input.
- **Truth maintenance / retraction** -- no equivalent. Re-run with new inputs.
- **Backward chaining** -- no equivalent.

---

## Migrating from Easy Rules

Easy Rules is closer to Firefly in spirit (stateless, single-fact). The migration is
usually mechanical.

**Easy Rules (annotated Java):**

```java
@Rule(name = "Approve high credit", priority = 1)
public class ApproveHighCreditRule {

    @Condition
    public boolean when(@Fact("creditScore") int score,
                        @Fact("annualIncome") double income) {
        return score >= 700 && income >= 50000;
    }

    @Action
    public void then(Facts facts) {
        facts.put("approvalStatus", "APPROVED");
    }
}
```

**Firefly (YAML):**

```yaml
name: "Approve high credit"
inputs:
  creditScore: "number"
  annualIncome: "number"
when:
  - creditScore at_least 700
  - annualIncome at_least 50000
then:
  - set approval_status to "APPROVED"
else:
  - set approval_status to "DECLINED"
output:
  approval_status: text
```

### Conceptual mapping

| Easy Rules                            | Firefly                                                |
| ------------------------------------- | ------------------------------------------------------ |
| `Facts facts = new Facts(); facts.put(...)` | `Map<String, Object>` input map                 |
| `Rules` collection                    | A single multi-rule YAML or many YAML rule definitions |
| `@Rule(priority = N)`                 | Order in `rules:` list                                 |
| `RulesEngine` (e.g., `DefaultRulesEngine`) | `ASTRulesEvaluationEngine`                       |
| `RuleListener`                        | Built-in audit trail + `CustomFunctionRegistry`        |
| MVEL expression `creditScore > 700`   | `creditScore greater_than 700` (or `creditScore > 700`) |
| `@Action` method body                 | `then:` action list                                    |
| Composite rules                       | Sub-rules in the `rules:` block                        |

### What you gain

- No Java compilation step to deploy a new rule.
- Database-backed rule definitions with hot-replace.
- Built-in REST / JSON / cache layers.
- Build-time validation of every rule example in your docs.

### What you give up

- MVEL / arbitrary Java expression power -- the Firefly DSL is intentionally narrower
  to keep rules reviewable.
- Direct access to your domain objects from inside a rule -- the engine sees only
  the input map.

---

## Migrating from a hand-rolled if/else service

This is the most common starting point. You have a Java service like:

```java
public DecisionResult decide(Customer c) {
    DecisionResult r = new DecisionResult();
    if (c.getCreditScore() >= 700 && c.getAnnualIncome() >= 50000) {
        r.setApproved(true);
        if (c.getCreditScore() >= 800) {
            r.setTier("PRIME");
        } else {
            r.setTier("STANDARD");
        }
    } else {
        r.setApproved(false);
        r.setReason("Insufficient credit or income");
    }
    return r;
}
```

The Firefly equivalent is the YAML rule shown in the [Drools section above](#migrating-from-drools-drl).
The translation pattern:

1. **Inputs**: every method parameter / accessed bean field → an entry in `inputs:`
2. **Top-level `if` test**: → `when:` conditions
3. **Inside `if` body**: → `then:` actions
4. **Inside `else` body**: → `else:` actions
5. **Nested `if/else`**: → `if cond then ... else ...` action, or a sub-rule
6. **Result builder**: → `output:` mapping

### Why move from hand-rolled to Firefly

- Rules become editable artefacts rather than code deployments.
- A non-engineer can read, review, and propose changes to the YAML.
- The audit trail and validation come for free.

---

## When Firefly is the wrong tool

Be honest about the model. Choose a different engine if you need any of:

- **Inference / forward chaining**: facts in working memory triggering more rules as
  they're derived. Use Drools.
- **Backward chaining / goal-driven reasoning**: e.g., "find a configuration that
  satisfies these constraints". Use Drools or a CLP solver.
- **Decision tables in Excel format** consumed directly. Use Drools DRT, OpenL Tablets,
  or a DMN engine.
- **Multi-fact joins**: rules that match across pairs/groups of facts.
- **Truth maintenance**: retracting a fact and having dependent conclusions roll back.
- **Workflow / BPMN** orchestration with long-running state.

For everything that's expressible as "given this payload, compute / decide / classify
the outcome", Firefly's narrower model is the right tool and will be faster to onramp,
easier to review, and harder to misuse than a full-blown rule engine.
