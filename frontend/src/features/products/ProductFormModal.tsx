import { App, Form, Input, InputNumber, Select, Switch, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { FormModal } from '../../components/ui/FormModal'
import { supplierApi } from '../suppliers/supplier-api'
import type { ProductSupplierLink, Supplier } from '../suppliers/supplier-api'
import { categoryApi, productApi, productUnitApi } from './product-api'
import type { Category, Product, ProductUnit, UpdateProductInput } from './product-api'

interface ProductFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: Product | null
  onClose: () => void
  /** Nhận bản ghi backend trả về — có mã SP vừa sinh khi tạo mới */
  onSaved: (saved: Product) => void
}

/**
 * Form chọn MỘT NCC (BR-PRD-10, cho đơn giản); API vẫn nhận mảng supplierIds để sau này mở rộng chọn nhiều
 * mà không phải đổi backend.
 */
type ProductFormValues = Omit<UpdateProductInput, 'supplierIds'> & { supplierId?: number | null }

export function ProductFormModal({ open, editing, onClose, onSaved }: ProductFormModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<ProductFormValues>()
  const [categories, setCategories] = useState<Category[]>([])
  const [units, setUnits] = useState<ProductUnit[]>([])
  const [suppliers, setSuppliers] = useState<Supplier[]>([])
  // NCC đang gán của SP đang sửa; kèm productId để không dùng nhầm dữ liệu của SP mở trước đó
  const [linked, setLinked] = useState<{ productId: number; items: ProductSupplierLink[] } | null>(null)
  const linkedItems = editing && linked?.productId === editing.id ? linked.items : null
  // SP có nhiều NCC (gán thêm ở màn NCC) → form chỉ hiện, không sửa, để không gỡ nhầm NCC thứ hai trở đi.
  // Sửa SP mà chưa tải xong / tải lỗi NCC đang gán → cũng không sửa (không gửi supplierIds = giữ nguyên)
  const supplierEditable = !editing || (linkedItems != null && linkedItems.length <= 1)

  // Tải danh mục, đơn vị, NCC mỗi lần mở form để có bản mới nhất (vừa thêm ở khung Cấu hình / màn NCC)
  useEffect(() => {
    if (!open) return
    const failed = (fallback: string) => (error: unknown) =>
      message.error(error instanceof ApiError ? error.message : fallback)
    categoryApi
      .listForSelect()
      .then((page) => setCategories(page.items))
      .catch(failed('Không tải được danh mục'))
    productUnitApi
      .listForSelect()
      .then((page) => setUnits(page.items))
      .catch(failed('Không tải được đơn vị tính'))
    // Chỉ NCC đang hoạt động mới gán mới được (BR-SUP-06); 100 là giới hạn trang của backend
    supplierApi
      .list({ page: 0, size: 100 }, { active: true })
      .then((page) => setSuppliers(page.items))
      .catch(failed('Không tải được nhà cung cấp'))
    if (editing) {
      supplierApi
        .listSuppliersOfProduct(editing.id, { page: 0, size: 100 })
        .then((page) => {
          setLinked({ productId: editing.id, items: page.items })
          if (page.items.length === 1) form.setFieldValue('supplierId', page.items[0].supplierId)
        })
        .catch(failed('Không tải được nhà cung cấp của sản phẩm'))
    }
  }, [open, editing, form, message])

  // Danh mục ngừng dùng không được chọn mới; nhưng vẫn hiện nếu là danh mục hiện tại của sản phẩm
  const categoryOptions = categories
    .filter((category) => category.active || category.id === editing?.categoryId)
    .map((category) => ({
      value: category.id,
      label: category.active ? category.name : `${category.name} (ngừng dùng)`,
    }))

  // Cùng quy tắc với danh mục
  const unitOptions = units
    .filter((unit) => unit.active || unit.id === editing?.unitId)
    .map((unit) => ({ value: unit.id, label: unit.active ? unit.name : `${unit.name} (ngừng dùng)` }))

  // NCC đã gán mà nay ngừng hoạt động vẫn hiện để giữ hoặc bỏ (BR-SUP-07); NCC ngừng chưa gán thì không có trong
  // danh sách nên không gán mới được (BR-SUP-06)
  const supplierOptions = [
    ...suppliers.map((supplier) => ({ value: supplier.id, label: `${supplier.code} · ${supplier.name}` })),
    ...(linkedItems ?? [])
      .filter((link) => !link.supplierActive)
      .map((link) => ({
        value: link.supplierId,
        label: `${link.supplierCode} · ${link.supplierName} (ngừng)`,
      })),
  ]

  const handleSubmit = async ({ supplierId, ...values }: ProductFormValues) => {
    // Đổi NCC = [NCC mới] → backend gỡ NCC cũ; xóa trắng = [] → gỡ. Không sửa được thì không gửi = giữ nguyên
    const supplierIds = supplierEditable ? (supplierId == null ? [] : [supplierId]) : undefined
    let saved: Product
    if (editing) {
      saved = await productApi.update(editing.id, { ...values, supplierIds })
    } else {
      const { active: _active, ...createInput } = values
      saved = await productApi.create({ ...createInput, supplierIds })
    }
    onSaved(saved)
  }

  return (
    <FormModal<ProductFormValues>
      open={open}
      title={editing ? `Sửa sản phẩm ${editing.code}` : 'Thêm sản phẩm'}
      form={form}
      initialValues={editing ?? undefined}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item name="name" label="Tên sản phẩm" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Cám heo con 25kg" />
      </Form.Item>
      <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true }]}>
        <Select options={categoryOptions} placeholder="Chọn danh mục" showSearch={{ optionFilterProp: 'label' }} />
      </Form.Item>
      <Form.Item
        name="unitId"
        label="Đơn vị tính"
        rules={[{ required: true }]}
        extra="Thiếu đơn vị (túi, thùng…)? Thêm ở nút Cấu hình trên trang Sản phẩm."
      >
        <Select
          options={unitOptions}
          placeholder="Chọn đơn vị"
          showSearch={{ optionFilterProp: 'label' }}
          onChange={(unitId: number) => {
            // BR-UNIT-03: đơn vị có khối lượng cố định (Kg = 1) thì điền sẵn đúng số đó
            const fixed = units.find((unit) => unit.id === unitId)?.fixedNetWeightKg
            if (fixed != null) form.setFieldValue('netWeightKg', fixed)
          }}
        />
      </Form.Item>
      <Form.Item
        name="netWeightKg"
        label="Khối lượng tịnh mỗi đơn vị (kg)"
        rules={[{ required: true }]}
        extra="Bao 25kg nhập 25, túi 5kg nhập 5; đơn vị Kg luôn là 1."
      >
        <InputNumber min={0.001} max={9_999_999.999} precision={3} style={{ width: '100%' }} />
      </Form.Item>
      <Form.Item
        name="minStockQuantity"
        label="Tồn tối thiểu (tùy chọn)"
        rules={[
          {
            // BR-INV-07: số nguyên; không dùng precision={0} vì nó âm thầm làm tròn
            validator: async (_, value?: number | null) => {
              if (value !== undefined && value !== null && !Number.isInteger(value)) throw new Error('Phải là số nguyên')
            },
          },
        ]}
        extra="Báo Sắp hết khi tổng tồn mọi kho ≤ mức này. Để trống nếu không cần cảnh báo."
      >
        <InputNumber min={0} style={{ width: '100%' }} placeholder="Ví dụ: 50" />
      </Form.Item>
      {linkedItems != null && linkedItems.length > 1 ? (
        <Form.Item label="Nhà cung cấp" extra="Sản phẩm có nhiều NCC — thêm / gỡ ở màn Nhà cung cấp.">
          <Typography.Text>
            {linkedItems.map((link) => `${link.supplierCode} · ${link.supplierName}`).join(', ')}
          </Typography.Text>
        </Form.Item>
      ) : (
        <Form.Item
          name="supplierId"
          label="Nhà cung cấp (tùy chọn)"
          extra="Chỉ lập được phiếu nhập SP từ NCC đã gán. Cần thêm NCC thì gán ở màn Nhà cung cấp."
        >
          <Select
            allowClear
            options={supplierOptions}
            placeholder="Chọn nhà cung cấp"
            showSearch={{ optionFilterProp: 'label' }}
            // Sửa SP: khóa ô cho đến khi tải xong NCC đang gán (xem supplierEditable)
            disabled={!supplierEditable}
          />
        </Form.Item>
      )}
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
