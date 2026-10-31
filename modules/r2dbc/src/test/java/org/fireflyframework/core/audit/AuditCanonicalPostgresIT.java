/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Dormant native fixture. An exact new Root-owned cluster/profile gate must precede execution. */
class AuditCanonicalPostgresIT {
    private static final String ACCESSOR = "public.firefly_audit_require_completed(uuid,uuid,bigint,text,boolean)";
    private static final String[] HELPERS = {
        "firefly_audit_private.canonical_text(text,text)",
        "firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)",
        "firefly_audit_private.require_record_hash(firefly_audit.audit_event)"
    };
    private static final String FIELDS = "resource_tenant_id,service,resource_type,resource_id,event_id,sequence,previous_hash,record_hash,"
            + "recorded_seconds,recorded_nanos,actor_issuer,actor_subject,actor_client_id,actor_platform_tenant_id,actor_staff_realm,actor_staff_id,"
            + "action,outcome,process_id,correlation_id,causation_id,occurred_seconds,occurred_nanos,evidence_digest,policy_revision";
    private static final String MARKERS = String.join(",", java.util.Collections.nCopies(25, "?"));
    private static Gate gate;
    private static Map<String,Boolean> initialAcl;
    private static boolean optInAttempted;

    @BeforeAll static void verifyFreshOwnedProfileThenExplicitlyOptIn() throws Exception {
        gate = Gate.load();
        try (var operator = gate.connect("OPERATOR")) {
            gate.verifyCluster(operator);
            try (var statement = operator.createStatement(); var rows = statement.executeQuery(
                    "SELECT (SELECT count(*) FROM firefly_audit.audit_event)+(SELECT count(*) FROM firefly_audit.audit_head)"
                    + "+(SELECT count(*) FROM firefly_audit.audit_outbox)+(SELECT count(*) FROM firefly_audit_private.context)"
                    + "+(SELECT count(*) FROM firefly_audit_private.completion)")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
            }
            var relations=new java.util.LinkedHashSet<String>();
            try(var statement=operator.createStatement();var rows=statement.executeQuery("SELECT n.nspname||'.'||c.relname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema' AND c.relkind IN('r','p','v','m','f','S')")){
                while(rows.next())relations.add(rows.getString(1));
            }
            assertThat(relations).containsExactlyInAnyOrder("firefly_audit.audit_event","firefly_audit.audit_head","firefly_audit.audit_outbox","firefly_audit_private.context","firefly_audit_private.completion");
            initialAcl = acl(operator);
            assertThat(initialAcl.values()).allMatch(value -> !value);
            gate.emit("acl-before-opt-in.json", initialAcl);
            optInAttempted = true;
            try (var statement = operator.createStatement()) {
                statement.execute("GRANT EXECUTE ON FUNCTION " + ACCESSOR + " TO firefly_audit_writer");
            }
            var admitted = acl(operator);
            assertThat(admitted.get("accessor-writer")).isTrue();
            admitted.remove("accessor-writer"); assertThat(admitted.values()).allMatch(value -> !value);
            gate.emit("acl-after-explicit-test-opt-in.json", acl(operator));
        }
    }

    @AfterAll static void revokeOnlyTheTrackedTestOptIn() throws Exception {
        if (!optInAttempted) return;
        try (var operator = gate.connect("OPERATOR")) {
            gate.verifyCluster(operator);
            try (var statement = operator.createStatement()) {
                statement.execute("REVOKE EXECUTE ON FUNCTION " + ACCESSOR + " FROM firefly_audit_writer");
            }
            assertThat(acl(operator)).isEqualTo(initialAcl);
            gate.emit("acl-after-test-revoke.json", acl(operator));
        }
    }

