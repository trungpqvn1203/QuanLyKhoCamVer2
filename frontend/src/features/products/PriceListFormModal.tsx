import { Form, Input, Switch } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { priceListApi } from './price-list-api'
import type { PriceList, UpdatePriceListInput } from './price-list-api'

interface PriceListFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: PriceList | null
  onClose: () => void
  onSaved: (saved: PriceList) => void
}

/** Form tạo mới / sửa thông tin một Bảng giá bán (BR-PRC-01..03). */
export function PriceListFormModal({ open, editing, onClose, onSaved }: PriceListFormModalProps) {
  const [form] = Form.useForm<UpdatePriceListInput>()

  const handleSubmit = async ({ active, ...fields }: UpdatePriceListInput) => {
    const saved = editing
      ? await priceListApi.update(editing.id, { ...fields, active })
      : await priceListApi.create(fields)
    onSaved(saved)
  }

  return (
    <FormModal<UpdatePriceListInput>
      open={open}
      title={editing ? `Sửa bảng giá "${editing.name}"` : 'Thêm bảng giá bán'}
      form={form}
      initialValues={editing ?? undefined}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item
        name="name"
        label="Tên bảng giá"
        rules={[{ required: true, whitespace: true, max: 100, message: 'Nhập tên bảng giá (tối đa 100 ký tự)' }]}
      >
        <Input placeholder="Ví dụ: Giá lẻ, Giá đại lý, Giá trang trại…" />
      </Form.Item>
      <Form.Item name="description" label="Mô tả (tùy chọn)" rules={[{ max: 500 }]}>
        <Input.TextArea rows={2} placeholder="Ví dụ: Áp dụng cho khách mua tại cửa hàng hoặc đại lý cấp 1" />
      </Form.Item>
      {editing && (
        <Form.Item name="active" label="Đang áp dụng" valuePropName="checked">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
