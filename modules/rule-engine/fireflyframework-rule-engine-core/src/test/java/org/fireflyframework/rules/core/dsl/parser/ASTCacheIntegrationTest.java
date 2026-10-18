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

package org.fireflyframework.rules.core.dsl.parser;

import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheAdapter;
import org.fireflyframework.cache.adapter.caffeine.CaffeineCacheConfig;
import org.fireflyframework.cache.manager.FireflyCacheManager;
import org.fireflyframework.rules.core.dsl.model.ASTRulesDSL;
import org.fireflyframework.rules.core.services.impl.CacheServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify that AST caching works correctly in reactive contexts.
 * This test ensures that the cache is used when parsing the same YAML content multiple times.
 */
@Slf4j
class ASTCacheIntegrationTest {

    private ASTRulesDSLParser parser;
    private CacheServiceImpl cacheService;
    private FireflyCacheManager cacheManager;

    @BeforeEach
    void setUp() throws Exception {
        // Create a Caffeine cache adapter with test configuration
        CaffeineCacheConfig config = CaffeineCacheConfig.builder()
                .keyPrefix("firefly:cache")
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats(true)
                .build();

        CaffeineCacheAdapter cacheAdapter = new CaffeineCacheAdapter("default", config);
        cacheManager = new FireflyCacheManager(cacheAdapter, null);

        // Create cache service with mock constant service
        cacheService = new CacheServiceImpl(cacheManager, Mockito.mock(org.fireflyframework.rules.core.services.ConstantService.class));

        // Create DSL parser and AST parser
        DSLParser dslParser = new DSLParser();
        parser = new ASTRulesDSLParser(dslParser);

        // Inject cache service using reflection (since it's @Autowired)
        java.lang.reflect.Field cacheServiceField = ASTRulesDSLParser.class.getDeclaredField("cacheService");
        cacheServiceField.setAccessible(true);
        cacheServiceField.set(parser, cacheService);

        // Clear cache before each test
        cacheManager.clear().block();
    }

    @Test
    void shouldCacheASTOnFirstParseAndRetrieveFromCacheOnSecondParse() {
        // Given
        String yamlContent = """
                rules:
                  - name: test_rule
                    when: age > 18
                    then:
                      - set result to "adult"
                """;

        // When - First parse (should cache)
        ASTRulesDSL firstParse = parser.parseRulesReactive(yamlContent).block();
        
        // Then - Verify first parse succeeded
        assertThat(firstParse).isNotNull();
        assertThat(firstParse.getRules()).hasSize(1);
        assertThat(firstParse.getRules().get(0).getName()).isEqualTo("test_rule");
        
        // When - Second parse (should retrieve from cache)
        ASTRulesDSL secondParse = parser.parseRulesReactive(yamlContent).block();
        
        // Then - Verify second parse succeeded and returned the same object
        assertThat(secondParse).isNotNull();
        assertThat(secondParse).isSameAs(firstParse); // Should be the exact same object from cache
        
        // Verify cache statistics
        var stats = cacheManager.getStats().block();
        assertThat(stats).isNotNull();
        assertThat(stats.getHitCount()).isGreaterThan(0);
        
        log.info("✓ AST cache integration test passed - Cache hit count: {}", stats.getHitCount());
    }

    @Test
    void shouldParseDifferentYamlContentSeparately() {
        // Given
        String yamlContent1 = """
                rules:
                  - name: rule1
                    when: age > 18
                    then:
                      - set result to "adult"
                """;
        
        String yamlContent2 = """
                rules:
                  - name: rule2
                    when: age < 18
                    then:
                      - set result to "minor"
                """;

        // When
        ASTRulesDSL parse1 = parser.parseRulesReactive(yamlContent1).block();
        ASTRulesDSL parse2 = parser.parseRulesReactive(yamlContent2).block();
        
        // Then - Should be different objects
        assertThat(parse1).isNotNull();
        assertThat(parse2).isNotNull();
        assertThat(parse1).isNotSameAs(parse2);
        assertThat(parse1.getRules().get(0).getName()).isEqualTo("rule1");
        assertThat(parse2.getRules().get(0).getName()).isEqualTo("rule2");
        
        log.info("✓ Different YAML content parsed separately");
    }

    @Test
    void shouldWorkInReactiveContext() {
        // Given
        String yamlContent = """
                rules:
                  - name: reactive_test
                    when: status == "active"
                    then:
                      - set approved to true
                """;

        // When - Parse in reactive context (no blocking)
        var result = parser.parseRulesReactive(yamlContent)
                .map(ast -> {
                    assertThat(ast).isNotNull();
                    assertThat(ast.getRules()).hasSize(1);
                    return ast.getRules().get(0).getName();
                })
                .block();
        
        // Then
        assertThat(result).isEqualTo("reactive_test");
        
        log.info("✓ Reactive context test passed");
    }

    @Test
    void shouldHandleCacheKeyGeneration() {
        // Given
        String yamlContent = """
                rules:
                  - name: test
                    when: true
                    then:
                      - set x to 1
                """;

        // When
        String cacheKey1 = cacheService.generateCacheKey(yamlContent);
        String cacheKey2 = cacheService.generateCacheKey(yamlContent);
        
        // Then - Same content should generate same cache key
        assertThat(cacheKey1).isEqualTo(cacheKey2);
        assertThat(cacheKey1).hasSize(64); // SHA-256 produces 64 hex characters
        
        // Different content should generate different cache key
        String differentYaml = yamlContent + "\n";
        String cacheKey3 = cacheService.generateCacheKey(differentYaml);
        assertThat(cacheKey3).isNotEqualTo(cacheKey1);
        
        log.info("✓ Cache key generation test passed");
    }
}

