/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fireflyframework.agentic.bridge.samples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable Spring Boot sample exercising the agentic bridge.
 *
 * <p>Configure the connection to the agentic service in
 * {@code application.yml}; once started, hit the {@link SampleController}
 * endpoints to invoke, stream, converse with, and inspect agents.</p>
 */
@SpringBootApplication
public class AgenticBridgeSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticBridgeSampleApplication.class, args);
    }
}
