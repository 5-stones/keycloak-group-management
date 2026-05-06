import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Select from 'react-select'
import { api, type Group, type PageMeta } from '../api'
import useToken from '../hooks/useToken'
import { Section, ErrorMessage } from '../components/Section'
import { Pagination, ThSortable } from '../components/Table'
import RoleChips from '../components/RoleChips'
import { btnNeutral, btnPrimary, iconBtnNeutral, iconBtnPrimary, inputBase } from '../lib/buttons'
import { IconArrowRight, IconChevronLeft, IconCog } from '../lib/icons'
import { computePermissions } from '../lib/roles'
import {
  rolesToOptions,
  SELECT_CLASS_NAMES,
  SELECT_PORTAL_TARGET,
  SELECT_STYLES,
  type RoleOption,
} from '../lib/select'

type Mode = 'tree' | 'flat'

interface GroupsListPageProps {
  isRealmAdmin: boolean
}

export default function GroupsListPage({ isRealmAdmin }: GroupsListPageProps) {
  const getToken = useToken()
  const [searchParams, setSearchParams] = useSearchParams()
  const parentId = searchParams.get('parentId')
  // Mode is `flat` when scope=inherited is in the URL; otherwise `tree`. Drill-down
  // (parentId set) implies tree mode regardless of `scope`.
  const mode: Mode = parentId
    ? 'tree'
    : searchParams.get('scope') === 'inherited' ? 'flat' : 'tree'

  const [groups, setGroups] = useState<Group[] | null>(null)
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [sortBy, setSortBy] = useState('name')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [error, setError] = useState<string | null>(null)
  // Info on the group we're currently drilled INTO (for breadcrumb header).
  // null at root, undefined while loading.
  const [parentGroup, setParentGroup] = useState<Group | null | undefined>(undefined)
  // Realm role→permission map; used to gate per-row action affordances.
  const [rolePerms, setRolePerms] = useState<Record<string, string[]>>({})
  // Realm's role vocabulary, used to populate the role-filter dropdown.
  const [roleVocabulary, setRoleVocabulary] = useState<string[]>([])

  // Role filter is URL-driven so it survives navigation/reload and shows up in shareable
  // links. Empty string in the dropdown maps to "no filter" (param omitted).
  const roleFilter = searchParams.get('role') ?? ''

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const token = await getToken()
        const data = await api.getRoles(token)
        if (!cancelled) {
          setRolePerms(data.rolePermissions ?? {})
          setRoleVocabulary(data.roles ?? [])
        }
      } catch {
        // Non-fatal — affordances will be gated as if no role-perms map exists,
        // which means non-admins see no actions until perms can be resolved.
      }
    })()
    return () => { cancelled = true }
  }, [])

  // Reset paging/search/sort when the URL-driven view changes (parentId, mode, role).
  useEffect(() => {
    setPage(1)
    setSearch('')
    setSearchInput('')
    setSortBy('name')
    setSortDir('asc')
    setGroups(null)
  }, [parentId, mode, roleFilter])

  // Load the parent group's info for the breadcrumb header. Skipped at root.
  useEffect(() => {
    let cancelled = false
    if (!parentId) {
      setParentGroup(null)
      return
    }
    setParentGroup(undefined)
    ;(async () => {
      try {
        const token = await getToken()
        const g = await api.getGroup(parentId, token)
        if (!cancelled) setParentGroup(g)
      } catch (e) {
        if (!cancelled) {
          setError((e as Error).message)
          setParentGroup(null)
        }
      }
    })()
    return () => { cancelled = true }
  }, [parentId])

  // Load the listing whenever any view-input changes.
  useEffect(() => {
    let cancelled = false
    setError(null)
    ;(async () => {
      try {
        const token = await getToken()
        const data = await api.getMyGroups(token, {
          page,
          search: search || undefined,
          sortBy,
          sortDir,
          scope: mode === 'flat' ? 'inherited' : 'direct',
          parentId: parentId || undefined,
          role: roleFilter || undefined,
        })
        if (!cancelled) {
          setGroups(data.data)
          setMeta(data.meta)
        }
      } catch (e) {
        if (!cancelled) setError((e as Error).message)
      }
    })()
    return () => { cancelled = true }
  }, [page, search, sortBy, sortDir, parentId, mode, roleFilter])

  const handleSearch = () => { setPage(1); setSearch(searchInput) }
  const handleClearSearch = () => { setSearchInput(''); setPage(1); setSearch('') }
  const handleSort = (field: string) => {
    if (sortBy === field) setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    else { setSortBy(field); setSortDir('asc') }
    setPage(1)
  }

  const drillInto = (group: Group) => setSearchParams({ parentId: group.id })

  const goUp = () => {
    if (parentGroup?.parentId) setSearchParams({ parentId: parentGroup.parentId })
    else setSearchParams({})
  }

  const goHome = () => setSearchParams({})

  const setMode = (m: Mode) => {
    if (m === 'tree') setSearchParams({})
    else setSearchParams({ scope: 'inherited' })
  }

  // Role filter is URL-driven so it round-trips through navigation/reload. Updating
  // it preserves any other URL params (parentId, scope) so the filter composes with
  // tree drill-down and the mode toggle.
  const setRoleFilter = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set('role', value)
    else next.delete('role')
    setSearchParams(next)
  }

  // Title reflects the current level. At root it's "My Groups"; drilled in,
  // it's the current group's name.
  const title = parentGroup && parentId
    ? `${parentGroup.name} — Subgroups`
    : 'My Groups'

  return (
    <Section title={title}>
      {!parentId && <ModeToggle mode={mode} onChange={setMode} />}
      {parentId && (
        <Breadcrumb
          parentGroup={parentGroup ?? null}
          onUp={goUp}
          onHome={goHome}
          onAncestorClick={(id) => setSearchParams({ parentId: id })}
        />
      )}

      <RoleFilter value={roleFilter} options={roleVocabulary} onChange={setRoleFilter} />

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
      {groups && groups.length === 0 && (
        <p className="mt-4 text-sm text-slate-500">
          {parentId
            ? 'This group has no subgroups.'
            : mode === 'flat'
              ? 'You do not have access to any groups.'
              : 'No groups found.'}
        </p>
      )}

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
              {groups.map((g) => {
                const roleNames = (g.roles ?? []).map((r) =>
                  typeof r === 'string' ? r : r.name,
                )
                const perms = computePermissions(
                  roleNames,
                  rolePerms,
                  isRealmAdmin,
                  g.isDirectMember ?? false,
                )
                // Manage page's first action is reading members, so gate cog on that.
                const canManage = perms.has('members:read')
                return (
                  <tr key={g.id}>
                    <td className="px-4 py-3 font-medium text-slate-900">{g.name}</td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-600">{g.path}</td>
                    <td className="px-4 py-3">
                      {g.roles && g.roles.length
                        ? <RoleChips roles={g.roles} />
                        : <span className="text-xs text-slate-400">member</span>}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex items-center gap-1">
                        {g.hasChildren && (
                          <button
                            onClick={() => drillInto(g)}
                            className={iconBtnNeutral}
                            title="View subgroups"
                            aria-label="View subgroups"
                          >
                            <IconArrowRight />
                          </button>
                        )}
                        {canManage && (
                          <Link
                            to={`/groups/${g.id}`}
                            className={iconBtnPrimary}
                            title="Manage"
                            aria-label="Manage group"
                          >
                            <IconCog />
                          </Link>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {meta && <Pagination meta={meta} page={page} setPage={setPage} className="px-4 py-3" />}
        </div>
      )}
    </Section>
  )
}

// ---------- Sub-components ----------
//
// Listing-page-specific UI bits, kept inline because they're not reusable elsewhere
// and live close to where they're rendered.

interface ModeToggleProps {
  mode: Mode
  onChange: (mode: Mode) => void
}

function ModeToggle({ mode, onChange }: ModeToggleProps) {
  const buttonClass = (active: boolean) =>
    `rounded px-3 py-1 ${active ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`
  return (
    <div className="mb-3 inline-flex rounded-md border border-slate-300 bg-white p-0.5 text-sm">
      <button
        onClick={() => onChange('tree')}
        className={buttonClass(mode === 'tree')}
        aria-pressed={mode === 'tree'}
      >
        Direct
      </button>
      <button
        onClick={() => onChange('flat')}
        className={buttonClass(mode === 'flat')}
        aria-pressed={mode === 'flat'}
        title="Show every group I have access to, flat"
      >
        All accessible
      </button>
    </div>
  )
}

interface BreadcrumbProps {
  parentGroup: Group | null
  onUp: () => void
  onHome: () => void
  onAncestorClick: (id: string) => void
}

function Breadcrumb({ parentGroup, onUp, onHome, onAncestorClick }: BreadcrumbProps) {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-2 text-sm text-slate-600">
      <button onClick={onUp} className={iconBtnNeutral} title="Up one level" aria-label="Up one level">
        <IconChevronLeft />
      </button>
      <button onClick={onHome} className="text-slate-500 hover:text-slate-700 hover:underline">
        My Groups
      </button>
      {parentGroup?.ancestors?.map((a) => (
        <span key={a.id} className="flex items-center gap-2">
          <span className="text-slate-400">/</span>
          <button
            onClick={() => onAncestorClick(a.id)}
            className="text-slate-500 hover:text-slate-700 hover:underline"
          >
            {a.name}
          </button>
        </span>
      ))}
      {parentGroup && (
        <>
          <span className="text-slate-400">/</span>
          <span className="text-slate-700">{parentGroup.name}</span>
        </>
      )}
    </div>
  )
}

interface RoleFilterProps {
  value: string
  options: readonly string[]
  onChange: (value: string) => void
}

function RoleFilter({ value, options, onChange }: RoleFilterProps) {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-2">
      <span className="text-sm text-slate-600">Filter by role:</span>
      <div className="min-w-[200px]">
        <Select<RoleOption, false>
          isClearable
          classNamePrefix="rs"
          classNames={SELECT_CLASS_NAMES}
          menuPortalTarget={SELECT_PORTAL_TARGET}
          options={rolesToOptions(options)}
          value={value ? { value, label: value } : null}
          onChange={(opt) => onChange(opt?.value || '')}
          placeholder="(any)"
          styles={SELECT_STYLES}
        />
      </div>
    </div>
  )
}
