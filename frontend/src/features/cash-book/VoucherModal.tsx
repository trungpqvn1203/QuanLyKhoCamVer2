import { Col, Form, Input, InputNumber, Radio, Row, Select, Typography } from 'antd'
import type { FormRule } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { todayIso } from '../../utils/format'
import { formatPriceInput, parsePriceInput } from '../../utils/money'
import { CASH_ACCOUNT_LABELS, cashVoucherApi } from './cash-book-api'
import type { CashAccount, CashCategory, CashDirection, CashVoucher, VoucherInput } from './cash-book-api'

interface VoucherModalProps {
  open: boolean
  /** Phiếu thu (IN) hay phiếu chi (OUT) — chọn từ nút bấm mở hộp thoại */
  direction: CashDirection
  /** Mọi danh mục; hộp thoại tự lọc danh mục đang dùng, cùng chiều (BR-CASH-05) */
  categories: CashCategory[]
  /** Ngày đầu kỳ sổ quỹ — phiếu không được ghi ngày trước đó (BR-CASH-04) */
  startDate: string
  onSaved: (voucher: CashVoucher) => void
  onClose: () => void
}

interface VoucherFormValues {
  account: CashAccount
  categoryId?: number
  amount?: number | null
  voucherDate: string
  counterparty?: string
  note?: string
}

const ACCOUNT_OPTIONS = (Object.keys(CASH_ACCOUNT_LABELS) as CashAccount[]).map((account) => ({
  value: account,
  label: CASH_ACCOUNT_LABELS[account],
}))

// Tiền tính đến đồng
const wholeDongRule: FormRule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !Number.isInteger(value)) throw new Error('Nhập số đồng nguyên')
  },
}

/** Lập phiếu thu / phiếu chi khác (BR-CASH-04): tiền điện, xăng xe, góp vốn... Mã phiếu do hệ thống sinh. */
export function VoucherModal({ open, direction, categories, startDate, onSaved, onClose }: VoucherModalProps) {
  const [form] = Form.useForm<VoucherFormValues>()
  const receipt = direction === 'IN'
  const options = categories
    .filter((category) => category.active && category.direction === direction)
    .map((category) => ({ value: category.id, label: category.name }))

  const handleSubmit = async (values: VoucherFormValues) => {
    const input: VoucherInput = {
      direction,
      account: values.account,
      categoryId: values.categoryId!,
      amount: values.amount!,
      voucherDate: values.voucherDate,
      counterparty: values.counterparty?.trim() || undefined,
      note: values.note?.trim() || undefined,
    }
    onSaved(await cashVoucherApi.create(input))
  }

  return (
    <FormModal<VoucherFormValues>
      open={open}
      title={receipt ? 'Lập phiếu thu' : 'Lập phiếu chi'}
      form={form}
      initialValues={{ account: 'CASH', voucherDate: todayIso() }}
      submitText={receipt ? 'Lập phiếu thu' : 'Lập phiếu chi'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item name="account" label={receipt ? 'Thu vào' : 'Chi từ'} rules={[{ required: true }]}>
        <Radio.Group options={ACCOUNT_OPTIONS} optionType="button" buttonStyle="solid" />
      </Form.Item>
      <Form.Item
        name="categoryId"
        label="Danh mục"
        rules={[{ required: true, message: 'Chọn danh mục' }]}
        extra={options.length === 0 ? `Chưa có danh mục ${receipt ? 'thu' : 'chi'} nào — tạo ở nút "Danh mục thu/chi"` : undefined}
      >
        <Select showSearch optionFilterProp="label" options={options} placeholder="Chọn danh mục" />
      </Form.Item>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="amount"
            label="Số tiền"
            rules={[{ required: true, message: 'Nhập số tiền' }, { type: 'number', min: 1, message: 'Lớn hơn 0' }, wholeDongRule]}
          >
            <InputNumber<number>
              min={0}
              style={{ width: '100%' }}
              formatter={formatPriceInput}
              parser={(text) => Number(parsePriceInput(text))}
              suffix="đ"
              aria-label="Số tiền"
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="voucherDate"
            label="Ngày phiếu"
            rules={[
              { required: true, message: 'Chọn ngày' },
              {
                validator: async (_, value?: string) => {
                  if (value && value < startDate) throw new Error('Trước ngày đầu kỳ sổ quỹ — phiếu sẽ không vào sổ')
                },
              },
            ]}
          >
            <Input type="date" />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item name="counterparty" label={receipt ? 'Người nộp' : 'Người nhận'} rules={[{ max: 200 }]}>
        <Input placeholder="Không bắt buộc" />
      </Form.Item>
      <Form.Item name="note" label="Ghi chú" rules={[{ max: 500 }]}>
        <Input.TextArea rows={2} placeholder="Không bắt buộc" />
      </Form.Item>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
        Phiếu không sửa, không xóa được. Ghi nhầm thì dùng nút Hoàn trong sổ quỹ để lập phiếu hoàn.
      </Typography.Paragraph>
    </FormModal>
  )
}
