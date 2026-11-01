import type { FormInstance } from 'antd'

/** Con trỏ vào ô của một dòng vừa thêm: dòng mới chỉ có trong DOM sau lần render kế tiếp nên phải chờ một nhịp. */
export function focusLineField<T>(form: FormInstance<T>, name: (string | number)[]) {
  setTimeout(() => form.focusField(name))
}

/**
 * Chọn một SP ở ô thêm nhanh thì nó vào dòng nào:
 * - `focus`: SP đã có trên phiếu → không tạo dòng trùng (mỗi SP một dòng), chỉ đưa con trỏ về dòng đó.
 * - `fill`: có dòng chưa có SP (vd. phiếu nhập vừa đổi NCC bị bỏ chọn SP) → điền vào dòng đầu tiên, giữ SL/giá đã gõ.
 * - `append`: còn lại → thêm dòng mới cuối bảng.
 */
export type QuickAddTarget = { action: 'focus' | 'fill' | 'append'; index: number }

/** Bỏ dấu + chữ thường để "cam heo" khớp "Cám heo"; đ không tách dấu qua NFD nên đổi riêng. */
export function foldVietnamese(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase()
}

/** Mọi từ gõ vào đều phải có trong nhãn, không cần đúng thứ tự: "25 heo" khớp "C01 · Cám heo 25kg". */
export function matchesSearch(label: string, input: string): boolean {
  const haystack = foldVietnamese(label)
  return foldVietnamese(input)
    .split(/\s+/)
    .filter(Boolean)
    .every((word) => haystack.includes(word))
}

export function resolveQuickAdd(
  lines: readonly ({ productId?: number } | undefined)[] | undefined,
  productId: number,
): QuickAddTarget {
  const current = lines ?? []
  const existing = current.findIndex((line) => line?.productId === productId)
  if (existing >= 0) return { action: 'focus', index: existing }
  const blank = current.findIndex((line) => line?.productId === undefined)
  if (blank >= 0) return { action: 'fill', index: blank }
  return { action: 'append', index: current.length }
}
