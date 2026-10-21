import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { onSessionExpired } from '../../api/client'
import { authApi } from './auth-api'
import type { CurrentUser } from './auth-api'
import { AuthContext } from './auth-context'

/**
 * Giữ "ai đang đăng nhập" cho cả ứng dụng. Không giữ token (token ở cookie HttpOnly):
 * mở trang → hỏi GET /api/auth/me; trả 401 dù đã thử gia hạn → chưa đăng nhập.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [sessionExpired, setSessionExpired] = useState(false)
  // Đọc trong handler hết phiên mà không phải đăng ký lại handler mỗi lần user đổi
  const loggedIn = useRef(false)

  useEffect(() => {
    loggedIn.current = user !== null
  }, [user])

  useEffect(() => {
    let ignore = false
    authApi
      .me()
      .then((me) => {
        if (!ignore) setUser(me)
      })
      .catch(() => {
        if (!ignore) setUser(null)
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [])

  // Đang dùng mà phiên hết hạn (quá 8 giờ không thao tác, bị khóa...) → RequireAuth chuyển về trang đăng nhập
  useEffect(() => {
    onSessionExpired(() => {
      // Mở trang lần đầu mà chưa đăng nhập cũng đi qua đây — chỉ báo "hết phiên" khi trước đó đang đăng nhập
      if (loggedIn.current) setSessionExpired(true)
      setUser(null)
    })
    return () => onSessionExpired(null)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const me = await authApi.login(username, password)
    setSessionExpired(false)
    setUser(me)
    return me
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      // Kể cả khi gọi API lỗi: backend đã/ sẽ hết hạn phiên; phía trình duyệt coi như đã đăng xuất
      setUser(null)
    }
  }, [])

  const value = useMemo(() => ({ user, loading, sessionExpired, login, logout }), [user, loading, sessionExpired, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
