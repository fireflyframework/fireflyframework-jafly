package org.fireflyframework.security.core.authz;

import org.fireflyframework.security.api.annotation.Secure;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRequirementTest {

    @Secure(roles = {"${role.admin}", "literal-role"}, scopes = "${scope.read}", permissions = "${perm.write}")
    private void annotatedTarget() {
    }

    private Secure secure() throws NoSuchMethodException {
        return SecureRequirementTest.class.getDeclaredMethod("annotatedTarget").getAnnotation(Secure.class);
    }

    @Test
    void resolvesPlaceholdersInRolesScopesAndPermissions() throws Exception {
        Map<String, String> props = Map.of(
                "${role.admin}", "flydocs-admin",
                "${scope.read}", "docs.read",
                "${perm.write}", "docs:write");
        UnaryOperator<String> resolver = s -> props.getOrDefault(s, s);

        SecureRequirement req = SecureRequirement.from(secure(), resolver);

        assertThat(req.roles()).containsExactly("flydocs-admin", "literal-role"); // literal passes through
        assertThat(req.scopes()).containsExactly("docs.read");
        assertThat(req.permissions()).containsExactly("docs:write");
    }

    @Test
    void fromWithoutResolver_keepsLiteralValuesUnchanged() throws Exception {
        SecureRequirement req = SecureRequirement.from(secure());

        assertThat(req.roles()).containsExactly("${role.admin}", "literal-role");
        assertThat(req.scopes()).containsExactly("${scope.read}");
    }
}
