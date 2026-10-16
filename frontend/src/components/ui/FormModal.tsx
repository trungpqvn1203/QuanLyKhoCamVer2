import { Alert, Form, Modal } from 'antd'
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
  /** Gọi API; ném ApiError nếu thất bại */
  onSubmit: (values: V) => Promise<void>
  onClose: () => void
  children: ReactNode
}

/**
 * Modal chứa form thêm/sửa (ADR-0002, tầng 2). Tự xử lý lỗi từ backend:
 * - VALIDATION_FAILED có details[].field → hiện lỗi dưới đúng ô input
 * - Lỗi khác (trùng tên, vi phạm nghiệp vụ...) → hiện Alert trong modal
 */
export function FormModal<V extends object>({
  open,
  title,
  form,
  initialValues,
  onSubmit,
  onClose,
  children,
}: FormModalProps<V>) {
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const close = () => {
    setErrorMessage(null)
    onClose()
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
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={submitting}
      onOk={() => form.submit()}
      onCancel={close}
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
