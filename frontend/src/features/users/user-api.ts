import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { Role } from '../auth/auth-api'

/** Không bao giờ có mật khẩu (kể cả dạng băm) trong response. */
export interface User {
  id: number
  username: string
  fullName: string
  roles: Role[]
  active: boolean
  lastLoginAt?: string
  createdAt: string
  updatedAt: string
}

export interface CreateUserInput {
  username: string
  fullName: string
  password: string
  roles: Role[]
}

/** Tên đăng nhập không đổi được; mật khẩu đổi qua resetPassword. */
export interface UpdateUserInput {
  fullName: string
  roles: Role[]
  active: boolean
}

export type UserFilters = {
  keyword?: string
  role?: Role
  active?: boolean
}

const BASE = '/api/users'

export const userApi = {
  list: ({ page, size }: PageQuery, filters: UserFilters = {}) =>
    apiRequest<PageResponse<User>>(withQuery(BASE, { page, size, sort: 'username,asc', ...toFilterParams(filters) })),

  create: (input: CreateUserInput) => apiRequest<User>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  /** Đổi vai trò hoặc khóa → backend thu hồi mọi phiên của người đó (mất quyền trong tối đa 15 phút). */
  update: (id: number, input: UpdateUserInput) =>
    apiRequest<User>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  resetPassword: (id: number, newPassword: string) =>
    apiRequest<void>(`${BASE}/${id}/reset-password`, { method: 'POST', body: JSON.stringify({ newPassword }) }),
}
