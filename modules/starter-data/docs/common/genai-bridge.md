# GenAI Bridge

The Firefly Framework spans two language ecosystems: a Java-based data starter (`fireflyframework-starter-data`) that handles data enrichment, job orchestration, and provider management, and a Python-based GenAI metaframework (`fireflyframework-genai`) that builds AI agents and pipelines on top of Pydantic AI. These two systems are designed to work together, but they run in different processes and different languages. The "bridge" is the glue between them.

The `fireflyframework-genai-data` Python package provides that bridge. It is a lightweight Python library that gives AI agents and GenAI pipelines direct access to the data starter's capabilities over HTTP. Instead of reimplementing enrichment logic or job management in Python, you call the Java service's REST API through a typed async client, and the bridge package wraps those calls into tools, pipeline steps, and middleware that plug natively into the GenAI framework.

In practice, this means an AI agent can enrich a company record, start a batch data job, check its status, and validate the results through quality gates -- all without leaving the Python agent framework. The bridge also provides lineage-tracking middleware so that every agent interaction with the data starter is recorded for observability and debugging. If you are building AI-powered workflows that need access to enterprise data operations, this package is how the two halves of the framework connect.

This document provides a comprehensive guide to the `fireflyframework-genai-data` Python package, which bridges the Java `fireflyframework-starter-data` service with the Python `fireflyframework-genai` metaframework. It covers the HTTP client, agent tools, pipeline steps, middleware, and agent templates.

## Table of Contents

