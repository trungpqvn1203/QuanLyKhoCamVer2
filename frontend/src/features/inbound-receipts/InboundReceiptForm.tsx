import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { App, Button, Col, Form, Input, InputNumber, Row, Select, Table, Typography } from 'antd'
import type { FormInstance, FormListFieldData, FormRule as Rule, TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { EMPTY_TEXT, todayIso } from '../../utils/format'
import { formatPriceInput, formatVnd, parsePriceInput } from '../../utils/money'
import { supplierApi } from '../suppliers/supplier-api'
import type { SupplierProduct } from '../suppliers/supplier-api'
import type { InboundReceipt } from './inbound-receipt-api'
import { draftLineAmount, draftTotal, effectiveReceived } from './receipt-form'
import type { LineFormValues, ReceiptFormValues } from './receipt-form'
import type { ReceiptReferences } from './use-receipt-references'

interface InboundReceiptFormProps {
  form: FormInstance<ReceiptFormValues>
  initialValues: ReceiptFormValues
  references: ReceiptReferences
  /** Phiếu nháp đang sửa (null = tạo mới) — để vẫn hiện tên NCC/kho/SP đã chọn dù nay đã ngừng hoặc bị gỡ liên kết */
  receipt: InboundReceipt | null
  disabled: boolean
}

/** Mỗi NCC lấy tối đa 100 SP đang liên kết (giới hạn size backend) — đủ cho MVP. */
const LINKED_PRODUCTS_SIZE = 100

const NO_MARGIN = { marginBottom: 0 }

// BR-INV-07: số lượng nguyên. Không dùng precision={0} của InputNumber vì nó âm thầm làm tròn 2,5 thành 3
const integerRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value !== undefined && value !== null && !Number.isInteger(value)) throw new Error('Phải là số nguyên')
  },
}

// BR-MON-04: đơn giá tối đa 2 chữ số lẻ
const priceRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value !== undefined && value !== null && Math.abs(value * 100 - Math.round(value * 100)) > 1e-6) {
      throw new Error('Tối đa 2 chữ số lẻ')
    }
  },
}

/**
 * Form phiếu nhập (thông tin chung + bảng dòng hàng động). Dùng cho tạo mới và sửa phiếu Nháp.
 * - Ô sản phẩm chỉ gồm SP đang gán cho NCC đã chọn (BR-SUP-08) — chọn NCC trước.
 * - Mỗi SP một dòng (BR-IMP-A05): SP đã chọn ở dòng khác bị làm mờ.
 * - SL thực nhận để trống = bằng SL đặt (BR-IMP-03); lệch thì hiện "Thiếu/Thừa" ngay dưới ô.
 * - Thành tiền / tổng chỉ là số tạm (cùng quy tắc HALF_UP); số chính thức do backend tính khi lưu.
 */
