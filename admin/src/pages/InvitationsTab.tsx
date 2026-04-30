import { useOutletContext } from 'react-router-dom'
import { api } from '../api'
import useToken from '../hooks/useToken'
import { Section, ErrorMessage } from '../components/Section'
import RoleChips from '../components/RoleChips'
import { btnOutlinePrimary, iconBtnDanger, iconBtnNeutral } from '../lib/buttons'
import { IconPlus, IconResend, IconTrash } from '../lib/icons'
import type { GroupDashboardContext } from './GroupDashboard'

export default function InvitationsTab() {
  const {
    groupId,
    invitations,
    invitationError,
    invitationResult,
    setInvitationError,
    setInvitationResult,
    reloadInvitations,
    openInviteModal,
  } = useOutletContext<GroupDashboardContext>()
  const getToken = useToken()
  const error = invitationError
  const result = invitationResult

  const clear = () => { setInvitationError(null); setInvitationResult(null) }

  const handleResend = async (invId: string) => {
    clear()
    try {
      const token = await getToken()
      await api.resendInvitation(groupId, invId, token)
      setInvitationResult({ message: 'Invitation resent successfully' })
    } catch (e) { setInvitationError((e as Error).message) }
  }

  const handleDelete = async (invId: string) => {
    clear()
    try {
      const token = await getToken()
      await api.deleteInvitation(groupId, invId, token)
      reloadInvitations()
    } catch (e) { setInvitationError((e as Error).message) }
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
