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
package org.fireflyframework.core.audit;

import java.util.UUID;
import org.fireflyframework.core.transaction.RootTransactionScope;
import org.fireflyframework.kernel.audit.*;
import reactor.core.publisher.Mono;

/** Local mapped operations, only inside the owner's admitted transaction. */
public interface LocalAuditRepository {
    Mono<AuditCheckpoint> head(RootTransactionScope scope,AuditStream stream);
    Mono<AuditRecord> find(RootTransactionScope scope,UUID resourceTenantId,UUID eventId);
    Mono<AuditRecord> append(RootTransactionScope scope,AuditCheckpoint expected,AuditRecord record);
}
