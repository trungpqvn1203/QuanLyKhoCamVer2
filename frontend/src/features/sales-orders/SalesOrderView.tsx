import { Alert, Descriptions, Flex, Table, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { EMPTY_TEXT, formatDate, formatDateTime, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { PaymentSection } from './PaymentSection'
import { RequestLinesTable } from './RequestLinesTable'
import { ORDER_CHANNEL_LABELS } from './sales-order-api'
import type { SalePayment, SalesOrder, SalesOrderLine } from './sales-order-api'

function StatusNotice({ order }: { order: SalesOrder }) {
  switch (order.status) {
    case 'PENDING':
      return (
        <Alert
          type="warning"
          showIcon
          title="Đơn khách đặt đang chờ xác nhận"
          description="Gọi lại khách để chốt số lượng, giá và kho xuất. Đơn chưa ảnh hưởng tồn kho."
        />
      )
    case 'COMPLETED':
      return (
        <Alert
          type="success"
          showIcon
          title={`Hoàn tất lúc ${formatDateTime(order.completedAt!)} — tồn kho ${order.warehouseName} đã được trừ`}
          description="Phiếu đã khóa. Giao sai hoặc hàng không đạt: dùng Đảo phiếu (phiếu đã thu tiền phải hoàn các khoản thu trước)."
        />
      )
    case 'REVERSED':
      return (
        <Alert
          type="warning"
          showIcon
          title={`Đã đảo lúc ${formatDateTime(order.reversedAt!)} — hàng đã về lại kho`}
          description={`Lý do: ${orEmpty(order.reason)}`}
        />
      )
    case 'CANCELLED':
      return (
        <Alert
          type="info"
          showIcon
          title={`Đã hủy lúc ${formatDateTime(order.cancelledAt!)} — ${order.confirmedAt ? 'phiếu' : 'đơn'} chưa từng tác động tồn kho`}
          description={order.reason ? `Lý do: ${order.reason}` : undefined}
        />
      )
    default:
      return null
  }
}

interface SalesOrderViewProps {
  order: SalesOrder
  /** Có = phiếu đang Hoàn tất → hiện nút Hoàn ở khoản thu chưa hoàn */
  onReversePayment?: (payment: SalePayment) => void
}

/**
 * Phiếu không sửa được (Hoàn tất / Đã hủy / Đã đảo — hoặc người chỉ có quyền xem): chỉ xem, kèm thanh toán
 * và lịch sử thanh toán. Phiếu từ đơn khách đặt có thêm bảng "Khách đặt" để đối chiếu (BR-SAL-20).
 */
export function SalesOrderView({ order, onReversePayment }: SalesOrderViewProps) {
  const lineColumns: TableProps<SalesOrderLine>['columns'] = [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unitName', width: 64 },
    { title: 'Số lượng', dataIndex: 'quantity', width: 90, align: 'right' },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 130, align: 'right', render: (price: number) => formatVnd(price) },
    { title: 'Thành tiền', dataIndex: 'lineAmount', width: 140, align: 'right', render: (amount: number) => formatVnd(amount) },
  ]

  const showPayments = order.status === 'COMPLETED' || order.payments.length > 0

  return (
    <Flex vertical gap={16}>
      <StatusNotice order={order} />
      <Descriptions
        bordered
        size="small"
        column={{ xs: 1, md: 2 }}
        items={[
          { key: 'code', label: 'Mã phiếu', children: <CodeText code={order.code} /> },
          { key: 'date', label: 'Ngày bán', children: formatDate(order.documentDate) },
          {
            key: 'customer',
            label: 'Khách hàng',
            children: `${order.customerCode} · ${order.customerName} · ${order.customerPhone}`,
          },
          {
            key: 'warehouse',
            label: 'Kho xuất',
            children: order.warehouseCode ? `${order.warehouseCode} · ${order.warehouseName}` : EMPTY_TEXT,
          },
          { key: 'channel', label: 'Khách đặt qua', children: ORDER_CHANNEL_LABELS[order.orderChannel] },
          {
            key: 'delivery',
            label: 'Ngày muốn giao',
            children: order.requestedDeliveryDate ? formatDate(order.requestedDeliveryDate) : EMPTY_TEXT,
          },
          { key: 'note', label: 'Ghi chú', span: 'filled', children: orEmpty(order.note) },
          {
            key: 'createdAt',
            label: 'Ngày tạo',
            children: `${formatDateTime(order.createdAt)}${order.createdBy ? ` · ${order.createdBy}` : ''}`,
          },
          ...(order.confirmedAt
            ? [{ key: 'confirmedAt', label: 'Xác nhận đơn', children: `${formatDateTime(order.confirmedAt)} · ${order.confirmedBy}` }]
            : []),
          { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(order.updatedAt) },
        ]}
      />
      {order.requestLines.length > 0 && <RequestLinesTable lines={order.requestLines} />}
      {order.lines.length > 0 && (
        <Table<SalesOrderLine>
          size="small"
          rowKey="id"
          columns={lineColumns}
          dataSource={order.lines}
          pagination={false}
          scroll={{ x: 'max-content' }}
          summary={() => (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={6} align="right">
                <Typography.Text type="secondary">Tổng tiền</Typography.Text>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={6} align="right">
                <Typography.Text strong>{formatVnd(order.totalAmount)}</Typography.Text>
              </Table.Summary.Cell>
            </Table.Summary.Row>
          )}
        />
      )}

      {showPayments && (
        <PaymentSection
          direction="receive"
          totalAmount={order.totalAmount}
          paidAmount={order.paidAmount}
          outstandingAmount={order.outstandingAmount}
          payments={order.payments}
          onReversePayment={onReversePayment}
        />
      )}
    </Flex>
  )
}
