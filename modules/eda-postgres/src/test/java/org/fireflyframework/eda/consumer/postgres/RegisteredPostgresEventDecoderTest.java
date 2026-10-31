/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.fireflyframework.eda.consumer.postgres.impl.RegisteredPostgresEventDecoder;
import org.fireflyframework.eda.serialization.JsonMessageSerializer;
import org.fireflyframework.eda.serialization.MessageSerializer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisteredPostgresEventDecoderTest {
    @Test void explicitClassTokenUsesGenuineSerializer(){
        var decoder=new RegisteredPostgresEventDecoder(new JsonMessageSerializer(new ObjectMapper()),Map.of("java.lang.String",String.class));
        assertThat(decoder.decode(bytes("payload"),"java.lang.String","String")).isEqualTo("payload");
    }
    @Test void explicitEventTypeAliasWorksOnlyWhenClassIsAbsent(){
        var decoder=new RegisteredPostgresEventDecoder(new JsonMessageSerializer(new ObjectMapper()),Map.of("event-map",Map.class));
        assertThat(decoder.decode(bytes("{\"value\":7}"),null,"event-map")).isEqualTo(Map.of("value",7));
        assertThatThrownBy(()->decoder.decode(bytes("{}"),"not.registered.Class","event-map")).hasMessage("PostgreSQL event type is not registered");
    }
    @Test void unknownWireClassNeverCallsSerializerOrFallsBack(){
        var serializer=mock(MessageSerializer.class);var decoder=new RegisteredPostgresEventDecoder(serializer,Map.of("known",String.class));
        assertThatThrownBy(()->decoder.decode(bytes("payload"),"java.lang.Runtime","known")).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(serializer);
    }
    @Test void genericObjectRequiresAnExplicitEntry(){
        var serializer=new JsonMessageSerializer(new ObjectMapper());var restricted=new RegisteredPostgresEventDecoder(serializer,Map.of("known",String.class));
        assertThatThrownBy(()->restricted.decode(bytes("{}"),"java.lang.Object",null)).isInstanceOf(IllegalArgumentException.class);
        var explicit=new RegisteredPostgresEventDecoder(serializer,Map.of("java.lang.Object",Object.class));assertThat(explicit.decode(bytes("{\"value\":7}"),"java.lang.Object",null)).isEqualTo(Map.of("value",7));
    }
    @Test void conflictingRegisteredClassAndTypeAreRefusedBeforeDecode(){
        var serializer=mock(MessageSerializer.class);var decoder=new RegisteredPostgresEventDecoder(serializer,Map.of("class",String.class,"type",Map.class));
        assertThatThrownBy(()->decoder.decode(bytes("{}"),"class","type")).hasMessage("PostgreSQL event type is ambiguous");verifyNoInteractions(serializer);
    }
    @Test void nullOrEmptyPayloadAndNullResultsCannotBecomeSuccess(){
        var serializer=mock(MessageSerializer.class);var decoder=new RegisteredPostgresEventDecoder(serializer,Map.of("known",String.class));
        assertThatThrownBy(()->decoder.decode(null,"known",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->decoder.decode(new byte[0],"known",null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->decoder.decode(bytes("payload"),"known",null)).hasMessage("PostgreSQL event decoder returned no payload");
    }
    @Test void registryIsDetachedFromCallerMutation(){
        var allowed=new java.util.HashMap<String,Class<?>>();allowed.put("known",String.class);var decoder=new RegisteredPostgresEventDecoder(new JsonMessageSerializer(new ObjectMapper()),allowed);allowed.clear();
        assertThat(decoder.decode(bytes("payload"),"known",null)).isEqualTo("payload");
    }
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
