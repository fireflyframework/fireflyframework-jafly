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
/**
 * Immutable audit evidence values and bounded integrity verification. These types do not append
 * records, authenticate actors, establish retention, or prove that business operations were audited.
 * Owners must persist evidence atomically with each applicable local business effect and obtain
 * verification checkpoints from independent custody. The existing security logging SPI is separate.
 *
 * <p>Canonical record protocol 1 uses big-endian integers. Text is a four-byte UTF-8 byte count
 * followed by the exact bytes; UUIDs are their two eight-byte words; instants are epoch seconds
 * (eight bytes) and nanoseconds (four bytes). Optional values use one zero/one presence byte.
 * No locale conversion, Unicode normalization, Java serialization or JSON rendering is involved.
 *
 * <p>The ordered fields are domain text {@code firefly.audit.record}, protocol integer 1,
 * stream tenant/service/resource type/resource ID, sequence, previous hash text, recorded instant,
 * event ID, actor issuer/subject/client/platform tenant text, optional staff realm and staff ID,
 * action, outcome enum name, process ID, correlation ID, optional causation ID, occurred instant,
 * evidence digest text, and policy revision. SHA-256 of those bytes is lowercase hexadecimal.
 * Enum renames and encoding changes require a new protocol and a compatibility design.
 *
 * <p>Verification checks the requested range only. Database uniqueness must cover event identities
 * beyond a range; independent business reconciliation must detect missing event production. A hash
 * chain without independently retained checkpoints cannot detect wholesale history replacement.
 */
package org.fireflyframework.kernel.audit;
