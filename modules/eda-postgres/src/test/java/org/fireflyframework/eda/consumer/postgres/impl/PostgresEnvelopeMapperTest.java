/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.fireflyframework.eda.consumer.postgres.model.*;
import org.fireflyframework.eda.event.EventEnvelope;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PostgresEnvelopeMapperTest {
    @Test void ordinaryJsonScalarTypesRetainTheExistingMapContract()throws Exception{
        String json="{\"small\":7,\"large\":4294967296,\"decimal\":1.25,\"nil\":null,\"flag\":true}";var mapper=new ObjectMapper();
        Map<String,Object> expected=mapper.readValue(json,new TypeReference<>(){});
        var envelope=new PostgresEnvelopeMapperImpl(mapper).map(claim(Json.of(json)),"payload",null);
        assertThat(envelope.headers()).isEqualTo(expected);assertThat(envelope.headers().get("small")).isInstanceOf(Integer.class);assertThat(envelope.headers().get("large")).isInstanceOf(Long.class);assertThat(envelope.headers().get("decimal")).isInstanceOf(Double.class);
    }
    @Test void nestedHeaderContainersAreDetachedAndReadonly()throws Exception{
        var envelope=new PostgresEnvelopeMapperImpl(new ObjectMapper()).map(claim(Json.of("{\"nested\":{\"items\":[null,\"value\"]}}")),"payload",null);
        assertThatThrownBy(()->envelope.headers().put("extra","value")).isInstanceOf(UnsupportedOperationException.class);
        var nested=(Map<?,?>)envelope.headers().get("nested");assertThatThrownBy(()->nested.clear()).isInstanceOf(UnsupportedOperationException.class);var list=(java.util.List<?>)nested.get("items");assertThatThrownBy(()->list.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void headerClassAndRoutingStringsCannotOverrideTheTrustedEnvelope()throws Exception{
        var e=new PostgresEnvelopeMapperImpl(new ObjectMapper()).map(claim(Json.of("{\"event_class\":\"java.lang.Runtime\",\"consumer_type\":\"KAFKA\",\"connection_id\":\"foreign\",\"destination\":\"other\"}")),"payload",null);
        assertThat(e.consumerType()).isEqualTo("POSTGRES");assertThat(e.connectionId()).isEqualTo("default");assertThat(e.destination()).isEqualTo("events");assertThat(e.headers().get("event_class")).isEqualTo("java.lang.Runtime");
    }
    @Test void sqlNullHeadersAreEmptyAndOtherJsonShapesRefuse(){
        var mapper=new PostgresEnvelopeMapperImpl(new ObjectMapper());assertThatCode(()->assertThat(mapper.map(claim(null),"payload",null).headers()).isEmpty()).doesNotThrowAnyException();
        for(String json:new String[]{"null","[]","7"})assertThatThrownBy(()->mapper.map(claim(Json.of(json)),"payload",null)).isInstanceOf(IllegalArgumentException.class);
    }
    private static DeliveryClaim claim(Json headers){var now=OffsetDateTime.parse("2026-10-30T00:00:00Z");return new DeliveryClaim(new PostgresDeliveryRow(1L,"events","channel","String",new byte[]{1},headers,null,"POSTGRES","publisher-connection","java.lang.String","IN_FLIGHT",1,null,now,null,null,UUID.randomUUID(),1,now.plusSeconds(30),null),3);}
}
