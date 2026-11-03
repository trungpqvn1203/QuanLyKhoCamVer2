import { Form, Input, InputNumber, Switch } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { vehicleApi } from './vehicle-api'
import type { Vehicle, VehicleInput } from './vehicle-api'

interface VehicleFormModalProps {
  open: boolean
  /** null = thêm mới */
  editing: Vehicle | null
  onClose: () => void
  onSaved: (saved: Vehicle) => void
}

/** Thêm / sửa xe (BR-VEH-01, 02). Biển số backend tự viết hoa và kiểm trùng (kể cả gõ khác cách). */
export function VehicleFormModal({ open, editing, onClose, onSaved }: VehicleFormModalProps) {
  const [form] = Form.useForm<VehicleInput>()

  const handleSubmit = async (values: VehicleInput) => {
    const input: VehicleInput = {
      plateNumber: values.plateNumber,
      name: values.name?.trim() || undefined,
      // InputNumber để trống trả null — không gửi = xe không ghi tải trọng
      capacityTons: values.capacityTons ?? undefined,
    }
    onSaved(editing ? await vehicleApi.update(editing.id, { ...input, active: values.active }) : await vehicleApi.create(input))
  }

  return (
    <FormModal<VehicleInput>
      open={open}
      title={editing ? `Sửa xe ${editing.plateNumber}` : 'Thêm xe'}
      form={form}
      initialValues={editing ?? {}}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item name="plateNumber" label="Biển số" rules={[{ required: true, whitespace: true, max: 20 }]}>
        <Input placeholder="Ví dụ: 29H-123.45" style={{ textTransform: 'uppercase' }} />
      </Form.Item>
      <Form.Item name="name" label="Tên xe" rules={[{ max: 100 }]}>
        <Input placeholder="Ví dụ: Xe tải Hino 5 tấn" />
      </Form.Item>
      <Form.Item
        name="capacityTons"
        label="Tải trọng (tấn)"
        tooltip="Chuyến vượt tải trọng sẽ được cảnh báo (vẫn cho xuất). Để trống nếu không cần cảnh báo"
      >
        <InputNumber min={0.001} max={999.999} step={0.5} style={{ width: '100%' }} />
      </Form.Item>
      {editing && (
        <Form.Item name="active" label="Đang dùng" valuePropName="checked">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
