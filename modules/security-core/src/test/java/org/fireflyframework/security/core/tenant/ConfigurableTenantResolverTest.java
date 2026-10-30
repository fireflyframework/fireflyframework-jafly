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

package org.fireflyframework.security.core.tenant;

import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

class ConfigurableTenantResolverTest {

    private final ConfigurableTenantResolver resolver = new ConfigurableTenantResolver(List.of("tenant_ids"));

    private static SecurityPrincipal principalWith(Object tenantClaim) {
        return SecurityPrincipal.builder()
                .subject("user-1")
                .claims(tenantClaim == null ? Map.of() : Map.of("tenant_ids", tenantClaim))
                .build();
    }

    @Test
    void emitsTheSingleTenant() {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("tenant-a"))))
                .expectNext("tenant-a")
                .verifyComplete();
    }

    @Test
    void emptyWhenNoTenant() {
        StepVerifier.create(resolver.resolveTenant(principalWith(null)))
                .verifyComplete();
    }

    @Test
    void emptyWhenMoreThanOneTenantFailClosed() {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("tenant-a", "tenant-b"))))
                .verifyComplete();
    }

    @Test
    void readsNestedClaimPath() {
        ConfigurableTenantResolver nested = new ConfigurableTenantResolver(List.of("org.tenant"));
        SecurityPrincipal principal = SecurityPrincipal.builder()
                .subject("user-1")
                .claims(Map.of("org", Map.of("tenant", "tenant-x")))
                .build();
        StepVerifier.create(nested.resolveTenant(principal))
                .expectNext("tenant-x")
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant-a", "opaque:tenant/α β", "opaque:tenant/a,b", " tenant-a "})
    void preservesEachScalarTenantAsOneExactOpaqueIdentifier(String tenant) {
        StepVerifier.create(resolver.resolveTenant(principalWith(tenant))).expectNext(tenant).verifyComplete();
    }

    @Test
    void scalarAndSingleCollectionElementResolveToTheSameExactIdentifier() {
        for (String tenant : List.of("opaque:tenant/α β", "opaque:tenant/a,b", " tenant-a ")) {
            StepVerifier.create(resolver.resolveTenant(principalWith(tenant))).expectNext(tenant).verifyComplete();
            StepVerifier.create(resolver.resolveTenant(principalWith(List.of(tenant)))).expectNext(tenant).verifyComplete();
        }
    }

    @Test
    void distinctOpaqueCollectionElementsRemainAmbiguous() {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("opaque tenant", "opaque,tenant"))))
                .verifyComplete();
    }

    @Test
    void numericLegacyProjectionRemainsCompatible() {
        StepVerifier.create(resolver.resolveTenant(principalWith(17))).expectNext("17").verifyComplete();
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of(17)))).expectNext("17").verifyComplete();
    }

    @Test
    void mixedLegacyCollectionDoesNotSilentlyDiscardAnAmbiguousElement() {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("tenant-a", 17)))).verifyComplete();
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("17", 17)))).expectNext("17").verifyComplete();
    }

    @Test
    void identicalOpaqueValuesAcrossClaimPathsCoalesceWithoutNormalization() {
        String tenant = "opaque tenant,α";
        var configured = new ConfigurableTenantResolver(List.of("first", "org.tenant"));
        var principal = SecurityPrincipal.builder().subject("user-1")
                .claims(Map.of("first", tenant, "org", Map.of("tenant", List.of(tenant)))).build();
        StepVerifier.create(configured.resolveTenant(principal)).expectNext(tenant).verifyComplete();
    }

    @Test
    void blankScalarDoesNotBecomeATenant() {
        StepVerifier.create(resolver.resolveTenant(principalWith(" \t "))).verifyComplete();
    }


    private static java.util.stream.Stream<String> malformedIdentifiers() {
        return List.of("tenant\nvalue", "tenant"+(char)0+"value", "tenant"+(char)0x85+"value",
                "tenant"+(char)0xD800, "tenant"+(char)0xDC00, "tenant"+(char)0xD800+"value", "\t", "\n").stream();
    }

    @ParameterizedTest
    @MethodSource("malformedIdentifiers")
    void malformedScalarTenantNeverResolves(String tenant) {
        StepVerifier.create(resolver.resolveTenant(principalWith(tenant))).verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("malformedIdentifiers")
    void malformedCollectionElementRefusesTheWholeTenantCandidate(String tenant) {
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of(tenant)))).verifyComplete();
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of("tenant-a", tenant)))).verifyComplete();
    }

    @Test
    void supplementaryUnicodeScalarAndCollectionRemainExact() {
        String tenant = " tenant/🌍 α,β ";
        StepVerifier.create(resolver.resolveTenant(principalWith(tenant))).expectNext(tenant).verifyComplete();
        StepVerifier.create(resolver.resolveTenant(principalWith(List.of(tenant)))).expectNext(tenant).verifyComplete();
    }

    @Test
    void legacyBlankAndNullCollectionEntriesDoNotCreateAnIdentifier() {
        StepVerifier.create(resolver.resolveTenant(principalWith("   "))).verifyComplete();
        StepVerifier.create(resolver.resolveTenant(principalWith(java.util.Arrays.asList(null, "   ", "tenant-a"))))
                .expectNext("tenant-a").verifyComplete();
    }

}
