# Keycloak Group Management Plugin

A Keycloak SPI plugin that adds local group administration, invitation management, and membership controls via a REST API — plus an optional bundled admin UI for realm operators.

## Features

- **Custom Roles** — Each group member can hold any number of arbitrary string roles. `admin` is the conventional privileged role; holders can manage members, roles, and invitations within the group. Other roles are application-defined and stored as data.
- **Per-role Permissions** — A realm-level role-to-permission map gates each REST endpoint individually (`members:read`, `invitations:write`, `group:write`, etc.). Privilege-escalation guard prevents non-admins from granting roles that exceed their own access.
- **Invitations** — Group admins invite users by email with an optional list of roles to grant on acceptance. Token-based, expiring, unique per (user, group). Inviting triggers a templated email; acceptance is a one-click browser flow that handles registration if needed.
- **Membership Management** — List, filter by role, set roles, remove members. Last-admin guard prevents accidentally orphaning a group.
- **JWT Token Mapper** — Configurable OIDC protocol mapper that emits the user's group memberships and per-group roles as a token claim.
- **Per-Realm Configuration** — Invitation TTL, post-accept redirect URL, role vocabulary, and role-to-permission map are all per-realm attributes.
- **Bundled Admin UI** — Optional React/Tailwind config UI packaged into the JAR (`./gradlew bundleAdminUi`) and served by Keycloak at `/realms/{realm}/group-mgmt/config/`.
- **Pagination, search, sorting** on every list endpoint.

## Documentation

| Document | What it covers |
|---|---|
| [docs/api.md](docs/api.md) | REST API reference, query parameters, JWT claim shape |
| [docs/authorization.md](docs/authorization.md) | Auth model, role vocabulary, permissions, privilege-escalation guard, last-admin guard |
| [docs/configuration.md](docs/configuration.md) | Per-realm config attributes and how to edit them |
| [docs/development.md](docs/development.md) | Local setup, build commands, bundling the SPA, running tests |
| [docs/deployment.md](docs/deployment.md) | Production deployment, required OIDC clients, database compatibility |

## Bundled admin UI

The plugin by default includes an optional membership admin UI served by Keycloak at `/realms/{realm}/group-mgmt/config/`. Realm and group admins share the same per-group dashboard; the Configuration tab is shown only to realm admins.

### Group dashboard

![Group dashboard with the member list, role chips, and per-row action icons](docs/assets/group-membership-admin-2-member-list.png)

Invite, role-edit, and remove members inline. Members support search and role filtering; the Invitations tab carries a pending-count badge.

### Realm configuration

![Realm-admin configuration page with the Roles & Permissions editor and invitation defaults](docs/assets/group-membership-admin-8-plugin-config.png)

Realm admins manage the role vocabulary, per-role permissions, post-accept redirect URL, and invitation TTL from the browser — no admin-console attribute editing required. See [docs/configuration.md](docs/configuration.md) for the underlying realm attributes and the REST equivalent.

## Install in your Keycloak instance

We publish a built JAR (with the bundled admin UI included) for every tagged release of this repo. Download it and drop it into your Keycloak's `providers/` directory.

```bash
export VERSION=1.0.0
wget "https://github.com/5-stones/keycloak-group-management/releases/download/v$VERSION/keycloak-group-management-$VERSION.jar"
```

In a `Dockerfile`:

```Dockerfile
ENV GROUP_MGMT_VERSION=1.0.0
ADD --chown=keycloak:keycloak \
  "https://github.com/5-stones/keycloak-group-management/releases/download/v$GROUP_MGMT_VERSION/keycloak-group-management-$GROUP_MGMT_VERSION.jar" \
  /opt/keycloak/providers/
```

> **Note:** `ADD` is used because modern `quay.io/keycloak/keycloak` base images [don't ship a package manager](https://www.keycloak.org/server/containers#_installing_additional_rpm_packages).

After placing the JAR, restart Keycloak. For production-mode (optimized) installs, run `kc.sh build` first so Quarkus picks up the new provider. On first start, the plugin auto-creates the `group_invitation` and `group_member_role` tables via Liquibase — no external migration step needed.

### Post-install configuration

Before users can hit the API, configure two OIDC clients in each realm where the plugin is used (`group-mgmt` for the invitation accept flow and `group-mgmt-test-ui` for the bundled SPA / any frontend you ship). Full client config — including required redirect URIs and Web Origins — is in [docs/deployment.md](docs/deployment.md). Once that's in place, realm admins can manage everything else from the bundled admin UI at `/realms/{realm}/group-mgmt/config/`.

Add the **Group Management Role** OIDC mapper to the client scope used by your application clients to surface the `group_roles` claim in tokens (see [docs/api.md](docs/api.md#jwt-token-mapper)).

## Local development environment

`docker compose up` boots the full stack — Keycloak, Postgres, Mailpit, and the admin SPA dev server — wired up against a pre-imported `master` realm with the dev OIDC client ready to go.

```bash
./gradlew bundleAdminUi build && docker compose up   # full stack incl. bundled admin UI
./gradlew build && docker compose up                 # backend only (no Node.js needed)
```

| Service | URL | What it is | Stack |
|---|---|---|---|
| **Keycloak** | <http://localhost:8080> · login `admin` / `admin` | The plugin host. | Kotlin · JVM 17 · Keycloak 26.3.3 SPIs (`RealmResourceProvider`, `JpaEntityProvider`, `ProtocolMapper`) · Gradle + Shadow |
| **Bundled admin UI** | <http://localhost:8080/realms/master/group-mgmt/config/> | The shipped admin UI served from inside the plugin JAR (only after `bundleAdminUi`). | React 19 · Tailwind v4 + `@tailwindcss/forms` · react-select · oidc-client-ts · Vite |
| **Admin SPA (dev)** | <http://localhost:3000> | Hot-reloading Vite dev server for `admin/`. Same code as the bundled UI. | Same as bundled admin UI |
| **Mailpit** | <http://localhost:8025> | Captures every invitation email the plugin sends. | — |
| **PostgreSQL** | `localhost:5432` · creds `keycloak` / `keycloak` | Plugin tables (`group_invitation`, `group_member_role`) live here alongside Keycloak's own. | PostgreSQL 15 in dev; the plugin is also compatible with MariaDB / MySQL / MSSQL / Oracle (Liquibase uses generic types) |

Tests run in-process against an H2 in-memory database (JUnit 5 + Hibernate); no Docker needed. For the full developer workflow — hot-reload, restart-on-rebuild, test commands, the SPA bundling pipeline — see [docs/development.md](docs/development.md).
