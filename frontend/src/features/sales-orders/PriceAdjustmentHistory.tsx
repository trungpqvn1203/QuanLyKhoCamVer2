import { Card, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { formatDateTime } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import type { SalePriceAdjustment } from './sales-order-api'

const columns: TableProps<SalePriceAdjustment>['columns'] = [
  { title: 'Thời điểm', dataIndex: 'createdAt', width: 150, render: (at: string) => formatDateTime(at) },
  { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
  { title: 'Tên sản phẩm', dataIndex: 'productName' },
  {
    title: 'Đơn giá',
    key: 'price',
    width: 200,
    align: 'right',
    render: (_, row) => `${formatVnd(row.oldUnitPrice)} → ${formatVnd(row.newUnitPrice)}`,
  },
  {
    title: 'Thành tiền',
    key: 'amount',
    width: 220,
    align: 'right',
    render: (_, row) => `${formatVnd(row.oldLineAmount)} → ${formatVnd(row.newLineAmount)}`,
  },
  {
    title: 'Lý do · người sửa',
    key: 'reason',
    render: (_, row) => (
      <>
        {row.reason} <Typography.Text type="secondary">· {row.adjustedBy}</Typography.Text>
      </>
    ),
  },
]

/** Lịch sử điều chỉnh giá sau khi giao (BR-SAL-26) — bất biến, cũ trước mới sau. */
export function PriceAdjustmentHistory({ adjustments }: { adjustments: SalePriceAdjustment[] }) {
  return (
    <Card size="small" title="Lịch sử điều chỉnh giá">
      <Table<SalePriceAdjustment>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={adjustments}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  )
}
