# Native Durable Batch

Optional Spring Batch JDBC infrastructure for application-owned restartable jobs. This module uses the framework's Boot-managed Spring Batch 6.0.5, a PostgreSQL JobRepository, explicit JDBC transactions, and a bounded application-owned executor.

Module qualification has passed 56 tests, including actual PostgreSQL rollback/checkpoints and separate packaged JVM stop, hard-kill/recovery/restart, and concurrent launch. Whole-framework compatibility and independent review remain pending. No published availability or product adoption is claimed.

## Add the module explicitly

```xml
<dependency>
    <groupId>org.fireflyframework</groupId>
    <artifactId>fireflyframework-batch</artifactId>
    <version>26.10.22</version>
</dependency>
```

The framework parent/BOM manage this optional coordinate. Existing starters do not include it. The module does not bring Boot's Batch or JDBC starters, create a DataSource, start a scheduler, launch a job, or apply database DDL. An application supplies its PostgreSQL JDBC driver and the normal resources below.

## Select three resources and disable startup jobs

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

The feature defaults to disabled. Enabled mode requires all three exact bean names and explicit `spring.batch.job.enabled=false`. A conventional name, `@Primary`, another available database, or an R2DBC connection factory does not select a fallback.

The transaction manager must be a `DataSourceTransactionManager` or its `JdbcTransactionManager` subtype whose DataSource is the same selected object. The executor must be an initialized `ThreadPoolTaskExecutor` with equal core/max sizes from 1 to 32, queue capacity zero, and `ThreadPoolExecutor.AbortPolicy`. The application owns its lifecycle. Resource validation happens before a metadata connection is opened.

This application configuration fragment uses an application-provided PostgreSQL driver and environment-bound connection properties:

```java
import java.util.concurrent.ThreadPoolExecutor;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class BatchResources {
    @Bean
    DataSource batchMetadataDataSource(Environment environment) {
        var source = new PGSimpleDataSource();
        source.setURL(environment.getRequiredProperty("application.batch.jdbc-url"));
        source.setUser(environment.getRequiredProperty("application.batch.username"));
        source.setPassword(environment.getRequiredProperty("application.batch.password"));
        return source;
    }

    @Bean
    JdbcTransactionManager batchMetadataTransactions(
            @Qualifier("batchMetadataDataSource") DataSource source) {
        return new JdbcTransactionManager(source);
    }

    @Bean
    ThreadPoolTaskExecutor batchWorkers() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setThreadNamePrefix("application-batch-");
        return executor;
    }
}
```

Use the host application's normal secret configuration. JDBC work is blocking: launch and operate it from appropriate worker threads, without blocking a reactive HTTP event loop.

## Provision the exact native schema

