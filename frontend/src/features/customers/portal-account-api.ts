import { apiRequest } from '../../api/client'

/** Tài khoản app của một khách, nhìn từ phía cửa hàng (BR-POR-02, 10). exists = false → chưa có tài khoản. */
export interface PortalAccount {
  customerId: number
  exists: boolean
  active?: boolean
  mustChangePassword?: boolean
  /** Chỉ có khi đang khóa tạm do sai mật khẩu nhiều lần (BR-POR-04) */
  lockedUntil?: string
  lastLoginAt?: string
  createdBy?: string
  createdAt?: string
}

const base = (customerId: number) => `/api/customers/${customerId}/portal-account`

export const portalAccountApi = {
  get: (customerId: number) => apiRequest<PortalAccount>(base(customerId)),

  create: (customerId: number, password: string) =>
    apiRequest<PortalAccount>(base(customerId), { method: 'POST', body: JSON.stringify({ password }) }),

  resetPassword: (customerId: number, password: string) =>
    apiRequest<PortalAccount>(`${base(customerId)}/password`, { method: 'PUT', body: JSON.stringify({ password }) }),

  lock: (customerId: number) => apiRequest<PortalAccount>(`${base(customerId)}/lock`, { method: 'POST' }),

  unlock: (customerId: number) => apiRequest<PortalAccount>(`${base(customerId)}/unlock`, { method: 'POST' }),
}
