import { Col, Form, Input, InputNumber, Row, Typography } from 'antd'
import type { FormRule as Rule } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { todayIso } from '../../utils/format'
import { formatPriceInput, formatVnd, parsePriceInput } from '../../utils/money'
import { PAYMENT_VERB } from './sales-order-api'
import type { PaymentDirection } from './sales-order-api'
import { paymentTotal, toPaymentInput } from './sales-order-form'
import type { PaymentFormValues } from './sales-order-form'

/** Phiếu cần thanh toán — phiếu bán (thu của khách) hoặc phiếu nhập (trả NCC). */
export interface PayableDocument {
  code: string
  /** Khách hàng hoặc nhà cung cấp */
  counterpartyName: string
  outstandingAmount: number
}

interface PaymentModalProps {
  open: boolean
  direction: PaymentDirection
  document: PayableDocument
  /** Gọi API; ném ApiError nếu thất bại — FormModal giữ hộp mở và hiện lỗi */
  onSubmit: (input: ReturnType<typeof toPaymentInput>) => Promise<void>
  onClose: () => void
}

// Tiền thu / trả tính đến đồng (BR-SAL-12, BR-SPAY-02)
const wholeDongRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !Number.isInteger(value)) throw new Error('Nhập số đồng nguyên')
  },
}

/**
 * Ghi nhận một lần thu của khách (BR-SAL-09..12) hoặc trả cho NCC (BR-SPAY-01, 02): tiền mặt, chuyển khoản, hoặc cả
 * hai. Mặc định điền tiền mặt = số còn nợ. Kiểm tra "không vượt số còn nợ" ngay trên trình duyệt cho nhanh;
 * backend kiểm tra lại sau khi khóa phiếu.
 */
export function PaymentModal({ open, direction, document: order, onSubmit, onClose }: PaymentModalProps) {
  const verb = PAYMENT_VERB[direction]
  const lowerVerb = verb.toLowerCase()
  const [form] = Form.useForm<PaymentFormValues>()
  const values = Form.useWatch([], form) as PaymentFormValues | undefined
  const total = paymentTotal(values)

  const amountInput = (label: string) => (
    <InputNumber<number>
      min={0}
      style={{ width: '100%' }}
      formatter={formatPriceInput}
      parser={(text) => Number(parsePriceInput(text))}
      suffix="đ"
      aria-label={label}
    />
  )

  return (
    <FormModal<PaymentFormValues>
      open={open}
      title={`${verb} tiền phiếu ${order.code}`}
      form={form}
      initialValues={{ paymentDate: todayIso(), cashAmount: order.outstandingAmount }}
      submitText="Ghi nhận"
      onSubmit={(submitted) => onSubmit(toPaymentInput(submitted))}
      onClose={onClose}
    >
      <Typography.Paragraph type="secondary">
        {direction === 'receive' ? `${order.counterpartyName} còn nợ` : `Còn nợ ${order.counterpartyName}`}{' '}
        <Typography.Text strong>{formatVnd(order.outstandingAmount)}</Typography.Text> trên phiếu này. Trả cả tiền
        mặt lẫn chuyển khoản thì nhập cả hai ô.
      </Typography.Paragraph>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="cashAmount"
            label="Tiền mặt"
            dependencies={['transferAmount']}
            rules={[
              { type: 'number', min: 0, message: 'Không được âm' },
              wholeDongRule,
              ({ getFieldsValue }) => ({
                validator: async () => {
                  const sum = paymentTotal(getFieldsValue(['cashAmount', 'transferAmount']) as PaymentFormValues)
                  if (sum <= 0) throw new Error('Nhập số tiền mặt hoặc chuyển khoản')
                  if (sum > order.outstandingAmount) throw new Error(`Vượt số còn nợ ${formatVnd(order.outstandingAmount)}`)
                },
              }),
            ]}
          >
            {amountInput('Tiền mặt')}
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="transferAmount"
            label="Chuyển khoản"
            rules={[{ type: 'number', min: 0, message: 'Không được âm' }, wholeDongRule]}
          >
            {amountInput('Chuyển khoản')}
          </Form.Item>
        </Col>
      </Row>
      <Typography.Paragraph>
        Tổng lần {lowerVerb}: <Typography.Text strong>{formatVnd(total)}</Typography.Text>
        {total > 0 && total <= order.outstandingAmount && (
          <Typography.Text type="secondary">
            {' '}
            · còn nợ sau khi {lowerVerb} {formatVnd(order.outstandingAmount - total)}
          </Typography.Text>
        )}
      </Typography.Paragraph>
      <Row gutter={16}>
        <Col span={10}>
          <Form.Item
            name="paymentDate"
            label={`Ngày ${lowerVerb}`}
            // BR-SAL-12 (đổi 2026-10-20): chọn ngày tự do — không giới hạn tương lai hay trước ngày bán
            rules={[{ required: true, message: `Chọn ngày ${lowerVerb}` }]}
          >
            <Input type="date" />
          </Form.Item>
        </Col>
        <Col span={14}>
          <Form.Item name="note" label="Ghi chú">
            <Input maxLength={500} placeholder="Ví dụ: mã giao dịch ngân hàng" />
          </Form.Item>
        </Col>
      </Row>
    </FormModal>
  )
}
