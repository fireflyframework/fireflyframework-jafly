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
package org.fireflyframework.batch.fixture;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

import javax.sql.DataSource;

public class MinimalBootMain {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {}

    public static void main(String[] args) {
        try (var context =
                new SpringApplicationBuilder(Application.class)
                        .web(WebApplicationType.NONE)
                        .properties("spring.main.banner-mode=off", "logging.level.root=WARN")
                        .run(args)) {
            if (!context.getBeansOfType(DataSource.class).isEmpty()
                    || !context.getBeansOfType(JobRepository.class).isEmpty()
                    || !context.getBeansOfType(JobOperator.class).isEmpty()) {
                throw new AssertionError(
                        "Disabled module created database or Batch infrastructure");
            }
            System.out.println(
                    "OWNED_MODULE_ORIGIN="
                            + org.fireflyframework.batch.jdbc.JdbcBatchSteps.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation());
            System.out.println("OWNED_MINIMAL_BOOT_NO_RESOURCES=true");
        } catch (RuntimeException failure) {
            failure.printStackTrace(System.err);
            throw failure;
        }
    }
}
