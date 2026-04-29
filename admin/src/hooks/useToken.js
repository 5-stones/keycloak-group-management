import { useCallback } from 'react'
import userManager from '../auth.js'

/**
 * Returns a function that resolves to a fresh OIDC access token, redirecting to
 * the login page if the local session is missing or expired.
 */
export default function useToken() {
  return useCallback(async () => {
    const user = await userManager.getUser()
    if (!user || user.expired) {
      await userManager.signinRedirect()
      return null
    }
    return user.access_token
  }, [])
}
