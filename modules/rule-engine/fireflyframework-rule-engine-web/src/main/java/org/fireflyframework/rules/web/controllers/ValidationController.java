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

package org.fireflyframework.rules.web.controllers;

import org.fireflyframework.rules.core.validation.YamlDslValidator;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidateYamlRequest;
import org.fireflyframework.rules.interfaces.dtos.validation.ValidationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST controller for YAML DSL validation services.
 * Provides comprehensive static analysis and validation of rule definitions.
 */
@RestController
@RequestMapping("/api/v1/validation")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "YAML DSL Validation", description = "Static code analysis and validation for YAML DSL rules")
public class ValidationController {

    private final YamlDslValidator yamlDslValidator;

    @Operation(
        summary = "Validate YAML DSL Rule",
        description = """
            Performs comprehensive static analysis and validation of a YAML DSL rule.
            Acts as a static code analyzer to catch syntax errors, naming convention violations,
            dependency issues, logic problems, and provide optimization suggestions.
            
            This endpoint helps developers:
            - Catch errors before deployment
            - Ensure naming convention compliance
            - Detect order-of-operations issues (like the bug we just fixed!)
            - Optimize rule performance
            - Follow best practices
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "YAML DSL content and validation options",
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ValidateYamlRequest.class),
                examples = {
                    @ExampleObject(
                        name = "Basic Credit Scoring Rule",
                        description = "A simple credit scoring rule with proper naming conventions",
                        value = """
                            {
                              "yamlContent": "name: Credit Score Assessment\\ndescription: Evaluate credit worthiness based on score and income\\nversion: 1.0.0\\ninputs:\\n  - creditScore\\n  - annualIncome\\n  - employmentYears\\n  - existingDebt\\nwhen:\\n  - creditScore at_least MIN_CREDIT_SCORE\\n  - annualIncome at_least 50000\\n  - employmentYears at_least 2\\nthen:\\n  - calculate debt_to_income as existingDebt / annualIncome\\n  - set is_eligible to true\\n  - set approval_tier to \\"STANDARD\\"\\nelse:\\n  - set is_eligible to false\\n  - set approval_tier to \\"DECLINED\\"\\noutput:\\n  is_eligible: is_eligible\\n  approval_tier: approval_tier\\n  debt_to_income: debt_to_income",
                              "includeSuggestions": true,
                              "includeMetrics": true
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Rule with Issues",
                        description = "A rule with various validation issues to demonstrate error detection",
                        value = """
                            {
                              "yamlContent": "name: Bad Rule with Naming Issues\\ninputs:\\n  - CREDIT_SCORE\\n  - annual_income\\n  - employment_years\\nwhen:\\n  - CREDIT_SCORE at_least 650\\n  - debt_to_income less_than 0.4\\nthen:\\n  - calculate DEBT_RATIO as monthlyDebt / annual_income\\n  - set FINAL_DECISION to \\"APPROVED\\"",
                              "categories": ["SYNTAX", "NAMING", "DEPENDENCIES"],
                              "minSeverity": "WARNING"
                            }
                            """
                    )
                }
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Validation completed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ValidationResult.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (empty YAML, too large, etc.)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error during validation",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/yaml")
    public Mono<ResponseEntity<ValidationResult>> validateYaml(
            @Valid @RequestBody ValidateYamlRequest request) {

        log.info("Validating YAML DSL rule, content length: {} characters",
                request.getYamlContent().length());

        return Mono.fromCallable(() -> yamlDslValidator.validate(request.getYamlContent()))
                .subscribeOn(Schedulers.boundedElastic()) // Execute blocking code on elastic thread pool
                .map(result -> {
                    // Filter results based on request parameters
                    ValidationResult filteredResult = filterValidationResult(result, request);

                    log.info("Validation completed: status={}, issues={}, quality_score={}",
                            filteredResult.getStatus(),
                            filteredResult.getSummary().getTotalIssues(),
                            filteredResult.getSummary().getQualityScore());

                    return ResponseEntity.ok(filteredResult);
                })
                .onErrorResume(e -> {
                    log.error("Error during YAML validation", e);

                    // Return a validation result with the error
                    ValidationResult errorResult = ValidationResult.builder()
                        .status(ValidationResult.ValidationStatus.CRITICAL_ERROR)
                        .summary(ValidationResult.ValidationSummary.builder()
                            .totalIssues(1)
                            .criticalErrors(1)
                            .qualityScore(0.0)
                            .build())
                        .issues(ValidationResult.ValidationIssues.builder()
                            .syntax(java.util.List.of(
                                ValidationResult.ValidationIssue.builder()
                                    .code("INTERNAL_ERROR")
                                    .severity(ValidationResult.ValidationSeverity.CRITICAL)
                                    .message("Internal validation error")
                                    .description("An unexpected error occurred: " + e.getMessage())
                                    .suggestion("Please check your YAML syntax and try again")
                                    .build()
                            ))
                            .build())
                        .build();

                    return Mono.just(ResponseEntity.ok(errorResult));
                });
    }

    @Operation(
        summary = "Quick YAML Syntax Check",
        description = """
            Performs a quick syntax-only validation of YAML DSL content.
            Useful for real-time validation in editors or IDEs.
            Only checks basic YAML syntax and DSL structure, not business logic.
            """,
        parameters = {
            @Parameter(
                name = "yaml",
                description = "YAML DSL content to validate (URL encoded)",
                required = true,
                example = "name: Test Rule\\ninputs: [creditScore]\\nwhen: [creditScore at_least 650]\\nthen: [set result to APPROVED]"
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Syntax check completed",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ValidationResult.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @GetMapping("/syntax")
    public Mono<ResponseEntity<ValidationResult>> checkSyntax(
            @RequestParam("yaml")
            @NotBlank(message = "YAML content cannot be empty")
            @Size(max = 50000, message = "YAML content too large for quick check (max 50KB)")
            String yaml) {

        log.debug("Quick syntax check, content length: {} characters", yaml.length());

        // Create a request with only syntax validation
        ValidateYamlRequest request = ValidateYamlRequest.builder()
            .yamlContent(yaml)
            .categories(java.util.Set.of(ValidateYamlRequest.ValidationCategory.SYNTAX))
            .includeSuggestions(false)
            .includeMetrics(false)
            .build();

        return Mono.fromCallable(() -> yamlDslValidator.validate(request.getYamlContent()))
                .subscribeOn(Schedulers.boundedElastic()) // Execute blocking code on elastic thread pool
                .map(result -> {
                    ValidationResult filteredResult = filterValidationResult(result, request);
                    return ResponseEntity.ok(filteredResult);
                })
                .onErrorResume(e -> {
                    log.error("Error during syntax check", e);

                    ValidationResult errorResult = ValidationResult.builder()
                        .status(ValidationResult.ValidationStatus.CRITICAL_ERROR)
                        .summary(ValidationResult.ValidationSummary.builder()
                            .totalIssues(1)
                            .criticalErrors(1)
                            .qualityScore(0.0)
                            .build())
                        .build();

                    return Mono.just(ResponseEntity.ok(errorResult));
                });
    }

    /**
     * Filter validation results based on request parameters
     */
    private ValidationResult filterValidationResult(ValidationResult result, ValidateYamlRequest request) {
        // For now, return the full result
        // In a more sophisticated implementation, we would:
        // 1. Filter by categories if specified
        // 2. Filter by minimum severity level
        // 3. Remove suggestions if not requested
        // 4. Remove metrics if not requested
        
        return result;
    }
}
