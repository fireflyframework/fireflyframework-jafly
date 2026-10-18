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

package org.fireflyframework.common.backoffice.context;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackofficeContextTest {

    @Test
    void operatorRoleAndPermissionHelpers() {
        BackofficeContext ctx = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .backofficeRoles(Set.of("admin", "support"))
                .backofficePermissions(Set.of("refund:approve"))
                .build();

        assertThat(ctx.hasBackofficeRole("admin")).isTrue();
        assertThat(ctx.hasBackofficeRole("missing")).isFalse();
        assertThat(ctx.hasBackofficeAnyRole("missing", "support")).isTrue();
        assertThat(ctx.hasBackofficePermission("refund:approve")).isTrue();
        assertThat(ctx.hasBackofficePermission("nope")).isFalse();
    }

    @Test
    void impersonationIsGenericSubject() {
        BackofficeContext notImpersonating = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID()).build();
        assertThat(notImpersonating.isImpersonating()).isFalse();

        BackofficeContext impersonating = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .impersonatedSubject("user-123")
                .impersonatedSubjectRoles(Set.of("customer"))
                .impersonationReason("Support ticket #42")
                .build();
        assertThat(impersonating.isImpersonating()).isTrue();
        assertThat(impersonating.getImpersonatedSubject()).isEqualTo("user-123");
        assertThat(impersonating.impersonatedSubjectHasRole("customer")).isTrue();
        assertThat(impersonating.getImpersonationReason()).isEqualTo("Support ticket #42");
    }

    @Test
    void carriesGenericTenantAndAttributes() {
        UUID tenant = UUID.randomUUID();
        BackofficeContext ctx = BackofficeContext.builder()
                .backofficeUserId(UUID.randomUUID())
                .tenantId(tenant)
                .attributes(Map.of("backofficeUserSubject", "ops@firefly"))
                .build();

        assertThat(ctx.getTenantId()).isEqualTo(tenant);
        assertThat(ctx.<String>getAttribute("backofficeUserSubject")).isEqualTo("ops@firefly");
        assertThat(ctx.<String>getAttribute("missing")).isNull();
    }
}
