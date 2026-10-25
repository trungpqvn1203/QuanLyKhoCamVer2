import { DeleteOutlined } from '@ant-design/icons'
import { App, Button, Flex, Form, Input, InputNumber, Popconfirm, Radio } from 'antd'
import type { FormRule } from 'antd'
import { ApiError } from '../../api/client'
import { FormModal } from '../../components/ui/FormModal'
import { formatDate } from '../../utils/format'
import { DAY_STATUS_LABELS, attendanceApi } from './attendance-api'
import type { AttendanceDay, AttendanceInput, AttendanceRow, DayStatus } from './attendance-api'

interface AttendanceCellModalProps {
  open: boolean
  row: AttendanceRow
  date: string
  /** Ô đã chấm; undefined = ô trống */
  current?: AttendanceDay
  onClose: () => void
  onChanged: () => void
}

const STATUS_OPTIONS = (Object.keys(DAY_STATUS_LABELS) as DayStatus[]).map((status) => ({
  value: status,
  label: DAY_STATUS_LABELS[status],
}))

// BR-ATT-01: tấn tối đa 3 chữ số lẻ (so qua đơn vị kg để tránh sai số dấu phẩy động)
const tonsRule: FormRule = {
  validator: async (_, value?: number | null) => {
    if (value != null && Math.abs(value * 1000 - Math.round(value * 1000)) > 1e-6) {
      throw new Error('Tối đa 3 chữ số lẻ (đến kg)')
    }
  },
}

/** Sửa một ô bảng công: trạng thái ngày + tấn bốc xuống + tấn giao hàng + ghi chú (BR-ATT-01..03). */
export function AttendanceCellModal({ open, row, date, current, onClose, onChanged }: AttendanceCellModalProps) {
  const { message } = App.useApp()
  const [form] = Form.useForm<AttendanceInput>()
  const status = Form.useWatch('status', form)

  const handleSubmit = async (values: AttendanceInput) => {
    // BR-ATT-02: ngày nghỉ không có tấn — gửi 0 thay vì số còn sót trong ô đã khóa
    const absent = values.status === 'ABSENT'
    await attendanceApi.record(row.employeeId, date, {
      ...values,
      unloadTons: absent ? 0 : values.unloadTons,
      deliveryTons: absent ? 0 : values.deliveryTons,
    })
    onChanged()
  }

  const clear = async () => {
    try {
      await attendanceApi.clear(row.employeeId, date)
      onChanged()
      onClose()
    } catch (err) {
      message.error(err instanceof ApiError ? err.message : 'Không xóa được chấm công')
    }
  }

  return (
    <FormModal<AttendanceInput>
      open={open}
      title={`${row.employeeCode} · ${row.employeeName} — ${formatDate(date)}`}
      form={form}
      initialValues={current ?? { status: 'FULL', unloadTons: 0, deliveryTons: 0 }}
      submitText="Lưu"
      onSubmit={handleSubmit}
      onClose={onClose}
    >
      <Form.Item name="status" label="Ngày công" rules={[{ required: true }]}>
        <Radio.Group options={STATUS_OPTIONS} optionType="button" buttonStyle="solid" />
      </Form.Item>
      {/* Flex + gap thay vì Row gutter (gutter dùng margin âm → thân modal hiện thanh cuộn ngang) */}
      <Flex gap={16}>
        <Form.Item
          name="unloadTons"
          label="Tấn bốc xuống"
          extra={row.paysByUnload ? undefined : 'Không hưởng lương bốc xuống — chỉ để ghi nhận'}
          rules={[tonsRule]}
          style={{ flex: 1 }}
        >
          <InputNumber<number>
            min={0}
            step={0.5}
            decimalSeparator=","
            style={{ width: '100%' }}
            suffix="tấn"
            disabled={status === 'ABSENT'}
            aria-label="Tấn bốc xuống"
          />
        </Form.Item>
        <Form.Item
          name="deliveryTons"
          label="Tấn giao hàng"
          extra={row.paysByDelivery ? undefined : 'Không hưởng lương giao hàng — chỉ để ghi nhận'}
          rules={[tonsRule]}
          style={{ flex: 1 }}
        >
          <InputNumber<number>
            min={0}
            step={0.5}
            decimalSeparator=","
            style={{ width: '100%' }}
            suffix="tấn"
            disabled={status === 'ABSENT'}
            aria-label="Tấn giao hàng"
          />
        </Form.Item>
      </Flex>
      <Form.Item name="note" label="Ghi chú" rules={[{ max: 500 }]}>
        <Input placeholder="Không bắt buộc" />
      </Form.Item>
      {current && (
        <Popconfirm
          title="Xóa chấm công ngày này?"
          description="Ngày sẽ trở về trạng thái chưa chấm."
          okText="Xóa"
          okButtonProps={{ danger: true }}
          cancelText="Không"
          onConfirm={clear}
        >
          <Button danger type="link" icon={<DeleteOutlined />} style={{ paddingInline: 0 }}>
            Xóa chấm công ngày này
          </Button>
        </Popconfirm>
      )}
    </FormModal>
  )
}
