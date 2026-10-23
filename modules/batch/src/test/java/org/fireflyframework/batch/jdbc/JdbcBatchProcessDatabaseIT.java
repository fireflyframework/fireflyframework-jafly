/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fireflyframework.batch.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.fireflyframework.batch.fixture.BatchProcessMain;
import org.fireflyframework.batch.fixture.CheckpointJobFixture;
import org.fireflyframework.batch.fixture.PostgresBatchFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

class JdbcBatchProcessDatabaseIT extends PostgresBatchFixture {
    private Path temporary;

    @BeforeEach
    void preserveOwnedProcessEvidence() throws Exception {
        Path parent = Path.of("target/durable-process-evidence");
        Files.createDirectories(parent);
        temporary = Files.createTempDirectory(parent, "owned-").toAbsolutePath();
        System.out.println("OWNED_PROCESS_EVIDENCE=" + temporary);
    }

    private final List<Process> children = new ArrayList<>();

    @AfterEach
    void cleanupOwnedChildren() throws Exception {
        var observed = new StringBuilder();
        for (Process child : children) {
            if (child.isAlive()) child.destroyForcibly();
            assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(child.isAlive()).isFalse();
            observed.append(child.pid()).append("\t").append(child.isAlive()).append("\n");
        }
        Files.writeString(temporary.resolve("owned-child-cleanup.tsv"), observed);
    }

    private DataSource prepared() throws Exception {
        DataSource source = schema("complete");
        String selected;
        try (var connection = source.getConnection()) {
            selected = connection.getSchema();
        }
        try (var connection = owner().getConnection();
                var statement = connection.createStatement()) {
            connection.setSchema(selected);
            statement.execute(
                    "CREATE TABLE owned_items(run_id varchar(100) NOT NULL,item_no integer NOT"
                            + " NULL,PRIMARY KEY(run_id,item_no))");
            statement.execute("GRANT SELECT,INSERT ON owned_items TO " + role);
        }
        return source;
    }

    private Process child(DataSource source, String name, String mode, Long execution, Path release)
            throws Exception {
        return child(source, name, mode, execution, release, null);
    }

    private Process child(
            DataSource source,
            String name,
            String mode,
            Long execution,
            Path release,
            Path startFile)
            throws Exception {
        Path bootstrap = temporary.resolve(name + "-bootstrap");
        for (Class<?> type :
                List.of(
                        BatchProcessMain.class,
                        CheckpointJobFixture.class,
                        CheckpointJobFixture.CursorReader.class)) {
            String resource = type.getName().replace('.', '/') + ".class";
            Path target = bootstrap.resolve(resource);
            Files.createDirectories(target.getParent());
            try (var input = type.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Owned bootstrap class missing");
                Files.write(target, input.readAllBytes());
            }
        }
        Path artifact = Path.of(System.getProperty("durable.batch.jar"));
        assertThat(artifact).isRegularFile();
        String runtime = Files.readString(Path.of("target/runtime-classpath.txt")).trim();
        assertThat(runtime)
                .doesNotContain("spring-boot-batch", "testcontainers", "junit", "target/classes");
        String driver =
                Path.of(
                                PGSimpleDataSource.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .toURI())
                        .toString();
        String classpath =
                bootstrap
                        + java.io.File.pathSeparator
                        + artifact
                        + java.io.File.pathSeparator
                        + runtime
                        + java.io.File.pathSeparator
                        + driver;
        var pins = new StringBuilder();
        for (String entry :
                classpath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            Path input = Path.of(entry);
            if (Files.isRegularFile(input)) {
                pins.append(input.toAbsolutePath())
                        .append("\t")
                        .append(
                                java.util.HexFormat.of()
                                        .formatHex(
                                                java.security.MessageDigest.getInstance("SHA-256")
                                                        .digest(Files.readAllBytes(input))))
                        .append("\n");
            }
        }
        Files.writeString(temporary.resolve(name + "-jar-pins.tsv"), pins);
        var command =
                new ArrayList<>(
                        List.of(
                                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-Xms16m",
                                "-Xmx192m",
                                "-cp",
                                classpath,
                                BatchProcessMain.class.getName(),
                                mode));
        if (execution != null) command.add(Long.toString(execution));
        var builder =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(temporary.resolve(name + ".log").toFile());
        var environment = builder.environment();
        environment.put("OWNED_BATCH_URL", database.getJdbcUrl());
        environment.put("OWNED_BATCH_USER", role);
        environment.put("OWNED_BATCH_PASSWORD", password);
        try (var connection = source.getConnection()) {
            environment.put("OWNED_BATCH_SCHEMA", connection.getSchema());
        }
        environment.put("OWNED_BATCH_RUN", "stable-process-run");
        environment.put("OWNED_BATCH_TENANT", "owned-tenant");
        environment.remove("OWNED_BATCH_RELEASE_FILE");
        environment.remove("OWNED_BATCH_START_FILE");
        if (startFile != null) environment.put("OWNED_BATCH_START_FILE", startFile.toString());
        if (release != null) environment.put("OWNED_BATCH_RELEASE_FILE", release.toString());
        Process started = builder.start();
        children.add(started);
        return started;
    }

