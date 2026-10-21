import { createContext, useContext } from 'react'
import type { CurrentUser } from './auth-api'
import { can } from './permissions'
import type { Permission } from './permissions'

export interface AuthState {
  user: CurrentUser | null
  /** Đang hỏi backend "ai đang đăng nhập" lúc mở trang — chưa biết thì chưa chuyển hướng */
  loading: boolean
  /** Bị đẩy ra vì hết phiên (khác với tự đăng xuất) → trang đăng nhập hiện thông báo */
  sessionExpired: boolean
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => Promise<void>
}

// Tách khỏi AuthProvider.tsx: file component chỉ nên export component (React Fast Refresh)
export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth phải nằm trong AuthProvider')
  return auth
}

/** const canWrite = useCan('sales.write') */
export function useCan(permission: Permission): boolean {
  return can(useAuth().user, permission)
}
