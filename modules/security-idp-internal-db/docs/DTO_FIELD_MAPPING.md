# DTO field mapping

The adapter uses DTOs from `org.fireflyframework.security.idp.dtos`. Entity fields belong to the internal database module; HTTP payloads follow the shared DTO names.

| DTO | Mapping |
| --- | --- |
| `CreateUserRequest` | `givenName` and `familyName` map to entity `firstName` and `lastName`. New internal users are enabled by `UserManagementService`; an `enabled` entry in generic attributes does not change that behavior. |
| `CreateUserResponse` / `UpdateUserResponse` | The identifier is `id`. Creation and update timestamps use `Instant`. These response DTOs do not expose the entity's first/last-name fields. |
| `UpdateUserRequest` | `givenName`, `familyName`, and the optional Boolean `enabled` update the matching entity fields. The target identifier is `userId`. |
| `UserInfoResponse` | Uses `preferredUsername`, `givenName`, and `familyName`. It has no `roles` or generic `attributes` field; request roles through the adapter's roles operation. |
| `SessionInfo` | `createdAt`, `lastAccessAt`, and `expiresAt` are separate `Instant` fields. The internal adapter sets `createdAt` and `expiresAt` from the session entity. |
| `CreateRolesResponse` | Returns `createdRoleNames`. |
| `CreateScopeRequest` | Uses `name` and `description`. The internal adapter stores the scope as a role. |
| `CreateScopeResponse` | Uses `id`, `name`, and `createdAt`. |

See the [DTO definitions](../../security-idp/src/main/java/org/fireflyframework/security/idp/dtos), [adapter mapping code](../src/main/java/org/fireflyframework/security/idp/internaldb/adapter/InternalDbIdpAdapterImpl.java), and [user service](../src/main/java/org/fireflyframework/security/idp/internaldb/service/UserManagementService.java).
