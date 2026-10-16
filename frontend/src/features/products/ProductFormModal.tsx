import { App, Form, Input, InputNumber, Select, Switch } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { FormModal } from '../../components/ui/FormModal'
import { PRODUCT_UNIT_LABELS, categoryApi, productApi } from './product-api'
import type { Category, Product, ProductUnit, UpdateProductInput } from './product-api'

interface ProductFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: Product | null
  onClose: () => void
  onSaved: () => void
}

const UNIT_OPTIONS = (Object.keys(PRODUCT_UNIT_LABELS) as ProductUnit[]).map((value) => ({
  value,
  label: PRODUCT_UNIT_LABELS[value],
}))

export function ProductFormModal({ open, editing, onClose, onSaved }: ProductFormModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<UpdateProductInput>()
  const [categories, setCategories] = useState<Category[]>([])

  // Tải danh mục mỗi lần mở form để có danh mục mới nhất
  useEffect(() => {
    if (!open) return
    categoryApi
      .listForSelect()
      .then((page) => setCategories(page.items))
      .catch((error: unknown) =>
        message.error(error instanceof ApiError ? error.message : 'Không tải được danh mục'),
      )
  }, [open, message])

  // Danh mục ngừng dùng không được chọn mới; nhưng vẫn hiện nếu là danh mục hiện tại của sản phẩm
  const categoryOptions = categories
    .filter((category) => category.active || category.id === editing?.categoryId)
    .map((category) => ({
      value: category.id,
      label: category.active ? category.name : `${category.name} (ngừng dùng)`,
    }))

  const handleSubmit = async (values: UpdateProductInput) => {
    if (editing) {
      await productApi.update(editing.id, values)
    } else {
      const { active: _active, ...createInput } = values
      await productApi.create(createInput)
    }
    onSaved()
  }

  return (
    <FormModal<UpdateProductInput>
      open={open}
      title={editing ? `Sửa sản phẩm ${editing.code}` : 'Thêm sản phẩm'}
      form={form}
      initialValues={editing ?? { unit: 'BAG' }}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item name="name" label="Tên sản phẩm" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Cám heo con 25kg" />
      </Form.Item>
      <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true }]}>
        <Select options={categoryOptions} placeholder="Chọn danh mục" showSearch={{ optionFilterProp: 'label' }} />
      </Form.Item>
      <Form.Item name="unit" label="Đơn vị tính" rules={[{ required: true }]}>
        <Select
          options={UNIT_OPTIONS}
          onChange={(unit: ProductUnit) => {
            // BR-PRD-06: đơn vị KG thì khối lượng một đơn vị luôn là 1 kg
            if (unit === 'KG') form.setFieldValue('netWeightKg', 1)
          }}
        />
      </Form.Item>
      <Form.Item
        name="netWeightKg"
        label="Khối lượng tịnh mỗi đơn vị (kg)"
        rules={[{ required: true }]}
        extra="Bao 25kg nhập 25; đơn vị KG luôn là 1."
      >
        <InputNumber min={0.001} max={9_999_999.999} precision={3} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item name="description" label="Mô tả" rules={[{ max: 1000 }]}>
        <Input.TextArea rows={2} />
      </Form.Item>
      {editing && (
        <Form.Item name="active" label="Đang kinh doanh" valuePropName="checked">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
