import { PrinterOutlined } from '@ant-design/icons'
import { Alert, Button, Flex, Input, Select, Switch, Table, Tooltip, Typography } from 'antd'
import type { TableProps } from 'antd'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { CodeText } from '../../components/ui/CodeLink'
import { formatQuantity, formatTons } from '../../utils/format'
import type { Category } from '../products/product-api'
import { reportApi } from './report-api'
import type { StockByWarehouse, StockByWarehouseFilters } from './report-api'
import { usePrintReport } from './use-print-report'

type Row = StockByWarehouse['rows'][number]

interface StockByWarehouseTabProps {
  categories: Category[]
}

/**
 * Tồn hiện tại dạng bảng SP × kho (BR-RPT-04) — nhìn một lần biết hàng nằm ở kho nào.
 * Báo cáo không phân trang: cửa hàng có vài trăm SP, cần thấy hết để đối chiếu và in.
 */
export function StockByWarehouseTab({ categories }: StockByWarehouseTabProps) {
  const [filters, setFilters] = useState<StockByWarehouseFilters>({ hideZero: true })
  const [keywordInput, setKeywordInput] = useState('')
  // Kết quả gắn với bộ lọc đã gửi → đang tải = kết quả chưa khớp bộ lọc hiện tại (không cần setState trong effect)
  const [result, setResult] = useState<{ key: string; data?: StockByWarehouse; error?: string } | null>(null)
  const key = JSON.stringify(filters)
  const loading = result?.key !== key
  const report = result?.key === key ? result.data : undefined
  const { print, printing } = usePrintReport()

  useEffect(() => {
    let ignore = false
    reportApi
      .stockByWarehouse(filters)
      .then((data) => !ignore && setResult({ key, data }))
      .catch((err: unknown) => {
        if (!ignore) setResult({ key, error: err instanceof ApiError ? err.message : 'Không tải được báo cáo' })
      })
    return () => {
      ignore = true
    }
  }, [filters, key])

  // Gõ xong 400 ms mới lọc, như ô tìm kiếm các màn danh sách
  useEffect(() => {
    const keyword = keywordInput.trim() || undefined
    if (keyword === filters.keyword) return
    const timer = setTimeout(() => setFilters((current) => ({ ...current, keyword })), 400)
    return () => clearTimeout(timer)
  }, [keywordInput, filters.keyword])

  const filterText = () => {
    const category = categories.find((item) => item.id === filters.categoryId)
    const parts = [category ? `Danh mục: ${category.name}` : 'Mọi danh mục']
    if (filters.keyword) parts.push(`Tìm: "${filters.keyword}"`)
    parts.push(filters.hideZero === false ? 'Gồm cả SP hết hàng' : 'Chỉ SP còn hàng')
    return parts.join(' · ')
  }

  const warehouseColumns: NonNullable<TableProps<Row>['columns']> = (report?.warehouses ?? []).map((warehouse) => ({
    title: (
      <Tooltip title={warehouse.name}>
        <span>
          {warehouse.code}
          {!warehouse.active && ' (ngừng)'}
        </span>
      </Tooltip>
    ),
    key: `w${warehouse.id}`,
    align: 'right',
    width: 110,
    render: (_, row) => {
      const quantity = row.quantities[warehouse.id]
      return quantity ? formatQuantity(quantity) : <Typography.Text type="secondary">—</Typography.Text>
    },
  }))

  const columns: TableProps<Row>['columns'] = [
    { title: 'Mã SP', dataIndex: 'productCode', width: 110, fixed: 'left', render: (code: string) => <CodeText code={code} /> },
    {
      title: 'Tên sản phẩm',
      dataIndex: 'productName',
      width: 240,
      render: (name: string, row) => (
        <>
          {name}
          {!row.productActive && <Typography.Text type="secondary"> (ngừng KD)</Typography.Text>}
        </>
      ),
    },
    { title: 'ĐVT', dataIndex: 'unitName', width: 70 },
    ...warehouseColumns,
    {
      title: 'Tổng SL',
      dataIndex: 'totalQuantity',
      align: 'right',
      width: 110,
      render: (quantity: number) => <Typography.Text strong>{formatQuantity(quantity)}</Typography.Text>,
    },
    { title: 'Tổng tấn', dataIndex: 'totalTons', align: 'right', width: 120, render: (tons: number) => formatTons(tons) },
  ]

  return (
    <Flex vertical gap={12}>
      <Flex gap={8} wrap align="center">
        <Input.Search
          allowClear
          placeholder="Tìm mã hoặc tên sản phẩm"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
          style={{ width: 260 }}
        />
        <Select<number>
          allowClear
          placeholder="Tất cả danh mục"
          options={categories.map((category) => ({ value: category.id, label: category.name }))}
          value={filters.categoryId}
          onChange={(categoryId) => setFilters((current) => ({ ...current, categoryId }))}
          style={{ width: 200 }}
        />
        <Flex gap={6} align="center">
          <Switch
            size="small"
            checked={filters.hideZero !== false}
            onChange={(hideZero) => setFilters((current) => ({ ...current, hideZero }))}
            aria-label="Ẩn sản phẩm hết hàng"
          />
          <span>Ẩn SP hết hàng</span>
        </Flex>
        <Button
          icon={<PrinterOutlined />}
          style={{ marginLeft: 'auto' }}
          disabled={!report || report.rows.length === 0}
          loading={printing}
          onClick={() =>
            report &&
            void print('Báo cáo tồn kho theo kho', ({ StockByWarehousePrint }, store, printedAt) => (
              <StockByWarehousePrint report={report} store={store} filterText={filterText()} printedAt={printedAt} />
            ))
          }
        >
          In A4
        </Button>
      </Flex>
      {result?.error && result.key === key && <Alert type="error" showIcon title={result.error} />}
      <Table<Row>
        size="small"
        rowKey="productId"
        columns={columns}
        dataSource={report?.rows ?? []}
        loading={loading}
        pagination={false}
        scroll={{ x: 'max-content', y: 'calc(100vh - 360px)' }}
        locale={{ emptyText: 'Không có sản phẩm nào khớp' }}
        summary={() =>
          report && report.rows.length > 0 ? (
            <Table.Summary fixed>
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={3}>
                  <Typography.Text strong>Tổng tấn · {report.rows.length} SP</Typography.Text>
                </Table.Summary.Cell>
                {report.warehouses.map((warehouse, index) => (
                  <Table.Summary.Cell key={warehouse.id} index={3 + index} align="right">
                    {formatTons(report.warehouseTons[warehouse.id] ?? 0)}
                  </Table.Summary.Cell>
                ))}
                <Table.Summary.Cell index={3 + report.warehouses.length} />
                <Table.Summary.Cell index={4 + report.warehouses.length} align="right">
                  <Typography.Text strong>{formatTons(report.totalTons)}</Typography.Text>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            </Table.Summary>
          ) : null
        }
      />
    </Flex>
  )
}
