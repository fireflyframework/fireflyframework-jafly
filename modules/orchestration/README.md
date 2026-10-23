![Jafly. : Orchestration](../../docs/site/assets/modules/orchestration.svg)

# : Orchestration

Coordinate workflows, compensating sagas, and try-confirm-cancel operations in a reactive application.

Choose a pattern based on the business failure model: workflow dependencies, saga compensation, or TCC reservation. Define steps and failure behavior explicitly, then use the corresponding engine. The configuration and tests below are the reference for persistence, retries, and event integration.

[Module guide](../../docs/site/modules/orchestration.md) · [Jafly README](../../README.md) · [Module catalog](../../docs/site/modules/index.md)

## In the onboarding service

An extended onboarding flow might verify documents, wait for a review, and provision an account. Define the steps and the response to each partial failure before choosing a workflow, saga, or TCC definition.

## Installation

[Installation and version availability](../../docs/site/installation.md) explains source builds and published releases.

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-orchestration</artifactId>
    <version>26.10.22</version>
</dependency>
```

## Quick start

Follow [Coordinate an onboarding review](../../docs/site/guides/onboarding-coordination.md) for the complete tested extension to the onboarding service. The API fragments below illustrate individual contracts.

The Java examples in this section are API fragments for application code. Use the stated imports and supply the application-owned collaborators described alongside them.

### Saga (annotations)

```java
@Saga(name = "OrderSaga")
public class OrderSaga {

    @SagaStep(id = "reserve", compensate = "cancelReservation")
    public Mono<String> reserveInventory(@Input OrderRequest request) {
        return inventoryService.reserve(request.items());
    }

    public Mono<Void> cancelReservation(@Input String reservationId) {
        return inventoryService.cancel(reservationId);
    }

    @SagaStep(id = "charge", dependsOn = "reserve", compensate = "refund")
    public Mono<String> chargePayment(@FromStep("reserve") String reservationId) {
        return paymentService.charge(reservationId);
    }

    public Mono<Void> refund(@Input String chargeId) {
        return paymentService.refund(chargeId);
    }

    @OnSagaComplete
    public void onComplete(SagaResult result) {
        log.info("Order saga completed: {}", result.correlationId());
    }
}
```

Execute it through the injected `SagaEngine`:

```java
sagaEngine.execute("OrderSaga", StepInputs.of("reserve", orderRequest))
    .subscribe(result -> {
        if (result.isSuccess()) {
            log.info("All steps completed in {}ms", result.duration().toMillis());
        } else {
            log.error("Failed at step: {}", result.firstErrorStepId().orElse("unknown"));
        }
    });
```

### Saga (builder DSL)

```java
SagaDefinition def = SagaBuilder.saga("TransferFunds")
    .triggerEventType("TransferRequested")
    .step("debit")
        .handler((input, ctx) -> accountService.debit(input))
        .compensation((result, ctx) -> accountService.credit(result))
        .retry(3).backoffMs(500).jitter()
        .stepEvent("transfers", "DebitCompleted", "accountId")
        .add()
    .step("credit")
        .dependsOn("debit")
        .handlerInput(input -> accountService.credit(input))
        .add()
    .build();

sagaEngine.execute(def, StepInputs.of("debit", transferRequest));
```

### TCC

```java
TccDefinition def = TccBuilder.tcc("PaymentTransaction")
    .triggerEventType("PaymentRequested")
    .participant("debit")
        .tryHandler((input, ctx) -> accountService.holdFunds(input))
        .confirmHandler((tryResult, ctx) -> accountService.commitHold(tryResult))
        .cancelHandler((tryResult, ctx) -> accountService.releaseHold(tryResult))
        .event("payments", "DebitConfirmed", "holdId")
        .add()
    .participant("credit")
        .tryHandler((input, ctx) -> accountService.prepareCredit(input))
        .confirmHandler((tryResult, ctx) -> accountService.commitCredit(tryResult))
        .cancelHandler((tryResult, ctx) -> accountService.cancelCredit(tryResult))
        .add()
    .build();

tccEngine.execute(def, TccInputs.of(Map.of("debit", request, "credit", request)))
    .subscribe(result -> {
        if (result.isConfirmed())   log.info("Transaction confirmed");
        else if (result.isCanceled()) log.warn("Transaction canceled");
    });
```

### Require TCC persistence acknowledgments

`BEST_EFFORT` is the default. Select `REQUIRED` when an execution must stop if its configured persistence provider does not acknowledge a save:

```yaml
firefly:
  orchestration:
    tcc:
      persistence-mode: REQUIRED
