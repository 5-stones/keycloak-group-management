# Configuration

Plugin configuration is per-realm, stored as realm attributes. Realm admins can read/write the configuration via the [`/api/config` endpoint](api.md#configuration) or, if the JAR is built with the bundled SPA, via the admin UI at `/realms/{realm}/group-mgmt/admin/`.

## Realm attributes

| Key | Description | Default |
|-----|-------------|---------|
| `group-invitation-ttl-hours` | Default invitation expiry in hours | `72` |
| `group-mgmt-post-accept-url` | URL to redirect to after invitation acceptance | _(shows built-in HTML page)_ |
| `group-mgmt-allowed-roles` | Comma-separated list of additional role names that can be assigned to members. `admin` and `member` are always implicit and do not need to be listed. | _(unset = no extra roles)_ — the vocabulary is always `admin` + `member` + the values listed here. |
| `group-mgmt-role-permissions` | Optional JSON object mapping role names to permission lists, e.g. `{"viewer":["members:read"],"manager":["members:read","members:write","roles:write"]}`. Permissions are validated against the vocabulary on PUT. Custom roles named here must also be in `group-mgmt-allowed-roles`; `member` is implicit and is always allowed. The `admin` role is reserved (cannot be redefined) and grants all permissions implicitly. | _(unset = no role-level permissions; only `admin` grants access)_ |

See [Authorization](authorization.md) for the semantics of `group-mgmt-allowed-roles` and `group-mgmt-role-permissions`.

## Editing via the bundled admin UI

![Bundled realm-admin configuration UI showing Roles & Permissions and Invitations](assets/group-membership-admin-8-plugin-config.png)

If the JAR was built with `./gradlew bundleAdminUi`, realm admins can manage these attributes from the browser at `/realms/{realm}/group-mgmt/admin/`. The UI provides:

- Plain text fields for `group-mgmt-post-accept-url` and `group-invitation-ttl-hours`.
- A structured Roles & Permissions editor that writes both `group-mgmt-allowed-roles` and `group-mgmt-role-permissions` atomically. Each row is one role; permissions are picked from a multi-select. `admin` is shown read-only at the top (reserved), `member` is shown as a baseline-perms row.

See [Development → Bundling the admin UI](development.md#bundling-the-admin-ui-into-the-jar) for how to package the SPA into the JAR.

## Customizing the invitation email

The plugin renders invitation emails through Keycloak's standard `EmailTemplateProvider`, so it customizes the same way every other Keycloak email does — via an email theme. The plugin ships defaults under `theme-resources/` (English subject/body in `messages_en.properties`; HTML and text wrappers that delegate to Keycloak's `<@layout.emailLayout>`).

### Message keys

Override any of these in `messages/messages_<lang>.properties` of your custom email theme:

| Key | Where it shows up |
|-----|-------------------|
| `groupInvitationSubject` | Subject line. Receives `{0} = groupName`. |
| `groupInviteBody` | Plain-text body |
| `groupInviteBodyHtml` | HTML body |

The two body keys receive the same five positional placeholders:

| Placeholder | Value |
|-------------|-------|
| `{0}` | `acceptUrl` — one-click accept link |
| `{1}` | `expiresAt` — ISO expiry timestamp |
| `{2}` | `realmName` — realm `displayName` or `name` |
| `{3}` | `groupName` |
| `{4}` | `inviterName` |

### Override with a custom theme

Create a Keycloak email theme alongside the plugin (typical Keycloak theme deployment — drop it under `themes/` in your distribution or pack it into a theme JAR):

```
themes/<your-theme>/email/
├── theme.properties              # parent=base
└── messages/
    └── messages_en.properties    # override any of the three keys above
```

`theme.properties`:

```properties
parent=base
```

`messages/messages_en.properties` (overriding just the bodies):

```properties
groupInvitationSubject=You''re invited to {0}
groupInviteBodyHtml=<p>{4} invited you to join <strong>{3}</strong>. <a href="{0}">Accept</a> (expires {1}).</p>
groupInviteBody={4} invited you to join {3}. Accept: {0} (expires {1}).
```

Then set the realm's **Email theme** to `<your-theme>`. For localization, ship a `messages_<lang>.properties` per language. To restyle the surrounding HTML wrapper instead of just the body, override `email/html/group-invitation.ftl` and `email/text/group-invitation.ftl` in the same theme — the plugin's defaults only invoke `<@layout.emailLayout>`, so replace that markup if you want full control.

If SMTP isn't configured for the realm, the plugin logs the email contents instead of sending — handy when developing without Mailpit.

## Editing via REST

`PUT /realms/{realm}/group-mgmt/api/config` with a JSON body whose keys match the attribute names above. Empty / blank values clear the attribute. The endpoint validates `group-mgmt-role-permissions` JSON before applying any changes — invalid input returns 400 with a message naming the offending role/permission, and no other attributes are touched.

Example:

```http
PUT /realms/master/group-mgmt/api/config
Authorization: Bearer ...
Content-Type: application/json

{
  "group-invitation-ttl-hours": "48",
  "group-mgmt-allowed-roles": "viewer,manager,billing",
  "group-mgmt-role-permissions": "{\"viewer\":[\"members:read\"],\"manager\":[\"members:read\",\"members:write\",\"roles:write\",\"invitations:read\",\"invitations:write\"]}"
}
```
