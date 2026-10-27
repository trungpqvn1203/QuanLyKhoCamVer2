import { Alert, App, Form, Modal } from 'antd'
import type { FormInstance } from 'antd'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../../api/client'
import { toFormFieldErrors } from '../../api/form-errors'

interface FormModalProps<V> {
  open: boolean
  title: string
  form: FormInstance<V>
  initialValues?: Partial<V>
  /** Nhãn nút gửi theo thao tác, ví dụ "Thêm" / "Lưu thay đổi"; mặc định "Lưu" */
  submitText?: string
  /** Gọi API; ném ApiError nếu thất bại */
  onSubmit: (values: V) => Promise<void>
  onClose: () => void
  /** Rộng hơn mặc định cho form có bảng nhiều dòng (ví dụ ghi nhận nợ đầu kỳ theo lô) */
  width?: number
  children: ReactNode
}

/**
 * Modal chứa form thêm/sửa (ADR-0002, tầng 2). Tự xử lý lỗi từ backend:
 * - VALIDATION_FAILED có details[].field → hiện lỗi dưới đúng ô input
 * - Lỗi khác (trùng tên, vi phạm nghiệp vụ...) → hiện Alert trong modal, giữ nguyên dữ liệu đã nhập
 *
 * Chống mất dữ liệu: bấm ra ngoài không đóng; Hủy / Esc / dấu X khi form đã sửa thì hỏi lại.
 * Form chưa sửa thì đóng ngay, không hỏi thừa.
 */
export function FormModal<V extends object>({
  open,
  title,
  form,
  initialValues,
  submitText = 'Lưu',
  onSubmit,
  onClose,
  width,
  children,
}: FormModalProps<V>) {
  const { modal } = App.useApp()
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const close = () => {
    setErrorMessage(null)
    onClose()
  }

  const requestClose = () => {
    if (submitting) return
    if (!form.isFieldsTouched()) {
      close()
      return
    }
    modal.confirm({
      title: 'Bỏ các thay đổi chưa lưu?',
      content: 'Dữ liệu vừa nhập trong biểu mẫu sẽ mất.',
      okText: 'Bỏ thay đổi',
      okButtonProps: { danger: true },
      cancelText: 'Tiếp tục sửa',
      onOk: close,
    })
  }

  // Tự gom dữ liệu → gọi API → map lỗi (ADR-0002, đánh đổi #2)
  const handleFinish = async (values: V) => {
    setSubmitting(true)
    setErrorMessage(null)
    try {
      await onSubmit(values)
      close()
    } catch (error) {
      const fieldErrors = toFormFieldErrors(error)
      if (fieldErrors.length > 0) {
        form.setFields(fieldErrors as Parameters<typeof form.setFields>[0])
      } else {
        setErrorMessage(error instanceof ApiError ? error.message : 'Đã có lỗi xảy ra')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      title={title}
      width={width}
      okText={submitText}
      cancelText="Hủy"
      confirmLoading={submitting}
      cancelButtonProps={{ disabled: submitting }}
      onOk={() => form.submit()}
      onCancel={requestClose}
      maskClosable={false}
      // Form dài thì cuộn trong thân modal, nút Hủy/Lưu luôn nằm trong tầm nhìn
      styles={{ body: { maxHeight: 'calc(100vh - 240px)', overflowY: 'auto' } }}
      // Đóng là hủy nội dung → lần mở sau Form tạo lại và nhận initialValues mới
      destroyOnHidden
    >
      {errorMessage && <Alert type="error" showIcon title={errorMessage} style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" onFinish={handleFinish} preserve={false} initialValues={initialValues}>
        {children}
      </Form>
    </Modal>
  )
}
