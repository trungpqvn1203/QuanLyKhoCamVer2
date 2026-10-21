import { Button, Flex, Result, Spin } from 'antd'
import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth, useCan } from './auth-context'
import type { Permission } from './permissions'
import { HOME_PATH } from './redirect'

/** Chưa đăng nhập → /login?redirect=<trang đang mở>. Đang hỏi backend thì chờ, chưa chuyển hướng vội. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <Flex align="center" justify="center" style={{ minHeight: '100vh' }}>
        <Spin size="large" />
      </Flex>
    )
  }
  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  return children
}

/**
 * Trang dành cho một số vai trò (ví dụ Bảng lương). Chỉ để báo rõ cho người dùng — kể cả không có lớp này,
 * gõ thẳng URL thì API vẫn trả 403 vì backend tự kiểm quyền.
 */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  if (!useCan(permission)) {
    return (
      <Result
        status="403"
        title="Không có quyền truy cập"
        subTitle="Vai trò của bạn không được dùng chức năng này. Liên hệ quản lý hệ thống nếu cần."
        extra={
          <Link to={HOME_PATH}>
            <Button type="primary">Về trang sản phẩm</Button>
          </Link>
        }
      />
    )
  }
  return children
}
