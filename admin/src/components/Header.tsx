import { Link, NavLink, useLocation } from 'react-router-dom'
import type { User } from 'oidc-client-ts'
import userManager from '../auth'
import { REALM } from '../config'
import { btnNeutral } from '../lib/buttons'

interface HeaderProps {
  user: User
  isRealmAdmin: boolean
}

export default function Header({ user, isRealmAdmin }: HeaderProps) {
  const { pathname } = useLocation()
  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm font-medium ${isActive ? 'text-indigo-700' : 'text-slate-600 hover:text-indigo-600'}`
  const managementActive = pathname === '/' || pathname.startsWith('/groups')
  const managementClass = `text-sm font-medium ${managementActive ? 'text-indigo-700' : 'text-slate-600 hover:text-indigo-600'}`
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-5">
        <div className="flex items-baseline gap-6">
          <div>
            <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
              Group Management Plugin
            </div>
            <h1 className="mt-1 flex items-center gap-2">
              <span className="text-sm font-medium text-slate-500">Realm</span>
              <span className="inline-flex items-center gap-2 rounded-full bg-indigo-50 px-3 py-1 text-base font-semibold text-indigo-700 ring-1 ring-inset ring-indigo-200">
                <span className="h-2 w-2 rounded-full bg-indigo-500" aria-hidden="true" />
                <span className="font-mono tracking-tight">{REALM}</span>
              </span>
            </h1>
            {isRealmAdmin && (
              <nav className="flex items-center gap-4 self-end mt-6">
                <Link to="/" className={managementClass}>Management</Link>
                <NavLink to="/config" className={navLinkClass}>Configuration</NavLink>
              </nav>
            )}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600">{user.profile?.preferred_username}</span>
          <button onClick={() => userManager.signoutRedirect()} className={btnNeutral}>Logout</button>
        </div>
      </div>
    </header>
  )
}
