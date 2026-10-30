-- Explicit opt-in local audit installation; not a default Flyway migration.
-- Provision roles separately. Technical scope is not actor authentication or owner permission.
BEGIN;
DO $$ BEGIN
 IF NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET')
    OR NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE WITH GRANT OPTION')
    OR pg_catalog.has_schema_privilege('firefly_audit_guard','public','CREATE') THEN
  RAISE EXCEPTION 'Separately delegated audit installer required' USING ERRCODE='42501';
 END IF;
 IF (SELECT count(*) FROM pg_catalog.pg_roles WHERE rolname IN('firefly_audit_guard','firefly_audit_writer','firefly_audit_reader')
    AND NOT rolsuper AND NOT rolbypassrls AND NOT rolinherit AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication
    AND rolcanlogin=(rolname<>'firefly_audit_guard'))<>3
    OR pg_catalog.pg_has_role('firefly_audit_writer','firefly_audit_guard','MEMBER')
    OR pg_catalog.pg_has_role('firefly_audit_reader','firefly_audit_guard','MEMBER') THEN
  RAISE EXCEPTION 'Exact preprovisioned audit roles required' USING ERRCODE='42501';
 END IF;
END $$;
CREATE SCHEMA firefly_audit AUTHORIZATION firefly_audit_guard;
CREATE SCHEMA firefly_audit_private AUTHORIZATION firefly_audit_guard;
GRANT CREATE ON SCHEMA public TO firefly_audit_guard;
SET LOCAL ROLE firefly_audit_guard;
REVOKE ALL ON SCHEMA firefly_audit,firefly_audit_private FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
GRANT USAGE ON SCHEMA firefly_audit TO firefly_audit_writer,firefly_audit_reader;
CREATE TABLE firefly_audit.audit_head (
 resource_tenant_id UUID NOT NULL,service TEXT NOT NULL,resource_type TEXT NOT NULL,resource_id UUID NOT NULL,
 last_sequence BIGINT NOT NULL CHECK(last_sequence>=1),last_hash TEXT NOT NULL CHECK(last_hash ~ '^[0-9a-f]{64}$'),
 PRIMARY KEY(resource_tenant_id,service,resource_type,resource_id)
);
CREATE TABLE firefly_audit.audit_event (
 resource_tenant_id UUID NOT NULL,service TEXT NOT NULL,resource_type TEXT NOT NULL,resource_id UUID NOT NULL,event_id UUID NOT NULL,
 sequence BIGINT NOT NULL CHECK(sequence>=1),previous_hash TEXT NOT NULL CHECK(previous_hash ~ '^[0-9a-f]{64}$'),record_hash TEXT NOT NULL CHECK(record_hash ~ '^[0-9a-f]{64}$'),
 recorded_seconds BIGINT NOT NULL,recorded_nanos INTEGER NOT NULL CHECK(recorded_nanos BETWEEN 0 AND 999999999),
 actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,actor_client_id TEXT NOT NULL,actor_platform_tenant_id TEXT NOT NULL,
 actor_staff_realm TEXT,actor_staff_id UUID,action TEXT NOT NULL,outcome TEXT NOT NULL,
 process_id UUID NOT NULL,correlation_id UUID NOT NULL,causation_id UUID,occurred_seconds BIGINT NOT NULL,
 occurred_nanos INTEGER NOT NULL CHECK(occurred_nanos BETWEEN 0 AND 999999999),evidence_digest TEXT NOT NULL CHECK(evidence_digest ~ '^[0-9a-f]{64}$'),policy_revision TEXT NOT NULL,
 parent_sequence BIGINT GENERATED ALWAYS AS(CASE WHEN sequence=1 THEN NULL ELSE sequence-1 END) STORED,
 PRIMARY KEY(resource_tenant_id,event_id),UNIQUE(resource_tenant_id,service,resource_type,resource_id,sequence,record_hash),
 UNIQUE(resource_tenant_id,service,resource_type,resource_id,sequence),
 CHECK((actor_staff_realm IS NULL)=(actor_staff_id IS NULL)),
 CHECK(sequence<>1 OR previous_hash=repeat('0',64)),
 FOREIGN KEY(resource_tenant_id,service,resource_type,resource_id,parent_sequence,previous_hash)
  REFERENCES firefly_audit.audit_event(resource_tenant_id,service,resource_type,resource_id,sequence,record_hash) DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE firefly_audit.audit_outbox (
 resource_tenant_id UUID NOT NULL,event_id UUID NOT NULL,service TEXT NOT NULL,resource_type TEXT NOT NULL,resource_id UUID NOT NULL,
 sequence BIGINT NOT NULL,record_hash TEXT NOT NULL,intent TEXT NOT NULL CHECK(intent='AUDIT_EXPORT'),
 PRIMARY KEY(resource_tenant_id,event_id),
 FOREIGN KEY(resource_tenant_id,event_id) REFERENCES firefly_audit.audit_event(resource_tenant_id,event_id) DEFERRABLE INITIALLY DEFERRED
);
ALTER TABLE firefly_audit.audit_head ADD CONSTRAINT audit_head_record_fk
 FOREIGN KEY(resource_tenant_id,service,resource_type,resource_id,last_sequence,last_hash)
 REFERENCES firefly_audit.audit_event(resource_tenant_id,service,resource_type,resource_id,sequence,record_hash) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE firefly_audit_private.context (
 nonce UUID PRIMARY KEY,xid XID8 NOT NULL,backend_pid INTEGER NOT NULL,db_session_user NAME NOT NULL,
 resource_tenant_id UUID NOT NULL,service TEXT NOT NULL,resource_type TEXT NOT NULL,resource_id UUID NOT NULL,event_id UUID NOT NULL,
 actor_issuer TEXT NOT NULL,actor_subject TEXT NOT NULL,actor_client_id TEXT NOT NULL,actor_platform_tenant_id TEXT NOT NULL,actor_staff_realm TEXT,actor_staff_id UUID,
 installed_at TIMESTAMPTZ NOT NULL,until_time TIMESTAMPTZ NOT NULL,new_event BOOLEAN NOT NULL,
 CHECK(isfinite(installed_at) AND isfinite(until_time) AND until_time>installed_at AND until_time<=installed_at+INTERVAL '5 seconds')
);
CREATE TABLE firefly_audit_private.completion (nonce UUID PRIMARY KEY REFERENCES firefly_audit_private.context(nonce));
REVOKE ALL ON ALL TABLES IN SCHEMA firefly_audit_private FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
CREATE FUNCTION firefly_audit_private.context_for(p_nonce UUID) RETURNS firefly_audit_private.context
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c firefly_audit_private.context%ROWTYPE;observed TIMESTAMPTZ;
BEGIN
 SELECT * INTO c FROM firefly_audit_private.context WHERE nonce=p_nonce AND xid=pg_catalog.pg_current_xact_id_if_assigned()
  AND backend_pid=pg_catalog.pg_backend_pid() AND db_session_user=session_user;
 observed:=pg_catalog.clock_timestamp();
 IF NOT FOUND OR session_user<>'firefly_audit_writer' OR observed<c.installed_at OR observed>=c.until_time THEN
  RAISE EXCEPTION 'Original active audit scope required' USING ERRCODE='42501';
 END IF;
 RETURN c;
END $$;
CREATE FUNCTION public.firefly_audit_scope_matches(p_tenant UUID,p_service TEXT,p_type TEXT,p_resource UUID,p_event UUID,p_write BOOLEAN) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
 RETURN session_user='firefly_audit_writer' AND EXISTS(SELECT 1 FROM firefly_audit_private.context c
  WHERE c.xid=pg_catalog.pg_current_xact_id_if_assigned() AND c.backend_pid=pg_catalog.pg_backend_pid() AND c.db_session_user=session_user
  AND c.resource_tenant_id=p_tenant AND c.service=p_service AND c.resource_type=p_type AND c.resource_id=p_resource
  AND(p_event IS NULL OR c.event_id=p_event) AND(NOT p_write OR c.new_event)
  AND pg_catalog.clock_timestamp()>=c.installed_at AND pg_catalog.clock_timestamp()<c.until_time
  AND NOT EXISTS(SELECT 1 FROM firefly_audit_private.completion done WHERE done.nonce=c.nonce));
END $$;
CREATE FUNCTION public.firefly_audit_enter(p_nonce UUID,p_tenant UUID,p_service TEXT,p_type TEXT,p_resource UUID,p_event UUID,
 p_issuer TEXT,p_subject TEXT,p_client TEXT,p_platform TEXT,p_realm TEXT,p_staff UUID,p_until TIMESTAMPTZ)
RETURNS TABLE(context_nonce UUID,event_id UUID,db_now_millis BIGINT,until_millis BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE observed TIMESTAMPTZ;cap TIMESTAMPTZ;r pg_catalog.pg_roles%ROWTYPE;fresh BOOLEAN;
BEGIN
 SELECT * INTO r FROM pg_catalog.pg_roles WHERE rolname=session_user;
 IF session_user<>'firefly_audit_writer' OR r.rolsuper OR r.rolbypassrls OR r.rolinherit OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication
  OR pg_catalog.pg_has_role(session_user,'firefly_audit_guard','MEMBER') OR pg_catalog.has_schema_privilege(session_user,'public','CREATE')
  OR pg_catalog.has_schema_privilege(session_user,'firefly_audit_private','USAGE') THEN
  RAISE EXCEPTION 'Restricted audit business writer required' USING ERRCODE='42501';
 END IF;
 IF p_nonce IS NULL OR p_nonce='00000000-0000-0000-0000-000000000000'::uuid OR p_tenant IS NULL OR p_resource IS NULL OR p_event IS NULL
  OR p_service IS NULL OR p_service !~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$' OR p_type IS NULL OR p_type !~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$'
  OR p_issuer IS NULL OR length(p_issuer) NOT BETWEEN 1 AND 2048 OR p_subject IS NULL OR length(p_subject) NOT BETWEEN 1 AND 2048
  OR p_client IS NULL OR length(p_client) NOT BETWEEN 1 AND 2048 OR p_platform IS NULL OR length(p_platform) NOT BETWEEN 1 AND 2048
  OR(p_realm IS NULL)<>(p_staff IS NULL) OR(p_realm IS NOT NULL AND length(p_realm) NOT BETWEEN 1 AND 2048)
  OR p_until IS NULL OR NOT isfinite(p_until) THEN
  RAISE EXCEPTION 'Exact admitted audit values required' USING ERRCODE='42501';
 END IF;
 IF EXISTS(SELECT 1 FROM firefly_audit_private.context c WHERE c.xid=pg_catalog.pg_current_xact_id_if_assigned()
  AND c.backend_pid=pg_catalog.pg_backend_pid() AND c.db_session_user=session_user
  AND NOT EXISTS(SELECT 1 FROM firefly_audit_private.completion done WHERE done.nonce=c.nonce)) THEN
  RAISE EXCEPTION 'Previous audit append must complete first' USING ERRCODE='42501';
 END IF;
 -- Stable event identity first, then stream, for every legitimate append/replay.
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('audit-event:'||p_tenant::text||':'||p_event::text,0));
 PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('audit-stream:'||p_tenant::text||':'||p_service||':'||p_type||':'||p_resource::text,0));
 observed:=pg_catalog.date_trunc('milliseconds',pg_catalog.clock_timestamp());cap:=LEAST(p_until,observed+INTERVAL '5 seconds');
 IF cap<=observed THEN RAISE EXCEPTION 'Original audit deadline required' USING ERRCODE='42501';END IF;
 SELECT NOT EXISTS(SELECT 1 FROM firefly_audit.audit_event a WHERE a.resource_tenant_id=p_tenant AND a.event_id=p_event) INTO fresh;
 INSERT INTO firefly_audit_private.context VALUES(p_nonce,pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),session_user,p_tenant,p_service,p_type,p_resource,p_event,p_issuer,p_subject,p_client,p_platform,p_realm,p_staff,observed,cap,fresh);
 RETURN QUERY SELECT p_nonce,p_event,pg_catalog.floor(extract(epoch FROM observed)*1000)::bigint,pg_catalog.floor(extract(epoch FROM cap)*1000)::bigint;