```

The native configuration gives the engine and orchestrator the same policy/provider. Manual assembly must do so explicitly; REQUIRED rejects absent or mismatched providers/policies. An initial save failure invokes no participant. A required checkpoint or final-save failure propagates `TccPersistenceException` with its correlation ID, save stage, attempted status, original cause, possible-effects flag and retained execution context. It cannot become a confirmed result, an error-suppressed success or an automatic compensation request.

Overall completion observations, execution-completed publication and success callbacks—including asynchronous callback subscription—wait for the final acknowledgment. Participant/phase events still describe calls that already occurred and do not establish overall financial completion. Preserve the original operation/resource IDs when handling uncertainty; do not blindly retry execution or compensate based only on this error.

An acknowledged save means the configured publisher completed. The default in-memory provider does not gain disk durability through this setting. Existing phase snapshots are not a participant journal or a durable pause/resume protocol: a CONFIRMED phase checkpoint can exist without an acknowledged final report. Actual provider storage, restart recovery and the product's financial action protocol need separate qualification. Existing participant-error cancellation rules remain; define participant timeout/uncertain-outcome semantics before using the example for money movement.

### Workflow

```java
@Workflow(id = "OrderProcessing", version = "1.0",
          publishEvents = true, triggerEventType = "OrderReceived")
public class OrderProcessingWorkflow {

    @WorkflowStep(id = "validate", timeoutMs = 5000)
    public Mono<Map<String, Object>> validate(@Input Map<String, Object> input) {
        return Mono.just(Map.of("orderId", input.get("orderId"), "validated", true));
    }

    @WorkflowStep(id = "charge", dependsOn = "validate",
                  maxRetries = 3, retryDelayMs = 1000)
    public Mono<String> charge(@FromStep("validate") Map<String, Object> validated) {
        return paymentService.charge((String) validated.get("orderId"));
    }

    @WorkflowStep(id = "ship", dependsOn = "charge")
    public Mono<String> ship(@FromStep("charge") String chargeId) {
        return shippingService.ship(chargeId);
    }
}
```

## Configuration

The `firefly.orchestration` namespace is bound by [OrchestrationProperties.java](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/orchestration/src/main/java/org/fireflyframework/orchestration/config/OrchestrationProperties.java). Nested configuration is declared in the linked class. Defaults are the field initializers.

| Setting | Java type | Source default |
| --- | --- | --- |
| `firefly.orchestration.workflow` | `WorkflowProperties` | `new WorkflowProperties()` |
| `firefly.orchestration.saga` | `SagaProperties` | `new SagaProperties()` |
| `firefly.orchestration.tcc` | `TccProperties` | `new TccProperties()` |
| `firefly.orchestration.persistence` | `PersistenceProperties` | `new PersistenceProperties()` |
| `firefly.orchestration.recovery` | `RecoveryProperties` | `new RecoveryProperties()` |

## API overview

| Type | Contract and responsibility |
| --- | --- |
| [`SagaEngine`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/orchestration/src/main/java/org/fireflyframework/orchestration/saga/engine/SagaEngine.java) | Core saga engine that orchestrates saga execution with compensation, persistence, DLQ integration, and ExpandEach fan-out support. |
| [`WorkflowEngine`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/orchestration/src/main/java/org/fireflyframework/orchestration/workflow/engine/WorkflowEngine.java) | Defines `WorkflowEngine`, `startWorkflow`, `cancelWorkflow`, `suspendWorkflow` operations. |
| [`TccEngine`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/orchestration/src/main/java/org/fireflyframework/orchestration/tcc/engine/TccEngine.java) | Core TCC engine that orchestrates TCC transaction execution with persistence, DLQ integration, and lifecycle events. |
| [`SagaBuilder`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/orchestration/src/main/java/org/fireflyframework/orchestration/saga/builder/SagaBuilder.java) | Fluent builder for constructing saga definitions programmatically. |
| [`WorkflowBuilder`](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/orchestration/src/main/java/org/fireflyframework/orchestration/workflow/builder/WorkflowBuilder.java) | Defines `WorkflowBuilder`, `description`, `version`, `triggerMode` operations. |

## Continue the application

Use the [EDA guide](../../docs/site/modules/eda.md) for the next part of this integration.

## Build and validation

Use Java 25. From the repository root, build this component with its required dependencies:

```bash
git clone https://github.com/fireflyframework/fireflyframework-jafly.git
cd fireflyframework-jafly
./mvnw -pl :fireflyframework-orchestration -am verify
./mvnw -pl :fireflyframework-orchestration -am install
```

`verify` runs the selected projects' tests. `install` makes the current build available in your local Maven repository.

Configuration details and source references: [module guide](../../docs/site/modules/orchestration.md).
