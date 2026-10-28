import { Alert, Descriptions, Flex, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { formatDate, formatDateTime, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { PaymentSection } from '../sales-orders/PaymentSection'
import type { SalePayment } from '../sales-orders/sales-order-api'
import type { InboundReceipt, InboundReceiptLine } from './inbound-receipt-api'

/** Dòng giải thích trạng thái ở đầu khung: người xem biết phiếu đã tác động tồn kho hay chưa. */
function StatusNotice({ receipt }: { receipt: InboundReceipt }) {
  switch (receipt.status) {
    case 'COMPLETED':
      return (
        <Alert
          type="success"
          showIcon
          title={`Hoàn tất lúc ${formatDateTime(receipt.completedAt!)} — tồn kho ${receipt.warehouseName} đã được cộng`}
          description="Phiếu đã khóa, không sửa được. Nếu sai, dùng Đảo phiếu rồi tạo phiếu đúng."
        />
      )
    case 'REVERSED':
      return (
        <Alert
          type="warning"
          showIcon
          title={`Đã đảo lúc ${formatDateTime(receipt.reversedAt!)} — tồn kho đã trừ lại số đã nhập`}
          description={`Lý do: ${orEmpty(receipt.reason)}`}
        />
      )
    case 'CANCELLED':
      return (
        <Alert
          type="info"
          showIcon
          title={`Đã hủy lúc ${formatDateTime(receipt.cancelledAt!)} — phiếu chưa từng tác động tồn kho`}
          description={receipt.reason ? `Lý do: ${receipt.reason}` : undefined}
        />
      )
    default:
      return null
  }
}

interface InboundReceiptViewProps {
  receipt: InboundReceipt
  /** Có = phiếu đang Hoàn tất → hiện nút Hoàn ở khoản trả chưa hoàn */
  onReversePayment?: (payment: SalePayment) => void
}

/** Phiếu ở trạng thái không sửa được (Hoàn tất / Đã hủy / Đã đảo): chỉ xem, kèm trả tiền NCC (BR-SPAY). */
export function InboundReceiptView({ receipt, onReversePayment }: InboundReceiptViewProps) {
  const showPayments = (receipt.status === 'COMPLETED' && receipt.tracksDebt) || receipt.payments.length > 0

  const columns: TableProps<InboundReceiptLine>['columns'] = [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
    { title: 'SL đặt', dataIndex: 'orderedQuantity', width: 90, align: 'right' },
    {
      title: 'SL thực nhận',
      dataIndex: 'receivedQuantity',
      width: 110,
      align: 'right',
      render: (received: number, line) =>
        received === line.orderedQuantity ? (
          received
        ) : (
          // Lệch so với SL đặt → tô màu để dễ thấy NCC giao thiếu/thừa
          <Typography.Text type="warning">{received}</Typography.Text>
        ),
    },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 130, align: 'right', render: (price: number) => formatVnd(price) },
    { title: 'Thành tiền', dataIndex: 'lineAmount', width: 140, align: 'right', render: (amount: number) => formatVnd(amount) },
  ]

  return (
    <Flex vertical gap={16}>
      <StatusNotice receipt={receipt} />
      <Descriptions
        bordered
        size="small"
        column={{ xs: 1, md: 2 }}
        items={[
          { key: 'code', label: 'Mã phiếu', children: <CodeText code={receipt.code} /> },
          { key: 'date', label: 'Ngày chứng từ', children: formatDate(receipt.documentDate) },
          { key: 'supplier', label: 'Nhà cung cấp', children: `${receipt.supplierCode} · ${receipt.supplierName}` },
          { key: 'warehouse', label: 'Kho nhận', children: `${receipt.warehouseCode} · ${receipt.warehouseName}` },
          { key: 'note', label: 'Ghi chú', span: 'filled', children: orEmpty(receipt.note) },
          { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(receipt.createdAt) },
          { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(receipt.updatedAt) },
        ]}
      />
      <Table<InboundReceiptLine>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={receipt.lines}
        pagination={false}
        scroll={{ x: 'max-content' }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={7} align="right">
              <Typography.Text type="secondary">Tổng tiền</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={7} align="right">
              <Typography.Text strong>{formatVnd(receipt.totalAmount)}</Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
      {receipt.status === 'COMPLETED' && !receipt.tracksDebt && (
        <Alert
          type="info"
          showIcon
          title="Phiếu hoàn tất trước khi có công nợ nhà cung cấp — coi như đã trả đủ, không ghi trả tiền"
        />
      )}
      {showPayments && (
        <PaymentSection
          direction="pay"
          totalAmount={receipt.totalAmount}
          paidAmount={receipt.paidAmount}
          outstandingAmount={receipt.outstandingAmount}
          payments={receipt.payments}
          onReversePayment={onReversePayment}
        />
      )}
    </Flex>
  )
}
