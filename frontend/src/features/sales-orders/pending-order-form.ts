import type { OrderChannel, PendingOrderInput, SalesOrder } from './sales-order-api'
import type { SalesOrderFormValues } from './sales-order-form'

export interface RequestLineFormValues {
  productId?: number
  quantity?: number
}

export interface PendingOrderFormValues {
  customerId?: number
  orderChannel?: OrderChannel
  requestedDeliveryDate?: string // "YYYY-MM-DD"
  note?: string
  lines?: RequestLineFormValues[]
}

/** Đơn mới: phần lớn đơn đến qua điện thoại → chọn sẵn kênh Điện thoại, chưa hẹn ngày giao. */
export function emptyPendingOrderForm(): PendingOrderFormValues {
  return { orderChannel: 'PHONE', lines: [] }
}

export function toPendingFormValues(order: SalesOrder): PendingOrderFormValues {
  return {
    customerId: order.customerId,
    orderChannel: order.orderChannel,
    requestedDeliveryDate: order.requestedDeliveryDate,
    note: order.note,
    lines: order.requestLines.map((line) => ({ productId: line.productId, quantity: line.quantity })),
  }
}

/** Giá trị form đã qua validateFields → body API. Ô ngày giao trống → không gửi (chưa hẹn ngày). */
export function toPendingOrderInput(values: PendingOrderFormValues): PendingOrderInput {
  return {
    customerId: values.customerId!,
    orderChannel: values.orderChannel!,
    requestedDeliveryDate: values.requestedDeliveryDate || undefined,
    note: values.note?.trim() || undefined,
    lines: (values.lines ?? []).map((line) => ({ productId: line.productId!, quantity: line.quantity! })),
  }
}

/**
 * BR-SAL-19: form xác nhận điền sẵn từ phần khách đặt — cùng SP, cùng SL, giá lấy từ bảng giá riêng của khách
 * (không có thì để trống cho kho nhập). Ngày bán = ngày ghi đơn; kho chỉ chọn sẵn khi có đúng một kho.
 * Kho sửa SL, đổi SP, thêm / bớt dòng trên form như phiếu nháp thường.
 */
export function confirmationFormValues(
  order: SalesOrder,
  prices: ReadonlyMap<number, number>,
  onlyWarehouseId?: number,
): SalesOrderFormValues {
  return {
    customerId: order.customerId,
    warehouseId: onlyWarehouseId,
    documentDate: order.documentDate,
    note: order.note,
    lines: order.requestLines.map((line) => ({
      productId: line.productId,
      quantity: line.quantity,
      unitPrice: prices.get(line.productId),
    })),
  }
}
