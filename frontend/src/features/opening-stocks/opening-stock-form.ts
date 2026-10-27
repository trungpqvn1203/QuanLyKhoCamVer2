import { todayIso } from '../../utils/format'
import { lineAmount } from '../../utils/money'
import type { OpeningStock, OpeningStockInput } from './opening-stock-api'

/** Giá trị một dòng trong Form — ô chưa nhập là undefined, ô đơn giá bị xóa là null. */
export interface OpeningLineFormValues {
  productId?: number
  quantity?: number
  unitPrice?: number | null
}

export interface OpeningStockFormValues {
  warehouseId?: number
  documentDate?: string
  note?: string
  lines?: OpeningLineFormValues[]
}

export function emptyOpeningStockForm(): OpeningStockFormValues {
  return { documentDate: todayIso(), lines: [{}] }
}

/** Đơn giá 0 trên phiếu đã lưu = chưa khai (BR-OPN-10) → để trống ô cho rõ. */
export function toOpeningFormValues(document: OpeningStock): OpeningStockFormValues {
  return {
    warehouseId: document.warehouseId,
    documentDate: document.documentDate,
    note: document.note,
    lines: document.lines.map((line) => ({
      productId: line.productId,
      quantity: line.quantity,
      unitPrice: line.unitPrice === 0 ? undefined : line.unitPrice,
    })),
  }
}

/** Giá trị form đã qua validateFields → body API. Đơn giá để trống thì không gửi. */
export function toOpeningInput(values: OpeningStockFormValues): OpeningStockInput {
  return {
    warehouseId: values.warehouseId!,
    documentDate: values.documentDate,
    note: values.note?.trim() || undefined,
    lines: (values.lines ?? []).map((line) => ({
      productId: line.productId!,
      quantity: line.quantity!,
      unitPrice: line.unitPrice ?? undefined,
    })),
  }
}

/** Tổng số lượng tạm khi đang nhập. */
export function draftTotalQuantity(lines: (OpeningLineFormValues | undefined)[] | undefined): number {
  return (lines ?? []).reduce((sum, line) => sum + (line?.quantity ?? 0), 0)
}

/** Tổng giá trị tạm (BR-MON-05) — dòng không có đơn giá tính 0. */
export function draftTotalAmount(lines: (OpeningLineFormValues | undefined)[] | undefined): number {
  return (lines ?? []).reduce((sum, line) => {
    if (!line?.quantity || line.unitPrice === undefined || line.unitPrice === null) return sum
    return sum + lineAmount(line.quantity, line.unitPrice)
  }, 0)
}
