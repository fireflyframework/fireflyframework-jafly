-- Create event outbox table for reliable message publishing (Transactional Outbox Pattern)
CREATE TABLE event_outbox (
    outbox_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    metadata TEXT,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER DEFAULT 0 NOT NULL,
    last_error TEXT,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT valid_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Create indexes for performance
CREATE INDEX idx_outbox_status ON event_outbox(status);
CREATE INDEX idx_outbox_created_at ON event_outbox(created_at);
CREATE INDEX idx_outbox_next_retry ON event_outbox(next_retry_at) WHERE status = 'FAILED';
CREATE INDEX idx_outbox_aggregate ON event_outbox(aggregate_id, aggregate_type);
CREATE INDEX idx_outbox_event_type ON event_outbox(event_type);
CREATE INDEX idx_outbox_pending ON event_outbox(created_at) WHERE status = 'PENDING';

-- Add comments for documentation
COMMENT ON TABLE event_outbox IS 'Transactional outbox for reliable event publishing';
COMMENT ON COLUMN event_outbox.outbox_id IS 'Unique identifier for the outbox entry';
COMMENT ON COLUMN event_outbox.aggregate_id IS 'Identifier of the aggregate that generated the event';
COMMENT ON COLUMN event_outbox.aggregate_type IS 'Type/class name of the aggregate';
COMMENT ON COLUMN event_outbox.event_type IS 'Type identifier for the event';
COMMENT ON COLUMN event_outbox.event_data IS 'JSON representation of the event data';
COMMENT ON COLUMN event_outbox.metadata IS 'Additional metadata for message publishing';
COMMENT ON COLUMN event_outbox.status IS 'Processing status of the outbox entry';
COMMENT ON COLUMN event_outbox.created_at IS 'Timestamp when the entry was created';
COMMENT ON COLUMN event_outbox.processed_at IS 'Timestamp when the entry was successfully processed';
COMMENT ON COLUMN event_outbox.retry_count IS 'Number of processing retry attempts';
COMMENT ON COLUMN event_outbox.last_error IS 'Error message from the last failed attempt';
COMMENT ON COLUMN event_outbox.next_retry_at IS 'Scheduled time for the next retry attempt';