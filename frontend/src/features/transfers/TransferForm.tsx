import { ArrowRightOutlined, DeleteOutlined } from '@ant-design/icons'
import { App, Button, Col, Form, Input, InputNumber, Row, Select, Table, Tooltip, Typography } from 'antd'
import type { FormInstance, FormListFieldData, RefSelectProps, FormRule as Rule, TableProps } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { focusLineField, resolveQuickAdd } from '../../components/ui/product-quick-add'
import { ProductLabel, ProductQuickAdd } from '../../components/ui/ProductQuickAdd'
import { EMPTY_TEXT, formatQuantity, formatTons, todayIso } from '../../utils/format'
import { productStockApi } from '../products/product-stock-api'
import type { ProductStock } from '../products/product-stock-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import type { Transfer } from './transfer-api'
import { draftTotalQuantity, toTons, totalTons } from './transfer-form'
import type { TransferFormValues, TransferLineFormValues } from './transfer-form'

interface TransferFormProps {
  form: FormInstance<TransferFormValues>
  initialValues: TransferFormValues
  warehouses: Warehouse[]
  /** Phiếu nháp đang sửa (null = tạo mới) — để vẫn hiện tên kho/SP đã chọn dù nay đã ngừng */
  transfer: Transfer | null
  disabled: boolean
}

interface ProductInfo {
  label: string
  unit: string
  netWeightKg: number
  /** Tồn tại kho nguồn; không có = chưa chọn kho nguồn / chưa tải xong */
  stock?: number
}

/** Tối đa 100 SP (giới hạn size của backend) — như các danh sách tham chiếu khác của MVP. */
const PRODUCTS_SIZE = 100
const NO_MARGIN = { marginBottom: 0 }

// BR-INV-07: số lượng nguyên. Không dùng precision={0} vì nó âm thầm làm tròn 2,5 thành 3
const integerRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value !== undefined && value !== null && !Number.isInteger(value)) throw new Error('Phải là số nguyên')
  },
}

/**
 * Form phiếu điều chuyển: kho nguồn → kho đích, ngày, bảng dòng (SP, SL). Dùng cho tạo mới và sửa phiếu Nháp.
 * - Kho nguồn chọn được cả kho đã ngừng (rút hàng khỏi kho đóng cửa); kho đích chỉ kho đang hoạt động (BR-TRF-09).
 * - Ô thêm nhanh chỉ liệt kê SP đang CÓ HÀNG ở kho nguồn, kể cả SP đã ngừng kinh doanh — hàng còn thì vẫn phải dời.
 * - Cột "Tồn kho nguồn" chỉ để tham khảo: phiếu nháp không giữ chỗ tồn; vượt tồn thì cảnh báo, việc chặn thật do
 *   backend làm lúc hoàn tất.
 */