END $$;
CREATE FUNCTION firefly_audit_private.require_graph(c firefly_audit_private.context) RETURNS VOID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE a firefly_audit.audit_event%ROWTYPE;
BEGIN
 SELECT * INTO a FROM firefly_audit.audit_event e WHERE e.resource_tenant_id=c.resource_tenant_id AND e.event_id=c.event_id
  AND e.service=c.service AND e.resource_type=c.resource_type AND e.resource_id=c.resource_id
  AND e.actor_issuer=c.actor_issuer AND e.actor_subject=c.actor_subject AND e.actor_client_id=c.actor_client_id
  AND e.actor_platform_tenant_id=c.actor_platform_tenant_id AND e.actor_staff_realm IS NOT DISTINCT FROM c.actor_staff_realm
  AND e.actor_staff_id IS NOT DISTINCT FROM c.actor_staff_id;
 IF NOT FOUND OR(c.new_event AND(a.recorded_seconds<>pg_catalog.floor(extract(epoch FROM c.installed_at))::bigint
  OR a.recorded_nanos<>((extract(epoch FROM c.installed_at)-pg_catalog.floor(extract(epoch FROM c.installed_at)))*1000000000)::integer))
  OR NOT EXISTS(SELECT 1 FROM firefly_audit.audit_outbox o WHERE o.resource_tenant_id=a.resource_tenant_id AND o.event_id=a.event_id
    AND o.service=a.service AND o.resource_type=a.resource_type AND o.resource_id=a.resource_id AND o.sequence=a.sequence AND o.record_hash=a.record_hash AND o.intent='AUDIT_EXPORT')
  OR NOT EXISTS(SELECT 1 FROM firefly_audit.audit_head h WHERE h.resource_tenant_id=a.resource_tenant_id AND h.service=a.service AND h.resource_type=a.resource_type AND h.resource_id=a.resource_id
    AND h.last_sequence>=a.sequence AND(h.last_sequence<>a.sequence OR h.last_hash=a.record_hash)) THEN
  RAISE EXCEPTION 'Complete immutable audit event and permanent outbox required' USING ERRCODE='23514';
 END IF;
