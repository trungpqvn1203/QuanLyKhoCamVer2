import { Alert, Descriptions, Flex, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { EMPTY_TEXT, formatDate, formatDateTime, formatQuantity, formatTons, orEmpty } from '../../utils/format'
import type { Transfer, TransferLine } from './transfer-api'
import { toTons, totalTons } from './transfer-form'

/** Dòng giải thích trạng thái: hàng đã chuyển hay chưa. */
function StatusNotice({ transfer }: { transfer: Transfer }) {
  switch (transfer.status) {
    case 'COMPLETED':
      return (
        <Alert
          type="success"
          showIcon
          title={`Đã hoàn tất lúc ${formatDateTime(transfer.completedAt!)} — hàng đã rời ${transfer.sourceWarehouseName} và vào ${transfer.targetWarehouseName}`}
          description="Phiếu đã khóa, không sửa được. Nếu chuyển nhầm, dùng Đảo phiếu để đưa hàng về kho nguồn (chỉ được khi kho đích còn đủ hàng)."
        />
      )
    case 'REVERSED':
      return (
        <Alert
          type="warning"
          showIcon
          title={`Đã đảo lúc ${formatDateTime(transfer.reversedAt!)} — hàng đã về lại ${transfer.sourceWarehouseName}`}
          description={`Lý do: ${orEmpty(transfer.reason)}`}
        />
      )
    case 'CANCELLED':
      return (
        <Alert
          type="info"
          showIcon
          title={`Đã hủy lúc ${formatDateTime(transfer.cancelledAt!)} — phiếu chưa từng tác động tồn kho`}
          description={transfer.reason ? `Lý do: ${transfer.reason}` : undefined}
        />
      )
    default:
      return null
  }
}

/** Phiếu không còn sửa được (Hoàn tất / Đã hủy / Đã đảo): chỉ xem. */
export function TransferView({ transfer }: { transfer: Transfer }) {
  const columns: TableProps<TransferLine>['columns'] = [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
    { title: 'Số lượng', dataIndex: 'quantity', width: 110, align: 'right', render: (quantity: number) => formatQuantity(quantity) },
    {
      title: 'Quy ra tấn',
      key: 'tons',
      width: 110,
      align: 'right',
      render: (_, line) => formatTons(toTons(line.quantity, line.netWeightKg)),
    },
  ]

  return (
    <Flex vertical gap={16}>
      <StatusNotice transfer={transfer} />
      <Descriptions
        bordered
        size="small"
        column={{ xs: 1, md: 2 }}
        items={[
          { key: 'code', label: 'Mã phiếu', children: <CodeText code={transfer.code} /> },
          { key: 'date', label: 'Ngày điều chuyển', children: formatDate(transfer.documentDate) },
          {
            key: 'source',
            label: 'Kho nguồn',
            children: `${transfer.sourceWarehouseCode} · ${transfer.sourceWarehouseName}`,
          },
          {
            key: 'target',
            label: 'Kho đích',
            children: `${transfer.targetWarehouseCode} · ${transfer.targetWarehouseName}`,
          },
          { key: 'note', label: 'Ghi chú', span: 'filled', children: orEmpty(transfer.note) },
          {
            key: 'createdBy',
            label: 'Người lập',
            children: `${orEmpty(transfer.createdBy)} · ${formatDateTime(transfer.createdAt)}`,
          },
          {
            key: 'completedBy',
            label: 'Người hoàn tất',
            children: transfer.completedAt
              ? `${orEmpty(transfer.completedBy)} · ${formatDateTime(transfer.completedAt)}`
              : EMPTY_TEXT,
          },
        ]}
      />
      <Table<TransferLine>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={transfer.lines}
        pagination={false}
        scroll={{ x: 'max-content' }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={4} align="right">
              <Typography.Text type="secondary">Tổng ({transfer.lineCount} dòng)</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={4} align="right">
              <Typography.Text strong>{formatQuantity(transfer.totalQuantity)}</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={5} align="right">
              <Typography.Text strong>{formatTons(totalTons(transfer.lines))}</Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Flex>
  )
}
