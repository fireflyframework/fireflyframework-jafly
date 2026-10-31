BEGIN;
SET LOCAL search_path TO pg_catalog;
LOCK TABLE {{outbox}} IN ACCESS EXCLUSIVE MODE;
ALTER TABLE {{outbox}}
    ADD COLUMN lease_token UUID,
    ADD COLUMN lease_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ;
COMMENT ON COLUMN {{outbox}}.lease_generation IS 'firefly.eda.delivery/v1';
CREATE INDEX {{dueIndex}} ON {{outbox}} (channel, next_attempt_at, created_at, id) WHERE status='PENDING';
CREATE INDEX {{leaseIndex}} ON {{outbox}} (channel, lease_until, created_at, id) WHERE status='IN_FLIGHT';
COMMIT;
