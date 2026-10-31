-- Optional read-only completion accessor after postgresql.sql; no default consumer grant.
BEGIN;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='5s';
DO $$ DECLARE guard OID; BEGIN
 SELECT oid INTO guard FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard'
  AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls;
 IF pg_catalog.getdatabaseencoding()<>'UTF8' OR guard IS NULL OR NOT pg_catalog.pg_has_role(current_user,'firefly_audit_guard','SET')
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
-- Pure private encoding helpers: only the guard can execute these functions.
CREATE FUNCTION firefly_audit_private.canonical_text(p_value pg_catalog.text,p_kind pg_catalog.text) RETURNS pg_catalog.bytea
LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE bytes pg_catalog.bytea;idx pg_catalog.int4;codepoint pg_catalog.int4;blank pg_catalog.bool:=true;
BEGIN
 IF p_value IS NULL OR p_kind IS NULL THEN
  RAISE EXCEPTION 'Required canonical audit text absent' USING ERRCODE='23514';
 END IF;
 IF (p_kind COLLATE pg_catalog."C")='TOKEN' THEN
  IF pg_catalog.octet_length(p_value) NOT BETWEEN 1 AND 128 OR (p_value COLLATE pg_catalog."C") !~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$' THEN
   RAISE EXCEPTION 'Invalid canonical audit token' USING ERRCODE='23514';
  END IF;
 ELSIF (p_kind COLLATE pg_catalog."C")='DIGEST' THEN
  IF pg_catalog.octet_length(p_value)<>64 OR (p_value COLLATE pg_catalog."C") !~ '^[0-9a-f]{64}$' THEN
   RAISE EXCEPTION 'Invalid canonical audit digest' USING ERRCODE='23514';
  END IF;
 ELSIF (p_kind COLLATE pg_catalog."C")='TEXT' THEN
  -- UTF8 byte bounds also bound UTF16 code units; PostgreSQL UTF8 rejects surrogate encodings.
  IF pg_catalog.octet_length(p_value) NOT BETWEEN 1 AND 2048 THEN
   RAISE EXCEPTION 'Invalid canonical audit text size' USING ERRCODE='23514';
  END IF;
  FOR idx IN 1..pg_catalog.char_length(p_value) LOOP
   codepoint:=pg_catalog.ascii(pg_catalog.substr(p_value,idx,1));
   IF codepoint BETWEEN 0 AND 31 OR codepoint BETWEEN 127 AND 159 OR codepoint IN(8232,8233) THEN
    RAISE EXCEPTION 'Invalid canonical audit text character' USING ERRCODE='23514';
   END IF;
   -- Character.isWhitespace among the remaining allowed scalar values, excluding nonbreaking spaces.
   IF NOT(codepoint IN(32,5760,8287,12288) OR codepoint BETWEEN 8192 AND 8198 OR codepoint BETWEEN 8200 AND 8202) THEN blank:=false;END IF;
  END LOOP;
  IF blank THEN RAISE EXCEPTION 'Blank canonical audit text' USING ERRCODE='23514';END IF;
 ELSE RAISE EXCEPTION 'Unknown canonical audit text kind' USING ERRCODE='23514';
 END IF;
 bytes:=pg_catalog.convert_to(p_value,'UTF8');
 RETURN pg_catalog.int4send(pg_catalog.octet_length(bytes))||bytes;
