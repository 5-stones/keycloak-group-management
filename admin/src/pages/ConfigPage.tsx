import { useState, useEffect, type ReactNode } from 'react'
import Select from 'react-select'
import { api, type Config } from '../api'
import useToken from '../hooks/useToken'
import { PERM_FALLBACK, ROLE_NAME_REGEX, buildRolesList, type RoleEntry } from '../lib/roles'
import {
  SELECT_CLASS_NAMES,
  SELECT_STYLES,
  SELECT_PORTAL_TARGET,
  rolesToOptions,
  type RoleOption,
} from '../lib/select'

type FormState = Record<string, string>

interface ConfigField {
  key: string
  label: string
  placeholder: string
  help: ReactNode
}

export default function ConfigPage() {
  const getToken = useToken()
  const [config, setConfig] = useState<Config | null>(null)
  const [form, setForm] = useState<FormState>({})
  const [rolesList, setRolesList] = useState<RoleEntry[]>([])
  const [permissions, setPermissions] = useState<string[]>(PERM_FALLBACK)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const loadConfig = async () => {
    setError(null)
    try {
      const token = await getToken()
      const [data, rolesData] = await Promise.all([
        api.getConfig(token),
        api.getRoles(token).catch(() => null),
      ])
      setConfig(data)
      setForm(Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v || ''])))
      setRolesList(buildRolesList(data['group-mgmt-allowed-roles'], data['group-mgmt-role-permissions']))
      if (rolesData?.permissions?.length) setPermissions(rolesData.permissions)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  useEffect(() => { loadConfig() }, [])

  const handleSave = async () => {
    setError(null); setSuccess(null); setSaving(true)
    try {
      const token = await getToken()

      // `member` is always implicit on the backend; don't write it to allowed-roles.
      const allowedRolesCsv = rolesList
        .filter((r) => !r.reserved)
        .map((r) => r.name)
        .join(',')
      const rolePermsObj: Record<string, string[]> = Object.fromEntries(
        rolesList
          .filter((r) => r.permissions.size > 0)
          .map((r) => [r.name, Array.from(r.permissions).sort()])
      )
      const rolePermsStr = Object.keys(rolePermsObj).length ? JSON.stringify(rolePermsObj) : ''

      const payload: Config = {
        ...form,
        'group-mgmt-allowed-roles': allowedRolesCsv,
        'group-mgmt-role-permissions': rolePermsStr,
      }

      const data = await api.updateConfig(payload, token)
      setConfig(data)
      setForm(Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v || ''])))
      setRolesList(buildRolesList(data['group-mgmt-allowed-roles'], data['group-mgmt-role-permissions']))
      setSuccess('Configuration saved.')
    } catch (e) {
      setError((e as Error).message)
    }
    setSaving(false)
  }

  const fields: ConfigField[] = [
    {
      key: 'group-mgmt-post-accept-url',
      label: 'Post-Accept Redirect URL',
      placeholder: 'https://app.example.com/invitation-result',
      help: (
        <>
          Where users are redirected after accepting an invitation. Leave blank to show the built-in HTML page.
          <span className="mt-2 block font-medium text-slate-600">Query params appended to the redirect:</span>
          <ul className="mt-1 list-inside list-disc space-y-1">
            <li>
              On success: <code className="rounded bg-slate-100 px-1">result=success</code>,{' '}
              <code className="rounded bg-slate-100 px-1">group_id</code>,{' '}
              <code className="rounded bg-slate-100 px-1">group_name</code>
            </li>
            <li>
              On error: <code className="rounded bg-slate-100 px-1">result=error</code>,{' '}
              <code className="rounded bg-slate-100 px-1">error</code> (e.g. <code className="rounded bg-slate-100 px-1">expired</code>, <code className="rounded bg-slate-100 px-1">invalid_token</code>),{' '}
              <code className="rounded bg-slate-100 px-1">error_description</code>
            </li>
          </ul>
        </>
      ),
    },
    {
      key: 'group-invitation-ttl-hours',
      label: 'Default Invitation TTL (hours)',
      placeholder: '72',
      help: 'Default expiry time for invitations. Can be overridden per-invitation.',
    },
  ]

  if (!config) {
    return <p className="text-sm text-slate-500">Loading configuration…</p>
  }

  return (
    <div className="space-y-10">
      <section className="space-y-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">Roles &amp; Permissions</h2>
          <p className="mt-1 text-xs text-slate-500">
            Define the realm's role vocabulary and the permissions each role grants.{' '}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-[11px]">admin</code> always grants every permission and cannot be redefined.{' '}
            <code className="rounded bg-slate-100 px-1 py-0.5 text-[11px]">member</code> permissions apply implicitly to every group member, regardless of whether they have <code className="rounded bg-slate-100 px-1 py-0.5 text-[11px]">member</code> in their explicit role list — use it for baseline read access. A custom role with no permissions is just a tag (assignable but grants no API access).
          </p>
        </div>
        <RolePermissionsEditor
          rolesList={rolesList}
          permissions={permissions}
          onChange={setRolesList}
        />
      </section>

      <section className="space-y-5">
        <h2 className="text-base font-semibold text-slate-900">Invitations</h2>
        {fields.map((f) => (
          <div key={f.key}>
            <label className="block text-sm font-medium text-slate-800">{f.label}</label>
            <input
              type="text"
              className="mt-1 block w-full rounded-md border-slate-300 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
              placeholder={f.placeholder}
              value={form[f.key] || ''}
              onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
            />
            <p className="mt-1 text-xs text-slate-500">{f.help}</p>
          </div>
        ))}
      </section>

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? 'Saving…' : 'Save Configuration'}
        </button>
        {error && <p className="text-sm text-red-600">{error}</p>}
        {success && <p className="text-sm text-emerald-600">{success}</p>}
      </div>
    </div>
  )
}