    private void held(DataSource source, Process process, String name) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        Path log = temporary.resolve(name + ".log");
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new AssertionError(Files.readString(log));
            if (Files.readString(log).contains("OWNED_HOLD_BEFORE_FOUR=true")) {
                assertThat(rows(source)).containsExactly(1, 2, 3);
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(
                "Owned child did not reach first committed checkpoint: " + Files.readString(log));
    }

    private List<Integer> rows(DataSource source) {
        return new JdbcTemplate(source)
                .queryForList("SELECT item_no FROM owned_items ORDER BY item_no", Integer.class);
    }

    private long lastExecution(DataSource source) {
        return new JdbcTemplate(source)
                .queryForObject(
                        "SELECT max(JOB_EXECUTION_ID) FROM BATCH_JOB_EXECUTION", Long.class);
    }

    private void success(Process process, String name) throws Exception {
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String output = Files.readString(temporary.resolve(name + ".log"));
        assertThat(process.exitValue()).withFailMessage(output).isZero();
        assertThat(output)
                .contains(
                        "OWNED_MODULE_ORIGIN="
                                + Path.of(System.getProperty("durable.batch.jar")).toUri().toURL());
    }

    private static boolean isNativeEmptyExecutionObservation(
            String output, long instanceId, long version) {
        var lines = output.lines().toList();
        return lines.contains(
                        "Exception in thread \"main\" java.lang.IllegalStateException: Cannot find"
                                + " any job execution for job instance: JobInstance: id="
                                + instanceId
                                + ", version="
                                + version
                                + ", Job=[owned-checkpoint-job]")
                && lines.contains(
                        "\tat org.springframework.batch.core.launch.support.TaskExecutorJobLauncher.createJobExecution(TaskExecutorJobLauncher.java:130)");
    }

    private static boolean isNativeSerializationRejection(String output) {
        var lines = output.lines().toList();
        return lines.contains(
                        "Exception in thread \"main\""
                            + " org.springframework.dao.CannotAcquireLockException:"
                            + " PreparedStatementCallback; SQL [INSERT INTO"
                            + " BATCH_JOB_INSTANCE(JOB_INSTANCE_ID, JOB_NAME, JOB_KEY, VERSION)")
                && lines.contains(
                        "Caused by: org.postgresql.util.PSQLException: ERROR: could not serialize"
                            + " access due to read/write dependencies among transactions")
                && lines.contains(
                        "\tat org.springframework.batch.core.repository.dao.jdbc.JdbcJobInstanceDao.createJobInstance(JdbcJobInstanceDao.java:164)")
                && lines.contains(
                        "\tat org.springframework.jdbc.support.SQLStateSQLExceptionTranslator.doTranslate(SQLStateSQLExceptionTranslator.java:144)");
    }

