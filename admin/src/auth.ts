import { UserManager, WebStorageStateStore } from 'oidc-client-ts'
import { KEYCLOAK_URL, REALM, CLIENT_ID, BASE_PATH, IS_BUNDLED } from './config'

// In bundled mode the SPA is served from Keycloak under BASE_PATH; redirects must
// target the same origin/path. In standalone mode, fall back to the dev-server URL.
const origin = (typeof window !== 'undefined') ? window.location.origin : 'http://localhost:3000'
const appOrigin = IS_BUNDLED ? `${origin}${BASE_PATH}` : 'http://localhost:3000'

const userManager = new UserManager({
  authority: `${KEYCLOAK_URL}/realms/${REALM}`,
  client_id: CLIENT_ID,
  redirect_uri: `${appOrigin}/callback`,
  post_logout_redirect_uri: `${appOrigin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  automaticSilentRenew: true,
})

export default userManager
