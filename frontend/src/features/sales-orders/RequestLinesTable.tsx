import { Card, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import type { SaleRequestLine } from './sales-order-api'

const columns: TableProps<SaleRequestLine>['columns'] = [
  { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
  { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
  { title: 'Tên sản phẩm', dataIndex: 'productName' },
  { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
  { title: 'SL khách đặt', dataIndex: 'quantity', width: 110, align: 'right' },
]

/**
 * Phần khách đặt (BR-SAL-20) — chỉ đọc, đặt cạnh dòng phiếu để đối chiếu "khách đặt" với "thực giao"
 * (kho có thể đã đổi SL hoặc đổi SP khi xác nhận).
 */
export function RequestLinesTable({
  lines,
  customerNote,
  title = 'Khách đặt',
}: {
  lines: SaleRequestLine[]
  /** Lời khách ghi trên app (BR-POR-11) — đặt cạnh phần khách đặt để kho đọc lúc xác nhận */
  customerNote?: string
  title?: string
}) {
  return (
    <Card size="small" title={title}>
      {customerNote && (
        <Typography.Paragraph style={{ marginBottom: 8 }}>
          <Typography.Text type="secondary">Khách ghi: </Typography.Text>
          {customerNote}
        </Typography.Paragraph>
      )}
      <Table<SaleRequestLine>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={lines}
        pagination={false}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  )
}
