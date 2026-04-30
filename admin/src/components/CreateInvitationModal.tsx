import { useEffect, useState } from 'react'
import RoleMultiSelect from './RoleMultiSelect'
import { ErrorMessage } from './Section'
import { btnNeutral, btnPrimary, inputBase } from '../lib/buttons'
import type { Vocabulary } from '../api'

export interface CreateInvitationPayload {
  email: string
  roles: string[]
  ttl: number | null
}

interface CreateInvitationModalProps {
  open: boolean
  vocabulary: Vocabulary
  onCancel: () => void
  onCreate: (payload: CreateInvitationPayload) => Promise<void>
}

export default function CreateInvitationModal({
  open,
  vocabulary,
  onCancel,
  onCreate,
}: CreateInvitationModalProps) {
  const [email, setEmail] = useState('')
  const [selectedRoles, setSelectedRoles] = useState<Set<string>>(() => new Set())
  const [ttl, setTtl] = useState('72')
  const [error, setError] = useState<string | null>(null)
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
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onCancel() }
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
      setError((e as Error).message)
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
