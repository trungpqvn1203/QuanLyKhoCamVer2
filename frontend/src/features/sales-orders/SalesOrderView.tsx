import { RollbackOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Flex, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { CodeText } from '../../components/ui/CodeLink'
import { formatDate, formatDateTime, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { PRODUCT_UNIT_LABELS } from '../products/product-api'
import type { ProductUnit } from '../products/product-api'
import { PAYMENT_METHOD_LABELS } from './sales-order-api'
import type { PaymentMethod, SalePayment, SalesOrder, SalesOrderLine } from './sales-order-api'

function StatusNotice({ order }: { order: SalesOrder }) {
  switch (order.status) {
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
          title={`Đã hủy lúc ${formatDateTime(order.cancelledAt!)} — phiếu chưa từng tác động tồn kho`}
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

/** Phiếu không sửa được (Hoàn tất / Đã hủy / Đã đảo): chỉ xem, kèm thanh toán và lịch sử thanh toán. */
export function SalesOrderView({ order, onReversePayment }: SalesOrderViewProps) {
  const lineColumns: TableProps<SalesOrderLine>['columns'] = [
    { title: '#', key: 'index', width: 40, align: 'center', render: (_, __, index) => index + 1 },
    { title: 'Mã SP', dataIndex: 'productCode', width: 100, render: (code: string) => <CodeText code={code} /> },
    { title: 'Tên sản phẩm', dataIndex: 'productName' },
    { title: 'ĐVT', dataIndex: 'unit', width: 64, render: (unit: ProductUnit) => PRODUCT_UNIT_LABELS[unit] },
    { title: 'Số lượng', dataIndex: 'quantity', width: 90, align: 'right' },
    { title: 'Đơn giá', dataIndex: 'unitPrice', width: 130, align: 'right', render: (price: number) => formatVnd(price) },
    { title: 'Thành tiền', dataIndex: 'lineAmount', width: 140, align: 'right', render: (amount: number) => formatVnd(amount) },
  ]

  const paymentColumns: TableProps<SalePayment>['columns'] = [
    { title: 'Ngày', dataIndex: 'paymentDate', width: 110, render: (date: string) => formatDate(date) },
    {
      title: 'Loại',
      dataIndex: 'type',
      width: 110,
      render: (type: SalePayment['type'], payment) =>
        type === 'REVERSAL' ? (
          <Tag color="warning" variant="filled">Hoàn tiền</Tag>
        ) : payment.reversed ? (
          <Tag variant="filled">Đã hoàn</Tag>
        ) : (
          <Tag color="success" variant="filled">Thu tiền</Tag>
        ),
    },
    { title: 'Hình thức', dataIndex: 'method', width: 120, render: (method: PaymentMethod) => PAYMENT_METHOD_LABELS[method] },
    {
      title: 'Số tiền',
      dataIndex: 'amount',
      width: 140,
      align: 'right',
      render: (amount: number, payment) =>
        payment.type === 'REVERSAL' ? (
          <Typography.Text type="warning">−{formatVnd(amount)}</Typography.Text>
        ) : (
          <Typography.Text delete={payment.reversed}>{formatVnd(amount)}</Typography.Text>
        ),
    },
    { title: 'Ghi chú / lý do', dataIndex: 'note', render: (note?: string) => orEmpty(note) },
    { title: 'Ghi lúc', dataIndex: 'createdAt', width: 150, render: (at: string) => formatDateTime(at) },
    ...(onReversePayment
      ? [
          {
            title: '',
            key: 'actions',
            width: 90,
            align: 'center' as const,
            render: (_: unknown, payment: SalePayment) =>
              payment.type === 'PAYMENT' &&
              !payment.reversed && (
                <Button size="small" type="link" danger icon={<RollbackOutlined />} onClick={() => onReversePayment(payment)}>
                  Hoàn
                </Button>
              ),
          },
        ]
      : []),
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
          { key: 'warehouse', label: 'Kho xuất', children: `${order.warehouseCode} · ${order.warehouseName}` },
          { key: 'note', label: 'Ghi chú', span: 'filled', children: orEmpty(order.note) },
          { key: 'createdAt', label: 'Ngày tạo', children: formatDateTime(order.createdAt) },
          { key: 'updatedAt', label: 'Cập nhật lần cuối', children: formatDateTime(order.updatedAt) },
        ]}
      />
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

      {showPayments && (
        <>
          <Typography.Title level={5} style={{ margin: 0 }}>
            Thanh toán
          </Typography.Title>
          <Descriptions
            size="small"
            bordered
            column={{ xs: 1, md: 3 }}
            items={[
              { key: 'total', label: 'Tổng phiếu', children: formatVnd(order.totalAmount) },
              { key: 'paid', label: 'Đã thu', children: formatVnd(order.paidAmount) },
              {
                key: 'outstanding',
                label: 'Còn nợ',
                children: (
                  <Typography.Text strong type={order.outstandingAmount > 0 ? 'danger' : 'success'}>
                    {formatVnd(order.outstandingAmount)}
                  </Typography.Text>
                ),
              },
            ]}
          />
          <Table<SalePayment>
            size="small"
            rowKey="id"
            columns={paymentColumns}
            dataSource={order.payments}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: 'Chưa thu khoản nào' }}
          />
        </>
      )}
    </Flex>
  )
}
