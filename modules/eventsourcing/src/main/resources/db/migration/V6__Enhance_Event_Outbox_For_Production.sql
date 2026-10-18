-- V6: Enhance Event Outbox Table for Production
-- This migration adds production-ready features to the event outbox table

-- Add audit and tracking columns
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 5;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS max_retries INTEGER DEFAULT 3;
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS partition_key VARCHAR(255);

-- Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_id ON event_outbox(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_correlation_id ON event_outbox(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_priority ON event_outbox(priority DESC, created_at ASC) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_partition_key ON event_outbox(partition_key) WHERE partition_key IS NOT NULL;

-- Add composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_outbox_status_priority ON event_outbox(status, priority DESC, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_status ON event_outbox(tenant_id, status) WHERE tenant_id IS NOT NULL;

-- Add check constraints for data integrity
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_outbox_priority_range') THEN
        ALTER TABLE event_outbox ADD CONSTRAINT chk_outbox_priority_range CHECK (priority BETWEEN 1 AND 10);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_outbox_max_retries_positive') THEN
        ALTER TABLE event_outbox ADD CONSTRAINT chk_outbox_max_retries_positive CHECK (max_retries >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_outbox_retry_count_valid') THEN
        ALTER TABLE event_outbox ADD CONSTRAINT chk_outbox_retry_count_valid CHECK (retry_count >= 0 AND retry_count <= max_retries + 10);
    END IF;
END $$;

-- Add trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_outbox_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_outbox_updated_at ON event_outbox;
CREATE TRIGGER trigger_outbox_updated_at
    BEFORE UPDATE ON event_outbox
    FOR EACH ROW
    EXECUTE FUNCTION update_outbox_updated_at();

-- Add trigger to calculate next retry time with exponential backoff
CREATE OR REPLACE FUNCTION calculate_next_retry()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'FAILED' AND NEW.retry_count < NEW.max_retries THEN
        -- Exponential backoff: 2^retry_count minutes
        NEW.next_retry_at = NOW() + (POWER(2, NEW.retry_count) || ' minutes')::INTERVAL;
    ELSE
        NEW.next_retry_at = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_calculate_next_retry ON event_outbox;
CREATE TRIGGER trigger_calculate_next_retry
    BEFORE INSERT OR UPDATE ON event_outbox
    FOR EACH ROW
    WHEN (NEW.status = 'FAILED')
    EXECUTE FUNCTION calculate_next_retry();

-- Add comments for new columns
COMMENT ON COLUMN event_outbox.updated_at IS 'Timestamp when the outbox entry was last updated';
COMMENT ON COLUMN event_outbox.tenant_id IS 'Tenant identifier for multi-tenancy support';
COMMENT ON COLUMN event_outbox.correlation_id IS 'Correlation ID for distributed tracing';
COMMENT ON COLUMN event_outbox.priority IS 'Processing priority (1=highest, 10=lowest)';
COMMENT ON COLUMN event_outbox.max_retries IS 'Maximum number of retry attempts';
COMMENT ON COLUMN event_outbox.partition_key IS 'Partition key for ordered processing';

-- Create view for outbox statistics
CREATE OR REPLACE VIEW v_outbox_statistics AS
SELECT 
    status,
    event_type,
    COUNT(*) as entry_count,
    AVG(retry_count) as avg_retry_count,
    MAX(retry_count) as max_retry_count,
    MIN(created_at) as oldest_entry,
    MAX(created_at) as newest_entry,
    COUNT(DISTINCT aggregate_id) as unique_aggregates
FROM event_outbox
GROUP BY status, event_type;

COMMENT ON VIEW v_outbox_statistics IS 'Aggregated statistics about outbox entries';

-- Create view for failed entries that need attention
CREATE OR REPLACE VIEW v_outbox_failed_entries AS
SELECT 
    outbox_id,
    aggregate_id,
    aggregate_type,
    event_type,
    status,
    retry_count,
    max_retries,
    last_error,
    created_at,
    next_retry_at,
    correlation_id
FROM event_outbox
WHERE status = 'FAILED' AND retry_count >= max_retries
ORDER BY created_at ASC;

COMMENT ON VIEW v_outbox_failed_entries IS 'Failed outbox entries that have exceeded max retries';

-- Create function to process pending outbox entries
CREATE OR REPLACE FUNCTION get_pending_outbox_entries(
    batch_size INTEGER DEFAULT 100,
    lock_timeout_seconds INTEGER DEFAULT 30
)
RETURNS TABLE(
    outbox_id UUID,
    aggregate_id UUID,
    aggregate_type VARCHAR,
    event_type VARCHAR,
    event_data TEXT,
    metadata TEXT,
    correlation_id VARCHAR,
    priority INTEGER
) AS $$
BEGIN
    RETURN QUERY
    UPDATE event_outbox
    SET status = 'PROCESSING',
        updated_at = NOW()
    WHERE outbox_id IN (
        SELECT o.outbox_id
        FROM event_outbox o
        WHERE o.status = 'PENDING'
           OR (o.status = 'FAILED' AND o.next_retry_at <= NOW())
        ORDER BY o.priority DESC, o.created_at ASC
        LIMIT batch_size
        FOR UPDATE SKIP LOCKED
    )
    RETURNING 
        event_outbox.outbox_id,
        event_outbox.aggregate_id,
        event_outbox.aggregate_type,
        event_outbox.event_type,
        event_outbox.event_data,
        event_outbox.metadata,
        event_outbox.correlation_id,
        event_outbox.priority;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_pending_outbox_entries(INTEGER, INTEGER) IS 'Get and lock pending outbox entries for processing';

-- Create function to mark outbox entry as completed
CREATE OR REPLACE FUNCTION mark_outbox_completed(entry_id UUID)
RETURNS void AS $$
BEGIN
    UPDATE event_outbox
    SET status = 'COMPLETED',
        processed_at = NOW(),
        updated_at = NOW()
    WHERE outbox_id = entry_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION mark_outbox_completed(UUID) IS 'Mark an outbox entry as successfully completed';

-- Create function to mark outbox entry as failed
CREATE OR REPLACE FUNCTION mark_outbox_failed(entry_id UUID, error_message TEXT)
RETURNS void AS $$
BEGIN
    UPDATE event_outbox
    SET status = 'FAILED',
        retry_count = retry_count + 1,
        last_error = error_message,
        updated_at = NOW()
    WHERE outbox_id = entry_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION mark_outbox_failed(UUID, TEXT) IS 'Mark an outbox entry as failed with error message';

-- Create function to cleanup old completed entries
CREATE OR REPLACE FUNCTION cleanup_completed_outbox(older_than_days INTEGER DEFAULT 7)
RETURNS TABLE(deleted_count BIGINT) AS $$
DECLARE
    total_deleted BIGINT := 0;
BEGIN
    DELETE FROM event_outbox
    WHERE status = 'COMPLETED'
      AND processed_at < NOW() - (older_than_days || ' days')::INTERVAL;
    
    GET DIAGNOSTICS total_deleted = ROW_COUNT;
    RETURN QUERY SELECT total_deleted;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_completed_outbox(INTEGER) IS 'Remove completed outbox entries older than specified days';