END $$;
CREATE FUNCTION public.firefly_audit_complete(p_nonce UUID,p_sequence BIGINT,p_hash TEXT) RETURNS UUID
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c firefly_audit_private.context%ROWTYPE;
BEGIN
 c:=firefly_audit_private.context_for(p_nonce);PERFORM firefly_audit_private.require_graph(c);
 IF NOT EXISTS(SELECT 1 FROM firefly_audit.audit_event a WHERE a.resource_tenant_id=c.resource_tenant_id AND a.event_id=c.event_id AND a.sequence=p_sequence AND a.record_hash=p_hash) THEN
  RAISE EXCEPTION 'Exact audit completion required' USING ERRCODE='23514';
 END IF;
 INSERT INTO firefly_audit_private.completion VALUES(p_nonce);PERFORM firefly_audit_private.context_for(p_nonce);RETURN p_nonce;
END $$;
CREATE FUNCTION firefly_audit_private.deferred_complete() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c firefly_audit_private.context%ROWTYPE;
BEGIN
 c:=firefly_audit_private.context_for(NEW.nonce);
 IF NOT EXISTS(SELECT 1 FROM firefly_audit_private.completion done WHERE done.nonce=c.nonce) THEN
  RAISE EXCEPTION 'Completed audit append required' USING ERRCODE='23514';
 END IF;
 PERFORM firefly_audit_private.require_graph(c);RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER complete_audit_scope AFTER INSERT ON firefly_audit_private.context DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION firefly_audit_private.deferred_complete();
