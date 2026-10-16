import { Form, Input, Select, Switch } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { WAREHOUSE_TYPE_LABELS, warehouseApi } from './warehouse-api'
import type { UpdateWarehouseInput, Warehouse, WarehouseType } from './warehouse-api'

interface WarehouseFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: Warehouse | null
  onClose: () => void
  onSaved: () => void
}

const TYPE_OPTIONS = (Object.keys(WAREHOUSE_TYPE_LABELS) as WarehouseType[]).map((value) => ({
  value,
  label: WAREHOUSE_TYPE_LABELS[value],
}))

export function WarehouseFormModal({ open, editing, onClose, onSaved }: WarehouseFormModalProps) {
  const [form] = Form.useForm<UpdateWarehouseInput>()

  const handleSubmit = async (values: UpdateWarehouseInput) => {
    if (editing) {
      await warehouseApi.update(editing.id, values)
    } else {
      // Tạo mới không gửi active: kho mới luôn hoạt động
      await warehouseApi.create({ name: values.name, type: values.type, address: values.address })
    }
    onSaved()
  }

  return (
    <FormModal<UpdateWarehouseInput>
      open={open}
      title={editing ? `Sửa kho ${editing.code}` : 'Thêm kho'}
      form={form}
      initialValues={editing ?? { type: 'CENTRAL' }}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      {/* Rule phía client chỉ để báo lỗi sớm; backend vẫn là nơi kiểm tra cuối cùng */}
      <Form.Item name="name" label="Tên kho" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Kho tổng Hà Nội" />
      </Form.Item>
      <Form.Item name="type" label="Loại kho" rules={[{ required: true }]}>
        <Select options={TYPE_OPTIONS} />
      </Form.Item>
      <Form.Item name="address" label="Địa chỉ" rules={[{ required: true, whitespace: true, max: 500 }]}>
        <Input.TextArea rows={2} />
      </Form.Item>
      {editing && (
        <Form.Item name="active" label="Đang hoạt động" valuePropName="checked">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
