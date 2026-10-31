package org.fireflyframework.eda.consumer.postgres;

import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PostgresConsumerGroupSchemaTest {
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-31T00:00:00Z");
    ConsumerGroupTables tables(){return new ConsumerGroupTables("group\"schema","outbox","registry","deliveries","journal");}
    static Stream<String> invalidNames(){return Stream.of(null,""," ","\0","\uD800","x".repeat(256),"😀".repeat(64));}
    @ParameterizedTest @MethodSource("invalidNames")
    void rejectsInvalidGroupIdentityWithoutNormalization(String name){
        assertThatThrownBy(()->new ConsumerGroupId(name)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void groupIdentityIsExactCaseSensitiveAndUnicodePreserving(){
        assertThat(new ConsumerGroupId("A")).isNotEqualTo(new ConsumerGroupId("a"));
        assertThat(new ConsumerGroupId(" é ").value()).isEqualTo(" é ");
    }
    @Test void subscriptionSnapshotsCallerStateAndRejectsInvalidBounds(){
        var channels=new HashSet<>(Set.of("a","b"));var subscription=new GroupSubscription(channels,3);channels.clear();
        assertThat(subscription.channels()).containsExactlyInAnyOrder("a","b");
        assertThatThrownBy(()->subscription.channels().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->new GroupSubscription(Set.of(),3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new GroupSubscription(Set.of("events"),0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new GroupSubscription(Collections.singleton(null),3)).isInstanceOf(IllegalArgumentException.class);
    }
    static Stream<Arguments> invalidPersistedChannels(){
        return Stream.<String[]>of(null,new String[0],new String[]{null},new String[]{""},new String[]{" "},new String[]{"\0"},
                new String[]{"\uD800"},new String[]{"x".repeat(64)},new String[]{"events","events"},new String[257])
                .map(value -> Arguments.of((Object) value));
    }
    @ParameterizedTest @MethodSource("invalidPersistedChannels")
    void persistedArraysAreValidatedBeforeAnySetConversion(String[] channels){
        assertThatThrownBy(()->new PostgresGroupRegistrationRow("group",channels,3,"ALL_RETAINED",NOW).subscription())
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validPersistedOrderIsIrrelevantAndArraysRemainDefensive(){
        String[] channels={"b","a"};var row=new PostgresGroupRegistrationRow("group",channels,3,"ALL_RETAINED",NOW);channels[0]="changed";
        assertThat(row.subscription()).isEqualTo(new GroupSubscription(Set.of("a","b"),3));
        row.channels()[0]="changed-again";assertThat(row.channels()).containsExactly("b","a");
    }
    @Test void malformedPersistedPolicyIdentityClockOrAttemptsRefuse(){
        assertThatThrownBy(()->new PostgresGroupRegistrationRow("group",new String[]{"events"},3,"all_retained",NOW).subscription()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PostgresGroupRegistrationRow("group",new String[]{"events"},0,"ALL_RETAINED",NOW).subscription()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PostgresGroupRegistrationRow("group",new String[]{"events"},3,"ALL_RETAINED",null).subscription()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void relationNamesAreBoundedAndMustBeDistinct(){
        assertThatThrownBy(()->new ConsumerGroupTables("public","same","same","deliveries","journal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new ConsumerGroupTables("public","outbox","groups","deliveries","x".repeat(64))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void migrationIsManualAtomicAndDoesNotRewriteGlobalOrGrantPrivileges(){
        String sql=PostgresConsumerGroupSchema.installationSql(tables());
        assertThat(sql).startsWith("BEGIN;").endsWith("COMMIT;\n").contains("UNIQUE(group_id,event_id)","ON DELETE RESTRICT","attempt_ceiling","ALL_RETAINED","firefly.eda.groups/v1");
        assertThat(sql).doesNotContain("IF NOT EXISTS","GRANT ","CREATE ROLE","DELETE FROM ","UPDATE \"group", "ALTER TABLE \"group\"\"schema\".\"outbox\"");
    }
    @Test void fixedJournalGuardsCoverUpdateDeleteAndTruncateIncludingReplicaMode(){
        String sql=PostgresConsumerGroupSchema.installationSql(tables());
        assertThat(sql).contains("BEFORE UPDATE OR DELETE", "BEFORE TRUNCATE", "FOR EACH STATEMENT", "ENABLE ALWAYS TRIGGER", "ERRCODE='55000'", "SET search_path TO pg_catalog");
        assertThat(sql).doesNotContain("SECURITY DEFINER", "DISABLE TRIGGER", "session_replication_role");
    }
    @Test void identifiersContainingOtherTemplateTokensAreNeverReinterpreted(){
        var names=new ConsumerGroupTables("{{outbox}}","{{registry}}","{{deliveries}}","{{redrives}}","{{guard}}");
        String sql=PostgresConsumerGroupSchema.installationSql(names);
        assertThat(sql).contains("LOCK TABLE \"{{outbox}}\".\"{{registry}}\"", "CREATE TABLE \"{{outbox}}\".\"{{deliveries}}\"", "CREATE TABLE \"{{outbox}}\".\"{{redrives}}\"", "CREATE TABLE \"{{outbox}}\".\"{{guard}}\"");
    }
}