CREATE FUNCTION firefly_audit_private.immutable_original() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path=pg_catalog AS $$ BEGIN RAISE EXCEPTION 'Immutable audit original required' USING ERRCODE='42501';END $$;
CREATE TRIGGER immutable_audit_original BEFORE UPDATE OR DELETE ON firefly_audit.audit_event FOR EACH ROW EXECUTE FUNCTION firefly_audit_private.immutable_original();
CREATE TRIGGER immutable_audit_original BEFORE UPDATE OR DELETE ON firefly_audit.audit_outbox FOR EACH ROW EXECUTE FUNCTION firefly_audit_private.immutable_original();
CREATE FUNCTION firefly_audit_private.advance_head() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN
 IF ROW(NEW.resource_tenant_id,NEW.service,NEW.resource_type,NEW.resource_id) IS DISTINCT FROM ROW(OLD.resource_tenant_id,OLD.service,OLD.resource_type,OLD.resource_id)
  OR OLD.last_sequence=9223372036854775807 OR NEW.last_sequence<>OLD.last_sequence+1 THEN
  RAISE EXCEPTION 'Monotonic audit head required' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER advance_audit_head BEFORE UPDATE ON firefly_audit.audit_head FOR EACH ROW EXECUTE FUNCTION firefly_audit_private.advance_head();
