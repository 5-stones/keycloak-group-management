import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import Select from 'react-select'
import { api, type Member, type PageMeta } from '../api'
import useToken from '../hooks/useToken'
import { Section, ErrorMessage } from '../components/Section'
import { Pagination, ThSortable } from '../components/Table'
import RoleChips from '../components/RoleChips'
import EditRolesModal from '../components/EditRolesModal'
import {
  SELECT_CLASS_NAMES,
  SELECT_STYLES,
  SELECT_PORTAL_TARGET,
  rolesToOptions,
  type RoleOption,
} from '../lib/select'
import {
  btnNeutral,
  btnOutlinePrimary,
  btnPrimary,
  iconBtnDanger,
  iconBtnWarning,
  inputBase,
} from '../lib/buttons'
import { IconPencil, IconPlus, IconTrash } from '../lib/icons'
import type { GroupDashboardContext } from './GroupDashboard'

interface LoadOpts {
  page?: number
  search?: string
  sortBy?: string
  sortDir?: 'asc' | 'desc'
  role?: string
}

export default function MembersTab() {
  const { groupId, vocabulary, openInviteModal } = useOutletContext<GroupDashboardContext>()
  const getToken = useToken()
  const [members, setMembers] = useState<Member[] | null>(null)
  const [meta, setMeta] = useState<PageMeta | null>(null)
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [sortBy, setSortBy] = useState('username')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [editingMemberId, setEditingMemberId] = useState<string | null>(null)
  const [editingRoles, setEditingRoles] = useState<Set<string>>(() => new Set())
  const [error, setError] = useState<string | null>(null)

  const loadMembers = async (opts: LoadOpts = {}) => {
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
    } catch (e) { setError((e as Error).message) }
  }

  useEffect(() => {
    setPage(1); setSearch(''); setSearchInput(''); setRoleFilter('')
    loadMembers({ page: 1, search: '', role: '' })
  }, [groupId])
  useEffect(() => { loadMembers() }, [page, search, sortBy, sortDir, roleFilter])

  const handleSearch = () => { setPage(1); setSearch(searchInput) }
  const handleClearSearch = () => { setSearchInput(''); setPage(1); setSearch('') }
  const handleSort = (field: string) => {
    if (sortBy === field) setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    else { setSortBy(field); setSortDir('asc') }
    setPage(1)
  }

  const handleRemove = async (userId: string) => {
    setError(null)
    try {
      const token = await getToken()
      await api.removeMember(groupId, userId, token)
      loadMembers()
    } catch (e) { setError((e as Error).message) }
  }

  const startEditRoles = (member: Member) => {
    setEditingMemberId(member.id)
    setEditingRoles(new Set(member.roles || []))
  }

  const saveEditRoles = async (memberId: string | null) => {
    if (!memberId) return
    setError(null)
    try {
      const token = await getToken()
      await api.setMemberRoles(groupId, memberId, Array.from(editingRoles), token)
      setEditingMemberId(null)
      setEditingRoles(new Set())
      loadMembers()
    } catch (e) { setError((e as Error).message) }
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
          <Select<RoleOption, false>
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
