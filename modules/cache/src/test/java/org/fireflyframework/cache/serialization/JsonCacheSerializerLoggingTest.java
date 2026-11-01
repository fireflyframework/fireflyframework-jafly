package org.fireflyframework.cache.serialization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import java.util.*;

class JsonCacheSerializerLoggingTest {
    static final String PRIVATE="PRIVATE_SECRET_VALUE";
    final Logger logger=(Logger)LoggerFactory.getLogger(JsonCacheSerializerImpl.class);
    final ListAppender<ILoggingEvent> events=new ListAppender<>();
    Level previousLevel;boolean previousAdditive;
    @BeforeEach void capture(){previousLevel=logger.getLevel();previousAdditive=logger.isAdditive();logger.setLevel(Level.ERROR);logger.setAdditive(false);events.start();logger.addAppender(events);}
    @AfterEach void release(){logger.detachAppender(events);events.stop();logger.setLevel(previousLevel);logger.setAdditive(previousAdditive);}
    void safeEvent(){assertEquals(1,events.list.size());var event=events.list.getFirst();assertNull(event.getThrowableProxy());assertFalse(event.getMessage().contains(PRIVATE));assertFalse(event.getFormattedMessage().contains(PRIVATE));assertTrue(event.getFormattedMessage().contains("java.lang.IllegalStateException"));for(Object argument:event.getArgumentArray())assertFalse(String.valueOf(argument).contains(PRIVATE));}
    @Test void serializeFailureRetainsCauseWithoutLoggingPayloadOrThrowable()throws Exception{
        var mapper=mock(ObjectMapper.class);var failure=new IllegalStateException(PRIVATE);when(mapper.writeValueAsString(any())).thenThrow(failure);
        var exception=assertThrows(SerializationException.class,()->new JsonCacheSerializerImpl(mapper).serialize(Map.of("secret",PRIVATE)));assertSame(failure,exception.getCause());safeEvent();
    }
    @Test void stringDecodeFailureRetainsCauseWithoutLoggingPayloadOrThrowable()throws Exception{
        var mapper=mock(ObjectMapper.class);var failure=new IllegalStateException(PRIVATE);when(mapper.readValue(eq(PRIVATE),eq(Map.class))).thenThrow(failure);
        var exception=assertThrows(SerializationException.class,()->new JsonCacheSerializerImpl(mapper).deserialize(PRIVATE,Map.class));assertSame(failure,exception.getCause());safeEvent();
    }
    @Test void conversionFailureRetainsCauseWithoutLoggingPayloadOrThrowable()throws Exception{
        var mapper=mock(ObjectMapper.class);var failure=new IllegalStateException(PRIVATE);when(mapper.writeValueAsString(any())).thenThrow(failure);
        var exception=assertThrows(SerializationException.class,()->new JsonCacheSerializerImpl(mapper).deserialize(Map.of("secret",PRIVATE),List.class));assertSame(failure,exception.getCause());safeEvent();
    }
}