export function InboundReceiptForm({ form, initialValues, references, receipt, disabled }: InboundReceiptFormProps) {
  const { message } = App.useApp()
  const supplierId = Form.useWatch('supplierId', form)
  const lines = Form.useWatch('lines', form) as LineFormValues[] | undefined
  // Ghi kèm NCC của kết quả: "đang tải" và "danh sách SP" đều suy ra từ đây, không cần setState trong effect
  const [linked, setLinked] = useState<{ supplierId: number; items: SupplierProduct[] } | null>(null)
  const linkedProducts = supplierId && linked?.supplierId === supplierId ? linked.items : []
  const loadingProducts = Boolean(supplierId) && linked?.supplierId !== supplierId

  useEffect(() => {
    if (!supplierId) return
    let ignore = false
    supplierApi
      .listProducts(supplierId, { page: 0, size: LINKED_PRODUCTS_SIZE })
      // BR-IMP-A04: SP ngừng kinh doanh không nhập được → không gợi ý
      .then((result) => result.items.filter((item) => item.productActive))
      .catch(() => [] as SupplierProduct[])
      .then((items) => {
        if (!ignore) setLinked({ supplierId, items })
      })
    return () => {
      ignore = true
    }
  }, [supplierId])

  // NCC / kho: chỉ bản đang hoạt động; riêng bản phiếu đang dùng thì giữ lại để không hiện id trống
  const supplierOptions = references.suppliers
    .filter((supplier) => supplier.active || supplier.id === receipt?.supplierId)
    .map((supplier) => ({
      value: supplier.id,
      label: `${supplier.code} · ${supplier.name}${supplier.active ? '' : ' (ngừng hoạt động)'}`,
    }))
  const warehouseOptions = references.warehouses
    .filter((warehouse) => warehouse.active || warehouse.id === receipt?.warehouseId)
    .map((warehouse) => ({
      value: warehouse.id,
      label: `${warehouse.code} · ${warehouse.name}${warehouse.active ? '' : ' (ngừng hoạt động)'}`,
    }))

  // SP: những SP đang gán cho NCC + SP có sẵn trên phiếu nháp (có thể đã bị gỡ liên kết — backend sẽ chặn khi lưu)
  const productInfo = new Map<number, { label: string; unit: string }>()
  for (const line of receipt?.supplierId === supplierId ? (receipt?.lines ?? []) : []) {
    productInfo.set(line.productId, { label: `${line.productCode} · ${line.productName}`, unit: line.unitName })
  }
  for (const item of linkedProducts) {
    productInfo.set(item.productId, { label: `${item.productCode} · ${item.productName}`, unit: item.unitName })
  }

  const chosenProductIds = (lines ?? []).map((line) => line?.productId)

  const productOptions = (rowIndex: number) =>
    [...productInfo].map(([productId, info]) => ({
      value: productId,
      label: info.label,
      disabled: chosenProductIds.some((chosen, index) => chosen === productId && index !== rowIndex),
    }))

  // Đổi NCC: SP của NCC cũ không còn hợp lệ (BR-SUP-08) → bỏ chọn SP ở mọi dòng, giữ số lượng/đơn giá
  const handleSupplierChange = () => {
    const current = (form.getFieldValue('lines') as LineFormValues[] | undefined) ?? []
    if (!current.some((line) => line?.productId !== undefined)) return
    form.setFieldValue(
      'lines',
      current.map((line) => ({ ...line, productId: undefined })),
    )
    message.info('Đã đổi nhà cung cấp — hãy chọn lại sản phẩm cho các dòng')
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
            placeholder={supplierId ? 'Chọn sản phẩm' : 'Chọn nhà cung cấp trước'}
            aria-label={`Sản phẩm dòng ${field.name + 1}`}
            disabled={!supplierId}
            loading={loadingProducts}
            options={productOptions(field.name)}
            showSearch={{ optionFilterProp: 'label' }}
            notFoundContent={loadingProducts ? 'Đang tải…' : 'Nhà cung cấp chưa được gán sản phẩm nào đang kinh doanh'}
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
      title: 'SL đặt',
      key: 'ordered',
      width: 110,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'orderedQuantity']}
          rules={[{ required: true, message: 'Nhập SL' }, { type: 'number', min: 1, message: 'Tối thiểu 1' }, integerRule]}
          style={NO_MARGIN}
        >
          <InputNumber min={1} style={{ width: '100%' }} aria-label={`SL đặt dòng ${field.name + 1}`} />
        </Form.Item>
      ),
    },
    {
      title: 'SL thực nhận',
      key: 'received',
      width: 130,
      render: (_, field) => {
        const line = lines?.[field.name]
        const difference =
          line?.receivedQuantity !== undefined && line?.receivedQuantity !== null && line?.orderedQuantity !== undefined
            ? line.receivedQuantity - line.orderedQuantity
            : 0
        return (
          <Form.Item
            name={[field.name, 'receivedQuantity']}
            rules={[{ type: 'number', min: 0, message: 'Không được âm' }, integerRule]}
            style={NO_MARGIN}
            extra={
              difference !== 0 && (
                <Typography.Text type="warning">
                  {difference < 0 ? `Thiếu ${-difference}` : `Thừa ${difference}`}
                </Typography.Text>
              )
            }
          >
            <InputNumber
              min={0}
              style={{ width: '100%' }}
              // Để trống = bằng SL đặt (BR-IMP-03) → gợi ý ngay trong ô
              placeholder={line?.orderedQuantity !== undefined ? String(line.orderedQuantity) : '= SL đặt'}
              aria-label={`SL thực nhận dòng ${field.name + 1}`}
            />
          </Form.Item>
        )
      },
    },
    {
      title: 'Đơn giá (đ)',
      key: 'price',
      width: 160,
      render: (_, field) => (
        <Form.Item
          name={[field.name, 'unitPrice']}
          rules={[{ required: true, message: 'Nhập đơn giá' }, { type: 'number', min: 0, message: 'Không được âm' }, priceRule]}
          style={NO_MARGIN}
        >
          <InputNumber<number>
            min={0}
            style={{ width: '100%' }}
            formatter={formatPriceInput}
            parser={(text) => Number(parsePriceInput(text))}
            aria-label={`Đơn giá dòng ${field.name + 1}`}
          />
        </Form.Item>
      ),
    },
    {
      title: 'Thành tiền',
      key: 'amount',
      width: 140,
      align: 'right',
      render: (_, field) => {
        const amount = draftLineAmount(lines?.[field.name])
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

  const receivedLineCount = (lines ?? []).filter((line) => (line ? (effectiveReceived(line) ?? 0) : 0) > 0).length

  return (
    <Form form={form} layout="vertical" initialValues={initialValues} disabled={disabled}>
      <Row gutter={16}>
        <Col xs={24} md={9}>
          <Form.Item name="supplierId" label="Nhà cung cấp" rules={[{ required: true, message: 'Chọn nhà cung cấp' }]}>
            <Select<number>
              placeholder="Chọn nhà cung cấp"
              options={supplierOptions}
              showSearch={{ optionFilterProp: 'label' }}
              onChange={handleSupplierChange}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={9}>
          <Form.Item name="warehouseId" label="Kho nhận" rules={[{ required: true, message: 'Chọn kho nhận' }]}>
            <Select<number> placeholder="Chọn kho nhận" options={warehouseOptions} showSearch={{ optionFilterProp: 'label' }} />
          </Form.Item>
        </Col>
        <Col xs={24} md={6}>
          <Form.Item
            name="documentDate"
            label="Ngày chứng từ"
            tooltip="Được ghi lùi ngày khi nhập bù phiếu giấy, không được chọn ngày tương lai"
            rules={[
              { required: true, message: 'Chọn ngày chứng từ' },
              {
                // BR-IMP-08 — so chuỗi "YYYY-MM-DD" theo thứ tự từ điển = so ngày
                validator: async (_, value?: string) => {
                  if (value && value > todayIso()) throw new Error('Không được chọn ngày tương lai')
                },
              },
            ]}
          >
            {/* Ô ngày gốc của trình duyệt: giá trị đúng dạng YYYY-MM-DD, không cần thư viện ngày (dayjs) */}
            <Input type="date" max={todayIso()} />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item name="note" label="Ghi chú">
        <Input.TextArea maxLength={500} showCount autoSize={{ minRows: 1, maxRows: 3 }} placeholder="Ví dụ: số hóa đơn, biển số xe giao hàng" />
      </Form.Item>

      <Form.List
        name="lines"
        rules={[
          {
            validator: async (_, value?: LineFormValues[]) => {
              if (!value?.length) throw new Error('Phiếu nhập phải có ít nhất một dòng hàng')
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
                    <Typography.Text type="secondary">
                      Tạm tính ({receivedLineCount}/{fields.length} dòng có hàng về)
                    </Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={6} align="right">
                    <Typography.Text strong>{formatVnd(draftTotal(lines))}</Typography.Text>
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
