import { todayIso } from '../../utils/format'
import { lineAmount } from '../../utils/money'
import type { PaymentInput, SalesOrder, SalesOrderInput } from './sales-order-api'

export interface SalesLineFormValues {
  productId?: number
  quantity?: number
  unitPrice?: number
}

export interface SalesOrderFormValues {
  customerId?: number
  warehouseId?: number
  documentDate?: string // "YYYY-MM-DD"
  note?: string
  lines?: SalesLineFormValues[]
}

/** Phiếu mới: ngày hôm nay (giờ VN), chưa có dòng — SP thêm qua ô thêm nhanh. */
export function emptySalesOrderForm(): SalesOrderFormValues {
  return { documentDate: todayIso(), lines: [] }
}

export function toSalesFormValues(order: SalesOrder): SalesOrderFormValues {
  return {
    customerId: order.customerId,
    warehouseId: order.warehouseId,
    documentDate: order.documentDate,
    note: order.note,
    lines: order.lines.map((line) => ({
      productId: line.productId,
      quantity: line.quantity,
      unitPrice: line.unitPrice,
    })),
  }
}

/** Giá trị form đã qua validateFields → body API. */
export function toSalesOrderInput(values: SalesOrderFormValues): SalesOrderInput {
  return {
    customerId: values.customerId!,
    warehouseId: values.warehouseId!,
    documentDate: values.documentDate,
    note: values.note?.trim() || undefined,
    lines: (values.lines ?? []).map((line) => ({
      productId: line.productId!,
      quantity: line.quantity!,
      unitPrice: line.unitPrice!,
    })),
  }
}

/** Thành tiền tạm của một dòng khi đang nhập; thiếu số lượng/đơn giá → undefined. */
export function draftSalesLineAmount(line: SalesLineFormValues | undefined): number | undefined {
  if (line?.quantity == null || line.unitPrice == null) return undefined
  return lineAmount(line.quantity, line.unitPrice)
}

export function draftSalesTotal(lines: (SalesLineFormValues | undefined)[] | undefined): number {
  return (lines ?? []).reduce((sum, line) => sum + (draftSalesLineAmount(line) ?? 0), 0)
}

/**
 * BR-SAL-07: đổi khách → điền lại giá theo bảng giá của khách MỚI cho các dòng đã chọn SP.
 * Dòng có giá riêng → lấy giá riêng; không có → giữ giá đang nhập (người bán tự quyết).
 */
export function applyPriceList(
  lines: SalesLineFormValues[],
  prices: ReadonlyMap<number, number>,
): SalesLineFormValues[] {
  return lines.map((line) => {
    const price = line.productId === undefined ? undefined : prices.get(line.productId)
    return price === undefined ? line : { ...line, unitPrice: price }
  })
}

/** Nguồn của giá điền sẵn — để ô giá ghi rõ "Giá riêng", tên bảng giá (ví dụ "Giá đại lý") hay "Giá bán". */
export type SuggestedPriceSource = 'CUSTOMER' | 'PRICE_LIST' | 'PRODUCT'

export interface SuggestedPrice {
  price: number
  source: SuggestedPriceSource
}

/**
 * BR-SAL-07, BR-PRC-05 (sửa 2026-11-16): giá điền sẵn theo thứ tự ưu tiên:
 * 1. Giá riêng của khách (`customerPrices`)
 * 2. Giá theo bảng giá áp dụng cho khách (`priceListPrices`, ví dụ Giá lẻ / Giá đại lý / Giá trang trại)
 * 3. Đơn giá bán mặc định của SP (`productPrices`)
 * Không có cả ba → không điền (nhập tay).
 */
export function suggestPrice(
  productId: number | undefined,
  customerPrices: ReadonlyMap<number, number>,
  productPrices: ReadonlyMap<number, number>,
  priceListPrices: ReadonlyMap<number, number> = new Map(),
): SuggestedPrice | undefined {
  if (productId === undefined) return undefined
  const customer = customerPrices.get(productId)
  if (customer !== undefined) return { price: customer, source: 'CUSTOMER' }
  const tier = priceListPrices.get(productId)
  if (tier !== undefined) return { price: tier, source: 'PRICE_LIST' }
  const product = productPrices.get(productId)
  return product === undefined ? undefined : { price: product, source: 'PRODUCT' }
}

/** Gộp các bảng giá theo thứ tự ưu tiên: giá riêng của khách đè lên bảng giá của khách, bảng giá đè lên giá SP. */
export function mergePriceLists(
  customerPrices: ReadonlyMap<number, number>,
  productPrices: ReadonlyMap<number, number>,
  priceListPrices: ReadonlyMap<number, number> = new Map(),
): Map<number, number> {
  return new Map([...productPrices, ...priceListPrices, ...customerPrices])
}

/** Đơn giá bán mặc định của các SP có đặt giá → Map theo productId. */
export function productPriceMap(products: readonly { id: number; salePrice?: number | null }[]): Map<number, number> {
  const map = new Map<number, number>()
  for (const product of products) {
    if (product.salePrice != null) map.set(product.id, product.salePrice)
  }
  return map
}

export interface PaymentFormValues {
  paymentDate?: string
  cashAmount?: number | null
  transferAmount?: number | null
  /** Chỉ trả lương (BR-EPAY-02) */
  advanceDeduction?: number | null
  note?: string
}

/** Tổng một lần thu / trả = tiền mặt + chuyển khoản + trừ tạm ứng (ô trống = 0). */
export function paymentTotal(values: PaymentFormValues | undefined): number {
  return (values?.cashAmount ?? 0) + (values?.transferAmount ?? 0) + (values?.advanceDeduction ?? 0)
}

/** Ô trống / 0 → không gửi; backend chỉ ghi dòng cho phần > 0 (BR-SAL-12). */
export function toPaymentInput(values: PaymentFormValues): PaymentInput {
  return {
    paymentDate: values.paymentDate,
    cashAmount: values.cashAmount || undefined,
    transferAmount: values.transferAmount || undefined,
    advanceDeduction: values.advanceDeduction || undefined,
    note: values.note?.trim() || undefined,
  }
}
