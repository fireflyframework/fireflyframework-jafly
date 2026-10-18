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

package org.fireflyframework.data.controller;

import org.fireflyframework.data.cost.CostReport;
import org.fireflyframework.data.cost.EnrichmentCostTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller for querying enrichment cost information.
 *
 * <p>Exposes the cost tracking data collected by {@link EnrichmentCostTracker}
 * via a REST API endpoint. This controller is only active when a
 * {@code EnrichmentCostTracker} bean is available in the application context.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>
 * GET /api/v1/enrichment/costs
 * </pre>
 *
 * @see EnrichmentCostTracker
 * @see CostReport
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enrichment/costs")
@Tag(name = "Enrichment Costs", description = "Enrichment cost tracking and reporting")
@ConditionalOnBean(EnrichmentCostTracker.class)
public class EnrichmentCostController {

    private final EnrichmentCostTracker costTracker;

    public EnrichmentCostController(EnrichmentCostTracker costTracker) {
        this.costTracker = costTracker;
    }

    /**
     * Returns an enrichment cost report with per-provider breakdown.
     *
     * @return the cost report
     */
    @GetMapping
    @Operation(
        summary = "Get enrichment cost report",
        description = "Returns a cost report with per-provider breakdown including call counts, " +
                     "cost per call, and total costs."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cost report generated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<CostReport> getCostReport() {
        log.debug("Generating enrichment cost report");
        return Mono.fromCallable(costTracker::getReport);
    }
}
