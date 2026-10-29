import { AccountBookOutlined, AppstoreOutlined, AuditOutlined, BankOutlined, BarChartOutlined, CalendarOutlined, DashboardOutlined, DollarOutlined, DownOutlined, ExportOutlined, FlagOutlined, HistoryOutlined, HomeOutlined, IdcardOutlined, ImportOutlined, InboxOutlined, KeyOutlined, LogoutOutlined, SafetyOutlined, ShopOutlined, ShoppingOutlined, TeamOutlined, UserOutlined, UsergroupAddOutlined, WalletOutlined } from '@ant-design/icons'
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
import { StoreInfoModal } from '../../features/store/StoreInfoModal'
import { SIDER_DIVIDER } from '../../theme/theme'

const { Sider, Header, Content } = Layout

interface MenuEntry {
  key: string
  icon?: ReactNode
  label: string
  /** Không có = mọi vai trò đều xem được */
  permission?: Permission
  /** Có = nhóm xổ xuống; key của nhóm không phải đường dẫn */
  children?: MenuEntry[]
  /** Kẻ một đường phía trên — tách phần thiết lập (làm một lần) khỏi các mục dùng hằng ngày */
  dividerBefore?: boolean
}

const MENU_ITEMS: MenuEntry[] = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Tổng quan' },
  { key: '/products', icon: <AppstoreOutlined />, label: 'Sản phẩm' },
  { key: '/warehouses', icon: <HomeOutlined />, label: 'Kho hàng' },
  { key: '/suppliers', icon: <ShopOutlined />, label: 'Nhà cung cấp', permission: 'suppliers' },
  { key: '/customers', icon: <TeamOutlined />, label: 'Khách hàng' },
  { key: '/inbound-receipts', icon: <ImportOutlined />, label: 'Nhập kho', permission: 'inbound' },
  { key: '/sales-orders', icon: <ExportOutlined />, label: 'Bán hàng' },
  { key: '/customer-debts', icon: <AccountBookOutlined />, label: 'Công nợ khách', permission: 'debts' },
  // Cùng quyền với phiếu nhập: quản lý kho trả tiền NCC (BR-SPAY-06)
  { key: '/supplier-debts', icon: <WalletOutlined />, label: 'Công nợ NCC', permission: 'inbound' },
  { key: '/stock-transactions', icon: <HistoryOutlined />, label: 'Lịch sử kho' },
  { key: '/reports', icon: <BarChartOutlined />, label: 'Báo cáo tồn kho' },
  { key: '/stock-counts', icon: <AuditOutlined />, label: 'Kiểm kê', permission: 'stockcount' },
  { key: '/employees', icon: <IdcardOutlined />, label: 'Nhân viên', permission: 'employees' },
  { key: '/attendance', icon: <CalendarOutlined />, label: 'Chấm công', permission: 'attendance' },
  { key: '/payroll', icon: <DollarOutlined />, label: 'Bảng lương', permission: 'payroll' },
  // Cùng quyền với bảng lương: chỉ quản lý hệ thống (BR-EPAY-06)
  { key: '/employee-debts', icon: <UsergroupAddOutlined />, label: 'Công nợ NV', permission: 'payroll' },
  { key: '/cash-book', icon: <BankOutlined />, label: 'Sổ quỹ', permission: 'cashbook' },
  {
    key: 'opening',
    icon: <FlagOutlined />,
    label: 'Số dư đầu kỳ',
    dividerBefore: true,
    // Tên ngắn (nhóm đã nói "đầu kỳ"), icon trùng mục dùng hằng ngày tương ứng để dễ nhận ra; tiêu đề trang vẫn đầy đủ
    children: [
      // Cùng quyền với sổ quỹ (BR-CASH-07)
      { key: '/opening/cash', icon: <BankOutlined />, label: 'Quỹ tiền', permission: 'cashbook' },
      { key: '/opening/stocks', icon: <InboxOutlined />, label: 'Tồn kho', permission: 'opening' },
      { key: '/opening/customer-debts', icon: <AccountBookOutlined />, label: 'Công nợ khách', permission: 'opening' },
      { key: '/opening/supplier-debts', icon: <WalletOutlined />, label: 'Công nợ NCC', permission: 'opening' },
    ],
  },
  { key: '/users', icon: <SafetyOutlined />, label: 'Người dùng', permission: 'users' },
]

/** Mục người dùng được phép; nhóm không còn mục con nào thì ẩn luôn nhóm. */
function visibleEntries(entries: MenuEntry[], allowed: (permission: Permission) => boolean): MenuEntry[] {
  return entries.flatMap((entry) => {
    if (entry.permission && !allowed(entry.permission)) return []
    if (!entry.children) return [entry]
    const children = visibleEntries(entry.children, allowed)
    return children.length > 0 ? [{ ...entry, children }] : []
  })
}

function toMenuItems(entries: MenuEntry[]): NonNullable<MenuProps['items']> {
  return entries.flatMap(({ key, icon, label, children, dividerBefore }) => {
    const item = children ? { key, icon, label, children: toMenuItems(children) } : { key, icon, label }
    return dividerBefore ? [{ type: 'divider' as const, key: `${key}-divider` }, item] : [item]
  })
}

/** Mục trang đang mở (khớp cả URL có thêm đoạn con) kèm nhóm chứa nó, để sáng menu và dựng breadcrumb. */
function findCurrent(entries: MenuEntry[], pathname: string, group?: MenuEntry): { entry: MenuEntry; group?: MenuEntry } | null {
  for (const entry of entries) {
    if (entry.children) {
      const found = findCurrent(entry.children, pathname, entry)
      if (found) return found
    } else if (pathname.startsWith(entry.key)) {
      return { entry, group }
    }
  }
  return null
}

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
  const [editingStore, setEditingStore] = useState(false)
  // Chỉ hiện mục người dùng được phép — ẩn cho gọn; quyền thật do backend kiểm
  const entries = visibleEntries(MENU_ITEMS, (permission) => can(user, permission))
  const menuItems = toMenuItems(entries)
  // Menu sáng đúng mục kể cả khi URL có thêm đoạn con (ví dụ /products/1 sau này)
  const current = findCurrent(entries, pathname)

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
      // Cấu hình ít khi sửa → để trong menu tài khoản, không thêm mục navbar
      ...(can(user, 'store.write')
        ? [{ key: 'store', icon: <ShoppingOutlined />, label: 'Thông tin cửa hàng' }]
        : []),
      { key: 'password', icon: <KeyOutlined />, label: 'Đổi mật khẩu' },
      { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'store') setEditingStore(true)
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
          selectedKeys={current ? [current.entry.key] : []}
          // Mở sẵn nhóm chứa trang đang xem (vào thẳng bằng URL vẫn thấy mục sáng)
          defaultOpenKeys={current?.group ? [current.group.key] : []}
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
          <Breadcrumb
            items={[
              { title: 'Kho Cám' },
              ...(current?.group ? [{ title: current.group.label }] : []),
              ...(current ? [{ title: current.entry.label }] : []),
            ]}
          />
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
        <StoreInfoModal
          open={editingStore}
          onClose={() => setEditingStore(false)}
          onSaved={() => {
            setEditingStore(false)
            message.success('Đã lưu thông tin cửa hàng')
          }}
        />
      </Layout>
    </Layout>
  )
}
