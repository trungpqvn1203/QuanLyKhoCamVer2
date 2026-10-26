import { apiRequest, withQuery } from '../../api/client'
import type { PageQuery, PageResponse } from '../../api/types'
import { toFilterParams } from '../../utils/filters'
import type { CustomerType } from '../customers/customer-api'
import type { ReceiptStatus } from '../inbound-receipts/inbound-receipt-api'
import type { ProductUnit } from '../products/product-api'

/** Cùng vòng đời với phiếu nhập (BR-SAL-04): Nháp → Hoàn tất / Đã hủy; Hoàn tất → Đã đảo. */
export type SalesOrderStatus = ReceiptStatus

/** BR-SAL-12 */
export type PaymentMethod = 'CASH' | 'BANK_TRANSFER'

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
}

/** Chiều tiền: thu của khách (phiếu bán) hay trả cho NCC (phiếu nhập, BR-SPAY). Chỉ đổi chữ hiển thị. */
export type PaymentDirection = 'receive' | 'pay'

export const PAYMENT_VERB: Record<PaymentDirection, string> = { receive: 'Thu', pay: 'Trả' }

export interface SalesOrderLine {
  id: number
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  quantity: number
  unitPrice: number
  lineAmount: number
}

/** Một dòng lịch sử thanh toán. REVERSAL = hoàn một khoản thu ghi nhầm (BR-SAL-13). */
export interface SalePayment {
  id: number
  type: 'PAYMENT' | 'REVERSAL'
  method: PaymentMethod
  amount: number
  paymentDate: string
  note?: string
  /** Chỉ có ở dòng hoàn */
  reversalOfId?: number
  /** Khoản thu này đã bị hoàn → không hoàn lại được */
  reversed: boolean
  createdAt: string
}

/** Chi tiết phiếu bán (GET /{id}, kết quả mọi thao tác). Trường null bị bỏ khỏi JSON → tùy chọn. */
export interface SalesOrder {
  id: number
  code: string
  status: SalesOrderStatus
  documentDate: string
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  customerId: number
  customerCode: string
  customerName: string
  customerPhone: string
  note?: string
  totalAmount: number
  /** BR-SAL-10: đã thu và còn nợ — số do backend tính */
  paidAmount: number
  outstandingAmount: number
  reason?: string
  completedAt?: string
  cancelledAt?: string
  reversedAt?: string
  createdAt: string
  updatedAt: string
  lines: SalesOrderLine[]
  payments: SalePayment[]
}

export type SalesOrderSummary = Omit<
  SalesOrder,
  'lines' | 'payments' | 'note' | 'reason' | 'customerPhone' | 'cancelledAt' | 'reversedAt' | 'updatedAt'
>

export interface SalesOrderLineInput {
  productId: number
  quantity: number
  unitPrice: number
}

export interface SalesOrderInput {
  customerId: number
  warehouseId: number
  documentDate?: string
  note?: string
  lines: SalesOrderLineInput[]
}

/** Một lần thu: tiền mặt và/hoặc chuyển khoản (BR-SAL-12). */
export interface PaymentInput {
  paymentDate?: string
  cashAmount?: number
  transferAmount?: number
  /** Chỉ trả lương (BR-EPAY-02): phần trừ vào tạm ứng của nhân viên */
  advanceDeduction?: number
  note?: string
}

/** unpaidOnly là chuỗi 'true' để dùng được với ô chọn của FilterBar (giá trị string | number). */
export type SalesOrderFilters = {
  keyword?: string
  status?: SalesOrderStatus
  warehouseId?: number
  customerId?: number
  documentDateFrom?: string
  documentDateTo?: string
  unpaidOnly?: 'true'
}

/** Giá riêng của một SP cho một khách (BR-SAL-06). */
export interface CustomerPrice {
  productId: number
  productCode: string
  productName: string
  unit: ProductUnit
  productActive: boolean
  unitPrice: number
  updatedAt: string
}

/** Công nợ một khách (BR-SAL-10). */
export interface CustomerDebt {
  customerId: number
  customerCode: string
  customerName: string
  customerType: CustomerType
  customerPhone: string
  customerActive: boolean
  outstandingAmount: number
  openOrderCount: number
  oldestDocumentDate: string
}

const BASE = '/api/sales-orders'

export const salesOrderApi = {
  list: ({ page, size }: PageQuery, filters: SalesOrderFilters = {}) =>
    apiRequest<PageResponse<SalesOrderSummary>>(
      withQuery(BASE, { page, size, sort: 'id,desc', ...toFilterParams(filters) }),
    ),

  get: (id: number) => apiRequest<SalesOrder>(`${BASE}/${id}`),

  create: (input: SalesOrderInput) =>
    apiRequest<SalesOrder>(BASE, { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: SalesOrderInput) =>
    apiRequest<SalesOrder>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  /** Nháp → Hoàn tất: trừ tồn mọi dòng; thiếu tồn một dòng thì không trừ dòng nào (BR-SAL-02). */
  complete: (id: number) => apiRequest<SalesOrder>(`${BASE}/${id}/complete`, { method: 'POST' }),

  cancel: (id: number, reason?: string) =>
    apiRequest<SalesOrder>(`${BASE}/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason?.trim() || undefined }),
    }),

  /** Hoàn tất → Đã đảo; lý do bắt buộc; phiếu đã thu tiền bị từ chối (BR-SAL-05, 14). */
  reverse: (id: number, reason: string) =>
    apiRequest<SalesOrder>(`${BASE}/${id}/reverse`, { method: 'POST', body: JSON.stringify({ reason }) }),

  recordPayment: (id: number, input: PaymentInput) =>
    apiRequest<SalesOrder>(`${BASE}/${id}/payments`, { method: 'POST', body: JSON.stringify(input) }),

  reversePayment: (id: number, paymentId: number, reason: string) =>
    apiRequest<SalesOrder>(`${BASE}/${id}/payments/${paymentId}/reverse`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
}

export const customerPriceApi = {
  list: (customerId: number) => apiRequest<CustomerPrice[]>(`/api/customers/${customerId}/prices`),

  /** Đặt hoặc ghi đè giá; trả lại cả bảng giá của khách. */
  set: (customerId: number, productId: number, unitPrice: number) =>
    apiRequest<CustomerPrice[]>(`/api/customers/${customerId}/prices/${productId}`, {
      method: 'PUT',
      body: JSON.stringify({ unitPrice }),
    }),

  remove: (customerId: number, productId: number) =>
    apiRequest<void>(`/api/customers/${customerId}/prices/${productId}`, { method: 'DELETE' }),
}

export const customerDebtApi = {
  /** Chỉ khách còn nợ, nợ nhiều nhất trước. */
  list: () => apiRequest<CustomerDebt[]>('/api/customer-debts'),
}
