/*
 * Copyright 2024-2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.eda.publisher.postgres;

import org.fireflyframework.eda.publisher.postgres.model.PostgresOutboxAppend;
import reactor.core.publisher.Mono;

public interface PostgresOutboxWriter {
    Mono<Long> append(String schema, String table, PostgresOutboxAppend row);
}
