# Database Schema

The library ships with 8 Flyway migrations under `classpath:db/migration`. All migrations are idempotent where possible (`IF NOT EXISTS`, `CREATE OR REPLACE`).

## Migration Overview

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__Create_Events_Table.sql` | Core events table with BIGSERIAL global sequence |
| V2 | `V2__Create_Snapshots_Table.sql` | Snapshots table with composite PK |
| V3 | `V3__Create_Event_Outbox_Table.sql` | Transactional outbox for reliable publishing |
| V4 | `V4__Enhance_Events_Table_For_Production.sql` | Audit columns, triggers, views, materialized view |
| V5 | `V5__Enhance_Snapshots_Table_For_Production.sql` | Audit columns, triggers, views, cleanup function |
| V6 | `V6__Enhance_Event_Outbox_For_Production.sql` | Priority, partitioning, retry triggers, functions |
| V7 | `V7__Create_Account_Ledger_Read_Model.sql` | Example read model for AccountLedger aggregate |
| V8 | `V8__Create_Projection_Tables.sql` | Projection position tracking and example balance projection |

## V1: events Table

```sql
CREATE TABLE events (
    event_id            UUID PRIMARY KEY,
    aggregate_id        UUID NOT NULL,
    aggregate_type      VARCHAR(255) NOT NULL,
    aggregate_version   BIGINT NOT NULL,
    global_sequence     BIGSERIAL UNIQUE,
    event_type          VARCHAR(255) NOT NULL,
    event_data          TEXT NOT NULL,
    metadata            TEXT,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT unique_aggregate_version UNIQUE(aggregate_id, aggregate_version)
);
```

Key points:
- `event_data` and `metadata` are **TEXT**, not JSONB. This makes the store database-agnostic.
- `global_sequence` is **BIGSERIAL** -- the database auto-assigns it. The R2dbcEventStore INSERT statement **excludes** `global_sequence`.
- The `UNIQUE(aggregate_id, aggregate_version)` constraint enforces optimistic concurrency at the database level.

**V1 Indexes:**

| Index | Columns |
|-------|---------|
| `idx_events_aggregate` | `(aggregate_id, aggregate_type)` |
| `idx_events_global_sequence` | `(global_sequence)` |
| `idx_events_type` | `(event_type)` |
| `idx_events_created_at` | `(created_at)` |
| `idx_events_aggregate_version` | `(aggregate_id, aggregate_version)` |

## V2: snapshots Table

```sql
CREATE TABLE snapshots (
    aggregate_id        UUID NOT NULL,
    aggregate_type      VARCHAR(255) NOT NULL,
    aggregate_version   BIGINT NOT NULL,
    snapshot_data       TEXT NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, aggregate_type)
);
```

Key points:
- The PK is `(aggregate_id, aggregate_type)` -- only **one snapshot per aggregate** is stored.
- Saving a snapshot performs an UPSERT (INSERT ON CONFLICT UPDATE).

**V2 Indexes:**

| Index | Columns |
|-------|---------|
| `idx_snapshots_version` | `(aggregate_version)` |
| `idx_snapshots_created_at` | `(created_at)` |
| `idx_snapshots_type` | `(aggregate_type)` |

## V3: event_outbox Table

```sql
CREATE TABLE event_outbox (
    outbox_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(255) NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    event_data      TEXT NOT NULL,
    metadata        TEXT,
    status          VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at    TIMESTAMP WITH TIME ZONE,
    retry_count     INTEGER DEFAULT 0 NOT NULL,
    last_error      TEXT,
    next_retry_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT valid_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED'))
);
```

**V3 Indexes:**

| Index | Columns | Condition |
|-------|---------|-----------|
| `idx_outbox_status` | `(status)` | |
| `idx_outbox_created_at` | `(created_at)` | |
| `idx_outbox_next_retry` | `(next_retry_at)` | `WHERE status = 'FAILED'` |
| `idx_outbox_aggregate` | `(aggregate_id, aggregate_type)` | |
| `idx_outbox_event_type` | `(event_type)` | |
| `idx_outbox_pending` | `(created_at)` | `WHERE status = 'PENDING'` |

## V4: Events Production Enhancements

Adds columns to the `events` table:

| Column | Type | Purpose |
|--------|------|---------|
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | Updated timestamp |
| `created_by` | `VARCHAR(255)` | User or service that created the event |
| `tenant_id` | `VARCHAR(255)` | Multi-tenancy support |
| `correlation_id` | `VARCHAR(255)` | Distributed tracing |
| `causation_id` | `VARCHAR(255)` | Causation chain |
| `event_size_bytes` | `INTEGER` | Calculated by trigger |
| `checksum` | `VARCHAR(64)` | SHA-256 integrity check |

**V4 Constraints:**

- `chk_aggregate_version_positive`: `aggregate_version >= 0`
- `chk_event_type_not_empty`: `event_type <> ''`
- `chk_aggregate_type_not_empty`: `aggregate_type <> ''`

**V4 Triggers:**

| Trigger | Fires | Function | Purpose |
|---------|-------|----------|---------|
| `trigger_events_updated_at` | BEFORE UPDATE | `update_events_updated_at()` | Auto-set `updated_at` |
| `trigger_calculate_event_size` | BEFORE INSERT OR UPDATE | `calculate_event_size()` | Calculate `event_size_bytes = LENGTH(event_data)` |

**V4 Views:**

| View | Type | Purpose |
|------|------|---------|
| `v_event_statistics` | VIEW | Counts, sizes by aggregate_type and event_type |
| `v_recent_events` | VIEW | Events from last 24 hours |
| `mv_aggregate_summary` | MATERIALIZED VIEW | Per-aggregate stats (version, event count, size) |

**V4 Functions:**

- `refresh_aggregate_summary()` -- refreshes the materialized view concurrently

## V5: Snapshots Production Enhancements

Adds columns to the `snapshots` table:

| Column | Type | Purpose |
|--------|------|---------|
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | Updated timestamp |
| `created_by` | `VARCHAR(255)` | Creator |
| `tenant_id` | `VARCHAR(255)` | Multi-tenancy |
| `snapshot_size_bytes` | `INTEGER` | Calculated by trigger |
| `checksum` | `VARCHAR(64)` | Integrity check |
| `compression_type` | `VARCHAR(50)` | GZIP, LZ4, ZSTD, NONE |
| `is_compressed` | `BOOLEAN` | Default FALSE |

**V5 Constraints:**

- `chk_snapshot_version_positive`: `aggregate_version > 0`
- `chk_snapshot_type_not_empty`: `aggregate_type <> ''`
- `chk_compression_type_valid`: NULL or one of GZIP, LZ4, ZSTD, NONE

**V5 Triggers:**

| Trigger | Function | Purpose |
|---------|----------|---------|
| `trigger_snapshots_updated_at` | `update_snapshots_updated_at()` | Auto-set `updated_at` |
| `trigger_calculate_snapshot_size` | `calculate_snapshot_size()` | Calculate `snapshot_size_bytes` |

**V5 Views and Functions:**

- `v_snapshot_statistics` -- aggregated stats by aggregate type
- `cleanup_old_snapshots(keep_count INTEGER DEFAULT 3)` -- keeps only latest N snapshots per aggregate

## V6: Event Outbox Production Enhancements

Adds columns to the `event_outbox` table:

| Column | Type | Default | Purpose |
|--------|------|---------|---------|
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | | Updated timestamp |
| `tenant_id` | `VARCHAR(255)` | | Multi-tenancy |
| `correlation_id` | `VARCHAR(255)` | | Tracing |
| `priority` | `INTEGER` | `5` | Processing priority (1=highest, 10=lowest) |
| `max_retries` | `INTEGER` | `3` | Maximum retry attempts |
| `partition_key` | `VARCHAR(255)` | | Ordered processing key |

**V6 Constraints:**

- `chk_outbox_priority_range`: `priority BETWEEN 1 AND 10`
- `chk_outbox_max_retries_positive`: `max_retries >= 0`
- `chk_outbox_retry_count_valid`: `retry_count >= 0 AND retry_count <= max_retries + 10`

**V6 Triggers:**

| Trigger | Function | Purpose |
|---------|----------|---------|
| `trigger_outbox_updated_at` | `update_outbox_updated_at()` | Auto-set `updated_at` |
| `trigger_calculate_next_retry` | `calculate_next_retry()` | Exponential backoff: `2^retry_count` minutes |

**V6 Views:**

- `v_outbox_statistics` -- counts by status and event type
- `v_outbox_failed_entries` -- entries that exceeded max retries

**V6 Functions:**

| Function | Purpose |
|----------|---------|
| `get_pending_outbox_entries(batch_size, lock_timeout)` | SELECT FOR UPDATE SKIP LOCKED with priority ordering |
| `mark_outbox_completed(entry_id)` | Set status=COMPLETED |
| `mark_outbox_failed(entry_id, error_message)` | Set status=FAILED, increment retry |
| `cleanup_completed_outbox(older_than_days)` | Delete old completed entries |

## V7: Account Ledger Read Model

```sql
CREATE TABLE account_ledger_read_model (
    account_id          UUID PRIMARY KEY,
    account_number      VARCHAR(50) NOT NULL UNIQUE,
    account_type        VARCHAR(50) NOT NULL,
    customer_id         UUID NOT NULL,
    balance             DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    frozen              BOOLEAN NOT NULL DEFAULT FALSE,
    closed              BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    opened_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at           TIMESTAMP WITH TIME ZONE,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    version             BIGINT NOT NULL DEFAULT 0,
    last_updated        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT valid_status CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))
);
```

This is the example read model for the AccountLedger aggregate used in the tutorial.

## V8: Projection Tables

```sql
CREATE TABLE projection_positions (
    projection_name     VARCHAR(255) NOT NULL,
    position            BIGINT NOT NULL DEFAULT 0,
    last_updated        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_projection_positions PRIMARY KEY (projection_name)
);

