import { Form, Input } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { authApi } from './auth-api'

/** Cùng ngưỡng với PasswordPolicy ở backend (backend vẫn kiểm lại, kể cả giới hạn 72 byte). */
export const MIN_PASSWORD_LENGTH = 8

interface ChangePasswordValues {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

interface ChangePasswordModalProps {
  open: boolean
  onClose: () => void
  onChanged: () => void
}

/** Đổi mật khẩu của chính mình. Xong thì các thiết bị KHÁC phải đăng nhập lại; thiết bị này giữ phiên. */
export function ChangePasswordModal({ open, onClose, onChanged }: ChangePasswordModalProps) {
  const [form] = Form.useForm<ChangePasswordValues>()

  const submit = async ({ currentPassword, newPassword }: ChangePasswordValues) => {
    await authApi.changePassword(currentPassword, newPassword)
    form.resetFields()
    onChanged()
  }

  return (
    <FormModal<ChangePasswordValues>
      open={open}
      title="Đổi mật khẩu"
      form={form}
      submitText="Đổi mật khẩu"
      onSubmit={submit}
      onClose={() => {
        form.resetFields()
        onClose()
      }}
    >
      <Form.Item name="currentPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}>
        <Input.Password autoComplete="current-password" />
      </Form.Item>
      <Form.Item
        name="newPassword"
        label="Mật khẩu mới"
        rules={[
          { required: true, message: 'Nhập mật khẩu mới' },
          { min: MIN_PASSWORD_LENGTH, message: `Tối thiểu ${MIN_PASSWORD_LENGTH} ký tự` },
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
              if (value && value !== getFieldValue('newPassword')) throw new Error('Hai lần nhập không khớp')
            },
          }),
        ]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
    </FormModal>
  )
}