ALTER TABLE firefly_audit.audit_head ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_audit.audit_head FORCE ROW LEVEL SECURITY;
ALTER TABLE firefly_audit.audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_audit.audit_event FORCE ROW LEVEL SECURITY;
ALTER TABLE firefly_audit.audit_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_audit.audit_outbox FORCE ROW LEVEL SECURITY;
DO $$ DECLARE relation TEXT;BEGIN
 FOREACH relation IN ARRAY ARRAY['audit_head','audit_event','audit_outbox'] LOOP
  EXECUTE pg_catalog.format('REVOKE ALL ON firefly_audit.%I FROM PUBLIC,firefly_audit_writer,firefly_audit_reader',relation);
  EXECUTE pg_catalog.format('CREATE POLICY audit_guard_integrity ON firefly_audit.%I TO firefly_audit_guard USING(true) WITH CHECK(true)',relation);
  EXECUTE pg_catalog.format('CREATE POLICY audit_scoped_read ON firefly_audit.%I FOR SELECT TO firefly_audit_writer USING(public.firefly_audit_scope_matches(resource_tenant_id,service,resource_type,resource_id,%s,false))',relation,CASE WHEN relation='audit_head' THEN 'NULL' ELSE 'event_id' END);
  EXECUTE pg_catalog.format('CREATE POLICY audit_scoped_insert ON firefly_audit.%I FOR INSERT TO firefly_audit_writer WITH CHECK(public.firefly_audit_scope_matches(resource_tenant_id,service,resource_type,resource_id,%s,true))',relation,CASE WHEN relation='audit_head' THEN 'NULL' ELSE 'event_id' END);
 END LOOP;
END $$;
CREATE POLICY audit_head_cas ON firefly_audit.audit_head FOR UPDATE TO firefly_audit_writer
 USING(public.firefly_audit_scope_matches(resource_tenant_id,service,resource_type,resource_id,NULL,true))
 WITH CHECK(public.firefly_audit_scope_matches(resource_tenant_id,service,resource_type,resource_id,NULL,true));
GRANT SELECT,INSERT ON firefly_audit.audit_event TO firefly_audit_writer;
GRANT SELECT,INSERT ON firefly_audit.audit_outbox TO firefly_audit_writer;
GRANT SELECT,INSERT,UPDATE(last_sequence,last_hash) ON firefly_audit.audit_head TO firefly_audit_writer;
GRANT SELECT ON firefly_audit.audit_head,firefly_audit.audit_event,firefly_audit.audit_outbox TO firefly_audit_reader;
-- Reader RLS has no default policy; an independently admitted export/read adapter is a later gate.
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA firefly_audit_private FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
REVOKE ALL ON FUNCTION public.firefly_audit_enter(UUID,UUID,TEXT,TEXT,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,TIMESTAMPTZ),public.firefly_audit_complete(UUID,BIGINT,TEXT),public.firefly_audit_scope_matches(UUID,TEXT,TEXT,UUID,UUID,BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.firefly_audit_enter(UUID,UUID,TEXT,TEXT,UUID,UUID,TEXT,TEXT,TEXT,TEXT,TEXT,UUID,TIMESTAMPTZ),public.firefly_audit_complete(UUID,BIGINT,TEXT),public.firefly_audit_scope_matches(UUID,TEXT,TEXT,UUID,UUID,BOOLEAN) TO firefly_audit_writer;
RESET ROLE;
REVOKE CREATE ON SCHEMA public FROM firefly_audit_guard;
DO $$ BEGIN IF pg_catalog.has_schema_privilege('firefly_audit_guard','public','CREATE') THEN RAISE EXCEPTION 'Audit temporary installation grant cleanup required' USING ERRCODE='42501';END IF;END $$;
COMMIT;
