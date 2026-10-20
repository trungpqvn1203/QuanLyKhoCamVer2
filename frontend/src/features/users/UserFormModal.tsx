import { Checkbox, Form, Input, Switch, Typography } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { ROLE_LABELS } from '../auth/auth-api'
import type { Role } from '../auth/auth-api'
import { MIN_PASSWORD_LENGTH } from '../auth/ChangePasswordModal'
import { userApi } from './user-api'
import type { User } from './user-api'

const ROLE_OPTIONS = (Object.keys(ROLE_LABELS) as Role[]).map((role) => ({ value: role, label: ROLE_LABELS[role] }))

/** Cùng quy tắc với PasswordPolicy ở backend: chữ thường không dấu, số, . _ - (gõ hoa vẫn được, backend đổi về thường). */
const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,50}$/

interface UserFormValues {
  username: string
  fullName: string
  password: string
  roles: Role[]
  active: boolean
}

interface UserFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: User | null
  onClose: () => void
  onSaved: (saved: User) => void
}

export function UserFormModal({ open, editing, onClose, onSaved }: UserFormModalProps) {
  const [form] = Form.useForm<UserFormValues>()

  const submit = async ({ username, fullName, password, roles, active }: UserFormValues) => {
    const saved = editing
      ? await userApi.update(editing.id, { fullName, roles, active })
      : await userApi.create({ username, fullName, password, roles })
    onSaved(saved)
  }

  return (
    <FormModal<UserFormValues>
      open={open}
      title={editing ? `Sửa tài khoản ${editing.username}` : 'Thêm tài khoản'}
      form={form}
      initialValues={editing ?? { roles: [], active: true }}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={submit}
      onClose={onClose}
    >
      {editing ? (
        <Form.Item label="Tên đăng nhập">
          <Typography.Text strong>{editing.username}</Typography.Text>
        </Form.Item>
      ) : (
        <Form.Item
          name="username"
          label="Tên đăng nhập"
          extra="3–50 ký tự: chữ không dấu, số, dấu chấm, gạch dưới, gạch ngang. Không đổi được sau khi tạo"
          rules={[
            { required: true, whitespace: true, message: 'Nhập tên đăng nhập' },
            { pattern: USERNAME_PATTERN, message: 'Chỉ gồm chữ không dấu, số, . _ - (3–50 ký tự)' },
          ]}
        >
          <Input placeholder="Ví dụ: lan.bh" autoComplete="off" />
        </Form.Item>
      )}
      <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, whitespace: true, max: 100 }]}>
        <Input placeholder="Ví dụ: Nguyễn Thị Lan" />
      </Form.Item>
      {!editing && (
        <Form.Item
          name="password"
          label="Mật khẩu ban đầu"
          extra="Gửi riêng cho người dùng; họ tự đổi sau khi đăng nhập"
          rules={[
            { required: true, message: 'Nhập mật khẩu' },
            { min: MIN_PASSWORD_LENGTH, message: `Tối thiểu ${MIN_PASSWORD_LENGTH} ký tự` },
          ]}
        >
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      )}
      <Form.Item
        name="roles"
        label="Vai trò"
        extra={editing ? 'Đổi vai trò → người này phải đăng nhập lại (quyền cũ còn tối đa 15 phút)' : 'Chọn được nhiều vai trò'}
        rules={[{ required: true, type: 'array', min: 1, message: 'Chọn ít nhất một vai trò' }]}
      >
        <Checkbox.Group options={ROLE_OPTIONS} />
      </Form.Item>
      {editing && (
        <Form.Item name="active" label="Đang hoạt động" valuePropName="checked" extra="Khóa → không đăng nhập / gia hạn được nữa">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
