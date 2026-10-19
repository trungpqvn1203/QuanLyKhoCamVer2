import { Form, Input, Typography } from 'antd'
import { FormModal } from './FormModal'

interface ReasonValues {
  reason?: string
}

interface ReasonModalProps {
  open: boolean
  title: string
  /** Giải thích hậu quả trước khi xác nhận (ví dụ "tồn kho sẽ bị trừ lại") */
  description: string
  submitText: string
  /** Đảo phiếu bắt buộc có lý do (BR-IMP-06); hủy nháp thì tùy chọn */
  required: boolean
  /** Gọi API; ném ApiError nếu thất bại — FormModal giữ modal mở và hiện lỗi (ví dụ không đủ tồn để đảo) */
  onSubmit: (reason: string | undefined) => Promise<void>
  onClose: () => void
}

/** Hộp nhập lý do (hủy/đảo phiếu nhập, hủy chốt lương) — dùng lại FormModal để có sẵn xử lý lỗi và chống mất dữ liệu. */
export function ReasonModal({ open, title, description, submitText, required, onSubmit, onClose }: ReasonModalProps) {
  const [form] = Form.useForm<ReasonValues>()

  return (
    <FormModal<ReasonValues>
      open={open}
      title={title}
      form={form}
      submitText={submitText}
      onSubmit={(values) => onSubmit(values.reason?.trim() || undefined)}
      onClose={onClose}
    >
      <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>
      <Form.Item
        name="reason"
        label="Lý do"
        rules={required ? [{ required: true, whitespace: true, message: 'Nhập lý do' }] : []}
      >
        <Input.TextArea maxLength={500} showCount autoSize={{ minRows: 2, maxRows: 4 }} autoFocus />
      </Form.Item>
    </FormModal>
  )
}
