import { DeleteOutlined } from '@ant-design/icons'
import { App, Button, Col, Form, Input, InputNumber, Radio, Row, Select, Table, Typography } from 'antd'
import type { FormInstance, FormListFieldData, RefSelectProps, FormRule as Rule, TableProps } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { focusLineField, resolveQuickAdd } from '../../components/ui/product-quick-add'
import { ProductLabel, ProductQuickAdd } from '../../components/ui/ProductQuickAdd'
import { EMPTY_TEXT } from '../../utils/format'
import { productStockApi } from '../products/product-stock-api'
import type { ProductStock } from '../products/product-stock-api'
import { ORDER_CHANNEL_LABELS, STAFF_ORDER_CHANNELS } from './sales-order-api'
import type { SalesOrder } from './sales-order-api'
import type { PendingOrderFormValues, RequestLineFormValues } from './pending-order-form'
import type { SalesReferences } from './use-sales-references'

interface PendingOrderFormProps {
  form: FormInstance<PendingOrderFormValues>
  initialValues: PendingOrderFormValues
  references: SalesReferences
  /** Đơn đang sửa (null = ghi mới) — khách không đổi được sau khi đã lưu (BR-SAL-18) */
  order: SalesOrder | null
  disabled: boolean
}

/** Tối đa 100 SP đang kinh doanh (giới hạn size backend) — đủ cho MVP. */
const PRODUCTS_SIZE = 100

const NO_MARGIN = { marginBottom: 0 }

// BR-INV-07: số lượng nguyên — không dùng precision={0} vì nó âm thầm làm tròn 2,5 thành 3
const integerRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value !== undefined && value !== null && !Number.isInteger(value)) throw new Error('Phải là số nguyên')
  },
}

interface ProductInfo {
  label: string
  unit: string
}

const CHANNEL_OPTIONS = STAFF_ORDER_CHANNELS.map((channel) => ({ value: channel, label: ORDER_CHANNEL_LABELS[channel] }))
// Đơn khách tự đặt qua app giữ kênh App (BR-POR-11) — hiện đúng kênh, không cho đổi
const APP_CHANNEL_OPTIONS = [{ value: 'APP', label: ORDER_CHANNEL_LABELS.APP }]

/**
 * Form ghi nhanh đơn khách đặt (BR-SAL-17) — lúc đang nghe máy / đọc tin Zalo: khách, kênh, ngày muốn giao,
 * SP + SL theo ĐVT của SP. Không có kho, không có giá: kho điền lúc xác nhận.
 * Thêm SP bằng ô thêm nhanh (F2); Enter ở ô SL → về ô thêm nhanh để gõ SP tiếp.
 */
