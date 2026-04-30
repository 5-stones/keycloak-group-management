import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useParams } from 'react-router-dom'
import { api, type Group, type Invitation, type InvitationCreated, type Vocabulary } from '../api'
import useToken from '../hooks/useToken'
import CreateInvitationModal, { type CreateInvitationPayload } from '../components/CreateInvitationModal'
import { btnNeutral, btnSuccess, inputBase } from '../lib/buttons'
import { IconPencil } from '../lib/icons'

export interface GroupDashboardContext {
  groupId: string
  vocabulary: Vocabulary
  invitations: Invitation[] | null
  invitationError: string | null
  invitationResult: InvitationCreated | null
  setInvitationError: (msg: string | null) => void
  setInvitationResult: (res: InvitationCreated | null) => void
  reloadInvitations: () => Promise<void>
  openInviteModal: () => void
}

export default function GroupDashboard() {
  const { groupId } = useParams<{ groupId: string }>()
  const getToken = useToken()
  const [vocabulary, setVocabulary] = useState<Vocabulary>({ roles: ['admin', 'member'] })
  const [group, setGroup] = useState<Group | null>(null)
  const [editingName, setEditingName] = useState(false)
  const [nameInput, setNameInput] = useState('')
  const [nameSaving, setNameSaving] = useState(false)
  const [nameError, setNameError] = useState<string | null>(null)
  const [invitations, setInvitations] = useState<Invitation[] | null>(null)
  const [invitationError, setInvitationError] = useState<string | null>(null)
  const [invitationResult, setInvitationResult] = useState<InvitationCreated | null>(null)
  const [isCreatingInvitation, setIsCreatingInvitation] = useState(false)

  useEffect(() => {
    if (!groupId) return
    let cancelled = false
    const load = async () => {
      try {
        const token = await getToken()
        const [g, rolesData] = await Promise.all([
          api.getGroup(groupId, token).catch((e: Error) => { setNameError(e.message); return null }),
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
    if (!groupId) return
    try {
      const token = await getToken()
      const data = await api.listInvitations(groupId, token)
      setInvitations(data.data)
    } catch (e) { setInvitationError((e as Error).message) }
  }
  useEffect(() => { setInvitations(null); loadInvitations() }, [groupId])

  const openInviteModal = () => {
    setInvitationError(null)
    setInvitationResult(null)
    setIsCreatingInvitation(true)
  }
  const handleCreateInvitation = async ({ email, roles, ttl }: CreateInvitationPayload) => {
    if (!groupId) return
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
    if (!groupId) return
    setNameError(null); setNameSaving(true)
    try {
      const token = await getToken()
      const updated = await api.updateGroup(groupId, { name: nameInput.trim() }, token)
      setGroup(updated); setNameInput(updated.name); setEditingName(false)
    } catch (e) { setNameError((e as Error).message) }
    setNameSaving(false)
  }

  const tabLinkClass = ({ isActive }: { isActive: boolean }) =>
    `inline-flex items-center border-b-2 px-1 pb-3 text-sm font-medium transition ${
      isActive
        ? 'border-indigo-500 text-indigo-700'
        : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
    }`

  if (!groupId) return null

  const ctx: GroupDashboardContext = {
    groupId,
    vocabulary,
    invitations,
    invitationError,
    invitationResult,
    setInvitationError,
    setInvitationResult,
    reloadInvitations: loadInvitations,
    openInviteModal,
  }

  return (
    <div className="space-y-6">
      <nav className="flex items-center gap-1 text-sm text-slate-500">
        <Link to="/" className="hover:text-indigo-600">All groups</Link>
        <span aria-hidden className="text-slate-300">/</span>
        <span className="font-medium text-slate-900">{group?.name || '…'}</span>
      </nav>

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

      <Outlet context={ctx} />

      <CreateInvitationModal
        open={isCreatingInvitation}
        vocabulary={vocabulary}
        onCancel={() => setIsCreatingInvitation(false)}
        onCreate={handleCreateInvitation}
      />
    </div>
  )
}
