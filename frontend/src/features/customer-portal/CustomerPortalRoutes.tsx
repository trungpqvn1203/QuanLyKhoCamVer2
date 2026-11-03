import { Flex, Spin } from 'antd'
import { useEffect } from 'react'
import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AccountPage } from './AccountPage'
import { ChangePasswordForm } from './ChangePasswordForm'
import { MyOrdersPage } from './MyOrdersPage'
import { OrderDetailPage } from './OrderDetailPage'
import { OrderFormPage } from './OrderFormPage'
import { usePortalAuth } from './portal-auth-context'
import { PortalAuthProvider } from './PortalAuthProvider'
import { PortalLayout } from './PortalLayout'
import { PortalLoginPage } from './PortalLoginPage'

/** Đường dẫn gốc của app khách — router.tsx gắn module này ở /khach/* */
const PORTAL_BASE = '/khach'

/**
 * "Thêm vào màn hình chính" (PWA): chỉ gắn manifest khi đang ở app khách, để trang quản trị không bị đề nghị cài
 * thành app. Không có service worker → chưa chạy offline, chưa có thông báo đẩy (BR-POR-Q02).
 */
function usePortalManifest() {
  useEffect(() => {
    const link = document.createElement('link')
    link.rel = 'manifest'
    link.href = '/manifest-khach.webmanifest'
    document.head.appendChild(link)
    return () => link.remove()
  }, [])
}

/** Chưa đăng nhập → trang đăng nhập; mật khẩu do cửa hàng cấp → bắt đổi trước (BR-POR-03). */
function RequirePortalAuth({ children }: { children: ReactNode }) {
  const { me, loading, reload } = usePortalAuth()
  if (loading) {
    return (
      <Flex justify="center" align="center" style={{ minHeight: '100vh' }}>
        <Spin size="large" />
      </Flex>
    )
  }
  if (!me) return <Navigate to={`${PORTAL_BASE}/dang-nhap`} replace />
  if (me.mustChangePassword) {
    return (
      <Flex justify="center" style={{ padding: 16 }}>
        <div style={{ width: '100%', maxWidth: 420 }}>
          <ChangePasswordForm forced onChanged={reload} />
        </div>
      </Flex>
    )
  }
  return children
}

/**
 * App khách (ADR-0005) — tải lười từ router.tsx, nằm NGOÀI nhánh "/" của trang quản trị (nhánh đó bọc phiên nhân
 * viên + menu quản trị). Phiên riêng (PortalAuthProvider), bố cục điện thoại riêng (PortalLayout).
 */
export default function CustomerPortalRoutes() {
  usePortalManifest()
  return (
    <PortalAuthProvider>
      <Routes>
        <Route path="dang-nhap" element={<PortalLoginPage />} />
        <Route
          element={
            <RequirePortalAuth>
              <PortalLayout />
            </RequirePortalAuth>
          }
        >
          <Route index element={<OrderFormPage />} />
          <Route path="don-hang" element={<MyOrdersPage />} />
          <Route path="don-hang/:id" element={<OrderDetailPage />} />
          <Route path="don-hang/:id/sua" element={<OrderFormPage />} />
          <Route path="tai-khoan" element={<AccountPage />} />
        </Route>
        <Route path="*" element={<Navigate to={PORTAL_BASE} replace />} />
      </Routes>
    </PortalAuthProvider>
  )
}
