# Deployment

Download the pre-built JAR from the [Releases](https://github.com/5-stones/keycloak-group-management/releases) page — see [README → Install in your Keycloak instance](../README.md#install-in-your-keycloak-instance) for the full install steps (`wget` and `Dockerfile` recipes).

The plugin automatically creates its `group_invitation` and `group_member_role` database tables via Liquibase on first boot. No external migration step is required.

## OIDC Clients

The plugin uses (or expects) two public OIDC clients per realm. Both ship in the dev `master-realm.json` import; production deployments need to recreate them.

### `group-mgmt` — invitation accept browser flow

- **Standard Flow Enabled**: `true`
- **Valid Redirect URIs**: the accept endpoint, e.g. `http://your-keycloak/realms/{realm}/group-mgmt/*`
- **Registration Enabled** (realm-level): recommended so invited users can register during the accept flow.

### `group-mgmt-test-ui` — used by the admin SPA

(The client ID is historical; it's the OIDC client the bundled/dev admin SPA logs into.)

- **Standard Flow Enabled**: `true`
- **Valid Redirect URIs**: include the bundled SPA's callback path, e.g. `http://your-keycloak/realms/{realm}/group-mgmt/config/*`. Add `http://localhost:3000/*` for dev.
- **Web Origins**: include the same hosts (`http://your-keycloak`, plus `http://localhost:3000` in dev) — required for the OIDC token-exchange CORS preflight.

If you only ship the backend (no `bundleAdminUi`) and don't use the dev admin SPA, you can skip this client.

## Bundled admin SPA

When the JAR is built with `./gradlew bundleAdminUi`, the realm-admin config UI is served from the JAR at `/realms/{realm}/group-mgmt/config/`. Realm admins log in via OIDC (same redirect-URI requirements as above) and configure the plugin from a browser without touching realm attributes manually. See [Development → Bundling the admin UI](development.md#bundling-the-admin-ui-into-the-jar) for the build pipeline.

## Database compatibility

Tested against PostgreSQL 15. The Liquibase changelog uses generic types (VARCHAR, BIGINT) and should work on any of the databases Keycloak itself supports — PostgreSQL, MariaDB, MySQL, MSSQL, Oracle. The plugin uses `BIGINT` for timestamps (epoch milliseconds) rather than database-native datetime types to avoid driver-specific edge cases.
