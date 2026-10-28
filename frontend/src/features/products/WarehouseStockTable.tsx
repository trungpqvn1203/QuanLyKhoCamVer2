import { Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { EMPTY_TEXT, formatDateTime, formatQuantity, formatTons } from '../../utils/format'
import type { ProductStockDetail, WarehouseStock } from './product-stock-api'

interface WarehouseStockTableProps {
  detail: ProductStockDetail
  /** Bản gọn cho popover: bỏ cột tấn và thời điểm cập nhật */
  compact?: boolean
}

/**
 * Tồn của một SP theo từng kho + dòng tổng. Dùng ở popover cột "Tồn kho" và tab "Tồn kho" của khung chi tiết.
 * Kho đã ngừng vẫn hiện và vẫn cộng vào tổng — hàng còn nằm đó đến khi điều chuyển đi (BR-STK-01).
 */
export function WarehouseStockTable({ detail, compact = false }: WarehouseStockTableProps) {
  const unit = detail.unitName.toLowerCase()

  const columns: TableProps<WarehouseStock>['columns'] = [
    {
      title: 'Kho',
      key: 'warehouse',
      render: (_, row) => (
        <>
          <CodeText code={row.warehouseCode} /> · {row.warehouseName}{' '}
          {!row.warehouseActive && (
            <Tag variant="filled" style={{ marginInlineEnd: 0 }}>
              Ngừng hoạt động
            </Tag>
          )}
        </>
      ),
    },
    {
      title: `Tồn (${unit})`,
      dataIndex: 'quantity',
      align: 'right',
      width: 100,
      render: (quantity: number) => formatQuantity(quantity),
    },
    ...(compact
      ? []
      : [
          {
            title: 'Quy đổi',
            dataIndex: 'tons',
            align: 'right' as const,
            width: 110,
            render: (tons: number) => formatTons(tons),
          },
        ]),
    {
      title: 'Đang về',
      dataIndex: 'incomingQuantity',
      align: 'right',
      width: 90,
      render: (incoming: number) =>
        incoming > 0 ? <Typography.Text type="secondary">+{formatQuantity(incoming)}</Typography.Text> : EMPTY_TEXT,
    },
    ...(compact
      ? []
      : [
          {
            title: 'Cập nhật tồn',
            dataIndex: 'updatedAt',
            width: 150,
            render: (updatedAt?: string) => (updatedAt ? formatDateTime(updatedAt) : EMPTY_TEXT),
          },
        ]),
  ]

  return (
    <Table<WarehouseStock>
      size="small"
      rowKey="warehouseId"
      columns={columns}
      dataSource={detail.warehouses}
      pagination={false}
      locale={{ emptyText: 'Chưa có hàng ở kho nào' }}
      summary={() =>
        detail.warehouses.length > 1 && (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>
              <Typography.Text strong>Tổng</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={1} align="right">
              <Typography.Text strong>{formatQuantity(detail.stockQuantity)}</Typography.Text>
            </Table.Summary.Cell>
            {!compact && (
              <Table.Summary.Cell index={2} align="right">
                {formatTons(detail.stockTons)}
              </Table.Summary.Cell>
            )}
            <Table.Summary.Cell index={compact ? 2 : 3} align="right">
              {detail.incomingQuantity > 0 ? `+${formatQuantity(detail.incomingQuantity)}` : EMPTY_TEXT}
            </Table.Summary.Cell>
            {!compact && <Table.Summary.Cell index={4} />}
          </Table.Summary.Row>
        )
      }
    />
  )
}
