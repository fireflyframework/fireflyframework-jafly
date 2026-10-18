-- V4: Enhance Events Table for Production
-- This migration adds production-ready features to the events table

-- Add audit and tracking columns
ALTER TABLE events ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS causation_id VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS event_size_bytes INTEGER;
ALTER TABLE events ADD COLUMN IF NOT EXISTS checksum VARCHAR(64);

-- Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_events_correlation_id ON events(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_causation_id ON events(causation_id) WHERE causation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_tenant_id ON events(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_created_by ON events(created_by) WHERE created_by IS NOT NULL;

-- Add composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_events_aggregate_type_created ON events(aggregate_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_events_type_created ON events(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_events_tenant_aggregate ON events(tenant_id, aggregate_id) WHERE tenant_id IS NOT NULL;

-- Note: GIN index for JSONB metadata removed for database compatibility (columns are now TEXT)

-- NOTE: Partial indexes with NOW() are not used here because NOW() is STABLE, not IMMUTABLE
-- Applications should use appropriate WHERE clauses in queries for time-based filtering
-- The idx_events_aggregate_type_created index will handle most time-based queries efficiently

-- Add check constraints for data integrity
-- Note: Using DO blocks to handle constraint creation idempotently
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_aggregate_version_positive') THEN
        ALTER TABLE events ADD CONSTRAINT chk_aggregate_version_positive CHECK (aggregate_version >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_event_type_not_empty') THEN
        ALTER TABLE events ADD CONSTRAINT chk_event_type_not_empty CHECK (event_type <> '');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_aggregate_type_not_empty') THEN
        ALTER TABLE events ADD CONSTRAINT chk_aggregate_type_not_empty CHECK (aggregate_type <> '');
    END IF;
END $$;

-- Add trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_events_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_events_updated_at ON events;
CREATE TRIGGER trigger_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW
    EXECUTE FUNCTION update_events_updated_at();

-- Add trigger to calculate event size
CREATE OR REPLACE FUNCTION calculate_event_size()
RETURNS TRIGGER AS $$
BEGIN
    NEW.event_size_bytes = LENGTH(NEW.event_data::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_calculate_event_size ON events;
CREATE TRIGGER trigger_calculate_event_size
    BEFORE INSERT OR UPDATE ON events
    FOR EACH ROW
    EXECUTE FUNCTION calculate_event_size();

-- Add comments for new columns
COMMENT ON COLUMN events.updated_at IS 'Timestamp when the event was last updated (for corrections/migrations)';
COMMENT ON COLUMN events.created_by IS 'User or service that created the event';
COMMENT ON COLUMN events.tenant_id IS 'Tenant identifier for multi-tenancy support';
COMMENT ON COLUMN events.correlation_id IS 'Correlation ID for distributed tracing';
COMMENT ON COLUMN events.causation_id IS 'ID of the event that caused this event';
COMMENT ON COLUMN events.event_size_bytes IS 'Size of the event data in bytes';
COMMENT ON COLUMN events.checksum IS 'SHA-256 checksum of event data for integrity verification';

-- Create view for event statistics
CREATE OR REPLACE VIEW v_event_statistics AS
SELECT 
    aggregate_type,
    event_type,
    COUNT(*) as event_count,
    AVG(event_size_bytes) as avg_size_bytes,
    MAX(event_size_bytes) as max_size_bytes,
    MIN(created_at) as first_event_at,
    MAX(created_at) as last_event_at,
    COUNT(DISTINCT aggregate_id) as unique_aggregates
FROM events
GROUP BY aggregate_type, event_type;

COMMENT ON VIEW v_event_statistics IS 'Aggregated statistics about events by type';

-- Create view for recent events (last 24 hours)
CREATE OR REPLACE VIEW v_recent_events AS
SELECT 
    event_id,
    aggregate_id,
    aggregate_type,
    event_type,
    aggregate_version,
    global_sequence,
    created_at,
    correlation_id,
    tenant_id
FROM events
WHERE created_at > NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC;

COMMENT ON VIEW v_recent_events IS 'Events created in the last 24 hours';

-- Create materialized view for aggregate summaries (refresh periodically)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_aggregate_summary AS
SELECT 
    aggregate_id,
    aggregate_type,
    MAX(aggregate_version) as current_version,
    COUNT(*) as total_events,
    MIN(created_at) as created_at,
    MAX(created_at) as last_modified_at,
    SUM(event_size_bytes) as total_size_bytes,
    tenant_id
FROM events
GROUP BY aggregate_id, aggregate_type, tenant_id;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_aggregate_summary_pk ON mv_aggregate_summary(aggregate_id, aggregate_type);
CREATE INDEX IF NOT EXISTS idx_mv_aggregate_summary_type ON mv_aggregate_summary(aggregate_type);
CREATE INDEX IF NOT EXISTS idx_mv_aggregate_summary_tenant ON mv_aggregate_summary(tenant_id) WHERE tenant_id IS NOT NULL;

COMMENT ON MATERIALIZED VIEW mv_aggregate_summary IS 'Summary statistics for each aggregate (refresh periodically)';

-- Add function to refresh materialized view
CREATE OR REPLACE FUNCTION refresh_aggregate_summary()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_aggregate_summary;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_aggregate_summary() IS 'Refresh the aggregate summary materialized view';