CREATE TABLE account_balance_projections (
    id                  BIGSERIAL NOT NULL,
    account_id          UUID NOT NULL,
    balance             DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    last_updated        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_balance_projections PRIMARY KEY (id),
    CONSTRAINT uk_account_balance_projections_account_id UNIQUE (account_id)
);
```

`projection_positions` is the checkpoint table used by `ProjectionService` to track the last processed global sequence.

## Complete Index List

### events table

| Index | Columns | Condition |
|-------|---------|-----------|
| PK | `event_id` | |
| UNIQUE | `global_sequence` | |
| UNIQUE | `(aggregate_id, aggregate_version)` | |
| `idx_events_aggregate` | `(aggregate_id, aggregate_type)` | |
| `idx_events_global_sequence` | `(global_sequence)` | |
| `idx_events_type` | `(event_type)` | |
| `idx_events_created_at` | `(created_at)` | |
| `idx_events_aggregate_version` | `(aggregate_id, aggregate_version)` | |
| `idx_events_correlation_id` | `(correlation_id)` | `WHERE correlation_id IS NOT NULL` |
| `idx_events_causation_id` | `(causation_id)` | `WHERE causation_id IS NOT NULL` |
| `idx_events_tenant_id` | `(tenant_id)` | `WHERE tenant_id IS NOT NULL` |
| `idx_events_created_by` | `(created_by)` | `WHERE created_by IS NOT NULL` |
| `idx_events_aggregate_type_created` | `(aggregate_type, created_at DESC)` | |
| `idx_events_type_created` | `(event_type, created_at DESC)` | |
| `idx_events_tenant_aggregate` | `(tenant_id, aggregate_id)` | `WHERE tenant_id IS NOT NULL` |

### snapshots table

| Index | Columns | Condition |
|-------|---------|-----------|
| PK | `(aggregate_id, aggregate_type)` | |
| `idx_snapshots_version` | `(aggregate_version)` | |
| `idx_snapshots_created_at` | `(created_at)` | |
| `idx_snapshots_type` | `(aggregate_type)` | |
| `idx_snapshots_tenant_id` | `(tenant_id)` | `WHERE tenant_id IS NOT NULL` |
| `idx_snapshots_type_version` | `(aggregate_type, aggregate_version DESC)` | |
| `idx_snapshots_type_created` | `(aggregate_type, created_at DESC)` | |

### event_outbox table

| Index | Columns | Condition |
|-------|---------|-----------|
| PK | `outbox_id` | |
| `idx_outbox_status` | `(status)` | |
| `idx_outbox_created_at` | `(created_at)` | |
| `idx_outbox_next_retry` | `(next_retry_at)` | `WHERE status = 'FAILED'` |
| `idx_outbox_aggregate` | `(aggregate_id, aggregate_type)` | |
| `idx_outbox_event_type` | `(event_type)` | |
| `idx_outbox_pending` | `(created_at)` | `WHERE status = 'PENDING'` |
| `idx_outbox_priority` | `(priority DESC, created_at ASC)` | `WHERE status = 'PENDING'` |
| `idx_outbox_status_priority` | `(status, priority DESC, created_at ASC)` | |

## Test Schema

For H2-based tests, `src/test/resources/db/test-schema.sql` creates minimal versions of `projection_positions` and `account_balance_projections` tables.
