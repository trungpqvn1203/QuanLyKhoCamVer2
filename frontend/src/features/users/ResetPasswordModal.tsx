import { Form, Input } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { MIN_PASSWORD_LENGTH } from '../auth/ChangePasswordModal'
import { userApi } from './user-api'
import type { User } from './user-api'

interface ResetPasswordModalProps {
  user: User | null
  onClose: () => void
  onDone: (user: User) => void
}

/** Người dùng quên mật khẩu: quản lý đặt mật khẩu mới; mọi phiên cũ của người đó bị thu hồi. */
export function ResetPasswordModal({ user, onClose, onDone }: ResetPasswordModalProps) {
  const [form] = Form.useForm<{ newPassword: string }>()

  const submit = async ({ newPassword }: { newPassword: string }) => {
    if (!user) return
    await userApi.resetPassword(user.id, newPassword)
    form.resetFields()
    onDone(user)
  }

  return (
    <FormModal<{ newPassword: string }>
      open={user !== null}
      title={`Đặt lại mật khẩu cho ${user?.username ?? ''}`}
      form={form}
      submitText="Đặt lại mật khẩu"
      onSubmit={submit}
      onClose={() => {
        form.resetFields()
        onClose()
      }}
    >
      <Form.Item
        name="newPassword"
        label="Mật khẩu mới"
        extra="Người dùng sẽ bị đăng xuất khỏi mọi thiết bị"
        rules={[
          { required: true, message: 'Nhập mật khẩu mới' },
          { min: MIN_PASSWORD_LENGTH, message: `Tối thiểu ${MIN_PASSWORD_LENGTH} ký tự` },
        ]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
    </FormModal>
  )
}
