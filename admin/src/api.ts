import { REALM } from './config'

const ROOT = `/realms/${REALM}/group-mgmt`
const BASE = `${ROOT}/api`

// ---------- API response types ----------

export interface PageMeta {
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
}

export interface Paged<T> {
  data: T[]
  meta: PageMeta
}

export interface RoleAssignment {
  name: string
  /**
   * `direct`     — the user has this role assigned to this group's `fs_group_member_role` row.
   * `inherited`  — the user has this role on an ancestor and it inherits down the tree.
   * `realm-admin` — the user is a realm admin, holding `admin` on every group via
   *                 Keycloak-level grants. Returned only when no more-specific source
   *                 (direct or inherited) applies.
   */
  source: 'direct' | 'inherited' | 'realm-admin'
}

export interface Group {
  id: string
  name: string
  path: string
  parentId?: string | null
  hasChildren?: boolean
  /** Root-first ancestry chain (excludes the group itself). Returned by `getGroup`. */
  ancestors?: Array<{ id: string; name: string }>
  /**
   * Inheritance-aware role assignments on the listing endpoint. On other endpoints
   * (members, invitations) roles are still returned as plain strings.
   */
  roles?: RoleAssignment[] | string[]
  /** True if the user is a direct member of this group (for `member`-baseline). */
  isDirectMember?: boolean
}

export interface Member {
  id: string
  username: string
  email: string | null
  firstName: string | null
  lastName: string | null
  roles: string[]
}

export interface Invitation {
  id: string
  email: string
  roles: string[]
  expiresAt: string
}

export interface InvitationCreated {
  id?: string
  email?: string
  roles?: string[]
  expiresAt?: string
  message?: string
}

export interface Vocabulary {
  roles: string[]
}

export interface RolesResponse {
  roles: string[]
  permissions: string[]
  rolePermissions: Record<string, string[]>
}

export type Config = Record<string, string | null>

// ---------- HTTP helper ----------

interface RequestOpts {
  method?: string
  token: string
  body?: unknown
}

export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function doRequest<T>(url: string, { method = 'GET', token, body }: RequestOpts): Promise<T> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
  const res = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null as T
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    const message =
      (data && typeof data === 'object' && 'error' in data && typeof (data as { error: unknown }).error === 'string')
        ? (data as { error: string }).error
        : `HTTP ${res.status}`
    throw new ApiError(message, res.status)
  }
  return data as T
}

const request = <T>(path: string, opts: RequestOpts) => doRequest<T>(`${BASE}${path}`, opts)

// ---------- Query-string params ----------

interface ListParams {
  page?: number
  pageSize?: number
  search?: string
  sortBy?: string
  sortDir?: 'asc' | 'desc' | string
  role?: string
  scope?: 'direct' | 'inherited'
  parentId?: string
}

function buildQuery(params: ListParams): string {
  const sp = new URLSearchParams({
    page: String(params.page ?? 1),
    pageSize: String(params.pageSize ?? 5),
  })
  if (params.search) sp.set('search', params.search)
  if (params.sortBy) sp.set('sortBy', params.sortBy)
  if (params.sortDir) sp.set('sortDir', params.sortDir)
  if (params.role) sp.set('role', params.role)
  if (params.scope) sp.set('scope', params.scope)
  if (params.parentId) sp.set('parentId', params.parentId)
  return sp.toString()
}

// ---------- Public API ----------

export const api = {
  getConfig: (token: string) =>
    request<Config>('/config', { token }),
  updateConfig: (config: Config, token: string) =>
    request<Config>('/config', { method: 'PUT', token, body: config }),

  getRoles: (token: string) =>
    request<RolesResponse>('/roles', { token }),

  getMyGroups: (token: string, params: ListParams = {}) =>
    request<Paged<Group>>(`/me/groups?${buildQuery(params)}`, { token }),

  getGroup: (groupId: string, token: string) =>
    request<Group>(`/groups/${groupId}`, { token }),
  updateGroup: (groupId: string, body: Partial<Pick<Group, 'name'>>, token: string) =>
    request<Group>(`/groups/${groupId}`, { method: 'PUT', token, body }),

  listMembers: (groupId: string, token: string, params: ListParams = {}) =>
    request<Paged<Member>>(`/groups/${groupId}/members?${buildQuery(params)}`, { token }),
  removeMember: (groupId: string, userId: string, token: string) =>
    request<null>(`/groups/${groupId}/members/${userId}`, { method: 'DELETE', token }),
  setMemberRoles: (groupId: string, userId: string, roles: string[], token: string) =>
    request<Member>(`/groups/${groupId}/members/${userId}/roles`, {
      method: 'PUT',
      token,
      body: { roles },
    }),

  listInvitations: (groupId: string, token: string) =>
    request<Paged<Invitation>>(`/groups/${groupId}/invitations`, { token }),
  createInvitation: (
    groupId: string,
    email: string,
    roles: string[],
    ttlHours: number | null,
    token: string,
  ) =>
    request<InvitationCreated>(`/groups/${groupId}/invitations`, {
      method: 'POST',
      token,
      body: { email, roles, ttlHours },
    }),
  resendInvitation: (groupId: string, invitationId: string, token: string) =>
    request<Invitation>(`/groups/${groupId}/invitations/${invitationId}/resend`, {
      method: 'POST',
      token,
    }),
  deleteInvitation: (groupId: string, invitationId: string, token: string) =>
    request<null>(`/groups/${groupId}/invitations/${invitationId}`, { method: 'DELETE', token }),
}
