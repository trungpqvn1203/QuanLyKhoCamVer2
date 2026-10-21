import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Flex, Form, Input, Typography, theme } from 'antd'
import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { authApi } from './auth-api'
import { useAuth } from './auth-context'
import { safeRedirect } from './redirect'

interface LoginValues {
  username: string
  password: string
}

/** Trang đăng nhập — ngoài khung AppLayout. Đăng nhập xong quay lại trang đang xem dở (?redirect=). */
export function LoginPage() {
  const { token } = theme.useToken()
  const { user, loading, sessionExpired, login } = useAuth()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const target = safeRedirect(searchParams.get('redirect'))

  // Lấy cookie XSRF-TOKEN trước: chính request đăng nhập cũng phải có CSRF (chống "login CSRF")
  useEffect(() => {
    authApi.csrf().catch(() => {
      // lỗi mạng sẽ hiện khi bấm đăng nhập
    })
  }, [])

  if (!loading && user) return <Navigate to={target} replace />

  const submit = async ({ username, password }: LoginValues) => {
    setSubmitting(true)
    setError(null)
    try {
      await login(username, password)
      navigate(target, { replace: true })
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'Không đăng nhập được, vui lòng thử lại')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Flex align="center" justify="center" style={{ minHeight: '100vh', background: token.colorBgLayout, padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Flex vertical gap={4} style={{ marginBottom: 24 }}>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Kho Cám
          </Typography.Title>
          <Typography.Text type="secondary">Đăng nhập để tiếp tục</Typography.Text>
        </Flex>
        {sessionExpired && !error && (
          <Alert type="info" showIcon message="Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại" style={{ marginBottom: 16 }} />
        )}
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form<LoginValues> layout="vertical" onFinish={submit} requiredMark={false} disabled={submitting}>
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, whitespace: true, message: 'Nhập tên đăng nhập' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" autoFocus />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            Đăng nhập
          </Button>
        </Form>
      </Card>
    </Flex>
  )
}
