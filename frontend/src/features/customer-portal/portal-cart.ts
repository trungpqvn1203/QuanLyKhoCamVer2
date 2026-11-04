import type { SaleRequestLine } from '../sales-orders/sales-order-api'
import { todayIso } from '../../utils/format'
import type { PortalOrderInput } from './portal-api'

/** BR-POR-12 — khớp PortalOrderLimits ở backend (backend vẫn là chốt chặn thật). */
export const MAX_LINE_QUANTITY = 9999
export const MAX_LINES = 50
export const MAX_DELIVERY_DAYS_AHEAD = 60

/** Giỏ đặt hàng: productId → số lượng (theo ĐVT của SP). SL 0 = bỏ khỏi giỏ. */
export type Cart = Record<number, number>

/** Đặt lại / sửa đơn: điền giỏ từ phần khách đặt của đơn cũ. */
export function cartFromRequestLines(lines: SaleRequestLine[]): Cart {
  return Object.fromEntries(lines.map((line) => [line.productId, line.quantity]))
}

/**
 * SL nguyên 0..9.999; null / âm / lẻ → 0 hoặc làm tròn xuống (ô +/- chỉ cho số nguyên). Giỏ đã đủ 50 SP thì không
 * thêm SP mới (vẫn sửa SL của SP đã có).
 */
export function setQuantity(cart: Cart, productId: number, quantity: number | null): Cart {
  const value = quantity == null || quantity < 0 ? 0 : Math.min(Math.floor(quantity), MAX_LINE_QUANTITY)
  if (value > 0 && !(productId in cart) && cartLineCount(cart) >= MAX_LINES) return cart
  const next = { ...cart }
  if (value === 0) delete next[productId]
  else next[productId] = value
  return next
}

export function cartLineCount(cart: Cart): number {
  return Object.keys(cart).length
}

/** Ngày muốn giao xa nhất (giờ VN): hôm nay + 60 ngày. Việt Nam không đổi giờ mùa hè → cộng đúng 60 × 24 giờ. */
export function latestDeliveryIso(now: Date = new Date()): string {
  return todayIso(new Date(now.getTime() + MAX_DELIVERY_DAYS_AHEAD * 24 * 60 * 60 * 1000))
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
