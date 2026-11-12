import { createContext, useContext } from 'react'
import type { PortalMe } from './portal-api'

export interface PortalAuthValue {
  me: PortalMe | null
  loading: boolean
  /** Đang dùng app thì phiên hết hạn / cửa hàng khóa tài khoản → trang đăng nhập hiện câu này */
  signOutNotice: string | null
  login: (phone: string, password: string) => Promise<PortalMe>
  logout: () => Promise<void>
  /** Sau khi đổi mật khẩu: đọc lại để mustChangePassword = false */
  reload: () => Promise<void>
}

export const PortalAuthContext = createContext<PortalAuthValue | null>(null)

export function usePortalAuth(): PortalAuthValue {
  const value = useContext(PortalAuthContext)
  if (!value) throw new Error('usePortalAuth phải nằm trong PortalAuthProvider')
  return value
}
