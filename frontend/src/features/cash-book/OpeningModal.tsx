import { Col, Form, Input, InputNumber, Row, Typography } from 'antd'
import type { FormRule } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { todayIso } from '../../utils/format'
import { formatPriceInput, parsePriceInput } from '../../utils/money'
import { cashBookApi } from './cash-book-api'
import type { CashBookOpening, OpeningInput } from './cash-book-api'

interface OpeningModalProps {
  open: boolean
  opening: CashBookOpening
  onSaved: (opening: CashBookOpening) => void
  onClose: () => void
}

interface OpeningFormValues {
  startDate?: string
  openingCash?: number | null
  openingBank?: number | null
}

const wholeDongRule: FormRule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !Number.isInteger(value)) throw new Error('Nhập số đồng nguyên')
  },
}

/**
 * Đặt / sửa đầu kỳ sổ quỹ (BR-CASH-03): ngày bắt đầu + tiền mặt và số dư tài khoản thực tế tại ngày đó.
 * Khoản thu/chi trước ngày này không vào sổ. Mỗi lần lưu ghi lại lịch sử (hiện ở trang Quỹ đầu kỳ).
 */
export function OpeningModal({ open, opening, onSaved, onClose }: OpeningModalProps) {
  const [form] = Form.useForm<OpeningFormValues>()

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

  const handleSubmit = async (values: OpeningFormValues) => {
    const input: OpeningInput = {
      startDate: values.startDate!,
      openingCash: values.openingCash ?? 0,
      openingBank: values.openingBank ?? 0,
    }
    onSaved(await cashBookApi.setOpening(input))
  }

  return (
    <FormModal<OpeningFormValues>
      open={open}
      title={opening.configured ? 'Sửa đầu kỳ sổ quỹ' : 'Đặt đầu kỳ sổ quỹ'}
      form={form}
      initialValues={
        opening.configured
          ? { startDate: opening.startDate, openingCash: opening.openingCash, openingBank: opening.openingBank }
          : { startDate: todayIso(), openingCash: 0, openingBank: 0 }
      }
      submitText="Lưu đầu kỳ"
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Typography.Paragraph type="secondary">
        Nhập số tiền mặt đang có trong quỹ và số dư tài khoản thực tế tại ngày bắt đầu. Khoản thu/chi có ngày trước
        ngày này sẽ không tính vào sổ quỹ. Sửa đầu kỳ làm đổi mọi số dư, nên mỗi lần sửa đều được ghi lại.
      </Typography.Paragraph>
      <Form.Item
        name="startDate"
        label="Ngày bắt đầu"
        rules={[
          { required: true, message: 'Chọn ngày bắt đầu' },
          {
            validator: async (_, value?: string) => {
              if (value && value > todayIso()) throw new Error('Không được ở tương lai')
            },
          },
        ]}
      >
        <Input type="date" />
      </Form.Item>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="openingCash" label="Tiền mặt đầu kỳ" rules={[{ required: true, message: 'Nhập số tiền (không có thì 0)' }, wholeDongRule]}>
            {amountInput('Tiền mặt đầu kỳ')}
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="openingBank" label="Số dư tài khoản đầu kỳ" rules={[{ required: true, message: 'Nhập số tiền (không có thì 0)' }, wholeDongRule]}>
            {amountInput('Số dư tài khoản đầu kỳ')}
          </Form.Item>
        </Col>
      </Row>
    </FormModal>
  )
}
