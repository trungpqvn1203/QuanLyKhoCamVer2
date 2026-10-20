import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Col, Form, Input, InputNumber, Row, Select, Table, Tooltip, Typography } from 'antd'
import type { FormInstance, FormListFieldData, FormRule as Rule, TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { EMPTY_TEXT, todayIso } from '../../utils/format'
import { formatPriceInput, formatVnd, hasAtMostTwoDecimals, parsePriceInput } from '../../utils/money'
import { PRODUCT_UNIT_LABELS } from '../products/product-api'
import type { ProductUnit } from '../products/product-api'
import { productStockApi } from '../products/product-stock-api'
import type { ProductStock } from '../products/product-stock-api'
import { customerPriceApi } from './sales-order-api'
import type { SalesOrder } from './sales-order-api'
import { applyPriceList, draftSalesLineAmount, draftSalesTotal } from './sales-order-form'
import type { SalesLineFormValues, SalesOrderFormValues } from './sales-order-form'
import type { SalesReferences } from './use-sales-references'

interface SalesOrderFormProps {
  form: FormInstance<SalesOrderFormValues>
  initialValues: SalesOrderFormValues
  references: SalesReferences
  /** Phiếu nháp đang sửa (null = tạo mới) — để vẫn hiện tên khách/kho/SP đã chọn dù nay đã ngừng */
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

const priceRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value !== undefined && value !== null && !hasAtMostTwoDecimals(value)) throw new Error('Tối đa 2 chữ số lẻ')
  },
}

interface ProductInfo {
  label: string
  unit: ProductUnit
  /** Tồn tại kho đã chọn; không có = chưa chọn kho / chưa tải xong */
  stock?: number
}

/**
 * Form phiếu bán (thông tin chung + bảng dòng hàng động). Dùng cho tạo mới và sửa phiếu Nháp.
 * - Chọn SP → đơn giá điền sẵn từ bảng giá riêng của khách (BR-SAL-07); sửa tay được. Ô giá có dấu "Giá riêng"
 *   khi đúng bằng bảng giá.
 * - Cột "Tồn kho" theo kho đã chọn: chỉ để tham khảo, phiếu nháp không giữ chỗ tồn (BR-SAL-02); vượt tồn thì
 *   cảnh báo, việc chặn thật do backend làm lúc hoàn tất.
 * - Thành tiền / tổng là số tạm (cùng quy tắc HALF_UP); số chính thức do backend tính khi lưu.
 */
