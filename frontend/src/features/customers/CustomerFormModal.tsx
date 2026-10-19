import { Form, Input, Select, Switch } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { CUSTOMER_TYPE_OPTIONS, customerApi, isValidPhone } from './customer-api'
import type { Customer, UpdateCustomerInput } from './customer-api'

interface CustomerFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: Customer | null
  onClose: () => void
  /** Nhận bản ghi backend trả về — có mã KH vừa sinh khi tạo mới */
  onSaved: (saved: Customer) => void
}

export function CustomerFormModal({ open, editing, onClose, onSaved }: CustomerFormModalProps) {
  const [form] = Form.useForm<UpdateCustomerInput>()

  const handleSubmit = async ({ active, ...fields }: UpdateCustomerInput) => {
    // Tạo mới không gửi active: khách mới luôn hoạt động.
    // SĐT gửi nguyên văn người dùng gõ — backend chuẩn hóa (BR-CUS-06)
    const saved = editing
      ? await customerApi.update(editing.id, { ...fields, active })
      : await customerApi.create(fields)
    onSaved(saved)
  }

  return (
    <FormModal<UpdateCustomerInput>
      open={open}
      title={editing ? `Sửa khách hàng ${editing.code}` : 'Thêm khách hàng'}
      form={form}
      initialValues={editing ?? undefined}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      {/* Rule phía client chỉ để báo lỗi sớm; backend vẫn là nơi kiểm tra cuối cùng */}
      <Form.Item name="name" label="Tên khách hàng" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Đại lý Minh Phát" />
      </Form.Item>
      <Form.Item name="type" label="Loại khách" rules={[{ required: true, message: 'Chọn loại khách' }]}>
        <Select options={CUSTOMER_TYPE_OPTIONS} placeholder="Chọn loại khách" />
      </Form.Item>
      <Form.Item
        name="phone"
        label="Số điện thoại"
        extra="Mỗi số chỉ dùng cho một khách. Được gõ kèm khoảng trắng, dấu chấm, gạch ngang"
        rules={[
          { required: true, whitespace: true, message: 'Nhập số điện thoại' },
          {
            validator: (_, value?: string) =>
              !value?.trim() || isValidPhone(value)
                ? Promise.resolve()
                : Promise.reject(new Error('Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số')),
          },
        ]}
      >
        <Input placeholder="Ví dụ: 0901 234 567" inputMode="tel" />
      </Form.Item>
      <Form.Item name="contactName" label="Người liên hệ" rules={[{ max: 200 }]}>
        <Input placeholder="Không bắt buộc — chủ đại lý, người nhận hàng" />
      </Form.Item>
      <Form.Item name="address" label="Địa chỉ" rules={[{ max: 500 }]}>
        <Input.TextArea rows={2} placeholder="Không bắt buộc" />
      </Form.Item>
      <Form.Item name="note" label="Ghi chú" rules={[{ max: 1000 }]}>
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
