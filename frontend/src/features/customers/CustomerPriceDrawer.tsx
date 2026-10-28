import { DeleteOutlined, SaveOutlined } from '@ant-design/icons'
import { Alert, App, Button, Drawer, Flex, Form, InputNumber, Popconfirm, Select, Table, Typography } from 'antd'
import type { FormRule as Rule, TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'
import { CodeText } from '../../components/ui/CodeLink'
import { formatDateTime } from '../../utils/format'
import { formatPriceInput, formatVnd, hasAtMostTwoDecimals, parsePriceInput } from '../../utils/money'
import { productApi } from '../products/product-api'
import type { Product } from '../products/product-api'
import { customerPriceApi } from '../sales-orders/sales-order-api'
import type { CustomerPrice } from '../sales-orders/sales-order-api'
import type { Customer } from './customer-api'

interface CustomerPriceDrawerProps {
  open: boolean
  customer: Customer | null
  onClose: () => void
}

interface PriceFormValues {
  productId?: number
  unitPrice?: number
}

/** Tối đa 100 SP đang kinh doanh (giới hạn size backend) — đủ cho MVP. */
const PRODUCTS_SIZE = 100

const priceRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !hasAtMostTwoDecimals(value)) throw new Error('Tối đa 2 chữ số lẻ')
  },
}

/**
 * Bảng giá riêng của một khách (BR-SAL-06..08). Chọn SP + nhập giá → lưu (SP đã có giá thì ghi đè).
 * Giá này chỉ để điền sẵn khi lập phiếu bán; đổi hay xóa giá không ảnh hưởng phiếu đã lập.
 */
export function CustomerPriceDrawer({ open, customer, onClose }: CustomerPriceDrawerProps) {
  const { notification } = App.useApp()
  const [form] = Form.useForm<PriceFormValues>()
  const [prices, setPrices] = useState<CustomerPrice[] | null>(null)
  const [products, setProducts] = useState<Product[]>([])
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const customerId = customer?.id

  useEffect(() => {
    if (!open || customerId === undefined) return
    let ignore = false
    customerPriceApi
      .list(customerId)
      .then((list) => {
        if (!ignore) setPrices(list)
      })
      .catch((err: unknown) => {
        if (!ignore) setError(err instanceof ApiError ? err.message : 'Không tải được bảng giá')
      })
    productApi
      .list({ page: 0, size: PRODUCTS_SIZE }, { active: true })
      .then((result) => {
        if (!ignore) setProducts(result.items)
      })
      .catch(() => {
        // ô chọn SP trống; lỗi thật hiện khi lưu
      })
    return () => {
      ignore = true
    }
  }, [open, customerId])

  // Chọn SP đã có giá → điền giá hiện tại để sửa
  const handleProductChange = (productId: number) => {
    const existing = prices?.find((price) => price.productId === productId)
    form.setFieldValue('unitPrice', existing?.unitPrice)
  }

  const save = async (values: PriceFormValues) => {
    if (customerId === undefined) return
    setSaving(true)
    setError(null)
    try {
      const updated = await customerPriceApi.set(customerId, values.productId!, values.unitPrice!)
      setPrices(updated)
      const saved = updated.find((price) => price.productId === values.productId)
      notification.success({
        title: 'Đã lưu giá riêng',
        description: saved ? `${saved.productName}: ${formatVnd(saved.unitPrice)}` : undefined,
      })
      form.resetFields()
    } catch (err) {
      const fieldErrors = toFormFieldErrors(err)
      if (fieldErrors.length > 0) form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      else setError(err instanceof ApiError ? err.message : 'Không lưu được giá')
    } finally {
      setSaving(false)
    }
  }

  const remove = async (price: CustomerPrice) => {
    if (customerId === undefined) return
    try {
      await customerPriceApi.remove(customerId, price.productId)
      setPrices((current) => current?.filter((item) => item.productId !== price.productId) ?? null)
      notification.success({ title: 'Đã bỏ giá riêng', description: `${price.productName} — lập phiếu sẽ nhập giá tay` })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Không bỏ được giá')
    }
  }

  const productOptions = products.map((product) => ({ value: product.id, label: `${product.code} · ${product.name}` }))

  const columns: TableProps<CustomerPrice>['columns'] = [
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      render: (name: string, price) => (
        <>
          {name}
          {!price.productActive && <Typography.Text type="secondary"> (ngừng kinh doanh)</Typography.Text>}
        </>
      ),
    },
    { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
    { title: 'Giá riêng', dataIndex: 'unitPrice', width: 140, align: 'right', render: (v: number) => formatVnd(v) },
    { title: 'Cập nhật', dataIndex: 'updatedAt', width: 150, render: (at: string) => formatDateTime(at) },
    {
      title: '',
      key: 'actions',
      width: 56,
      align: 'center',
      render: (_, price) => (
        <Popconfirm
          title={`Bỏ giá riêng của ${price.productName}?`}
          okText="Bỏ giá"
          okButtonProps={{ danger: true }}
          cancelText="Không"
          onConfirm={() => remove(price)}
        >
          <Button type="text" danger icon={<DeleteOutlined />} aria-label={`Bỏ giá ${price.productName}`} />
        </Popconfirm>
      ),
    },
  ]

  return (
    <Drawer
      open={open}
      title={customer ? `Bảng giá riêng · ${customer.code} · ${customer.name}` : 'Bảng giá riêng'}
      size={760}
      onClose={onClose}
      destroyOnHidden
    >
      <Flex vertical gap={16}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          Giá riêng được điền sẵn khi lập phiếu bán cho khách này; người bán vẫn sửa được trên từng phiếu. Đổi giá ở đây
          không làm thay đổi các phiếu đã lập.
        </Typography.Paragraph>
        {error && <Alert type="error" showIcon closable title={error} onClose={() => setError(null)} />}
        <Form form={form} layout="inline" onFinish={save} disabled={saving}>
          <Form.Item name="productId" rules={[{ required: true, message: 'Chọn sản phẩm' }]} style={{ flex: 1, minWidth: 260 }}>
            <Select<number>
              placeholder="Chọn sản phẩm"
              options={productOptions}
              showSearch={{ optionFilterProp: 'label' }}
              onChange={handleProductChange}
            />
          </Form.Item>
          <Form.Item
            name="unitPrice"
            rules={[{ required: true, message: 'Nhập giá' }, { type: 'number', min: 0, message: 'Không được âm' }, priceRule]}
          >
            <InputNumber<number>
              min={0}
              style={{ width: 180 }}
              placeholder="Giá bán (đ)"
              formatter={formatPriceInput}
              parser={(text) => Number(parsePriceInput(text))}
              aria-label="Giá riêng"
            />
          </Form.Item>
          <Form.Item style={{ marginInlineEnd: 0 }}>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              Lưu giá
            </Button>
          </Form.Item>
        </Form>
        <Table<CustomerPrice>
          size="small"
          rowKey="productId"
          loading={prices === null && !error}
          columns={columns}
          dataSource={prices ?? []}
          pagination={false}
          locale={{ emptyText: 'Khách chưa có giá riêng — lập phiếu sẽ nhập giá tay' }}
        />
      </Flex>
    </Drawer>
  )
}
