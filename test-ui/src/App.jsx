import { useState, useEffect, useCallback } from 'react'
import { BrowserRouter, Routes, Route, Link, useParams, useSearchParams, useNavigate } from 'react-router-dom'
import userManager from './auth.js'
import { api } from './api.js'
import { REALM } from './config.js'

const styles = {
  app: { fontFamily: 'system-ui, sans-serif', maxWidth: 960, margin: '0 auto', padding: 20 },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '2px solid #333', paddingBottom: 10, marginBottom: 20 },
  groupBar: { display: 'flex', gap: 8, alignItems: 'center', marginBottom: 20, padding: 12, backgroundColor: '#f0f4f8', borderRadius: 8 },
  section: { marginBottom: 30, padding: 16, border: '1px solid #ddd', borderRadius: 8 },
  sectionTitle: { marginTop: 0 },
  row: { display: 'flex', gap: 8, marginBottom: 8, flexWrap: 'wrap', alignItems: 'center' },
  input: { padding: '6px 10px', border: '1px solid #ccc', borderRadius: 4, fontSize: 14 },
  btn: { padding: '6px 14px', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 14, color: '#fff', backgroundColor: '#4a90d9' },
  btnDanger: { backgroundColor: '#d9534f' },
  btnSuccess: { backgroundColor: '#5cb85c' },
  btnWarning: { backgroundColor: '#f0ad4e' },
  table: { width: '100%', borderCollapse: 'collapse', marginTop: 10, fontSize: 14 },
  th: { textAlign: 'left', padding: '8px 6px', borderBottom: '2px solid #ddd', backgroundColor: '#f5f5f5' },
  td: { padding: '6px', borderBottom: '1px solid #eee' },
  error: { color: '#d9534f', marginTop: 8 },
  success: { color: '#5cb85c', marginTop: 8 },
  pre: { backgroundColor: '#f5f5f5', padding: 10, borderRadius: 4, overflow: 'auto', fontSize: 13 },
  label: { fontWeight: 'bold', fontSize: 14 },
}

