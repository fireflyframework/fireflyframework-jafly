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


package org.fireflyframework.security.idp.adapter;

import org.fireflyframework.security.idp.dtos.CreateUserRequest;
import org.fireflyframework.security.idp.dtos.CreateUserResponse;
import org.fireflyframework.security.idp.dtos.RegisterUserRequest;
import org.fireflyframework.security.idp.port.AuthenticationPort;
import org.fireflyframework.security.idp.port.MfaPort;
import org.fireflyframework.security.idp.port.RoleScopePort;
import org.fireflyframework.security.idp.port.SessionPort;
import org.fireflyframework.security.idp.port.TokenIntrospectionPort;
import org.fireflyframework.security.idp.port.UserAdminPort;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Identity Provider (IdP) adapter — the aggregate of the segregated capability ports
 * ({@link AuthenticationPort}, {@link TokenIntrospectionPort}, {@link UserAdminPort},
 * {@link RoleScopePort}, {@link SessionPort}, {@link MfaPort}) that standardizes authentication and
 * user-management across IdPs such as Keycloak, AWS Cognito, and Entra ID.
 *
 * <p>Because every capability-port method has a {@code NotSupported} default, an adapter may
 * implement this aggregate and override only the operations its provider supports, or implement a
 * single focused port directly. This keeps providers from stubbing capabilities they cannot perform
 * (the previous fat-interface ISP violation).
 */
public interface IdpAdapter
        extends AuthenticationPort, TokenIntrospectionPort, UserAdminPort, RoleScopePort, SessionPort, MfaPort {

    /**
     * Register a new user via self-service (public, no admin auth required).
     *
     * <p>The default implementation converts the registration request into a
     * {@link CreateUserRequest} and delegates to {@link UserAdminPort#createUser(CreateUserRequest)}.
     * Adapters may override this to use provider-specific self-service APIs
     * (e.g. Cognito {@code SignUp}, Keycloak self-registration).
     *
     * @param request the self-service registration details
     * @return a reactive publisher with the created user's summary
     */
    default Mono<ResponseEntity<CreateUserResponse>> registerUser(RegisterUserRequest request) {
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .givenName(request.getFirstName())
                .familyName(request.getLastName())
                .build();
        return createUser(createRequest);
    }
}
