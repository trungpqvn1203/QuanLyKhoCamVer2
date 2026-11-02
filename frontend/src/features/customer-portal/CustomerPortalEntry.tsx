import { Flex, Spin } from 'antd'
import { Suspense, lazy } from 'react'

// Tải lười (ADR-0005): nhân viên mở trang quản trị không tải code của app khách và ngược lại
const CustomerPortalRoutes = lazy(() => import('./CustomerPortalRoutes'))

/** Điểm vào app khách cho router.tsx (route /khach/*). */
export function CustomerPortalEntry() {
  return (
    <Suspense
      fallback={
        <Flex justify="center" align="center" style={{ minHeight: '100vh' }}>
          <Spin size="large" />
        </Flex>
      }
    >
      <CustomerPortalRoutes />
    </Suspense>
  )
}
