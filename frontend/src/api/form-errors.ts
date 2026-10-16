import { ApiError } from './client'

export interface FormFieldError {
  name: string
  errors: string[]
}

/**
 * Chuyển `error.details[]` của lỗi VALIDATION_FAILED thành dạng Form.setFields() của Ant Design.
 * Gộp nhiều lỗi cùng một field; bỏ qua detail không có field.
 * Hàm thuần (không phụ thuộc Ant Design) để test được dễ dàng.
 */
export function toFormFieldErrors(error: unknown): FormFieldError[] {
  if (!(error instanceof ApiError)) return []

  const byField = new Map<string, string[]>()
  for (const detail of error.details) {
    if (!detail.field) continue
    byField.set(detail.field, [...(byField.get(detail.field) ?? []), detail.message])
  }
  return [...byField].map(([name, errors]) => ({ name, errors }))
}
