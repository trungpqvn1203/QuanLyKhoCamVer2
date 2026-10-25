import { Flex, Form, Input, InputNumber, Switch, Typography } from 'antd'
import type { FormRule, InputNumberProps } from 'antd'
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

// BR-EMP-02, BR-EMP-04: ít nhất một trong ba đơn giá. Gắn ở ô lương ngày — trùng field backend báo lỗi
const atLeastOneRateRule: FormRule = ({ getFieldValue }) => ({
  validator: async (_, dailyWage?: number | null) => {
    if (dailyWage == null && getFieldValue('unloadTonRate') == null && getFieldValue('deliveryTonRate') == null) {
      throw new Error('Nhập lương ngày, đơn giá bốc xuống hoặc đơn giá giao hàng (ít nhất một)')
    }
  },
})

/**
 * Form.Item gắn value/onChange vào component con TRỰC TIẾP của nó — ở đây là PriceInput, nên phải chuyển tiếp
 * xuống InputNumber ({...props}). Thiếu dòng đó thì ô vẫn gõ được nhưng form không nhận giá trị nào.
 */
function PriceInput(props: InputNumberProps<number>) {
  return (
    <InputNumber<number>
      {...props}
      min={0}
      style={{ width: '100%' }}
      formatter={formatPriceInput}
      parser={(text) => Number(parsePriceInput(text))}
      placeholder="Không hưởng"
    />
  )
}

export function EmployeeFormModal({ open, editing, onClose, onSaved }: EmployeeFormModalProps) {
  const [form] = Form.useForm<UpdateEmployeeInput>()

  const handleSubmit = async ({ active, ...fields }: UpdateEmployeeInput) => {
    // Ô đơn giá bị xóa cho ra undefined → gửi null để PUT bỏ hẳn cách tính đó
    const input = {
      ...fields,
      dailyWage: fields.dailyWage ?? null,
      unloadTonRate: fields.unloadTonRate ?? null,
      deliveryTonRate: fields.deliveryTonRate ?? null,
    }
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
      <Form.Item
        name="dailyWage"
        label="Lương ngày công"
        dependencies={['unloadTonRate', 'deliveryTonRate']}
        rules={[priceRule, atLeastOneRateRule]}
      >
        <PriceInput suffix="đ/ngày" aria-label="Lương ngày công" />
      </Form.Item>
      {/* Flex + gap thay vì Row gutter: gutter dùng margin âm → thân modal (overflow: auto) hiện thanh cuộn ngang */}
      <Flex gap={16}>
        <Form.Item name="unloadTonRate" label="Đơn giá tấn bốc xuống" rules={[priceRule]} style={{ flex: 1 }}>
          <PriceInput suffix="đ/tấn" aria-label="Đơn giá tấn bốc xuống" />
        </Form.Item>
        <Form.Item name="deliveryTonRate" label="Đơn giá tấn giao hàng" rules={[priceRule]} style={{ flex: 1 }}>
          <PriceInput suffix="đ/tấn" aria-label="Đơn giá tấn giao hàng" />
        </Form.Item>
      </Flex>
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