export function PendingOrderForm({ form, initialValues, references, order, disabled }: PendingOrderFormProps) {
  const { message } = App.useApp()
  const lines = Form.useWatch('lines', form) as RequestLineFormValues[] | undefined
  const quickAddRef = useRef<RefSelectProps>(null)
  const [products, setProducts] = useState<ProductStock[] | null>(null)

  useEffect(() => {
    let ignore = false
    productStockApi
      .list({ page: 0, size: PRODUCTS_SIZE }, { active: true })
      .then((result) => result.items)
      .catch(() => [] as ProductStock[])
      .then((items) => {
        if (!ignore) setProducts(items)
      })
    return () => {
      ignore = true
    }
  }, [])

  const customerOptions = references.customers
    .filter((customer) => customer.active || customer.id === order?.customerId)
    .map((customer) => ({
      value: customer.id,
      label: `${customer.code} · ${customer.name} · ${customer.phone}${customer.active ? '' : ' (ngừng hoạt động)'}`,
    }))

  // SP đang kinh doanh + SP đã có trong đơn (có thể đã ngừng — backend sẽ chặn khi lưu)
  const productInfo = new Map<number, ProductInfo>()
  for (const line of order?.requestLines ?? []) {
    productInfo.set(line.productId, { label: `${line.productCode} · ${line.productName}`, unit: line.unitName })
  }
  for (const item of products ?? []) {
    productInfo.set(item.id, { label: `${item.code} · ${item.name}`, unit: item.unitName })
  }
  const productLabels = new Map([...productInfo].map(([productId, info]) => [productId, info.label]))
  const quickAddOptions = [...productInfo].map(([productId, info]) => ({ value: productId, label: info.label }))

  // Mỗi SP một dòng: SP đã có thì chỉ đưa con trỏ về dòng đó
  const pickProduct = (productId: number, add: (line: RequestLineFormValues) => void) => {
    const target = resolveQuickAdd(form.getFieldValue('lines'), productId)
    if (target.action === 'focus') {
      message.info(`Sản phẩm đã có ở dòng ${target.index + 1}`)
    } else if (target.action === 'append') {
      add({ productId })
    } else {
      form.setFieldValue(['lines', target.index, 'productId'], productId)
    }
    focusLineField(form, ['lines', target.index, 'quantity'])
  }

  const lineColumns = (remove: (index: number) => void): TableProps<FormListFieldData>['columns'] => [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    {
      title: 'Sản phẩm',
      key: 'product',
      render: (_, field) => (
        <Form.Item name={[field.name, 'productId']} rules={[{ required: true, message: 'Chọn sản phẩm' }]} style={NO_MARGIN}>
          <ProductLabel labels={productLabels} />
        </Form.Item>
      ),
    },
    {
      title: 'ĐVT',
      key: 'unit',
      width: 64,
      render: (_, field) => {
        const productId = lines?.[field.name]?.productId
        return (productId === undefined ? undefined : productInfo.get(productId)?.unit) ?? EMPTY_TEXT
      },
    },
    {
      title: 'SL khách đặt',
      key: 'quantity',
      width: 140,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'quantity']}
          rules={[{ required: true, message: 'Nhập SL' }, { type: 'number', min: 1, message: 'Tối thiểu 1' }, integerRule]}
          style={NO_MARGIN}
        >
          <InputNumber
            min={1}
            style={{ width: '100%' }}
            aria-label={`Số lượng dòng ${field.name + 1}`}
            onPressEnter={() => quickAddRef.current?.focus()}
          />
        </Form.Item>
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 48,
      align: 'center',
      render: (_, field) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => remove(field.name)}
          aria-label={`Xóa dòng ${field.name + 1}`}
        />
      ),
    },
  ]

  return (
    <Form form={form} layout="vertical" initialValues={initialValues} disabled={disabled}>
      <Row gutter={16}>
        <Col xs={24} md={10}>
          <Form.Item
            name="customerId"
            label="Khách hàng"
            rules={[{ required: true, message: 'Chọn khách hàng' }]}
            tooltip={order ? 'Không đổi được khách của đơn đã lưu — ghi nhầm khách thì hủy đơn và ghi lại' : undefined}
          >
            <Select<number>
              placeholder="Tìm theo mã, tên hoặc SĐT"
              options={customerOptions}
              showSearch={{ optionFilterProp: 'label' }}
              disabled={order !== null}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="orderChannel" label="Khách đặt qua" rules={[{ required: true, message: 'Chọn kênh' }]}>
            <Radio.Group
              optionType="button"
              options={order?.orderChannel === 'APP' ? APP_CHANNEL_OPTIONS : CHANNEL_OPTIONS}
              disabled={order?.orderChannel === 'APP'}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name="requestedDeliveryDate"
            label="Ngày muốn giao"
            tooltip="Khách đặt trước thì chọn ngày giao; để trống nếu chưa hẹn"
          >
            <Input type="date" />
          </Form.Item>
        </Col>
      </Row>
      {order?.customerNote && (
        <Form.Item label="Khách ghi">
          <Typography.Text>{order.customerNote}</Typography.Text>
        </Form.Item>
      )}
      <Form.Item name="note" label="Ghi chú nội bộ" tooltip="Chỉ cửa hàng thấy — khách không thấy trên app">
        <Input.TextArea
          maxLength={500}
          showCount
          autoSize={{ minRows: 1, maxRows: 3 }}
          placeholder="Ví dụ: giao buổi sáng, gọi trước khi giao"
        />
      </Form.Item>

      <Form.List
        name="lines"
        rules={[
          {
            validator: async (_, value?: RequestLineFormValues[]) => {
              if (!value?.length) throw new Error('Đơn đặt phải có ít nhất một sản phẩm')
            },
          },
        ]}
      >
        {(fields, { add, remove }, { errors }) => (
          <>
            <ProductQuickAdd
              selectRef={quickAddRef}
              options={quickAddOptions}
              loading={products === null}
              notFoundContent="Không có sản phẩm đang kinh doanh phù hợp"
              placeholder="Gõ mã hoặc tên sản phẩm khách đặt (F2)"
              onPick={(productId) => pickProduct(productId, add)}
            />
            <Table<FormListFieldData>
              size="small"
              rowKey="key"
              columns={lineColumns(remove)}
              dataSource={fields}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
            <Form.ErrorList errors={errors} />
          </>
        )}
      </Form.List>
    </Form>
  )
}