- [Overview](#overview)
- [Installation](#installation)
- [DataStarterClient](#datastarterclient)
- [Agent Tools](#agent-tools)
  - [DataEnrichmentTool](#dataenrichmenttool)
  - [DataJobTool](#datajobtool)
  - [DataOperationsTool](#dataoperationstool)
- [DataToolKit](#datatoolkit)
- [Pipeline Steps](#pipeline-steps)
  - [EnrichmentStep](#enrichmentstep)
  - [QualityGateStep](#qualitygatestep)
- [Middleware](#middleware)
  - [DataLineageMiddleware](#datalineagemiddleware)
- [Agent Templates](#agent-templates)
  - [create_data_analyst_agent()](#create_data_analyst_agent)
- [Auto-discovery via Entry Points](#auto-discovery-via-entry-points)
- [Example: Data Analyst Agent with Enrichment](#example-data-analyst-agent-with-enrichment)
- [Example: Pipeline with Enrichment and Quality Gate](#example-pipeline-with-enrichment-and-quality-gate)
- [Best Practices](#best-practices)

---

## Overview

The `fireflyframework-genai-data` package provides native integration between the Java-based data starter and the Python-based GenAI agent framework. It enables AI agents to:

- **Enrich data** through the data starter REST API
- **Manage data jobs** (start, check status, collect results)
- **Execute provider-specific operations** against registered data providers
- **Track data lineage** across agent runs
- **Validate data quality** within GenAI pipelines

The package follows the same hexagonal architecture and protocol-driven design as the broader Firefly Framework ecosystem.

```
┌──────────────────────────────┐     HTTP      ┌───────────────────────────┐
│   fireflyframework-genai     │ ──────────>   │ fireflyframework-starter- │
│   (Python Agent Framework)   │               │ data (Java REST Service)  │
│                              │  <──────────  │                           │
│   ┌──────────────────────┐   │   JSON        │   ┌───────────────────┐   │
│   │ fireflyframework-    │   │               │   │ Enrichment API    │   │
│   │ genai-data (Bridge)  │   │               │   │ Jobs API          │   │
│   │                      │   │               │   │ Operations API    │   │
│   │ - DataStarterClient  │   │               │   │ Providers API     │   │
│   │ - Tools              │   │               │   └───────────────────┘   │
│   │ - Pipeline Steps     │   │               │                           │
│   │ - Middleware          │   │               │                           │
│   │ - Agent Templates    │   │               │                           │
│   └──────────────────────┘   │               │                           │
└──────────────────────────────┘               └───────────────────────────┘
```

---

## Installation

```bash
pip install fireflyframework-genai-data
```

**Requirements:**

| Dependency | Version |
|---|---|
| Python | >= 3.13 |
| `fireflyframework-genai` | >= 26.02.07 |
| `httpx` | >= 0.27.0 |

**Development dependencies:**

```bash
pip install fireflyframework-genai-data[dev]
```

This adds `pytest >= 8.0` and `pytest-asyncio >= 0.24`.

---

## DataStarterClient

The `DataStarterClient` is a lightweight async HTTP client that wraps the data starter REST API endpoints. It is implemented as a Python `dataclass` and uses `httpx.AsyncClient` under the hood.

### Constructor

```python
from fireflyframework_genai_data.client import DataStarterClient

client = DataStarterClient(
    base_url="http://localhost:8080",
    timeout=30.0,  # optional, defaults to 30 seconds
)
```

### Enrichment Methods

```python
# Execute a data enrichment request
result = await client.enrich(
    type="company",
    strategy="ENHANCE",
    parameters={"domain": "example.com"},
    tenant_id="tenant-001",  # optional
)

# Preview enrichment without committing changes
preview = await client.preview_enrichment(
    type="company",
    strategy="MERGE",
    parameters={"domain": "example.com"},
)
```

### Job Management Methods

```python
# Start a data processing job
job = await client.start_job(
    job_type="batch-enrichment",
    parameters={"source": "crm", "limit": 1000},
)

# Check job status
status = await client.check_job(execution_id="exec-abc-123")

# Collect results of a completed job
results = await client.collect_results(execution_id="exec-abc-123")
```

### Provider / Operations Methods

```python
# List available data providers
providers = await client.list_providers(type="enrichment")  # type is optional

# Execute a provider-specific operation
result = await client.execute_operation(
    type="clearbit",
    operation_id="company-lookup",
    request={"domain": "example.com"},
)
```

### Lifecycle

```python
# Always close the client when done
await client.close()

# Or use as an async context manager pattern
try:
    client = DataStarterClient(base_url="http://localhost:8080")
    result = await client.enrich(...)
finally:
    await client.close()
```

### API Endpoints Reference

| Method | HTTP | Endpoint |
|---|---|---|
| `enrich()` | `POST` | `/api/v1/enrichment` |
| `preview_enrichment()` | `POST` | `/api/v1/enrichment/preview` |
| `start_job()` | `POST` | `/api/v1/jobs` |
| `check_job()` | `GET` | `/api/v1/jobs/{execution_id}` |
| `collect_results()` | `GET` | `/api/v1/jobs/{execution_id}/results` |
| `list_providers()` | `GET` | `/api/v1/providers` |
| `execute_operation()` | `POST` | `/api/v1/operations/{type}/{operation_id}` |

---

## Agent Tools

The package provides three agent tools that extend `BaseTool` from `fireflyframework_genai.tools.base`. Each tool wraps a `DataStarterClient` and exposes data operations as agent-callable functions.

### DataEnrichmentTool

Triggers data enrichment through the data starter API.

```python
from fireflyframework_genai_data.client import DataStarterClient
from fireflyframework_genai_data.tools.enrichment_tool import DataEnrichmentTool

client = DataStarterClient(base_url="http://localhost:8080")
tool = DataEnrichmentTool(client)
```

**Tool metadata:**

| Property | Value |
|---|---|
| Name | `data_enrichment` |
| Tags | `data`, `enrichment` |
| Description | Enrich data records using configurable strategies (ENHANCE, MERGE, VALIDATE, etc.) |

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `type` | `str` | Yes | The enrichment type identifier |
| `strategy` | `str` | Yes | Enrichment strategy (e.g., ENHANCE, MERGE, VALIDATE) |
| `parameters` | `dict` | Yes | Strategy-specific parameters |
| `tenant_id` | `str` | No | Tenant identifier for multi-tenant isolation |

### DataJobTool

Manages data processing jobs with support for start, check, and collect actions.

```python
from fireflyframework_genai_data.tools.job_tool import DataJobTool

tool = DataJobTool(client)
```

**Tool metadata:**

| Property | Value |
|---|---|
| Name | `data_job` |
| Tags | `data`, `jobs` |
| Description | Manage data processing jobs: start, check status, or collect results |

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `action` | `str` | Yes | Action to perform: `start`, `check`, or `collect` |
| `job_type` | `str` | No | Type of job to start (required for `start`) |
| `execution_id` | `str` | No | Job execution ID (required for `check` and `collect`) |
| `parameters` | `dict` | No | Job parameters (used with `start`) |

**Action dispatch:**

```python
# Start a job
result = await tool._execute(action="start", job_type="batch-enrichment", parameters={})

# Check job status
result = await tool._execute(action="check", execution_id="exec-123")

# Collect results
result = await tool._execute(action="collect", execution_id="exec-123")
```

### DataOperationsTool

Executes provider-specific data operations by type and operation ID.

```python
from fireflyframework_genai_data.tools.operations_tool import DataOperationsTool

tool = DataOperationsTool(client)
```

**Tool metadata:**

| Property | Value |
|---|---|
| Name | `data_operations` |
| Tags | `data`, `operations` |
| Description | Execute provider-specific data operations by type and operation ID |

**Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `type` | `str` | Yes | The provider type identifier |
| `operation_id` | `str` | Yes | The operation identifier to execute |
| `request` | `dict` | Yes | Operation request payload |

---

## DataToolKit

The `DataToolKit` is a convenience class that bundles all three data tools into a single toolkit for easy agent registration. It extends `ToolKit` from `fireflyframework_genai.tools.toolkit`.

```python
from fireflyframework_genai_data.tools.toolkit import DataToolKit

toolkit = DataToolKit(
    base_url="http://localhost:8080",
    timeout=30.0,  # optional
)
```

**Toolkit metadata:**

| Property | Value |
|---|---|
| Name | `data_starter` |
| Tags | `data` |
| Description | Toolkit providing data enrichment, job management, and operations tools |

**Bundled tools:**

1. `DataEnrichmentTool`
2. `DataJobTool`
3. `DataOperationsTool`

The toolkit creates its own `DataStarterClient` internally, so you only need to provide the base URL.

**Usage with an agent:**

```python
from fireflyframework_genai.agents.agent import FireflyAgent
from fireflyframework_genai_data.tools.toolkit import DataToolKit

toolkit = DataToolKit(base_url="http://localhost:8080")

agent = FireflyAgent(
    name="data-agent",
    tools=[toolkit],
    instructions=("Analyze data and perform enrichment as needed.",),
)
```

---

## Pipeline Steps

The package provides two `StepExecutor`-compatible pipeline steps for use with the GenAI pipeline framework.

### EnrichmentStep

A pipeline step that performs data enrichment via the data starter API.

```python
from fireflyframework_genai_data.client import DataStarterClient
from fireflyframework_genai_data.steps.enrichment_step import EnrichmentStep

client = DataStarterClient(base_url="http://localhost:8080")

step = EnrichmentStep(
    client=client,
    enrichment_type="company",
    strategy="ENHANCE",  # optional, defaults to "ENHANCE"
)
```

**Behavior:**

- The `inputs` dict is forwarded as the `parameters` payload to the enrichment API
- If `inputs` contains a `tenant_id` key, it is extracted and sent separately as the tenant identifier
- After execution, enrichment metadata is stored on `context.metadata["enrichment_results"]` for downstream steps
- Returns the API response as-is

**Pipeline context metadata:**

```python
# After execution, the context will contain:
context.metadata["enrichment_results"] = [
    {
        "type": "company",
        "strategy": "ENHANCE",
        "correlation_id": "pipeline-correlation-id",
    }
]
```

### QualityGateStep

A pipeline step that validates data against a set of rules. If any rule fails, the step raises a `ValueError` listing all violations.

```python
from fireflyframework_genai_data.steps.quality_step import QualityGateStep

step = QualityGateStep(rules=[
    # Simple callable rules
    lambda data: data.get("email") is not None,

    # Tuples with (callable, description) for better error messages
    (lambda data: "@" in data.get("email", ""), "email must contain @"),
    (lambda data: data.get("score", 0) >= 0.0, "score must be non-negative"),
])
```

**Behavior:**

- Each rule is a callable that receives the `inputs` dict and returns `True` (pass) or `False` (fail)
- Rules can optionally be `(callable, description)` tuples for human-readable violation messages
- Bare callables without descriptions are assigned auto-generated names (`rule_0`, `rule_1`, etc.)
- If any rule fails, a `ValueError` is raised with all violations listed
- Violations are also stored on `context.metadata["quality_violations"]`
- On success, `context.metadata["quality_checks_passed"]` is incremented by the number of rules
- The original `inputs` are returned unchanged when all rules pass

**Error format:**

```
ValueError: Quality gate failed with 2 violation(s): email must contain @; score must be non-negative
```

---

## Middleware

### DataLineageMiddleware

An `AgentMiddleware`-compatible middleware that tracks data lineage for every agent run. It captures timing, agent identity, and method information.

```python
from fireflyframework_genai_data.middleware.lineage import DataLineageMiddleware

middleware = DataLineageMiddleware()
```

**Lifecycle hooks:**

| Hook | Action |
|---|---|
| `before_run` | Generates a unique `lineage_id` (UUID hex), attaches it and the agent name to `context.metadata`, records the start time |
| `after_run` | Computes elapsed time in milliseconds, creates a lineage record, appends it to the internal records list |

**Context metadata set by `before_run`:**

| Key | Value |
|---|---|
| `lineage_id` | Unique UUID hex string for this run |
| `lineage_agent` | Name of the agent being run |
| `lineage_start_ns` | Monotonic nanosecond timestamp (removed after run) |

**Lineage record structure (appended to `records`):**

```python
{
    "lineage_id": "a1b2c3d4e5f6...",
    "agent_name": "data-analyst",
    "method": "run",           # from context.method
    "elapsed_ms": 142.5,       # milliseconds
    "has_result": True,        # whether the agent produced a result
}
```

**Accessing lineage records:**

```python
middleware = DataLineageMiddleware()

# After agent runs, retrieve all collected records
for record in middleware.records:
    print(f"Agent {record['agent_name']} ran in {record['elapsed_ms']:.1f}ms")
```

The `records` property returns a copy of the internal list, so modifications to the returned list do not affect the middleware state.

---

## Agent Templates

### create_data_analyst_agent()

A factory function that creates a pre-configured data analyst agent with enrichment, job management, and operations tools, plus lineage tracking middleware.

```python
from fireflyframework_genai_data.agents.templates import create_data_analyst_agent

agent = create_data_analyst_agent(
    base_url="http://localhost:8080",
    name="data-analyst",       # optional, defaults to "data-analyst"
    model=None,                # optional, None uses framework default
)
```

**Included components:**

| Component | Details |
|---|---|
| Tools | Full `DataToolKit` wired to the provided `base_url` |
| Middleware | `DataLineageMiddleware` for automatic lineage tracking |
| Instructions | Default data analysis instructions (overridable via `instructions` kwarg) |
| Tags | `data`, `analyst` |

**Default instructions:**

> "You are a data analyst agent with access to data enrichment, job management, and data operations tools. Analyze data requests, choose appropriate enrichment strategies, and manage data processing jobs efficiently. Always validate inputs before processing and provide clear summaries of results."

**Customization:**

All aspects of the agent are customizable via keyword arguments:

```python
# Custom instructions
agent = create_data_analyst_agent(
    base_url="http://localhost:8080",
    instructions="Focus on financial data enrichment and validation.",
)

# Additional tools alongside the data toolkit
agent = create_data_analyst_agent(
    base_url="http://localhost:8080",
    tools=[my_custom_tool],  # merged with DataToolKit
)

# Additional middleware alongside lineage middleware
agent = create_data_analyst_agent(
    base_url="http://localhost:8080",
    middleware=[my_logging_middleware],  # merged with DataLineageMiddleware
)

# Any additional FireflyAgent kwargs
agent = create_data_analyst_agent(
    base_url="http://localhost:8080",
    memory=my_memory_backend,
    output_type=AnalysisReport,
)
```

---

## Auto-discovery via Entry Points

The package registers its tools as Python entry points under the `fireflyframework_genai.tools` group, enabling auto-discovery by the GenAI framework:

```toml
# From pyproject.toml
[project.entry-points."fireflyframework_genai.tools"]
enrichment_tool = "fireflyframework_genai_data.tools.enrichment_tool:DataEnrichmentTool"
job_tool = "fireflyframework_genai_data.tools.job_tool:DataJobTool"
operations_tool = "fireflyframework_genai_data.tools.operations_tool:DataOperationsTool"
```

This means that when the GenAI framework scans for available tools via `importlib.metadata.entry_points()`, the data tools are automatically discovered without explicit registration.

---

## Example: Data Analyst Agent with Enrichment

This example creates a data analyst agent, runs an enrichment operation, and inspects the lineage:

```python
import asyncio
from fireflyframework_genai_data.agents.templates import create_data_analyst_agent

async def main():
    # Create the pre-configured agent
    agent = create_data_analyst_agent(
        base_url="http://localhost:8080",
        model="openai:gpt-4o",
    )

    # Run the agent with a data analysis prompt
    result = await agent.run(
        "Enrich the company data for domain 'example.com' using the ENHANCE "
        "strategy, then check if the enrichment produced valid results."
    )

    print("Agent result:", result)

    # Inspect lineage records from the middleware
    # (The middleware is accessible via the agent's middleware list)
    for mw in agent.middleware:
        if hasattr(mw, "records"):
            for record in mw.records:
                print(f"Lineage: {record['agent_name']} "
                      f"took {record['elapsed_ms']:.1f}ms")

asyncio.run(main())
```

---

## Example: Pipeline with Enrichment and Quality Gate

This example builds a GenAI pipeline that enriches data and then validates it through a quality gate:

```python
import asyncio
from fireflyframework_genai.pipeline.builder import PipelineBuilder
from fireflyframework_genai.pipeline.context import PipelineContext
from fireflyframework_genai_data.client import DataStarterClient
from fireflyframework_genai_data.steps.enrichment_step import EnrichmentStep
from fireflyframework_genai_data.steps.quality_step import QualityGateStep

async def main():
    client = DataStarterClient(base_url="http://localhost:8080")

    try:
        # Define pipeline steps
        enrich = EnrichmentStep(
            client=client,
            enrichment_type="company",
            strategy="ENHANCE",
        )

        validate = QualityGateStep(rules=[
            (lambda d: d.get("companyName") is not None, "companyName is required"),
            (lambda d: d.get("domain") is not None, "domain is required"),
            (lambda d: isinstance(d.get("employeeCount"), int), "employeeCount must be an integer"),
            (lambda d: d.get("enrichmentScore", 0) >= 0.5, "enrichmentScore must be >= 0.5"),
        ])

        # Build the pipeline: enrich -> validate
        pipeline = (
            PipelineBuilder()
            .add_step("enrich", enrich)
            .add_step("validate", validate, depends_on=["enrich"])
            .build()
        )

        # Execute the pipeline
        context = PipelineContext(correlation_id="pipeline-001")
        result = await pipeline.execute(
            context=context,
            inputs={"domain": "example.com"},
        )

        print("Pipeline result:", result)
        print("Quality checks passed:", context.metadata.get("quality_checks_passed"))
        print("Enrichment metadata:", context.metadata.get("enrichment_results"))

    finally:
        await client.close()

asyncio.run(main())
```

### Pipeline with Error Handling

```python
async def run_pipeline_safely(client, domain):
    enrich = EnrichmentStep(client=client, enrichment_type="company")

    validate = QualityGateStep(rules=[
        (lambda d: d.get("companyName") is not None, "companyName is required"),
    ])

    pipeline = (
        PipelineBuilder()
        .add_step("enrich", enrich)
        .add_step("validate", validate, depends_on=["enrich"])
        .build()
    )

    context = PipelineContext(correlation_id="safe-pipeline")

    try:
        result = await pipeline.execute(
            context=context,
            inputs={"domain": domain},
        )
        return {"status": "success", "data": result}
    except ValueError as e:
        # Quality gate failure
        violations = context.metadata.get("quality_violations", [])
        return {"status": "quality_failure", "violations": violations}
    except Exception as e:
        return {"status": "error", "message": str(e)}
```

---

## Best Practices

1. **Use `DataToolKit` instead of individual tools.** The toolkit bundles all data tools with a single `DataStarterClient`, avoiding redundant HTTP client instances.

2. **Use `create_data_analyst_agent()` for rapid prototyping.** The template function provides sensible defaults for data analysis agents. Override specific aspects (instructions, tools, middleware) as needed rather than building from scratch.

3. **Always close the `DataStarterClient`.** The client holds an `httpx.AsyncClient` that should be closed when no longer needed to release network connections. Use try/finally blocks or manage the lifecycle alongside your application.

4. **Add descriptive rule names to `QualityGateStep`.** Pass `(callable, description)` tuples instead of bare callables so quality violations produce actionable error messages.

5. **Inspect lineage records for debugging.** The `DataLineageMiddleware.records` property provides a chronological log of all agent runs, including elapsed times. Use this for performance analysis and debugging.

6. **Combine pipeline steps for robust workflows.** Use `EnrichmentStep` followed by `QualityGateStep` in a pipeline to ensure enriched data meets quality standards before downstream processing.

7. **Leverage entry points for auto-discovery.** Install the package in your Python environment and let the GenAI framework discover data tools automatically. This reduces boilerplate and ensures tools are available wherever the package is installed.

8. **Configure timeouts appropriately.** The default 30-second timeout on `DataStarterClient` may not be sufficient for large enrichment batches or slow providers. Adjust the `timeout` parameter based on your expected workload.

9. **Use `preview_enrichment()` for testing.** Before running enrichment in production, use the preview endpoint to verify the enrichment output without committing changes.

> **See also:** [Data Quality](data-quality.md) | [Data Lineage](data-lineage.md) | [Data Transformation](data-transformation.md)
