// Helpers for parsing/normalising the realm role-permission config payload.

export const PERM_FALLBACK = [
  'group:write',
  'members:read',
  'members:write',
  'roles:write',
  'invitations:read',
  'invitations:write',
]

export const ROLE_NAME_REGEX = /^[a-z0-9_-]{1,64}$/

export function parseRolePermissionsSafe(str) {
  if (!str) return {}
  try {
    const obj = JSON.parse(str)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return {}
    const result = {}
    for (const [k, v] of Object.entries(obj)) {
      if (Array.isArray(v)) result[k] = v.filter(p => typeof p === 'string')
    }
    return result
  } catch {
    return {}
  }
}

export function buildRolesList(allowedRolesCsv, rolePermsJson) {
  const allowed = (allowedRolesCsv || '')
    .split(',').map(s => s.trim().toLowerCase()).filter(Boolean)
  const rolePerms = parseRolePermissionsSafe(rolePermsJson)
  // `member` is always implicit (like `admin`); ensure it shows up as a row.
  const names = [...new Set([...allowed, ...Object.keys(rolePerms), 'member'])]
    .filter(n => n !== 'admin')
    .sort()
  return names.map(name => ({
    name,
    permissions: new Set(rolePerms[name] || []),
    reserved: name === 'member',
  }))
}
