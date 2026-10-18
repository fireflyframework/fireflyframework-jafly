-- V5: Enhance Snapshots Table for Production
-- This migration adds production-ready features to the snapshots table

-- Add audit and tracking columns
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS snapshot_size_bytes INTEGER;
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS checksum VARCHAR(64);
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS compression_type VARCHAR(50);
ALTER TABLE snapshots ADD COLUMN IF NOT EXISTS is_compressed BOOLEAN DEFAULT FALSE;

-- Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_snapshots_tenant_id ON snapshots(tenant_id) WHERE tenant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snapshots_created_by ON snapshots(created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_snapshots_tenant_aggregate ON snapshots(tenant_id, aggregate_id) WHERE tenant_id IS NOT NULL;

-- Add composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_snapshots_type_version ON snapshots(aggregate_type, aggregate_version DESC);
CREATE INDEX IF NOT EXISTS idx_snapshots_type_created ON snapshots(aggregate_type, created_at DESC);

-- Add check constraints for data integrity
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_snapshot_version_positive') THEN
        ALTER TABLE snapshots ADD CONSTRAINT chk_snapshot_version_positive CHECK (aggregate_version > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_snapshot_type_not_empty') THEN
        ALTER TABLE snapshots ADD CONSTRAINT chk_snapshot_type_not_empty CHECK (aggregate_type <> '');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_compression_type_valid') THEN
        ALTER TABLE snapshots ADD CONSTRAINT chk_compression_type_valid CHECK (
            compression_type IS NULL OR compression_type IN ('GZIP', 'LZ4', 'ZSTD', 'NONE')
        );
    END IF;
END $$;

-- Add trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_snapshots_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_snapshots_updated_at ON snapshots;
CREATE TRIGGER trigger_snapshots_updated_at
    BEFORE UPDATE ON snapshots
    FOR EACH ROW
    EXECUTE FUNCTION update_snapshots_updated_at();

-- Add trigger to calculate snapshot size
CREATE OR REPLACE FUNCTION calculate_snapshot_size()
RETURNS TRIGGER AS $$
BEGIN
    NEW.snapshot_size_bytes = LENGTH(NEW.snapshot_data::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_calculate_snapshot_size ON snapshots;
CREATE TRIGGER trigger_calculate_snapshot_size
    BEFORE INSERT OR UPDATE ON snapshots
    FOR EACH ROW
    EXECUTE FUNCTION calculate_snapshot_size();

-- Add comments for new columns
COMMENT ON COLUMN snapshots.updated_at IS 'Timestamp when the snapshot was last updated';
COMMENT ON COLUMN snapshots.created_by IS 'User or service that created the snapshot';
COMMENT ON COLUMN snapshots.tenant_id IS 'Tenant identifier for multi-tenancy support';
COMMENT ON COLUMN snapshots.snapshot_size_bytes IS 'Size of the snapshot data in bytes';
COMMENT ON COLUMN snapshots.checksum IS 'SHA-256 checksum of snapshot data for integrity verification';
COMMENT ON COLUMN snapshots.compression_type IS 'Type of compression used (GZIP, LZ4, ZSTD, NONE)';
COMMENT ON COLUMN snapshots.is_compressed IS 'Whether the snapshot data is compressed';

-- Create view for snapshot statistics
CREATE OR REPLACE VIEW v_snapshot_statistics AS
SELECT 
    aggregate_type,
    COUNT(*) as snapshot_count,
    AVG(aggregate_version) as avg_version,
    MAX(aggregate_version) as max_version,
    AVG(snapshot_size_bytes) as avg_size_bytes,
    MAX(snapshot_size_bytes) as max_size_bytes,
    MIN(created_at) as first_snapshot_at,
    MAX(created_at) as last_snapshot_at,
    COUNT(DISTINCT aggregate_id) as unique_aggregates,
    SUM(CASE WHEN is_compressed THEN 1 ELSE 0 END) as compressed_count
FROM snapshots
GROUP BY aggregate_type;

COMMENT ON VIEW v_snapshot_statistics IS 'Aggregated statistics about snapshots by aggregate type';

-- Create function to clean up old snapshots (keep only latest N per aggregate)
CREATE OR REPLACE FUNCTION cleanup_old_snapshots(keep_count INTEGER DEFAULT 3)
RETURNS TABLE(deleted_count BIGINT) AS $$
DECLARE
    total_deleted BIGINT := 0;
BEGIN
    WITH ranked_snapshots AS (
        SELECT 
            aggregate_id,
            aggregate_type,
            aggregate_version,
            ROW_NUMBER() OVER (
                PARTITION BY aggregate_id, aggregate_type 
                ORDER BY aggregate_version DESC
            ) as rn
        FROM snapshots
    ),
    snapshots_to_delete AS (
        SELECT aggregate_id, aggregate_type, aggregate_version
        FROM ranked_snapshots
        WHERE rn > keep_count
    )
    DELETE FROM snapshots
    WHERE (aggregate_id, aggregate_type, aggregate_version) IN (
        SELECT aggregate_id, aggregate_type, aggregate_version 
        FROM snapshots_to_delete
    );
    
    GET DIAGNOSTICS total_deleted = ROW_COUNT;
    RETURN QUERY SELECT total_deleted;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_snapshots(INTEGER) IS 'Remove old snapshots, keeping only the latest N per aggregate';