interface RolePermissionsEditorProps {
  rolesList: RoleEntry[]
  permissions: string[]
  onChange: (next: RoleEntry[]) => void
}

function RolePermissionsEditor({ rolesList, permissions, onChange }: RolePermissionsEditorProps) {
  const [newRoleName, setNewRoleName] = useState('')
  const [newRolePerms, setNewRolePerms] = useState<Set<string>>(() => new Set())
  const [addError, setAddError] = useState<string | null>(null)

  const setPerms = (idx: number, perms: Set<string>) => {
    onChange(rolesList.map((r, i) => (i === idx ? { ...r, permissions: perms } : r)))
  }

  const removeRole = (idx: number) => {
    onChange(rolesList.filter((_, i) => i !== idx))
  }

  const addRole = () => {
    setAddError(null)
    const name = newRoleName.trim().toLowerCase()
    if (!ROLE_NAME_REGEX.test(name)) {
      setAddError(`Name must match ${ROLE_NAME_REGEX} (lowercase, digits, dash, underscore; 1-64 chars).`)
      return
    }
    if (name === 'admin') {
      setAddError("'admin' is reserved.")
      return
    }
    if (rolesList.some((r) => r.name === name)) {
      setAddError(`'${name}' already exists.`)
      return
    }
    const sorted: RoleEntry[] = [...rolesList, { name, permissions: newRolePerms, reserved: false }]
      .sort((a, b) => a.name.localeCompare(b.name))
    onChange(sorted)
    setNewRoleName('')
    setNewRolePerms(new Set())
  }

  const optionsAll = rolesToOptions(permissions)

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs font-medium uppercase tracking-wider text-slate-500">
            <th className="w-1/4 px-4 py-3">Role</th>
            <th className="px-4 py-3">Permissions</th>
            <th className="w-20 px-4 py-3 text-center"></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          <tr className="bg-slate-50/40">
            <td className="px-4 py-3">
              <span className="font-semibold text-slate-900">admin</span>
              <span className="ml-2 inline-flex rounded-full bg-slate-200 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-slate-600">
                reserved
              </span>
            </td>
            <td className="px-4 py-3 text-xs text-emerald-700">
              all permissions ({permissions.length})
            </td>
            <td></td>
          </tr>

          {rolesList.map((role, idx) => (
            <tr key={role.name} className={role.reserved ? 'bg-slate-50/40' : ''}>
              <td className="px-4 py-3 align-middle">
                <span className="font-semibold text-slate-900">{role.name}</span>
                {role.reserved && (
                  <span className="ml-2 inline-flex rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-amber-700">
                    baseline
                  </span>
                )}
                {role.reserved && (
                  <p className="mt-1 text-[11px] text-slate-500">applies to every group member</p>
                )}
              </td>
              <td className="px-4 py-3 align-middle">
                <Select<RoleOption, true>
                  isMulti
                  isClearable={false}
                  classNamePrefix="rs"
                  classNames={SELECT_CLASS_NAMES}
                  styles={SELECT_STYLES}
                  menuPortalTarget={SELECT_PORTAL_TARGET}
                  aria-label={`Permissions for ${role.name}`}
                  options={optionsAll}
                  value={optionsAll.filter((o) => role.permissions.has(o.value))}
                  onChange={(opts) => setPerms(idx, new Set((opts || []).map((o) => o.value)))}
                  placeholder="Add permissions…"
                />
              </td>
              <td className="px-4 py-3 text-center align-middle">
                {!role.reserved && (
                  <button
                    type="button"
                    onClick={() => removeRole(idx)}
                    title="Remove role"
                    className="rounded-md p-1 text-slate-400 transition hover:bg-red-50 hover:text-red-600"
                    aria-label={`Remove role ${role.name}`}
                  >
                    <svg viewBox="0 0 20 20" fill="currentColor" className="h-5 w-5">
                      <path fillRule="evenodd" d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" clipRule="evenodd" />
                    </svg>
                  </button>
                )}
              </td>
            </tr>
          ))}

          <tr className="bg-indigo-50/40">
            <td className="px-4 py-3 align-middle">
              <input
                type="text"
                className="block w-full rounded-md border-slate-300 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                placeholder="role-name"
                value={newRoleName}
                onChange={(e) => setNewRoleName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && addRole()}
              />
            </td>
            <td className="px-4 py-3 align-middle">
              <Select<RoleOption, true>
                isMulti
                isClearable={false}
                classNamePrefix="rs"
                classNames={SELECT_CLASS_NAMES}
                styles={SELECT_STYLES}
                menuPortalTarget={SELECT_PORTAL_TARGET}
                aria-label="Permissions for new role"
                options={optionsAll}
                value={optionsAll.filter((o) => newRolePerms.has(o.value))}
                onChange={(opts) => setNewRolePerms(new Set((opts || []).map((o) => o.value)))}
                placeholder="Add permissions…"
              />
            </td>
            <td className="px-4 py-3 text-center align-middle">
              <button
                type="button"
                onClick={addRole}
                disabled={!newRoleName.trim()}
                className="rounded-md bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white shadow-sm hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Add
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      {addError && (
        <p className="border-t border-slate-200 bg-red-50 px-4 py-2 text-xs text-red-700">{addError}</p>
      )}
    </div>
  )
}
