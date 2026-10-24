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


package org.fireflyframework.utils.template;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RestrictedHtmlTemplateRendererTest {
    private final RestrictedHtmlTemplateRenderer renderer = new RestrictedHtmlTemplateRenderer();

    @Test
    void actualFinancialRowsAndExactNumbersRenderAsEscapedXhtml() {
        var model = Map.<String,Object>of("issuer", "Synthetic & Bank", "rows", List.of(
                Map.of("label", "Opening <balance>", "amount", new BigDecimal("1234.123456789012345678")),
                Map.of("label", "Large integer", "amount", new BigInteger("9007199254740993"))));
        var result = renderer.render("<section><h1>${issuer}</h1><table><#list rows as row><tr><td>${row.label}</td><td>${row.amount}</td></tr></#list></table></section>", model);
        assertThat(result.success()).isTrue();
        assertThat(result.html()).contains("Synthetic &amp; Bank", "Opening &lt;balance&gt;", "1234.123456789012345678", "9007199254740993");
    }

    @Test
    void arbitraryJavaAndTemplateModelValuesAreRejected() {
        for (Object value : List.of(new Object(), java.time.Instant.now(), new freemarker.template.SimpleScalar("unsafe direct model"), 0.1d)) {
            var result = renderer.render("<p>${value}</p>", Map.of("value", value));
            assertThat(result.success()).isFalse();
            assertThat(result.code()).isEqualTo("UNSUPPORTED_MODEL");
            assertThat(result.html()).isNull();
        }
    }

    @Test
    void classesApisAndExternalTemplateLookupsAreUnavailable() {
        for (String template : List.of(
                "<p>${\"java.lang.System\"?new()}</p>",
                "<p>${facts?api}</p>",
                "<#include '/etc/passwd'>", "<#include 'https://example.invalid/template'>", "<#import 'other.ftl' as other>")) {
            var result = renderer.render(template, Map.of("facts", Map.of("name", "Synthetic")));
            assertThat(result.success()).isFalse();
            assertThat(result.html()).isNull();
        }
    }

    @Test
    void forcedEscapingCannotBeSuppressed() {
        for (String template : List.of("<#noautoesc><p>${value}</p></#noautoesc>", "<p>${value?no_esc}</p>", "<#ftl output_format='plainText'><p>${value}</p>")) {
            assertThat(renderer.render(template, Map.of("value", "<script>unsafe</script>")).success()).isFalse();
        }
    }

    @Test
    void activeHtmlStylesLinksAndXmlEntitiesAreRejectedWithoutReturningPartialOutput() {
        for (String template : List.of("<script>unsafe()</script>", "<p onclick='unsafe()'>Text</p>",
                "<style>body{color:red}</style><p>Text</p>", "<p style='color:red'>Text</p>",
                "<a href='https://example.invalid'>Link</a>", "<img src='file:/etc/passwd'/>",
                "<!DOCTYPE x [<!ENTITY data SYSTEM 'file:/etc/passwd'>]><p>&data;</p>", "<p>&unknown;</p>")) {
            var result = renderer.render(template, Map.of());
            assertThat(result.success()).isFalse();
            assertThat(result.html()).isNull();
        }
    }

    @Test
    void outputAndTemplateElementBudgetsRejectAmplification() {
        var limited = new RestrictedHtmlTemplateRenderer(new RestrictedTemplateLimits(4096, 256, 100, 8, 1024, 100, 1000));
        assertThat(limited.render("<p>${value}</p>", Map.of("value", "x".repeat(512))).code()).isEqualTo("RENDER_LIMIT");
        assertThat(limited.render("<#list 1..1000000 as i><p>x</p></#list>", Map.of()).code()).isEqualTo("RENDER_LIMIT");
    }

    @Test
    void blankMalformedAndVisuallyEmptyDocumentsNeverSucceed() {
        for (String template : List.of("", "  ", "<p></p>", "<section><p>Bad</section>", "<p>${missing}</p>")) {
            assertThat(renderer.render(template, Map.of()).success()).isFalse();
        }
    }

    @Test
    void modelDepthCyclesAndNumericExpansionAreBounded() {
        var cyclic = new java.util.HashMap<String,Object>(); cyclic.put("self", cyclic);
        assertThat(renderer.render("<p>${self}</p>", cyclic).success()).isFalse();
        assertThat(renderer.render("<p>${amount}</p>", Map.of("amount", new BigDecimal("1e-100000"))).success()).isFalse();
    }

    @Test
    void separateInstancesDoNotInheritGlobalUtilityHooksOrLoaders() throws Exception {
        TemplateRenderUtil.setTemplatePostProcessor((html, model) -> "global hook leaked");
        try {
            var result = renderer.render("<p>Actual output</p>", Map.of());
            assertThat(result.success()).isTrue();
            assertThat(result.html()).isEqualTo("<p>Actual output</p>");
        } finally {
            TemplateRenderUtil.setTemplatePostProcessor(null);
        }
    }
    @Test
    void harmlessMarkupCannotBeInjectedBySuppressingEscaping() {
        var result = renderer.render("<p>${value?no_esc}</p>", Map.of("value", "<strong>Injected</strong>"));
        assertThat(result.success()).isFalse();
    }

    @Test
    void utf8OutputLimitCountsSupplementaryCharactersAndInstancesRecoverAfterFailure() {
        var limited = new RestrictedHtmlTemplateRenderer(new RestrictedTemplateLimits(4096, 20, 100, 8, 1024, 100, 1000));
        assertThat(limited.render("<p>${value}</p>", Map.of("value", "😀".repeat(4))).code()).isEqualTo("RENDER_LIMIT");
        assertThat(limited.render("<p>${value}</p>", Map.of("value", "😀".repeat(3))).success()).isTrue();
        assertThat(limited.render("<p>Recovered</p>", Map.of()).success()).isTrue();
        assertThat(renderer.render("<p>${value}</p>", Map.of("value", "\ud800")).success()).isFalse();
    }
    @Test
    void nonbreakingSpaceAndInvisibleFormatOnlyOutputIsEmpty() {
        for (String template : List.of("<p>&#160;</p>", "<p>&#x200b;</p>")) {
            assertThat(renderer.render(template, Map.of()).success()).isFalse();
        }
    }
    @Test
    void classResolverBlocksARealConstructorCapabilityWithSafePositiveControl() throws Exception {
        String source = "<p>${\"freemarker.template.utility.ObjectConstructor\"?new()(\"java.lang.String\", \"constructed\")}</p>";
        var unrestricted = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_35);
        var template = new freemarker.template.Template("private-positive-control", new java.io.StringReader(source), unrestricted);
        var out = new java.io.StringWriter();
        template.process(Map.of(), out);
        assertThat(out.toString()).isEqualTo("<p>constructed</p>");
        assertThat(renderer.render(source, Map.of()).success()).isFalse();
    }
}
