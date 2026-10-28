import { Alert, Descriptions, Flex, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { EMPTY_TEXT, formatDate, formatDateTime, formatQuantity, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import type { OpeningStock, OpeningStockLine } from './opening-stock-api'

/** Dòng giải thích trạng thái: phiếu đã cộng tồn hay chưa. */
function StatusNotice({ document }: { document: OpeningStock }) {
  switch (document.status) {
    case 'COMPLETED':
      return (
        <Alert
          type="success"
          showIcon
          title={`Đã xác nhận lúc ${formatDateTime(document.completedAt!)} — tồn kho ${document.warehouseName} đã được cộng`}
          description="Phiếu đã khóa, không sửa được. Nếu khai sai, dùng Đảo phiếu rồi lập phiếu đúng (chỉ được khi hàng chưa bán bớt)."
        />
      )
    case 'REVERSED':
      return (
        <Alert
          type="warning"
          showIcon
          title={`Đã đảo lúc ${formatDateTime(document.reversedAt!)} — tồn kho đã trừ lại số đã khai`}
          description={`Lý do: ${orEmpty(document.reason)}. Các sản phẩm trên phiếu được khai đầu kỳ lại.`}
        />
      )
    case 'CANCELLED':
      return (
        <Alert
          type="info"
          showIcon
          title={`Đã hủy lúc ${formatDateTime(document.cancelledAt!)} — phiếu chưa từng tác động tồn kho`}
          description={document.reason ? `Lý do: ${document.reason}` : undefined}
        />
      )
    default:
      return null
  }
}

/** Phiếu không còn sửa được (Hoàn tất / Đã hủy / Đã đảo): chỉ xem. */
export function OpeningStockView({ document }: { document: OpeningStock }) {
  const columns: TableProps<OpeningStockLine>['columns'] = [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
    { title: 'Số lượng', dataIndex: 'quantity', width: 110, align: 'right', render: (quantity: number) => formatQuantity(quantity) },
    {
      title: 'Đơn giá',
      dataIndex: 'unitPrice',
      width: 130,
      align: 'right',
      // 0 = không khai (BR-OPN-10)
      render: (price: number) => (price === 0 ? EMPTY_TEXT : formatVnd(price)),
    },
    {
      title: 'Giá trị',
      dataIndex: 'lineAmount',
      width: 140,
      align: 'right',
      render: (amount: number, line) => (line.unitPrice === 0 ? EMPTY_TEXT : formatVnd(amount)),
    },
  ]

  return (
    <Flex vertical gap={16}>
      <StatusNotice document={document} />
      <Descriptions
        bordered
        size="small"
        column={{ xs: 1, md: 2 }}
        items={[
          { key: 'code', label: 'Mã phiếu', children: <CodeText code={document.code} /> },
          { key: 'date', label: 'Ngày tồn đầu kỳ', children: formatDate(document.documentDate) },
          { key: 'warehouse', label: 'Kho', span: 'filled', children: `${document.warehouseCode} · ${document.warehouseName}` },
          { key: 'note', label: 'Ghi chú', span: 'filled', children: orEmpty(document.note) },
          {
            key: 'createdBy',
            label: 'Người lập',
            children: `${orEmpty(document.createdBy)} · ${formatDateTime(document.createdAt)}`,
          },
          {
            key: 'completedBy',
            label: 'Người xác nhận',
            children: document.completedAt
              ? `${orEmpty(document.completedBy)} · ${formatDateTime(document.completedAt)}`
              : EMPTY_TEXT,
          },
        ]}
      />
      <Table<OpeningStockLine>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={document.lines}
        pagination={false}
        scroll={{ x: 'max-content' }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={4} align="right">
              <Typography.Text type="secondary">Tổng ({document.lineCount} dòng)</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={4} align="right">
              <Typography.Text strong>{formatQuantity(document.totalQuantity)}</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={5} />
            <Table.Summary.Cell index={6} align="right">
              <Typography.Text strong>{document.totalAmount === 0 ? EMPTY_TEXT : formatVnd(document.totalAmount)}</Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Flex>
  )
}