END $$;
CREATE FUNCTION firefly_audit_private.canonical_record_bytes(a firefly_audit.audit_event) RETURNS pg_catalog.bytea
LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN
 IF a.resource_tenant_id IS NULL OR a.resource_id IS NULL OR a.event_id IS NULL OR a.process_id IS NULL OR a.correlation_id IS NULL
  OR a.sequence IS NULL OR a.sequence<1 OR (a.actor_staff_realm IS NULL)<>(a.actor_staff_id IS NULL)
  OR a.recorded_seconds IS NULL OR a.recorded_seconds NOT BETWEEN -31557014167219200 AND 31556889864403199
  OR a.occurred_seconds IS NULL OR a.occurred_seconds NOT BETWEEN -31557014167219200 AND 31556889864403199
  OR a.recorded_nanos IS NULL OR a.recorded_nanos NOT BETWEEN 0 AND 999999999
  OR a.occurred_nanos IS NULL OR a.occurred_nanos NOT BETWEEN 0 AND 999999999
  OR a.outcome IS NULL OR (a.outcome COLLATE pg_catalog."C") NOT IN('ATTEMPTED','ACCEPTED','REJECTED','FAILED','CANCELLED','COMPLETED','UNKNOWN')
  OR (a.sequence=1 AND (a.previous_hash COLLATE pg_catalog."C")<>pg_catalog.repeat('0',64)) THEN
  RAISE EXCEPTION 'Invalid canonical audit record fields' USING ERRCODE='23514';
 END IF;
 RETURN firefly_audit_private.canonical_text('firefly.audit.record','TOKEN')||pg_catalog.int4send(1)
  ||pg_catalog.uuid_send(a.resource_tenant_id)||firefly_audit_private.canonical_text(a.service,'TOKEN')
  ||firefly_audit_private.canonical_text(a.resource_type,'TOKEN')||pg_catalog.uuid_send(a.resource_id)
  ||pg_catalog.int8send(a.sequence)||firefly_audit_private.canonical_text(a.previous_hash,'DIGEST')
  ||pg_catalog.int8send(a.recorded_seconds)||pg_catalog.int4send(a.recorded_nanos)||pg_catalog.uuid_send(a.event_id)
  ||firefly_audit_private.canonical_text(a.actor_issuer,'TEXT')||firefly_audit_private.canonical_text(a.actor_subject,'TEXT')
  ||firefly_audit_private.canonical_text(a.actor_client_id,'TEXT')||firefly_audit_private.canonical_text(a.actor_platform_tenant_id,'TEXT')
  ||pg_catalog.boolsend(a.actor_staff_id IS NOT NULL)
  ||CASE WHEN a.actor_staff_id IS NULL THEN ''::pg_catalog.bytea ELSE firefly_audit_private.canonical_text(a.actor_staff_realm,'TEXT')||pg_catalog.uuid_send(a.actor_staff_id) END
  ||firefly_audit_private.canonical_text(a.action,'TOKEN')||firefly_audit_private.canonical_text(a.outcome,'TOKEN')
  ||pg_catalog.uuid_send(a.process_id)||pg_catalog.uuid_send(a.correlation_id)||pg_catalog.boolsend(a.causation_id IS NOT NULL)
  ||CASE WHEN a.causation_id IS NULL THEN ''::pg_catalog.bytea ELSE pg_catalog.uuid_send(a.causation_id) END
  ||pg_catalog.int8send(a.occurred_seconds)||pg_catalog.int4send(a.occurred_nanos)
  ||firefly_audit_private.canonical_text(a.evidence_digest,'DIGEST')||firefly_audit_private.canonical_text(a.policy_revision,'TOKEN');
END $$;
CREATE FUNCTION firefly_audit_private.require_record_hash(a firefly_audit.audit_event) RETURNS pg_catalog.void
LANGUAGE plpgsql IMMUTABLE SECURITY INVOKER SET search_path=pg_catalog AS $$
BEGIN
 PERFORM firefly_audit_private.canonical_text(a.record_hash,'DIGEST');
 IF (pg_catalog.encode(pg_catalog.sha256(firefly_audit_private.canonical_record_bytes(a)),'hex') COLLATE pg_catalog."C") IS DISTINCT FROM (a.record_hash COLLATE pg_catalog."C") THEN
  RAISE EXCEPTION 'Canonical completed audit record required' USING ERRCODE='23514';
 END IF;
END $$;
REVOKE ALL ON FUNCTION firefly_audit_private.canonical_text(TEXT,TEXT),
 firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event),
 firefly_audit_private.require_record_hash(firefly_audit.audit_event) FROM PUBLIC,firefly_audit_writer,firefly_audit_reader;
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
 PERFORM firefly_audit_private.require_record_hash(a);
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
DO $$ DECLARE guard OID;BEGIN
 SELECT oid INTO guard FROM pg_catalog.pg_roles WHERE rolname='firefly_audit_guard';
 IF EXISTS(SELECT 1 FROM (VALUES
   (to_regprocedure('firefly_audit_private.canonical_text(text,text)'),'pg_catalog.bytea'::pg_catalog.regtype),
   (to_regprocedure('firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)'),'pg_catalog.bytea'::pg_catalog.regtype),
   (to_regprocedure('firefly_audit_private.require_record_hash(firefly_audit.audit_event)'),'pg_catalog.void'::pg_catalog.regtype)) wanted(id,result_type)
  LEFT JOIN pg_catalog.pg_proc p ON p.oid=wanted.id
  WHERE p.oid IS NULL OR p.proowner<>guard OR p.prosecdef OR p.provolatile<>'i' OR p.prokind<>'f'
   OR p.prorettype<>wanted.result_type OR p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog'])
  OR EXISTS(SELECT 1 FROM pg_catalog.pg_proc p CROSS JOIN LATERAL aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) a
   WHERE p.oid IN(to_regprocedure('firefly_audit_private.canonical_text(text,text)'),
    to_regprocedure('firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)'),
    to_regprocedure('firefly_audit_private.require_record_hash(firefly_audit.audit_event)'))
    AND(a.grantee<>guard OR a.privilege_type<>'EXECUTE')) THEN
  RAISE EXCEPTION 'Restricted pure canonical audit helpers required' USING ERRCODE='42501';
 END IF;
END $$;
COMMIT;
