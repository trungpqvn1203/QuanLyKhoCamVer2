import { RollbackOutlined } from '@ant-design/icons'
import { Button, Descriptions, Table, Tag, Typography } from 'antd'
import type { TableProps } from 'antd'
import { formatDate, formatDateTime, orEmpty } from '../../utils/format'
import { formatVnd } from '../../utils/money'
import { PAYMENT_METHOD_LABELS, PAYMENT_VERB } from './sales-order-api'
import type { PaymentDirection } from './sales-order-api'

/** Một dòng thanh toán, đủ cho cả thu tiền phiếu bán, trả NCC (PaymentMethod) và trả lương (có thêm trừ tạm ứng). */
export interface PaymentRow {
  id: number
  type: 'PAYMENT' | 'REVERSAL'
  method: string
  amount: number
  paymentDate: string
  note?: string
  /** Khoản này đã bị hoàn → không hoàn lại được */
  reversed: boolean
  createdAt: string
}

interface PaymentSectionProps<P extends PaymentRow> {
  direction: PaymentDirection
  totalAmount: number
  paidAmount: number
  outstandingAmount: number
  payments: P[]
  /** Nhãn cột "Tổng …": mặc định "Tổng phiếu"; trả lương dùng "Tổng lương" */
  totalLabel?: string
  /** Nhãn hình thức; mặc định tiền mặt / chuyển khoản. Trả lương thêm "Trừ tạm ứng" */
  methodLabels?: Record<string, string>
  /** Có = phiếu đang Hoàn tất → hiện nút Hoàn ở khoản chưa hoàn */
  onReversePayment?: (payment: P) => void
}

/**
 * Phần "Thanh toán" trong khung chi tiết phiếu: tổng phiếu / đã thu (trả) / còn nợ + lịch sử. Lịch sử bất biến:
 * khoản ghi nhầm hiện gạch ngang kèm dòng hoàn (BR-SAL-13, BR-SPAY-03).
 */
export function PaymentSection<P extends PaymentRow>({
  direction,
  totalAmount,
  paidAmount,
  outstandingAmount,
  payments,
  totalLabel = 'Tổng phiếu',
  methodLabels = PAYMENT_METHOD_LABELS,
  onReversePayment,
}: PaymentSectionProps<P>) {
  const verb = PAYMENT_VERB[direction]

  const columns: TableProps<P>['columns'] = [
    { title: 'Ngày', dataIndex: 'paymentDate', width: 110, render: (date: string) => formatDate(date) },
    {
      title: 'Loại',
      dataIndex: 'type',
      width: 110,
      render: (type: P['type'], payment) =>
        type === 'REVERSAL' ? (
          <Tag color="warning" variant="filled">Hoàn tiền</Tag>
        ) : payment.reversed ? (
          <Tag variant="filled">Đã hoàn</Tag>
        ) : (
          <Tag color="success" variant="filled">{verb} tiền</Tag>
        ),
    },
    { title: 'Hình thức', dataIndex: 'method', width: 120, render: (method: string) => methodLabels[method] ?? method },
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
            render: (_: unknown, payment: P) =>
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
          { key: 'total', label: totalLabel, children: formatVnd(totalAmount) },
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
      <Table<P>
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
