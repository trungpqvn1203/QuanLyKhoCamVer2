import { AppstoreOutlined, HomeOutlined } from '@ant-design/icons'
import { Breadcrumb, Flex, Layout, Menu, Typography, theme } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { SIDER_DIVIDER } from '../../theme/theme'

const { Sider, Header, Content } = Layout

const MENU_ITEMS = [
  { key: '/products', icon: <AppstoreOutlined />, label: 'Sản phẩm' },
  { key: '/warehouses', icon: <HomeOutlined />, label: 'Kho hàng' },
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
  // Menu sáng đúng mục kể cả khi URL có thêm đoạn con (ví dụ /products/1 sau này)
  const current = MENU_ITEMS.find((item) => pathname.startsWith(item.key))

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
          items={MENU_ITEMS}
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
      </Layout>
    </Layout>
  )
}
