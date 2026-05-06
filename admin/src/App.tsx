import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import type { User } from 'oidc-client-ts'
import userManager from './auth'
import { api } from './api'
import { BASE_PATH } from './config'
import Header from './components/Header'
import ConfigPage from './pages/ConfigPage'
import GroupsListPage from './pages/GroupsListPage'
import GroupDashboard from './pages/GroupDashboard'
import MembersTab from './pages/MembersTab'
import InvitationsTab from './pages/InvitationsTab'
import InvitationResultPage from './pages/InvitationResultPage'
import NotFound from './pages/NotFound'

const HOME_PATH = `${BASE_PATH}/`
const CALLBACK_PATH = `${BASE_PATH}/callback`

export default function App() {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isRealmAdmin, setIsRealmAdmin] = useState(false)

  useEffect(() => {
    let cancelled = false
    const init = async () => {
      try {
        if (window.location.pathname === CALLBACK_PATH && window.location.search.includes('code=')) {
          const u = await userManager.signinRedirectCallback()
          if (cancelled) return
          setUser(u)
          const returnTo = sessionStorage.getItem('returnTo') || HOME_PATH
          sessionStorage.removeItem('returnTo')
          window.history.replaceState({}, '', returnTo)
          setLoading(false)
          return
        }

        const u = await userManager.getUser()
        if (cancelled) return
        if (u && !u.expired) {
          setUser(u)
          setLoading(false)
        } else {
          const loc = window.location.pathname + window.location.search
          if (loc !== HOME_PATH) sessionStorage.setItem('returnTo', loc)
          await userManager.signinRedirect()
        }
      } catch (err) {
        if (cancelled) return
        if (window.location.pathname === CALLBACK_PATH) {
          window.history.replaceState({}, '', HOME_PATH)
        }
        console.error('Admin SPA auth failure:', err)
        setError('Auth failed: ' + ((err as Error)?.message || String(err)))
        setLoading(false)
      }
    }
    init()

    userManager.events.addUserLoaded((u) => setUser(u))
    userManager.events.addUserUnloaded(() => setUser(null))

    return () => { cancelled = true }
  }, [])

  // Probe whether the current user is a realm admin so we can conditionally
  // show the "Configuration" link. The /api/config endpoint is realm-admin-
  // only; success = admin, 403 = not.
  useEffect(() => {
    if (!user) return
    let cancelled = false
    ;(async () => {
      try {
        const u = await userManager.getUser()
        if (!u || u.expired) return
        await api.getConfig(u.access_token)
        if (!cancelled) setIsRealmAdmin(true)
      } catch {
        if (!cancelled) setIsRealmAdmin(false)
      }
    })()
    return () => { cancelled = true }
  }, [user])

  if (error) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-16">
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{error}</div>
      </div>
    )
  }
  if (loading || !user) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-500">
        Authenticating…
      </div>
    )
  }

  return (
    <BrowserRouter basename={BASE_PATH || undefined}>
      <div className="min-h-screen">
        <Header user={user} isRealmAdmin={isRealmAdmin} />
        <main className="mx-auto max-w-5xl px-6 py-8">
          <Routes>
            <Route path="/" element={<GroupsListPage isRealmAdmin={isRealmAdmin} />} />
            <Route path="/groups/:groupId" element={<GroupDashboard />}>
              <Route index element={<Navigate to="members" replace />} />
              <Route path="members" element={<MembersTab />} />
              <Route path="invitations" element={<InvitationsTab />} />
            </Route>
            <Route path="/config" element={<ConfigPage />} />
            <Route path="/invitation-result" element={<InvitationResultPage />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  )
}
