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

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.repository.*;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;

/** Explicit construction only; no automatic repository bean or fallback context. */
@NoRepositoryBean
public interface AuditNativeContextRepository extends Repository<AuditRows.Event,UUID> {
    record Stamp(UUID contextNonce,UUID eventId,Long dbNowMillis,Long untilMillis) { }
    @Query("SELECT * FROM public.firefly_audit_enter(:nonce,:tenant,:service,:type,:resource,:event,:issuer,:subject,:client,:platform,:realm,:staff,:until)")
    Mono<Stamp> enter(@Param("nonce")UUID nonce,@Param("tenant")UUID tenant,@Param("service")String service,@Param("type")String type,@Param("resource")UUID resource,@Param("event")UUID event,@Param("issuer")String issuer,@Param("subject")String subject,@Param("client")String client,@Param("platform")String platform,@Param("realm")String realm,@Param("staff")UUID staff,@Param("until")OffsetDateTime until);
    @Query("SELECT public.firefly_audit_complete(:nonce,:sequence,:hash) AS nonce")
    Mono<UUID> complete(@Param("nonce")UUID nonce,@Param("sequence")long sequence,@Param("hash")String hash);
}
