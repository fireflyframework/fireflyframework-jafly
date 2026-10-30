package org.fireflyframework.core.auditing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.data.annotation.*;
import org.springframework.data.auditing.ReactiveIsNewAwareAuditingHandler;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.r2dbc.mapping.R2dbcSimpleTypeHolder;
import java.time.*;
import java.util.Date;
import java.util.TimeZone;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class UtcOffsetDateTimeProviderTest {
    private static final Instant NOW=Instant.parse("2026-01-01T12:34:56Z");
    static class AuditBase { @Id String id; @Version Long version; @CreatedDate OffsetDateTime created; @LastModifiedDate OffsetDateTime modified; }
    static class InheritedAudit extends AuditBase { String value; }
    static class LocalAudit { @Id String id; @Version Long version; @CreatedDate LocalDateTime created; @LastModifiedDate LocalDateTime modified; }
    static class InstantAudit { @Id String id; @Version Long version; @CreatedDate Instant created; @LastModifiedDate Instant modified; }
    static class DateAudit { @Id String id; @CreatedDate Date created; }
    static class LongAudit { @Id String id; @CreatedDate Long created; }
    record ImmutableAudit(@Id String id,@Version Long version,@CreatedDate OffsetDateTime created,@LastModifiedDate OffsetDateTime modified) {}
    static ReactiveIsNewAwareAuditingHandler handler() {
        var context=new R2dbcMappingContext();context.setSimpleTypeHolder(R2dbcSimpleTypeHolder.HOLDER);
        for(var type:new Class<?>[]{InheritedAudit.class,LocalAudit.class,InstantAudit.class,DateAudit.class,LongAudit.class,ImmutableAudit.class})context.getRequiredPersistentEntity(type);
        var result=new ReactiveIsNewAwareAuditingHandler(PersistentEntities.of(context));result.setDateTimeProvider(new UtcOffsetDateTimeProvider(Clock.fixed(NOW,ZoneId.of("Asia/Tokyo"))));return result;
    }
    @Test void ordinaryInfrastructureDoesNotAutoRegisterOrEnableProvider() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(org.fireflyframework.core.config.R2dbcAutoConfiguration.class))
            .withBean(org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class,()->org.mockito.Mockito.mock(org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class))
            .run(app->{assertNull(app.getStartupFailure());assertTrue(app.getBeansOfType(org.springframework.data.auditing.DateTimeProvider.class).isEmpty());assertTrue(app.getBeansOfType(org.springframework.data.auditing.ReactiveAuditingHandler.class).isEmpty());});
    }
    @Test void injectedClockIsNormalizedToUtcAndNullIsRejected() {
        var provider=new UtcOffsetDateTimeProvider(Clock.fixed(NOW,ZoneId.of("Asia/Tokyo")));
        assertEquals(OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),provider.getNow().orElseThrow());assertThrows(NullPointerException.class,()->new UtcOffsetDateTimeProvider(null));
    }
    @Test void inheritedFieldsUseStandardCreateAndUpdateAndIsNewDetection() {
        var handler=handler();var entity=new InheritedAudit();var created=(InheritedAudit)handler.markAudited(entity).block();assertSame(entity,created);assertEquals(NOW,created.created.toInstant());assertEquals(created.created,created.modified);
        entity.version=3L;handler.setDateTimeProvider(new UtcOffsetDateTimeProvider(Clock.fixed(NOW.plusSeconds(5),ZoneOffset.UTC)));var updated=(InheritedAudit)handler.markAudited(entity).block();assertEquals(NOW,updated.created.toInstant());assertEquals(NOW.plusSeconds(5),updated.modified.toInstant());
    }
    @Test void standardMappingPreservesImmutableCreateAndUpdateCopies() {
        var handler=handler();var original=new ImmutableAudit(null,null,null,null);var created=(ImmutableAudit)handler.markAudited(original).block();assertNotSame(original,created);assertNull(original.created());assertEquals(NOW,created.created().toInstant());
        handler.setDateTimeProvider(new UtcOffsetDateTimeProvider(Clock.fixed(NOW.plusSeconds(5),ZoneOffset.UTC)));var updated=(ImmutableAudit)handler.markAudited(new ImmutableAudit("present",1L,created.created(),created.modified())).block();assertEquals(created.created(),updated.created());assertEquals(NOW.plusSeconds(5),updated.modified().toInstant());
    }
    @Test void localOffsetAndInstantFieldsRemainUtcInNonUtcJvm() {
        var previous=TimeZone.getDefault();try {TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));var handler=handler();var local=(LocalAudit)handler.markAudited(new LocalAudit()).block();var offset=(InheritedAudit)handler.markAudited(new InheritedAudit()).block();var instant=(InstantAudit)handler.markAudited(new InstantAudit()).block();assertEquals(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC),local.created);assertEquals(ZoneOffset.UTC,offset.created.getOffset());assertEquals(NOW,offset.created.toInstant());assertEquals(NOW,instant.created);}finally{TimeZone.setDefault(previous);}
    }
    @Test void modifyOnCreationFalseKeepsStandardFlagBehavior() {
        var handler=handler();handler.setModifyOnCreation(false);var entity=(InheritedAudit)handler.markAudited(new InheritedAudit()).block();assertNotNull(entity.created);assertNull(entity.modified);
    }
    @Test void dateTimeForNowFalseKeepsStandardFlagBehavior() {
        var handler=handler();handler.setDateTimeForNow(false);var entity=(InheritedAudit)handler.markAudited(new InheritedAudit()).block();assertNull(entity.created);assertNull(entity.modified);
    }
    @Test void unsupportedLegacyDateAndLongRemainExplicitlyUnsupported() {
        var handler=handler();assertThrows(IllegalArgumentException.class,()->handler.markAudited(new DateAudit()).block());assertThrows(IllegalArgumentException.class,()->handler.markAudited(new LongAudit()).block());
    }
}
