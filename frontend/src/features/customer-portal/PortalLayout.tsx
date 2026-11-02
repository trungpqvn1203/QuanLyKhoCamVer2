import { ShoppingCartOutlined, UnorderedListOutlined, UserOutlined } from '@ant-design/icons'
import { Button, Flex, Typography, theme } from 'antd'
import type { ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { usePortalAuth } from './portal-auth-context'

const BASE = '/khach'

/** Chiều cao thanh điều hướng dưới — trang con chừa khoảng này để nút cuối trang không bị che. */
export const BOTTOM_NAV_HEIGHT = 60

interface NavItem {
  to: string
  label: string
  icon: ReactNode
  /** Đường dẫn hiện tại thuộc mục này không */
  match: (path: string) => boolean
}

const NAV_ITEMS: NavItem[] = [
  { to: BASE, label: 'Đặt hàng', icon: <ShoppingCartOutlined />, match: (path) => path === BASE || path === `${BASE}/` },
  {
    to: `${BASE}/don-hang`,
    label: 'Đơn của tôi',
    icon: <UnorderedListOutlined />,
    match: (path) => path.startsWith(`${BASE}/don-hang`),
  },
  { to: `${BASE}/tai-khoan`, label: 'Tài khoản', icon: <UserOutlined />, match: (path) => path.startsWith(`${BASE}/tai-khoan`) },
]

/**
 * Bố cục điện thoại của app khách: thanh tiêu đề trên, nội dung ở giữa (rộng tối đa 600px — mở trên máy tính
 * vẫn gọn), thanh điều hướng cố định ở dưới cho ngón cái.
 */
export function PortalLayout() {
  const { me } = usePortalAuth()
  const { token } = theme.useToken()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return (
    <div style={{ minHeight: '100vh', background: token.colorBgLayout }}>
      <Flex
        align="center"
        justify="space-between"
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 10,
          background: token.colorBgContainer,
          padding: '12px 16px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Typography.Text strong style={{ fontSize: 16 }}>
          Đặt cám
        </Typography.Text>
        <Typography.Text type="secondary" ellipsis style={{ maxWidth: '60%' }}>
          {me?.customerName}
        </Typography.Text>
      </Flex>
      <main style={{ maxWidth: 600, margin: '0 auto', padding: 16, paddingBottom: BOTTOM_NAV_HEIGHT + 24 }}>
        <Outlet />
      </main>
      <nav
        style={{
          position: 'fixed',
          bottom: 0,
          left: 0,
          right: 0,
          height: BOTTOM_NAV_HEIGHT,
          background: token.colorBgContainer,
          borderTop: `1px solid ${token.colorBorderSecondary}`,
          display: 'flex',
          zIndex: 10,
        }}
      >
        {NAV_ITEMS.map((item) => {
          const active = item.match(pathname)
          return (
            <Button
              key={item.to}
              type="text"
              onClick={() => navigate(item.to)}
              style={{ flex: 1, height: '100%', flexDirection: 'column', gap: 2, borderRadius: 0 }}
              aria-current={active ? 'page' : undefined}
            >
              <span style={{ fontSize: 20, color: active ? token.colorPrimary : undefined }}>{item.icon}</span>
              <Typography.Text style={{ fontSize: 12 }} type={active ? undefined : 'secondary'} strong={active}>
                {item.label}
              </Typography.Text>
            </Button>
          )
        })}
      </nav>
    </div>
  )
}
