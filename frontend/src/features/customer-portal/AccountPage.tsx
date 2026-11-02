import { LogoutOutlined } from '@ant-design/icons'
import { Button, Card, Descriptions, Flex } from 'antd'
import { useNavigate } from 'react-router-dom'
import { ChangePasswordForm } from './ChangePasswordForm'
import { usePortalAuth } from './portal-auth-context'

/** Thông tin khách, đổi mật khẩu, đăng xuất. Đổi SĐT / tên: gọi cửa hàng (dữ liệu khách do cửa hàng quản lý). */
export function AccountPage() {
  const { me, logout, reload } = usePortalAuth()
  const navigate = useNavigate()
  if (!me) return null

  const signOut = async () => {
    await logout()
    navigate('/khach/dang-nhap', { replace: true })
  }

  return (
    <Flex vertical gap={12}>
      <Card size="small" title="Tài khoản">
        <Descriptions
          size="small"
          column={1}
          items={[
            { key: 'name', label: 'Khách hàng', children: me.customerName },
            { key: 'code', label: 'Mã khách', children: me.customerCode },
            { key: 'phone', label: 'Số điện thoại', children: me.phone },
          ]}
        />
      </Card>
      <ChangePasswordForm onChanged={reload} />
      <Button danger block icon={<LogoutOutlined />} onClick={() => void signOut()}>
        Đăng xuất
      </Button>
    </Flex>
  )
}
