/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.core.audit;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.fireflyframework.core.audit.impl.SpringDataCompletedAuditAppendServiceImpl;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppenderImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AuditRepositoryClassLoaderTest {
    void withoutRepositoryVisibility(Class<?> repository, Runnable construction) {
        var thread=Thread.currentThread();var original=thread.getContextClassLoader();
        var unrelated=new ClassLoader(repository.getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.equals(repository.getName()))throw new ClassNotFoundException(name);
                return super.loadClass(name,resolve);
            }
        };
        try {
            thread.setContextClassLoader(unrelated);
            assertThrows(ClassNotFoundException.class,()->Class.forName(repository.getName(),false,unrelated));
            assertDoesNotThrow(construction::run);
            assertSame(unrelated,thread.getContextClassLoader());
        } finally {thread.setContextClassLoader(original);}
    }
    @Test void appenderUsesItsDeclaredRepositoryLoaderWithoutChangingCallerContextOrOpeningConnections() {
        var fixture=new AuditTestFixture();
        withoutRepositoryVisibility(AuditNativeContextRepository.class,()->new SpringDataTransactionalAuditAppenderImpl(
                fixture.resources,fixture.client,fixture.template,fixture.manager,
                event->Mono.error(new IllegalStateException("No audit execution is admitted by this constructor test")),
                fixture.clock,fixture.nanos,UUID::randomUUID));
        assertEquals(0,fixture.driver.creates);assertTrue(fixture.driver.sql.isEmpty());
    }
    @Test void completionReaderUsesItsDeclaredRepositoryLoaderWithoutChangingCallerContextOrOpeningConnections() {
        var fixture=new AuditTestFixture();
        withoutRepositoryVisibility(AuditCompletedAppendRepository.class,()->new SpringDataCompletedAuditAppendServiceImpl(
                fixture.resources,fixture.client,fixture.template,fixture.manager,fixture.clock,fixture.nanos));
        assertEquals(0,fixture.driver.creates);assertTrue(fixture.driver.sql.isEmpty());
    }
}