export function TransferForm({ form, initialValues, warehouses, transfer, disabled }: TransferFormProps) {
  const { message } = App.useApp()
  const sourceWarehouseId = Form.useWatch('sourceWarehouseId', form)
  const lines = Form.useWatch('lines', form) as TransferLineFormValues[] | undefined
  const quickAddRef = useRef<RefSelectProps>(null)

  // Ghi kèm kho của kết quả: "đang tải" suy ra từ đây, không cần setState trong effect
  const [stock, setStock] = useState<{ warehouseId: number; items: ProductStock[] } | null>(null)
  const loadingStock = Boolean(sourceWarehouseId) && stock?.warehouseId !== sourceWarehouseId
  const stockItems = sourceWarehouseId && stock?.warehouseId === sourceWarehouseId ? stock.items : []

  useEffect(() => {
    if (!sourceWarehouseId) return
    let ignore = false
    productStockApi
      .list({ page: 0, size: PRODUCTS_SIZE }, { warehouseId: sourceWarehouseId })
      .then((result) => result.items)
      .catch(() => [] as ProductStock[])
      .then((items) => {
        if (!ignore) setStock({ warehouseId: sourceWarehouseId, items })
      })
    return () => {
      ignore = true
    }
  }, [sourceWarehouseId])

  const warehouseLabel = (warehouse: Warehouse) =>
    `${warehouse.code} · ${warehouse.name}${warehouse.active ? '' : ' (ngừng hoạt động)'}`
  const sourceOptions = warehouses.map((warehouse) => ({ value: warehouse.id, label: warehouseLabel(warehouse) }))
  const targetOptions = warehouses
    .filter((warehouse) => warehouse.active || warehouse.id === transfer?.targetWarehouseId)
    .map((warehouse) => ({
      value: warehouse.id,
      label: warehouseLabel(warehouse),
      disabled: warehouse.id === sourceWarehouseId,
    }))

  // SP có sẵn trên phiếu nháp (kể cả khi kho nguồn nay không còn hàng) + SP của kho nguồn
  const productInfo = new Map<number, ProductInfo>()
  for (const line of transfer?.lines ?? []) {
    productInfo.set(line.productId, {
      label: `${line.productCode} · ${line.productName}`,
      unit: line.unitName,
      netWeightKg: line.netWeightKg,
      stock: loadingStock || !sourceWarehouseId ? undefined : 0,
    })
  }
  for (const item of stockItems) {
    productInfo.set(item.id, {
      label: `${item.code} · ${item.name}`,
      unit: item.unitName,
      netWeightKg: item.netWeightKg,
      stock: item.warehouseStockQuantity ?? 0,
    })
  }

  const productLabels = new Map([...productInfo].map(([productId, info]) => [productId, info.label]))
  const weights = new Map([...productInfo].map(([productId, info]) => [productId, info.netWeightKg]))
  const quickAddOptions = stockItems
    .filter((item) => (item.warehouseStockQuantity ?? 0) > 0)
    .map((item) => ({
      value: item.id,
      label: `${item.code} · ${item.name} — tồn ${formatQuantity(item.warehouseStockQuantity ?? 0)}`,
    }))

  const pickProduct = (productId: number, add: (line: TransferLineFormValues) => void) => {
    const target = resolveQuickAdd(form.getFieldValue('lines'), productId)
    if (target.action === 'focus') message.info(`Sản phẩm đã có ở dòng ${target.index + 1}`)
    else if (target.action === 'append') add({ productId })
    else form.setFieldValue(['lines', target.index, 'productId'], productId)
    focusLineField(form, ['lines', target.index, 'quantity'])
  }

  const focusQuickAdd = () => quickAddRef.current?.focus()

  const lineColumns = (remove: (index: number) => void): TableProps<FormListFieldData>['columns'] => [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    {
      title: 'Sản phẩm',
      key: 'product',
      width: 360,
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
      title: (
        <Tooltip title="Tồn hiện tại ở kho nguồn — chỉ để tham khảo, phiếu nháp không giữ chỗ tồn">Tồn kho nguồn</Tooltip>
      ),
      key: 'stock',
      width: 130,
      align: 'right',
      render: (_, field) => {
        const productId = lines?.[field.name]?.productId
        const current = productId === undefined ? undefined : productInfo.get(productId)?.stock
        return current === undefined ? EMPTY_TEXT : formatQuantity(current)
      },
    },
    {
      title: 'Số lượng chuyển',
      key: 'quantity',
      width: 150,
      render: (_, field) => {
        const line = lines?.[field.name]
        const current = line?.productId === undefined ? undefined : productInfo.get(line.productId)?.stock
        const overStock = current !== undefined && line?.quantity != null && line.quantity > current
        return (
          <Form.Item
            name={[field.name, 'quantity']}
            rules={[{ required: true, message: 'Nhập SL' }, { type: 'number', min: 1, message: 'Tối thiểu 1' }, integerRule]}
            extra={overStock && <Typography.Text type="warning">Vượt tồn</Typography.Text>}
            style={NO_MARGIN}
          >
            <InputNumber
              min={1}
              style={{ width: '100%' }}
              aria-label={`Số lượng dòng ${field.name + 1}`}
              onPressEnter={focusQuickAdd}
            />
          </Form.Item>
        )
      },
    },
    {
      title: 'Quy ra tấn',
      key: 'tons',
      width: 110,
      align: 'right',
      render: (_, field) => {
        const line = lines?.[field.name]
        const weight = line?.productId === undefined ? undefined : weights.get(line.productId)
        return line?.quantity && weight !== undefined ? formatTons(toTons(line.quantity, weight)) : EMPTY_TEXT
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
          onClick={() => remove(field.name)}
          aria-label={`Xóa dòng ${field.name + 1}`}
        />
      ),
    },
  ]

  return (
    <Form form={form} layout="vertical" initialValues={initialValues} disabled={disabled}>
      <Row gutter={16} align="middle">
        <Col xs={24} md={11}>
          <Form.Item
            name="sourceWarehouseId"
            label="Kho nguồn (chuyển đi)"
            rules={[{ required: true, message: 'Chọn kho nguồn' }]}
            tooltip="Chọn được cả kho đã ngừng hoạt động để rút hết hàng còn lại"
          >
            <Select<number>
              placeholder="Chọn kho nguồn"
              options={sourceOptions}
              showSearch={{ optionFilterProp: 'label' }}
              // Đổi kho nguồn mà kho đích đang trùng → bỏ chọn kho đích thay vì để form ở trạng thái sai
              onChange={(value) => {
                if (value === form.getFieldValue('targetWarehouseId')) form.setFieldValue('targetWarehouseId', undefined)
              }}
            />
          </Form.Item>
        </Col>
        <Col xs={0} md={2} style={{ textAlign: 'center' }}>
          <ArrowRightOutlined aria-hidden />
        </Col>
        <Col xs={24} md={11}>
          <Form.Item
            name="targetWarehouseId"
            label="Kho đích (nhận hàng)"
            dependencies={['sourceWarehouseId']}
            rules={[
              { required: true, message: 'Chọn kho đích' },
              {
                validator: async (_, value?: number) => {
                  if (value !== undefined && value === form.getFieldValue('sourceWarehouseId')) {
                    throw new Error('Kho đích phải khác kho nguồn')
                  }
                },
              },
            ]}
          >
            <Select<number> placeholder="Chọn kho đích" options={targetOptions} showSearch={{ optionFilterProp: 'label' }} />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Form.Item
            name="documentDate"
            label="Ngày điều chuyển"
            tooltip="Được ghi lùi ngày để nhập bù phiếu giấy. Không được chọn ngày tương lai"
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
        <Col xs={24} md={16}>
          <Form.Item name="note" label="Ghi chú">
            <Input.TextArea
              maxLength={500}
              showCount
              autoSize={{ minRows: 1, maxRows: 3 }}
              placeholder="Ví dụ: xe 29H-123.45, lái xe Tuấn"
            />
          </Form.Item>
        </Col>
      </Row>

      <Form.List
        name="lines"
        rules={[
          {
            validator: async (_, value?: TransferLineFormValues[]) => {
              if (!value?.length) throw new Error('Phiếu phải có ít nhất một dòng hàng')
            },
          },
        ]}
      >
        {(fields, { add, remove }, { errors }) => (
          <>
            <ProductQuickAdd
              selectRef={quickAddRef}
              options={quickAddOptions}
              disabled={!sourceWarehouseId}
              loading={loadingStock}
              placeholder={sourceWarehouseId ? undefined : 'Chọn kho nguồn trước để thêm sản phẩm'}
              notFoundContent="Kho nguồn không có hàng phù hợp"
              onPick={(productId) => pickProduct(productId, add)}
            />
            <Table<FormListFieldData>
              size="small"
              rowKey="key"
              columns={lineColumns(remove)}
              dataSource={fields}
              pagination={false}
              scroll={{ x: 'max-content' }}
              summary={() => (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={4} align="right">
                    <Typography.Text type="secondary">Tổng ({fields.length} dòng)</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={4}>
                    <Typography.Text strong>{formatQuantity(draftTotalQuantity(lines))}</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={5} align="right">
                    <Typography.Text strong>{formatTons(totalTons(lines, weights))}</Typography.Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={6} />
                </Table.Summary.Row>
              )}
            />
            <Form.ErrorList errors={errors} />
          </>
        )}
      </Form.List>
    </Form>
  )
}
