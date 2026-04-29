import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Link, NavLink, Navigate, Outlet, useOutletContext, useParams, useSearchParams, useLocation } from 'react-router-dom'
import Select from 'react-select'
import userManager from './auth.js'
import { api } from './api.js'
import { REALM, BASE_PATH } from './config.js'
import useToken from './hooks/useToken.js'
import ConfigPage from './pages/ConfigPage.jsx'

const HOME_PATH = `${BASE_PATH}/`
const CALLBACK_PATH = `${BASE_PATH}/callback`

// ---------- Reusable button class strings ----------
const btnBase =
  'inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium shadow-sm focus:outline-none focus:ring-2 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50'
const btnPrimary = `${btnBase} bg-indigo-600 text-white hover:bg-indigo-500 focus:ring-indigo-500`
const btnOutlinePrimary = `${btnBase} border border-indigo-600 bg-white text-indigo-700 hover:bg-indigo-50 focus:ring-indigo-500`
const btnNeutral = `${btnBase} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus:ring-indigo-500`
const btnDanger = `${btnBase} bg-red-600 text-white hover:bg-red-500 focus:ring-red-500`
const btnSuccess = `${btnBase} bg-emerald-600 text-white hover:bg-emerald-500 focus:ring-emerald-500`
const btnWarning = `${btnBase} bg-amber-500 text-white hover:bg-amber-400 focus:ring-amber-500`

const iconBtnBase =
  'inline-flex h-8 w-8 items-center justify-center rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50'
const iconBtnPrimary = `${iconBtnBase} bg-indigo-600 text-white hover:bg-indigo-500 focus:ring-indigo-500`
const iconBtnNeutral = `${iconBtnBase} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus:ring-indigo-500`
const iconBtnDanger = `${iconBtnBase} bg-red-600 text-white hover:bg-red-500 focus:ring-red-500`
const iconBtnWarning = `${iconBtnBase} bg-amber-500 text-white hover:bg-amber-400 focus:ring-amber-500`

const iconProps = { width: 16, height: 16, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }
const IconPencil = () => <svg {...iconProps}><path d="M12 20h9" /><path d="M16.5 3.5a2.121 2.121 0 113 3L7 19l-4 1 1-4 12.5-12.5z" /></svg>
const IconTrash = () => <svg {...iconProps}><path d="M3 6h18" /><path d="M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2" /><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" /><path d="M10 11v6" /><path d="M14 11v6" /></svg>
const IconResend = () => <svg {...iconProps}><path d="M22 2L11 13" /><path d="M22 2l-7 20-4-9-9-4 20-7z" /></svg>
const IconArrowRight = () => <svg {...iconProps}><path d="M5 12h14" /><path d="M12 5l7 7-7 7" /></svg>
const IconPlus = () => <svg {...iconProps}><path d="M12 5v14" /><path d="M5 12h14" /></svg>

const inputBase =
  'block w-full rounded-md border-slate-300 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500'

// ---------- react-select integration ----------
function rolesToOptions(roles) {
  return (roles || []).map((r) => ({ value: r, label: r }))
}

const SELECT_CLASS_NAMES = {
  control: () => 'rs__control',
  multiValue: () => 'rs__multi-value',
  multiValueLabel: () => 'rs__multi-value__label',
  multiValueRemove: () => 'rs__multi-value__remove',
  option: ({ isFocused, isSelected }) =>
    `${isFocused ? 'rs__option--is-focused' : ''} ${isSelected ? 'rs__option--is-selected' : ''}`,
}

const SELECT_PORTAL_TARGET = typeof document !== 'undefined' ? document.body : null

const SELECT_STYLES = {
  menu: (base) => ({ ...base, zIndex: 9999 }),
  menuPortal: (base) => ({ ...base, zIndex: 9999 }),
}

// ---------- App ----------

export default function App() {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
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
        setError('Auth failed: ' + (err?.message || String(err)))
        setLoading(false)
      }
    }
    init()

    userManager.events.addUserLoaded((u) => setUser(u))
    userManager.events.addUserUnloaded(() => setUser(null))

    return () => { cancelled = true }
  }, [])

  // Probe whether the current user is a realm admin so we can conditionally
  // show the "Plugin Configuration" link. The /api/config endpoint is
  // realm-admin-only; success = admin, 403 = not.
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
            <Route path="/" element={<Home />} />
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

