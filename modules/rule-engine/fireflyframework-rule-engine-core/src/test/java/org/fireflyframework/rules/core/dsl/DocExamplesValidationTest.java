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

package org.fireflyframework.rules.core.dsl;

import org.fireflyframework.rules.core.dsl.parser.ASTRulesDSLParser;
import org.fireflyframework.rules.core.dsl.parser.DSLParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Doc-example guard: extracts every fenced YAML block from the user-facing markdown
 * docs and parses each one through the real {@link ASTRulesDSLParser}. If any documented
 * example stops being parseable -- because of a code change, a typo introduced during
 * editing, or a deliberate breaking change that wasn't reflected in the docs -- this
 * test fails at build time with a precise file:line reference, so the docs and the
 * implementation cannot silently drift.
 *
 * <p>Only blocks that <em>look like</em> complete top-level rules are validated:
 * a block must contain at least one of {@code name:}, {@code when:}, {@code rules:},
 * {@code conditions:}, or {@code then:} as a top-level key. Pure snippets (e.g., a
 * lone list of {@code - set ...} action items pulled out for illustration) are
 * skipped -- they aren't meant to stand on their own.</p>
 *
 * <p>The set of docs scanned: {@code docs/yaml-dsl-reference.md},
 * {@code docs/quick-start-guide.md}, {@code docs/common-patterns-guide.md},
 * {@code docs/b2b-credit-scoring-tutorial.md}, {@code README.md}.</p>
 */
class DocExamplesValidationTest {

    private static final Pattern YAML_FENCE =
            Pattern.compile("(?m)^```ya?ml\\s*\\n(.*?)\\n```", Pattern.DOTALL);

    /**
     * Skip marker: any fenced YAML block immediately preceded (allowing only whitespace
     * lines between) by an HTML comment containing {@code doc-test:skip} is treated as a
     * schema illustration or partial snippet and not validated. Authors may include a
     * trailing rationale (e.g., {@code <!-- doc-test:skip (schema sketch) -->}).
     */
    private static final String SKIP_MARKER = "doc-test:skip";

    /**
     * Heuristic: a block is "full enough to parse" if it declares one of these at the
     * top of the YAML (i.e., as an unindented key on its own line).
     */
    private static final Pattern TOP_LEVEL_RULE_KEY =
            Pattern.compile("(?m)^(name|when|then|else|rules|conditions|inputs?|outputs?|constants|circuit_breaker|metadata|version|description):");

    private static final List<String> DOC_FILES = List.of(
            "docs/yaml-dsl-reference.md",
            "docs/quick-start-guide.md",
            "docs/common-patterns-guide.md",
            "docs/b2b-credit-scoring-tutorial.md",
            "docs/migration-guide.md",
            "README.md"
    );

    /**
     * Produce one parameterised test case per parseable YAML block. Each {@link Arguments}
     * carries a human-readable label (file + first line of block) and the raw YAML.
     */
    static Stream<Arguments> docYamlBlocks() throws IOException {
        Path repoRoot = locateRepoRoot();
        List<Arguments> blocks = new ArrayList<>();
        for (String relPath : DOC_FILES) {
            Path path = repoRoot.resolve(relPath);
            if (!Files.exists(path)) continue;

            String content = Files.readString(path);
            String[] allLines = content.split("\n", -1);

            Matcher m = YAML_FENCE.matcher(content);
            while (m.find()) {
                String yaml = m.group(1);
                if (!looksLikeFullRule(yaml)) continue;
                if (precededBySkipMarker(content, m.start())) continue;

                int charIndex = m.start();
                int line = 1;
                for (int i = 0; i < charIndex && i < content.length(); i++) {
                    if (content.charAt(i) == '\n') line++;
                }
                String firstNonEmptyLine = Arrays.stream(yaml.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .findFirst().orElse("(empty)");
                String label = relPath + ":" + line + " -- " + truncate(firstNonEmptyLine, 60);

                blocks.add(Arguments.of(label, yaml));
            }
            // Suppress unused warning -- allLines kept for potential future use.
            if (allLines.length < 0) throw new IllegalStateException();
        }
        return blocks.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("docYamlBlocks")
    void everyDocumentedRuleParses(String label, String yaml) {
        ASTRulesDSLParser parser = new ASTRulesDSLParser(new DSLParser());

        assertThatCode(() -> parser.parseRulesReactive(yaml).block())
                .as("Doc example must parse: %s%n--- YAML ---%n%s%n--- end ---", label, yaml)
                .doesNotThrowAnyException();
    }

    private static boolean looksLikeFullRule(String yaml) {
        return TOP_LEVEL_RULE_KEY.matcher(yaml).find();
    }

    /**
     * Look backwards from the start of the fence for the most recent non-blank line; if
     * it contains the {@link #SKIP_MARKER}, treat this block as opt-out from validation.
     */
    private static boolean precededBySkipMarker(String content, int fenceStart) {
        int probe = fenceStart - 1;
        // Walk back over any whitespace-only lines.
        while (probe >= 0) {
            // Find start of the line that ends at `probe`.
            int lineEnd = probe;
            while (probe >= 0 && content.charAt(probe) != '\n') probe--;
            String line = content.substring(probe + 1, lineEnd + 1).trim();
            if (!line.isEmpty()) {
                return line.contains(SKIP_MARKER);
            }
            probe--; // skip the newline character itself
        }
        return false;
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    /**
     * Walks up from the test working directory until it finds the multi-module repo root
     * (identified by the presence of the top-level {@code docs/} directory next to a
     * {@code pom.xml}). Surefire runs in the module directory by default.
     */
    private static Path locateRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && cursor != null; i++) {
            if (Files.isDirectory(cursor.resolve("docs"))
                    && Files.isRegularFile(cursor.resolve("pom.xml"))
                    && Files.isRegularFile(cursor.resolve("README.md"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException(
                "Could not locate repo root from " + Paths.get("").toAbsolutePath());
    }
}
