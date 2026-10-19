import { ApiError } from './client'

/** Đường dẫn tới một ô trong Form của Ant Design: ['name'] hoặc ['lines', 0, 'orderedQuantity']. */
export type FieldPath = (string | number)[]

export interface FormFieldError {
  name: FieldPath
  errors: string[]
}

/**
 * Tên field backend → đường dẫn ô form: "lines[0].orderedQuantity" → ['lines', 0, 'orderedQuantity'].
 * Form.List của Ant Design đặt tên ô theo mảng; truyền nguyên chuỗi thì không khớp ô nào.
 * Đoạn chỉ gồm chữ số là chỉ số mảng (API không có field nào tên là số).
 */
export function toFieldPath(field: string): FieldPath {
  return (field.match(/[^.[\]]+/g) ?? []).map((part) => (/^\d+$/.test(part) ? Number(part) : part))
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
  return [...byField].map(([field, errors]) => ({ name: toFieldPath(field), errors }))
}
