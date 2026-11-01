/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package org.fireflyframework.eda.consumer.postgres.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.fireflyframework.eda.consumer.postgres.model.DeliveryClaim;
import org.fireflyframework.eda.event.EventEnvelope;

final class PostgresEnvelopeMapperImpl {
    private final ObjectMapper mapper;
    PostgresEnvelopeMapperImpl(ObjectMapper mapper){this.mapper=mapper;}
    EventEnvelope map(DeliveryClaim claim,Object payload,EventEnvelope.AckCallback callback)throws Exception{
        var row=claim.row();Map<String,Object> headers=Map.of();
        if(row.headers()!=null){JsonNode node=mapper.readTree(row.headers().asString());if(node==null||!node.isObject())throw new IllegalArgumentException("Delivery headers must be an object");headers=object(node,0);}
        return new EventEnvelope(row.destination(),row.eventType(),payload,row.transactionId(),headers,EventEnvelope.EventMetadata.empty(),row.createdAt().toInstant(),row.publisherType(),"POSTGRES","default",callback);
    }
    private static Map<String,Object> object(JsonNode node,int depth){var result=new LinkedHashMap<String,Object>();node.properties().forEach(e->result.put(e.getKey(),value(e.getValue(),depth+1)));return Collections.unmodifiableMap(result);}
    private static Object value(JsonNode node,int depth){
        if(depth>32)throw new IllegalArgumentException("Delivery headers exceed supported depth");
        if(node.isNull())return null;if(node.isObject())return object(node,depth);if(node.isArray()){var result=new ArrayList<Object>();node.forEach(v->result.add(value(v,depth+1)));return Collections.unmodifiableList(result);}
        if(node.isTextual())return node.textValue();if(node.isBoolean())return node.booleanValue();if(node.isNumber()){Number number=node.numberValue();if(number instanceof Double d&&!Double.isFinite(d)||number instanceof Float f&&!Float.isFinite(f))throw new IllegalArgumentException("Nonfinite header value");return number;}throw new IllegalArgumentException("Unsupported header value");
    }
}
