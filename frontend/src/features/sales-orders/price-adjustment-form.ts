import { lineAmount } from '../../utils/money'
import type { PriceAdjustmentInput, SalesOrderLine } from './sales-order-api'

/** Giá mới theo productId — ô để trống = giữ giá cũ. */
export interface PriceAdjustmentFormValues {
  prices?: Record<string, number | null | undefined>
  reason?: string
}

/** Mở hộp: mọi ô điền sẵn giá hiện tại, người dùng chỉ sửa dòng cần đổi. */
export function initialPriceValues(lines: SalesOrderLine[]): PriceAdjustmentFormValues {
  return { prices: Object.fromEntries(lines.map((line) => [String(line.productId), line.unitPrice])) }
}

/** Giá sẽ áp dụng cho một dòng: ô trống → giá cũ. */
export function effectivePrice(line: SalesOrderLine, values: PriceAdjustmentFormValues | undefined): number {
  return values?.prices?.[String(line.productId)] ?? line.unitPrice
}

/** Tổng phiếu tạm tính sau điều chỉnh (cùng quy tắc HALF_UP với backend); số chính thức do backend tính. */
export function adjustedTotal(lines: SalesOrderLine[], values: PriceAdjustmentFormValues | undefined): number {
  return lines.reduce((sum, line) => sum + lineAmount(line.quantity, effectivePrice(line, values)), 0)
}

/** BR-SAL-24: chỉ gửi dòng thật sự đổi giá; không dòng nào đổi → lines rỗng (FE báo lỗi, không gọi API). */
export function toPriceAdjustmentInput(lines: SalesOrderLine[], values: PriceAdjustmentFormValues): PriceAdjustmentInput {
  return {
    reason: values.reason?.trim() ?? '',
    lines: lines
      .filter((line) => effectivePrice(line, values) !== line.unitPrice)
      .map((line) => ({ productId: line.productId, unitPrice: effectivePrice(line, values) })),
  }
}
