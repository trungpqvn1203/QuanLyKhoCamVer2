import { Col, Form, Input, InputNumber, Row, Select, Typography } from 'antd'
import type { FormRule as Rule } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { todayIso } from '../../utils/format'
import { formatPriceInput, formatVnd, parsePriceInput } from '../../utils/money'
import { paymentTotal, toPaymentInput } from '../sales-orders/sales-order-form'
import type { PaymentFormValues } from '../sales-orders/sales-order-form'
import { advanceApi } from './employee-debt-api'
import type { AdvanceLedger } from './employee-debt-api'

/** Nhân viên nhận tạm ứng — chỉ cần mã và tên để hiển thị. */
export interface AdvanceTarget {
  id: number
  code: string
  name: string
}

interface AdvanceModalProps {
  open: boolean
  /** Có = ứng cho người này (mở từ một dòng công nợ); không có = hiện ô chọn trong `employees` */
  employee?: AdvanceTarget
  /** Nhân viên đang làm việc để chọn (BR-EPAY-A01: chỉ ứng cho người đang làm) */
  employees: AdvanceTarget[]
  onSaved: (ledger: AdvanceLedger) => void
  onClose: () => void
}

interface AdvanceFormValues extends PaymentFormValues {
  employeeId?: number
}

// Tiền ứng tính đến đồng (BR-EPAY-03)
const wholeDongRule: Rule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !Number.isInteger(value)) throw new Error('Nhập số đồng nguyên')
  },
}

/**
 * Ghi nhận một lần ứng tiền cho nhân viên (BR-EPAY-03): tiền mặt, chuyển khoản, hoặc cả hai. Tạm ứng không có hạn mức
 * trên — số dư chỉ giảm khi trừ vào lương. Ngày ứng chọn tự do.
 */
export function AdvanceModal({ open, employee, employees, onSaved, onClose }: AdvanceModalProps) {
  const [form] = Form.useForm<AdvanceFormValues>()
  const values = Form.useWatch([], form) as AdvanceFormValues | undefined
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
    <FormModal<AdvanceFormValues>
      open={open}
      title={employee ? `Ứng tiền · ${employee.code} ${employee.name}` : 'Ứng tiền cho nhân viên'}
      form={form}
      initialValues={{ paymentDate: todayIso(), employeeId: employee?.id }}
      submitText="Ghi nhận"
      onSubmit={async (submitted) => {
        const employeeId = employee?.id ?? submitted.employeeId
        if (employeeId == null) return
        onSaved(await advanceApi.record(employeeId, toPaymentInput(submitted)))
      }}
      onClose={onClose}
    >
      {!employee && (
        <Form.Item name="employeeId" label="Nhân viên" rules={[{ required: true, message: 'Chọn nhân viên' }]}>
          <Select
            showSearch
            placeholder="Chọn nhân viên đang làm việc"
            optionFilterProp="label"
            options={employees.map((e) => ({ value: e.id, label: `${e.code} · ${e.name}` }))}
          />
        </Form.Item>
      )}
      <Typography.Paragraph type="secondary">
        Số tiền ứng được cộng vào dư tạm ứng của nhân viên, rồi trừ dần vào lương khi trả lương. Nhân viên không trả
        lại bằng tiền mặt.
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
        Tổng lần ứng: <Typography.Text strong>{formatVnd(total)}</Typography.Text>
      </Typography.Paragraph>
      <Row gutter={16}>
        <Col span={10}>
          <Form.Item name="paymentDate" label="Ngày ứng" rules={[{ required: true, message: 'Chọn ngày ứng' }]}>
            <Input type="date" />
          </Form.Item>
        </Col>
        <Col span={14}>
          <Form.Item name="note" label="Ghi chú">
            <Input maxLength={500} placeholder="Ví dụ: ứng tiền thuốc cho con" />
          </Form.Item>
        </Col>
      </Row>
    </FormModal>
  )
}
