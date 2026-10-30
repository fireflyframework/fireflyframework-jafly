/*
 * Copyright 2026 Firefly Software Foundation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.fireflyframework.security.api.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PolicyEvaluationTest {
    @Test
    void provenanceAvailabilityIsExplicitAndDoesNotCreateAPermit() {
        var metadata = new PolicyEvaluationProvenance("reported-revision", "reported-decision");
        assertFalse(new PolicyEvaluation(Decision.deny("denied"), Optional.of(metadata)).decision().granted());
        assertTrue(new PolicyEvaluation(Decision.permit(), Optional.empty()).provenance().isEmpty());
    }

    @Test
    void exactOpaqueMetadataIsNotTrimmedOrParsed() {
        var metadata = new PolicyEvaluationProvenance(" opaque/revision:17 ", "opaque/decision:23");
        assertEquals(" opaque/revision:17 ", metadata.policyRevision());
        assertEquals("opaque/decision:23", metadata.decisionId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n", "revision\tchanged", "revision\u007fchanged"})
    void absentBlankOrControlMetadataIsRejected(String value) {
        assertThrows(IllegalArgumentException.class, () -> new PolicyEvaluationProvenance(value, "decision"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyEvaluationProvenance("revision", value));
    }

    @Test
    void metadataHasFiniteExactBounds() {
        assertDoesNotThrow(() -> new PolicyEvaluationProvenance("r".repeat(1024), "d".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> new PolicyEvaluationProvenance("r".repeat(1025), "decision"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyEvaluationProvenance("revision", "d".repeat(257)));
    }

    @Test
    void resultRequiresDecisionAndExplicitOptional() {
        assertThrows(NullPointerException.class, () -> new PolicyEvaluation(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new PolicyEvaluation(Decision.permit(), null));
    }
}