export function SalesOrderForm({ form, initialValues, references, order, disabled }: SalesOrderFormProps) {
  const { message } = App.useApp()
  const customerId = Form.useWatch('customerId', form)
  const warehouseId = Form.useWatch('warehouseId', form)
  const lines = Form.useWatch('lines', form) as SalesLineFormValues[] | undefined

  // Kết quả kèm khóa (kho / khách) đã tải: "đang tải" suy ra từ việc khóa có khớp hay chưa
  const [products, setProducts] = useState<{ warehouseId?: number; items: ProductStock[] } | null>(null)
  const [prices, setPrices] = useState<{ customerId: number; map: Map<number, number> } | null>(null)
  const loadingProducts = products === null || products.warehouseId !== warehouseId
  const priceMap = customerId && prices?.customerId === customerId ? prices.map : new Map<number, number>()

  useEffect(() => {
    let ignore = false
    productStockApi
      .list({ page: 0, size: PRODUCTS_SIZE }, { active: true, warehouseId })
      .then((result) => result.items)
      .catch(() => [] as ProductStock[])
      .then((items) => {
        if (!ignore) setProducts({ warehouseId, items })
      })
    return () => {
      ignore = true
    }
  }, [warehouseId])

  useEffect(() => {
    if (!customerId) return
    let ignore = false
    customerPriceApi
      .list(customerId)
      .then((list) => new Map(list.map((price) => [price.productId, price.unitPrice])))
      .catch(() => new Map<number, number>())
      .then((map) => {
        if (ignore) return
        setPrices({ customerId, map })
        // Đổi khách sau khi đã có dòng → điền lại giá theo bảng giá của khách mới
        const current = (form.getFieldValue('lines') as SalesLineFormValues[] | undefined) ?? []
        const repriced = applyPriceList(current, map)
        if (form.isFieldTouched('customerId') && repriced.some((line, index) => line !== current[index])) {
          form.setFieldValue('lines', repriced)
          message.info('Đã điền lại đơn giá theo bảng giá riêng của khách')
        }
      })
    return () => {
      ignore = true
    }
  }, [customerId, form, message])

  const customerOptions = references.customers
    .filter((customer) => customer.active || customer.id === order?.customerId)
    .map((customer) => ({
      value: customer.id,
      label: `${customer.code} · ${customer.name} · ${customer.phone}${customer.active ? '' : ' (ngừng hoạt động)'}`,
    }))
  const warehouseOptions = references.warehouses
    .filter((warehouse) => warehouse.active || warehouse.id === order?.warehouseId)
    .map((warehouse) => ({
      value: warehouse.id,
      label: `${warehouse.code} · ${warehouse.name}${warehouse.active ? '' : ' (ngừng hoạt động)'}`,
    }))

  // SP đang kinh doanh + SP có sẵn trên phiếu nháp (có thể đã ngừng — backend sẽ chặn khi lưu)
  const productInfo = new Map<number, ProductInfo>()
  for (const line of order?.lines ?? []) {
    productInfo.set(line.productId, { label: `${line.productCode} · ${line.productName}`, unit: line.unit })
  }
  for (const item of loadingProducts ? [] : (products?.items ?? [])) {
    productInfo.set(item.id, {
      label: `${item.code} · ${item.name}`,
      unit: item.unit,
      stock: warehouseId ? (item.warehouseStockQuantity ?? 0) : undefined,
    })
  }

  const chosenProductIds = (lines ?? []).map((line) => line?.productId)
  const productOptions = (rowIndex: number) =>
    [...productInfo].map(([productId, info]) => ({
      value: productId,
      label: info.label,
      disabled: chosenProductIds.some((chosen, index) => chosen === productId && index !== rowIndex),
    }))

  // BR-SAL-07: chọn SP → điền giá riêng nếu khách có; không có thì để người bán nhập tay
  const handleProductChange = (rowIndex: number, productId: number) => {
    const price = priceMap.get(productId)
    if (price !== undefined) form.setFieldValue(['lines', rowIndex, 'unitPrice'], price)
  }

  const lineColumns = (remove: (index: number) => void, count: number): TableProps<FormListFieldData>['columns'] => [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    {
      title: 'Sản phẩm',
      key: 'product',
      width: 300,
      render: (_, field) => (
        <Form.Item name={[field.name, 'productId']} rules={[{ required: true, message: 'Chọn sản phẩm' }]} style={NO_MARGIN}>
          <Select<number>
            placeholder="Chọn sản phẩm"
            aria-label={`Sản phẩm dòng ${field.name + 1}`}
            loading={loadingProducts}
            options={productOptions(field.name)}
            showSearch={{ optionFilterProp: 'label' }}
            onChange={(productId) => handleProductChange(field.name, productId)}
            notFoundContent={loadingProducts ? 'Đang tải…' : 'Không có sản phẩm đang kinh doanh'}
          />
        </Form.Item>
      ),
    },
    {
      title: 'ĐVT',
      key: 'unit',
      width: 64,
      render: (_, field) => {
        const productId = lines?.[field.name]?.productId
        const unit = productId === undefined ? undefined : productInfo.get(productId)?.unit
        return unit ? PRODUCT_UNIT_LABELS[unit] : EMPTY_TEXT
      },
    },
    {
      title: (
        <Tooltip title="Tồn hiện tại ở kho xuất — chỉ để tham khảo, phiếu nháp không giữ chỗ tồn">Tồn kho</Tooltip>
      ),
      key: 'stock',
      width: 90,
      align: 'right',
      render: (_, field) => {
        const productId = lines?.[field.name]?.productId
        const stock = productId === undefined ? undefined : productInfo.get(productId)?.stock
        return stock === undefined ? EMPTY_TEXT : stock
      },
    },
    {
      title: 'Số lượng',
      key: 'quantity',
      width: 120,
      render: (_, field) => {
        const line = lines?.[field.name]
        const stock = line?.productId === undefined ? undefined : productInfo.get(line.productId)?.stock
        const overStock = stock !== undefined && line?.quantity != null && line.quantity > stock
        return (
          <Form.Item
            name={[field.name, 'quantity']}
            rules={[{ required: true, message: 'Nhập SL' }, { type: 'number', min: 1, message: 'Tối thiểu 1' }, integerRule]}
            style={NO_MARGIN}
            extra={overStock && <Typography.Text type="warning">Vượt tồn</Typography.Text>}
          >
            <InputNumber min={1} style={{ width: '100%' }} aria-label={`Số lượng dòng ${field.name + 1}`} />
          </Form.Item>
        )
      },
    },
    {
      title: 'Đơn giá (đ)',
      key: 'price',
      width: 170,
      render: (_, field) => {
        const line = lines?.[field.name]
        const listPrice = line?.productId === undefined ? undefined : priceMap.get(line.productId)
        const hint =
          listPrice === undefined ? undefined : line?.unitPrice === listPrice ? (
            <Typography.Text type="secondary">Giá riêng</Typography.Text>
          ) : (
            <Typography.Text type="warning">Giá riêng {formatVnd(listPrice)}</Typography.Text>
          )
        return (
          <Form.Item
            name={[field.name, 'unitPrice']}
            rules={[{ required: true, message: 'Nhập đơn giá' }, { type: 'number', min: 0, message: 'Không được âm' }, priceRule]}
            style={NO_MARGIN}
            extra={hint}
          >
            <InputNumber<number>
              min={0}
              style={{ width: '100%' }}
              formatter={formatPriceInput}
              parser={(text) => Number(parsePriceInput(text))}
              aria-label={`Đơn giá dòng ${field.name + 1}`}
            />
          </Form.Item>
        )
      },
    },
    {
      title: 'Thành tiền',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (_, field) => {
        const amount = draftSalesLineAmount(lines?.[field.name])
        return amount === undefined ? EMPTY_TEXT : formatVnd(amount)
      },
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
          disabled={count === 1}
          onClick={() => remove(field.name)}
          aria-label={`Xóa dòng ${field.name + 1}`}
        />
      ),
    },
  ]

  return (
    <Form form={form} layout="vertical" initialValues={initialValues} disabled={disabled}>
      <Row gutter={16}>
        <Col xs={24} md={9}>
          <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách hàng' }]}>
            <Select<number>
              placeholder="Tìm theo mã, tên hoặc SĐT"
              options={customerOptions}
              showSearch={{ optionFilterProp: 'label' }}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={9}>
          <Form.Item name="warehouseId" label="Kho xuất" rules={[{ required: true, message: 'Chọn kho xuất' }]}>
            <Select<number> placeholder="Chọn kho xuất" options={warehouseOptions} showSearch={{ optionFilterProp: 'label' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name="documentDate"
            label="Ngày bán"
            tooltip="Được ghi lùi ngày khi nhập bù phiếu giấy, không được chọn ngày tương lai"
            rules={[
              { required: true, message: 'Chọn ngày bán' },
              {
                validator: async (_, value?: string) => {
                  if (value && value > todayIso()) throw new Error('Không được chọn ngày tương lai')
                },
              },
            ]}
          >
            <Input type="date" max={todayIso()} />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item name="note" label="Ghi chú">
        <Input.TextArea maxLength={500} showCount autoSize={{ minRows: 1, maxRows: 3 }} placeholder="Ví dụ: địa chỉ giao, biển số xe" />
      </Form.Item>

      <Form.List
        name="lines"
        rules={[
          {
            validator: async (_, value?: SalesLineFormValues[]) => {
              if (!value?.length) throw new Error('Phiếu bán phải có ít nhất một dòng hàng')
            },
          },
        ]}
      >
        {(fields, { add, remove }, { errors }) => (
          <>
            <Table<FormListFieldData>
              size="small"
              rowKey="key"
              columns={lineColumns(remove, fields.length)}
              dataSource={fields}
              pagination={false}
              scroll={{ x: 'max-content' }}
              summary={() => (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={6} align="right">
                    <Typography.Text type="secondary">Tạm tính ({fields.length} dòng)</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={6} align="right">
                    <Typography.Text strong>{formatVnd(draftSalesTotal(lines))}</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={7} />
                </Table.Summary.Row>
              )}
            />
            <Form.ErrorList errors={errors} />
            <Button type="dashed" block icon={<PlusOutlined />} onClick={() => add({})} style={{ marginTop: 12 }}>
              Thêm dòng hàng
            </Button>
          </>
        )}
      </Form.List>
    </Form>
  )
}
