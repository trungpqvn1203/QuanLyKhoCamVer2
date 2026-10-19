import { Col, Form, Input, InputNumber, Row, Switch, Typography } from 'antd'
import type { FormRule } from 'antd'
import { FormModal } from '../../components/ui/FormModal'
import { formatPriceInput, hasAtMostTwoDecimals, parsePriceInput } from '../../utils/money'
import { PHONE_FORMAT_MESSAGE, isValidPhone } from '../../utils/phone'
import { employeeApi } from './employee-api'
import type { Employee, UpdateEmployeeInput } from './employee-api'

interface EmployeeFormModalProps {
  open: boolean
  /** null = tạo mới */
  editing: Employee | null
  onClose: () => void
  onSaved: (saved: Employee) => void
}

const priceRule: FormRule = {
  validator: async (_, value?: number | null) => {
    if (value != null && !hasAtMostTwoDecimals(value)) throw new Error('Tối đa 2 chữ số lẻ')
  },
}

// BR-EMP-02: ít nhất một đơn giá. Gắn ở ô lương ngày — trùng field backend báo lỗi
const atLeastOneRateRule: FormRule = ({ getFieldValue }) => ({
  validator: async (_, dailyWage?: number | null) => {
    if (dailyWage == null && getFieldValue('tonRate') == null) {
      throw new Error('Nhập lương ngày hoặc đơn giá tấn (ít nhất một)')
    }
  },
})

function PriceInput({ suffix, ariaLabel }: { suffix: string; ariaLabel: string }) {
  return (
    <InputNumber<number>
      min={0}
      style={{ width: '100%' }}
      formatter={formatPriceInput}
      parser={(text) => Number(parsePriceInput(text))}
      suffix={suffix}
      aria-label={ariaLabel}
      placeholder="Không hưởng"
    />
  )
}

export function EmployeeFormModal({ open, editing, onClose, onSaved }: EmployeeFormModalProps) {
  const [form] = Form.useForm<UpdateEmployeeInput>()

  const handleSubmit = async ({ active, ...fields }: UpdateEmployeeInput) => {
    // Ô đơn giá bị xóa cho ra undefined → gửi null để PUT bỏ hẳn cách tính đó
    const input = { ...fields, dailyWage: fields.dailyWage ?? null, tonRate: fields.tonRate ?? null }
    const saved = editing ? await employeeApi.update(editing.id, { ...input, active }) : await employeeApi.create(input)
    onSaved(saved)
  }

  return (
    <FormModal<UpdateEmployeeInput>
      open={open}
      title={editing ? `Sửa nhân viên ${editing.code}` : 'Thêm nhân viên'}
      form={form}
      initialValues={editing ?? undefined}
      submitText={editing ? 'Lưu thay đổi' : 'Thêm'}
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item name="name" label="Họ tên" rules={[{ required: true, whitespace: true, max: 200 }]}>
        <Input placeholder="Ví dụ: Nguyễn Văn An" />
      </Form.Item>
      <Form.Item
        name="phone"
        label="Số điện thoại"
        rules={[
          { max: 20 },
          {
            validator: async (_, value?: string) => {
              if (value?.trim() && !isValidPhone(value)) throw new Error(PHONE_FORMAT_MESSAGE)
            },
          },
        ]}
      >
        <Input placeholder="Không bắt buộc" inputMode="tel" />
      </Form.Item>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="dailyWage"
            label="Lương ngày công"
            dependencies={['tonRate']}
            rules={[priceRule, atLeastOneRateRule]}
          >
            <PriceInput suffix="đ/ngày" ariaLabel="Lương ngày công" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="tonRate" label="Đơn giá bốc vác" rules={[priceRule]}>
            <PriceInput suffix="đ/tấn" ariaLabel="Đơn giá bốc vác" />
          </Form.Item>
        </Col>
      </Row>
      <Typography.Paragraph type="secondary" style={{ marginTop: -8 }}>
        Để trống ô nào thì nhân viên không hưởng lương theo cách đó. Đổi đơn giá chỉ áp dụng cho các tháng chưa chốt lương.
      </Typography.Paragraph>
      {editing && (
        <Form.Item name="active" label="Đang làm việc" valuePropName="checked">
          <Switch />
        </Form.Item>
      )}
    </FormModal>
  )
}
