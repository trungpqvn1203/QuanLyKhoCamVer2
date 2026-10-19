import { Form, Input, Switch } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { supplierApi } from './supplier-api'
import type { Supplier, UpdateSupplierInput } from './supplier-api'

interface SupplierFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: Supplier | null
  onClose: () => void
  /** Nhận bản ghi backend trả về — có mã NCC vừa sinh khi tạo mới */
  onSaved: (saved: Supplier) => void
}

export function SupplierFormModal({ open, editing, onClose, onSaved }: SupplierFormModalProps) {
  const [form] = Form.useForm<UpdateSupplierInput>()

  const handleSubmit = async ({ active, ...fields }: UpdateSupplierInput) => {
    // Tạo mới không gửi active: NCC mới luôn hoạt động.
    // Ô tùy chọn để trống gửi "" hoặc không gửi — backend đều lưu NULL (BR-SUP-01)
    const saved = editing
      ? await supplierApi.update(editing.id, { ...fields, active })
      : await supplierApi.create(fields)
    onSaved(saved)
  }

  return (
    <FormModal<UpdateSupplierInput>
      open={open}
      title={editing ? `Sửa nhà cung cấp ${editing.code}` : 'Thêm nhà cung cấp'}
      form={form}
      initialValues={editing ?? undefined}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      {/* Rule phía client chỉ để báo lỗi sớm; backend vẫn là nơi kiểm tra cuối cùng */}
      <Form.Item name="name" label="Tên nhà cung cấp" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Công ty CP Thức ăn chăn nuôi Việt" />
      </Form.Item>
      <Form.Item name="contactName" label="Người liên hệ" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Họ tên người phụ trách" />
      </Form.Item>
      <Form.Item name="phone" label="Số điện thoại" rules={[{ max: 20 }]}>
        <Input placeholder="Không bắt buộc" />
      </Form.Item>
      <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không đúng định dạng' }, { max: 254 }]}>
        <Input placeholder="Không bắt buộc" />
      </Form.Item>
      <Form.Item name="address" label="Địa chỉ" rules={[{ max: 500 }]}>
        <Input.TextArea rows={2} placeholder="Không bắt buộc" />
      </Form.Item>
      {editing && (
        <Form.Item name="active" label="Đang hoạt động" valuePropName="checked">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
