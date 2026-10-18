# Internal database adapter API

`InternalDbIdpAdapter` implements the shared [`IdpAdapter`](../../security-idp/src/main/java/org/fireflyframework/security/idp/adapter/IdpAdapter.java) contract. Its methods return Reactor publishers. The [implementation](../src/main/java/org/fireflyframework/security/idp/internaldb/adapter/InternalDbIdpAdapter.java) maps service results into the shared DTOs and, where specified, `ResponseEntity` values.

## HTTP routes

The shared [`IdpController`](../../security-idp/src/main/java/org/fireflyframework/security/idp/web/IdpController.java) uses `/idp`. [`IdpWebAutoConfiguration`](../../security-idp/src/main/java/org/fireflyframework/security/idp/config/IdpWebAutoConfiguration.java) registers it in a reactive web application when an `IdpAdapter` bean is available. The application owns authentication and authorization rules for these routes, including administrator access.

| Method | Route | Adapter operation |
| --- | --- | --- |
| POST | `/idp/login` | `login(LoginRequest)` |
| POST | `/idp/refresh` | `refresh(RefreshRequest)` |
| POST | `/idp/logout` | `logout(LogoutRequest)` |
| GET | `/idp/introspect` | `introspect(String)` |
| GET | `/idp/userinfo` | `getUserInfo(String)` |
| POST | `/idp/revoke-refresh-token` | `revokeRefreshToken(String)`; `refreshToken` request parameter |
| POST | `/idp/register` | Shared `registerUser(RegisterUserRequest)` contract |
| POST | `/idp/admin/users` | `createUser(CreateUserRequest)` |
| PUT | `/idp/admin/users` | `updateUser(UpdateUserRequest)` |
| DELETE | `/idp/admin/users/{userId}` | `deleteUser(String)` |
| POST | `/idp/admin/password` | `changePassword(ChangePasswordRequest)` |
| POST | `/idp/admin/password/reset` | `resetPassword(String)`; `username` request parameter |
| POST | `/idp/admin/mfa/challenge` | `mfaChallenge(String)`; `username` request parameter |
| POST | `/idp/admin/mfa/verify` | `mfaVerify(MfaVerifyRequest)` |
| GET | `/idp/admin/users/{userId}/sessions` | `listSessions(String)` |
| DELETE | `/idp/admin/sessions/{sessionId}` | `revokeSession(String)` |
| GET | `/idp/admin/users/{userId}/roles` | `getRoles(String)` |
| POST | `/idp/admin/roles` | `createRoles(CreateRolesRequest)` |
| POST | `/idp/admin/scopes` | `createScope(CreateScopeRequest)` |
| POST | `/idp/admin/users/roles/assign` | `assignRolesToUser(AssignRolesRequest)` |
| POST | `/idp/admin/users/roles/remove` | `removeRolesFromUser(AssignRolesRequest)` |

These are the common controller mappings. A deployment must test its chosen adapter, DTO validation, security chain, and route exposure together. The internal adapter treats a created scope as a role; it does not implement a full OAuth2 scope model.

## Response and token handling

Login and refresh return a token response on success and an HTTP 401 response with an empty token DTO on failure. Introspection returns `active=false` for an invalid token. These responses do not use the former Security Center's example JSON error envelope.

The controller passes the `Authorization` header unchanged to introspection and user-info methods. The internal adapter parses the supplied value as a JWT. Check header normalization in the host integration before using these endpoints with a Bearer authentication chain; this library's controller does not strip the `Bearer ` prefix.

## DTOs and application responsibilities

Use [DTO field mapping](DTO_FIELD_MAPPING.md) and the [shared DTO sources](../../security-idp/src/main/java/org/fireflyframework/security/idp/dtos) when building requests. The module does not supply application-specific account linking, approval decisions, password-reset delivery, or route-level rate limits.

The [integration guide](INTEGRATION.md) covers the current dependency and property names. The [security identity guide](../../../docs/site/modules/security-idp.md) explains the wider contract.
