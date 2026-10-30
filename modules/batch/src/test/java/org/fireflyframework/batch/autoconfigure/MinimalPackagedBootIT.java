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
package org.fireflyframework.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.fireflyframework.batch.fixture.MinimalBootMain;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class MinimalPackagedBootIT {
    private void run(boolean successful, String... arguments) throws Exception {
        Path parent = Path.of("target/durable-process-evidence");
        Files.createDirectories(parent);
        Path evidence = Files.createTempDirectory(parent, "minimal-").toAbsolutePath();
        Path bootstrap = evidence.resolve("bootstrap");
        for (String name :
                List.of(
                        MinimalBootMain.class.getName(),
                        MinimalBootMain.class.getName() + "$Application")) {
            String resource = name.replace('.', '/') + ".class";
            Path target = bootstrap.resolve(resource);
            Files.createDirectories(target.getParent());
            try (var input = MinimalBootMain.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Owned bootstrap missing");
                Files.write(target, input.readAllBytes());
            }
        }
        Path artifact = Path.of(System.getProperty("durable.batch.jar"));
        assertThat(artifact).isRegularFile();
        String dependencies = Files.readString(Path.of("target/runtime-classpath.txt")).trim();
        assertThat(dependencies)
                .doesNotContain(
                        "spring-boot-batch-",
                        "spring-boot-jdbc-",
                        "testcontainers",
                        "junit",
                        "target/classes");
        String classpath =
                bootstrap
                        + java.io.File.pathSeparator
                        + artifact
                        + java.io.File.pathSeparator
                        + dependencies;
        var command =
                new ArrayList<>(
                        List.of(
                                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-Xms16m",
                                "-Xmx192m",
                                "-cp",
                                classpath,
                                MinimalBootMain.class.getName()));
        command.addAll(List.of(arguments));
        Path log = evidence.resolve("java.log");
        var child =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertThat(child.waitFor(25, TimeUnit.SECONDS)).isTrue();
            String output = Files.readString(log);
            if (successful) {
                assertThat(child.exitValue()).withFailMessage(output).isZero();
                assertThat(output)
                        .contains(
                                "OWNED_MINIMAL_BOOT_NO_RESOURCES=true",
                                "OWNED_MODULE_ORIGIN=" + artifact.toUri().toURL());
            } else {
                assertThat(child.exitValue()).withFailMessage(output).isNotZero();
                assertThat(output).contains("data-source-ref");
            }
        } finally {
            if (child.isAlive()) child.destroyForcibly();
            assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
            Files.writeString(
                    evidence.resolve("cleanup.txt"),
                    "pid=" + child.pid() + "\nalive=" + child.isAlive());
            assertThat(child.isAlive()).isFalse();
        }
    }

    @Test
    void absentModeHasNoDatabaseEvenWithPackagedModulePresent() throws Exception {
        run(true);
    }

    @Test
    void explicitFalseModeIgnoresIncompleteResourceNames() throws Exception {
        run(
                true,
                "--firefly.batch.durable.enabled=false",
                "--firefly.batch.durable.data-source-ref=not-present");
    }

    @Test
    void explicitTrueModeRejectsMissingResourcesInActualPackagedBoot() throws Exception {
        run(false, "--firefly.batch.durable.enabled=true", "--spring.batch.job.enabled=false");
    }
}