The resource `org/fireflyframework/batch/jdbc/schema-postgresql-6.0.5.sql` is an unchanged copy of the [Spring Batch 6.0.5 PostgreSQL schema](https://github.com/spring-projects/spring-batch/blob/v6.0.5/spring-batch-core/src/main/resources/org/springframework/batch/core/schema-postgresql.sql). Its SHA-256 is `f2ac75cae966e4eb1cd97ee6b11f130495b7109153754a7983befb87614d6e38`.

Have the deployment owner apply it explicitly through the application's controlled migration process. It is outside Flyway's default migration location and has no automatic initializer. Select the intended schema through the DataSource. The initial supported table prefix is `BATCH_`; the three sequence names are `BATCH_JOB_INSTANCE_SEQ`, `BATCH_JOB_EXECUTION_SEQ`, and `BATCH_STEP_EXECUTION_SEQ`.

The runtime role needs schema usage, native table read/insert/update access, and sequence usage/read access. It does not need schema ownership or CREATE permission. Readiness checks PostgreSQL identity, required columns/types, native sequences, and access without inserting a job or advancing a sequence. These checks do not certify storage hardware, backup policy, or every migration constraint. Native administrative deletion APIs additionally require deletion privileges if an owner elects to expose them.

## Use native steps and native operator APIs

`JdbcBatchSteps.chunk(name, chunkSize)` returns the native Batch 6 `ChunkOrientedStepBuilder`. `tasklet(name, tasklet)` returns its native tasklet builder. Both bind the explicitly selected JDBC transaction manager. Normal readers, processors, writers, streams, listeners, and job builders remain native Spring Batch APIs.

A restartable reader must save a stable cursor in its `ExecutionContext` and reopen from that cursor. A committed checkpoint must refer to an immutable input snapshot, with a stable order and item identity. Re-querying today's changing account set under an old cursor does not establish a valid restart.

A JDBC writer participates in the local transaction only when it uses the same selected DataSource through transaction-aware Spring JDBC access such as `JdbcTemplate`/`DataSourceUtils`. A different database, independent connection, R2DBC operation, or remote SDK call is outside that local transaction. A durable metadata repository alone does not establish atomic item writes: the native chunk builder defaults to a resource-free transaction manager unless explicitly configured.

Use Batch 6 object-based `JobOperator.start(Job, JobParameters)`, `stop(JobExecution)`, `restart(JobExecution)`, and `recover(JobExecution)`, then re-read stored state through `JobRepository`. Keep the job registered under its stable name. A stop acknowledgment is a request; the current chunk may still commit. Wait for the stored stopped state and inspect its actual checkpoint before restart. A failed attempt and a new successful attempt remain separate executions of the same instance.

There is no automatic recover/restart worker. Before native recovery after a hard failure, establish that the previous process cannot continue; for a distributed business effect, also establish the owner's lease/fence and producer safety. Native recovery changes metadata and does not fence an old remote request or prove that an external effect did not occur.

## Project-owned providers

Ordinary project repository/operator beans replace defaults without `@Primary` or special method names. A project `DefaultBatchConfiguration` or `@EnableBatchProcessing` configuration backs off the default provider group. Deferred Boot defaults preserve either registration order and actual project component scans. Enabled-mode resource/schema/readiness checks still run, require a single repository and operator, and reject known resource-free repositories.

An opaque custom provider must use the selected JDBC resources and pass actual persistence/restart qualification. An interface or bean name cannot prove its storage behavior. Disabled mode leaves the application's existing Batch configuration alone.

## Bind a business run outside the framework

The application owns a durable run claim containing the exact tenant, book, job kind, cutoff, input snapshot, model/policy/currency revisions, and full intent digest. Exact retries must retain that claim; changed intent must conflict. Use identifying native parameters for stable tenant/run/intent references. Do not add a random run incrementer to turn a conflicting retry into a new accepted run.

Do not persist bearer tokens, caller security contexts, credentials, personal facts, or large business payloads in job parameters or execution contexts. Store small versioned references and cursors. The native metadata schema has no built-in tenant authority: an exposed execution ID must first resolve to its owning run and current caller permission. The application must supply current service authority before each new business effect, including after restart.

For an external producer, durably establish a stable item action before I/O, call the genuine owner SDK with current service credentials, and retain its authoritative receipt. Unknown outcomes stay unresolved until the producer supplies an observed effect or an authoritative no-effect closure under the same immutable intent/deadline. A metadata checkpoint or empty lookup is not such a receipt. Reconciliation exceptions need an owner, reason, retained evidence, and permitted human resolution; a batch retry must not invent a financial adjustment.

This module supplies execution infrastructure. It does not implement bank/provider reconciliation, a balanced general ledger, IFRS 9 classification/measurement/ECL/hedge accounting, IFRS 17 measurement/reinsurance, statutory reporting, or a complete banking/insurance journey.

## Qualification

Module tests use disposable PostgreSQL and synthetic item rows. Configuration/classpath/resource guards, native schema access, project-provider backoff, transactional rollback, checkpoint persistence, process death/restart, concurrent launch, executor admission, and an owner-run/current-authority recipe are separate gates. The focused module gate currently passes 56 tests with zero failures, errors, or skips. Its owner-run recipe is test-owned application code using an explicit synthetic deployment principal and an actual strict in-process PolicyDecisionPort. It proves full-intent replay checks, current policy before new fixture effects, and owner OPEN state despite native COMPLETED when stored acknowledgments are missing. It does not qualify a real external financial producer or a deployed identity provider. Whole-framework and independent final review remain pending.

Run the module through the framework's normal Maven verification with the configured dependency repository. PostgreSQL/container absence is a failed infrastructure gate, not a skipped durability test. Full framework compatibility and independent review are required before adoption. Publication planning currently fails closed for the new unmapped native coordinate until a reviewed package home/preflight is supplied; historical package associations remain unchanged.


The maintained executable fixtures are [checkpoint rollback/restart](src/test/java/org/fireflyframework/batch/jdbc/JdbcCheckpointDatabaseTest.java), [packaged process lifecycle/concurrent admission](src/test/java/org/fireflyframework/batch/jdbc/JdbcBatchProcessDatabaseIT.java), and [application-owned run/current-policy recipe](src/test/java/org/fireflyframework/batch/jdbc/OwnedRunBoundaryDatabaseTest.java). The latter uses only a local in-process PDP inside its synthetic JDBC writer; an application must keep remote policy or producer network I/O outside held database locks and implement its own durable effect protocol. Do not copy the fixture's trusted principal construction into an HTTP authentication boundary.
