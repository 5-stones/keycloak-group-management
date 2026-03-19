# Keycloak Group Management Plugin

A Keycloak SPI plugin that adds local group administration, invitation management, and membership controls via a REST API.

## Features

- **Group Admins** -- Any group can have designated admins (tracked via group attributes) who can manage that group's members and invitations without needing realm-level admin access.
- **Invitations** -- Group admins can invite users by email. Invitations are token-based, expiring, and unique per user/group. Inviting triggers an email to the recipient.
- **Invitation Acceptance** -- A browser-based flow handles login/registration and acceptance in a single click from the email link. Non-existing users can register during the flow.
- **Membership Management** -- List, remove, promote, and demote group members via API.
- **JWT Token Mapper** -- A configurable OIDC protocol mapper that adds group membership and role (admin/member) to access tokens and ID tokens.
- **Pagination** -- All list endpoints return paginated responses with metadata.
- **Search** -- Member and group listings support search queries pushed to the database.
- **Sorting** -- Member listing supports DB-level sorting by username, email, name, and admin status. Group listing supports sorting by name.
- **Per-Realm Configuration** -- Configurable invitation TTL and post-accept redirect URL per realm.
- **Email Templating** -- Invitation emails use Keycloak's FreeMarker templating system and can be customized via realm email themes.

## REST API

Base path: `/realms/{realm}/group-mgmt`

### Invitations

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/groups/{groupId}/invitations` | Create invitation. Body: `{ "email": "...", "role": "member\|admin", "ttlHours": 72 }` |
| `GET` | `/groups/{groupId}/invitations` | List invitations. Params: `page`, `pageSize` |
| `GET` | `/groups/{groupId}/invitations/{id}` | Get invitation |
| `DELETE` | `/groups/{groupId}/invitations/{id}` | Delete/revoke invitation |
| `POST` | `/groups/{groupId}/invitations/{id}/resend` | Resend invitation email |

### Members

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/groups/{groupId}/members` | List members. Params: `page`, `pageSize`, `search`, `sortBy`, `sortDir` |
| `DELETE` | `/groups/{groupId}/members/{userId}` | Remove member |
| `PUT` | `/groups/{groupId}/members/{userId}/promote` | Promote to group admin |
| `PUT` | `/groups/{groupId}/members/{userId}/demote` | Demote from group admin |

### Invitation Acceptance

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/invitations/accept?token=...` | Browser flow: login/register then accept |
| `POST` | `/invitations/accept?token=...` | API flow: accepts with bearer token |

### User

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/me/groups` | List groups for the authenticated user. Params: `page`, `pageSize`, `search`, `sortBy`, `sortDir` |

### Configuration

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/config` | Get realm plugin configuration (realm admin only) |
| `PUT` | `/config` | Update realm plugin configuration (realm admin only) |

### Query Parameters

**Pagination** (all list endpoints):

| Param | Description | Default |
|-------|-------------|---------|
| `page` | Page number (1-based) | `1` |
| `pageSize` | Items per page (1-100) | `20` |

**Members endpoint** (`/groups/{groupId}/members`):

| Param | Description | Default |
|-------|-------------|---------|
| `search` | Partial match on username, email, first name, last name | _(none)_ |
| `sortBy` | Sort field: `username`, `email`, `name`, `admin` | `username` |
| `sortDir` | Sort direction: `asc`, `desc` | `asc` |

**Groups endpoint** (`/me/groups`):

| Param | Description | Default |
|-------|-------------|---------|
| `search` | Partial match on group name | _(none)_ |
| `sortBy` | Sort field: `name` | `name` |
| `sortDir` | Sort direction: `asc`, `desc` | `asc` |

**Configurable realm attributes:**

| Key | Description | Default |
|-----|-------------|---------|
| `group-invitation-ttl-hours` | Default invitation expiry in hours | `72` |
| `group-mgmt-post-accept-url` | URL to redirect to after invitation acceptance | _(shows built-in HTML page)_ |

## Authorization

| Role | Scope |
|------|-------|
| **Realm admin** (`admin` role or `manage-users` on `realm-management`/`{realm}-realm` client) | All operations across all groups |
| **Group admin** (user ID in the group's `group-admins` attribute) | Manage invitations and members within their group |
| **Authenticated user** | Accept invitations sent to their email, list their own groups |

## JWT Token Mapper

The plugin registers a **Group Management Role** protocol mapper. Once added to a client scope, it emits a claim like:

```json
{
  "group_roles": [
    { "id": "50b9d34a-...", "role": "admin", "name": "Engineering" },
    { "id": "a1b2c3d4-...", "role": "member", "name": "Marketing" }
  ]
}
```

**Configuration** (in Keycloak admin: Client Scopes > Mappers > Add mapper > By configuration > Group Management Role):

| Option | Description | Default |
|--------|-------------|---------|
| Token Claim Name | The claim key in the token | `group_roles` |
| Include Group Name | Include group name in each entry | `true` |
| Include in ID Token | Add claim to ID token | `true` |
| Include in Access Token | Add claim to access token | `true` |
| Include in Userinfo | Add claim to userinfo response | `true` |

## Development

### Prerequisites

- Java 17+
- Docker & Docker Compose

### Build

```bash
./gradlew build
```

The plugin JAR is output to `build/libs/keycloak-group-management-1.0.0-SNAPSHOT.jar`.

### Run

```bash
./gradlew build && docker compose up
```

First run requires `docker compose down -v` if schema or realm import changes are made.

### Services

| Service | URL | Description |
|---------|-----|-------------|
| **Keycloak** | [http://localhost:8080](http://localhost:8080) | Keycloak admin console. Login: `admin` / `admin` |
| **Test UI** | [http://localhost:3000](http://localhost:3000) | React app for testing all plugin APIs. Authenticates via OIDC. |
| **Mailpit** | [http://localhost:8025](http://localhost:8025) | Email inbox. All invitation emails sent by the plugin appear here. |
| **PostgreSQL** | `localhost:5432` | Database. Credentials: `keycloak` / `keycloak` |

### Restart after code changes

The plugin does not hot-reload. After rebuilding:

```bash
./gradlew build && docker compose restart keycloak
```

### Reset everything

```bash
docker compose down -v && ./gradlew build && docker compose up
```

## Deployment

Copy `build/libs/keycloak-group-management-1.0.0-SNAPSHOT.jar` into Keycloak's `providers/` directory and restart (or run `kc.sh build`).

The plugin automatically creates its `group_invitation` database table via Liquibase on first boot.

### OIDC Client

The plugin requires a `group-mgmt` public OIDC client in each realm where the invitation acceptance browser flow is used. The client needs:

- **Standard Flow Enabled**: `true`
- **Valid Redirect URIs**: the accept endpoint URL (e.g., `http://your-keycloak/realms/{realm}/group-mgmt/*`)
- **Registration Enabled**: recommended on the realm so invited users can create accounts

## Tech Stack

- **Kotlin** with JVM 17
- **Keycloak 26.3.3** SPIs: `RealmResourceProvider`, `JpaEntityProvider`, `ProtocolMapper`
- **Gradle** with Shadow plugin for fat JAR packaging
- **PostgreSQL** / MariaDB / MySQL compatible (uses `BIGINT` for timestamps)
