import { useEffect } from 'react'
import RoleMultiSelect from './RoleMultiSelect'
import { btnNeutral, btnSuccess } from '../lib/buttons'
import type { Member, Vocabulary } from '../api'

interface EditRolesModalProps {
  member: Member | null
  vocabulary: Vocabulary
  editingRoles: Set<string>
  setEditingRoles: (next: Set<string>) => void
  onCancel: () => void
  onSave: () => void
}

export default function EditRolesModal({
  member,
  vocabulary,
  editingRoles,
  setEditingRoles,
  onCancel,
  onSave,
}: EditRolesModalProps) {
  useEffect(() => {
    if (!member) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onCancel() }
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
