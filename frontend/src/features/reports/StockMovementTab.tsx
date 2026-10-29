import { HistoryOutlined, PrinterOutlined } from '@ant-design/icons'
import { Alert, Button, DatePicker, Flex, Select, Table, Tooltip, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { CodeText } from '../../components/ui/CodeLink'
import { formatDate, formatQuantity, formatTons } from '../../utils/format'
import { DISPLAY_DATE_FORMAT, displayDateToIso } from '../../utils/filters'
import type { Category } from '../products/product-api'
import type { Warehouse } from '../warehouses/warehouse-api'
import { PERIOD_LABELS, presetRange } from './period'
import type { DateRange, PeriodPreset } from './period'
import { reportApi } from './report-api'
import type { StockMovement, StockMovementRow } from './report-api'
import { usePrintReport } from './use-print-report'

interface StockMovementTabProps {
  categories: Category[]
  warehouses: Warehouse[]
}

const PERIOD_OPTIONS = (Object.keys(PERIOD_LABELS) as PeriodPreset[]).map((value) => ({
  value,
  label: PERIOD_LABELS[value],
}))

/**
 * Nhập – xuất – tồn theo kỳ (BR-RPT-05), kiểu báo cáo "Xuất nhập tồn" của KiotViet / MISA: chọn kỳ nhanh
 * (tháng này, tháng trước…) hoặc tự chọn ngày; mỗi SP có tồn đầu, nhập, xuất, tồn cuối (SL + tấn).
 * Bấm biểu tượng lịch sử để xem từng giao dịch tạo nên các con số.
 */
export function StockMovementTab({ categories, warehouses }: StockMovementTabProps) {
  const navigate = useNavigate()
  const [preset, setPreset] = useState<PeriodPreset>('thisMonth')
  const [customRange, setCustomRange] = useState<DateRange | undefined>()
  const [warehouseId, setWarehouseId] = useState<number | undefined>()
  const [categoryId, setCategoryId] = useState<number | undefined>()
  const range = preset === 'custom' ? customRange : presetRange(preset)

  const [result, setResult] = useState<{ key: string; data?: StockMovement; error?: string } | null>(null)
  const key = JSON.stringify({ range, warehouseId, categoryId })
  const loading = range !== undefined && result?.key !== key
  const report = result?.key === key ? result.data : undefined
  const { print, printing } = usePrintReport()

  const from = range?.from
  const to = range?.to
  useEffect(() => {
    if (!from || !to) return
    let ignore = false
    reportApi
      .stockMovement({ from, to, warehouseId, categoryId })
      .then((data) => !ignore && setResult({ key, data }))
      .catch((err: unknown) => {
        if (!ignore) setResult({ key, error: err instanceof ApiError ? err.message : 'Không tải được báo cáo' })
      })
    return () => {
      ignore = true
    }
  }, [from, to, warehouseId, categoryId, key])

  const warehouseText = warehouseId
    ? `Kho: ${warehouses.find((warehouse) => warehouse.id === warehouseId)?.name ?? ''}`
    : 'Tất cả kho'

  const quantityCell = (field: keyof StockMovementRow, strong = false) => ({
    dataIndex: field,
    align: 'right' as const,
    width: 90,
    render: (value: number) =>
      strong ? <Typography.Text strong>{formatQuantity(value)}</Typography.Text> : formatQuantity(value),
  })
  const tonsCell = (field: keyof StockMovementRow) => ({
    dataIndex: field,
    align: 'right' as const,
    width: 110,
    render: (value: number) => <Typography.Text type="secondary">{formatTons(value)}</Typography.Text>,
  })

  const columns: TableProps<StockMovementRow>['columns'] = [
    { title: 'Mã SP', dataIndex: 'productCode', width: 110, fixed: 'left', render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName', width: 220 },
    { title: 'ĐVT', dataIndex: 'unitName', width: 70 },
    { title: 'Tồn đầu kỳ', children: [{ title: 'SL', ...quantityCell('openingQuantity') }, { title: 'Tấn', ...tonsCell('openingTons') }] },
    { title: 'Nhập trong kỳ', children: [{ title: 'SL', ...quantityCell('inboundQuantity') }, { title: 'Tấn', ...tonsCell('inboundTons') }] },
    { title: 'Xuất trong kỳ', children: [{ title: 'SL', ...quantityCell('outboundQuantity') }, { title: 'Tấn', ...tonsCell('outboundTons') }] },
    { title: 'Tồn cuối kỳ', children: [{ title: 'SL', ...quantityCell('closingQuantity', true) }, { title: 'Tấn', ...tonsCell('closingTons') }] },
    {
      title: '',
      key: 'history',
      width: 48,
      fixed: 'right',
      render: (_, row) => (
        <Tooltip title="Xem lịch sử tồn kho của SP này">
          <Button
            type="text"
            size="small"
            icon={<HistoryOutlined />}
            aria-label={`Lịch sử tồn kho ${row.productCode}`}
            onClick={() =>
              navigate(`/stock-transactions?productId=${row.productId}${warehouseId ? `&warehouseId=${warehouseId}` : ''}`)
            }
          />
        </Tooltip>
      ),
    },
  ]

  return (
    <Flex vertical gap={12}>
      <Flex gap={8} wrap align="center">
        <Select<PeriodPreset> options={PERIOD_OPTIONS} value={preset} onChange={setPreset} style={{ width: 150 }} />
        {preset === 'custom' && (
          // Không điều khiển (uncontrolled) để khỏi phụ thuộc dayjs, giống ô ngày của thanh lọc chung
          <DatePicker.RangePicker
            format={DISPLAY_DATE_FORMAT}
            placeholder={['Từ ngày', 'Đến ngày']}
            onChange={(_, [fromText, toText]) => {
              const from = displayDateToIso(fromText)
              const to = displayDateToIso(toText)
              setCustomRange(from && to ? { from, to } : undefined)
            }}
            style={{ width: 260 }}
          />
        )}
        {range && preset !== 'custom' && (
          <Typography.Text type="secondary">
            {formatDate(range.from)} – {formatDate(range.to)}
          </Typography.Text>
        )}
        <Select<number>
          allowClear
          placeholder="Tất cả kho"
          options={warehouses.map((warehouse) => ({
            value: warehouse.id,
            label: `${warehouse.code} · ${warehouse.name}${warehouse.active ? '' : ' (ngừng)'}`,
          }))}
          value={warehouseId}
          onChange={setWarehouseId}
          style={{ width: 220 }}
        />
        <Select<number>
          allowClear
          placeholder="Tất cả danh mục"
          options={categories.map((category) => ({ value: category.id, label: category.name }))}
          value={categoryId}
          onChange={setCategoryId}
          style={{ width: 200 }}
        />
        <Button
          icon={<PrinterOutlined />}
          style={{ marginLeft: 'auto' }}
          disabled={!report || report.rows.length === 0}
          loading={printing}
          onClick={() =>
            report &&
            void print('Báo cáo nhập xuất tồn', ({ StockMovementPrint }, store, printedAt) => (
              <StockMovementPrint report={report} store={store} warehouseText={warehouseText} printedAt={printedAt} />
            ))
          }
        >
          In A4
        </Button>
      </Flex>
      {!range && <Alert type="info" showIcon title="Chọn khoảng ngày để xem báo cáo" />}
      {result?.error && result.key === key && <Alert type="error" showIcon title={result.error} />}
      <Table<StockMovementRow>
        size="small"
        bordered
        rowKey="productId"
        columns={columns}
        dataSource={report?.rows ?? []}
        loading={loading}
        pagination={false}
        scroll={{ x: 'max-content', y: 'calc(100vh - 400px)' }}
        locale={{ emptyText: 'Không có phát sinh và tồn trong kỳ' }}
        summary={() =>
          report && report.rows.length > 0 ? (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={3}>
                  <Typography.Text strong>Tổng tấn · {report.rows.length} SP</Typography.Text>
                </Table.Summary.Cell>
                {(['openingTons', 'inboundTons', 'outboundTons', 'closingTons'] as const).map((field, index) => [
                  <Table.Summary.Cell key={`${field}-q`} index={3 + index * 2} />,
                  <Table.Summary.Cell key={field} index={4 + index * 2} align="right">
                    <Typography.Text strong>{formatTons(report.totals[field])}</Typography.Text>
                  </Table.Summary.Cell>,
                ])}
                <Table.Summary.Cell index={11} />
              </Table.Summary.Row>
            </Table.Summary>
          ) : null
        }
      />
    </Flex>
  )
}