export default function App() {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    const init = async () => {
      try {
        if (window.location.pathname === '/callback' && window.location.search.includes('code=')) {
          const u = await userManager.signinRedirectCallback()
          if (cancelled) return
          setUser(u)
          const returnTo = sessionStorage.getItem('returnTo') || '/'
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
          if (loc !== '/') sessionStorage.setItem('returnTo', loc)
          await userManager.signinRedirect()
        }
      } catch (err) {
        if (cancelled) return
        if (window.location.pathname === '/callback') {
          window.history.replaceState({}, '', '/')
          await userManager.signinRedirect()
          return
        }
        setError('Auth failed: ' + err.message)
        setLoading(false)
      }
    }
    init()

    userManager.events.addUserLoaded(u => setUser(u))
    userManager.events.addUserUnloaded(() => setUser(null))

    return () => { cancelled = true }
  }, [])

  if (error) return <div style={styles.app}><p style={styles.error}>{error}</p></div>
  if (loading || !user) return <div style={styles.app}><p>Authenticating...</p></div>

  return (
    <BrowserRouter>
      <div style={styles.app}>
        <Header user={user} />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/groups/:groupId" element={<GroupDashboard />} />
          <Route path="/accept" element={<AcceptSection />} />
          <Route path="/invitation-result" element={<InvitationResultPage />} />
          <Route path="/admin/config" element={<ConfigPage />} />
          <Route path="/debug" element={<TokenViewer />} />
          <Route path="*" element={<p>Page not found. <Link to="/">Go home</Link></p>} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

function Header({ user }) {
  return (
    <header style={styles.header}>
      <h1 style={{ margin: 0 }}><a href="/" style={{ color: 'inherit', textDecoration: 'none' }}>Group Management</a></h1>
      <div>
        <span style={{ marginRight: 12 }}>{user.profile?.preferred_username}</span>
        <button style={styles.btn} onClick={() => userManager.signoutRedirect()}>Logout</button>
      </div>
    </header>
  )
}

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

  const handleSearch = () => {
    setPage(1)
    setSearch(searchInput)
  }

  const handleClearSearch = () => {
    setSearchInput('')
    setPage(1)
    setSearch('')
  }

  const handleSort = (field) => {
    if (sortBy === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortBy(field)
      setSortDir('asc')
    }
    setPage(1)
  }

  const sortIndicator = (field) => {
    if (sortBy !== field) return ''
    return sortDir === 'asc' ? ' \u25B2' : ' \u25BC'
  }

  const thSortable = { ...styles.th, cursor: 'pointer', userSelect: 'none' }

  return (
    <>
      <div style={styles.section}>
        <h2 style={styles.sectionTitle}>My Groups</h2>
        <div style={styles.row}>
          <input
            style={{ ...styles.input, flex: 1 }}
            placeholder="Search groups..."
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
          />
          <button style={styles.btn} onClick={handleSearch}>Search</button>
          {search && <button style={{ ...styles.btn, backgroundColor: '#888' }} onClick={handleClearSearch}>Clear</button>}
        </div>
        {error && <p style={styles.error}>{error}</p>}
        {groups === null && !error && <p style={{ color: '#666' }}>Loading groups...</p>}
        {groups && groups.length === 0 && <p style={{ color: '#666' }}>No groups found.</p>}
        {groups && groups.length > 0 && (
          <>
            <table style={styles.table}>
              <thead>
                <tr>
                  <th style={thSortable} onClick={() => handleSort('name')}>Name{sortIndicator('name')}</th>
                  <th style={styles.th}>Path</th>
                  <th style={styles.th}>Role</th>
                  <th style={styles.th}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {groups.map(g => (
                  <tr key={g.id}>
                    <td style={styles.td}><strong>{g.name}</strong></td>
                    <td style={styles.td}><code style={{ fontSize: 12 }}>{g.path}</code></td>
                    <td style={styles.td}>{g.isGroupAdmin ? 'Admin' : 'Member'}</td>
                    <td style={styles.td}>
                      <Link to={`/groups/${g.id}`}>
                        <button style={styles.btn}>Manage</button>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {meta && meta.totalPages > 1 && (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12, fontSize: 14 }}>
                <span style={{ color: '#666' }}>
                  Showing {(meta.page - 1) * meta.pageSize + 1}–{Math.min(meta.page * meta.pageSize, meta.totalCount)} of {meta.totalCount}
                </span>
                <div style={{ display: 'flex', gap: 4 }}>
                  <button
                    style={{ ...styles.btn, ...(page <= 1 ? { opacity: 0.5, cursor: 'default' } : {}) }}
                    onClick={() => page > 1 && setPage(page - 1)}
                    disabled={page <= 1}
                  >
                    Previous
                  </button>
                  {Array.from({ length: meta.totalPages }, (_, i) => i + 1).map(p => (
                    <button
                      key={p}
                      style={{ ...styles.btn, ...(p === page ? { backgroundColor: '#333' } : { backgroundColor: '#aaa' }) }}
                      onClick={() => setPage(p)}
                    >
                      {p}
                    </button>
                  ))}
                  <button
                    style={{ ...styles.btn, ...(page >= meta.totalPages ? { opacity: 0.5, cursor: 'default' } : {}) }}
                    onClick={() => page < meta.totalPages && setPage(page + 1)}
                    disabled={page >= meta.totalPages}
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
      <div style={styles.section}>
        <h2 style={styles.sectionTitle}>Quick Links</h2>
        <ul style={{ margin: 0, paddingLeft: 20 }}>
          <li><Link to="/accept">Accept an invitation (by token)</Link></li>
          <li><Link to="/admin/config">Plugin configuration</Link></li>
          <li><Link to="/debug">View access token</Link></li>
        </ul>
      </div>
    </>
  )
}

function GroupDashboard() {
  const { groupId } = useParams()

  return (
    <>
      <div style={styles.groupBar}>
        <span style={styles.label}>Group:</span>
        <code style={{ flex: 1, fontSize: 14 }}>{groupId}</code>
        <Link to="/"><button style={{ ...styles.btn, backgroundColor: '#888' }}>Change Group</button></Link>
      </div>
      <InvitationSection groupId={groupId} />
      <MemberSection groupId={groupId} />
    </>
  )
}

function useToken() {
  return useCallback(async () => {
    const user = await userManager.getUser()
    if (!user || user.expired) {
      await userManager.signinRedirect()
      return null
    }
    return user.access_token
  }, [])
}

function InvitationSection({ groupId }) {
  const getToken = useToken()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState('member')
  const [ttl, setTtl] = useState('72')
  const [invitations, setInvitations] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const clear = () => { setError(null); setResult(null) }

  const loadInvitations = async () => {
    try {
      const token = await getToken()
      const data = await api.listInvitations(groupId, token)
      setInvitations(data.data)
    } catch (e) { setError(e.message) }
  }

  useEffect(() => { loadInvitations() }, [groupId])

  const handleCreate = async () => {
    clear()
    try {
      const token = await getToken()
      const data = await api.createInvitation(groupId, email, role, ttl ? Number(ttl) : null, token)
      setResult(data)
      setEmail('')
      loadInvitations()
    } catch (e) { setError(e.message) }
  }

  const handleResend = async (invId) => {
    clear()
    try {
      const token = await getToken()
      await api.resendInvitation(groupId, invId, token)
      setResult({ message: 'Invitation resent successfully' })
    } catch (e) { setError(e.message) }
  }

  const handleDelete = async (invId) => {
    clear()
    try {
      const token = await getToken()
      await api.deleteInvitation(groupId, invId, token)
      loadInvitations()
    } catch (e) { setError(e.message) }
  }

  return (
    <div style={styles.section}>
      <h2 style={styles.sectionTitle}>Invitations</h2>
      <div style={styles.row}>
        <input style={styles.input} placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} />
        <select style={styles.input} value={role} onChange={e => setRole(e.target.value)}>
          <option value="member">Member</option>
          <option value="admin">Admin</option>
        </select>
        <input style={{ ...styles.input, width: 80 }} placeholder="TTL (hrs)" value={ttl} onChange={e => setTtl(e.target.value)} />
        <button style={styles.btn} onClick={handleCreate}>Create Invitation</button>
      </div>
      {error && <p style={styles.error}>{error}</p>}
      {result && <pre style={styles.pre}>{JSON.stringify(result, null, 2)}</pre>}
      {invitations && (
        <table style={styles.table}>
          <thead>
            <tr>
              <th style={styles.th}>ID</th>
              <th style={styles.th}>Email</th>
              <th style={styles.th}>Role</th>
              <th style={styles.th}>Expires</th>
              <th style={styles.th}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {invitations.map(inv => (
              <tr key={inv.id}>
                <td style={styles.td}><code>{inv.id.slice(0, 8)}...</code></td>
                <td style={styles.td}>{inv.email}</td>
                <td style={styles.td}>{inv.role}</td>
                <td style={styles.td}>{inv.expiresAt}</td>
                <td style={styles.td}>
                  <div style={{ display: 'flex', gap: 4 }}>
                    <button style={styles.btn} onClick={() => handleResend(inv.id)}>Resend</button>
                    <button style={{ ...styles.btn, ...styles.btnDanger }} onClick={() => handleDelete(inv.id)}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
            {invitations.length === 0 && (
              <tr><td style={styles.td} colSpan={5}>No invitations found</td></tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}

function MemberSection({ groupId }) {
  const getToken = useToken()
  const [members, setMembers] = useState(null)
  const [meta, setMeta] = useState(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [sortBy, setSortBy] = useState('username')
  const [sortDir, setSortDir] = useState('asc')
  const [error, setError] = useState(null)

  const loadMembers = async (opts = {}) => {
    const p = opts.page ?? page
    const s = opts.search ?? search
    const sb = opts.sortBy ?? sortBy
    const sd = opts.sortDir ?? sortDir
    setError(null)
    try {
      const token = await getToken()
      const data = await api.listMembers(groupId, token, { page: p, search: s || undefined, sortBy: sb, sortDir: sd })
      setMembers(data.data)
      setMeta(data.meta)
    } catch (e) { setError(e.message) }
  }

  useEffect(() => { setPage(1); setSearch(''); setSearchInput(''); loadMembers({ page: 1, search: '' }) }, [groupId])
  useEffect(() => { loadMembers() }, [page, search, sortBy, sortDir])

  const handleSearch = () => {
    setPage(1)
    setSearch(searchInput)
  }

  const handleClearSearch = () => {
    setSearchInput('')
    setPage(1)
    setSearch('')
  }

  const handleSort = (field) => {
    if (sortBy === field) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortBy(field)
      setSortDir('asc')
    }
    setPage(1)
  }

  const sortIndicator = (field) => {
    if (sortBy !== field) return ''
    return sortDir === 'asc' ? ' \u25B2' : ' \u25BC'
  }

  const thSortable = { ...styles.th, cursor: 'pointer', userSelect: 'none' }

  const handleRemove = async (userId) => {
    setError(null)
    try {
      const token = await getToken()
      await api.removeMember(groupId, userId, token)
      loadMembers()
    } catch (e) { setError(e.message) }
  }

  const handlePromote = async (userId) => {
    setError(null)
    try {
      const token = await getToken()
      await api.promoteMember(groupId, userId, token)
      loadMembers()
    } catch (e) { setError(e.message) }
  }

  const handleDemote = async (userId) => {
    setError(null)
    try {
      const token = await getToken()
      await api.demoteMember(groupId, userId, token)
      loadMembers()
    } catch (e) { setError(e.message) }
  }

  return (
    <div style={styles.section}>
      <h2 style={styles.sectionTitle}>Members</h2>
      <div style={styles.row}>
        <input
          style={{ ...styles.input, flex: 1 }}
          placeholder="Search members..."
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleSearch()}
        />
        <button style={styles.btn} onClick={handleSearch}>Search</button>
        {search && <button style={{ ...styles.btn, backgroundColor: '#888' }} onClick={handleClearSearch}>Clear</button>}
      </div>
      {error && <p style={styles.error}>{error}</p>}
      {members && (
        <>
          <table style={styles.table}>
            <thead>
              <tr>
                <th style={thSortable} onClick={() => handleSort('username')}>Username{sortIndicator('username')}</th>
                <th style={thSortable} onClick={() => handleSort('email')}>Email{sortIndicator('email')}</th>
                <th style={thSortable} onClick={() => handleSort('name')}>Name{sortIndicator('name')}</th>
                <th style={thSortable} onClick={() => handleSort('admin')}>Admin{sortIndicator('admin')}</th>
                <th style={styles.th}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {members.map(m => (
                <tr key={m.id}>
                  <td style={styles.td}>{m.username}</td>
                  <td style={styles.td}>{m.email}</td>
                  <td style={styles.td}>{[m.firstName, m.lastName].filter(Boolean).join(' ')}</td>
                  <td style={styles.td}>{m.isGroupAdmin ? 'Yes' : 'No'}</td>
                  <td style={styles.td}>
                    <div style={{ display: 'flex', gap: 4 }}>
                      {m.isGroupAdmin
                        ? <button style={{ ...styles.btn, ...styles.btnWarning }} onClick={() => handleDemote(m.id)}>Demote</button>
                        : <button style={{ ...styles.btn, ...styles.btnSuccess }} onClick={() => handlePromote(m.id)}>Promote</button>
                      }
                      <button style={{ ...styles.btn, ...styles.btnDanger }} onClick={() => handleRemove(m.id)}>Remove</button>
                    </div>
                  </td>
                </tr>
              ))}
              {members.length === 0 && (
                <tr><td style={styles.td} colSpan={5}>No members found</td></tr>
              )}
            </tbody>
          </table>
          {meta && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 12, fontSize: 14 }}>
              <span style={{ color: '#666' }}>
                {meta.totalCount === 0 ? 'No results' : `Showing ${(meta.page - 1) * meta.pageSize + 1}\u2013${Math.min(meta.page * meta.pageSize, meta.totalCount)} of ${meta.totalCount}`}
              </span>
              {meta.totalPages > 1 && (
                <div style={{ display: 'flex', gap: 4 }}>
                  <button
                    style={{ ...styles.btn, ...(page <= 1 ? { opacity: 0.5, cursor: 'default' } : {}) }}
                    onClick={() => page > 1 && setPage(page - 1)}
                    disabled={page <= 1}
                  >
                    Previous
                  </button>
                  {Array.from({ length: meta.totalPages }, (_, i) => i + 1).map(p => (
                    <button
                      key={p}
                      style={{ ...styles.btn, ...(p === page ? { backgroundColor: '#333' } : { backgroundColor: '#aaa' }) }}
                      onClick={() => setPage(p)}
                    >
                      {p}
                    </button>
                  ))}
                  <button
                    style={{ ...styles.btn, ...(page >= meta.totalPages ? { opacity: 0.5, cursor: 'default' } : {}) }}
                    onClick={() => page < meta.totalPages && setPage(page + 1)}
                    disabled={page >= meta.totalPages}
                  >
                    Next
                  </button>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function AcceptSection() {
  const getToken = useToken()
  const [invToken, setInvToken] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const handleAccept = async () => {
    setError(null); setResult(null)
    try {
      const token = await getToken()
      const data = await api.acceptInvitation(invToken, token)
      setResult(data)
    } catch (e) { setError(e.message) }
  }

  return (
    <div style={styles.section}>
      <h2 style={styles.sectionTitle}>Accept Invitation</h2>
      <div style={styles.row}>
        <input style={{ ...styles.input, flex: 1 }} placeholder="Invitation token" value={invToken} onChange={e => setInvToken(e.target.value)} />
        <button style={{ ...styles.btn, ...styles.btnSuccess }} onClick={handleAccept}>Accept</button>
      </div>
      {error && <p style={styles.error}>{error}</p>}
      {result && (
        <div style={{ marginTop: 12 }}>
          <p style={styles.success}>{result.message}</p>
          <p>Group: <strong>{result.groupName}</strong></p>
          <Link to="/">Go to dashboard</Link>
        </div>
      )}
    </div>
  )
}

function InvitationResultPage() {
  const [searchParams] = useSearchParams()
  const result = searchParams.get('result')
  const isSuccess = result === 'success'
  const groupName = searchParams.get('group_name')
  const groupId = searchParams.get('group_id')
  const error = searchParams.get('error')
  const errorDescription = searchParams.get('error_description')

  return (
    <div style={styles.section}>
      {isSuccess ? (
        <>
          <h2 style={{ ...styles.sectionTitle, color: '#5cb85c' }}>Invitation Accepted</h2>
          <p style={{ fontSize: 16, lineHeight: 1.6 }}>
            You have successfully joined <strong>{groupName || 'the group'}</strong>.
          </p>
          {groupId && (
            <p style={{ fontSize: 14 }}>
              <Link to={`/groups/${groupId}`}>Manage this group</Link>
            </p>
          )}
        </>
      ) : (
        <>
          <h2 style={{ ...styles.sectionTitle, color: '#d9534f' }}>
            {error ? error.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()) : 'Error'}
          </h2>
          <p style={{ fontSize: 16, lineHeight: 1.6 }}>
            {errorDescription || 'Something went wrong with your invitation.'}
          </p>
        </>
      )}
      <Link to="/">Go to dashboard</Link>
    </div>
  )
}

function ConfigPage() {
  const getToken = useToken()
  const [config, setConfig] = useState(null)
  const [form, setForm] = useState({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)

  const loadConfig = async () => {
    setError(null)
    try {
      const token = await getToken()
      const data = await api.getConfig(token)
      setConfig(data)
      setForm(Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v || ''])))
    } catch (e) { setError(e.message) }
  }

  useEffect(() => { loadConfig() }, [])

  const handleSave = async () => {
    setError(null); setSuccess(null); setSaving(true)
    try {
      const token = await getToken()
      const data = await api.updateConfig(form, token)
      setConfig(data)
      setForm(Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v || ''])))
      setSuccess('Configuration saved.')
    } catch (e) { setError(e.message) }
    setSaving(false)
  }

  const fields = [
    { key: 'group-mgmt-post-accept-url', label: 'Post-Accept Redirect URL', placeholder: 'https://app.example.com/invitation-result', help: 'Where users are redirected after accepting an invitation. Leave blank for built-in HTML page.' },
    { key: 'group-invitation-ttl-hours', label: 'Default Invitation TTL (hours)', placeholder: '72', help: 'Default expiry time for invitations. Can be overridden per-invitation.' },
  ]

  if (!config) return <div style={styles.section}><p>Loading configuration...</p></div>

  return (
    <div style={styles.section}>
      <h2 style={styles.sectionTitle}>Plugin Configuration</h2>
      <p style={{ color: '#666', fontSize: 14, marginBottom: 16 }}>Realm: <strong>{REALM}</strong> — These settings are stored as realm attributes and apply to all groups in this realm.</p>
      {fields.map(f => (
        <div key={f.key} style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', fontWeight: 'bold', fontSize: 14, marginBottom: 4 }}>{f.label}</label>
          <input
            style={{ ...styles.input, width: '100%', boxSizing: 'border-box' }}
            placeholder={f.placeholder}
            value={form[f.key] || ''}
            onChange={e => setForm({ ...form, [f.key]: e.target.value })}
          />
          <p style={{ margin: '4px 0 0', fontSize: 12, color: '#999' }}>{f.help}</p>
        </div>
      ))}
      <div style={styles.row}>
        <button style={styles.btn} onClick={handleSave} disabled={saving}>
          {saving ? 'Saving...' : 'Save Configuration'}
        </button>
      </div>
      {error && <p style={styles.error}>{error}</p>}
      {success && <p style={styles.success}>{success}</p>}
    </div>
  )
}

function TokenViewer() {
  const [show, setShow] = useState(false)
  const [token, setToken] = useState('')

  const handleShow = async () => {
    if (show) {
      setShow(false)
      return
    }
    const user = await userManager.getUser()
    setToken(user?.access_token || 'No token')
    setShow(true)
  }

  return (
    <div style={styles.section}>
      <h2 style={styles.sectionTitle}>Debug</h2>
      <button style={styles.btn} onClick={handleShow}>
        {show ? 'Hide' : 'Show'} Access Token
      </button>
      {show && (
        <pre style={{ ...styles.pre, marginTop: 10, wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>
          {token}
        </pre>
      )}
    </div>
  )
}