    @Test
    void nativeRejectionRequiresExactDiagnosticDescriptors() {
        String observed =
                "OWNED_READY_TO_START=true\n"
                    + "Exception in thread \"main\" java.lang.IllegalStateException: Cannot find"
                    + " any job execution for job instance: JobInstance: id=7, version=0,"
                    + " Job=[owned-checkpoint-job]\n"
                    + "\tat org.springframework.batch.core.launch.support.TaskExecutorJobLauncher.createJobExecution(TaskExecutorJobLauncher.java:130)\n";
        assertThat(isNativeEmptyExecutionObservation(observed, 7, 0)).isTrue();
        for (String other :
                List.of(
                        observed.replace("id=7", "id=8"),
                        observed.replace("version=0", "version=1"),
                        observed.replace("owned-checkpoint-job", "another-job"),
                        observed.replace("IllegalStateException", "IllegalArgumentException"),
                        observed.replace(":130)", ":131)"),
                        observed.replace("createJobExecution(", "otherMethod("),
                        observed.replace("Exception in thread", "Prefix Exception in thread"),
                        "java.lang.IllegalStateException: unrelated initialization failure")) {
            assertThat(isNativeEmptyExecutionObservation(other, 7, 0)).isFalse();
        }
        String serialization =
                "Exception in thread \"main\" org.springframework.dao.CannotAcquireLockException:"
                    + " PreparedStatementCallback; SQL [INSERT INTO"
                    + " BATCH_JOB_INSTANCE(JOB_INSTANCE_ID, JOB_NAME, JOB_KEY, VERSION)\n"
                    + "\tat org.springframework.jdbc.support.SQLStateSQLExceptionTranslator.doTranslate(SQLStateSQLExceptionTranslator.java:144)\n"
                    + "\tat org.springframework.batch.core.repository.dao.jdbc.JdbcJobInstanceDao.createJobInstance(JdbcJobInstanceDao.java:164)\n"
                    + "Caused by: org.postgresql.util.PSQLException: ERROR: could not serialize"
                    + " access due to read/write dependencies among transactions\n";
        assertThat(isNativeSerializationRejection(serialization)).isTrue();
        for (String other :
                List.of(
                        serialization.replace("BATCH_JOB_INSTANCE(", "ANOTHER_TABLE("),
                        serialization.replace(
                                "CannotAcquireLockException", "IllegalStateException"),
                        serialization.replace(
                                "could not serialize access due to read/write dependencies among"
                                    + " transactions",
                                "deadlock detected"),
                        serialization.replace(
                                "org.postgresql.util.PSQLException", "java.sql.SQLException"),
                        serialization.replace(":164)", ":165)"),
                        serialization.replace(":144)", ":145)"),
                        serialization.replace("Exception in thread", "Prefix Exception in thread"),
                        "org.springframework.dao.CannotAcquireLockException: unrelated lock"
                            + " failure")) {
            assertThat(isNativeSerializationRejection(other)).isFalse();
        }
    }

