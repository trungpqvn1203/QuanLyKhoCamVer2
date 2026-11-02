import type { SaleRequestLine } from '../sales-orders/sales-order-api'
import type { PortalOrderInput } from './portal-api'

/** Giỏ đặt hàng: productId → số lượng (theo ĐVT của SP). SL 0 = bỏ khỏi giỏ. */
export type Cart = Record<number, number>

/** Đặt lại / sửa đơn: điền giỏ từ phần khách đặt của đơn cũ. */
export function cartFromRequestLines(lines: SaleRequestLine[]): Cart {
  return Object.fromEntries(lines.map((line) => [line.productId, line.quantity]))
}

/** SL nguyên ≥ 0; null / âm / lẻ → 0 hoặc làm tròn xuống (ô +/- chỉ cho số nguyên). */
export function setQuantity(cart: Cart, productId: number, quantity: number | null): Cart {
  const value = quantity == null || quantity < 0 ? 0 : Math.floor(quantity)
  const next = { ...cart }
  if (value === 0) delete next[productId]
  else next[productId] = value
  return next
}

export function cartLineCount(cart: Cart): number {
  return Object.keys(cart).length
}

/**
 * Giỏ → body API. SP không còn bán (không có trong danh sách SP hiện tại, ví dụ đặt lại đơn cũ) bị bỏ ra và trả về
 * riêng để báo cho khách, thay vì gửi lên rồi nhận lỗi 409.
 */
export function toOrderInput(
  cart: Cart,
  availableProductIds: ReadonlySet<number>,
  requestedDeliveryDate?: string,
  note?: string,
): { input: PortalOrderInput; unavailable: number[] } {
  const entries = Object.entries(cart).map(([id, quantity]) => ({ productId: Number(id), quantity }))
  return {
    input: {
      requestedDeliveryDate: requestedDeliveryDate || undefined,
      note: note?.trim() || undefined,
      lines: entries.filter((line) => availableProductIds.has(line.productId)),
    },
    unavailable: entries.filter((line) => !availableProductIds.has(line.productId)).map((line) => line.productId),
  }
}
