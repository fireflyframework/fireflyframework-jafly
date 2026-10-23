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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

class MinimalBootRuntimeTest {
    @TempDir Path temporary;

    @Test
    void actualDefaultBootRuntimeHasNoDatabaseOrJobInfrastructure() throws Exception {
        String dependencies = Files.readString(Path.of("target/runtime-classpath.txt")).trim();
        assertThat(dependencies)
                .doesNotContain("spring-boot-batch-")
                .doesNotContain("spring-boot-jdbc-")
                .doesNotContain("spring-boot-starter-batch-");
        String classpath =
                Path.of("target/test-classes").toAbsolutePath()
                        + File.pathSeparator
                        + Path.of("target/classes").toAbsolutePath()
                        + File.pathSeparator
                        + dependencies;
        Path log = temporary.resolve("minimal-boot.log");
        Process process =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-cp",
                                classpath,
                                "org.fireflyframework.batch.fixture.MinimalBootMain")
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertThat(process.waitFor(Duration.ofSeconds(25).toMillis(), TimeUnit.MILLISECONDS))
                    .isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("OWNED_MINIMAL_BOOT_NO_RESOURCES=true");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
