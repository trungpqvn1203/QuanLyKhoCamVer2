import { Alert, App, Button, Card, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { portalApi } from './portal-api'

interface PasswordValues {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

interface ChangePasswordFormProps {
  /** Lần đầu đăng nhập bằng mật khẩu cửa hàng cấp (BR-POR-03) — chưa đổi thì chưa đặt hàng được */
  forced?: boolean
  onChanged: () => Promise<void> | void
}

/** Đổi mật khẩu app khách. Đổi xong, các thiết bị khác của khách phải đăng nhập lại (BR-POR-05). */
export function ChangePasswordForm({ forced, onChanged }: ChangePasswordFormProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<PasswordValues>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (values: PasswordValues) => {
    setSubmitting(true)
    setError(null)
    try {
      await portalApi.changePassword(values.currentPassword, values.newPassword)
      form.resetFields()
      message.success('Đã đổi mật khẩu')
      await onChanged()
    } catch (err) {
      const fieldErrors = toFormFieldErrors(err)
      if (fieldErrors.length > 0) form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      else setError(err instanceof ApiError ? err.message : 'Không đổi được mật khẩu')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card title={forced ? 'Đặt mật khẩu của bạn' : 'Đổi mật khẩu'}>
      {forced && (
        <Typography.Paragraph type="secondary">
          Bạn đang dùng mật khẩu cửa hàng cấp. Hãy đặt mật khẩu riêng trước khi đặt hàng.
        </Typography.Paragraph>
      )}
      {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }} />}
      <Form<PasswordValues> form={form} layout="vertical" onFinish={submit} disabled={submitting} requiredMark={false}>
        <Form.Item
          name="currentPassword"
          label={forced ? 'Mật khẩu cửa hàng cấp' : 'Mật khẩu hiện tại'}
          rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="Mật khẩu mới"
          rules={[
            { required: true, message: 'Nhập mật khẩu mới' },
            { min: 8, message: 'Tối thiểu 8 ký tự' },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="Nhập lại mật khẩu mới"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Nhập lại mật khẩu mới' },
            ({ getFieldValue }) => ({
              validator: async (_, value?: string) => {
                if (value && value !== getFieldValue('newPassword')) throw new Error('Hai mật khẩu không khớp')
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={submitting}>
          Lưu mật khẩu
        </Button>
      </Form>
    </Card>
  )
}
