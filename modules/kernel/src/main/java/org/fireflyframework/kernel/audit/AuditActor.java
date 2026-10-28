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
package org.fireflyframework.kernel.audit;

import java.util.UUID;

/**
 * Exact claimed actor identity. Trusted authentication adapters must supply and verify it.
 * The platform tenant is the exact authenticated key, independent of the resource tenant UUID.
 * Staff realm and stable staff ID are either both present or both absent.
 */
public record AuditActor(String issuer, String subject, String clientId, String platformTenantId,
                         String staffRealm, UUID staffId) {
    public AuditActor {
        AuditValidation.text(issuer);
        AuditValidation.text(subject);
        AuditValidation.text(clientId);
        AuditValidation.text(platformTenantId);
        if ((staffRealm == null) != (staffId == null))
            throw new IllegalArgumentException("Incomplete audit staff identity");
        if (staffRealm != null) AuditValidation.text(staffRealm);
    }
}
