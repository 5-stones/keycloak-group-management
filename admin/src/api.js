import { REALM } from './config.js'

const ROOT = `/realms/${REALM}/group-mgmt`
const BASE = `${ROOT}/api`

async function doRequest(url, { method = 'GET', token, body } = {}) {
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    const err = new Error(data?.error || `HTTP ${res.status}`)
    err.status = res.status
    throw err
  }
  return data
}

const request = (path, opts) => doRequest(`${BASE}${path}`, opts)

export const api = {
  // Realm config (realm-admin only)
  getConfig: (token) =>
    request('/config', { token }),
  updateConfig: (config, token) =>
    request('/config', { method: 'PUT', token, body: config }),

  // Role/permission vocabulary
  getRoles: (token) =>
    request('/roles', { token }),

  // User's groups
  getMyGroups: (token, { page = 1, pageSize = 5, search, sortBy, sortDir } = {}) => {
    const params = new URLSearchParams({ page, pageSize })
    if (search) params.set('search', search)
    if (sortBy) params.set('sortBy', sortBy)
    if (sortDir) params.set('sortDir', sortDir)
    return request(`/me/groups?${params}`, { token })
  },

  // Group resource
  getGroup: (groupId, token) =>
    request(`/groups/${groupId}`, { token }),
  updateGroup: (groupId, body, token) =>
    request(`/groups/${groupId}`, { method: 'PUT', token, body }),

  // Members
  listMembers: (groupId, token, { page = 1, pageSize = 5, search, sortBy, sortDir, role } = {}) => {
    const params = new URLSearchParams({ page, pageSize })
    if (search) params.set('search', search)
    if (sortBy) params.set('sortBy', sortBy)
    if (sortDir) params.set('sortDir', sortDir)
    if (role) params.set('role', role)
    return request(`/groups/${groupId}/members?${params}`, { token })
  },
  removeMember: (groupId, userId, token) =>
    request(`/groups/${groupId}/members/${userId}`, { method: 'DELETE', token }),
  setMemberRoles: (groupId, userId, roles, token) =>
    request(`/groups/${groupId}/members/${userId}/roles`, { method: 'PUT', token, body: { roles } }),

  // Invitations
  listInvitations: (groupId, token) =>
    request(`/groups/${groupId}/invitations`, { token }),
  getInvitation: (groupId, invitationId, token) =>
    request(`/groups/${groupId}/invitations/${invitationId}`, { token }),
  createInvitation: (groupId, email, roles, ttlHours, token) =>
    request(`/groups/${groupId}/invitations`, { method: 'POST', token, body: { email, roles, ttlHours } }),
  resendInvitation: (groupId, invitationId, token) =>
    request(`/groups/${groupId}/invitations/${invitationId}/resend`, { method: 'POST', token }),
  deleteInvitation: (groupId, invitationId, token) =>
    request(`/groups/${groupId}/invitations/${invitationId}`, { method: 'DELETE', token }),
}
