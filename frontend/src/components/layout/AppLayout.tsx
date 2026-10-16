import { AppstoreOutlined, HomeOutlined } from '@ant-design/icons'
import { Layout, Menu, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

const { Sider, Header, Content } = Layout

const MENU_ITEMS = [
  { key: '/products', icon: <AppstoreOutlined />, label: 'Sản phẩm' },
  { key: '/warehouses', icon: <HomeOutlined />, label: 'Kho hàng' },
]

/** Khung chung: sidebar + header + vùng nội dung (ADR-0002, tầng 3). */
export function AppLayout() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  // Menu sáng đúng mục kể cả khi URL có thêm đoạn con (ví dụ /products/1 sau này)
  const selectedKey = MENU_ITEMS.find((item) => pathname.startsWith(item.key))?.key

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={0} width={220} theme="light">
        <Typography.Title level={4} style={{ padding: '16px 24px', margin: 0 }}>
          Kho Cám
        </Typography.Title>
        <Menu
          mode="inline"
          items={MENU_ITEMS}
          selectedKeys={selectedKey ? [selectedKey] : []}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ paddingInline: 24 }}>
          <Typography.Text type="secondary">Hệ thống quản lý chuỗi phân phối cám</Typography.Text>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
