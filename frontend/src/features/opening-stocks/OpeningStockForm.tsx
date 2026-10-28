import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { Alert, Button, Col, Form, Input, InputNumber, Row, Select, Table, Typography } from 'antd'
import type { FormInstance, FormListFieldData, FormRule as Rule, TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { EMPTY_TEXT, formatQuantity, todayIso } from '../../utils/format'
import { formatPriceInput, formatVnd, hasAtMostTwoDecimals, lineAmount, parsePriceInput } from '../../utils/money'
import type { Product } from '../products/product-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { openingStockApi } from './opening-stock-api'
import type { OpeningStock } from './opening-stock-api'
import { draftTotalAmount, draftTotalQuantity } from './opening-stock-form'
import type { OpeningLineFormValues, OpeningStockFormValues } from './opening-stock-form'

interface OpeningStockFormProps {
  form: FormInstance<OpeningStockFormValues>
  initialValues: OpeningStockFormValues
  warehouses: Warehouse[]
  products: Product[]
  /** Phiếu nháp đang sửa (null = tạo mới) — để vẫn hiện tên kho/SP đã chọn dù nay đã ngừng */
  document: OpeningStock | null
  disabled: boolean
}

const NO_MARGIN = { marginBottom: 0 }

// BR-INV-07: số lượng nguyên. Không dùng precision={0} vì nó âm thầm làm tròn 2,5 thành 3
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

/**
 * Form phiếu tồn đầu kỳ: kho, ngày, bảng dòng (SP, SL, đơn giá tùy chọn). Dùng cho tạo mới và sửa phiếu Nháp.
 * - SP đã có nhập/xuất tại kho đang chọn bị làm mờ kèm lý do (BR-OPN-05) — backend vẫn kiểm lại lúc xác nhận.
 * - Mỗi SP một dòng: SP đã chọn ở dòng khác bị làm mờ.
 */
export function OpeningStockForm({ form, initialValues, warehouses, products, document, disabled }: OpeningStockFormProps) {
  const warehouseId = Form.useWatch('warehouseId', form)
  const lines = Form.useWatch('lines', form) as OpeningLineFormValues[] | undefined
  // Ghi kèm kho của kết quả: "đang tải" suy ra từ đây, không cần setState trong effect
  const [movement, setMovement] = useState<{ warehouseId: number; productIds: Set<number> } | null>(null)
  const movedIds = warehouseId && movement?.warehouseId === warehouseId ? movement.productIds : new Set<number>()
  const loadingMovement = Boolean(warehouseId) && movement?.warehouseId !== warehouseId

  useEffect(() => {
    if (!warehouseId) return
    let ignore = false
    openingStockApi
      .productsWithMovement(warehouseId)
      .catch(() => [] as number[])
      .then((ids) => {
        if (!ignore) setMovement({ warehouseId, productIds: new Set(ids) })
      })
    return () => {
      ignore = true
    }
  }, [warehouseId])

  const warehouseOptions = warehouses
    .filter((warehouse) => warehouse.active || warehouse.id === document?.warehouseId)
    .map((warehouse) => ({
      value: warehouse.id,
      label: `${warehouse.code} · ${warehouse.name}${warehouse.active ? '' : ' (ngừng hoạt động)'}`,
    }))

  // SP đang kinh doanh + SP có sẵn trên phiếu nháp (có thể đã ngừng — backend sẽ chặn khi lưu)
  const productInfo = new Map<number, { label: string; unit: string }>()
  for (const line of document?.lines ?? []) {
    productInfo.set(line.productId, { label: `${line.productCode} · ${line.productName}`, unit: line.unitName })
  }
  for (const product of products) {
    productInfo.set(product.id, { label: `${product.code} · ${product.name}`, unit: product.unitName })
  }

  const chosenProductIds = (lines ?? []).map((line) => line?.productId)
  const blockedLines = (lines ?? []).filter((line) => line?.productId !== undefined && movedIds.has(line.productId))

  const productOptions = (rowIndex: number) =>
    [...productInfo].map(([productId, info]) => {
      const moved = movedIds.has(productId)
      return {
        value: productId,
        label: moved ? `${info.label} — đã có nhập/xuất tại kho` : info.label,
        disabled: moved || chosenProductIds.some((chosen, index) => chosen === productId && index !== rowIndex),
      }
    })

  const lineColumns = (remove: (index: number) => void, count: number): TableProps<FormListFieldData>['columns'] => [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    {
      title: 'Sản phẩm',
      key: 'product',
      width: 360,
      render: (_, field) => (
        <Form.Item name={[field.name, 'productId']} rules={[{ required: true, message: 'Chọn sản phẩm' }]} style={NO_MARGIN}>
          <Select<number>
            placeholder={warehouseId ? 'Chọn sản phẩm' : 'Chọn kho trước'}
            aria-label={`Sản phẩm dòng ${field.name + 1}`}
            disabled={!warehouseId}
            loading={loadingMovement}
            options={productOptions(field.name)}
            showSearch={{ optionFilterProp: 'label' }}
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
        return unit ?? EMPTY_TEXT
      },
    },
    {
      title: 'Số lượng tồn',
      key: 'quantity',
      width: 130,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'quantity']}
          rules={[{ required: true, message: 'Nhập SL' }, { type: 'number', min: 1, message: 'Tối thiểu 1' }, integerRule]}
          style={NO_MARGIN}
        >
          <InputNumber min={1} style={{ width: '100%' }} aria-label={`Số lượng dòng ${field.name + 1}`} />
        </Form.Item>
      ),
    },
    {
      title: 'Đơn giá (tùy chọn)',
      key: 'price',
      width: 170,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'unitPrice']}
          rules={[{ type: 'number', min: 0, message: 'Không được âm' }, priceRule]}
          style={NO_MARGIN}
        >
          <InputNumber<number>
            min={0}
            style={{ width: '100%' }}
            formatter={formatPriceInput}
            parser={(text) => Number(parsePriceInput(text))}
            placeholder="Không bắt buộc"
            aria-label={`Đơn giá dòng ${field.name + 1}`}
          />
        </Form.Item>
      ),
    },
    {
      title: 'Giá trị',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (_, field) => {
        const line = lines?.[field.name]
        return line?.quantity && line.unitPrice !== undefined && line.unitPrice !== null
          ? formatVnd(lineAmount(line.quantity, line.unitPrice))
          : EMPTY_TEXT
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
        <Col xs={24} md={14}>
          <Form.Item
            name="warehouseId"
            label="Kho"
            rules={[{ required: true, message: 'Chọn kho' }]}
            tooltip="Mỗi phiếu khai tồn cho một kho. Nhiều kho thì lập nhiều phiếu"
          >
            <Select<number> placeholder="Chọn kho" options={warehouseOptions} showSearch={{ optionFilterProp: 'label' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={10}>
          <Form.Item
            name="documentDate"
            label="Ngày tồn đầu kỳ"
            tooltip="Ngày bắt đầu quản lý kho này trên phần mềm. Không được chọn ngày tương lai"
            rules={[
              { required: true, message: 'Chọn ngày' },
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
        <Input.TextArea
          maxLength={500}
          showCount
          autoSize={{ minRows: 1, maxRows: 3 }}
          placeholder="Ví dụ: số liệu kiểm đếm ngày 31/10, người đếm"
        />
      </Form.Item>

      {blockedLines.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          title={`${blockedLines.length} dòng có sản phẩm đã nhập/xuất tại kho này — không xác nhận được`}
          description="Tồn đầu kỳ chỉ dùng cho sản phẩm chưa có biến động. Hãy bỏ các dòng đó; lệch số thì dùng kiểm kê."
        />
      )}

      <Form.List
        name="lines"
        rules={[
          {
            validator: async (_, value?: OpeningLineFormValues[]) => {
              if (!value?.length) throw new Error('Phiếu phải có ít nhất một dòng hàng')
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
                  <Table.Summary.Cell index={0} colSpan={3} align="right">
                    <Typography.Text type="secondary">Tổng ({fields.length} dòng)</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3}>
                    <Typography.Text strong>{formatQuantity(draftTotalQuantity(lines))}</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={4} />
                  <Table.Summary.Cell index={5} align="right">
                    <Typography.Text strong>{formatVnd(draftTotalAmount(lines))}</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={6} />
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
