import { LockOutlined, PhoneOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Flex, Form, Input, Typography, theme } from 'antd'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { usePortalAuth } from './portal-auth-context'

interface LoginValues {
  phone: string
  password: string
}

/**
 * Đăng nhập app khách bằng SĐT + mật khẩu cửa hàng cấp (BR-POR-01, 02). Chưa có tài khoản / quên mật khẩu:
 * gọi cửa hàng (BR-POR-Q01 — chưa có tự đăng ký).
 */
export function PortalLoginPage() {
  const { me, loading, signOutNotice, login } = usePortalAuth()
  const navigate = useNavigate()
  const { token } = theme.useToken()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!loading && me) return <Navigate to="/khach" replace />

  const submit = async (values: LoginValues) => {
    setSubmitting(true)
    setError(null)
    try {
      await login(values.phone.trim(), values.password)
      navigate('/khach', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không đăng nhập được, vui lòng thử lại')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Flex justify="center" align="center" style={{ minHeight: '100vh', padding: 16, background: token.colorBgLayout }}>
      <Card style={{ width: '100%', maxWidth: 400 }}>
        <Flex vertical gap={4} style={{ marginBottom: 24 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Đặt cám
          </Typography.Title>
          <Typography.Text type="secondary">Đăng nhập bằng số điện thoại đã đăng ký với cửa hàng</Typography.Text>
        </Flex>
        {signOutNotice && <Alert type="info" showIcon title={signOutNotice} style={{ marginBottom: 16 }} />}
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
        <Form<LoginValues> layout="vertical" onFinish={submit} requiredMark={false} disabled={submitting}>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, whitespace: true, message: 'Nhập số điện thoại' }]}>
            <Input prefix={<PhoneOutlined />} inputMode="tel" autoComplete="username" size="large" placeholder="0901 234 567" />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting}>
            Đăng nhập
          </Button>
        </Form>
        <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0, fontSize: 13 }}>
          Chưa có tài khoản hoặc quên mật khẩu? Gọi cửa hàng để được cấp mật khẩu.
        </Typography.Paragraph>
      </Card>
    </Flex>
  )
}
