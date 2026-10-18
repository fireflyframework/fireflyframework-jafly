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

package org.fireflyframework.security.idp.port;

import org.fireflyframework.security.idp.dtos.AssignRolesRequest;
import org.fireflyframework.security.idp.dtos.CreateRolesRequest;
import org.fireflyframework.security.idp.dtos.CreateRolesResponse;
import org.fireflyframework.security.idp.dtos.CreateScopeRequest;
import org.fireflyframework.security.idp.dtos.CreateScopeResponse;
import org.fireflyframework.security.idp.dtos.UpdateRoleRequest;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Segregated IdP capability port for role and scope management and role assignment.
 */
public interface RoleScopePort {

    default Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        return Mono.error(new UnsupportedOperationException("getRoles is not supported by this IdP provider"));
    }

    default Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        return Mono.error(new UnsupportedOperationException("createRoles is not supported by this IdP provider"));
    }

    default Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        return Mono.error(new UnsupportedOperationException("createScope is not supported by this IdP provider"));
    }

    default Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        return Mono.error(new UnsupportedOperationException("assignRolesToUser is not supported by this IdP provider"));
    }

    default Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        return Mono.error(new UnsupportedOperationException("removeRolesFromUser is not supported by this IdP provider"));
    }

    /** Lists every realm role name (the role catalog), independent of any user. */
    default Mono<ResponseEntity<List<String>>> listRoles() {
        return Mono.error(new UnsupportedOperationException("listRoles is not supported by this IdP provider"));
    }

    default Mono<Void> deleteRole(String roleName) {
        return Mono.error(new UnsupportedOperationException("deleteRole is not supported by this IdP provider"));
    }

    default Mono<Void> updateRole(UpdateRoleRequest request) {
        return Mono.error(new UnsupportedOperationException("updateRole is not supported by this IdP provider"));
    }
}
