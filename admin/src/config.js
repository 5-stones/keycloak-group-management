// Detects whether the SPA is running standalone (Vite dev server on :3001) or
// bundled inside the Keycloak plugin and served at /realms/{realm}/group-mgmt/config/.
//
// - Bundled mode: read realm from the URL path, use window.location.origin as the
//   Keycloak base URL, and treat /realms/{realm}/group-mgmt/config as the SPA root.
// - Standalone mode: hit Keycloak at :8080 (proxied through Vite's dev server).
const BUNDLED_PATH_REGEX = /^\/realms\/([^/]+)\/group-mgmt\/config(\/|$)/

function detectBundled() {
  if (typeof window === 'undefined') return null
  const match = window.location.pathname.match(BUNDLED_PATH_REGEX)
  if (!match) return null
  const realm = decodeURIComponent(match[1])
  return {
    keycloakUrl: window.location.origin,
    realm,
    basePath: `/realms/${match[1]}/group-mgmt/config`,
  }
}

const bundled = detectBundled()

export const KEYCLOAK_URL = bundled?.keycloakUrl ?? 'http://localhost:8080'
export const REALM = bundled?.realm ?? 'master'
export const CLIENT_ID = 'group-mgmt-test-ui'
// Path prefix the SPA is mounted at — empty in standalone mode, the realm/group-mgmt/config
// prefix in bundled mode. Used by React Router as basename.
export const BASE_PATH = bundled?.basePath ?? ''
export const IS_BUNDLED = bundled != null
