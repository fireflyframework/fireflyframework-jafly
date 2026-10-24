![Jafly durable batch](../assets/modules/batch.svg)

# Durable batch

Run application-owned restartable jobs with native Spring Batch JDBC metadata, explicit transactions, and PostgreSQL checkpoints. Use it when a bounded input snapshot must resume from committed progress after a process stops or fails.

| Maven artifact | Packaging | Source project |
| --- | --- | --- |
| `org.fireflyframework:fireflyframework-batch` | `jar` | [modules/batch](https://github.com/fireflyframework/fireflyframework-jafly/tree/main/modules/batch) |

Part of [Business coordination](index.md#business-coordination), alongside [orchestration](orchestration.md). A batch checkpoint records local job progress; a saga or TCC definition supplies application-specific coordination and failure behavior.

## Add the module explicitly

Check [installation and version availability](../installation.md) before choosing a published artifact. The current source candidate uses:

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-batch</artifactId>
    <version>26.10.22</version>
</dependency>
```

The parent/BOM manage this optional coordinate. Existing starters do not include it. The module does not create a DataSource, launch a job, schedule work, or apply database DDL. The host application supplies the PostgreSQL JDBC driver and owns its resources and migrations.

## Select the database, transaction manager, and executor

```yaml
spring:
  batch:
    job:
      enabled: false
firefly:
  batch:
    durable:
      enabled: true
      data-source-ref: batchMetadataDataSource
      transaction-manager-ref: batchMetadataTransactions
      task-executor-ref: batchWorkers
```

The feature defaults to disabled. Enabled mode requires all three exact bean names and explicit `spring.batch.job.enabled=false`. A conventional bean name, `@Primary`, another database, or an R2DBC connection factory does not select a fallback.

| Application bean | Required contract |
| --- | --- |
| `batchMetadataDataSource` | PostgreSQL JDBC `DataSource` selecting the intended metadata schema |
| `batchMetadataTransactions` | `DataSourceTransactionManager`, or its `JdbcTransactionManager` subtype, using that same DataSource object |
| `batchWorkers` | Initialized `ThreadPoolTaskExecutor`, equal core/max sizes from 1 through 32, queue capacity 0, and `ThreadPoolExecutor.AbortPolicy` |

The application owns executor shutdown. Keep JDBC job work off a reactive HTTP event loop. The [module README](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/batch/README.md#select-three-resources-and-disable-startup-jobs) contains the complete resource configuration fragment with environment-bound connection properties.

Existing application-provided Batch infrastructure remains application-owned. The module's configuration backs off when native project providers are supplied; it does not replace a project's repository or operator behind its back.

## Provision the native metadata schema

Have the deployment owner apply [the bundled PostgreSQL 6.0.5 schema](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/batch/src/main/resources/org/fireflyframework/batch/jdbc/schema-postgresql-6.0.5.sql) through the application's controlled migration process. Its SHA-256 is `f2ac75cae966e4eb1cd97ee6b11f130495b7109153754a7983befb87614d6e38`. It is outside Flyway's default migration location and has no automatic initializer.

The initial supported table prefix is `BATCH_`. The runtime role needs schema usage, native metadata table read/insert/update, and sequence usage/read access. It does not need schema ownership or CREATE permission. Native administrative deletion APIs require additional deletion privileges if the deployment elects to expose them.

Startup checks PostgreSQL identity, required columns/types, native sequences, and access without inserting a job or advancing a sequence. Missing schema or unsuitable resources fail configuration instead of creating a second metadata store. These checks do not certify storage durability, backups, or every migration constraint.

## Bind step transactions explicitly

The injected `JdbcBatchSteps` bean exposes the native builders:

| Entry point | Behavior |
| --- | --- |
| `chunk(name, chunkSize)` | Returns a native `ChunkOrientedStepBuilder` with the selected JDBC transaction manager |
| `tasklet(name, tasklet)` | Returns a native tasklet builder with that same manager |

Use normal Spring Batch readers, processors, writers, streams, listeners, and job builders. A JDBC writer participates in the local transaction only when it uses the selected DataSource through transaction-aware access such as `JdbcTemplate` or `DataSourceUtils`.

A second database, independently opened connection, R2DBC operation, or remote SDK call is outside that local JDBC transaction. A durable metadata repository alone does not make item writes atomic: the native chunk builder otherwise defaults to a resource-free transaction manager. The [maintained checkpoint fixture](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/batch/src/test/java/org/fireflyframework/batch/jdbc/JdbcCheckpointDatabaseTest.java) demonstrates rollback and restart with real PostgreSQL.

## Preserve inputs and inspect stored executions

A restartable reader must save a stable cursor in its `ExecutionContext` and reopen from it. The cursor must refer to an immutable input snapshot with stable ordering and item identity. Re-querying a changed account population under yesterday's cursor does not establish a valid restart.

Use the Batch 6 object-based operator APIs with the job registered under its stable name:

- `JobOperator.start(Job, JobParameters)` starts the retained job definition and parameters.
- `stop(JobExecution)` requests a stop; the current chunk may still commit. Re-read the stored execution and checkpoint before restarting.
- `restart(JobExecution)` starts a new attempt for a restartable execution.
- `recover(JobExecution)` is an explicit recovery operation; retain the previous attempt and inspect the stored result through `JobRepository`.

The [packaged process fixture](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/batch/src/test/java/org/fireflyframework/batch/jdbc/JdbcBatchProcessDatabaseIT.java) covers stop, process death/recovery/restart, and concurrent admission. An operator's HTTP or CLI boundary still needs legitimate current authority and deployment-specific limits; the module does not expose one automatically.

## Keep business receipts authoritative

A reconciliation or settlement owner must retain its full business intent, input snapshot and producer references. Native `COMPLETED` records framework execution progress; it does not replace acknowledgments from the financial owner or establish that external effects occurred.

The [owner-run fixture](https://github.com/fireflyframework/fireflyframework-jafly/blob/main/modules/batch/src/test/java/org/fireflyframework/batch/jdbc/OwnedRunBoundaryDatabaseTest.java) uses a test-owned synthetic principal and an in-process current-policy decision. It demonstrates replay checks and an owner OPEN state when retained acknowledgments are missing despite native COMPLETED. It does not qualify a real financial producer or identity provider. Keep remote policy and producer I/O outside held database locks and implement the application's durable effect protocol.

## Qualification boundary

The focused module source has 56 passing tests, including real PostgreSQL and separate packaged processes. A retained Batch-containing whole-framework gate has 3,827 registered tests with 24 skipped and no failures/errors. The later web-privacy source change has its own verification; the current combined whole reactor, independent Batch review, and package-home qualification remain separate pending gates. No published availability, product adoption, GL posting, or financial close follows from this documentation entry.
