package org.fireflyframework.security.core.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class StrictEmbeddedPolicyDecisionAdapterTest {
    private final SecurityPrincipal principal = SecurityPrincipal.builder().subject("synthetic-user").build();

    private EmbeddedPolicyDecisionAdapterImpl adapter(List<PolicyRule> rules) {
        try {
            return EmbeddedPolicyDecisionAdapterImpl.class.getConstructor(List.class, boolean.class)
                    .newInstance(rules, true);
        } catch (ReflectiveOperationException error) {
            return fail("Explicit strict policy mode must be available without changing the legacy constructor", error);
        }
    }

    private Decision decide(List<PolicyRule> rules) {
        return adapter(rules).authorize(principal, "read", "synthetic-resource", Map.of()).block();
    }

    @Test void missingRulesDenyRatherThanDeferToAnAbsentRbacGate() {
        assertFalse(decide(List.of()).granted());
    }
    @Test void anExplicitPermitStillWorksAndNormalAbstentionIsAllowed() {
        assertTrue(decide(List.of((p,a,r,c)->Mono.just(Decision.indeterminate("not-applicable")),
                (p,a,r,c)->Mono.just(Decision.permit()))).granted());
    }
    @Test void denyOverridesAnotherPermit() {
        assertFalse(decide(List.of((p,a,r,c)->Mono.just(Decision.permit()),
                (p,a,r,c)->Mono.just(Decision.deny("blocked")))).granted());
    }
    @Test void asynchronousRuleFailureCannotHideBehindAnotherPermit() {
        assertFalse(decide(List.of((p,a,r,c)->Mono.just(Decision.permit()),
                (p,a,r,c)->Mono.error(new IllegalStateException("synthetic-private-detail")))).granted());
    }
    @Test void synchronousRuleFailureCannotHideBehindAnotherPermit() {
        assertFalse(decide(List.of((p,a,r,c)->{throw new IllegalStateException("synthetic-private-detail");},
                (p,a,r,c)->Mono.just(Decision.permit()))).granted());
    }
    @Test void emptyRulePublisherCannotHideBehindAnotherPermit() {
        assertFalse(decide(List.of((p,a,r,c)->Mono.empty(),
                (p,a,r,c)->Mono.just(Decision.permit()))).granted());
    }
    @Test void malformedDecisionEffectCannotHideBehindAnotherPermitInEitherOrder() {
        PolicyRule malformed = (p,a,r,c) -> Mono.just(new Decision(null,List.of(),"missing effect"));
        PolicyRule permit = (p,a,r,c) -> Mono.just(Decision.permit());
        assertFalse(decide(List.of(malformed,permit)).granted());
        assertFalse(decide(List.of(permit,malformed)).granted());
    }
    @Test void permittedObligationsAreRetainedInRuleOrder() {
        var first=Obligation.of("row-filter",Map.of("tenant","synthetic-tenant"));
        var second=Obligation.of("mask",Map.of("field","synthetic-field"));
        var decision=decide(List.of((p,a,r,c)->Mono.just(Decision.permit(List.of(first))),
                (p,a,r,c)->Mono.just(Decision.permit(List.of(second)))));
        assertTrue(decision.granted());assertEquals(List.of(first,second),decision.obligations());
        assertThrows(UnsupportedOperationException.class,()->decision.obligations().clear());
    }
}