    @Test void nativeBytesAndSha256EqualAllActualKernelGoldenVectors() throws Exception {
        try (var operator = guardedOperator()) {
            for (var vector : AuditCanonicalGoldenVectors.vectors()) {
                String row = "ROW(" + MARKERS + ",?)::firefly_audit.audit_event";
                try (var query = operator.prepareStatement("SELECT firefly_audit_private.canonical_record_bytes(" + row + ")")) {
                    bind(query, values(vector.record(), true));
                    try (var result = query.executeQuery()) { assertThat(result.next()).isTrue(); assertThat(result.getBytes(1)).isEqualTo(vector.bytes()); }
                }
                try (var query = operator.prepareStatement("SELECT pg_catalog.encode(pg_catalog.sha256(firefly_audit_private.canonical_record_bytes(" + row + ")),'hex'),firefly_audit_private.require_record_hash(" + row + ")")) {
                    var parameters = values(vector.record(), true); var twice = new ArrayList<>(parameters); twice.addAll(parameters); bind(query, twice);
                    try (var result = query.executeQuery()) { assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo(vector.record().recordHash()); }
                }
            }
            operator.rollback();
        }
    }

    @Test void actualCompletedAsciiRowReturnsItsCanonicalHashWithoutReopeningRls() throws Exception {
        try (var writer = gate.connect("WRITER")) {
            writer.setAutoCommit(false); var append = append(writer, false, false, null);
            assertCompleted(writer, append, false);
            try (var query = writer.prepareStatement("SELECT count(*) FROM firefly_audit.audit_event WHERE resource_tenant_id=? AND event_id=?")) {
                query.setObject(1, append.record.event().stream().resourceTenantId()); query.setObject(2, append.record.event().eventId());
                try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero(); }
            }
            writer.rollback();
        }
    }

    @Test void actualUnicodeStaffCausationAndNegativeNanosecondEventVerify() throws Exception {
        try (var writer = gate.connect("WRITER")) {
            writer.setAutoCommit(false); var append = append(writer, true, false, null); assertCompleted(writer, append, false); writer.rollback();
        }
    }

    @Test void nonnullZeroEventAndOptionalUuidsRemainValid() throws Exception {
        try (var writer = gate.connect("WRITER")) {
            writer.setAutoCommit(false); var append = append(writer, true, true, null); assertCompleted(writer, append, false); writer.rollback();
        }
    }

    @Test void relationallyCompleteArbitraryLowerHexHashIsNotCanonicalAttestation() throws Exception {
        try (var writer = gate.connect("WRITER")) {
            writer.setAutoCommit(false); var append = append(writer, false, false, "HASH");
            expectState("23514", () -> assertCompleted(writer, append, false)); writer.rollback();
        }
    }

    @Test void eachUnboundStoredFieldMustStillMatchTheStoredHash() throws Exception {
        for (String field : List.of("action", "outcome", "process", "correlation", "causation", "occurred-seconds", "occurred-nanos", "evidence", "policy")) {
            try (var writer = gate.connect("WRITER")) {
                writer.setAutoCommit(false); var append = append(writer, false, false, field);
                expectState("23514", () -> assertCompleted(writer, append, false)); writer.rollback();
            }
        }
    }

    @Test void invalidStoredTokenOrOutcomeCannotBeCertified() throws Exception {
        for (String field : List.of("invalid-action", "invalid-outcome", "invalid-policy")) {
            try (var writer = gate.connect("WRITER")) {
                writer.setAutoCommit(false); var append = append(writer, false, false, field);
                expectState("23514", () -> assertCompleted(writer, append, false)); writer.rollback();
            }
        }
    }

    @Test void rawKernelInstantNanosStaffAndRequiredFieldDomainsRefuse() throws Exception {
        var base = AuditCanonicalGoldenVectors.vectors().getFirst().record();
        for (var invalid : List.of(new Change(8, Instant.MIN.getEpochSecond() - 1), new Change(8, Instant.MAX.getEpochSecond() + 1),
                new Change(9, -1), new Change(9, 1000000000), new Change(21, Instant.MIN.getEpochSecond() - 1),
                new Change(22, 1000000000), new Change(5, 0L), new Change(6, "f".repeat(64)), new Change(0, null), new Change(14, "realm-without-id"), new Change(15, new UUID(0,0)), new Change(17, "UNSUPPORTED"))) {
            try (var operator = guardedOperator()) {
                var parameters = values(base, true); parameters.set(invalid.index, invalid.value);
                expectState("23514", () -> { try (var query = operator.prepareStatement("SELECT firefly_audit_private.canonical_record_bytes(ROW(" + MARKERS + ",?)::firefly_audit.audit_event)")) { bind(query, parameters); query.executeQuery().close(); } });
                operator.rollback();
            }
        }
    }

    @Test void nativeTextDomainAndInvalidUtf8AreNotNormalized() throws Exception {
        for (String value : List.of("\u00a0", "\u2007", "\u202f", "\u2000x", "é".repeat(1024), "🦋".repeat(512))) {
            try (var operator = guardedOperator(); var query = operator.prepareStatement("SELECT firefly_audit_private.canonical_text(?,'TEXT')")) {
                query.setString(1, value); try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getBytes(1).length).isEqualTo(4 + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length); } operator.rollback();
            }
        }
        for (String value : List.of("", " ", "\u1680", "\u2000", "x\u0085", "x\u2028", "é".repeat(1025), "🦋".repeat(513))) {
            try (var operator = guardedOperator()) {
                expectState("23514", () -> { try (var query = operator.prepareStatement("SELECT firefly_audit_private.canonical_text(?,'TEXT')")) { query.setString(1, value); query.executeQuery().close(); } }); operator.rollback();
            }
        }
        var forbidden=new ArrayList<String>();
        for(int cp=1;cp<=Character.MAX_CODE_POINT;cp++)if(Character.isWhitespace(cp)||Character.isISOControl(cp))forbidden.add(new String(Character.toChars(cp)));
        forbidden.add("\u2028");forbidden.add("\u2029");
        for(String value:forbidden)try(var operator=guardedOperator()){
            expectState("23514",()->{try(var query=operator.prepareStatement("SELECT firefly_audit_private.canonical_text(?,'TEXT')")){query.setString(1,value);query.executeQuery().close();}});operator.rollback();
        }
        for(var item:List.of(Map.entry("TOKEN","a".repeat(128)),Map.entry("DIGEST","a".repeat(64))))try(var operator=guardedOperator();var query=operator.prepareStatement("SELECT firefly_audit_private.canonical_text(?,?)")){
            query.setString(1,item.getValue());query.setString(2,item.getKey());try(var rows=query.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getBytes(1).length).isEqualTo(item.getValue().length()+4);}operator.rollback();
        }
        for(var item:List.of(Map.entry("TOKEN","_bad"),Map.entry("TOKEN","é"),Map.entry("TOKEN","a".repeat(129)),Map.entry("DIGEST","A".repeat(64)),Map.entry("DIGEST","g".repeat(64)),Map.entry("DIGEST","a".repeat(63))))try(var operator=guardedOperator()){
            expectState("23514",()->{try(var query=operator.prepareStatement("SELECT firefly_audit_private.canonical_text(?,?)")){query.setString(1,item.getValue());query.setString(2,item.getKey());query.executeQuery().close();}});operator.rollback();
        }
        for (String invalidUtf8 : List.of("00", "eda080", "f4908080", "c080")) {
            try (var operator = guardedOperator()) {
                expectState("22021", () -> { try (var query = operator.prepareStatement("SELECT pg_catalog.convert_from(pg_catalog.decode(?,'hex'),'UTF8')")) { query.setString(1, invalidUtf8); query.executeQuery().close(); } }); operator.rollback();
            }
        }
    }

    @Test void privateHelpersRemainDeniedToBothRuntimeRoles() throws Exception {
        for (String role : List.of("WRITER", "READER")) {
            for (String sql : List.of("SELECT firefly_audit_private.canonical_text('issuer','TEXT')",
                    "SELECT firefly_audit_private.canonical_record_bytes(NULL::firefly_audit.audit_event)",
                    "SELECT firefly_audit_private.require_record_hash(NULL::firefly_audit.audit_event)")) {
                try (var connection = gate.connect(role)) {
                    expectState("42501", () -> { try (var statement = connection.createStatement()) { statement.executeQuery(sql).close(); } });
                }
            }
        }
        try (var operator = gate.connect("OPERATOR")) { var actual = acl(operator); actual.remove("accessor-writer"); assertThat(actual.values()).allMatch(value -> !value); }
    }

    @Test void privateCatalogOwnerPathVolatilityAndAclStayExact() throws Exception {
        try (var operator = gate.connect("OPERATOR")) {
            for (String helper : HELPERS) try (var query = operator.prepareStatement(
                    "SELECT r.rolname,p.prosecdef,p.provolatile,p.proconfig=ARRAY['search_path=pg_catalog'] FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles r ON r.oid=p.proowner WHERE p.oid=pg_catalog.to_regprocedure(?)")) {
                query.setString(1, helper); try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("firefly_audit_guard"); assertThat(rows.getBoolean(2)).isFalse(); assertThat(rows.getString(3)).isEqualTo("i"); assertThat(rows.getBoolean(4)).isTrue(); assertThat(rows.next()).isFalse(); }
            }
        }
    }

    @Test void replayVerifiesTheSameActualRecordWithoutAnotherAppend() throws Exception {
        Append original;
        try (var writer = gate.connect("WRITER")) { writer.setAutoCommit(false); original = append(writer, true, false, null); assertCompleted(writer, original, false); writer.commit(); }
        try (var writer = gate.connect("WRITER")) {
            writer.setAutoCommit(false); var replay = new Append(enter(writer, original.record.event()), original.record, original.storedHash);
            complete(writer, replay); assertCompleted(writer, replay, true); writer.commit();
        }
    }

    @Test void existingGraphOnlyWrongHashIsNotBlessedByReplayVerification() throws Exception {
        Append original;
        try (var writer = gate.connect("WRITER")) { writer.setAutoCommit(false); original = append(writer, false, false, "HASH"); writer.commit(); }
        try (var writer = gate.connect("WRITER")) {
            writer.setAutoCommit(false); var replay = new Append(enter(writer, original.record.event()), original.record, original.storedHash);
            complete(writer, replay); expectState("23514", () -> assertCompleted(writer, replay, true)); writer.rollback();
        }
    }

    private static Append append(Connection writer, boolean unicode, boolean zero, String change) throws Exception {
        UUID eventId = zero ? new UUID(0, 0) : UUID.randomUUID(); var stream = new AuditStream(UUID.randomUUID(), "audit.native", "resource", UUID.randomUUID());
        var actor = new AuditActor(unicode ? "é漢🦋" : "issuer", "subject", "client", "opaque:tenant", unicode ? "realm-𐐀" : null, unicode ? (zero ? new UUID(0,0) : UUID.randomUUID()) : null);
        var event = new AuditEvent(eventId, stream, actor, "ACTION", AuditEvent.Outcome.ACCEPTED,
                zero ? new UUID(0,0) : UUID.randomUUID(), zero ? new UUID(0,0) : UUID.randomUUID(), unicode ? (zero ? new UUID(0,0) : UUID.randomUUID()) : null,
                Instant.ofEpochSecond(-1,999999999), "a".repeat(64), "policy:one");
        var entered = enter(writer, event); var record = AuditRecordHasher.record(AuditCheckpoint.genesis(stream), event, entered.recorded);
        var row = values(record, false);
        if (change != null) switch (change) {
            case "HASH" -> row.set(7, flip(record.recordHash()));
            case "action" -> row.set(16, "OTHER"); case "outcome" -> row.set(17, "REJECTED");
            case "process" -> row.set(18, UUID.randomUUID()); case "correlation" -> row.set(19, UUID.randomUUID()); case "causation" -> row.set(20, UUID.randomUUID());
            case "occurred-seconds" -> row.set(21, 1L); case "occurred-nanos" -> row.set(22, 1);
            case "evidence" -> row.set(23, "b".repeat(64)); case "policy" -> row.set(24, "policy:other");
            case "invalid-action" -> row.set(16, "bad action"); case "invalid-outcome" -> row.set(17, "INVENTED"); case "invalid-policy" -> row.set(24, "bad policy");
            default -> throw new IllegalArgumentException("Unknown controlled field mutation");
        }
        String hash = (String)row.get(7);
        try (var insert = writer.prepareStatement("INSERT INTO firefly_audit.audit_event(" + FIELDS + ") VALUES(" + MARKERS + ")")) { bind(insert, row); assertThat(insert.executeUpdate()).isEqualTo(1); }
        try (var insert = writer.prepareStatement("INSERT INTO firefly_audit.audit_head(resource_tenant_id,service,resource_type,resource_id,last_sequence,last_hash) VALUES(?,?,?,?,?,?)")) { bind(insert, List.of(stream.resourceTenantId(),stream.service(),stream.resourceType(),stream.resourceId(),1L,hash)); assertThat(insert.executeUpdate()).isEqualTo(1); }
        try (var insert = writer.prepareStatement("INSERT INTO firefly_audit.audit_outbox(resource_tenant_id,event_id,service,resource_type,resource_id,sequence,record_hash,intent) VALUES(?,?,?,?,?,?,?,'AUDIT_EXPORT')")) { bind(insert,List.of(stream.resourceTenantId(),eventId,stream.service(),stream.resourceType(),stream.resourceId(),1L,hash)); assertThat(insert.executeUpdate()).isEqualTo(1); }
        var result = new Append(entered,record,hash); complete(writer,result); return result;
    }

    private static Entered enter(Connection writer, AuditEvent event) throws Exception {
        var s=event.stream();var a=event.actor();UUID nonce=UUID.randomUUID();
        try (var query=writer.prepareStatement("SELECT context_nonce,db_now_millis FROM public.firefly_audit_enter(?,?,?,?,?,?,?,?,?,?,?,?,pg_catalog.clock_timestamp()+INTERVAL '4 seconds')")) {
            var parameters=new ArrayList<Object>(); java.util.Collections.addAll(parameters,nonce,s.resourceTenantId(),s.service(),s.resourceType(),s.resourceId(),event.eventId(),a.issuer(),a.subject(),a.clientId(),a.platformTenantId(),a.staffRealm(),a.staffId());bind(query,parameters);
            try(var rows=query.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getObject(1,UUID.class)).isEqualTo(nonce);return new Entered(nonce,Instant.ofEpochMilli(rows.getLong(2)));}
        }
    }
    private static void complete(Connection writer,Append append)throws Exception {try(var query=writer.prepareStatement("SELECT public.firefly_audit_complete(?,?,?)")){bind(query,List.of(append.entered.nonce,append.record.sequence(),append.storedHash));query.executeQuery().close();}}
    private static void assertCompleted(Connection writer,Append append,boolean replay)throws Exception {
        try(var query=writer.prepareStatement("SELECT * FROM public.firefly_audit_require_completed(?,?,?,?,?)")){
            bind(query,List.of(append.entered.nonce,append.record.event().eventId(),append.record.sequence(),append.storedHash,replay));
            try(var rows=query.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getString("record_hash")).isEqualTo(append.record.recordHash());assertThat(rows.getBoolean("replay")).isEqualTo(replay);assertThat(rows.getObject("event_id",UUID.class)).isEqualTo(append.record.event().eventId());assertThat(rows.next()).isFalse();}
        }
    }
    private static List<Object> values(AuditRecord record,boolean parent){
        var e=record.event();var s=e.stream();var a=e.actor();var result=new ArrayList<Object>();
        java.util.Collections.addAll(result,s.resourceTenantId(),s.service(),s.resourceType(),s.resourceId(),e.eventId(),record.sequence(),record.previousHash(),record.recordHash(),record.recordedAt().getEpochSecond(),record.recordedAt().getNano(),a.issuer(),a.subject(),a.clientId(),a.platformTenantId(),a.staffRealm(),a.staffId(),e.action(),e.outcome().name(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt().getEpochSecond(),e.occurredAt().getNano(),e.evidenceDigest(),e.policyRevision());
        if(parent)result.add(record.sequence()==1?null:record.sequence()-1);return result;
    }
    private static void bind(PreparedStatement query,List<?> values)throws SQLException {query.setQueryTimeout(5);for(int i=0;i<values.size();i++){Object v=values.get(i);if(v==null)query.setNull(i+1,Types.NULL);else query.setObject(i+1,v);}}
    private static Connection guardedOperator()throws Exception {var connection=gate.connect("OPERATOR");connection.setAutoCommit(false);try(var statement=connection.createStatement()){statement.execute("SET LOCAL ROLE firefly_audit_guard");}return connection;}
    private static String flip(String value){return(value.charAt(0)=='0'?'1':'0')+value.substring(1);}
    private static void expectState(String expected,SqlAction action)throws Exception {try{action.run();fail("Expected native refusal");}catch(SQLException error){assertThat(error.getSQLState()).isEqualTo(expected);}}
    @FunctionalInterface private interface SqlAction {void run()throws Exception;}
    private record Entered(UUID nonce,Instant recorded) { }
    private record Append(Entered entered,AuditRecord record,String storedHash) { }
    private record Change(int index,Object value) { }

    private static Map<String,Boolean> acl(Connection operator)throws Exception {
        var result=new LinkedHashMap<String,Boolean>();var signatures=new ArrayList<>(List.of(HELPERS));signatures.add(ACCESSOR);
        for(String signature:signatures){for(String role:List.of("firefly_audit_writer","firefly_audit_reader"))try(var query=operator.prepareStatement("SELECT pg_catalog.has_function_privilege(?,?, 'EXECUTE')")){query.setString(1,role);query.setString(2,signature);try(var rows=query.executeQuery()){assertThat(rows.next()).isTrue();result.put((signature.equals(ACCESSOR)?"accessor":signature)+"-"+(role.endsWith("writer")?"writer":"reader"),rows.getBoolean(1));}}
            try(var query=operator.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_catalog.pg_proc p CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(p.proacl,pg_catalog.acldefault('f',p.proowner))) a WHERE p.oid=pg_catalog.to_regprocedure(?) AND a.grantee=0 AND a.privilege_type='EXECUTE')")){query.setString(1,signature);try(var rows=query.executeQuery()){assertThat(rows.next()).isTrue();result.put(signature+"-public",rows.getBoolean(1));}}
        }
        return result;
    }

    private static final class Gate {
        private final Map<?,?> value;
        private final Path evidence;
        private Gate(Map<?,?> value,Path evidence){this.value=value;this.evidence=evidence;}
        static Gate load()throws Exception {
            Path path=Path.of(required("AUDIT_CANONICAL_NATIVE_GATE"));String expected=required("AUDIT_CANONICAL_NATIVE_GATE_SHA256");
            byte[] bytes=privateFile(path);assertThat(hex(bytes)).isEqualTo(expected);Map<?,?> value=new ObjectMapper().readValue(bytes,Map.class);
            assertThat(value.get("status")).isEqualTo("ROOT_APPROVED_AUDIT_CANONICAL_NATIVE_ONE_RUN");assertThat(value.get("oneRunOnly")).isEqualTo(true);assertThat(value.get("allowTestWriterAccessorExecute")).isEqualTo(true);
            assertThat(value.get("workSeconds")).isEqualTo(720);assertThat(value.get("cleanupSeconds")).isEqualTo(90);assertThat(value.get("initialFreeGiB")).isEqualTo(12);assertThat(value.get("ongoingFreeGiB")).isEqualTo(8);
            for(String key:List.of("sourceSql","defaultSql","ownedResourceProof")){Path input=Path.of((String)value.get(key+"Path"));assertThat(hex(privateFile(input))).isEqualTo(value.get(key+"Sha256"));}
            Map<?,?> ownership=new ObjectMapper().readValue(privateFile(Path.of((String)value.get("ownedResourceProofPath"))),Map.class);
            assertThat(ownership.get("status")).isEqualTo("ROOT_VERIFIED_OWNED_POSTGRES_RESOURCE");
            for(String key:List.of("dataDirectory","systemIdentifier","port","database"))assertThat(ownership.get(key)).isEqualTo(value.get(key));
            assertThat(ownership.get("originalOwnershipGuardsVerified")).isEqualTo(true);
            Path evidence=Path.of((String)value.get("evidenceDirectory"));assertThat(evidence.toRealPath()).isEqualTo(evidence);assertThat(Files.getPosixFilePermissions(evidence)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
            assertThat(value.get("port")).isInstanceOf(Integer.class);assertThat((Integer)value.get("port")).isBetween(1,65535);
            String url=(String)value.get("jdbcUrl");assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/[a-z][a-z0-9_]{1,62}");
            assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:"+value.get("port")+"/"+value.get("database"));
            var gate=new Gate(value,evidence);
            gate.emit("native-fixture-one-run-claim.json",Map.of("status","EXACT_ROOT_AUTHORITY_CONSUMED_BY_TEST_FIXTURE","authoritySha256",expected,"sourceSqlSha256",value.get("sourceSqlSha256"),"ownedResourceProofSha256",value.get("ownedResourceProofSha256")));
            return gate;
        }
        Connection connect(String role)throws Exception {
            assertThat(Files.getFileStore(Path.of((String)value.get("dataDirectory"))).getUsableSpace()).isGreaterThanOrEqualTo(8L*1024*1024*1024);
            var properties=new java.util.Properties();properties.setProperty("user",required("AUDIT_CANONICAL_"+role+"_USER"));properties.setProperty("password",required("AUDIT_CANONICAL_"+role+"_PASSWORD"));
            properties.setProperty("connectTimeout","5");properties.setProperty("socketTimeout","5");properties.setProperty("loginTimeout","5");properties.setProperty("sslmode","disable");
            var connection=DriverManager.getConnection((String)value.get("jdbcUrl"),properties);
            try {
                try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT session_user")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo(role.equals("OPERATOR")?value.get("operatorRole"):"firefly_audit_"+role.toLowerCase(java.util.Locale.ROOT));}
                return connection;
            } catch(Exception|AssertionError failure){connection.close();throw failure;}
        }
        void verifyCluster(Connection operator)throws Exception {
            try(var statement=operator.createStatement();var rows=statement.executeQuery("SELECT pg_catalog.current_setting('data_directory'),(SELECT system_identifier::text FROM pg_catalog.pg_control_system()),pg_catalog.current_setting('port')::integer,pg_catalog.current_database(),pg_catalog.getdatabaseencoding()")){
                assertThat(rows.next()).isTrue();assertThat(Path.of(rows.getString(1)).toRealPath().toString()).isEqualTo(value.get("dataDirectory"));assertThat(rows.getString(2)).isEqualTo(value.get("systemIdentifier"));assertThat(rows.getInt(3)).isEqualTo(value.get("port"));assertThat(rows.getString(4)).isEqualTo(value.get("database"));assertThat(rows.getString(5)).isEqualTo("UTF8");
            }
        }
        void emit(String name,Object result)throws Exception {
            byte[] bytes=(new ObjectMapper().writeValueAsString(result)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThat(bytes.length).isLessThanOrEqualTo(4096);
            try(var output=Files.newByteChannel(evidence.resolve(name),java.util.Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())output.write(buffer);}
        }
        private static String required(String key){String result=System.getenv(key);if(result==null||result.isBlank())throw new IllegalStateException("Explicit new Root-owned native fixture authority and credentials required");return result;}
        private static byte[] privateFile(Path path)throws Exception {assertThat(path.isAbsolute()).isTrue();assertThat(Files.isSymbolicLink(path)).isFalse();assertThat(Files.isRegularFile(path)).isTrue();assertThat(path.toRealPath()).isEqualTo(path);assertThat(Files.size(path)).isLessThanOrEqualTo(1048576);assertThat(Files.getPosixFilePermissions(path)).isEqualTo(PosixFilePermissions.fromString("rw-------"));return Files.readAllBytes(path);}
        private static String hex(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    }
}
