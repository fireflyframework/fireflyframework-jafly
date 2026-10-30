/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.api.domain;

/**
 * Opaque metadata reported by the policy engine for the same evaluation response.
 * This metadata alone establishes neither a cryptographic proof nor native authority.
 *
 * @param policyRevision exact engine-reported revision; never inferred from a client clock or grant
 * @param decisionId exact engine-reported evaluation identifier
 */
public record PolicyEvaluationProvenance(String policyRevision, String decisionId) {
    public PolicyEvaluationProvenance {
        requireMetadata(policyRevision, 1024);
        requireMetadata(decisionId, 256);
    }

    private static void requireMetadata(String value, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid policy evaluation metadata");
        }
    }
}
