# Coordinate a customer application

![Onboarding coordination](../../site/assets/banners/onboarding-coordination.svg)

Use Jafly's Saga, TCC, and Workflow engines to handle three independent parts of customer onboarding: recording an eligible application, reserving review resources, and ordering a document review. The application owns the provider port and its controlled in-memory implementation.

Use JDK 25. After installing the framework libraries, run from the framework checkout:

```sh
./mvnw -B -ntp -f docs/examples/onboarding-coordination/pom.xml clean verify
```

The six tests start the actual Spring-configured engines. They check Saga compensation, TCC phase order and cancellation, Workflow dependency order, and a new execution after provider recovery. The original failed workflow stays failed; `resumeWorkflow` applies to suspended executions.

The example itself needs no Docker engine, database, broker, or credentials. Its state is process-local, and its models run independently. Read the [coordination tutorial](../../site/guides/onboarding-coordination.md) for all source files, observed state changes, and the recovery decisions a deployed adapter must implement.
