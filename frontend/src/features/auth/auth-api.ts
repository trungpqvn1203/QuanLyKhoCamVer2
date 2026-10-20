import { apiRequest } from '../../api/client'

/** Vai trò nghiệp vụ (ADR-0003). Một người có thể kiêm nhiều vai trò. */
export type Role = 'WAREHOUSE_MANAGER' | 'SALES_STAFF' | 'SYSTEM_ADMIN'

export const ROLE_LABELS: Record<Role, string> = {
  WAREHOUSE_MANAGER: 'Quản lý kho',
  SALES_STAFF: 'Nhân viên bán hàng',
  SYSTEM_ADMIN: 'Quản lý hệ thống',
}

export const ROLE_COLORS: Record<Role, string> = {
  WAREHOUSE_MANAGER: 'blue',
  SALES_STAFF: 'green',
  SYSTEM_ADMIN: 'purple',
}

export interface CurrentUser {
  id: number
  username: string
  fullName: string
  roles: Role[]
}

const BASE = '/api/auth'

/**
 * Không hàm nào nhận hay trả token: token nằm trong cookie HttpOnly do backend đặt/xóa,
 * trình duyệt tự gửi kèm — JavaScript (kể cả mã độc chèn vào trang) không đọc được.
 */
export const authApi = {
  /** Đảm bảo có cookie XSRF-TOKEN trước request ghi đầu tiên (đăng nhập). */
  csrf: () => apiRequest<void>(`${BASE}/csrf`),

  login: (username: string, password: string) =>
    apiRequest<CurrentUser>(`${BASE}/login`, { method: 'POST', body: JSON.stringify({ username, password }) }),

  logout: () => apiRequest<void>(`${BASE}/logout`, { method: 'POST' }),

  me: () => apiRequest<CurrentUser>(`${BASE}/me`),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>(`${BASE}/me/password`, {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
}
