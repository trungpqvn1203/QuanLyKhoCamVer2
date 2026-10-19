import { lineAmount } from '../../utils/money'
import { todayIso } from '../../utils/format'
import type { InboundReceipt, InboundReceiptInput } from './inbound-receipt-api'

/** Giá trị một dòng trong Form — ô chưa nhập là undefined, ô SL thực nhận bị xóa là null. */
export interface LineFormValues {
  productId?: number
  orderedQuantity?: number
  receivedQuantity?: number | null
  unitPrice?: number
}

export interface ReceiptFormValues {
  supplierId?: number
  warehouseId?: number
  documentDate?: string // "YYYY-MM-DD" — ô input type="date" dùng đúng định dạng này
  note?: string
  lines?: LineFormValues[]
}

/** Phiếu mới: ngày hôm nay (giờ VN, BR-IMP-08), sẵn một dòng trống. */
export function emptyReceiptForm(): ReceiptFormValues {
  return { documentDate: todayIso(), lines: [{}] }
}

/** Phiếu nháp đã lưu → giá trị form để sửa tiếp. */
export function toFormValues(receipt: InboundReceipt): ReceiptFormValues {
  return {
    supplierId: receipt.supplierId,
    warehouseId: receipt.warehouseId,
    documentDate: receipt.documentDate,
    note: receipt.note,
    lines: receipt.lines.map((line) => ({
      productId: line.productId,
      orderedQuantity: line.orderedQuantity,
      receivedQuantity: line.receivedQuantity,
      unitPrice: line.unitPrice,
    })),
  }
}

/**
 * Giá trị form (đã qua validateFields, các ô bắt buộc chắc chắn có) → body API.
 * SL thực nhận để trống → không gửi → backend lấy bằng SL đặt (BR-IMP-03).
 */
export function toReceiptInput(values: ReceiptFormValues): InboundReceiptInput {
  return {
    supplierId: values.supplierId!,
    warehouseId: values.warehouseId!,
    documentDate: values.documentDate,
    note: values.note?.trim() || undefined,
    lines: (values.lines ?? []).map((line) => ({
      productId: line.productId!,
      orderedQuantity: line.orderedQuantity!,
      receivedQuantity: line.receivedQuantity ?? undefined,
      unitPrice: line.unitPrice!,
    })),
  }
}

/** SL dùng để cộng tồn và tính tiền: SL thực nhận, để trống thì bằng SL đặt. */
export function effectiveReceived(line: LineFormValues): number | undefined {
  return line.receivedQuantity ?? line.orderedQuantity
}

/** Thành tiền tạm của một dòng khi đang nhập; chưa đủ số lượng/đơn giá → undefined. */
export function draftLineAmount(line: LineFormValues | undefined): number | undefined {
  const quantity = line ? effectiveReceived(line) : undefined
  if (quantity === undefined || line?.unitPrice === undefined || line.unitPrice === null) return undefined
  return lineAmount(quantity, line.unitPrice)
}

/** Tổng tạm của phiếu = cộng thành tiền các dòng đã tính được (BR-MON-05). */
export function draftTotal(lines: (LineFormValues | undefined)[] | undefined): number {
  return (lines ?? []).reduce((sum, line) => sum + (draftLineAmount(line) ?? 0), 0)
}
