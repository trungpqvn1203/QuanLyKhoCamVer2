import { AccountBookOutlined, AppstoreOutlined, CalendarOutlined, DollarOutlined, DownOutlined, ExportOutlined, HistoryOutlined, HomeOutlined, IdcardOutlined, ImportOutlined, KeyOutlined, LogoutOutlined, SafetyOutlined, ShopOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons'
import { App, Breadcrumb, Button, Dropdown, Flex, Layout, Menu, Typography, theme } from 'antd'
import type { MenuProps } from 'antd'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ROLE_LABELS } from '../../features/auth/auth-api'
import { useAuth } from '../../features/auth/auth-context'
import { ChangePasswordModal } from '../../features/auth/ChangePasswordModal'
import { can } from '../../features/auth/permissions'
import type { Permission } from '../../features/auth/permissions'
import { SIDER_DIVIDER } from '../../theme/theme'

const { Sider, Header, Content } = Layout

interface MenuEntry {
  key: string
  icon: ReactNode
  label: string
  /** Không có = mọi vai trò đều xem được */
  permission?: Permission
}

const MENU_ITEMS: MenuEntry[] = [
  { key: '/products', icon: <AppstoreOutlined />, label: 'Sản phẩm' },
  { key: '/warehouses', icon: <HomeOutlined />, label: 'Kho hàng' },
  { key: '/suppliers', icon: <ShopOutlined />, label: 'Nhà cung cấp', permission: 'suppliers' },
  { key: '/customers', icon: <TeamOutlined />, label: 'Khách hàng' },
  { key: '/inbound-receipts', icon: <ImportOutlined />, label: 'Nhập kho', permission: 'inbound' },
  { key: '/sales-orders', icon: <ExportOutlined />, label: 'Bán hàng' },
  { key: '/customer-debts', icon: <AccountBookOutlined />, label: 'Công nợ', permission: 'debts' },
  { key: '/stock-transactions', icon: <HistoryOutlined />, label: 'Lịch sử kho' },
  { key: '/employees', icon: <IdcardOutlined />, label: 'Nhân viên', permission: 'employees' },
  { key: '/attendance', icon: <CalendarOutlined />, label: 'Chấm công', permission: 'attendance' },
  { key: '/payroll', icon: <DollarOutlined />, label: 'Bảng lương', permission: 'payroll' },
  { key: '/users', icon: <SafetyOutlined />, label: 'Người dùng', permission: 'users' },
]

/** Cao bằng Header mặc định của Ant Design để logo thẳng hàng với breadcrumb. */
const HEADER_HEIGHT = 64

/**
 * Khung chung (ADR-0002, tầng 3), chia 3 vùng rõ ràng:
 * sidebar tối | header trắng có viền dưới | nội dung trong khung trắng trên nền xám.
 * Cả khung cao đúng 100vh để bảng cuộn bên trong, không cuộn cả trang.
 */
export function AppLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { token } = theme.useToken()
  const { message } = App.useApp()
  const { user, logout } = useAuth()
  const [changingPassword, setChangingPassword] = useState(false)
  // Chỉ hiện mục người dùng được phép — ẩn cho gọn; quyền thật do backend kiểm
  const menuItems = MENU_ITEMS.filter((item) => !item.permission || can(user, item.permission)).map(
    ({ key, icon, label }) => ({ key, icon, label }),
  )
  // Menu sáng đúng mục kể cả khi URL có thêm đoạn con (ví dụ /products/1 sau này)
  const current = MENU_ITEMS.find((item) => pathname.startsWith(item.key))

  const userMenu: MenuProps = {
    items: [
      {
        key: 'info',
        disabled: true,
        label: (
          <Flex vertical>
            <Typography.Text strong>{user?.fullName}</Typography.Text>
            <Typography.Text type="secondary">
              {user?.username} · {user?.roles.map((role) => ROLE_LABELS[role]).join(', ')}
            </Typography.Text>
          </Flex>
        ),
      },
      { type: 'divider' },
      { key: 'password', icon: <KeyOutlined />, label: 'Đổi mật khẩu' },
      { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'password') setChangingPassword(true)
      if (key === 'logout') void logout()
    },
  }

  return (
    <Layout style={{ height: '100vh' }}>
      {/* Dưới 992px sidebar thu lại; nút mở hiện ở mép trái */}
      <Sider breakpoint="lg" collapsedWidth={0} width={224}>
        <Flex
          align="center"
          gap={10}
          style={{
            height: HEADER_HEIGHT,
            paddingInline: 20,
            marginBottom: 8,
            borderBottom: `1px solid ${SIDER_DIVIDER}`,
          }}
        >
          <Flex
            align="center"
            justify="center"
            style={{
              width: 32,
              height: 32,
              borderRadius: token.borderRadius,
              background: token.colorPrimary,
              color: token.colorWhite,
              fontWeight: 700,
            }}
          >
            KC
          </Flex>
          <Typography.Text strong style={{ color: token.colorWhite, fontSize: token.fontSizeLG }}>
            Kho Cám
          </Typography.Text>
        </Flex>
        <Menu
          theme="dark"
          mode="inline"
          items={menuItems}
          selectedKeys={current ? [current.key] : []}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            height: HEADER_HEIGHT,
            paddingInline: token.paddingLG,
            display: 'flex',
            alignItems: 'center',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Breadcrumb items={[{ title: 'Kho Cám' }, ...(current ? [{ title: current.label }] : [])]} />
          <Dropdown menu={userMenu} trigger={['click']} placement="bottomRight">
            <Button type="text" icon={<UserOutlined />} style={{ marginInlineStart: 'auto' }}>
              {user?.fullName} <DownOutlined />
            </Button>
          </Dropdown>
        </Header>
        <Content style={{ padding: token.paddingLG, overflow: 'auto' }}>
          <div
            style={{
              background: token.colorBgContainer,
              borderRadius: token.borderRadiusLG,
              border: `1px solid ${token.colorBorderSecondary}`,
              padding: token.paddingLG,
            }}
          >
            <Outlet />
          </div>
        </Content>
        <ChangePasswordModal
          open={changingPassword}
          onClose={() => setChangingPassword(false)}
          onChanged={() => {
            setChangingPassword(false)
            message.success('Đã đổi mật khẩu. Các thiết bị khác phải đăng nhập lại')
          }}
        />
      </Layout>
    </Layout>
  )
}
