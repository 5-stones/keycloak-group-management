import { REALM } from './config.js'

const BASE = `/realms/${REALM}/group-mgmt`

async function request(path, { method = 'GET', token, body } = {}) {
  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.error || `HTTP ${res.status}`)
  return data
}

export const api = {
  // Invitations
  listInvitations: (groupId, token) =>
    request(`/groups/${groupId}/invitations`, { token }),
  getInvitation: (groupId, invitationId, token) =>
    request(`/groups/${groupId}/invitations/${invitationId}`, { token }),
  createInvitation: (groupId, email, role, ttlHours, token) =>
    request(`/groups/${groupId}/invitations`, { method: 'POST', token, body: { email, role, ttlHours } }),
  resendInvitation: (groupId, invitationId, token) =>
    request(`/groups/${groupId}/invitations/${invitationId}/resend`, { method: 'POST', token }),
  deleteInvitation: (groupId, invitationId, token) =>
    request(`/groups/${groupId}/invitations/${invitationId}`, { method: 'DELETE', token }),

  // Members
  listMembers: (groupId, token, { page = 1, pageSize = 5, search, sortBy, sortDir } = {}) => {
    const params = new URLSearchParams({ page, pageSize })
    if (search) params.set('search', search)
    if (sortBy) params.set('sortBy', sortBy)
    if (sortDir) params.set('sortDir', sortDir)
    return request(`/groups/${groupId}/members?${params}`, { token })
  },
  removeMember: (groupId, userId, token) =>
    request(`/groups/${groupId}/members/${userId}`, { method: 'DELETE', token }),
  promoteMember: (groupId, userId, token) =>
    request(`/groups/${groupId}/members/${userId}/promote`, { method: 'PUT', token }),
  demoteMember: (groupId, userId, token) =>
    request(`/groups/${groupId}/members/${userId}/demote`, { method: 'PUT', token }),

  // Accept
  acceptInvitation: (invitationToken, authToken) =>
    request(`/invitations/accept?token=${encodeURIComponent(invitationToken)}`, { method: 'POST', token: authToken }),

  // User
  getMyGroups: (token, { page = 1, pageSize = 5, search, sortBy, sortDir } = {}) => {
    const params = new URLSearchParams({ page, pageSize })
    if (search) params.set('search', search)
    if (sortBy) params.set('sortBy', sortBy)
    if (sortDir) params.set('sortDir', sortDir)
    return request(`/me/groups?${params}`, { token })
  },

  // Config
  getConfig: (token) =>
    request('/config', { token }),
  updateConfig: (config, token) =>
    request('/config', { method: 'PUT', token, body: config }),
}
