import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { onPortalAccountDisabled, onPortalSessionExpired } from '../../api/client'
import { portalApi } from './portal-api'
import type { PortalMe } from './portal-api'
import { PortalAuthContext } from './portal-auth-context'

/**
 * "Khách nào đang đăng nhập app" — cùng cách làm với AuthProvider của nhân viên nhưng phiên riêng (ADR-0005):
 * mở app → GET /api/portal/me; 401 dù đã thử gia hạn → chưa đăng nhập. Không giữ token (cookie HttpOnly).
 */
export function PortalAuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<PortalMe | null>(null)
  const [loading, setLoading] = useState(true)
  const [signOutNotice, setSignOutNotice] = useState<string | null>(null)
  const loggedIn = useRef(false)

  useEffect(() => {
    loggedIn.current = me !== null
  }, [me])

  useEffect(() => {
    let ignore = false
    portalApi
      .me()
      .then((current) => {
        if (!ignore) setMe(current)
      })
      .catch(() => {
        if (!ignore) setMe(null)
      })
      .finally(() => {
        if (!ignore) setLoading(false)
      })
    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    onPortalSessionExpired(() => {
      if (loggedIn.current) setSignOutNotice('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại')
      setMe(null)
    })
    // Bị khóa giữa chừng: xóa cookie phiên (logout) rồi về trang đăng nhập kèm lý do từ backend
    onPortalAccountDisabled((message) => {
      setSignOutNotice(message)
      setMe(null)
      portalApi.logout().catch(() => undefined)
    })
    return () => {
      onPortalSessionExpired(null)
      onPortalAccountDisabled(null)
    }
  }, [])

  const login = useCallback(async (phone: string, password: string) => {
    await portalApi.csrf()
    const current = await portalApi.login(phone, password)
    setSignOutNotice(null)
    setMe(current)
    return current
  }, [])

  const logout = useCallback(async () => {
    try {
      await portalApi.logout()
    } finally {
      setMe(null)
    }
  }, [])

  const reload = useCallback(async () => {
    setMe(await portalApi.me())
  }, [])

  const value = useMemo(
    () => ({ me, loading, signOutNotice, login, logout, reload }),
    [me, loading, signOutNotice, login, logout, reload],
  )
  return <PortalAuthContext.Provider value={value}>{children}</PortalAuthContext.Provider>
}
