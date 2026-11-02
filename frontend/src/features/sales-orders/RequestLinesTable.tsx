import { Card, Table } from 'antd'
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
export function RequestLinesTable({ lines, title = 'Khách đặt' }: { lines: SaleRequestLine[]; title?: string }) {
  return (
    <Card size="small" title={title}>
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
