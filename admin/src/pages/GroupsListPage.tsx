import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Group, type PageMeta } from '../api'
import useToken from '../hooks/useToken'
import { Section, ErrorMessage } from '../components/Section'
import { Pagination, ThSortable } from '../components/Table'
import RoleChips from '../components/RoleChips'
import { btnNeutral, btnPrimary, iconBtnPrimary, inputBase } from '../lib/buttons'
import { IconArrowRight } from '../lib/icons'

interface LoadOpts {
  page?: number
  search?: string
  sortBy?: string
  sortDir?: 'asc' | 'desc'
}

export default function GroupsListPage() {
  const getToken = useToken()
  const [groups, setGroups] = useState<Group[] | null>(null)
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [sortBy, setSortBy] = useState('name')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [error, setError] = useState<string | null>(null)

  const loadGroups = async (opts: LoadOpts = {}) => {
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
    } catch (e) { setError((e as Error).message) }
  }

  useEffect(() => { loadGroups() }, [page, search, sortBy, sortDir])

  const handleSearch = () => { setPage(1); setSearch(searchInput) }
  const handleClearSearch = () => { setSearchInput(''); setPage(1); setSearch('') }
  const handleSort = (field: string) => {
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
