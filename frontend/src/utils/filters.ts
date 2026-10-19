/**
 * Bộ lọc danh sách: object phẳng { tênThamSố: giá trị } gửi thẳng lên query string của API.
 * Khai báo bằng `type` (không dùng `interface`) để gán được vào kiểu này.
 */
export type FilterValues = Record<string, string | number | boolean | undefined>

/** Có ít nhất một điều kiện đang áp dụng — kể cả active=false, bỏ qua từ khóa chỉ có khoảng trắng. */
export function hasActiveFilters(filters: FilterValues): boolean {
  return Object.values(filters).some((value) => (typeof value === 'string' ? value.trim() !== '' : value !== undefined))
}

/** Đổi bộ lọc sang tham số query: cắt khoảng trắng từ khóa, bỏ giá trị trống, boolean → "true"/"false". */
export function toFilterParams(filters: FilterValues): Record<string, string | number | undefined> {
  const params: Record<string, string | number | undefined> = {}
  for (const [key, value] of Object.entries(filters)) {
    if (typeof value === 'string') params[key] = value.trim() || undefined
    else if (typeof value === 'boolean') params[key] = String(value)
    else params[key] = value
  }
  return params
}

/** Định dạng ngày hiển thị trên ô chọn ngày của thanh lọc. */
export const DISPLAY_DATE_FORMAT = 'DD/MM/YYYY'

/**
 * "01/10/2026" (ô chọn ngày trả về) → "2026-10-01" (ISO, backend bind vào LocalDate).
 * Chuỗi rỗng (đầu khoảng bị bỏ trống) hoặc sai định dạng → undefined = không lọc đầu đó.
 */
export function displayDateToIso(text: string): string | undefined {
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text.trim())
  return match ? `${match[3]}-${match[2]}-${match[1]}` : undefined
}
