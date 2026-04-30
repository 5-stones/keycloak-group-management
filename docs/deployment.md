# Deployment

Download the pre-built JAR from the [Releases](https://github.com/5-stones/keycloak-group-management/releases) page — see [README → Install in your Keycloak instance](../README.md#install-in-your-keycloak-instance) for the full install steps (`wget` and `Dockerfile` recipes).

The plugin automatically creates its `group_invitation` and `group_member_role` database tables via Liquibase on first boot. No external migration step is required.

## OIDC Clients

The plugin uses Keycloak's built-in `security-admin-console` client by default for both the bundled admin SPA and the invitation accept flow. **No new clients to create** — just edit the existing one.

### Setup: extend `security-admin-console`

The realm's `security-admin-console` client (the same one Keycloak's own admin console uses) needs two extra entries:

- **Valid Redirect URIs**: append `http://your-keycloak/realms/{realm}/group-mgmt/*` (and `http://localhost:3000/*` for the dev Vite server, if you use it).
- **Web Origins**: append the same hosts.

After that, both flows work out of the box. The audience for the admin SPA is realm/group admins, so the admin-console look-and-feel is appropriate. Invitees clicking email links will also see admin-console-themed login by default — fine for internal use, but probably wrong for customer-facing flows.

### Customer-facing invitation branding

If you ship a customer-facing UI with its own OIDC client (custom theme, different IdPs, registration flow, MFA policy), you almost certainly want invitees to see *that* login experience instead of the admin-console default. Override it from the bundled admin UI's **Configuration** page → **Invitation Login Client ID**, or by setting the realm attribute directly:

```
group-mgmt-invitation-client-id = your-customer-client
```

When this attribute is set, the plugin uses your client's `client_id` when redirecting invitees to login. The only constraint: that client must include `http://your-keycloak/realms/{realm}/group-mgmt/invitations/accept` in its Valid Redirect URIs. Web Origins for `your-keycloak` should also be set on that client so the post-accept redirect works.

## Bundled admin SPA

When the JAR is built with `./gradlew bundleAdminUi`, the admin UI is served from the JAR at `/realms/{realm}/group-mgmt/admin/`. Realm and group admins log in via OIDC (same redirect-URI requirements as above) and manage groups, members, and invitations from a browser; realm admins additionally get a Configuration page that edits the plugin's realm attributes without the operator having to touch them by hand. See [Development → Bundling the admin UI](development.md#bundling-the-admin-ui-into-the-jar) for the build pipeline.

## Database compatibility

Tested against PostgreSQL 15. The Liquibase changelog uses generic types (VARCHAR, BIGINT) and should work on any of the databases Keycloak itself supports — PostgreSQL, MariaDB, MySQL, MSSQL, Oracle. The plugin uses `BIGINT` for timestamps (epoch milliseconds) rather than database-native datetime types to avoid driver-specific edge cases.
