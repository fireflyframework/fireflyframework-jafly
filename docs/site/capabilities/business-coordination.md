# Business coordination

![Business coordination](../assets/banners/capability-business-coordination.svg)

Customer onboarding can involve recording an application, checking eligibility, reserving reviewer capacity, and collecting documents. Those steps need different responses to failure. Choose a coordination model from the business obligation, rather than applying every engine to the same request.

## Match the model to the work

| Need | Model |
| --- | --- |
| Completed steps need a business undo action if later work fails | Saga with explicit compensations |
| Resources can be reserved before a final decision | Try-confirm-cancel participants |
| Work has dependencies and an execution state to inspect | Workflow with ordered steps |
| A bounded input snapshot needs stored checkpoints and restart after process failure | Durable JDBC batch |

Saga, TCC, and workflow belong to the [orchestration module](../modules/orchestration.md). [Durable batch](../modules/batch.md) uses native Spring Batch JDBC metadata and explicit local transactions for restartable jobs. A local command handler is still the smallest choice when the whole operation fits one service decision.

## Follow the onboarding examples

The [coordination tutorial](../guides/onboarding-coordination.md) runs the framework engines in a Spring context with application-owned, in-memory provider implementations. Each model runs independently:

- The saga records an application and checks eligibility. Its failure path removes the recorded application.
- TCC reserves reviewer and document-check capacity. A failed reservation cancels completed reservations; a successful try phase proceeds to confirmation.
- The workflow validates the applicant, checks documents, and records the review result in dependency order.

Use the tutorial's standalone project and tests at [`docs/examples/onboarding-coordination`](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/docs/examples/onboarding-coordination). The provider state makes the business effects visible without calling an external eligibility or document service.

## Define recovery before connecting providers

A compensation is an application operation. It may need to be repeatable and may itself fail. A TCC participant needs a real reservation contract; naming an ordinary remote call a reservation does not make it reversible.

Set timeouts and retries to fit each operation. Keep an execution ID so operators can inspect a failure. A failed workflow and a suspended workflow have different recovery paths: the tutorial starts a new execution after provider recovery, while resume applies to a suspended execution.

The example demonstrates engine behavior with controlled providers. Before replacing them, test the real provider's duplicate calls, reservation expiry, compensation failure, and recovery after a process restart. Do not infer cross-service transactions or exactly-once work from an engine success result.

## Restart bounded jobs from stored checkpoints

For an application-owned reconciliation or export job, retain the exact input snapshot, job parameters and item cursor before restarting. The optional batch module requires an explicitly selected PostgreSQL DataSource, matching JDBC transaction manager, bounded executor, and deployment-owned metadata schema. It does not launch jobs or create a scheduler automatically.

A writer participates in the local transaction only through the same selected DataSource. Remote SDK calls, a second database and R2DBC work remain outside that JDBC transaction. Keep remote authorization and producer I/O outside held database locks, and reconcile retained business receipts before treating native COMPLETED as a business result. See the [Batch guide](../modules/batch.md) for configuration and the maintained restart/process-lifecycle fixtures.

## Keep coordination and history separate

The [event-store tutorial](../guides/onboarding-event-store.md) proves a PostgreSQL append/replay boundary for the application aggregate. It does not atomically combine that database with a saga's remote calls. Decide where application state is committed and how incomplete coordination will be reconciled.

Next, use [service operations](service-operations.md) to make those execution outcomes visible to the people running the service.
