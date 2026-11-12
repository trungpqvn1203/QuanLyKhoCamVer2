import { todayIso } from '../../utils/format'
import type { Transfer, TransferInput } from './transfer-api'

/** Giá trị một dòng trong Form — ô chưa nhập là undefined. */
export interface TransferLineFormValues {
  productId?: number
  quantity?: number
}

export interface TransferFormValues {
  sourceWarehouseId?: number
  targetWarehouseId?: number
  documentDate?: string
  note?: string
  lines?: TransferLineFormValues[]
}

export function emptyTransferForm(): TransferFormValues {
  return { documentDate: todayIso(), lines: [] }
}

export function toTransferFormValues(transfer: Transfer): TransferFormValues {
  return {
    sourceWarehouseId: transfer.sourceWarehouseId,
    targetWarehouseId: transfer.targetWarehouseId,
    documentDate: transfer.documentDate,
    note: transfer.note,
    lines: transfer.lines.map((line) => ({ productId: line.productId, quantity: line.quantity })),
  }
}

/** Giá trị form đã qua validateFields → body API. */
export function toTransferInput(values: TransferFormValues): TransferInput {
  return {
    sourceWarehouseId: values.sourceWarehouseId!,
    targetWarehouseId: values.targetWarehouseId!,
    documentDate: values.documentDate,
    note: values.note?.trim() || undefined,
    lines: (values.lines ?? []).map((line) => ({ productId: line.productId!, quantity: line.quantity! })),
  }
}

/** Tổng số lượng tạm khi đang nhập. */
export function draftTotalQuantity(lines: (TransferLineFormValues | undefined)[] | undefined): number {
  return (lines ?? []).reduce((sum, line) => sum + (line?.quantity ?? 0), 0)
}

/** BR-STK-02: tấn = SL × kg/bao ÷ 1000, làm tròn đến kg (3 chữ số lẻ). */
export function toTons(quantity: number, netWeightKg: number): number {
  return Math.round(quantity * netWeightKg) / 1000
}

/**
 * Tổng tấn của các dòng — cộng kg nguyên rồi mới chia, tránh sai số cộng dồn số thực (0,1 + 0,2).
 * Dòng chưa có SL hoặc không biết trọng lượng SP thì bỏ qua.
 */
export function totalTons(
  lines: readonly ({ productId?: number; quantity?: number; netWeightKg?: number } | undefined)[] | undefined,
  weights?: ReadonlyMap<number, number>,
): number {
  const kilograms = (lines ?? []).reduce((sum, line) => {
    const weight = line?.netWeightKg ?? (line?.productId === undefined ? undefined : weights?.get(line.productId))
    return line?.quantity && weight !== undefined ? sum + Math.round(line.quantity * weight) : sum
  }, 0)
  return kilograms / 1000
}
