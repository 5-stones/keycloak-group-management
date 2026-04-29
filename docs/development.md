# Development

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Node.js (only required when building or iterating on the bundled admin SPA — backend-only builds skip it)

## Build

```bash
./gradlew build                  # backend only (no Node.js needed)
./gradlew bundleAdminUi build    # backend + admin SPA bundled into the JAR
```

The plugin JAR is output to `build/libs/keycloak-group-management-1.0.0-SNAPSHOT.jar`.

## Run

```bash
./gradlew build && docker compose up
```

First run requires `docker compose down -v` if schema or realm-import changes are made.

## Services

| Service | URL | Description |
|---------|-----|-------------|
| **Keycloak** | [http://localhost:8080](http://localhost:8080) | Keycloak admin console. Login: `admin` / `admin` |
| **Bundled admin UI** | [http://localhost:8080/realms/master/group-mgmt/config/](http://localhost:8080/realms/master/group-mgmt/config/) | Realm-admin config UI served from the plugin JAR. Only present after `bundleAdminUi`. |
| **Admin SPA (dev)** | [http://localhost:3000](http://localhost:3000) | Vite dev server for the admin SPA in `admin/`. Hot reload. Same code as the bundled UI. |
| **Mailpit** | [http://localhost:8025](http://localhost:8025) | Email inbox. All invitation emails sent by the plugin appear here. |
| **PostgreSQL** | `localhost:5432` | Database. Credentials: `keycloak` / `keycloak` |

## Bundling the admin UI into the JAR

The admin SPA in `admin/` (the realm-admin config UI) can be packaged inside the plugin JAR and served by Keycloak at `/realms/{realm}/group-mgmt/config/`. This is opt-in — the default `./gradlew build` produces a backend-only JAR.

```bash
./gradlew bundleAdminUi build      # full build incl. tests + bundled SPA
./gradlew bundleAdminUi shadowJar  # skip tests; faster iteration
```

What the `bundleAdminUi` task does:

1. Runs `npm install` in `admin/` (only if `node_modules/` is missing).
2. Runs `npm run build` (Vite). Output goes to `admin/dist/`.
3. Copies `admin/dist/*` to `build/admin-ui/`. The standard `processResources` task picks that up and stages it under `build/resources/main/admin-ui/`, which `shadowJar` packages into the JAR at the path `admin-ui/`.
4. At runtime, `UiResource` (`com.weare5stones.keycloak.groupmgmt.rest.UiResource`) reads files out of the classpath at `admin-ui/...` and serves them.

After bundling, redeploy:

```bash
./gradlew bundleAdminUi build && docker compose restart keycloak
```

(Bundle changes don't require wiping the DB — only schema or realm-import changes do.)

If you build without the bundle, the URL above returns a friendly 404 with a hint pointing back to this command.

**Iterating on the admin UI**: use the `admin` Vite dev server at `http://localhost:3000` for fast iteration (Vite HMR; backend calls proxied to Keycloak). Only re-run `bundleAdminUi` when you're ready to ship the change in the JAR.

## Tests

The Kotlin test suite covers the privilege-escalation rules (pure-logic tests) and the data-access layer's cross-group isolation (JPA tests against an in-memory H2 database — no Keycloak or Docker required).

```bash
./gradlew test                          # run all tests
./gradlew test --rerun-tasks            # force re-run when nothing changed
./gradlew cleanTest test                # equivalent: wipe cache, then run
```

Filter to a specific class or test:

```bash
./gradlew test --tests "GroupRoleServiceGrantTest"           # all grant rules
./gradlew test --tests "GroupRoleServiceJpaIsolationTest"    # cross-group isolation
./gradlew test --tests "*realm-scoped*"                       # any test name match
```

Each test prints `PASSED` / `FAILED` / `SKIPPED` as it runs and ends with a one-line summary. Failures show full stack traces. The HTML report lands at `build/reports/tests/test/index.html`.

**What's covered:**

- `GroupRoleServiceGrantTest` (~37 tests, pure functions): admin/realm-admin bypass, `admin`-only-by-admin rule, permission-subset rule, missing-permission reporting, `member` baseline propagation, end-to-end role-grant scenarios, permission vocabulary completeness.
- `GroupRoleServiceJpaIsolationTest` (~16 tests, real JPQL on H2): `(realm, group, user)` predicate enforcement on every read function — `getRoles`, `getMembersWithRole`, `getAllMemberRoles`, `getRolesForUserInGroups`. Confirms a user's roles in group A do not leak into queries against group B (or another realm).
- `GroupRoleServiceCrossGroupTest` (~22 tests, integrated permission resolution on H2): exercises `hasPermission` and `evaluateGrantOnGroup` end-to-end. Covers exactly the scenarios that worry an operator: admin in group A acting on group B (without/with membership), viewer in B trying to escalate, manager in A having no power in B, realm-admin bypass everywhere, realm-level isolation when group IDs collide, and multi-role users with different perms per group.

The tests do not require `docker compose` to be running. They spin up their own H2 instance per test class.

## Restart after code changes

The plugin does not hot-reload. After rebuilding:

```bash
./gradlew build && docker compose restart keycloak
```

## Reset everything

```bash
docker compose down -v && ./gradlew build && docker compose up
```

## Cutting a release

The project ships pre-built JARs as GitHub release assets via `.github/workflows/release.yml`. The release flow is `npm version`-driven, mirroring our other Keycloak plugins.

**One-time setup** (just for the maintainer, on first checkout):

```bash
npm install   # installs conventional-changelog-cli for CHANGELOG generation
```

**To cut a release**:

```bash
npm version patch    # or `minor` / `major` — uses semver
```

That single command runs the following steps in order (npm's lifecycle hooks):

1. Bumps `version` in `package.json`.
2. Runs `bin/release.js`, which rewrites the `artifactVersion` line in `gradle.properties` to match.
3. Regenerates `CHANGELOG.md` via `conventional-changelog -p angular` from your commit history (commits should follow [Conventional Commits](https://www.conventionalcommits.org/) — `feat:`, `fix:`, `chore:`, etc.).
4. Stages `gradle.properties` and `CHANGELOG.md`.
5. Creates a commit (`v1.2.3`) and a matching git tag.
6. Pushes both the commit and the tag (`postversion` script).

The push of the `v*` tag triggers `.github/workflows/release.yml`, which:

1. Sets up JDK 17 and Node 22 (Node is required because `bundleAdminUi` runs `npm run build` in `admin/`).
2. Verifies `gradle.properties` `artifactVersion` matches the tag (catches manual-tag mistakes).
3. Runs `./gradlew bundleAdminUi build` — tests run as part of `build`, so failing tests abort the release.
4. Creates a GitHub release with auto-generated release notes (commits since the previous tag).
5. Uploads `build/libs/keycloak-group-management-1.2.3.jar` as the release asset, named to match the tag.

**Source of truth for the version**: `package.json`. `gradle.properties` is downstream — never edit it by hand for releases; let `npm version` sync it. (You can still override the version ad-hoc for one-off builds with `./gradlew -PartifactVersion=... bundleAdminUi build`.)

**Pre-releases**: tags like `v1.2.3-rc1` will trigger the workflow same as any other `v*` tag. If you want them flagged as pre-releases on GitHub, edit `prerelease: false` in the workflow or mark the release manually after publish.