function NotFound() {
  return (
    <div className="rounded-md border border-slate-200 bg-white p-6 text-sm text-slate-700 shadow-sm">
      Page not found.{' '}
      <Link to="/" className="text-indigo-600 hover:underline">Go home</Link>.
    </div>
  )
}

function Header({ user, isRealmAdmin }) {
  const { pathname } = useLocation()
  const navLinkClass = ({ isActive }) =>
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

// ---------- Home (group list) ----------

function Home() {
  const getToken = useToken()
  const [groups, setGroups] = useState(null)
  const [meta, setMeta] = useState(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [sortBy, setSortBy] = useState('name')
  const [sortDir, setSortDir] = useState('asc')
  const [error, setError] = useState(null)

  const loadGroups = async (opts = {}) => {
    const p = opts.page ?? page
    const s = opts.search ?? search
    const sb = opts.sortBy ?? sortBy
    const sd = opts.sortDir ?? sortDir
    setError(null)
    try {
      const token = await getToken()
      const data = await api.getMyGroups(token, { page: p, search: s || undefined, sortBy: sb, sortDir: sd })
      setGroups(data.data)
      setMeta(data.meta)
    } catch (e) { setError(e.message) }
  }

  useEffect(() => { loadGroups() }, [page, search, sortBy, sortDir])

  const handleSearch = () => { setPage(1); setSearch(searchInput) }
  const handleClearSearch = () => { setSearchInput(''); setPage(1); setSearch('') }
  const handleSort = (field) => {
    if (sortBy === field) setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    else { setSortBy(field); setSortDir('asc') }
    setPage(1)
  }

  return (
    <Section title="My Groups">
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="text"
          className={`${inputBase} flex-1`}
          placeholder="Search groups…"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
        />
        <button onClick={handleSearch} className={btnPrimary}>Search</button>
        {search && <button onClick={handleClearSearch} className={btnNeutral}>Clear</button>}
      </div>

      {error && <ErrorMessage>{error}</ErrorMessage>}
      {groups === null && !error && <p className="mt-4 text-sm text-slate-500">Loading groups…</p>}
      {groups && groups.length === 0 && <p className="mt-4 text-sm text-slate-500">No groups found.</p>}

      {groups && groups.length > 0 && (
        <div className="mt-4 overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
              <tr>
                <ThSortable label="Name" field="name" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <th className="px-4 py-3">Path</th>
                <th className="px-4 py-3">Roles</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {groups.map((g) => (
                <tr key={g.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">{g.name}</td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-600">{g.path}</td>
                  <td className="px-4 py-3">
                    {g.roles && g.roles.length
                      ? <RoleChips roles={g.roles} />
                      : <span className="text-xs text-slate-400">member</span>}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/groups/${g.id}`} className={iconBtnPrimary} title="Manage" aria-label="Manage group"><IconArrowRight /></Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {meta && <Pagination meta={meta} page={page} setPage={setPage} className="px-4 py-3" />}
        </div>
      )}
    </Section>
  )
}

// ---------- Group dashboard ----------

function GroupDashboard() {
  const { groupId } = useParams()
  const getToken = useToken()
  const [vocabulary, setVocabulary] = useState({ roles: ['admin', 'member'] })
  const [group, setGroup] = useState(null)
  const [editingName, setEditingName] = useState(false)
  const [nameInput, setNameInput] = useState('')
  const [nameSaving, setNameSaving] = useState(false)
  const [nameError, setNameError] = useState(null)
  const [invitations, setInvitations] = useState(null)
  const [invitationError, setInvitationError] = useState(null)
  const [invitationResult, setInvitationResult] = useState(null)
  const [isCreatingInvitation, setIsCreatingInvitation] = useState(false)

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      try {
        const token = await getToken()
        const [g, rolesData] = await Promise.all([
          api.getGroup(groupId, token).catch((e) => { setNameError(e.message); return null }),
          api.getRoles(token).catch(() => null),
        ])
        if (cancelled) return
        if (g) { setGroup(g); setNameInput(g.name) }
        if (rolesData?.roles?.length) setVocabulary({ roles: rolesData.roles })
      } catch {
        // already surfaced above
      }
    }
    load()
    return () => { cancelled = true }
  }, [groupId, getToken])

  const loadInvitations = async () => {
    try {
      const token = await getToken()
      const data = await api.listInvitations(groupId, token)
      setInvitations(data.data)
    } catch (e) { setInvitationError(e.message) }
  }
  useEffect(() => { setInvitations(null); loadInvitations() }, [groupId])

  const openInviteModal = () => {
    setInvitationError(null)
    setInvitationResult(null)
    setIsCreatingInvitation(true)
  }
  const handleCreateInvitation = async ({ email, roles, ttl }) => {
    setInvitationError(null); setInvitationResult(null)
    const token = await getToken()
    const data = await api.createInvitation(groupId, email, roles, ttl, token)
    setInvitationResult(data)
    setIsCreatingInvitation(false)
    loadInvitations()
  }

  const startEditName = () => { setNameError(null); setNameInput(group?.name || ''); setEditingName(true) }
  const cancelEditName = () => { setNameError(null); setNameInput(group?.name || ''); setEditingName(false) }
  const saveName = async () => {
    setNameError(null); setNameSaving(true)
    try {
      const token = await getToken()
      const updated = await api.updateGroup(groupId, { name: nameInput.trim() }, token)
      setGroup(updated); setNameInput(updated.name); setEditingName(false)
    } catch (e) { setNameError(e.message) }
    setNameSaving(false)
  }

  const tabLinkClass = ({ isActive }) =>
    `inline-flex items-center border-b-2 px-1 pb-3 text-sm font-medium transition ${
      isActive
        ? 'border-indigo-500 text-indigo-700'
        : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
    }`

  return (
    <div className="space-y-6">
      {/* Breadcrumb */}
      <nav className="flex items-center gap-1 text-sm text-slate-500">
        <Link to="/" className="hover:text-indigo-600">All groups</Link>
        <span aria-hidden className="text-slate-300">/</span>
        <span className="font-medium text-slate-900">{group?.name || '…'}</span>
      </nav>

      {/* Page header */}
      <div>
        {editingName ? (
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="text"
              className={`${inputBase} flex-1 text-2xl font-semibold tracking-tight`}
              value={nameInput}
              onChange={(e) => setNameInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') saveName()
                else if (e.key === 'Escape') cancelEditName()
              }}
              autoFocus
            />
            <button
              onClick={saveName}
              disabled={nameSaving || !nameInput.trim() || nameInput.trim() === group?.name}
              className={btnSuccess}
            >
              {nameSaving ? 'Saving…' : 'Save'}
            </button>
            <button onClick={cancelEditName} disabled={nameSaving} className={btnNeutral}>Cancel</button>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <h2 className="text-2xl font-semibold tracking-tight text-slate-900">
              {group?.name || '…'}
            </h2>
            <button
              onClick={startEditName}
              disabled={!group}
              title="Rename group"
              aria-label="Rename group"
              className="inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-indigo-600 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <IconPencil />
            </button>
          </div>
        )}
        {nameError && <p className="mt-2 text-sm text-red-600">{nameError}</p>}
      </div>

      {/* Tabs */}
      <div className="border-b border-slate-200">
        <nav className="-mb-px flex gap-6">
          <NavLink to="members" className={tabLinkClass}>Members</NavLink>
          <NavLink to="invitations" className={tabLinkClass}>
            Invitations
            {invitations && invitations.length > 0 && (
              <span className="ml-2 inline-flex items-center justify-center rounded-full bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-700">
                {invitations.length}
              </span>
            )}
          </NavLink>
        </nav>
      </div>

      {/* Active tab content */}
      <Outlet context={{
        groupId,
        vocabulary,
        invitations,
        invitationError,
        invitationResult,
        setInvitationError,
        setInvitationResult,
        reloadInvitations: loadInvitations,
        openInviteModal,
      }} />

      <CreateInvitationModal
        open={isCreatingInvitation}
        vocabulary={vocabulary}
        onCancel={() => setIsCreatingInvitation(false)}
        onCreate={handleCreateInvitation}
      />
    </div>
  )
}

// Tab wrappers — pull shared state from the GroupDashboard outlet context.
function MembersTab() {
  const { groupId, vocabulary, openInviteModal } = useOutletContext()
  return <MemberSection groupId={groupId} vocabulary={vocabulary} openInviteModal={openInviteModal} />
}

function InvitationsTab() {
  const ctx = useOutletContext()
  return <InvitationSection {...ctx} />
}

// ---------- Reusable role multiselect ----------

function RoleMultiSelect({ vocabulary, value, onChange, ariaLabel, minWidth = 220 }) {
  const options = rolesToOptions(vocabulary.roles)
  const selected = options.filter((o) => value.has(o.value))
  return (
    <Select
      isMulti
      isClearable={false}
      classNamePrefix="rs"
      classNames={SELECT_CLASS_NAMES}
      menuPortalTarget={SELECT_PORTAL_TARGET}
      aria-label={ariaLabel}
      options={options}
      value={selected}
      onChange={(opts) => onChange(new Set((opts || []).map((o) => o.value)))}
      placeholder="Select roles…"
      styles={{ ...SELECT_STYLES, container: (base) => ({ ...base, minWidth, flex: 1 }) }}
    />
  )
}

// ---------- Invitations ----------

function InvitationSection({ groupId, invitations, invitationError, invitationResult, setInvitationError, setInvitationResult, reloadInvitations, openInviteModal }) {
  const getToken = useToken()
  const error = invitationError
  const result = invitationResult

  const clear = () => { setInvitationError(null); setInvitationResult(null) }

  const handleResend = async (invId) => {
    clear()
    try {
      const token = await getToken()
      await api.resendInvitation(groupId, invId, token)
      setInvitationResult({ message: 'Invitation resent successfully' })
    } catch (e) { setInvitationError(e.message) }
  }

  const handleDelete = async (invId) => {
    clear()
    try {
      const token = await getToken()
      await api.deleteInvitation(groupId, invId, token)
      reloadInvitations()
    } catch (e) { setInvitationError(e.message) }
  }

  return (
    <Section
      title="Invitations"
      actions={
        <button onClick={openInviteModal} className={`${btnOutlinePrimary} gap-1.5`}>
          <IconPlus />
          Create Invitation
        </button>
      }
    >
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {result?.message && (
        <p className="mt-3 rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {result.message}
        </p>
      )}

      {invitations && (
        <div className="mt-4 overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3">Email</th>
                <th className="px-4 py-3">Roles</th>
                <th className="px-4 py-3">Expires</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {invitations.map((inv) => (
                <tr key={inv.id}>
                  <td className="break-all px-4 py-3 text-slate-700">
                    {inv.email || <span className="text-xs text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-3">
                    {inv.roles && inv.roles.length
                      ? <RoleChips roles={inv.roles} />
                      : <span className="text-xs text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-3 text-xs text-slate-600">{inv.expiresAt}</td>
                  <td className="px-4 py-3 text-right">
                    <div className="inline-flex gap-2">
                      <button onClick={() => handleResend(inv.id)} className={iconBtnNeutral} title="Resend invitation" aria-label="Resend invitation"><IconResend /></button>
                      <button onClick={() => handleDelete(inv.id)} className={iconBtnDanger} title="Delete invitation" aria-label="Delete invitation"><IconTrash /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {invitations.length === 0 && (
                <tr>
                  <td colSpan={4} className="px-4 py-6 text-center text-sm text-slate-500">No invitations found</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}

// ---------- Members ----------

function MemberSection({ groupId, vocabulary, openInviteModal }) {
  const getToken = useToken()
  const [members, setMembers] = useState(null)
  const [meta, setMeta] = useState(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [sortBy, setSortBy] = useState('username')
  const [sortDir, setSortDir] = useState('asc')
  const [editingMemberId, setEditingMemberId] = useState(null)
  const [editingRoles, setEditingRoles] = useState(() => new Set())
  const [error, setError] = useState(null)

  const loadMembers = async (opts = {}) => {
    const p = opts.page ?? page
    const s = opts.search ?? search
    const sb = opts.sortBy ?? sortBy
    const sd = opts.sortDir ?? sortDir
    const r = opts.role ?? roleFilter
    setError(null)
    try {
      const token = await getToken()
      const data = await api.listMembers(groupId, token, {
        page: p, search: s || undefined, sortBy: sb, sortDir: sd, role: r || undefined,
      })
      setMembers(data.data)
      setMeta(data.meta)
    } catch (e) { setError(e.message) }
  }

  useEffect(() => {
    setPage(1); setSearch(''); setSearchInput(''); setRoleFilter('')
    loadMembers({ page: 1, search: '', role: '' })
  }, [groupId])
  useEffect(() => { loadMembers() }, [page, search, sortBy, sortDir, roleFilter])

  const handleSearch = () => { setPage(1); setSearch(searchInput) }
  const handleClearSearch = () => { setSearchInput(''); setPage(1); setSearch('') }
  const handleSort = (field) => {
    if (sortBy === field) setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    else { setSortBy(field); setSortDir('asc') }
    setPage(1)
  }

  const handleRemove = async (userId) => {
    setError(null)
    try {
      const token = await getToken()
      await api.removeMember(groupId, userId, token)
      loadMembers()
    } catch (e) { setError(e.message) }
  }

  const startEditRoles = (member) => {
    setEditingMemberId(member.id)
    setEditingRoles(new Set(member.roles || []))
  }

  const saveEditRoles = async (memberId) => {
    setError(null)
    try {
      const token = await getToken()
      await api.setMemberRoles(groupId, memberId, Array.from(editingRoles), token)
      setEditingMemberId(null)
      setEditingRoles(new Set())
      loadMembers()
    } catch (e) { setError(e.message) }
  }

  const cancelEditRoles = () => { setEditingMemberId(null); setEditingRoles(new Set()) }

  return (
    <Section
      title="Members"
      actions={
        <button onClick={openInviteModal} className={`${btnOutlinePrimary} gap-1.5`}>
          <IconPlus />
          Invite
        </button>
      }
    >
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="text"
          className={`${inputBase} flex-1`}
          placeholder="Search members…"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
        />
        <button onClick={handleSearch} className={btnPrimary}>Search</button>
        {search && <button onClick={handleClearSearch} className={btnNeutral}>Clear</button>}
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-2">
        <span className="text-sm text-slate-600">Filter by role:</span>
        <div className="min-w-[200px]">
          <Select
            isClearable
            classNamePrefix="rs"
            classNames={SELECT_CLASS_NAMES}
            menuPortalTarget={SELECT_PORTAL_TARGET}
            options={rolesToOptions(vocabulary.roles)}
            value={roleFilter ? { value: roleFilter, label: roleFilter } : null}
            onChange={(opt) => { setPage(1); setRoleFilter(opt?.value || '') }}
            placeholder="(any)"
            styles={SELECT_STYLES}
          />
        </div>
      </div>

      {error && <ErrorMessage>{error}</ErrorMessage>}

      {members && (
        <div className="mt-4 overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
              <tr>
                <ThSortable label="Username" field="username" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <ThSortable label="Email" field="email" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <ThSortable label="Name" field="name" sortBy={sortBy} sortDir={sortDir} onSort={handleSort} />
                <th className="px-4 py-3">Roles</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {members.map((m) => (
                <tr key={m.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">{m.username}</td>
                  <td className="break-all px-4 py-3 text-slate-700">
                    {m.email || <span className="text-xs text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-3 text-slate-700">{[m.firstName, m.lastName].filter(Boolean).join(' ')}</td>
                  <td className="px-4 py-3">
                    {m.roles && m.roles.length ? (
                      <RoleChips roles={m.roles} />
                    ) : (
                      <span className="text-xs text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="inline-flex gap-2">
                      <button onClick={() => startEditRoles(m)} className={iconBtnWarning} title="Edit roles" aria-label="Edit roles"><IconPencil /></button>
                      <button onClick={() => handleRemove(m.id)} className={iconBtnDanger} title="Remove member" aria-label="Remove member"><IconTrash /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {members.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-sm text-slate-500">No members found</td>
                </tr>
              )}
            </tbody>
          </table>
          {meta && <Pagination meta={meta} page={page} setPage={setPage} className="px-4 py-3" />}
        </div>
      )}

      <EditRolesModal
        member={members?.find((m) => m.id === editingMemberId) || null}
        vocabulary={vocabulary}
        editingRoles={editingRoles}
        setEditingRoles={setEditingRoles}
        onCancel={cancelEditRoles}
        onSave={() => saveEditRoles(editingMemberId)}
      />
    </Section>
  )
}

function EditRolesModal({ member, vocabulary, editingRoles, setEditingRoles, onCancel, onSave }) {
  // Esc closes the modal.
  useEffect(() => {
    if (!member) return
    const onKey = (e) => { if (e.key === 'Escape') onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [member, onCancel])

  if (!member) return null
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="edit-roles-title"
    >
      <div className="absolute inset-0 bg-slate-900/40" onClick={onCancel} />
      <div className="relative w-full max-w-lg overflow-hidden rounded-lg bg-white shadow-xl">
        <header className="border-b border-slate-200 px-5 py-3">
          <h3 id="edit-roles-title" className="text-sm font-semibold text-slate-900">
            Edit roles for <span className="text-indigo-700">{member.username}</span>
          </h3>
        </header>
        <div className="px-5 py-4">
          <label className="mb-1 block text-xs font-medium text-slate-600">Roles</label>
          <RoleMultiSelect
            vocabulary={vocabulary}
            value={editingRoles}
            onChange={setEditingRoles}
            ariaLabel={`Roles for ${member.username}`}
            minWidth={0}
          />
          <p className="mt-2 text-xs text-slate-500">
            Empty = plain member of the group. Removing the last <code className="rounded bg-slate-100 px-1">admin</code> is blocked unless you are a realm admin.
          </p>
        </div>
        <footer className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3">
          <button onClick={onCancel} className={btnNeutral}>Cancel</button>
          <button onClick={onSave} className={btnSuccess}>Save</button>
        </footer>
      </div>
    </div>
  )
}

function CreateInvitationModal({ open, vocabulary, onCancel, onCreate }) {
  const [email, setEmail] = useState('')
  const [selectedRoles, setSelectedRoles] = useState(() => new Set())
  const [ttl, setTtl] = useState('72')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (open) {
      setEmail('')
      setSelectedRoles(new Set())
      setTtl('72')
      setError(null)
      setSubmitting(false)
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (e) => { if (e.key === 'Escape') onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  if (!open) return null

  const handleSubmit = async () => {
    setError(null)
    setSubmitting(true)
    try {
      await onCreate({ email, roles: Array.from(selectedRoles), ttl: ttl ? Number(ttl) : null })
    } catch (e) {
      setError(e.message)
      setSubmitting(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="create-invitation-title"
    >
      <div className="absolute inset-0 bg-slate-900/40" onClick={onCancel} />
      <div className="relative w-full max-w-lg overflow-hidden rounded-lg bg-white shadow-xl">
        <header className="border-b border-slate-200 px-5 py-3">
          <h3 id="create-invitation-title" className="text-sm font-semibold text-slate-900">Create invitation</h3>
        </header>
        <div className="space-y-4 px-5 py-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-[1fr_8rem]">
            <div>
              <label className="block text-sm font-medium text-slate-800">Email</label>
              <input
                type="email"
                className={`${inputBase} mt-1`}
                placeholder="user@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoFocus
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-800">TTL (hours)</label>
              <input
                type="number"
                min="1"
                className={`${inputBase} mt-1`}
                placeholder="72"
                value={ttl}
                onChange={(e) => setTtl(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-800">Roles to grant</label>
            <div className="mt-1">
              <RoleMultiSelect
                vocabulary={vocabulary}
                value={selectedRoles}
                onChange={setSelectedRoles}
                ariaLabel="Roles to grant"
                minWidth={0}
              />
            </div>
          </div>
          {error && <ErrorMessage>{error}</ErrorMessage>}
        </div>
        <footer className="flex justify-end gap-2 border-t border-slate-200 bg-slate-50 px-5 py-3">
          <button onClick={onCancel} className={btnNeutral} disabled={submitting}>Cancel</button>
          <button onClick={handleSubmit} className={btnPrimary} disabled={submitting || !email}>
            {submitting ? 'Creating…' : 'Create'}
          </button>
        </footer>
      </div>
    </div>
  )
}

// ---------- Invitation result page ----------

function InvitationResultPage() {
  const [searchParams] = useSearchParams()
  const result = searchParams.get('result')
  const isSuccess = result === 'success'
  const groupName = searchParams.get('group_name')
  const groupId = searchParams.get('group_id')
  const error = searchParams.get('error')
  const errorDescription = searchParams.get('error_description')

  if (isSuccess) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-emerald-800">Invitation Accepted</h2>
        <p className="mt-2 text-sm text-emerald-900">
          You have successfully joined <strong>{groupName || 'the group'}</strong>.
        </p>
        <div className="mt-3 flex gap-3 text-sm">
          {groupId && (
            <Link to={`/groups/${groupId}`} className="text-indigo-700 hover:underline">
              Manage this group
            </Link>
          )}
          <Link to="/" className="text-indigo-700 hover:underline">Go to dashboard</Link>
        </div>
      </div>
    )
  }
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-red-800">
        {error ? error.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()) : 'Error'}
      </h2>
      <p className="mt-2 text-sm text-red-900">
        {errorDescription || 'Something went wrong with your invitation.'}
      </p>
      <p className="mt-3 text-sm">
        <Link to="/" className="text-indigo-700 hover:underline">Go to dashboard</Link>
      </p>
    </div>
  )
}

// ---------- Small reusable building blocks ----------

function Section({ title, actions, children }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-slate-900">{title}</h2>
        {actions}
      </div>
      {children}
    </section>
  )
}

function ErrorMessage({ children }) {
  return <p className="mt-3 text-sm text-red-600">{children}</p>
}


function RoleChips({ roles }) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((r) => (
        <span
          key={r}
          className="inline-flex items-center rounded-md bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700 ring-1 ring-inset ring-indigo-200"
        >
          {r}
        </span>
      ))}
    </div>
  )
}

function ThSortable({ label, field, sortBy, sortDir, onSort }) {
  const arrow = sortBy === field ? (sortDir === 'asc' ? '▲' : '▼') : ''
  return (
    <th
      onClick={() => onSort(field)}
      className="cursor-pointer select-none px-4 py-3 hover:bg-slate-100"
    >
      {label} <span className="text-[10px] text-slate-400">{arrow}</span>
    </th>
  )
}

function Pagination({ meta, page, setPage, className = '' }) {
  if (!meta) return null
  const showing =
    meta.totalCount === 0
      ? 'No results'
      : `Showing ${(meta.page - 1) * meta.pageSize + 1}–${Math.min(meta.page * meta.pageSize, meta.totalCount)} of ${meta.totalCount}`
  return (
    <div className={`flex items-center justify-between border-t border-slate-200 bg-slate-50 text-sm ${className}`}>
      <span className="text-xs text-slate-500">{showing}</span>
      {meta.totalPages > 1 && (
        <div className="inline-flex gap-1">
          <button
            onClick={() => page > 1 && setPage(page - 1)}
            disabled={page <= 1}
            className={`${btnNeutral} px-2 py-1 text-xs`}
          >
            Previous
          </button>
          {Array.from({ length: meta.totalPages }, (_, i) => i + 1).map((p) => (
            <button
              key={p}
              onClick={() => setPage(p)}
              className={
                p === page
                  ? `${btnPrimary} px-2 py-1 text-xs`
                  : `${btnNeutral} px-2 py-1 text-xs`
              }
            >
              {p}
            </button>
          ))}
          <button
            onClick={() => page < meta.totalPages && setPage(page + 1)}
            disabled={page >= meta.totalPages}
            className={`${btnNeutral} px-2 py-1 text-xs`}
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
