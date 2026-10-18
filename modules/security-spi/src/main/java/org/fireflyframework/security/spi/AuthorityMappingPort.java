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

package org.fireflyframework.security.spi;

import java.util.Map;
import java.util.Set;

/**
 * Normalizes provider-specific token claims into framework authorities and scopes. Adapters map
 * the various issuer conventions (e.g. {@code realm_access.roles} for Keycloak, {@code cognito:groups}
 * for Cognito, {@code roles} for Entra) into a consistent authority set.
 */
public interface AuthorityMappingPort {

    Set<String> mapAuthorities(Map<String, Object> claims);

    Set<String> mapScopes(Map<String, Object> claims);
}
