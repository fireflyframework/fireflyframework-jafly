BEGIN;
SET LOCAL search_path TO pg_catalog;
LOCK TABLE {{outbox}} IN ACCESS SHARE MODE;
CREATE TABLE {{registry}} (
 group_id pg_catalog.varchar(255) COLLATE pg_catalog."C" PRIMARY KEY,
 channels pg_catalog.text[] COLLATE pg_catalog."C" NOT NULL,
 max_attempts pg_catalog.int4 NOT NULL CHECK(max_attempts>=1),
 replay_policy pg_catalog.varchar(32) NOT NULL CHECK(replay_policy='ALL_RETAINED'),
 registered_at pg_catalog.timestamptz NOT NULL DEFAULT pg_catalog.clock_timestamp(),
 CHECK(pg_catalog.octet_length(group_id) BETWEEN 1 AND 255),
 CHECK(pg_catalog.cardinality(channels) BETWEEN 1 AND 256)
);
COMMENT ON TABLE {{registry}} IS 'firefly.eda.groups/v1:registry';
CREATE TABLE {{deliveries}} (
 delivery_id pg_catalog.uuid PRIMARY KEY DEFAULT pg_catalog.gen_random_uuid(),
 group_id pg_catalog.varchar(255) COLLATE pg_catalog."C" NOT NULL REFERENCES {{registry}}(group_id) ON DELETE RESTRICT,
 event_id pg_catalog.int8 NOT NULL REFERENCES {{outbox}}(id) ON DELETE RESTRICT,
 status pg_catalog.varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','IN_FLIGHT','PROCESSED','DEAD_LETTER')),
 attempts pg_catalog.int4 NOT NULL DEFAULT 0,
 attempt_ceiling pg_catalog.int4 NOT NULL,
 error_message pg_catalog.text,
 processed_at pg_catalog.timestamptz,
 failed_at pg_catalog.timestamptz,
 lease_token pg_catalog.uuid,
 lease_generation pg_catalog.int8 NOT NULL DEFAULT 0 CHECK(lease_generation>=0),
 lease_until pg_catalog.timestamptz,
 next_attempt_at pg_catalog.timestamptz,
 UNIQUE(group_id,event_id),
 CHECK(attempts>=0 AND attempt_ceiling>=1 AND attempts<=attempt_ceiling),
 CHECK((status='IN_FLIGHT')=(lease_until IS NOT NULL)),
 CHECK(status<>'IN_FLIGHT' OR (lease_token IS NOT NULL AND lease_generation>0)),
 CHECK(status='PENDING' OR next_attempt_at IS NULL),
 CHECK((status='PROCESSED')=(processed_at IS NOT NULL))
);
COMMENT ON TABLE {{deliveries}} IS 'firefly.eda.groups/v1:delivery';
CREATE INDEX {{dueIndex}} ON {{deliveries}}(group_id,next_attempt_at,event_id) WHERE status='PENDING';
CREATE INDEX {{leaseIndex}} ON {{deliveries}}(group_id,lease_until,event_id) WHERE status='IN_FLIGHT';
CREATE TABLE {{redrives}} (
 audit_id pg_catalog.uuid PRIMARY KEY DEFAULT pg_catalog.gen_random_uuid(),
 group_id pg_catalog.varchar(255) COLLATE pg_catalog."C" NOT NULL,
 event_id pg_catalog.int8 NOT NULL,
 command_id pg_catalog.uuid NOT NULL,
 terminal_token pg_catalog.uuid NOT NULL,
 terminal_generation pg_catalog.int8 NOT NULL CHECK(terminal_generation>0),
 additional_attempts pg_catalog.int4 NOT NULL CHECK(additional_attempts>0),
 old_ceiling pg_catalog.int4 NOT NULL,
 new_ceiling pg_catalog.int4 NOT NULL CHECK(new_ceiling>old_ceiling),
 attempts pg_catalog.int4 NOT NULL CHECK(attempts>=0),
 prior_failure_code pg_catalog.text,
 prior_failed_at pg_catalog.timestamptz,
 actor pg_catalog.varchar(255) COLLATE pg_catalog."C" NOT NULL,
 reason pg_catalog.text COLLATE pg_catalog."C" NOT NULL,
 redriven_at pg_catalog.timestamptz NOT NULL,
 UNIQUE(group_id,command_id),
 FOREIGN KEY(group_id,event_id) REFERENCES {{deliveries}}(group_id,event_id) ON DELETE RESTRICT,
 CHECK(pg_catalog.octet_length(actor) BETWEEN 1 AND 255),
 CHECK(pg_catalog.octet_length(reason) BETWEEN 1 AND 1024),
 CHECK(new_ceiling::pg_catalog.int8-old_ceiling::pg_catalog.int8=additional_attempts::pg_catalog.int8)
);
COMMENT ON TABLE {{redrives}} IS 'firefly.eda.groups/v1:audit';
CREATE FUNCTION {{guard}}() RETURNS pg_catalog.trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path TO pg_catalog
AS $ff_group_journal${{guardBody}}$ff_group_journal$;
CREATE TRIGGER {{mutationGuard}} BEFORE UPDATE OR DELETE ON {{redrives}}
FOR EACH STATEMENT EXECUTE FUNCTION {{guard}}();
CREATE TRIGGER {{truncateGuard}} BEFORE TRUNCATE ON {{redrives}}
FOR EACH STATEMENT EXECUTE FUNCTION {{guard}}();
ALTER TABLE {{redrives}} ENABLE ALWAYS TRIGGER {{mutationGuard}};
ALTER TABLE {{redrives}} ENABLE ALWAYS TRIGGER {{truncateGuard}};
COMMIT;