    @Test
    void simultaneousIndependentProcessesAdmitOneLiveExecutionAndOneEffectStream()
            throws Exception {
        DataSource source = prepared();
        Path startFile = temporary.resolve("simultaneous-start");
        Path release = temporary.resolve("winner-release");
        Process first = child(source, "race-first", "start", null, release, startFile);
        Process second = child(source, "race-second", "start", null, release, startFile);
        long readyDeadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!Files.readString(temporary.resolve("race-first.log"))
                        .contains("OWNED_READY_TO_START=true")
                || !Files.readString(temporary.resolve("race-second.log"))
                        .contains("OWNED_READY_TO_START=true")) {
            if (!first.isAlive() || !second.isAlive() || System.nanoTime() > readyDeadline) {
                throw new AssertionError(
                        Files.readString(temporary.resolve("race-first.log"))
                                + Files.readString(temporary.resolve("race-second.log")));
            }
            Thread.sleep(20);
        }
        Files.writeString(startFile, "owned synchronized admission");
        long raceDeadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (first.isAlive() == second.isAlive()) {
            if (System.nanoTime() > raceDeadline)
                throw new AssertionError("Native concurrent admission did not settle");
            Thread.sleep(20);
        }
        Process winner = first.isAlive() ? first : second;
        Process loser = first.isAlive() ? second : first;
        String winnerName = first.isAlive() ? "race-first" : "race-second";
        String loserName = first.isAlive() ? "race-second" : "race-first";
        held(source, winner, winnerName);
        assertThat(loser.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(loser.exitValue()).isNotZero();
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isEqualTo(1);
        var instance =
                jdbc.queryForMap("SELECT JOB_INSTANCE_ID,VERSION,JOB_NAME FROM BATCH_JOB_INSTANCE");
        long instanceId = ((Number) instance.get("job_instance_id")).longValue();
        long instanceVersion = ((Number) instance.get("version")).longValue();
        assertThat(instance.get("job_name")).isEqualTo("owned-checkpoint-job");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BATCH_JOB_EXECUTION", Integer.class))
                .isEqualTo(1);
        var execution = jdbc.queryForMap("SELECT JOB_INSTANCE_ID,STATUS FROM BATCH_JOB_EXECUTION");
        assertThat(((Number) execution.get("job_instance_id")).longValue()).isEqualTo(instanceId);
        assertThat(execution.get("status")).isEqualTo("STARTED");
        String loserOutput = Files.readString(temporary.resolve(loserName + ".log"));
        assertThat(loserOutput).doesNotContain("OWNED_EXECUTION_ID=", "OWNED_FINAL_STATUS=");
        if (!isNativeEmptyExecutionObservation(loserOutput, instanceId, instanceVersion)
                && !isNativeSerializationRejection(loserOutput)) {
            assertThat(loserOutput)
                    .containsAnyOf(
                            "JobExecutionAlreadyRunningException",
                            "DuplicateKeyException",
                            "CannotSerializeTransactionException",
                            "JobInstanceAlreadyExistsException",
                            "java.lang.IllegalStateException: JobInstance must not already exist");
        }
        Files.writeString(release, "owned admitted process may finish");
        success(winner, winnerName);
        assertThat(rows(source)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM BATCH_JOB_EXECUTION WHERE STATUS='COMPLETED'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM BATCH_JOB_EXECUTION WHERE STATUS IN"
                                        + " ('STARTING','STARTED','STOPPING')",
                                Integer.class))
                .isZero();
        Files.writeString(
                temporary.resolve("concurrent-observation.txt"),
                "winner=" + winnerName + "\nloser=" + loserName + "\nrows=" + rows(source));
    }

    @Test
    void killedProcessRequiresObservedDeathThenNativeRecoverAndRestartFromPersistedCursor()
            throws Exception {
        DataSource source = prepared();
        Process original =
                child(source, "before-kill", "start", null, temporary.resolve("never-release"));
        held(source, original, "before-kill");
        long originalId = lastExecution(source);
        original.destroyForcibly();
        assertThat(original.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(original.isAlive()).isFalse();
        assertThat(rows(source)).containsExactly(1, 2, 3);
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE"
                                                + " JOB_EXECUTION_ID=?",
                                        String.class,
                                        originalId))
                .isEqualTo("STARTED");
        Files.writeString(
                temporary.resolve("kill-observation.txt"),
                "old-process-alive="
                        + original.isAlive()
                        + "\nrows-before-recovery="
                        + rows(source)
                        + "\noriginal-execution="
                        + originalId);
        Process replacement = child(source, "after-kill", "recover-restart", originalId, null);
        success(replacement, "after-kill");
        assertThat(Files.readString(temporary.resolve("after-kill.log")))
                .contains("OWNED_RECOVERED_STORED_STATUS=FAILED", "OWNED_FINAL_STATUS=COMPLETED");
        assertThat(rows(source)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Files.writeString(temporary.resolve("kill-final-observation.txt"), "rows=" + rows(source));
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BATCH_JOB_EXECUTION", Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID=?",
                                String.class,
                                originalId))
                .isEqualTo("FAILED");
    }

    @Test
    void separateProcessRequestsStopAndFreshProcessRestartsSameInstance() throws Exception {
        DataSource source = prepared();
        Path release = temporary.resolve("release-after-stop");
        Process original = child(source, "before-stop", "start", null, release);
        held(source, original, "before-stop");
        long originalId = lastExecution(source);
        Process stopper = child(source, "stopper", "stop", originalId, null);
        success(stopper, "stopper");
        assertThat(Files.readString(temporary.resolve("stopper.log")))
                .contains("OWNED_STOP_ACK=true");
        Files.writeString(release, "owned release after actual stop acknowledgment");
        success(original, "before-stop");
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT STATUS FROM BATCH_JOB_EXECUTION WHERE"
                                                + " JOB_EXECUTION_ID=?",
                                        String.class,
                                        originalId))
                .isEqualTo("STOPPED");
        List<Integer> stoppedRows = rows(source);
        assertThat(stoppedRows.size()).isBetween(3, 6);
        assertThat(stoppedRows)
                .isEqualTo(
                        java.util.stream.IntStream.rangeClosed(1, stoppedRows.size())
                                .boxed()
                                .toList());
        Files.writeString(
                temporary.resolve("stop-observation.txt"),
                "stopped-rows=" + stoppedRows + "\noriginal-execution=" + originalId);
        Process replacement = child(source, "after-stop", "restart", originalId, null);
        success(replacement, "after-stop");
        assertThat(rows(source)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_INSTANCE", Integer.class))
                .isEqualTo(1);
        assertThat(
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM BATCH_JOB_EXECUTION", Integer.class))
                .isEqualTo(2);
    }
}
