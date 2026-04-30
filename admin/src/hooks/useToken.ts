import { useCallback } from 'react'
import userManager from '../auth'

/**
 * Returns a function that resolves to a fresh OIDC access token, redirecting to
 * the login page if the local session is missing or expired.
 */
export default function useToken(): () => Promise<string> {
  return useCallback(async () => {
    const user = await userManager.getUser()
    if (!user || user.expired) {
      await userManager.signinRedirect()
      // After redirect the page navigates away — caller code below this point
      // never runs. Throwing keeps the return type non-nullable.
      throw new Error('Redirecting to login')
    }
    return user.access_token
  }, [])
}
