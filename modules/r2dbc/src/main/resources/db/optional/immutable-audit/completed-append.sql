-- Optional read-only completion accessor after postgresql.sql; no default consumer grant.
BEGIN;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='5s';
DO $$ DECLARE guard OID; BEGIN
 SELECT oid INTO guard FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls;
 IF guard IS NULL OR NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET')
  OR NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE WITH GRANT OPTION')
  OR pg_catalog.has_schema_privilege('firefly_audit_guard','public','CREATE')
  OR EXISTS(SELECT 1 FROM (VALUES(to_regprocedure('firefly_audit_private.context_for(uuid)')),
    (to_regprocedure('firefly_audit_private.require_graph(firefly_audit_private.context)'))) wanted(id)
   LEFT JOIN pg_catalog.pg_proc p ON p.oid=wanted.id
   WHERE p.oid IS NULL OR p.proowner<>guard OR NOT p.prosecdef OR p.provolatile<>'v'
    OR p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']) THEN
  RAISE EXCEPTION 'Original restricted audit guards required' USING ERRCODE='42501';
 END IF;
END $$;
GRANT CREATE ON SCHEMA public TO firefly_audit_guard;
SET LOCAL ROLE firefly_audit_guard;
CREATE FUNCTION public.firefly_audit_require_completed(p_nonce UUID,p_event UUID,p_sequence BIGINT,p_hash TEXT,p_replay BOOLEAN)
RETURNS TABLE(context_nonce UUID,db_now_millis BIGINT,until_millis BIGINT,replay BOOLEAN,
 resource_tenant_id UUID,service TEXT,resource_type TEXT,resource_id UUID,event_id UUID,
 sequence BIGINT,previous_hash TEXT,record_hash TEXT,recorded_seconds BIGINT,recorded_nanos INTEGER,
 actor_issuer TEXT,actor_subject TEXT,actor_client_id TEXT,actor_platform_tenant_id TEXT,
 actor_staff_realm TEXT,actor_staff_id UUID,action TEXT,outcome TEXT,process_id UUID,correlation_id UUID,
 causation_id UUID,occurred_seconds BIGINT,occurred_nanos INTEGER,evidence_digest TEXT,policy_revision TEXT)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE c firefly_audit_private.context;again firefly_audit_private.context;
 a firefly_audit.audit_event;observed TIMESTAMPTZ;
BEGIN
 IF p_nonce IS NULL OR p_nonce='00000000-0000-0000-0000-000000000000'::uuid
  OR p_event IS NULL
  OR p_sequence IS NULL OR p_sequence<1 OR p_hash IS NULL OR (p_hash COLLATE "C") !~ '^[0-9a-f]{64}$' OR p_replay IS NULL THEN
  RAISE EXCEPTION 'Exact completed audit identity required' USING ERRCODE='23514';
 END IF;
 c:=firefly_audit_private.context_for(p_nonce);
 IF c.event_id<>p_event OR c.new_event=p_replay
  OR NOT EXISTS(SELECT 1 FROM firefly_audit_private.completion done WHERE done.nonce=c.nonce) THEN
  RAISE EXCEPTION 'Original completed audit scope required' USING ERRCODE='42501';
 END IF;
 PERFORM firefly_audit_private.require_graph(c);
 SELECT e.* INTO a FROM firefly_audit.audit_event e
  WHERE e.resource_tenant_id=c.resource_tenant_id AND e.event_id=c.event_id
   AND e.sequence=p_sequence AND e.record_hash=p_hash;
 IF NOT FOUND THEN RAISE EXCEPTION 'Exact completed audit record required' USING ERRCODE='23514';END IF;
 -- Recheck the original context after the row read; completed scopes never become writable again.
 again:=firefly_audit_private.context_for(p_nonce);
 IF again IS DISTINCT FROM c THEN RAISE EXCEPTION 'Unchanged original audit context required' USING ERRCODE='42501';END IF;
 observed:=pg_catalog.date_trunc('milliseconds',pg_catalog.clock_timestamp());
 IF observed<c.installed_at OR observed>=c.until_time THEN
  RAISE EXCEPTION 'Original completed audit deadline required' USING ERRCODE='42501';
 END IF;
 RETURN QUERY SELECT c.nonce,pg_catalog.floor(extract(epoch FROM observed)*1000)::bigint,
  pg_catalog.floor(extract(epoch FROM c.until_time)*1000)::bigint,NOT c.new_event,
  a.resource_tenant_id,a.service,a.resource_type,a.resource_id,a.event_id,a.sequence,a.previous_hash,a.record_hash,
  a.recorded_seconds,a.recorded_nanos,a.actor_issuer,a.actor_subject,a.actor_client_id,a.actor_platform_tenant_id,
  a.actor_staff_realm,a.actor_staff_id,a.action,a.outcome,a.process_id,a.correlation_id,a.causation_id,
  a.occurred_seconds,a.occurred_nanos,a.evidence_digest,a.policy_revision;
END $$;
REVOKE ALL ON FUNCTION public.firefly_audit_require_completed(UUID,UUID,BIGINT,TEXT,BOOLEAN) FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
RESET ROLE;
REVOKE CREATE ON SCHEMA public FROM firefly_audit_guard;
DO $$ DECLARE fn OID;guard OID;BEGIN
 fn:=to_regprocedure('public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean)');
 SELECT oid INTO guard FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p WHERE p.oid=fn AND p.proowner=guard AND p.prosecdef
   AND p.provolatile='v' AND p.proconfig=ARRAY['search_path=pg_catalog'])
  OR pg_catalog.has_schema_privilege('firefly_audit_guard','public','CREATE')
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_proc p CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) a
   WHERE p.oid=fn AND(a.grantee<>guard OR a.privilege_type<>'EXECUTE')) THEN
  RAISE EXCEPTION 'Restricted completed audit accessor installation required' USING ERRCODE='42501';
 END IF;
END $$;
COMMIT;
