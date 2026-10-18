/*
 * Copyright 2024-2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.fireflyframework.security.idp.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RFC 7662 token introspection response. Product-specific claims are returned generically in
 * {@link #attributes}; the framework keeps no product domain (no party, contract, or business role).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntrospectionResponse {
    private boolean active;
    private String scope;
    private String username;
    private Long exp;
    private Long iat;
    private String sub;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> aud;
    private String iss;
    private String jti;
    private Map<String, Object> attributes;
}
