import { RollbackOutlined } from '@ant-design/icons'
import { Button, Descriptions, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { formatDate, formatDateTime, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { PAYMENT_METHOD_LABELS, PAYMENT_VERB } from './sales-order-api'
import type { PaymentDirection, PaymentMethod, SalePayment } from './sales-order-api'

interface PaymentSectionProps {
  direction: PaymentDirection
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
  payments: SalePayment[]
  /** Có = phiếu đang Hoàn tất → hiện nút Hoàn ở khoản chưa hoàn */
  onReversePayment?: (payment: SalePayment) => void
}

/**
 * Phần "Thanh toán" trong khung chi tiết phiếu: tổng phiếu / đã thu (trả) / còn nợ + lịch sử. Lịch sử bất biến:
 * khoản ghi nhầm hiện gạch ngang kèm dòng hoàn (BR-SAL-13, BR-SPAY-03).
 */
export function PaymentSection({
  direction,
  totalAmount,
  paidAmount,
  outstandingAmount,
  payments,
  onReversePayment,
}: PaymentSectionProps) {
  const verb = PAYMENT_VERB[direction]

  const columns: TableProps<SalePayment>['columns'] = [
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
          <Tag color="success" variant="filled">{verb} tiền</Tag>
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

  return (
    <>
      <Typography.Title level={5} style={{ margin: 0 }}>
        Thanh toán
      </Typography.Title>
      <Descriptions
        size="small"
        bordered
        column={{ xs: 1, md: 3 }}
        items={[
          { key: 'total', label: 'Tổng phiếu', children: formatVnd(totalAmount) },
          { key: 'paid', label: `Đã ${verb.toLowerCase()}`, children: formatVnd(paidAmount) },
          {
            key: 'outstanding',
            label: 'Còn nợ',
            children: (
              <Typography.Text strong type={outstandingAmount > 0 ? 'danger' : 'success'}>
                {formatVnd(outstandingAmount)}
              </Typography.Text>
            ),
          },
        ]}
      />
      <Table<SalePayment>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={payments}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: `Chưa ${verb.toLowerCase()} khoản nào` }}
      />
    </>
  )
}
